package com.ultimasmp.anticheat.client.detection.fly;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Fly-/Jesus-/Step-Erkennung: unnatürliches Y-Achsen-Verhalten ohne
 * Elytra (Kreativ/Zuschauer werden schon im DetectionManager ausgefiltert).
 *
 *  - Hover: sekundenlang in der Luft stehen, ohne zu fallen
 *  - Steigen: in der Luft weiter an Höhe gewinnen (kein Sprungbogen)
 *  - Langzeit: unrealistisch lange Airtime
 *  - Jesus: schnelles "Laufen" auf der Wasseroberfläche ohne zu schwimmen
 *  - Step: sofortiges Hochsetzen um einen ganzen Block ohne Sprung
 *
 * Hinweis: Levitation-Effekte anderer Spieler sind client-seitig nicht
 * sichtbar; die abfallenden Verdachtswerte fangen solche Einzelfälle ab.
 */
public class FlyDetection implements DetectionModule {
	private static final int HOVER_MIN_AIR_TICKS = 12;
	private static final int RISE_MIN_AIR_TICKS = 10;
	private static final int LONG_AIR_TICKS = 80;
	private static final int JESUS_MIN_TICKS = 15;

	@Override
	public String id() {
		return "fly";
	}

	@Override
	public String displayName() {
		return "Fly/Jesus";
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		TickSnapshot now = data.latest();
		if (now == null || now.gliding || now.riding) {
			return;
		}

		// Durchschnittliche Vertikalbewegung der letzten 5 Ticks
		double dySum = 0;
		int dyCount = 0;
		TickSnapshot prev2 = null; // Snapshot von vor 2 Ticks (für Step)
		for (TickSnapshot snap : data.history) {
			long age = ctx.tick() - snap.tick;
			if (age < 5 && snap.consecutive) {
				dySum += snap.deltaY;
				dyCount++;
			}
			if (age == 2) {
				prev2 = snap;
			}
		}
		double avgDy = dyCount > 0 ? dySum / dyCount : 0;

		// Hover: lange Airtime praktisch ohne Vertikalbewegung
		if (data.airTicks > HOVER_MIN_AIR_TICKS && dyCount >= 4 && Math.abs(avgDy) < 0.02
				&& data.cooldownPassed("fly_hover", ctx.tick(), 10)) {
			ctx.flag(this, data, 10, "schwebt seit " + data.airTicks + " Ticks");
		}

		// Steigen ohne Sprungbogen: Nach dem Absprung nimmt dY sofort ab;
		// wer nach 10+ Ticks Luft noch deutlich steigt, fliegt.
		if (data.airTicks > RISE_MIN_AIR_TICKS && dyCount >= 4 && avgDy > 0.1
				&& data.cooldownPassed("fly_rise", ctx.tick(), 10)) {
			ctx.flag(this, data, 12, String.format("steigt in der Luft (%.2f Blöcke/Tick)", avgDy));
		}

		// Sehr lange Airtime (normaler Fall von Weltdecke ~5s wäre mit Fallgeschwindigkeit)
		if (data.airTicks > LONG_AIR_TICKS && data.cooldownPassed("fly_long", ctx.tick(), 40)) {
			ctx.flag(this, data, 8, data.airTicks + " Ticks ohne Bodenkontakt");
		}

		// Jesus: schnelle, ebene Bewegung auf der Wasseroberfläche
		if (data.waterSurfaceTicks > JESUS_MIN_TICKS && data.cooldownPassed("fly_jesus", ctx.tick(), 15)) {
			ctx.flag(this, data, 12, "läuft über Wasser");
		}

		// Step: aus dem Stand einen ganzen Block hochgesetzt (Vanilla-Step ist 0.6)
		if (now.consecutive && now.grounded && prev2 != null && prev2.grounded
				&& now.deltaY > 0.9 && now.deltaY < 1.6
				&& data.cooldownPassed("fly_step", ctx.tick(), 10)) {
			ctx.flag(this, data, 10, String.format("Step um %.1f Blöcke ohne Sprung", now.deltaY));
		}
	}
}
