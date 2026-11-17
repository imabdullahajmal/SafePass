package dev.safepass.mixin;

import dev.safepass.Masking;
import dev.safepass.SafePassCommands;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    @Shadow private TextFieldWidget chatField;
    @Unique private String safepass$realText;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void safepass$handleLocalCommands(String message, boolean addToHistory, CallbackInfo ci) {
        if (SafePassCommands.handle(message)) {
            ci.cancel();
            return;
        }
        SafePassCommands.captureLogin(message);
        Masking.consumeOneMessageMode();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void safepass$maskChatField(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        safepass$realText = chatField.getText();
        String masked = Masking.displayText(safepass$realText);
        if (!masked.equals(safepass$realText)) chatField.setText(masked);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void safepass$restoreChatField(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (safepass$realText != null && !chatField.getText().equals(safepass$realText)) chatField.setText(safepass$realText);
        safepass$realText = null;
    }
}
