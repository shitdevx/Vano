import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var vm: VanoViewModel
    @State private var showConfirm = false
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                Text("Settings").font(.title2).bold().foregroundColor(.white).frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .leading, spacing: 12) {
                    Text("Model selection").font(.headline).foregroundColor(.white)
                    Text("Choose which downloaded model is used for chat. Green = selected.").font(.caption).foregroundColor(.gray)
                    ForEach(ModelId.allCases) { model in
                        let downloaded = vm.modelManager.isDownloaded(model)
                        let isSelected = vm.selectedModel == model.fileName
                        let prog = vm.modelManager.downloadStates[model]?.progress ?? 0
                        let isDownloading = vm.modelManager.downloadStates[model]?.isDownloading ?? false
                        HStack {
                            VStack(alignment: .leading) {
                                Text(model.displayName).font(.subheadline).foregroundColor(.white)
                                Text("\(model.sizeLabel) • \(model.description)").font(.caption2).foregroundColor(.gray)
                                if downloaded, let sz = vm.modelManager.sizeLabel(for: model) { Text("Downloaded • \(sz)").font(.caption2).foregroundColor(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)) }
                                if isDownloading { ProgressView(value: prog).tint(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0)) }
                            }
                            Spacer()
                            if isDownloading { Text("\(Int(prog*100))%").font(.caption2).foregroundColor(.gray) }
                            else if downloaded {
                                Button { vm.setSelectedModel(model) } label: {
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle").foregroundColor(isSelected ? Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0) : .gray)
                                }
                            } else {
                                Button { vm.modelManager.download(model) } label: { Image(systemName: "arrow.down.circle") }.tint(Color(red: 0x22/255.0, green: 0xC5/255.0, blue: 0x5E/255.0))
                            }
                        }.padding(.vertical, 4)
                        Divider().background(Color.gray.opacity(0.15))
                    }
                }.padding(16).background(Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0)).clipShape(RoundedRectangle(cornerRadius: 16))

                VStack(alignment: .leading, spacing: 12) {
                    Text("Danger zone").bold().foregroundColor(.white)
                    Text("Remove all downloaded models and conversations. This cannot be undone.").font(.caption).foregroundColor(.white.opacity(0.8))
                    Button(role: .destructive) { showConfirm = true } label: {
                        Label("Remove all models and data", systemImage: "trash.fill").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent).tint(.red)
                }.padding(16).background(Color.red.opacity(0.2)).clipShape(RoundedRectangle(cornerRadius: 16))

                VStack(spacing: 8) {
                    Text("Vano").bold().foregroundColor(.white)
                    Text("Private, offline AI chat").font(.caption).foregroundColor(.gray)
                    Divider().background(Color.gray.opacity(0.15))
                    HStack { Text("Version").font(.caption).foregroundColor(.gray); Spacer(); Text("1.11 (12)").font(.caption).bold() }
                    HStack { Text("Package").font(.caption).foregroundColor(.gray); Spacer(); Text("com.vamora.vano").font(.caption).bold() }
                    HStack { Text("Models").font(.caption).foregroundColor(.gray); Spacer(); Text("github.com/TheVamoraProject/Vano").font(.caption).bold() }
                    Text("© 2026 Vamora Project • Built with green accent").font(.caption2).foregroundColor(.gray)
                }.padding(16).background(Color(red: 0x1E/255.0, green: 0x24/255.0, blue: 0x20/255.0)).clipShape(RoundedRectangle(cornerRadius: 16))
            }.padding(16)
        }.background(Color(red: 0x0A/255.0, green: 0x0F/255.0, blue: 0x0A/255.0).ignoresSafeArea())
        .alert("Remove all data?", isPresented: $showConfirm) {
            Button("Delete all", role: .destructive) { vm.deleteAll() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("This will delete all models (GGUF files) and all chat history. You will need to download a model again.") }
    }
}
