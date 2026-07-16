package com.ultimasmp.anticheat.client.detection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.detection.aimbot.AimbotDetection;
import com.ultimasmp.anticheat.client.detection.antikb.AntiKnockbackDetection;
import com.ultimasmp.anticheat.client.detection.autoclicker.AutoClickerDetection;
import com.ultimasmp.anticheat.client.detection.fly.FlyDetection;
import com.ultimasmp.anticheat.client.detection.hitbox.HitboxDetection;
import com.ultimasmp.anticheat.client.detection.killaura.KillauraDetection;
import com.ultimasmp.anticheat.client.detection.noslow.NoSlowDetection;
import com.ultimasmp.anticheat.client.detection.reach.ReachDetection;
import com.ultimasmp.anticheat.client.detection.scaffold.ScaffoldDetection;
import com.ultimasmp.anticheat.client.detection.speed.SpeedDetection;
import com.ultimasmp.anticheat.client.detection.timer.TimerDetection;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.world.GameMode;

/**
 * Verwaltet alle Detection-Module und führt pro Client-Tick die Checks für
 * jeden beobachteten Spieler aus. Deaktivierte Module werden komplett
 * übersprungen (keine Rechenzeit, keine Notifications, keine GUI-Einträge).
 */
public class DetectionManager {
	/** Spieler weiter als 64 Blöcke werden nicht geprüft. */
	private static final double MAX_CHECK_DISTANCE_SQ = 64.0 * 64.0;

	private final ConfigManager configManager;
	private final List<DetectionModule> modules;
	private final Map<String, DetectionModule> byId = new HashMap<>();

	public DetectionManager(ConfigManager configManager) {
		this.configManager = configManager;
		this.modules = List.of(
				new KillauraDetection(),
				new ReachDetection(),
				new AimbotDetection(),
				new AutoClickerDetection(),
				new NoSlowDetection(),
				new SpeedDetection(),
				new FlyDetection(),
				new ScaffoldDetection(),
				new HitboxDetection(),
				new TimerDetection(),
				new AntiKnockbackDetection()
		);
		for (DetectionModule m : modules) {
			byId.put(m.id(), m);
		}
	}

	public List<DetectionModule> modules() {
		return modules;
	}

	public DetectionModule byId(String id) {
		return byId.get(id);
	}

	public boolean isEnabled(DetectionModule module) {
		return configManager.module(module.id()).enabled;
	}

	/**
	 * Die Angreifer-Zuordnung im PlayerTracker ist nur nötig, wenn mindestens
	 * ein Modul sie konsumiert — sonst wird auch diese Arbeit eingespart.
	 */
	public boolean needsAttackAttribution() {
		return configManager.module("killaura").enabled
				|| configManager.module("reach").enabled
				|| configManager.module("hitbox").enabled
				|| configManager.module("anti_knockback").enabled;
	}

	/**
	 * @param serverUnstable true bei erkanntem Server-Lag; Bewegungs-Module
	 *                       werden dann pausiert (siehe ServerLagMonitor)
	 */
	public void runChecks(DetectionContext ctx, boolean serverUnstable) {
		if (ctx.client().getNetworkHandler() == null || ctx.client().player == null) {
			return;
		}
		for (AbstractClientPlayerEntity player : ctx.world().getPlayers()) {
			if (player == ctx.client().player) {
				continue;
			}
			// Weit entfernte Spieler: Daten sind zu grob, Rechenzeit sparen
			if (player.squaredDistanceTo(ctx.client().player) > MAX_CHECK_DISTANCE_SQ) {
				continue;
			}
			// Ignorierte Spieler (Freunde-Whitelist) komplett überspringen
			if (configManager.isIgnored(player.getName().getString())) {
				continue;
			}
			PlayerTrackData data = ctx.tracker().get(player.getUuid());
			if (data == null || data.latest() == null) {
				continue;
			}
			// Kreativ-/Zuschauer-Spieler dürfen fliegen & schnell sein -> überspringen
			PlayerListEntry entry = ctx.client().getNetworkHandler().getPlayerListEntry(player.getUuid());
			if (entry != null && (entry.getGameMode() == GameMode.CREATIVE || entry.getGameMode() == GameMode.SPECTATOR)) {
				continue;
			}
			for (DetectionModule module : modules) {
				if (serverUnstable && module.movementSensitive()) {
					continue;
				}
				if (isEnabled(module)) {
					module.check(ctx, player, data);
				}
			}
		}
	}
}
