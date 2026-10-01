package com.vnap.dialogue;

import com.vnap.config.VillagerNewsSettings;
import com.vnap.network.DialogueAnimationNetwork;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PillagerDialogueController {
	private static final double PLAYER_RANGE = 12.0;
	private static final double REACTION_RANGE = 10.0;
	private static final double PLAYER_APPROACH_RANGE = 10.0;
	private static final long DIALOGUE_COOLDOWN = 20L * 45L;
	private static final long GROUP_GAG_COOLDOWN = 20L * 15L;
	private static final long NEARBY_CHECK_INTERVAL = 20L * 3L;
	private static final long AMBIENT_MIN_DELAY = 20L * 200L;
	private static final long AMBIENT_MAX_DELAY = 20L * 320L;
	private static final int[] PLAYER_SIGHTING_LINES = {1, 2, 51, 53, 57, 58, 65, 80, 113};
	private static final int[] PLAYER_GEAR_LINES = {4, 5, 6, 49, 78, 83, 86, 94};
	private static final int[] PLAYER_ARMOR_LINES = {4, 94};
	private static final int[] PLAYER_STARE_LINES = {3, 54, 96, 115, 116};
	private static final int[] VILLAGER_SIGHTING_LINES = {9, 10, 11, 12, 13, 14, 16, 95, 97};
	private static final int[] VILLAGER_FLEE_LINES = {15, 60, 91, 105, 109, 110, 114};
	private static final int[] PILLAGER_SHOT_HIT_LINES = {85, 99, 107, 108, 55, 59, 61, 93};
	private static final int[] GOLEM_LINES = {17, 18, 19, 20, 21, 22, 23, 24, 77, 88, 100};
	private static final int[] RAID_LINES = {29, 30, 31, 32, 75, 101, 104, 112};
	private static final int[] GROUP_GAG_LINES = {56, 62, 63, 68, 69, 71, 82, 84, 98};
	private static final Set<Integer> WAVE_LINES = Set.of(63, 68, 69, 71, 84);
	private static final int[] PATROL_LINES = {41, 42, 43, 44, 45, 46, 47, 48, 50, 52, 64, 66, 67, 70, 72, 74, 76, 79, 81, 89, 90, 92, 106, 111};
	private static final int[] PILLAGER_HURT_LINES = {33, 34, 35, 73, 87, 102, 117};
	private static final int[] PLAYER_FOOD_LINES = {103};
	private static final int[] EGG_HATCH_LINES = {118, 119, 120, 121};
	private static final Map<UUID, PillagerState> STATES = new HashMap<>();
	private static final Map<String, Integer> LAST_POOL_LINES = new HashMap<>();
	private static final List<PendingVillagerShot> PENDING_VILLAGER_SHOTS = new ArrayList<>();
	private static long ticks;

	private PillagerDialogueController() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PillagerDialogueController::tick);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level instanceof ServerLevel && entity instanceof Mob mob && isPillager(mob)) {
				tryPool(mob, "player_attack", player, DIALOGUE_COOLDOWN, 35, PILLAGER_HURT_LINES);
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F) return;
			if (entity instanceof Villager villager && source.getDirectEntity() instanceof AbstractArrow arrow
					&& arrow.getOwner() instanceof Mob shooter && isPillager(shooter)
					&& villager.isAlive() && villager.level() instanceof ServerLevel level) {
				PENDING_VILLAGER_SHOTS.add(new PendingVillagerShot(level, shooter.getUUID(), villager.getUUID(), ticks + 40L, ticks + 140L));
			}
			if (entity instanceof Mob mob && isPillager(mob)) {
				tryPool(mob, "hurt", source.getEntity() instanceof LivingEntity attacker ? attacker : null,
					20L * 8L, PILLAGER_HURT_LINES);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.clear();
			LAST_POOL_LINES.clear();
			PENDING_VILLAGER_SHOTS.clear();
			ticks = 0L;
		});
	}

	public static void onPillagerSpawnEgg(ServerLevel level, BlockPos position, Player player) {
		AABB area = new AABB(position).inflate(3.0);
		Mob pillager = level.getEntitiesOfClass(Mob.class, area, PillagerDialogueController::isPillager).stream()
			.min((first, second) -> Double.compare(first.distanceToSqr(Vec3.atCenterOf(position)), second.distanceToSqr(Vec3.atCenterOf(position))))
			.orElse(null);
		if (pillager != null) tryPool(pillager, "egg_hatch", player, 0L, EGG_HATCH_LINES);
	}

	public static void onPillagerProjectileHitBlock(AbstractArrow projectile) {
		if (!(projectile.getOwner() instanceof Mob pillager) || !isPillager(pillager) || !pillager.isAlive()) return;
		LivingEntity target = pillager.getTarget();
		if (target == null || !target.isAlive() || target.level() != pillager.level()) return;
		ContextualDialogueController.playPillagerDialogue(pillager, "pillager_037",
			"pillager_missed_shot:" + pillager.getUUID(), DIALOGUE_COOLDOWN, target);
	}

	private static void tick(MinecraftServer server) {
		if (!server.tickRateManager().runsNormally() || !VillagerNewsSettings.dialogueEnabled()) {
			STATES.clear();
			return;
		}
		if (++ticks % 10L != 0L) return;
		processPendingVillagerShots();
		Set<UUID> seenPillagers = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Player player : level.players()) {
				AABB area = player.getBoundingBox().inflate(48.0, 24.0, 48.0);
				for (Mob mob : level.getEntitiesOfClass(Mob.class, area, PillagerDialogueController::isPillager)) {
					seenPillagers.add(mob.getUUID());
					PillagerState state = STATES.computeIfAbsent(mob.getUUID(), ignored -> new PillagerState(ticks + nextAmbientDelay()));
					if (state.lastProcessedTick == ticks) continue;
					state.lastProcessedTick = ticks;
					process(mob, state, level);
				}
			}
		}
		STATES.keySet().removeIf(id -> !seenPillagers.contains(id));
	}

	private static void process(Mob pillager, PillagerState state, ServerLevel level) {
		boolean chargingCrossbow = pillager instanceof net.minecraft.world.entity.monster.illager.Pillager illager
			&& illager.isChargingCrossbow();
		LivingEntity target = pillager.getTarget();
		boolean aimingCrossbow = target != null && pillager.isAggressive()
			&& pillager.getMainHandItem().getItem() instanceof CrossbowItem
			&& CrossbowItem.isCharged(pillager.getMainHandItem())
			&& pillager.hasLineOfSight(target);
		if (aimingCrossbow && !state.wasAimingCrossbow
			&& tryPlay(pillager, 36, target, "crossbow_aim")) {
			state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
			state.wasAimingCrossbow = true;
			return;
		}
		state.wasAimingCrossbow = aimingCrossbow;
		if (chargingCrossbow && !state.wasChargingCrossbow
			&& tryPlay(pillager, 38, target, "crossbow_reload")) {
			state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
			state.wasChargingCrossbow = true;
			return;
		}
		state.wasChargingCrossbow = chargingCrossbow;
		Player player = level.getNearestPlayer(pillager, PLAYER_RANGE);
		if (processPlayer(pillager, player, state)) return;
		if (state.wasAggressive && !pillager.isAggressive()) {
			state.wasAggressive = false;
			if (tryPlay(pillager, 40, null, "combat_ends")) return;
		}
		if (pillager.isAggressive()) state.wasAggressive = true;
		if (ticks >= state.nextNearbyTick) {
			state.nextNearbyTick = ticks + NEARBY_CHECK_INTERVAL;
			if (processGolem(pillager, state, level)) {
				state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
				return;
			}
			if (processVillager(pillager, state, level)) {
				state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
				return;
			}
			if (processPillagerGroup(pillager, state, level)) {
				state.nextNearbyTick = ticks + GROUP_GAG_COOLDOWN;
				return;
			}
			if (level.isRaided(pillager.blockPosition()) && processRaid(pillager, level)) {
				state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
				return;
			}
			if (pillager.isAggressive() && pillager.getTarget() != null) {
				if (RandomSource.create().nextInt(4) == 0 && tryPlay(pillager, 39, pillager.getTarget(), "combat_action")) {
					state.nextNearbyTick = ticks + DIALOGUE_COOLDOWN;
					return;
				}
			}
		}
		if (ticks >= state.nextAmbientTick && !pillager.isAggressive() && !pillager.isUsingItem()) {
			state.nextAmbientTick = ticks + (playAmbient(pillager, level) ? nextAmbientDelay() : NEARBY_CHECK_INTERVAL);
		}
	}

	private static boolean processPlayer(Mob pillager, Player player, PillagerState state) {
		if (player == null || !pillager.hasLineOfSight(player)) {
			if (state.playerNearby && state.lastPlayerId != null) {
				state.playerNearby = false;
				state.playerLeftAt = ticks;
				if (tryPlay(pillager, 7, null, "player_left")) return true;
			}
			state.stareTicks = 0;
			return false;
		}
		double distance = pillager.distanceTo(player);
		if (!state.playerNearby && distance <= PLAYER_APPROACH_RANGE) {
			boolean returned = state.playerLeftAt > 0L && ticks - state.playerLeftAt <= 20L * 60L;
			state.playerNearby = true;
			state.lastPlayerId = player.getUUID();
			if (returned) return tryPool(pillager, "player_returned", player, DIALOGUE_COOLDOWN, 8);
			return tryPool(pillager, "player_approach", player, DIALOGUE_COOLDOWN, PLAYER_SIGHTING_LINES);
		}
		if (distance > 12.0) {
			state.playerNearby = false;
			state.playerLeftAt = ticks;
			state.stareTicks = 0;
			return false;
		}
		if (distance <= 9.0 && isLookingAt(player, pillager)) {
			state.stareTicks += 10;
			if (state.stareTicks >= 40 && ticks >= state.nextPlayerCommentTick) {
				state.stareTicks = 0;
				if (tryPool(pillager, "player_stares", player, DIALOGUE_COOLDOWN, PLAYER_STARE_LINES)) {
					state.nextPlayerCommentTick = ticks + DIALOGUE_COOLDOWN;
					return true;
				}
			}
		} else {
			state.stareTicks = 0;
		}
		if (distance <= PLAYER_RANGE && ticks >= state.nextPlayerCommentTick) {
			boolean reacted = false;
			String heldItem = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();
			if (heldItem.endsWith("_sword")) reacted = tryPool(pillager, "player_sword", player, DIALOGUE_COOLDOWN, 5, new int[] {78});
			else if (heldItem.endsWith("_hoe")) reacted = tryPool(pillager, "player_hoe", player, DIALOGUE_COOLDOWN, 86);
			else if (heldItem.equals("shield")) reacted = tryPool(pillager, "player_shield", player, DIALOGUE_COOLDOWN, 83);
			else if (heldItem.equals("bow") || heldItem.equals("crossbow")) reacted = tryPool(pillager, "player_bow", player, DIALOGUE_COOLDOWN, 49, new int[] {5});
			else if (player.getMainHandItem().get(DataComponents.FOOD) != null) reacted = tryPool(pillager, "player_food", player, DIALOGUE_COOLDOWN, PLAYER_FOOD_LINES);
			else if (playerIsArmored(player)) reacted = tryPool(pillager, "player_armor", player, DIALOGUE_COOLDOWN, PLAYER_ARMOR_LINES);
			else if (player.getMainHandItem().isDamageableItem()) reacted = tryPool(pillager, "player_item", player, DIALOGUE_COOLDOWN, 6);
			else if (!player.getMainHandItem().isEmpty()) reacted = tryPool(pillager, "player_item", player, DIALOGUE_COOLDOWN, 6);
			if (reacted) {
				state.nextPlayerCommentTick = ticks + DIALOGUE_COOLDOWN;
				return true;
			}
		}
		return false;
	}

	private static boolean processGolem(Mob pillager, PillagerState state, ServerLevel level) {
		Mob golem = level.getEntitiesOfClass(Mob.class,
			pillager.getBoundingBox().inflate(REACTION_RANGE), entity -> isEntity(entity, "iron_golem") && entity.isAlive()).stream()
			.min((first, second) -> Double.compare(pillager.distanceToSqr(first), pillager.distanceToSqr(second))).orElse(null);
		if (golem == null || !pillager.hasLineOfSight(golem)) {
			if (golem != null) return false;
			if (state.lastGolemId != null && ticks - state.lastGolemTick <= 20L * 10L) {
				if (tryPlay(pillager, 21, null, "golem_left")) {
					state.lastGolemId = null;
					return true;
				}
				return false;
			}
			state.lastGolemId = null;
			return false;
		}
		boolean newlySeen = !golem.getUUID().equals(state.lastGolemId);
		state.lastGolemId = golem.getUUID();
		state.lastGolemTick = ticks;
		long nearbyVillagers = level.getEntitiesOfClass(Villager.class, golem.getBoundingBox().inflate(8.0), Villager::isAlive).size();
		int line = golem.getHealth() < golem.getMaxHealth() ? 23
			: golem.getTarget() instanceof Villager || nearbyVillagers > 0 ? 22
			: golem.getTarget() == pillager ? 18
			: golem.distanceTo(pillager) < 4.0 ? 20
			: golem.distanceTo(pillager) > 7.0 ? 24
			: newlySeen ? 17
			: 19;
		return tryPool(pillager, "golem:" + golem.getUUID(), golem, DIALOGUE_COOLDOWN, GOLEM_LINES);
	}

	private static boolean processVillager(Mob pillager, PillagerState state, ServerLevel level) {
		var villagers = level.getEntitiesOfClass(Villager.class,
			pillager.getBoundingBox().inflate(REACTION_RANGE), entity -> entity.isAlive() && pillager.hasLineOfSight(entity));
		if (villagers.isEmpty()) return false;
		Villager villager = villagers.stream()
			.min((first, second) -> Double.compare(pillager.distanceToSqr(first), pillager.distanceToSqr(second))).orElseThrow();
		double distance = villager.distanceTo(pillager);
		Vec3 awayFromPillager = villager.position().subtract(pillager.position()).normalize();
		Vec3 villagerMotion = villager.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		boolean fleeing = villagerMotion.lengthSqr() > 0.0064
			&& villagerMotion.normalize().dot(awayFromPillager) > 0.35;
		int line = villagers.size() >= 4 ? 13
			: fleeing ? 15
			: distance < 2.5 ? 12
			: villager.isBaby() ? 16
			: villagers.size() >= 2 ? 14
			: isLookingAt(villager, pillager) ? 10
			: state.lastVillagerId == null || !state.lastVillagerId.equals(villager.getUUID()) ? 9
			: 11;
		state.lastVillagerId = villager.getUUID();
		if (fleeing) return tryPool(pillager, "villager_flee:" + villager.getUUID(), villager, DIALOGUE_COOLDOWN, VILLAGER_FLEE_LINES);
		return tryPool(pillager, "villager:" + villager.getUUID(), villager, DIALOGUE_COOLDOWN,
			line, VILLAGER_SIGHTING_LINES);
	}

	private static boolean processPillagerGroup(Mob pillager, PillagerState state, ServerLevel level) {
		Mob buddy = level.getEntitiesOfClass(Mob.class, pillager.getBoundingBox().inflate(8.0),
			entity -> entity != pillager && isPillager(entity)).stream()
			.min((first, second) -> Double.compare(pillager.distanceToSqr(first), pillager.distanceToSqr(second))).orElse(null);
		if (buddy == null) {
			state.lastBuddyId = null;
			state.buddyNearbySince = 0L;
			return false;
		}
		boolean newBuddy = !buddy.getUUID().equals(state.lastBuddyId);
		if (newBuddy) {
			state.lastBuddyId = buddy.getUUID();
			state.buddyNearbySince = ticks;
		}
		if (pillager.distanceToSqr(buddy) > 36.0) return tryPlay(pillager, 28, buddy, "pillager_group:" + buddy.getUUID());
		if (!newBuddy && ticks - state.buddyNearbySince <= 20L * 30L) return false;
		int line = tryPoolLine(pillager, GROUP_GAG_LINES, buddy,
			"pillager_group:" + buddy.getUUID(), GROUP_GAG_COOLDOWN);
		if (WAVE_LINES.contains(line)) waveNearbyPillagers(pillager, level);
		return line >= 0;
	}

	private static boolean processRaid(Mob pillager, ServerLevel level) {
		Villager villager = level.getEntitiesOfClass(Villager.class,
			pillager.getBoundingBox().inflate(24.0), entity -> entity.isAlive()).stream()
			.min((first, second) -> Double.compare(pillager.distanceToSqr(first), pillager.distanceToSqr(second))).orElse(null);
		boolean patrolNearby = !level.getEntitiesOfClass(Mob.class, pillager.getBoundingBox().inflate(12.0),
			entity -> entity != pillager && isPillager(entity)).isEmpty();
		int line = nearBlock(level, pillager.blockPosition(), Blocks.BELL, 6) ? 30
			: patrolNearby ? 29
			: villager != null ? 31
			: 32;
		return tryPool(pillager, "raid:" + (villager == null ? "active" : villager.getUUID()), villager,
			DIALOGUE_COOLDOWN, RAID_LINES);
	}

	private static boolean playAmbient(Mob pillager, ServerLevel level) {
		int line;
		if (nearBlock(level, pillager.blockPosition(), Blocks.BELL, 6)) line = 47;
		else if (pillager.getMainHandItem().getItem() instanceof CrossbowItem) line = 43;
		else if (!level.getEntitiesOfClass(Villager.class, pillager.getBoundingBox().inflate(16.0), Villager::isAlive).isEmpty()) {
			line = 44;
		} else {
			line = new int[] {41, 42, 45, 46, 48}[RandomSource.create().nextInt(5)];
		}
		return tryPool(pillager, "ambient", null, DIALOGUE_COOLDOWN, line, PATROL_LINES);
	}

	private static boolean nearBlock(ServerLevel level, BlockPos center, net.minecraft.world.level.block.Block block, int radius) {
		for (int x = -radius; x <= radius; x++) {
			for (int y = -2; y <= 2; y++) {
				for (int z = -radius; z <= radius; z++) {
					if (level.getBlockState(center.offset(x, y, z)).is(block)) return true;
				}
			}
		}
		return false;
	}

	private static boolean isLookingAt(LivingEntity player, LivingEntity target) {
		Vec3 direction = target.getEyePosition().subtract(player.getEyePosition()).normalize();
		return player.getLookAngle().dot(direction) > 0.94;
	}

	private static boolean playerIsArmored(Player player) {
		return !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
			|| !player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
	}

	private static boolean tryPlay(Mob pillager, int line, LivingEntity target, String context) {
		return ContextualDialogueController.playPillagerDialogue(pillager,
			"pillager_" + String.format("%03d", line),
			"pillager_" + context + ":" + pillager.getUUID(), DIALOGUE_COOLDOWN, target);
	}

	private static boolean tryPool(Mob pillager, String context, LivingEntity target, long cooldown, int... lines) {
		return tryPoolLine(pillager, lines, target, context, cooldown) >= 0;
	}

	private static boolean tryPool(Mob pillager, String context, LivingEntity target, long cooldown, int firstLine, int[] lines) {
		int[] combined = new int[lines.length + 1];
		combined[0] = firstLine;
		System.arraycopy(lines, 0, combined, 1, lines.length);
		return tryPool(pillager, context, target, cooldown, combined);
	}

	private static int tryPoolLine(Mob pillager, int[] lines, LivingEntity target, String context, long cooldown) {
		if (lines.length == 0) return -1;
		String poolKey = pillager.getUUID() + ":" + context;
		Integer previous = LAST_POOL_LINES.get(poolKey);
		int start = RandomSource.create().nextInt(lines.length);
		for (int pass = 0; pass < 2; pass++) {
			for (int offset = 0; offset < lines.length; offset++) {
				int line = lines[(start + offset) % lines.length];
				if (pass == 0 && previous != null && lines.length > 1 && line == previous) continue;
				if (tryPlay(pillager, line, target, context, cooldown)) {
					LAST_POOL_LINES.put(poolKey, line);
					return line;
				}
			}
		}
		return -1;
	}

	private static boolean tryPlay(Mob pillager, int line, LivingEntity target, String context, long cooldown) {
		return ContextualDialogueController.playPillagerDialogue(pillager,
			"pillager_" + String.format("%03d", line),
			"pillager_" + context + ":" + pillager.getUUID(), cooldown, target);
	}

	private static void waveNearbyPillagers(Mob speaker, ServerLevel level) {
		for (Mob buddy : level.getEntitiesOfClass(Mob.class, speaker.getBoundingBox().inflate(10.0),
				entity -> entity != speaker && isPillager(entity))) {
			DialogueAnimationNetwork.send(level, buddy, "pillager_wave_only", 0, 180);
		}
	}

	private static void processPendingVillagerShots() {
		PENDING_VILLAGER_SHOTS.removeIf(pending -> {
			if (ticks < pending.dueTick()) return false;
			Mob shooter = pending.level().getEntity(pending.pillagerId()) instanceof Mob mob ? mob : null;
			Villager villager = pending.level().getEntity(pending.villagerId()) instanceof Villager entity ? entity : null;
			if (shooter == null || !shooter.isAlive() || villager == null || !villager.isAlive()) return true;
			if (tryPool(shooter, "villager_arrow_hit", villager, 20L * 8L, PILLAGER_SHOT_HIT_LINES)) return true;
			return ticks >= pending.expiresAt();
		});
	}

	private static boolean isPillager(Mob mob) {
		return isEntity(mob, "pillager");
	}

	private static boolean isEntity(Mob mob, String path) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals(path);
	}

	private static long nextAmbientDelay() {
		return AMBIENT_MIN_DELAY + RandomSource.create().nextInt((int) (AMBIENT_MAX_DELAY - AMBIENT_MIN_DELAY + 1L));
	}

	private static final class PillagerState {
		private long nextAmbientTick;
		private long nextNearbyTick;
		private long nextPlayerCommentTick;
		private long lastProcessedTick = Long.MIN_VALUE;
		private long playerLeftAt;
		private UUID lastPlayerId;
		private int stareTicks;
		private boolean playerNearby;
		private boolean wasAggressive;
		private boolean wasAimingCrossbow;
		private boolean wasChargingCrossbow;
		private UUID lastVillagerId;
		private UUID lastBuddyId;
		private long buddyNearbySince;
		private UUID lastGolemId;
		private long lastGolemTick;

		private PillagerState(long nextAmbientTick) {
			this.nextAmbientTick = nextAmbientTick;
		}
	}

	private record PendingVillagerShot(ServerLevel level, UUID pillagerId, UUID villagerId, long dueTick, long expiresAt) {
	}
}
