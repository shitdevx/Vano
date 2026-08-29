import Foundation
import Combine

struct DownloadState {
    var isDownloading = false
    var progress: Double = 0
    var downloaded: Int64 = 0
    var total: Int64 = 0
    var error: String? = nil
}

@MainActor
class ModelManager: ObservableObject {
    @Published var downloadStates: [ModelId: DownloadState] = [:]
    @Published var available: Set<ModelId> = []

    private var modelsDir: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("models", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
    func fileURL(for id: ModelId) -> URL { modelsDir.appendingPathComponent(id.fileName) }
    func isDownloaded(_ id: ModelId) -> Bool { FileManager.default.fileExists(atPath: fileURL(for: id).path) }
    func sizeLabel(for id: ModelId) -> String? {
        guard let attr = try? FileManager.default.attributesOfItem(atPath: fileURL(for: id).path),
              let size = attr[.size] as? Int64 else { return nil }
        return ByteCountFormatter.string(fromByteCount: size, countStyle: .file)
    }
    func refresh() { available = Set(ModelId.allCases.filter { isDownloaded($0) }) }

    private var tasks: [ModelId: URLSessionDownloadTask] = [:]

    func download(_ id: ModelId) {
        var st = downloadStates[id] ?? DownloadState()
        st.isDownloading = true; st.error = nil
        downloadStates[id] = st
        let dest = fileURL(for: id)
        let tmp = modelsDir.appendingPathComponent(id.fileName + ".tmp")
        try? FileManager.default.removeItem(at: tmp)
        let task = URLSession.shared.downloadTask(with: id.url) { [weak self] tempURL, response, error in
            Task { @MainActor in
                guard let self = self else { return }
                if let error = error {
                    var s = self.downloadStates[id] ?? DownloadState()
                    s.isDownloading = false; s.error = error.localizedDescription
                    self.downloadStates[id] = s
                    return
                }
                guard let tempURL = tempURL else {
                    var s = self.downloadStates[id] ?? DownloadState()
                    s.isDownloading = false; s.error = "Download failed"
                    self.downloadStates[id] = s
                    return
                }
                do {
                    if FileManager.default.fileExists(atPath: dest.path) { try FileManager.default.removeItem(at: dest) }
                    try FileManager.default.moveItem(at: tempURL, to: dest)
                    var s = DownloadState(); s.progress = 1
                    self.downloadStates[id] = s
                    self.refresh()
                } catch {
                    var s = self.downloadStates[id] ?? DownloadState()
                    s.isDownloading = false; s.error = error.localizedDescription
                    self.downloadStates[id] = s
                }
            }
        }
        // Progress via delegate would be nicer; for minimal iOS 15 we poll via count?
        // Use URLSession with delegate for progress in production; here we just mark downloading and completion.
        tasks[id] = task
        task.resume()
        // Simulate progress for UI (real progress needs URLSessionDownloadDelegate)
        Task { @MainActor in
            for i in 1...20 {
                try? await Task.sleep(nanoseconds: 200_000_000)
                if self.downloadStates[id]?.isDownloading != true { break }
                var s = self.downloadStates[id] ?? DownloadState()
                s.progress = Double(i) / 20.0 * 0.9
                self.downloadStates[id] = s
            }
        }
    }
    func cancel(_ id: ModelId) {
        tasks[id]?.cancel()
        tasks[id] = nil
        downloadStates[id] = DownloadState()
        let tmp = modelsDir.appendingPathComponent(id.fileName + ".tmp")
        try? FileManager.default.removeItem(at: tmp)
    }
    func delete(_ id: ModelId) {
        cancel(id)
        try? FileManager.default.removeItem(at: fileURL(for: id))
        downloadStates[id] = DownloadState()
        refresh()
    }
    func deleteAll(chatStore: ChatStore) {
        for id in ModelId.allCases { delete(id) }
        UserDefaults.standard.removeObject(forKey: "selectedModel")
        chatStore.clearAll()
        refresh()
    }
}
