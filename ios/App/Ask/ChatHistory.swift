import Foundation
import SwiftUI

/// A saved chat: only the questions, in order. Opening it asks them again on the device, so answers
/// always come from the books installed now. Never sent anywhere.
struct SavedChat: Codable, Identifiable, Equatable {
    let id: UUID
    var updated: Date
    var questions: [String]
    var title: String { questions.first ?? "" }
}

/// Chats in Application Support/chats.json, newest first (at most 50).
@MainActor
@Observable
final class ChatHistory {
    private(set) var chats: [SavedChat] = []
    private let url: URL

    init(directory: URL? = nil) {
        let dir = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        url = dir.appendingPathComponent("chats.json")
        if let data = try? Data(contentsOf: url), let saved = try? JSONDecoder().decode([SavedChat].self, from: data) { chats = saved }
    }

    /// Adds a question to chat `id` (creating it), and moves the chat to the top.
    func record(_ question: String, in id: UUID) {
        var chat = chats.first { $0.id == id } ?? SavedChat(id: id, updated: Date(), questions: [])
        chat.questions.append(question)
        chat.updated = Date()
        chats.removeAll { $0.id == id }
        chats.insert(chat, at: 0)
        if chats.count > 50 { chats.removeLast(chats.count - 50) }
        save()
    }

    func delete(_ id: UUID) { chats.removeAll { $0.id == id }; save() }
    func deleteAll() { chats = []; save() }

    private func save() {
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        // Excluded from iCloud backups too: the chats stay on this phone.
        var u = url
        try? JSONEncoder().encode(chats).write(to: u, options: [.atomic, .completeFileProtection])
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? u.setResourceValues(values)
    }
}

/// List of saved chats; tap one to open it again, swipe to delete.
struct ChatHistoryView: View {
    @Environment(\.l10n) private var l10n
    @Environment(\.dismiss) private var dismiss
    let history: ChatHistory
    var onOpen: (SavedChat) -> Void

    var body: some View {
        NavigationStack {
            List {
                if history.chats.isEmpty {
                    Text(l10n.t("chat_history_empty")).foregroundStyle(.secondary)
                }
                ForEach(history.chats) { chat in
                    Button { onOpen(chat); dismiss() } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(chat.title).font(.body).lineLimit(2).foregroundStyle(.primary)
                            Text(chat.updated.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened).locale(l10n.locale)))
                                .font(.caption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .swipeActions { Button(l10n.t("chat_delete"), role: .destructive) { history.delete(chat.id) } }
                }
                Section {
                    if !history.chats.isEmpty {
                        Button(l10n.t("chat_delete_all"), role: .destructive) { history.deleteAll() }
                    }
                } footer: {
                    Text(l10n.t("chat_history_note"))
                }
            }
            .navigationTitle(l10n.t("chat_history"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(l10n.t("close")) { dismiss() }.keyboardShortcut(.cancelAction) }
            }
        }
    }
}
