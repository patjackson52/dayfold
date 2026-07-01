import SwiftUI
import BackgroundTasks
import client

// ADR 0044 Phase B — the iOS app host. Renders the shared Compose MainViewController; installs the
// process-global notification glue (UN/CL delegates) + the BGTaskScheduler reconcile lane. Delegates are
// set on the main thread in didFinishLaunching (incl. the background-launch path) or CL/UN callbacks
// would silently never fire.
@main
struct DayfoldApp: App {
  @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
  var body: some Scene {
    WindowGroup {
      ContentView().ignoresSafeArea()
    }
  }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
  // Must match Info.plist BGTaskSchedulerPermittedIdentifiers.
  private let bgTaskId = "com.sloopworks.dayfold.now.refresh"

  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    // Reconcile-only BGTask — neither lane needs it to DELIVER (region monitoring wakes the app; scheduled
    // local notifs fire directly); it just keeps the region set + exact schedules fresh opportunistically.
    // register() MUST happen before didFinishLaunching returns.
    BGTaskScheduler.shared.register(forTaskWithIdentifier: bgTaskId, using: nil) { [weak self] task in
      IosBackgroundNotifyKt.bgReconcile()
      self?.submitReconcile()               // re-arm the next opportunistic run
      task.setTaskCompleted(success: true)
    }

    // Main thread. Sets the (retained) UN + CL delegates, warms the shared ContentStore, requests notif auth.
    IosNotifGlue.shared.start()
    submitReconcile()

    // S3 verification scaffold — enable device-local proximity + register geofences for the seeded place
    // so a simulator location crossing fires didEnterRegion → the pass. The real path is the settings
    // toggle (S4). Slight delay lets the MainViewController seed the ContentStore first.
    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
      IosNotifGlue.shared.debugEnableProximity()
    }
    return true
  }

  func applicationDidEnterBackground(_ application: UIApplication) {
    submitReconcile()
  }

  private func submitReconcile() {
    let request = BGAppRefreshTaskRequest(identifier: bgTaskId)
    request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
    try? BGTaskScheduler.shared.submit(request)
  }
}
