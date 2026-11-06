package fr.neamar.kiss.dashboard;

import java.util.Locale;

/** Conservative presentation only: unknown actions always retain their original text. */
final class CallActionPolicy {
    enum Kind { TEXT, HANG_UP, MUTE, UNMUTE }
    static Kind classify(boolean call, CharSequence title) {
        if (!call || title == null) return Kind.TEXT;
        String label = title.toString().trim().toLowerCase(Locale.ROOT);
        switch (label) {
            case "hang up": case "hangup": case "end call": return Kind.HANG_UP;
            case "mute": case "mute microphone": return Kind.MUTE;
            case "unmute": case "unmute microphone": return Kind.UNMUTE;
            default: return Kind.TEXT;
        }
    }
}
