package com.moyettes.legacyvoicechat.mixin;

import net.minecraft.network.packet.CustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.DataOutputStream;

/**
 * Works around OSL networking on 1.1-1.2.x (osl-networking-impl 0.3.0+mc11w49a-mc12w16a): its packet factory sets the
 * payload's channel and data but not its size, so vanilla writes size 0 followed by the bytes. The other side then
 * reads no data (OSL fails with a NullPointerException) and takes the bytes for the next packet, which breaks the
 * connection. Every OSL channel is affected, the handshake included. Harmless once OSL sets the size itself.
 */
@Mixin(CustomPayloadPacket.class)
public class CustomPayloadPacketMixin {

	@Shadow
	public int size;
	@Shadow
	public byte[] data;

	@Inject(method = "write", at = @At("HEAD"))
	private void lvc$writeRealSize(DataOutputStream output, CallbackInfo ci) {
		if (data != null && size != data.length) {
			size = data.length;
		}
	}
}
