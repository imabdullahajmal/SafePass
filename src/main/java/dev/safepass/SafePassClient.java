package dev.safepass;

import dev.safepass.vault.PasswordVault;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class SafePassClient implements ClientModInitializer {
    public static final PasswordVault VAULT = new PasswordVault();

    @Override
    public void onInitializeClient() {
        VAULT.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VAULT.lock());
    }
}
