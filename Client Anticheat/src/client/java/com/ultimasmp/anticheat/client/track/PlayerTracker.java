package com.ultimasmp.anticheat.client.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ultimasmp.anticheat.client.detection.DetectionManager;
import com.ultimasmp.anticheat.client.util.MathUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Erhebt pro Client-Tick den Zustand aller sichtbaren fremden Spieler und
 * leitet daraus Ereignisse ab (Armschwünge, erlittene Treffer, Angreifer-
 * Zuordnung). Die Detection-Module arbeiten ausschließlich auf diesen Daten.
 */
public class PlayerTracker {
	private final DetectionManager detections;
	private final Map<UUID, PlayerTrackData> tracked = new HashMap<>();
	/** In diesem Tick zugeordnete Treffer (für Reach/Killaura/Hitbox). */
	private final List<AttackEvent> eventsThisTick = new ArrayList<>();

	public PlayerTracker(DetectionManager detections) {
		this.detections = detections;
	}

	public PlayerTrackData get(UUID uuid) {
		return tracked.get(uuid);
	}

	public List<AttackEvent> eventsThisTick() {
		return eventsThisTick;
	}

	/** Beim Verlassen der Welt alles verwerfen. */
	public void reset() {
		tracked.clear();
		eventsThisTick.clear();
	}

	public void update(MinecraftClient client, long tick) {
		eventsThisTick.clear();
		ClientWorld world = client.world;
		if (world == null || client.player == null) {
			return;
		}

		// Erlittene Treffer dieses Ticks sammeln (hurtTime-Flanke)
		List<PlayerTrackData> hurtThisTick = new ArrayList<>(2);

		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			if (player == client.player) {
				continue; // Wir beobachten nur andere Spieler
			}
			PlayerTrackData data = tracked.computeIfAbsent(player.getUuid(),
					u -> new PlayerTrackData(u, player.getName().getString()));
			data.name = player.getName().getString();
			data.lastSeenTick = tick;

			if (updateSnapshot(world, player, data, tick)) {
				hurtThisTick.add(data);
			}
			data.pruneOld(tick);
		}

		// Angreifer-Zuordnung nur berechnen, wenn ein Modul sie überhaupt braucht
		if (!hurtThisTick.isEmpty() && detections.needsAttackAttribution()) {
			for (PlayerTrackData victim : hurtThisTick) {
				attributeAttack(world, client, victim, tick);
			}
		}

