import Foundation

// ── Request / Response models ─────────────────────────────────────────────────
// These mirror the shared-types Zod schemas on the web side.

// MARK: — Transaction

enum TransactionStatus: String, Codable, CaseIterable {
    case initiated     = "INITIATED"
    case reserved      = "RESERVED"
    case riskCleared   = "RISK_CLEARED"
    case processing    = "PROCESSING"
    case completed     = "COMPLETED"
    case failed        = "FAILED"
    case rolledBack    = "ROLLED_BACK"

    var isTerminal: Bool {
        [.completed, .failed, .rolledBack].contains(self)
    }

    var displayLabel: String {
        switch self {
        case .initiated:   return "Initiated"
        case .reserved:    return "Funds Reserved"
        case .riskCleared: return "Risk Cleared"
        case .processing:  return "Processing"
        case .completed:   return "Completed"
        case .failed:      return "Failed"
        case .rolledBack:  return "Rolled Back"
        }
    }
}

struct Transaction: Codable, Identifiable {
    let transactionId: String
    let payerId:       String
    let payeeId:       String
    let amount:        Decimal
    let currency:      String
    let status:        TransactionStatus
    let initiatedAt:   Date
    let completedAt:   Date?
    let failureReason: String?
    let note:          String?

    var id: String { transactionId }
}

struct PagedTransactions: Codable {
    let content:       [Transaction]
    let totalElements: Int
    let totalPages:    Int
    let number:        Int
    let size:          Int
    let last:          Bool
}

struct CreateTransactionRequest: Encodable {
    let payeeId:  String
    let amount:   Decimal
    let currency: String
    let note:     String?
}

// MARK: — Account / Ledger

struct Account: Codable, Identifiable {
    let id:               String
    let userId:           String
    let currency:         String
    let availableBalance: Decimal
    let reservedBalance:  Decimal
}

// MARK: — Wallet top-up

struct TopUpIntentRequest: Encodable {
    let amount:   Decimal
    let currency: String
}

struct TopUpIntentResponse: Codable {
    let paymentIntentId: String
    let clientSecret:    String
    let amount:          Decimal
    let currency:        String
}

struct TopUpConfirmRequest: Encodable {
    let paymentIntentId: String
}

// MARK: — User / KYC

enum KycStatus: String, Codable {
    case pending            = "PENDING"
    case documentSubmitted  = "DOCUMENT_SUBMITTED"
    case aiProcessing       = "AI_PROCESSING"
    case approved           = "APPROVED"
    case rejected           = "REJECTED"
    case manualReview       = "MANUAL_REVIEW"
}

struct UserProfile: Codable, Identifiable {
    let id:        String
    let email:     String
    let name:      String?
    let kycStatus: KycStatus
    let role:      String
}

enum KycDocumentType: String, CaseIterable {
    case nationalId      = "NATIONAL_ID"
    case passport        = "PASSPORT"
    case drivingLicense  = "DRIVING_LICENSE"
    case panCard         = "PAN_CARD"

    var displayLabel: String {
        switch self {
        case .nationalId:     return "National ID / Aadhaar"
        case .passport:       return "Passport"
        case .drivingLicense: return "Driver's License"
        case .panCard:        return "PAN Card"
        }
    }
}

struct KycDocumentRequest: Encodable {
    let documentType:    String
    let base64ImageData: String
    let mimeType:        String
    let registeredName:  String?   // cross-matched against extracted name by AI platform
}

// MARK: — Risk / Back-office

enum RiskDecision: String, Codable {
    case approved = "APPROVED"
    case review   = "REVIEW"
    case rejected = "REJECTED"

    var displayLabel: String {
        switch self {
        case .approved: return "Approved"
        case .review:   return "Review"
        case .rejected: return "Rejected"
        }
    }

    var color: String {
        switch self {
        case .approved: return "success"
        case .review:   return "warning"
        case .rejected: return "danger"
        }
    }
}

/// `ruleFlags` arrives as a JSON object with arbitrary values (e.g. `{ "VELOCITY": true }`).
/// We only need the keys, so we decode them via a nested keyed container.
struct RiskCase: Identifiable, Decodable {
    let id:             String
    let transactionId:  String
    let userId:         String
    let riskScore:      Int
    let decision:       RiskDecision
    let ruleFlagKeys:   [String]        // extracted from the ruleFlags object
    let ragExplanation: String?
    let createdAt:      String

    private enum CodingKeys: String, CodingKey {
        case id, transactionId, userId, riskScore, decision, ruleFlags, ragExplanation, createdAt
    }

    // Dynamic key to iterate any JSON object field names
    private struct AnyKey: CodingKey {
        var stringValue: String
        var intValue:    Int? { nil }
        init(stringValue: String) { self.stringValue = stringValue }
        init?(intValue: Int) { nil }
    }

    init(from decoder: Decoder) throws {
        let c    = try decoder.container(keyedBy: CodingKeys.self)
        id             = try c.decode(String.self,      forKey: .id)
        transactionId  = try c.decode(String.self,      forKey: .transactionId)
        userId         = try c.decode(String.self,      forKey: .userId)
        riskScore      = try c.decode(Int.self,         forKey: .riskScore)
        decision       = try c.decode(RiskDecision.self,forKey: .decision)
        ragExplanation = try c.decodeIfPresent(String.self, forKey: .ragExplanation)
        createdAt      = try c.decode(String.self,      forKey: .createdAt)
        // Extract flag names from the ruleFlags object
        if let flagContainer = try? c.nestedContainer(keyedBy: AnyKey.self, forKey: .ruleFlags) {
            ruleFlagKeys = flagContainer.allKeys.map(\.stringValue)
        } else {
            ruleFlagKeys = []
        }
    }
}

// MARK: — Notification

struct PushNotification: Codable, Identifiable {
    let id:            String
    let type:          String
    let title:         String
    let body:          String
    let createdAt:     Date
    let transactionId: String?
}

struct PagedNotifications: Codable {
    let content:       [PushNotification]
    let totalElements: Int
    let last:          Bool
}

// MARK: — AI

struct ErrorResolutionRequest: Encodable {
    let errorCode:    String
    let errorMessage: String?
}

struct ErrorResolutionResponse: Codable {
    let errorCode:  String
    let resolution: String
}

struct FraudExplainRequest: Encodable {
    let transactionId: String
    let riskScore:     Int
    let flaggedRules:  [String]
}

struct FraudExplainResponse: Codable {
    let transactionId: String
    let explanation:   String
}
