import Foundation
import Combine

@MainActor
class ChatStore: ObservableObject {
    @Published var chats: [Chat] = []
    private var fileURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("chats.json")
    }
    func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([Chat].self, from: data) else { chats = []; return }
        chats = decoded
    }
    private func persist() {
        if let data = try? JSONEncoder().encode(chats) { try? data.write(to: fileURL) }
    }
    func createChat() -> Chat {
        let now = Date()
        let chat = Chat(id: UUID().uuidString, title: "New chat", createdAt: now, updatedAt: now, messages: [])
        chats.insert(chat, at: 0)
        persist()
        return chat
    }
    func deleteChat(id: String) { chats.removeAll { $0.id == id }; persist() }
    func deleteMessage(chatId: String, messageId: String) {
        guard let idx = chats.firstIndex(where: { $0.id == chatId }) else { return }
        chats[idx].messages.removeAll { $0.id == messageId }
        chats[idx].updatedAt = Date()
        persist()
    }
    func updateMessage(chatId: String, messageId: String, content: String, modelName: String? = nil, tokens: Int? = nil, genTimeMs: Int64? = nil, tps: Float? = nil) {
        guard let cIdx = chats.firstIndex(where: { $0.id == chatId }),
              let mIdx = chats[cIdx].messages.firstIndex(where: { $0.id == messageId }) else { return }
        chats[cIdx].messages[mIdx].content = content
        if let m = modelName { chats[cIdx].messages[mIdx].modelName = m }
        if let t = tokens { chats[cIdx].messages[mIdx].tokens = t }
        if let g = genTimeMs { chats[cIdx].messages[mIdx].genTimeMs = g }
        if let s = tps { chats[cIdx].messages[mIdx].tokensPerSec = s }
        chats[cIdx].updatedAt = Date()
        persist()
    }
    func addMessage(chatId: String, message: ChatMessage, newTitle: String? = nil) {
        guard let idx = chats.firstIndex(where: { $0.id == chatId }) else { return }
        chats[idx].messages.append(message)
        if chats[idx].title == "New chat", let t = newTitle { chats[idx].title = String(t.prefix(40)) }
        chats[idx].updatedAt = Date()
        persist()
    }
    func chat(for id: String) -> Chat? { chats.first { $0.id == id } }
    func clearAll() { chats = []; try? FileManager.default.removeItem(at: fileURL) }
}
