package com.moyettes.legacyvoicechat.mixin;

import com.moyettes.legacyvoicechat.Voice;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {


	@Inject(
		method = "disconnect(Ljava/lang/String;[Ljava/lang/Object;)V",
		at = @At("HEAD")
	)
	private void onDisconnect(CallbackInfo ci) {
		// LVC's own disconnect (idempotent). This used to fire OSL's DISCONNECT event by hand, so every other mod's
		// disconnect listener ran twice once OSL fires it itself (gen2 does).
		Voice.forceDisconnect();
	}
}
