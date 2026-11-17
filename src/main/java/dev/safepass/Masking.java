package dev.safepass;

import java.util.Locale;

/** Determines which part of a chat line is sensitive without changing the submitted text. */
public final class Masking {
    private static boolean oneMessageMode;

    private Masking() { }

    public static void enableOneMessageMode() {
        oneMessageMode = true;
    }

    public static boolean consumeOneMessageMode() {
        boolean active = oneMessageMode;
        oneMessageMode = false;
        return active;
    }

    public static String displayText(String text) {
        if (oneMessageMode) return bullets(text, 0);
        String lower = text.toLowerCase(Locale.ROOT);
        // Recognised auth commands with a secret portion: mask everything after the command word.
        if (lower.startsWith("/login ") || lower.startsWith("/register ") || lower.startsWith("/reg ") || lower.startsWith("/l ")) {
            int secretStart = text.indexOf(' ') + 1;
            // Bare command with no password (e.g. "/login" or "/login "): nothing sensitive to mask.
            if (secretStart >= text.length()) return text;
            return bullets(text, secretStart);
        }
        if (lower.startsWith("/safepass unlock ")) return bullets(text, "/safepass unlock ".length());
        if (lower.startsWith("/safepass save ")) return bullets(text, "/safepass save ".length());
        return text;
    }

    private static String bullets(String input, int from) {
        if (from >= input.length()) return input;
        return input.substring(0, from) + "•".repeat(input.length() - from);
    }
}
