import SwiftUI

struct ContentView: View {
    @EnvironmentObject var vm: VanoViewModel
    var body: some View {
        Group {
            if vm.chatStore.chats.isEmpty && vm.modelManager.available.isEmpty {
                // Check if we should show download: if available empty, show download regardless of chats
                // Use separate logic: if available empty -> download
                if vm.modelManager.available.isEmpty {
                    DownloadView()
                } else {
                    TabView {
                        ChatListView().tabItem { Label("Chats", systemImage: "bubble.left.fill") }
                        SettingsView().tabItem { Label("Settings", systemImage: "gearshape.fill") }
                    }.accentColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
                }
            } else if vm.modelManager.available.isEmpty {
                DownloadView()
            } else {
                TabView {
                    ChatListView().tabItem { Label("Chats", systemImage: "bubble.left.fill") }
                    SettingsView().tabItem { Label("Settings", systemImage: "gearshape.fill") }
                }.accentColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
            }
        }
        .background(Color(red: 0x0A/255.0, green: 0x0F/255.0, blue: 0x0A/255.0).ignoresSafeArea())
    }
}
