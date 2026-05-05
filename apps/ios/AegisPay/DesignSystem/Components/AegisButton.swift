import SwiftUI

// ── Button style variants ─────────────────────────────────────────────────────

enum AegisButtonVariant {
    case primary, secondary, destructive, ghost
}

struct AegisButtonStyle: ButtonStyle {
    let variant:  AegisButtonVariant
    let loading:  Bool
    let fullWidth: Bool

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 8) {
            if loading {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(tintColor)
                    .scaleEffect(0.8)
            }
            configuration.label
        }
        .font(.aegisSubhead)
        .foregroundStyle(tintColor)
        .frame(maxWidth: fullWidth ? .infinity : nil)
        .padding(.horizontal, 20)
        .padding(.vertical, 13)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(borderColor, lineWidth: variant == .secondary ? 1.5 : 0)
        )
        .opacity(configuration.isPressed ? 0.82 : 1)
        .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
    }

    private var tintColor: Color {
        switch variant {
        case .primary:     return .white
        case .secondary:   return .aegisPrimary
        case .destructive: return .white
        case .ghost:       return .aegisPrimary
        }
    }

    private var background: some ShapeStyle {
        switch variant {
        case .primary:     return AnyShapeStyle(Color.aegisPrimary)
        case .secondary:   return AnyShapeStyle(Color.clear)
        case .destructive: return AnyShapeStyle(Color.aegisDanger)
        case .ghost:       return AnyShapeStyle(Color.clear)
        }
    }

    private var borderColor: Color {
        variant == .secondary ? .aegisPrimary : .clear
    }
}

// ── Convenience view modifier ─────────────────────────────────────────────────

extension View {
    func aegisButtonStyle(
        _ variant:   AegisButtonVariant = .primary,
        loading:     Bool = false,
        fullWidth:   Bool = false
    ) -> some View {
        self.buttonStyle(
            AegisButtonStyle(variant: variant, loading: loading, fullWidth: fullWidth)
        )
        .disabled(loading)
    }
}
