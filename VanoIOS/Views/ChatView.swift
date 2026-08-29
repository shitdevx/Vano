import SwiftUI
import UniformTypeIdentifiers

struct ChatView: View {
    var chatId: String
    @EnvironmentObject var vm: VanoViewModel
    @Environment(\.dismiss) var dismiss
    @State private var input = ""
    @State private var attachedName: String?
    @State private var attachedText: String?
    @State private var showImporter = false
    var chat: Chat? { vm.chatStore.chats.first { $0.id == chatId } }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        if let chat = chat, chat.messages.isEmpty {
                            VStack(spacing: 6) {
                                Text("Start the conversation").font(.headline).foregroundColor(.gray)
                                Text("Ask anything — Vano is running locally on your device.").font(.caption).foregroundColor(.gray)
                            }.padding(32)
                        } else if let msgs = chat?.messages {
                            ForEach(msgs) { msg in
                                let isUser = msg.role == "user"
                                let isPlaceholder = msg.content == "▌" && !isUser
                                VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                                    HStack {
                                        if isUser { Spacer() }
                                        VStack(alignment: .leading, spacing: 6) {
                                            if let att = msg.attachmentName {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "doc.text").font(.caption2)
                                                    Text(att).font(.caption2).bold().lineLimit(1)
                                                }
                                                .padding(.horizontal, 8).padding(.vertical, 4)
                                                .background(isUser ? Color.white.opacity(0.15) : Color.gray.opacity(0.2))
                                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                                if !msg.content.isEmpty { Divider() }
                                            }
                                            if isPlaceholder {
                                                HStack(spacing: 8) {
                                                    ProgressView().scaleEffect(0.7)
                                                    Text("Thinking…").font(.subheadline).foregroundColor(.gray)
                                                }
                                            } else {
                                                if isUser {
                                                    Text(msg.content).foregroundColor(.black).font(.body)
                                                } else {
                                                    // Markdown via AttributedString (iOS 15+)
                                                    if let attr = try? AttributedString(markdown: msg.content, options: AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
                                                        Text(attr).foregroundColor(.white)
                                                    } else {
                                                        Text(msg.content).foregroundColor(.white)
                                                    }
                                                }
                                            }
                                        }
                                        .padding(12)
                                        .background(isUser ? Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0) : Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0))
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                        .frame(maxWidth: 340, alignment: isUser ? .trailing : .leading)
                                        if !isUser { Spacer() }
                                    }
                                    if let att = msg.attachmentName, !isPlaceholder {
                                        Text("\(att) • \(msg.attachmentText?.count ?? 0) chars").font(.system(size: 10)).foregroundColor(.gray)
                                            .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
                                    }
                                    if !isPlaceholder {
                                        if !isUser, let model = msg.modelName {
                                            HStack(spacing: 6) {
                                                HStack(spacing: 4) {
                                                    Image(systemName: "cpu").font(.caption2)
                                                    Text(model.components(separatedBy: " ").first ?? model).font(.caption2)
                                                    if model.contains(" ") {
                                                        Text(model.components(separatedBy: " ").dropFirst().joined(separator: " ")).font(.caption2).bold().padding(.horizontal, 4).background(Color.green.opacity(0.2)).clipShape(RoundedRectangle(cornerRadius: 4))
                                                    }
                                                }
                                                .padding(.horizontal, 6).padding(.vertical, 3).background(Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0)).clipShape(RoundedRectangle(cornerRadius: 6))
                                                if let t = msg.tokens { Label("\(t) tokens", systemImage: "memorychip").font(.caption2).foregroundColor(.gray) }
                                                if let ms = msg.genTimeMs { Label(String(format: "%.1fs", Double(ms)/1000), systemImage: "clock").font(.caption2).foregroundColor(.gray) }
                                                if let tps = msg.tokensPerSec { Label(String(format: "%.2f t/s", tps), systemImage: "speedometer").font(.caption2).foregroundColor(.gray) }
                                            }
                                        }
                                        HStack(spacing: 8) {
                                            Button { UIPasteboard.general.string = msg.content } label: { Image(systemName: "doc.on.doc").font(.caption) }.tint(.gray)
                                            Button(role: .destructive) { vm.deleteMessage(chatId: chatId, messageId: msg.id) } label: { Image(systemName: "trash").font(.caption) }.tint(.gray)
                                        }
                                        .frame(maxWidth: .infinity, alignment: isUser ? .trailing : .leading)
                                    }
                                }
                                .id(msg.id)
                            }
                            if vm.isGenerating {
                                HStack {
                                    RoundedRectangle(cornerRadius: 16).fill(Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0)).frame(width: 120, height: 40).overlay(HStack(spacing: 8){ ProgressView().scaleEffect(0.7); Text("Generating…").font(.caption).foregroundColor(.gray) })
                                    Spacer()
                                }
                            }
                        }
                    }.padding(12)
                }.onChange(of: chat?.messages.count) { _ in
                    if let last = chat?.messages.last?.id { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
                }
            }
            Divider().background(Color.gray.opacity(0.2))
            if let name = attachedName {
                HStack {
                    Label(name, systemImage: "doc.text").font(.caption).foregroundColor(.white)
                    Text("\(attachedText?.count ?? 0) chars • 8192 ctx").font(.caption2).foregroundColor(.gray)
                    Spacer()
                    Button { attachedName = nil; attachedText = nil } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.gray) }
                }.padding(.horizontal, 10).padding(.top, 6)
            }
            HStack(alignment: .bottom, spacing: 8) {
                Button { showImporter = true } label: { Image(systemName: "paperclip").foregroundColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)) }.disabled(vm.isGenerating)
                TextField(attachedName != nil ? "Ask about file..." : "Message...", text: $input, axis: .vertical).lineLimit(4).padding(10).background(Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0)).clipShape(RoundedRectangle(cornerRadius: 22)).foregroundColor(.white)
                Button {
                    let txt = input.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !txt.isEmpty || attachedName != nil { vm.sendMessage(chatId: chatId, text: txt, fileName: attachedName, fileText: attachedText); input=""; attachedName=nil; attachedText=nil }
                } label: {
                    if vm.isGenerating { ProgressView().tint(.white) } else { Image(systemName: "paperplane.fill").foregroundColor(.black) }
                }
                .frame(width: 48, height: 48).background(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)).clipShape(Circle()).disabled(vm.isGenerating || (input.isEmpty && attachedName==nil))
            }.padding(10)
        }
        .navigationTitle(chat?.title ?? "Chat").navigationBarTitleDisplayMode(.inline)
        .background(Color(red: 0x0A/255.0, green: 0x0F/255.0, blue: 0x0A/255.0).ignoresSafeArea())
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.plainText, .json, .commaSeparatedText, .pdf, .markdown], allowsMultipleSelection: false) { result in
            guard let url = try? result.get().first else { return }
            if url.pathExtension.lowercased() == "png" || url.pathExtension.lowercased() == "jpg" || url.pathExtension.lowercased() == "jpeg" {
                // Block photos – text-only
                return
            }
            guard url.startAccessingSecurityScopedResource() else { return }
            defer { url.stopAccessingSecurityScopedResource() }
            if let data = try? Data(contentsOf: url), let text = String(data: data, encoding: .utf8) {
                attachedName = url.lastPathComponent
                attachedText = String(text.prefix(15000))
            }
        }
    }
}
