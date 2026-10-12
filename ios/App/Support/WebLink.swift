import Foundation

/// A web address from data (a mosque's website, a source's original page) made safe to open: web pages only, always
/// over HTTPS. Any other kind of link (tel:, another app's scheme, javascript:, file:) is refused.
enum WebLink {
    static func url(_ raw: String) -> URL? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let lower = s.lowercased()
        let https: String
        if lower.hasPrefix("https://") { https = s }
        else if lower.hasPrefix("http://") { https = "https://" + s.dropFirst(7) }
        else if lower.contains(":") && !lower.contains("://") && !lower.contains(".") { return nil }
        else if lower.contains("://") { return nil }
        else { https = "https://" + s }
        guard let u = URL(string: https), u.scheme == "https", let host = u.host, host.contains(".") else { return nil }
        return u
    }
}
