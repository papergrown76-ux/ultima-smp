package com.ultimasmp.anticheat.client.detection.timer;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;
import com.ultimasmp.anticheat.client.util.MathUtil;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;

/**
 * Timer-/Speed-Hack-Erkennung: Aktions- und Bewegungsfrequenz schneller, als
 * der Server-Tick erlaubt.
 *
 * Ein Timer beschleunigt ALLES gleichmäßig — dadurch bewegt sich der Spieler
 * mit unnatürlich konstanter, leicht überhöhter Schrittweite pro Tick.
 * Menschliche Bewegung (Beschleunigen, Springen, Kurven) hat dagegen immer
 * messbare Varianz. Geprüft wird also: überhöhtes Tempo + nahezu null Varianz.
 */
public class TimerDetection implements DetectionModule {
	/** Fensterlänge in Ticks. */
	private static final int WINDOW = 20;
	/** Vanilla-Sprint sind ~0.28 Blöcke/Tick; ab +10% wird es verdächtig. */
	private static final double MIN_PER_TICK = 0.31;
	/** Maximale Standardabweichung der Schrittweite, die als "Roboter" gilt. */
	private static final double MAX_STDDEV = 0.005;

	@Override
	public String id() {
		return "timer";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.timer");
	}

	@Override
	public boolean movementSensitive() {
		return true;
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		TickSnapshot now = data.latest();
		if (now == null || !now.consecutive || now.gliding || now.riding || now.touchingWater) {
			return;
		}
		if (now.deltaXZ < MIN_PER_TICK) {
			return; // Früh raus: aktuell nicht schnell genug für das Muster
		}

		double[] deltas = new double[WINDOW];
		int count = 0;
		for (TickSnapshot snap : data.history) {
			if (ctx.tick() - snap.tick >= WINDOW) {
				continue;
			}
			// Fenster muss lückenlos, am Boden und "sauber" sein
			if (!snap.consecutive || snap.gliding || snap.riding || snap.touchingWater
					|| snap.hurtTime > 0 || !snap.grounded) {
				return;
			}
			if (count < deltas.length) {
				deltas[count++] = snap.deltaXZ;
			}
		}
		if (count < WINDOW) {
			return;
		}

		double sum = 0;
		for (int i = 0; i < count; i++) {
			sum += deltas[i];
		}
		double mean = sum / count;
		double stdDev = MathUtil.stdDev(deltas, count);

		if (mean > MIN_PER_TICK && stdDev < MAX_STDDEV
				&& data.cooldownPassed("timer", ctx.tick(), WINDOW)) {
			ctx.flag(this, data, 12, I18n.translate("ultima_anticheat.detail.timer.uniform", mean * 20.0));
		}
	}
}
