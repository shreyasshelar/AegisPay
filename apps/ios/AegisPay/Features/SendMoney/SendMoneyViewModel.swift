import SwiftUI

// MARK: — Step enum

enum SendStep: Equatable {
    case payee, amount, review, status
}

// MARK: — ViewModel

@MainActor
final class SendMoneyViewModel: ObservableObject {

    // ── Step ──────────────────────────────────────────────────────────────────
    @Published private(set) var step: SendStep = .payee

    // ── KYC gate ──────────────────────────────────────────────────────────────
    @Published private(set) var kycStatus:    KycStatus?
    @Published private(set) var kycLoading:   Bool = true

    // ── Form state ────────────────────────────────────────────────────────────
    @Published var payeeId    = ""
    @Published var amountText = ""
    @Published var note       = ""
    @Published var currency   = "INR"

    // ── Submit state ──────────────────────────────────────────────────────────
    @Published private(set) var isSubmitting    = false
    @Published private(set) var submissionError: String?

    // ── FX / Risk ─────────────────────────────────────────────────────────────
    /// Live rates for INR equivalent calculation.
    /// No `private(set)` so tests can inject rates directly via `vm.fxRates = ...`.
    @Published var fxRates: FxRates = FxRates(usd: 0.01190, eur: 0.01099, gbp: 0.00936)
    /// Amber warning when the INR-equivalent amount ≥ ₹10,000 (risk review threshold).
    @Published private(set) var riskWarning: String? = nil

    private static let RISK_THRESHOLD_INR: Decimal = 10_000

    // ── Status step state ─────────────────────────────────────────────────────
    @Published private(set) var createdTx:      Transaction?
    @Published private(set) var isLoadingStatus = false
    @Published private(set) var statusError:    String?

    // AI error resolution
    @Published private(set) var errorResolution:  ErrorResolutionResponse?
    @Published private(set) var isResolvingError  = false

    // ── Idempotency key — generated once, reused on retry ────────────────────
    private(set) var idempotencyKey = UUID().uuidString

    // ── Services ──────────────────────────────────────────────────────────────
    private let txService   = TransactionService()
    private let aiService   = AiService()
    private let userService = UserService()
    private var socket:    StompWebSocket?
    private var pollTask:  Task<Void, Never>?

    // ── KYC check + FX load ───────────────────────────────────────────────────

    func loadKycStatus(userId: String) async {
        kycLoading = true
        async let profile = userService.getProfile(userId: userId)
        async let rates   = FxRateService.shared.rates()
        kycStatus = (try? await profile)?.kycStatus
        fxRates   = await rates
        kycLoading = false
    }

    // ── Risk threshold check ──────────────────────────────────────────────────

