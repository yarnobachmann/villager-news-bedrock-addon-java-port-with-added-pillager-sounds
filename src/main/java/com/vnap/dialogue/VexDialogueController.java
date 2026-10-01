package com.vnap.dialogue;

import com.vnap.config.VillagerNewsSettings;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VexDialogueController {
	private static final long CHECK_INTERVAL = 10L;
	private static final long LINE_COOLDOWN = 20L * 35L;
	private static final Map<UUID, VexState> STATES = new HashMap<>();
	private static long ticks;

	private VexDialogueController() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VexDialogueController::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F || !(source.getEntity() instanceof Mob vex) || !isVex(vex)) return;
			ContextualDialogueController.playVexDialogue(vex, "vex_001", "vex:stab:" + vex.getUUID(), LINE_COOLDOWN, entity);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			ticks = 0L;
		});
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally() || !VillagerNewsSettings.dialogueEnabled()) return;
		if (++ticks % CHECK_INTERVAL != 0L) return;
		Set<UUID> seen = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Player player : level.players()) {
				AABB area = player.getBoundingBox().inflate(32.0, 20.0, 32.0);
				for (Mob vex : level.getEntitiesOfClass(Mob.class, area, VexDialogueController::isVex)) {
					if (!vex.isAlive()) continue;
					UUID id = vex.getUUID();
					seen.add(id);
					VexState state = STATES.computeIfAbsent(id, ignored -> new VexState(ticks + 40L));
					if (state.spawnLinePending && ticks >= state.spawnLineDue) {
						state.spawnLinePending = false;
						if (ContextualDialogueController.playVexDialogue(vex, "vex_005", "vex:arrival:" + id, LINE_COOLDOWN, player)) continue;
					}
					if (vex.getTarget() instanceof Player target && target.isAlive()) {
						if (hasArmor(target) && !state.armorLinePlayed) {
							if (ContextualDialogueController.playVexDialogue(vex, "vex_004", "vex:armor:" + id, LINE_COOLDOWN, target)) {
								state.armorLinePlayed = true;
								continue;
							}
						}
						if (!state.targetLinePlayed && ContextualDialogueController.playVexDialogue(vex, "vex_003",
							"vex:target:" + id, LINE_COOLDOWN, target)) {
							state.targetLinePlayed = true;
						}
					} else if (state.hadTarget && !state.vanishLinePlayed
							&& ContextualDialogueController.playVexDialogue(vex, "vex_002", "vex:vanish:" + id,
							LINE_COOLDOWN, player)) {
						state.vanishLinePlayed = true;
					}
					state.hadTarget = vex.getTarget() != null;
				}
			}
		}
		STATES.keySet().removeIf(id -> !seen.contains(id));
	}

	private static boolean isVex(Mob mob) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("vex");
	}

	private static boolean hasArmor(Player player) {
		return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	private static final class VexState {
		private final long spawnLineDue;
		private boolean spawnLinePending = true;
		private boolean armorLinePlayed;
		private boolean targetLinePlayed;
		private boolean vanishLinePlayed;
		private boolean hadTarget;

		private VexState(long spawnLineDue) {
			this.spawnLineDue = spawnLineDue;
		}
	}
}
