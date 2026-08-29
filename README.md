# Vano — On-Device AI (Android + iOS)

Green accent `#22C55E` on dark `#0A0F0A`. Two GGUFs: `vano-Q4_0.gguf` (1.55 GB) and `vano-mini-Q3_K_M.gguf` (1.38 GB) from `github.com/TheVamoraProject/Vano/releases` — URL `vano-Q4_0` = `https://github.com/TheVamoraProject/Vano/releases/download/1.0/vano-Q4_0.gguf`, `vano-mini` = `https://github.com/TheVamoraProject/Vano/releases/download/mini/vano-mini-Q3_K_M.gguf`. Logo `Vano_Assistant.png` (green with white pills).

- **Android** `minSdk 29 targetSdk 34 compileSdk 35` — Jetpack Compose, Navigation, DataStore, Room-like `chats.json`, `ModelManager` streaming download, `LlamaInferenceManager` `llama-android 0.1.1` `context 8192 temp 0.1 topP 0.9 topK 40 maxTokens 200 repeat 1.5` (fallback stub), markdown `compose-markdown 0.5.8`, file attach `text/*` (8192), chat search/add/delete, per-message `vano Q4_0` badge + `17 tokens 0.6s 27 t/s` + Copy/Delete, Settings model picker + Remove all, dark welcome (no flash), bottom `Chats|Settings` no white flashbang.

- **iOS** `iOS 15+` SwiftUI — `VanoIOS/` + `VanoIOS.xcodeproj` + `Package.swift` binaryTarget `LlamaFramework b5046` `https://github.com/ggml-org/llama.cpp/releases/download/b5046/llama-b5046-xcframework.zip`. Same features: `DownloadView`, `ChatListView`, `ChatView` (AttributedString markdown, file attach, `vano Q4_0` badge), `SettingsView`. Real GGUF via `Managers/InferenceManager.swift` `canImport(LlamaFramework)` `llama_model_load_from_file` `n_ctx 8192 temp 0.1 topP 0.9 topK 40 repeat 1.5 presence 0.5 frequency 0.5 maxTokens 200` — stub on Linux CI.

### Quick Start
- Android: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (51M, `adb install -r`)
- iOS: Open `VanoIOS.xcodeproj` in Xcode 14+ on macOS → iPhone 15 simulator (iOS 15) → Run. Or `swift package resolve && swift build`.

### GitHub Actions
Push to `main` → `.github/workflows/android.yml` (ubuntu JDK17, `./gradlew assembleDebug/Release` → `Vano-debug-apk`/`Vano-release-apk`) and `ios.yml` (macos-14 Xcode 15.4, `xcodebuild build` + `Vano-iOS-unsigned.ipa`).

### Repo
```
Vano/
  app/ (Android) + VanoIOS/ (iOS) + VanoIOS.xcodeproj + Package.swift
  .github/workflows/android.yml, ios.yml
  gradle/, settings.gradle.kts, build.gradle.kts
```

Built from your `1.11` Android + new iOS `1.12`.
