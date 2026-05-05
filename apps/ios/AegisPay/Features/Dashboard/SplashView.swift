import SwiftUI

struct SplashView: View {
    @State private var scale = 0.7
    @State private var opacity = 0.0

    var body: some View {
        ZStack {
            Color.aegisPrimary.ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: "shield.checkered")
                    .font(.system(size: 72, weight: .semibold))
                    .foregroundStyle(.white)
                    .scaleEffect(scale)
                    .opacity(opacity)

                Text("AegisPay")
                    .font(.aegisTitle)
                    .foregroundStyle(.white)
                    .opacity(opacity)
            }
        }
        .onAppear {
            withAnimation(.spring(duration: 0.6, bounce: 0.35)) {
                scale   = 1.0
                opacity = 1.0
            }
        }
    }
}
