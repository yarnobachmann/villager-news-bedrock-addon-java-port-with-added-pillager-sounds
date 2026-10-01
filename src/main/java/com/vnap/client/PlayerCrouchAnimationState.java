package com.vnap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

final class PlayerCrouchAnimationState {
	private static UUID playerId;
	private static int crouchTicks;

	private PlayerCrouchAnimationState() {
	}

	static void tick(Minecraft client) {
		Player player = client.player;
		if (player == null) {
			reset();
			return;
		}
		if (!player.getUUID().equals(playerId)) {
			playerId = player.getUUID();
			crouchTicks = 0;
		}
		if (player.isShiftKeyDown()) crouchTicks = Math.min(crouchTicks + 1, 1200);
		else crouchTicks = 0;
	}

	static float crouchTicks() {
		return crouchTicks;
	}

	static float flying() {
		Player player = Minecraft.getInstance().player;
		return player != null && player.getAbilities().flying ? 1.0F : 0.0F;
	}

	static void reset() {
		playerId = null;
		crouchTicks = 0;
	}
}
