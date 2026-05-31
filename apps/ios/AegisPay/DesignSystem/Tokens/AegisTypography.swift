import SwiftUI

/// Typographic scale — mirrors the web design-system tokens.
extension Font {
    static let aegisTitle     = Font.system(size: 28, weight: .bold,     design: .rounded)
    static let aegisHeadline  = Font.system(size: 20, weight: .semibold, design: .rounded)
    static let aegisSubhead   = Font.system(size: 16, weight: .semibold, design: .default)
    static let aegisBody      = Font.system(size: 15, weight: .regular,  design: .default)
    static let aegisBodySmall = Font.system(size: 13, weight: .regular,  design: .default)
    static let aegisCaption   = Font.system(size: 11, weight: .medium,   design: .default)
    static let aegisMono      = Font.system(size: 13, weight: .regular,  design: .monospaced)
    static let aegisMonoSmall = Font.system(size: 11, weight: .regular,  design: .monospaced)
    static let aegisAmount    = Font.system(size: 34, weight: .bold,     design: .rounded)
}