		// Nicht mehr sichtbare Spieler nach 10 Sekunden vergessen
		tracked.values().removeIf(d -> tick - d.lastSeenTick > 200);
	}

	/**
	 * Erhebt den Snapshot eines Spielers.
	 *
	 * @return true, wenn der Spieler in diesem Tick Schaden erlitten hat
	 */
	private boolean updateSnapshot(ClientWorld world, AbstractClientPlayerEntity player,
			PlayerTrackData data, long tick) {
		TickSnapshot prev = data.latest();
		TickSnapshot snap = new TickSnapshot();
		snap.tick = tick;
		snap.x = player.getX();
		snap.y = player.getY();
		snap.z = player.getZ();
		snap.yaw = MathHelper.wrapDegrees(player.getYaw());
		snap.pitch = player.getPitch();

		snap.consecutive = prev != null && tick - prev.tick == 1;
		if (snap.consecutive) {
			snap.deltaXZ = MathUtil.horizontalDistance(prev.x, prev.z, snap.x, snap.z);
			snap.deltaY = snap.y - prev.y;
			snap.deltaYaw = MathHelper.wrapDegrees(snap.yaw - prev.yaw);
			snap.deltaPitch = snap.pitch - prev.pitch;
		}

		snap.touchingWater = player.isTouchingWater();
		snap.submerged = player.isSubmergedInWater();
		snap.swimming = player.isSwimming();
		snap.usingItem = player.isUsingItem();
		snap.gliding = player.isGliding();
		snap.riding = player.hasVehicle();
		snap.sneaking = player.isSneaking();
		snap.climbing = world.getBlockState(player.getBlockPos()).isIn(BlockTags.CLIMBABLE);
		snap.blockBelowSolid = hasSolidBelow(world, player);
		snap.grounded = player.isOnGround() || snap.blockBelowSolid;
		snap.hurtTime = player.hurtTime;

		// Abgeleitete Zähler
		if (!snap.grounded && !snap.touchingWater && !snap.climbing && !snap.gliding && !snap.riding) {
			data.airTicks++;
		} else {
			data.airTicks = 0;
		}
		data.useItemTicks = snap.usingItem ? data.useItemTicks + 1 : 0;
		// "Wasseroberfläche": im Wasser, aber weder untergetaucht noch schwimmend,
		// dabei schnell und ohne Höhenänderung -> Kandidat für Jesus-Hack
		boolean onWaterSurface = snap.touchingWater && !snap.submerged && !snap.swimming
				&& !snap.riding && snap.consecutive
				&& Math.abs(snap.deltaY) < 0.03 && snap.deltaXZ * 20.0 > 6.0;
		data.waterSurfaceTicks = onWaterSurface ? data.waterSurfaceTicks + 1 : 0;

		// Armschwung-Flanke erkennen (auch Neustart des Schwungs mitten in der Animation)
		boolean swinging = player.handSwinging;
		boolean newSwing = (swinging && !data.prevSwinging)
				|| (swinging && player.handSwingTicks < data.prevSwingTicks);
		if (newSwing) {
			data.addSwing(tick);
		}
		data.prevSwinging = swinging;
		data.prevSwingTicks = player.handSwingTicks;

		boolean justHurt = prev != null && prev.hurtTime <= 0 && snap.hurtTime > 0;
		data.addSnapshot(snap);
		return justHurt;
	}

	/** Prüft, ob direkt unter der Hitbox des Spielers ein solider Block liegt. */
	private boolean hasSolidBelow(ClientWorld world, AbstractClientPlayerEntity player) {
		Box box = player.getBoundingBox();
		double checkY = player.getY() - 0.3;
		double[][] corners = {
				{box.minX + 0.05, box.minZ + 0.05},
				{box.maxX - 0.05, box.minZ + 0.05},
				{box.minX + 0.05, box.maxZ - 0.05},
				{box.maxX - 0.05, box.maxZ - 0.05},
		};
		for (double[] corner : corners) {
			BlockPos pos = BlockPos.ofFloored(corner[0], checkY, corner[1]);
			if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Sucht heuristisch den Angreifer zu einem erlittenen Treffer: ein anderer
	 * Spieler, der in den letzten 3 Ticks geschlagen hat, nahe genug steht und
	 * dessen Blickrichtung am besten zum Opfer passt.
	 */
	private void attributeAttack(ClientWorld world, MinecraftClient client,
			PlayerTrackData victimData, long tick) {
		AbstractClientPlayerEntity victim = findPlayer(world, victimData.uuid);
		if (victim == null) {
			return;
		}

		AbstractClientPlayerEntity bestAttacker = null;
		double bestOffset = Double.MAX_VALUE;
		double bestDistance = 0;

		for (AbstractClientPlayerEntity candidate : world.getPlayers()) {
			if (candidate == victim || candidate == client.player) {
				continue;
			}
			PlayerTrackData candData = tracked.get(candidate.getUuid());
			if (candData == null || tick - candData.lastSwingTick() > 3) {
				continue; // Hat nicht kürzlich geschlagen
			}
			double distance = distanceEyeToBox(candidate, victim);
			if (distance > 6.5) {
				continue; // Außerhalb jeder plausiblen Nahkampf-Reichweite
			}
			Vec3d toVictim = victim.getBoundingBox().getCenter().subtract(candidate.getEyePos());
			double offset = MathUtil.angleBetweenDeg(candidate.getRotationVector(), toVictim);
			if (offset < bestOffset) {
				bestOffset = offset;
				bestAttacker = candidate;
				bestDistance = distance;
			}
		}

		if (bestAttacker == null) {
			return;
		}

		PlayerTrackData attackerData = tracked.get(bestAttacker.getUuid());
		double rotDeltaPreSwing = maxRotDeltaBefore(attackerData, attackerData.lastSwingTick(), 3);

		AttackEvent event = new AttackEvent(bestAttacker.getUuid(), victim.getUuid(),
				victimData.name, tick, bestDistance, bestOffset, rotDeltaPreSwing);
		attackerData.addAttackEvent(event);
		eventsThisTick.add(event);

		// Anti-Knockback-Beobachtung für das Opfer starten
		victimData.pendingKnockbackCheck = true;
		victimData.hurtTick = tick;
		victimData.posAtHurt = victim.getPos();
		Vec3d away = new Vec3d(victim.getX() - bestAttacker.getX(), 0, victim.getZ() - bestAttacker.getZ());
		victimData.expectedKnockbackDir = away.lengthSquared() < 1.0e-6 ? Vec3d.ZERO : away.normalize();
	}

	/** Distanz von den Augen des Angreifers zum nächsten Punkt der Opfer-Hitbox. */
	public static double distanceEyeToBox(AbstractClientPlayerEntity attacker, AbstractClientPlayerEntity victim) {
		Vec3d eye = attacker.getEyePos();
		Box box = victim.getBoundingBox();
		double cx = MathHelper.clamp(eye.x, box.minX, box.maxX);
		double cy = MathHelper.clamp(eye.y, box.minY, box.maxY);
		double cz = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
		double dx = eye.x - cx;
		double dy = eye.y - cy;
		double dz = eye.z - cz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Größte Rotationsänderung (|dYaw|+|dPitch|) in den Ticks vor einem Zeitpunkt. */
	private static double maxRotDeltaBefore(PlayerTrackData data, long beforeTick, int window) {
		double max = 0;
		for (TickSnapshot snap : data.history) {
			if (snap.tick >= beforeTick - window && snap.tick <= beforeTick && snap.consecutive) {
				max = Math.max(max, Math.abs(snap.deltaYaw) + Math.abs(snap.deltaPitch));
			}
		}
		return max;
	}

	private static AbstractClientPlayerEntity findPlayer(ClientWorld world, UUID uuid) {
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			if (player.getUuid().equals(uuid)) {
				return player;
			}
		}
		return null;
	}
}
