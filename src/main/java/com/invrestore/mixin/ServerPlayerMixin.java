package com.invrestore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.invrestore.server.DeathHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Captures the death snapshot at the very start of {@code ServerPlayer#die},
 * i.e. before vanilla drops the inventory. This is what makes the snapshot the
 * exact inventory the player died with, rather than a post-respawn state.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void invrestore$captureDeath(DamageSource source, CallbackInfo callbackInfo) {
		DeathHandler.onPlayerDeath((ServerPlayer) (Object) this, source);
	}
}
