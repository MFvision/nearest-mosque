import AVFoundation
import SwiftUI

/// Reads text aloud with the phone's own voices (on the device, offline). One thing at a time: starting
/// another, or tapping again, stops the current one.
@MainActor
@Observable
final class Speaker: NSObject, AVSpeechSynthesizerDelegate {
    static let shared = Speaker()
    private let synth = AVSpeechSynthesizer()
    /// What is being read now (an answer's id), for the button's state.
    private(set) var speaking: String?

    override init() {
        super.init()
        synth.delegate = self
    }

    /// `parts` are (text, BCP-47 language) read in order.
    func toggle(_ id: String, _ parts: [(String, String)]) {
        if speaking == id { stop(); return }
        stop()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        speaking = id
        for (text, lang) in parts where !text.isEmpty {
            let u = AVSpeechUtterance(string: text)
            u.voice = AVSpeechSynthesisVoice(language: lang) ?? AVSpeechSynthesisVoice(language: String(lang.prefix(2)))
            u.postUtteranceDelay = 0.3
            synth.speak(u)
        }
    }

    func stop() {
        synth.stopSpeaking(at: .immediate)
        speaking = nil
    }

    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in if !self.synth.isSpeaking { self.speaking = nil } }
    }

    nonisolated func speechSynthesizer(_ s: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in if !self.synth.isSpeaking { self.speaking = nil } }
    }
}
