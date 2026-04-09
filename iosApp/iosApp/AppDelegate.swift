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
        let entryPoint = IosEntryPoint()
        let rootViewController = entryPoint.createViewController() as! UIViewController
        window?.rootViewController = rootViewController
        window?.makeKeyAndVisible()
        return true
    }
}
