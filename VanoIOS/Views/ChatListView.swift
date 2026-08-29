import SwiftUI

struct ChatListView: View {
    @EnvironmentObject var vm: VanoViewModel
    @State private var query = ""
    @State private var showDelete: String?
    var filtered: [Chat] {
        if query.isEmpty { return vm.chatStore.chats }
        return vm.chatStore.chats.filter { $0.title.localizedCaseInsensitiveContains(query) || $0.preview.localizedCaseInsensitiveContains(query) }
    }
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                TextField("Search chats", text: $query)
                    .textFieldStyle(RoundedBorderTextFieldStyle()).padding(16)
                if filtered.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bubble.left").font(.largeTitle).foregroundColor(.gray)
                        Text(query.isEmpty ? "No chats yet" : "No results for \"\(query)\"").foregroundColor(.gray)
                        if query.isEmpty { Text("Tap + to start a new conversation").font(.caption).foregroundColor(.gray) }
                    }.frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(filtered) { chat in
                            NavigationLink(destination: ChatView(chatId: chat.id)) {
                                HStack(spacing: 12) {
                                    RoundedRectangle(cornerRadius: 10).fill(Color(red: 0x14/255.0, green: 0x53/255.0, blue: 0x2D/255.0)).frame(width: 42, height: 42).overlay(Image(systemName: "bubble.left.fill").foregroundColor(.white))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(chat.title).font(.subheadline).bold().lineLimit(1)
                                        Text(chat.preview).font(.caption).foregroundColor(.gray).lineLimit(1)
                                        Text(chat.updatedAt, style: .date).font(.caption2).foregroundColor(.gray)
                                    }
                                    Spacer()
                                    Button(role: .destructive) { showDelete = chat.id } label: { Image(systemName: "trash").foregroundColor(.gray) }
                                }
                            }.listRowBackground(Color(red: 0x12/255.0, green: 0x14/255.0, blue: 0x12/255.0))
                        }
                    }.listStyle(PlainListStyle())
                }
            }
            .navigationTitle("Vano").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink(destination: SettingsView()) { Image(systemName: "gearshape") }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { _ = vm.createChat() } label: { Image(systemName: "plus") }
                }
            }
            .background(Color(red: 0x0A/255.0, green: 0x0F/255.0, blue: 0x0A/255.0).ignoresSafeArea())
        }
        .alert("Delete chat?", isPresented: .constant(showDelete != nil)) {
            Button("Delete", role: .destructive) { if let id = showDelete { vm.deleteChat(id: id); showDelete = nil } }
            Button("Cancel", role: .cancel) { showDelete = nil }
        } message: { Text("This will permanently delete the conversation.") }
    }
}
