package com.vnap.mixin;

import com.vnap.dialogue.ContextualDialogueController;
import com.vnap.dialogue.PillagerDialogueController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {
	@Shadow
	public static native EntityType<?> getType(ItemStack stack);

	@Inject(method = "useOn", at = @At("RETURN"))
	private void vnap$onPillagerSpawnEggUse(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!cir.getReturnValue().consumesAction() || !(context.getLevel() instanceof ServerLevel level)
				|| context.getPlayer() == null || SpawnEggItemMixin.getType(context.getItemInHand()) != EntityTypes.PILLAGER) return;
		PillagerDialogueController.onPillagerSpawnEgg(level, context.getClickedPos(), context.getPlayer());
	}

	@Inject(method = "spawnOffspringFromSpawnEgg", at = @At("RETURN"))
	private static void vnap$spawnOffspringFromSpawnEgg(Player player, Mob parent, EntityType<? extends Mob> type,
			ServerLevel level, Vec3 position, ItemStack stack, CallbackInfoReturnable<Optional<Mob>> cir) {
		cir.getReturnValue().ifPresent(entity -> {
			if (entity instanceof Villager villager) {
				ContextualDialogueController.onBabySpawnedFromEgg(villager, player);
			}
		});
	}
}