    /// Recompute `riskWarning` whenever amount or currency changes.
    /// Frankfurter rates: 1 INR = rate[currency] units.
    /// To convert `amt` in foreign currency to INR: inrEquivalent = amt / rate[currency].
    func updateRiskWarning() {
        guard let amt = amount, amt > 0 else { riskWarning = nil; return }

        let inrEquivalent: Decimal
        if currency == "INR" {
            inrEquivalent = amt
        } else if let rate = fxRates.rate(for: currency), rate > 0 {
            // 1 INR = rate foreign units  →  1 foreign unit = 1/rate INR
            inrEquivalent = amt / Decimal(rate)
        } else {
            inrEquivalent = amt
        }

        if inrEquivalent >= SendMoneyViewModel.RISK_THRESHOLD_INR {
            let formatted = inrEquivalent.formatted(currency: "INR")
            riskWarning = "Transfers ≥ ₹10,000 (~\(formatted)) trigger enhanced risk review — approval may take up to 60 s."
        } else {
            riskWarning = nil
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    var payeeIdError: String? {
        guard !payeeId.isEmpty else { return nil }
        // UUID(uuidString:) validates the canonical 8-4-4-4-12 UUID format.
        return UUID(uuidString: payeeId) == nil ? "Must be a valid UUID" : nil
    }

    var amount: Decimal? { Decimal(string: amountText) }

    var amountError: String? {
        guard !amountText.isEmpty else { return nil }
        guard let amt = amount else { return "Enter a valid amount" }
        if amt <= 0        { return "Amount must be greater than 0" }
        if amt > 1_000_000 { return "Maximum 10,00,000 per transfer" }
        return nil
    }

    var isPayeeValid: Bool {
        !payeeId.isEmpty && payeeIdError == nil
    }

    var isAmountValid: Bool {
        amount != nil && amountError == nil
    }

    // ── Step navigation ───────────────────────────────────────────────────────

    func goTo(_ target: SendStep) {
        withAnimation(.easeInOut(duration: 0.25)) { step = target }
    }

    func back() {
        switch step {
        case .payee:  break
        case .amount: goTo(.payee)
        case .review: goTo(.amount)
        case .status: break   // no back from status — use reset()
        }
    }

    // ── Submit (Step Review → Status) ─────────────────────────────────────────

    func submit() async {
        guard let amt = amount, isPayeeValid, isAmountValid else { return }
        isSubmitting    = true
        submissionError = nil

        let request = CreateTransactionRequest(
            payeeId:  payeeId,
            amount:   amt,
            currency: currency,
            note:     note.isEmpty ? nil : note
        )

        do {
            let tx = try await txService.create(request, idempotencyKey: idempotencyKey)
            createdTx = tx
            HapticFeedback.impact(.medium)
            goTo(.status)
        } catch {
            submissionError = error.localizedDescription
            HapticFeedback.error()
            // idempotencyKey intentionally preserved for retry
        }
        isSubmitting = false
    }

    // ── Status step — live updates ────────────────────────────────────────────

    func startLiveUpdates(userId: String, accessToken: String) {
        guard let txId = createdTx?.transactionId,
              !(createdTx?.status.isTerminal ?? false) else { return }

        socket = StompWebSocket(
            userId:    userId,
            wsBaseURL: AppConfig.wsBaseURL,
            onMessage: { [weak self] notification in
                guard let self,
                      notification.transactionId == txId else { return }
                Task { await self.refreshStatus() }
            }
        )
        socket?.connect(accessToken: accessToken)

        // Polling fallback every 4 s
        pollTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                guard !Task.isCancelled else { break }
                await refreshStatus()
                if createdTx?.status.isTerminal == true { break }
            }
        }
    }

    func stopLiveUpdates() {
        socket?.disconnect()
        socket     = nil
        pollTask?.cancel()
        pollTask   = nil
    }

    private func refreshStatus() async {
        guard let txId = createdTx?.transactionId else { return }
        isLoadingStatus = true
        do {
            let tx = try await txService.get(id: txId)
            let wasTerminal = createdTx?.status.isTerminal ?? false
            createdTx = tx

            if !wasTerminal && tx.status == .completed {
                HapticFeedback.success()
            }

            if (tx.status == .failed || tx.status == .rolledBack),
               let reason = tx.failureReason,
               errorResolution == nil,
               !isResolvingError
            {
                await resolveError(reason: reason)
            }
        } catch {
            statusError = error.localizedDescription
        }
        isLoadingStatus = false
    }

    // ── AI error resolution ───────────────────────────────────────────────────

    func resolveError(reason: String) async {
        guard !isResolvingError else { return }
        isResolvingError = true
        do {
            errorResolution = try await aiService.resolveError(
                ErrorResolutionRequest(
                    errorCode:    reason.components(separatedBy: ":").last?
                                       .trimmingCharacters(in: .whitespaces) ?? "UNKNOWN",
                    errorMessage: reason
                )
            )
        } catch { /* non-fatal */ }
        isResolvingError = false
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    func reset() {
        stopLiveUpdates()
        payeeId          = ""
        amountText       = ""
        note             = ""
        currency         = "INR"
        riskWarning      = nil
        createdTx        = nil
        submissionError  = nil
        statusError      = nil
        errorResolution  = nil
        isResolvingError = false
        idempotencyKey   = UUID().uuidString
        withAnimation(.easeInOut(duration: 0.25)) { step = .payee }
    }
}
