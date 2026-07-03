package com.autoauction.client.mixin;

import com.autoauction.client.AutoauctionClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftRenderFrameMixin {
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void autoauction$renderSmoothLook(boolean tick, CallbackInfo info) {
		AutoauctionClient.renderSmoothLook((Minecraft) (Object) this);
	}
}
