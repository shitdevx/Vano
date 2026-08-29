import SwiftUI

struct DownloadView: View {
    @EnvironmentObject var vm: VanoViewModel
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Spacer().frame(height: 24)
                RoundedRectangle(cornerRadius: 20).fill(Color(red: 0x12/255.0, green: 0x14/255.0, blue: 0x12/255.0))
                    .frame(width: 84, height: 84)
                    .overlay(Image("VanoLogo").resizable().scaledToFit().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 12)))
                Text("Welcome to Vano").font(.title2).bold().foregroundColor(.white)
                Text("Choose a model to download. You can change it later in Settings.").font(.subheadline).foregroundColor(.gray).multilineTextAlignment(.center).padding(.horizontal, 12)
                ForEach(ModelId.allCases) { model in
                    let state = vm.modelManager.downloadStates[model]
                    let isDownloading = state?.isDownloading ?? false
                    let progress = state?.progress ?? 0
                    let downloaded = vm.modelManager.isDownloaded(model)
                    let isSelected = vm.selectedModel == model.fileName
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text(model.displayName).bold().foregroundColor(.white)
                                    if downloaded { Image(systemName: "checkmark.circle.fill").foregroundColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)) }
                                }
                                Text(model.sizeLabel).font(.caption).foregroundColor(.gray)
                                Text(model.description).font(.caption2).foregroundColor(.gray)
                                if downloaded, let sz = vm.modelManager.sizeLabel(for: model) { Text("Downloaded • \(sz)").font(.caption2).foregroundColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)) }
                            }
                            Spacer()
                        }
                        if isDownloading {
                            ProgressView(value: progress).tint(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
                            HStack {
                                Text(String(format: "%.0f%%", progress*100)).font(.caption2).foregroundColor(.gray)
                                Spacer()
                                Button("Cancel") { vm.modelManager.cancel(model) }.font(.caption)
                            }
                        } else {
                            HStack(spacing: 8) {
                                if downloaded {
                                    Button(isSelected ? "Selected" : "Select") { vm.setSelectedModel(model) }
                                        .buttonStyle(.borderedProminent).tint(isSelected ? Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0) : Color.gray)
                                    Button(role: .destructive) { vm.modelManager.delete(model) } label: { Label("Delete", systemImage: "trash") }
                                        .buttonStyle(.bordered)
                                } else {
                                    Button { vm.modelManager.download(model) } label: { Label("Download", systemImage: "arrow.down.circle") }
                                        .buttonStyle(.borderedProminent).tint(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
                                }
                            }
                            if let err = state?.error { Text(err).font(.caption2).foregroundColor(.red) }
                        }
                    }
                    .padding(16).background(isSelected ? Color(red: 0x14/255.0, green: 0x53/255.0, blue: 0x2D/255.0) : Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0))
                    .clipShape(RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(isSelected ? Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0) : Color.clear, lineWidth: 1))
                }
                Button(vm.modelManager.available.isEmpty ? "Download a model to continue" : "Continue to chat") {
                    // TabView will appear automatically when available not empty
                }
                .disabled(vm.modelManager.available.isEmpty)
                .buttonStyle(.borderedProminent).tint(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)).frame(maxWidth: .infinity).controlSize(.large)
                if vm.modelManager.available.isEmpty { Text("At least one model is required").font(.caption2).foregroundColor(.gray) }
            }.padding(20)
        }.background(Color(red: 0x0A/255.0, green: 0x0F/255.0, blue: 0x0A/255.0).ignoresSafeArea())
    }
}
