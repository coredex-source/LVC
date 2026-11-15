package com.moyettes.legacyvoicechat.mixin;

import net.minecraft.network.Connection;
import com.moyettes.legacyvoicechat.client.Voice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {


	@Inject(
		method = "disconnect(Lnet/minecraft/text/Text;)V",
		at = @At("HEAD")
	)
	private void onDisconnect(CallbackInfo ci) {
		// LVC's own disconnect (idempotent). This used to fire OSL's DISCONNECT event by hand, so every other mod's
		// disconnect listener ran twice once OSL fires it itself (gen2 does).
		Voice.forceDisconnect();
	}
}
