import Foundation
import Combine

@MainActor
class VanoViewModel: ObservableObject {
    @Published var selectedModel: String? = UserDefaults.standard.string(forKey: "selectedModel")
    @Published var isGenerating = false
    let modelManager = ModelManager()
    let chatStore = ChatStore()
    private let inference = InferenceManager()

    init() {
        chatStore.load()
        modelManager.refresh()
        if selectedModel == nil, let first = modelManager.available.first {
            selectedModel = first.fileName
            UserDefaults.standard.set(first.fileName, forKey: "selectedModel")
        }
        // Observe available to auto-select
    }

    func setSelectedModel(_ id: ModelId) {
        Task { await inference.unload() }
        selectedModel = id.fileName
        UserDefaults.standard.set(id.fileName, forKey: "selectedModel")
    }
    func createChat() -> Chat { chatStore.createChat() }
    func deleteChat(id: String) { chatStore.deleteChat(id: id) }
    func deleteMessage(chatId: String, messageId: String) { chatStore.deleteMessage(chatId: chatId, messageId: messageId) }
    func deleteAll() {
        Task { await inference.unload() }
        modelManager.deleteAll(chatStore: chatStore)
        selectedModel = nil
    }

    func sendMessage(chatId: String, text: String, fileName: String? = nil, fileText: String? = nil) {
        let displayTitle = text.isEmpty && fileText != nil ? "Attached \(fileName ?? "file")" : text
        let userMsg = ChatMessage(id: UUID().uuidString, role: "user", content: text.isEmpty && fileText != nil ? "[File: \(fileName ?? "file")]" : text, timestamp: Date(), attachmentName: fileName, attachmentText: fileText?.prefix(15000).description)
        chatStore.addMessage(chatId: chatId, message: userMsg, newTitle: String(displayTitle.prefix(40)))
        guard let sel = selectedModel, let modelId = ModelId(rawValue: sel), modelManager.isDownloaded(modelId) else {
            let err = ChatMessage(id: UUID().uuidString, role: "assistant", content: "No model selected. Go to Settings and select a downloaded model.", timestamp: Date())
            chatStore.addMessage(chatId: chatId, message: err)
            return
        }
        isGenerating = true
        let placeholderId = UUID().uuidString
        let placeholder = ChatMessage(id: placeholderId, role: "assistant", content: "▌", timestamp: Date())
        chatStore.addMessage(chatId: chatId, message: placeholder)
        Task {
            let fileURL = modelManager.fileURL(for: modelId)
            let loaded = await inference.ensureLoaded(fileURL: fileURL)
            guard loaded else {
                chatStore.updateMessage(chatId: chatId, messageId: placeholderId, content: "Error: Failed to load GGUF. Try Vano mini.")
                isGenerating = false
                return
            }
            let history = chatStore.chat(for: chatId)?.messages.filter { $0.id != placeholderId && $0.id != userMsg.id } ?? []
            let promptWithFile: String
            if let ft = fileText, !ft.isEmpty {
                promptWithFile = "\(text.isEmpty ? "Analyze this file" : text)\n\n[Attached file: \(fileName ?? "file")]\n\(ft.prefix(12000))\n[End of file]"
            } else { promptWithFile = text }
            do {
                let res = try await inference.generate(history: history, prompt: promptWithFile)
                chatStore.updateMessage(chatId: chatId, messageId: placeholderId, content: res.text, modelName: modelId.tag, tokens: res.tokens, genTimeMs: res.genTimeMs, tps: res.tokensPerSec)
            } catch {
                let fallback = "\(modelId.displayName) (fallback) — you said: \"\(text)\"\n\nOn-device inference failed: \(error.localizedDescription)"
                chatStore.updateMessage(chatId: chatId, messageId: placeholderId, content: fallback, modelName: modelId.tag)
            }
            isGenerating = false
        }
    }
}
