import SwiftUI

@main
struct VanoIOSApp: App {
    @StateObject private var vm = VanoViewModel()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(vm)
                .preferredColorScheme(.dark)
                .accentColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
        }
    }
}
