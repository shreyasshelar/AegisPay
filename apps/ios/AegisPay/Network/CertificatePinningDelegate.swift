import Foundation
import CryptoKit
import os.log

/// URLSessionDelegate that enforces SPKI-hash-based certificate pinning.
///
/// In development (when `AppConfig.pinnedCertificateHashes` is empty) all
/// server-trust challenges are passed through so local dev still works.
/// In production the delegate verifies that the leaf certificate's
/// SubjectPublicKeyInfo matches one of the pre-configured SHA-256 hashes.
final class CertificatePinningDelegate: NSObject, URLSessionDelegate {

    // ── Pinned hashes ──────────────────────────────────────────────────────────

    private let pinnedHashes: Set<String>

    private static let log = Logger(
        subsystem: "io.aegispay.app",
        category:  "CertificatePinning"
    )

    override init() {
        self.pinnedHashes = Set(AppConfig.pinnedCertificateHashes)
        super.init()
    }

    // ── URLSessionDelegate ─────────────────────────────────────────────────────

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod
                == NSURLAuthenticationMethodServerTrust else {
            // Not a server-trust challenge — let the system handle it.
            completionHandler(.performDefaultHandling, nil)
            return
        }

        // Dev mode: no hashes configured → pass through.
        guard !pinnedHashes.isEmpty else {
            completionHandler(.performDefaultHandling, nil)
            return
        }

        guard
            let serverTrust = challenge.protectionSpace.serverTrust,
            SecTrustGetCertificateCount(serverTrust) > 0
        else {
            Self.log.warning("Certificate pinning: no certificates in server trust — rejecting")
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        // Evaluate the trust chain first.
        var evaluationError: CFError?
        let isTrusted = SecTrustEvaluateWithError(serverTrust, &evaluationError)
        guard isTrusted else {
            Self.log.warning(
                "Certificate pinning: server trust evaluation failed — \(String(describing: evaluationError))"
            )
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        // Extract the leaf certificate (index 0).
        guard let leafCert = SecTrustGetCertificateAtIndex(serverTrust, 0) else {
            Self.log.warning("Certificate pinning: unable to extract leaf certificate — rejecting")
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        // Compute the SHA-256 hash of the SPKI bytes.
        guard let spkiHash = spkiSHA256(for: leafCert) else {
            Self.log.warning("Certificate pinning: unable to compute SPKI hash — rejecting")
            completionHandler(.cancelAuthenticationChallenge, nil)
            return
        }

        if pinnedHashes.contains(spkiHash) {
            // Hash matches — allow.
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        } else {
            Self.log.warning(
                "Certificate pinning: SPKI hash '\(spkiHash)' not in pinned set — rejecting"
            )
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }

    // ── SPKI extraction ────────────────────────────────────────────────────────

    /// Extracts the SubjectPublicKeyInfo (SPKI) bytes from a `SecCertificate`
    /// and returns their SHA-256 digest encoded as base64.
    ///
    /// RFC 7469 / Android's CertificatePinner use the same approach:
    /// hash the SPKI block (not the whole cert or just the key bytes).
    private func spkiSHA256(for certificate: SecCertificate) -> String? {
        // Copy the DER-encoded certificate data.
        let certData = SecCertificateCopyData(certificate) as Data

        // Parse the DER to extract the SPKI offset.
        // DER structure: SEQUENCE { tbsCert SEQUENCE { version, serialNumber,
        //   sigAlg, issuer, validity, subject, SPKI SEQUENCE { ... } } ... }
        // We need to walk the ASN.1 to find the subjectPublicKeyInfo field.
        guard let spkiData = extractSPKI(from: certData) else {
            return nil
        }

        let digest = SHA256.hash(data: spkiData)
        return Data(digest).base64EncodedString()
    }

    /// Minimal ASN.1 DER walker that locates the SubjectPublicKeyInfo field
    /// inside a DER-encoded X.509 certificate.
    ///
    /// X.509 TBSCertificate layout (all SEQUENCE/CONTEXT tags):
    ///   SEQUENCE (Certificate)
    ///     SEQUENCE (TBSCertificate)
    ///       [0] version            (optional, EXPLICIT)
    ///       INTEGER serialNumber
    ///       SEQUENCE sigAlgId
    ///       SEQUENCE issuer
    ///       SEQUENCE validity
    ///       SEQUENCE subject
    ///       SEQUENCE subjectPublicKeyInfo   ← we want this
    ///       ...
    private func extractSPKI(from certDER: Data) -> Data? {
        var index = certDER.startIndex

        // Outermost SEQUENCE (the Certificate).
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // TBSCertificate SEQUENCE.
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // version [0] EXPLICIT (optional, tag 0xa0)
        if certDER[index] == 0xa0 {
            guard skipTag(0xa0, in: certDER, at: &index) else { return nil }
        }

        // serialNumber INTEGER
        guard skipTag(0x02, in: certDER, at: &index) else { return nil }

        // signature AlgorithmIdentifier SEQUENCE
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // issuer Name SEQUENCE
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // validity SEQUENCE
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // subject Name SEQUENCE
        guard skipTag(0x30, in: certDER, at: &index) else { return nil }

        // subjectPublicKeyInfo SEQUENCE — capture start and length.
        guard certDER[index] == 0x30 else { return nil }
        let spkiStart = index
        guard let spkiLength = readLength(in: certDER, at: &index) else { return nil }
        let spkiEnd = index + spkiLength
        guard spkiEnd <= certDER.endIndex else { return nil }

        return certDER[spkiStart..<spkiEnd]
    }

    /// Advances `index` past a TLV whose tag matches `expectedTag`, returns `false` if mismatch.
    private func skipTag(_ expectedTag: UInt8, in data: Data, at index: inout Data.Index) -> Bool {
        guard index < data.endIndex, data[index] == expectedTag else { return false }
        index = data.index(after: index)
        guard let length = readLength(in: data, at: &index) else { return false }
        index += length
        return index <= data.endIndex
    }

    /// Reads a DER-encoded length at `index`, advances `index` past the length bytes,
    /// and returns the length value.
    private func readLength(in data: Data, at index: inout Data.Index) -> Int? {
        guard index < data.endIndex else { return nil }
        let first = data[index]
        index = data.index(after: index)

        if first & 0x80 == 0 {
            // Short form.
            return Int(first)
        }

        let numBytes = Int(first & 0x7F)
        guard numBytes > 0, numBytes <= 4 else { return nil }
        guard index + numBytes <= data.endIndex else { return nil }

        var length = 0
        for _ in 0..<numBytes {
            length = (length << 8) | Int(data[index])
            index = data.index(after: index)
        }
        return length
    }
}
