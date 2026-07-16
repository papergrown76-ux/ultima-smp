package com.ultimasmp.anticheat.client.detection.speed;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Speed-/Movement-Anomalie-Erkennung.
 *
 * Zwei Muster:
 *  - anhaltend überhöhte Horizontalgeschwindigkeit (Sprint-Springen erreicht
 *    ~7.1 Blöcke/s, mit Speed II ~9). Status-Effekte ANDERER Spieler sind
 *    client-seitig nicht sichtbar, daher ist die Schwelle bewusst großzügig.
 *  - einzelne extreme Positionssprünge (Burst), die kein Sync-Teleport sind.
 */
public class SpeedDetection implements DetectionModule {
	/** Anhaltende Geschwindigkeit (Blöcke/s), die legal kaum erreichbar ist. */
	private static final double SUSTAINED_BPS = 9.5;
	/** Fensterlänge für die anhaltende Messung (Ticks). */
	private static final int SUSTAINED_WINDOW = 20;
	/** Einzel-Tick-Distanz (Blöcke), die als Burst gilt (= 24 Blöcke/s). */
	private static final double BURST_PER_TICK = 1.2;
	/** Darüber ist es ein Teleport (Ender-Perle, Server-Sync) -> ignorieren. */
	private static final double TELEPORT_PER_TICK = 8.0;

	@Override
	public String id() {
		return "speed";
	}

	@Override
	public String displayName() {
		return "Speed";
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		TickSnapshot now = data.latest();
		if (now == null || !now.consecutive || now.gliding || now.riding || now.touchingWater) {
			return;
		}

		// Burst: einzelner extremer Schritt
		if (now.deltaXZ > BURST_PER_TICK && now.deltaXZ < TELEPORT_PER_TICK
				&& now.hurtTime == 0 // Rückstoß kann kurz sehr schnell sein
				&& data.cooldownPassed("speed_burst", ctx.tick(), 5)) {
			ctx.flag(this, data, 8, String.format("%.1f Blöcke in einem Tick", now.deltaXZ));
		}

		// Anhaltend: Durchschnitt über die letzten SUSTAINED_WINDOW Ticks,
		// nur wenn alle Ticks "sauber" sind (kein Gleiten/Reiten/Wasser/Rückstoß)
		int counted = 0;
		double total = 0;
		for (TickSnapshot snap : data.history) {
			if (ctx.tick() - snap.tick >= SUSTAINED_WINDOW) {
				continue;
			}
			if (!snap.consecutive || snap.gliding || snap.riding || snap.touchingWater || snap.hurtTime > 0) {
				return; // Fenster nicht sauber -> keine Aussage möglich
			}
			total += snap.deltaXZ;
			counted++;
		}
		if (counted < SUSTAINED_WINDOW) {
			return;
		}
		double avgBps = total / counted * 20.0;
		if (avgBps > SUSTAINED_BPS && data.cooldownPassed("speed_sustained", ctx.tick(), 20)) {
			double amount = Math.min(25.0, (avgBps - SUSTAINED_BPS) * 4.0 + 8.0);
			ctx.flag(this, data, amount, String.format("%.1f Blöcke/s über %d Ticks", avgBps, counted));
		}
	}
}
