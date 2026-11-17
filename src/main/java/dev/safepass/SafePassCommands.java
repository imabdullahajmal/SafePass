package dev.safepass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Locale;

/** Local command parser. Called before chat history/network submission. */
public final class SafePassCommands {
    private SafePassCommands() { }

    public static boolean handle(String message) {
        if (!message.toLowerCase(Locale.ROOT).startsWith("/safepass")) return false;
        String[] parts = message.trim().split("\\s+", 3);
        String action = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "help";
        switch (action) {
            case "unlock" -> unlock(parts.length == 3 ? parts[2] : "");
            case "save" -> save(parts.length == 3 ? parts[2] : "");
            case "login" -> login();
            case "forget" -> forget();
            case "autosave" -> autosave(parts.length == 3 ? parts[2] : "");
            case "mode" -> {
                Masking.enableOneMessageMode();
                feedback("SafePass: the next chat message will be masked.");
            }
            default -> feedback("SafePass: unlock <vault-passphrase>, save <password>, login, forget, autosave on|off, mode");
        }
        return true;
    }

    public static void captureLogin(String message) {
        if (!SafePassClient.VAULT.autoSave() || !SafePassClient.VAULT.isUnlocked()) return;
        String lower = message.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("/login ") || lower.startsWith("/l ") || lower.startsWith("/register ") || lower.startsWith("/reg "))) return;
        String afterCommand = message.substring(message.indexOf(' ') + 1).trim();
        // /register <pass> <pass> repeats the password; only the first one is the actual secret.
        String password = afterCommand.split("\\s+", 2)[0];
        // No password entered (e.g. bare "/login" or "/register"): nothing to auto-save.
        if (password.isEmpty()) return;
        if (SafePassClient.VAULT.save(currentIdentity(), password)) feedback("SafePass: password updated.");
    }

    private static void unlock(String passphrase) {
        if (passphrase.isEmpty()) { feedback("SafePass: a vault passphrase is required."); return; }
        feedback(SafePassClient.VAULT.unlock(passphrase) ? "SafePass: vault unlocked." : "SafePass: could not unlock vault (wrong passphrase or corrupt data).");
    }

    private static void save(String password) {
        if (password.isEmpty()) { feedback("SafePass: a password is required."); return; }
        if (!SafePassClient.VAULT.isUnlocked()) { feedback("SafePass: unlock the vault first."); return; }
        feedback(SafePassClient.VAULT.save(currentIdentity(), password) ? "SafePass: password saved." : "SafePass: could not save password.");
    }

    private static void login() {
        if (!SafePassClient.VAULT.isUnlocked()) { feedback("SafePass: unlock the vault first."); return; }
        String password = SafePassClient.VAULT.password(currentIdentity());
        if (password == null) { feedback("SafePass: no password is saved for this server/account."); return; }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.player.networkHandler.sendChatCommand("login " + password);
    }

    private static void forget() {
        if (!SafePassClient.VAULT.isUnlocked()) { feedback("SafePass: unlock the vault first."); return; }
        feedback(SafePassClient.VAULT.forget(currentIdentity()) ? "SafePass: saved password removed." : "SafePass: no saved password for this server/account.");
    }

    private static void autosave(String value) {
        if (!value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off")) { feedback("SafePass: use autosave on|off."); return; }
        SafePassClient.VAULT.setAutoSave(value.equalsIgnoreCase("on"));
        feedback("SafePass: automatic saving is " + value.toLowerCase(Locale.ROOT) + ".");
    }

    private static String currentIdentity() {
        MinecraftClient client = MinecraftClient.getInstance();
        String server = client.getCurrentServerEntry() == null ? "singleplayer" : client.getCurrentServerEntry().address;
        return server.toLowerCase(Locale.ROOT) + "|" + client.getSession().getUsername().toLowerCase(Locale.ROOT);
    }

    private static void feedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(Text.literal(message));
    }
}
