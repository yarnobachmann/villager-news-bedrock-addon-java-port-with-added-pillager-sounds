package com.vnap.mixin;

import com.vnap.dialogue.PillagerDialogueController;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class PillagerMissedShotMixin {
	@Inject(method = "onHitBlock", at = @At("TAIL"))
	private void vnap$playMissedShotReaction(BlockHitResult hitResult, CallbackInfo ci) {
		Projectile projectile = (Projectile) (Object) this;
		if (projectile instanceof AbstractArrow arrow && !projectile.level().isClientSide()) {
			PillagerDialogueController.onPillagerProjectileHitBlock(arrow);
		}
	}
}
