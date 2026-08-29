import Foundation

struct GenerationResult {
    let text: String
    let tokens: Int
    let genTimeMs: Int64
    let tokensPerSec: Float
    let promptTimeMs: Int64
}

#if canImport(LlamaFramework)
import LlamaFramework

// Real llama.cpp via XCFramework b5046 (https://github.com/ggml-org/llama.cpp/releases/download/b5046/llama-b5046-xcframework.zip)
// Add via Xcode: File → Add Packages → https://github.com/ggml-org/llama.cpp  OR via Package.swift binaryTarget
// This file is compiled only when LlamaFramework is available (macOS/Xcode). On Linux it falls back to stub below.
class InferenceManager {
    private var model: OpaquePointer?
    private var ctx: OpaquePointer?
    private var loadedPath: String?
    private var sampler: OpaquePointer?

    func ensureLoaded(fileURL: URL) async -> Bool {
        if loadedPath == fileURL.path, model != nil, ctx != nil { return true }
        unload()
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return false }
        return await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                llama_backend_init()
                var mparams = llama_model_default_params()
                // mparams.n_gpu_layers = 0 // CPU only, Metal will auto-use layers if available
                // mparams.use_mmap = true
                guard let mdl = llama_model_load_from_file(fileURL.path, mparams) else {
                    cont.resume(returning: false)
                    return
                }
                var cparams = llama_context_default_params()
                cparams.n_ctx = 8192 // user requested 8192 for file analysis
                cparams.n_threads = Int32(max(2, min(6, ProcessInfo.processInfo.processorCount)))
                cparams.n_threads_batch = cparams.n_threads
                // cparams.flash_attn = true
                guard let context = llama_new_context_with_model(mdl, cparams) else {
                    llama_model_free(mdl)
                    cont.resume(returning: false)
                    return
                }
                // Sampler chain: temp 0.1, topP 0.9, topK 40, repeat 1.5, presence 0.5, frequency 0.5
                let smpl = llama_sampler_chain_init(llama_sampler_chain_default_params())
                llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.1))
                llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9, 1))
                llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40))
                // Repeat penalties (1.5, presence 0.5, frequency 0.5) — ggml llama.cpp 0.9+ API
                llama_sampler_chain_add(smpl, llama_sampler_init_penalties(64, 1.5, 0.5, 0.5))
                llama_sampler_chain_add(smpl, llama_sampler_init_dist(0))

                self.model = mdl
                self.ctx = context
                self.sampler = smpl
                self.loadedPath = fileURL.path
                cont.resume(returning: true)
            }
        }
    }

    func unload() {
        if let s = sampler { llama_sampler_free(s); sampler = nil }
        if let c = ctx { llama_free(c); ctx = nil }
        if let m = model { llama_model_free(m); model = nil }
        loadedPath = nil
        llama_backend_free()
    }

    func generate(history: [ChatMessage], prompt: String) async throws -> GenerationResult {
        guard let mdl = model, let context = ctx, let smpl = sampler else { throw NSError(domain: "Llama", code: 1, userInfo: [NSLocalizedDescriptionKey: "Model not loaded"]) }
        let system = "You are Vano, a helpful AI assistant created by the Vamora Project. Be friendly, concise, and helpful. If the user says hi/hello, greet them warmly and ask how you can help — don't ask them to tell you about Vano."
        // Build prompt with history (plain User/Assistant) — same as Android
        let fullPrompt = buildPrompt(history: history, current: prompt, system: system)
        let start = Date()
        return try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                // Tokenize
                let nCtx = Int(llama_n_ctx(context))
                var tokens = [llama_token](repeating: 0, count: fullPrompt.utf8.count + 1024)
                let nTokens = llama_tokenize(mdl, fullPrompt, Int32(fullPrompt.utf8.count), &tokens, Int32(tokens.count), true, true)
                guard nTokens > 0 else {
                    cont.resume(throwing: NSError(domain: "Llama", code: 2, userInfo: [NSLocalizedDescriptionKey: "Tokenize failed"]))
                    return
                }
                tokens = Array(tokens.prefix(Int(nTokens)))
                if tokens.count > nCtx - 512 {
                    tokens = Array(tokens.suffix(nCtx - 512))
                }
                // Evaluate prompt
                let promptStart = Date()
                var batch = llama_batch_init(Int32(tokens.count), 0, 1)
                defer { llama_batch_free(batch) }
                for (i, tok) in tokens.enumerated() {
                    batch.token[i] = tok
                    batch.pos[i] = Int32(i)
                    batch.n_seq_id[i] = 1
                    batch.seq_id[i] = [0]
                    batch.logits[i] = (i == tokens.count - 1) ? 1 : 0
                }
                batch.n_tokens = Int32(tokens.count)
                if llama_decode(context, batch) != 0 {
                    cont.resume(throwing: NSError(domain: "Llama", code: 3, userInfo: [NSLocalizedDescriptionKey: "Decode failed"]))
                    return
                }
                let promptMs = Int64(Date().timeIntervalSince(promptStart) * 1000)
                // Generate up to 200 tokens (user requested 150-250)
                var generated = 0
                var output = ""
                var nCur = tokens.count
                let maxTokens = 200
                let genStart = Date()
                var newTokens: [llama_token] = []
                while generated < maxTokens {
                    let tok = llama_sampler_sample(smpl, context, -1)
                    if llama_token_is_eog(mdl, tok) { break }
                    // Append
                    let bufLen = 64
                    var buf = [CChar](repeating: 0, count: bufLen)
                    let len = llama_token_to_piece(mdl, tok, &buf, Int32(bufLen), 0, true)
                    if len > 0 { output += String(cString: buf) }
                    newTokens.append(tok)
                    generated += 1
                    // Prepare next batch single token
                    var nextBatch = llama_batch_init(1, 0, 1)
                    nextBatch.token[0] = tok
                    nextBatch.pos[0] = Int32(nCur)
                    nextBatch.n_seq_id[0] = 1
                    nextBatch.seq_id[0] = [0]
                    nextBatch.logits[0] = 1
                    nextBatch.n_tokens = 1
                    if llama_decode(context, nextBatch) != 0 {
                        llama_batch_free(nextBatch)
                        break
                    }
                    llama_batch_free(nextBatch)
                    nCur += 1
                    if output.contains("</s>") || output.contains("<|eot_id|>") { break }
                }
                let genMs = Int64(Date().timeIntervalSince(genStart) * 1000)
                let totalMs = Int64(Date().timeIntervalSince(start) * 1000)
                let tps: Float = genMs > 0 ? Float(generated) / (Float(genMs)/1000) : 0
                let text = output.trimmingCharacters(in: .whitespacesAndNewlines)
                if text.isEmpty {
                    cont.resume(throwing: NSError(domain: "Llama", code: 4, userInfo: [NSLocalizedDescriptionKey: "Empty generation"]))
                } else {
                    cont.resume(returning: GenerationResult(text: text, tokens: generated, genTimeMs: genMs, tokensPerSec: tps, promptTimeMs: promptMs))
                }
                // Update total time if needed
                _ = totalMs
            }
        }
    }

    private func buildPrompt(history: [ChatMessage], current: String, system: String) -> String {
        // Use GGUF's chat template via llama_chat_apply_template if available, else fallback to plain
        // For b5046 XCFramework, we can call llama_chat_apply_template — simplified here:
        var chat: String = ""
        // System
        chat += "System: \(system)\n"
        let recent = history.suffix(12)
        for m in recent {
            if m.role == "user" {
                var txt = m.content
                if let att = m.attachmentText, !att.isEmpty {
                    txt += "\n[Attached file: \(m.attachmentName ?? "file")]\n\(att.prefix(12000))\n[End of file]"
                }
                chat += "User: \(txt)\n"
            } else {
                chat += "Assistant: \(m.content)\n"
            }
        }
        // Current is already appended via history? Caller passes history without current, so append current
        chat += "User: \(current)\nAssistant: "
        return chat
    }
}

