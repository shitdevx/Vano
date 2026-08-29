import Foundation

enum ModelId: String, CaseIterable, Identifiable {
    case vano = "vano-Q4_0.gguf"
    case vanoMini = "vano-mini-Q3_K_M.gguf"
    var id: String { rawValue }
    var displayName: String {
        switch self {
        case .vano: return "Vano"
        case .vanoMini: return "Vano mini"
        }
    }
    var fileName: String { rawValue }
    var url: URL {
        switch self {
        case .vano: return URL(string: "https://github.com/TheVamoraProject/Vano/releases/download/1.0/vano-Q4_0.gguf")!
        case .vanoMini: return URL(string: "https://github.com/TheVamoraProject/Vano/releases/download/mini/vano-mini-Q3_K_M.gguf")!
        }
    }
    var sizeLabel: String {
        switch self {
        case .vano: return "1.55 GB"
        case .vanoMini: return "1.38 GB"
        }
    }
    var description: String {
        switch self {
        case .vano: return "Main model • Best quality • Recommended"
        case .vanoMini: return "Lightweight • Faster • Lower RAM"
        }
    }
    var tag: String {
        switch self {
        case .vano: return "vano Q4_0"
        case .vanoMini: return "vano Q3_K_M"
        }
    }
}

struct Chat: Identifiable, Codable, Equatable {
    var id: String
    var title: String
    var createdAt: Date
    var updatedAt: Date
    var messages: [ChatMessage]
    var preview: String { messages.last?.content.prefix(60).description ?? "No messages yet" }
}

struct ChatMessage: Identifiable, Codable, Equatable {
    var id: String
    var role: String // "user" or "assistant"
    var content: String
    var timestamp: Date
    var modelName: String? = nil
    var tokens: Int? = nil
    var genTimeMs: Int64? = nil
    var tokensPerSec: Float? = nil
    var attachmentName: String? = nil
    var attachmentText: String? = nil
}
