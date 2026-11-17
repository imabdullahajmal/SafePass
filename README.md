# SafePass

Client-only Fabric mod for Minecraft 1.21.11. It masks recognised authentication
commands while they are being typed and optionally stores a per-server/account
password in an encrypted local vault.

## Use

All commands are client-side and are never sent to a server:

* `/safepass unlock <vault-passphrase>` — creates or unlocks the vault for this session.
* `/safepass save <password>` — stores a password for the current server and Minecraft account.
* `/safepass login` — sends `/login <saved-password>` to the current server.
* `/safepass forget` — removes the current saved password.
* `/safepass autosave on|off` — opt in/out of saving a password entered with `/login` or `/l`.
* `/safepass mode` — toggles one-message masking for servers that ask for a raw password
  instead of a command. The next submitted message is sent unchanged but shown as dots.

Recognised `/login`, `/l`, `/register`, `/reg`, and SafePass vault commands mask their
secret portion automatically. Vault data is AES-256-GCM encrypted with a key derived
from your vault passphrase using PBKDF2-HMAC-SHA256; the vault passphrase is never
written to disk. Losing it makes saved passwords unrecoverable.

Build with JDK 21+: `./gradlew build` (or `gradlew.bat build` on Windows).