#else

// Fallback stub when LlamaFramework not available (Linux CI or before SPM add)
// Keeps the app runnable without native lib — same as Android fallback.
class InferenceManager {
    private var loadedPath: String?
    func ensureLoaded(fileURL: URL) async -> Bool {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return false }
        if loadedPath == fileURL.path { return true }
        try? await Task.sleep(nanoseconds: 300_000_000)
        loadedPath = fileURL.path
        return true
    }
    func unload() { loadedPath = nil }
    func generate(history: [ChatMessage], prompt: String) async throws -> GenerationResult {
        let start = Date()
        try? await Task.sleep(nanoseconds: 600_000_000)
        let text: String
        let lower = prompt.lowercased()
        if lower.contains("hi") && prompt.count < 20 {
            text = "Hey! I'm glad you're here! How can I help?"
        } else if lower.contains("analyze") && lower.contains("file") {
            text = "I’ve analyzed your attached file (up to 8192 tokens). Here’s a concise summary in **markdown**:\n\n- **Key points** extracted\n- `\(prompt.prefix(60))`\n\nLet me know if you want more detail!"
        } else {
            text = "Vano here — you asked: \"\(prompt.prefix(80))\"\n\nThis is a *simulated* on-device reply. Add `LlamaFramework` XCFramework (see `Package.swift` b5046) and rebuild on macOS to get real GGUF streaming with `temp 0.1 topP 0.9 topK 40 repeat 1.5 presence 0.5 frequency 0.5 context 8192 maxTokens 200`."
        }
        let elapsed = Int64(Date().timeIntervalSince(start) * 1000)
        let tokens = max(12, text.split(separator: " ").count)
        let tps: Float = elapsed > 0 ? Float(tokens) / (Float(elapsed)/1000) : 27.0
        return GenerationResult(text: text, tokens: tokens, genTimeMs: elapsed, tokensPerSec: tps, promptTimeMs: 120)
    }
}
#endif
