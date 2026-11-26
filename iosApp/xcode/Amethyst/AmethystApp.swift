import AmethystKit
import SwiftUI

/// The app's entry point. It only hosts the shared Kotlin UI (`MainViewController` in :iosApp),
/// the same AmethystApp that Android and the desktop run.
@main
struct AmethystIOSApp: App {
    var body: some Scene {
        WindowGroup {
            SharedAppView()
                // The shared UI draws edge to edge and pads itself with the safe-area insets, as on Android.
                .ignoresSafeArea()
        }
    }
}

private struct SharedAppView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
