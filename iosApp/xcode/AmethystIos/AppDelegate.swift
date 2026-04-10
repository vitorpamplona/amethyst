import UIKit
import AmethystIos

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        let viewController = MainViewControllerKt.MainViewController()
        window?.rootViewController = viewController
        window?.makeKeyAndVisible()
        return true
    }
}
