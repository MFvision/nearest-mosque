#if !targetEnvironment(macCatalyst) && !os(visionOS)
import ClockKit
import SwiftUI

/// Settings → Apple Watch: how to put Near Mosque on a watch face, and the ready-made faces when the app has any
/// (.watchface files exported from a real watch and added to App/Resources; none ship yet).
struct WatchFacesView: View {
    @Environment(\.l10n) private var l10n
    @State private var added: String?

    private var faces: [URL] { Bundle.main.urls(forResourcesWithExtension: "watchface", subdirectory: nil) ?? [] }

    var body: some View {
        Form {
            Section {
                Label(l10n.t("watch_face_howto"), systemImage: "hand.tap")
            } footer: {
                Text(l10n.t("watch_face_note"))
            }
            if !faces.isEmpty {
                Section {
                    ForEach(faces, id: \.self) { url in
                        Button {
                            CLKWatchFaceLibrary().addWatchFace(at: url) { error in
                                Task { @MainActor in added = error == nil ? url.deletingPathExtension().lastPathComponent : nil }
                            }
                        } label: {
                            Label(url.deletingPathExtension().lastPathComponent, systemImage: "plus.circle")
                        }
                    }
                    if let added { Text(l10n.t("import_done", added)).font(.footnote).foregroundStyle(.secondary) }
                }
            }
        }
        .navigationTitle(l10n.t("watch_section"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
#endif
