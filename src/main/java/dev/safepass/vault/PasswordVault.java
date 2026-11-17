package dev.safepass.vault;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Encrypted local vault. The passphrase-derived key lives only for this game session. */
public final class PasswordVault {
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int KDF_ITERATIONS = 310_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("safepass.json");
    private final Map<String, String> passwords = new HashMap<>();
    private byte[] salt;
    private SecretKeySpec sessionKey;
    private boolean autoSave;

    public void load() {
        if (!Files.exists(path)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            salt = Base64.getDecoder().decode(root.get("salt").getAsString());
            autoSave = root.has("autoSave") && root.get("autoSave").getAsBoolean();
        } catch (IOException | IllegalArgumentException | JsonParseException ignored) {
            salt = null; // Never overwrite an unreadable vault until the user explicitly unlocks it.
        }
    }

    public boolean unlock(String passphrase) {
        try {
            if (salt == null) {
                salt = random(SALT_BYTES);
                sessionKey = derive(passphrase, salt);
                passwords.clear();
                return write();
            }
            SecretKeySpec candidate = derive(passphrase, salt);
            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            String plaintext = decrypt(candidate, root.get("nonce").getAsString(), root.get("payload").getAsString());
            JsonObject stored = GSON.fromJson(plaintext, JsonObject.class);
            passwords.clear();
            for (String key : stored.keySet()) passwords.put(key, stored.get(key).getAsString());
            sessionKey = candidate;
            return true;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | JsonParseException ignored) {
            return false;
        }
    }

    public boolean isUnlocked() { return sessionKey != null; }
    public boolean autoSave() { return autoSave; }

    public void setAutoSave(boolean enabled) {
        autoSave = enabled;
        if (sessionKey != null) write();
        else writeSettingsOnly();
    }

    public String password(String identity) { return passwords.get(identity); }

    public boolean save(String identity, String password) {
        if (sessionKey == null) return false;
        passwords.put(identity, password);
        return write();
    }

    public boolean forget(String identity) {
        if (sessionKey == null || passwords.remove(identity) == null) return false;
        return write();
    }

    public void lock() {
        sessionKey = null;
        passwords.replaceAll((key, value) -> "");
        passwords.clear();
    }

    private boolean write() {
        if (sessionKey == null || salt == null) return false;
        try {
            JsonObject values = new JsonObject();
            passwords.forEach(values::addProperty);
            byte[] nonce = random(NONCE_BYTES);
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("salt", Base64.getEncoder().encodeToString(salt));
            root.addProperty("nonce", Base64.getEncoder().encodeToString(nonce));
            root.addProperty("payload", encrypt(sessionKey, nonce, GSON.toJson(values)));
            root.addProperty("autoSave", autoSave);
            atomicWrite(root);
            return true;
        } catch (IOException | GeneralSecurityException ignored) {
            return false;
        }
    }

    private void writeSettingsOnly() {
        if (!Files.exists(path)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            root.addProperty("autoSave", autoSave);
            atomicWrite(root);
        } catch (IOException | JsonParseException ignored) { }
    }

    private void atomicWrite(JsonObject root) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static SecretKeySpec derive(String passphrase, byte[] salt) throws GeneralSecurityException {
        char[] chars = passphrase.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, KDF_ITERATIONS, KEY_BITS);
            return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(), "AES");
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private static String encrypt(SecretKeySpec key, byte[] nonce, String text) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String decrypt(SecretKeySpec key, String encodedNonce, String encodedPayload) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(encodedNonce)));
        return new String(cipher.doFinal(Base64.getDecoder().decode(encodedPayload)), StandardCharsets.UTF_8);
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
