// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "VanoIOS",
    platforms: [.iOS(.v15)],
    products: [
        .executable(name: "VanoIOS", targets: ["VanoIOS"])
    ],
    dependencies: [
        // Prebuilt llama.cpp XCFramework b5046 — includes llama, ggml, Metal
        // Use any recent build from https://github.com/ggml-org/llama.cpp/releases
        .package(url: "https://github.com/ggml-org/llama.cpp", branch: "master")
        // If Xcode SPM fails, use binaryTarget directly:
        // .package(url: "https://github.com/ggml-org/llama.cpp", from: "b5046")
    ],
    targets: [
        .executableTarget(
            name: "VanoIOS",
            dependencies: [
                // When using master branch SPM, the product is "LlamaFramework" via binaryTarget
                // If building from source, depend on "llama"
            ],
            path: "VanoIOS",
            swiftSettings: [.unsafeFlags(["-O2"])]
        ),
        .binaryTarget(
            name: "LlamaFramework",
            url: "https://github.com/ggml-org/llama.cpp/releases/download/b5046/llama-b5046-xcframework.zip",
            checksum: "c19be78b5f00d8d29a25da41042cb7afa094cbf6280a225abe614b03b20029ab"
        )
    ]
)
