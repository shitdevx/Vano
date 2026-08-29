import Foundation

struct GenerationResult {
    let text: String
    let tokens: Int
    let genTimeMs: Int64
    let tokensPerSec: Float
    let promptTimeMs: Int64
}

#if canImport(llama)
import llama

class InferenceManager {
    private var model: UnsafeMutablePointer<llama_model>?
    private var ctx: UnsafeMutablePointer<llama_context>?
    private var loadedPath: String?
    private var sampler: UnsafeMutablePointer<llama_sampler>?

    func ensureLoaded(fileURL: URL) async -> Bool {
        if loadedPath == fileURL.path, model != nil, ctx != nil { return true }
        unload()
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return false }
        return await withCheckedContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                llama_backend_init()
                var mparams = llama_model_default_params()
                guard let mdl = llama_model_load_from_file(fileURL.path, mparams) else {
                    cont.resume(returning: false)
                    return
                }
                var cparams = llama_context_default_params()
                cparams.n_ctx = 8192
                cparams.n_threads = Int32(max(2, min(6, ProcessInfo.processInfo.processorCount)))
                cparams.n_threads_batch = cparams.n_threads
                guard let context = llama_init_from_model(mdl, cparams) else {
                    llama_model_free(mdl)
                    cont.resume(returning: false)
                    return
                }
                let smpl = llama_sampler_chain_init(llama_sampler_chain_default_params())
                llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.1))
                llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9, 1))
                llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40))
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
        guard let mdl = model, let context = ctx, let smpl = sampler else {
            throw NSError(domain: "Llama", code: 1, userInfo: [NSLocalizedDescriptionKey: "Model not loaded"])
        }
        let system = "You are Vano, a helpful AI assistant created by the Vamora Project. Be friendly, concise, and helpful. If the user says hi/hello, greet them warmly and ask how you can help — don't ask them to tell you about Vano."
        let fullPrompt = buildPrompt(history: history, current: prompt, system: system)
        let vocab = llama_model_get_vocab(mdl)
        let start = Date()
        return try await withCheckedThrowingContinuation { cont in
            DispatchQueue.global(qos: .userInitiated).async {
                let nCtx = Int(llama_n_ctx(context))
                var tokens = [llama_token](repeating: 0, count: fullPrompt.utf8.count + 1024)
                let nTokens = fullPrompt.withCString { ptr in
                    llama_tokenize(mdl, ptr, Int32(strlen(ptr)), &tokens, Int32(tokens.count), true, true)
                }
                guard nTokens > 0 else {
                    cont.resume(throwing: NSError(domain: "Llama", code: 2, userInfo: [NSLocalizedDescriptionKey: "Tokenize failed"]))
                    return
                }
                tokens = Array(tokens.prefix(Int(nTokens)))
                if tokens.count > nCtx - 512 {
                    tokens = Array(tokens.suffix(nCtx - 512))
                }
                let promptStart = Date()
                var batch = llama_batch_init(Int32(tokens.count), 0, 1)
                defer { llama_batch_free(batch) }
                for (i, tok) in tokens.enumerated() {
                    batch.token[i] = tok
                    batch.pos[i] = Int32(i)
                    batch.n_seq_id[i] = 1
                    let seqIdPtr = UnsafeMutablePointer<llama_seq_id>.allocate(capacity: 1)
                    seqIdPtr[0] = 0
                    batch.seq_id[i] = seqIdPtr
                    batch.logits[i] = (i == tokens.count - 1) ? 1 : 0
                }
                batch.n_tokens = Int32(tokens.count)
                if llama_decode(context, batch) != 0 {
                    cont.resume(throwing: NSError(domain: "Llama", code: 3, userInfo: [NSLocalizedDescriptionKey: "Decode failed"]))
                    return
                }
                let promptMs = Int64(Date().timeIntervalSince(promptStart) * 1000)
                var generated = 0
                var output = ""
                var nCur = tokens.count
                let maxTokens = 200
                let genStart = Date()
                while generated < maxTokens {
                    let tok = llama_sampler_sample(smpl, context, -1)
                    if llama_vocab_is_eog(vocab, tok) { break }
                    let bufLen = 64
                    var buf = [CChar](repeating: 0, count: bufLen)
                    let len = llama_token_to_piece(mdl, tok, &buf, Int32(bufLen), 0, true)
                    if len > 0 { output += String(cString: buf) }
                    generated += 1
                    var nextBatch = llama_batch_init(1, 0, 1)
                    nextBatch.token[0] = tok
                    nextBatch.pos[0] = Int32(nCur)
                    nextBatch.n_seq_id[0] = 1
                    let seqPtr = UnsafeMutablePointer<llama_seq_id>.allocate(capacity: 1)
                    seqPtr[0] = 0
                    nextBatch.seq_id[0] = seqPtr
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
                _ = totalMs
            }
        }
    }

    private func buildPrompt(history: [ChatMessage], current: String, system: String) -> String {
        var chat: String = ""
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
        chat += "User: \(current)\nAssistant: "
        return chat
    }
}

#else

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
            text = "I've analyzed your attached file (up to 8192 tokens). Here's a concise summary in **markdown**:\n\n- **Key points** extracted\n- `\(prompt.prefix(60))`\n\nLet me know if you want more detail!"
        } else {
            text = "Vano here — you asked: \"\(prompt.prefix(80))\"\n\nThis is a *simulated* on-device reply. Add `llama` XCFramework and rebuild on macOS to get real GGUF streaming with `temp 0.1 topP 0.9 topK 40 repeat 1.5 presence 0.5 frequency 0.5 context 8192 maxTokens 200`."
        }
        let elapsed = Int64(Date().timeIntervalSince(start) * 1000)
        let tokens = max(12, text.split(separator: " ").count)
        let tps: Float = elapsed > 0 ? Float(tokens) / (Float(elapsed)/1000) : 27.0
        return GenerationResult(text: text, tokens: tokens, genTimeMs: elapsed, tokensPerSec: tps, promptTimeMs: 120)
    }
}
#endif
