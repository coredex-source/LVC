package com.moyettes.legacyvoicechat.mixin;

import com.moyettes.legacyvoicechat.Voice;
import com.moyettes.legacyvoicechat.client.VoiceClient;
import com.moyettes.legacyvoicechat.events.ClientEvents;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.mob.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {

	@Inject(method = "render(Lnet/minecraft/entity/mob/player/PlayerEntity;DDDFF)V", at = @At("TAIL"))
	public void renderIcons(PlayerEntity playerEntity, double d, double e, double f, float g, float h, CallbackInfo ci) {
		VoiceClient vc = Voice.getVoiceClient();
		if(vc == null){
			return;
		}

		// ClientEvents is in loader-agnostic client-common; it takes int
		// networkId instead of PlayerEntity. RenderEvents resolves the
		// networkId back to a PlayerEntity for the render path.
		ClientEvents.INSTANCE.onRenderPlayerIcons(playerEntity.networkId, d, e, f);
	}
}
