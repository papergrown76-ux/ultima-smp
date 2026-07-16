package com.ultimasmp.anticheat.client.detection.scaffold;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.MathHelper;

/**
 * Scaffold-Erkennung: unnatürlich schnelles, perfekt ausgerichtetes
 * Blockplatzieren beim Rückwärtslaufen ("Brücken").
 *
 * Der Client sieht fremde Blockplatzierungen nicht als Ereignis, daher wird
 * das Bewegungsmuster geprüft: konstante Höhe, Blick steil nach unten,
 * Bewegung entgegen der Blickrichtung, und unter dem Spieler tauchen Blöcke
 * auf, wo kurz zuvor noch Luft war. Legales Rückwärts-Brücken ist deutlich
 * langsamer und unregelmäßiger.
 */
public class ScaffoldDetection implements DetectionModule {
	/** Analysefenster in Ticks. */
	private static final int WINDOW = 8;
	/** Mindest-Blickneigung nach unten (Grad). */
	private static final float MIN_PITCH = 60.0f;
	/** Mindest-Gesamtstrecke im Fenster (Blöcke), sonst kein "schnelles Brücken". */
	private static final double MIN_DISTANCE = 0.8;

	@Override
	public String id() {
		return "scaffold";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.scaffold");
	}

	@Override
	public boolean movementSensitive() {
		return true;
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		TickSnapshot now = data.latest();
		if (now == null || !now.consecutive || !now.blockBelowSolid || now.riding || now.gliding) {
			return;
		}
		if (now.pitch < MIN_PITCH) {
			return; // Früh raus: Blick nicht nach unten
		}

		TickSnapshot oldest = null;
		double distance = 0;
		boolean hadAirBelow = false;
		int checked = 0;
		for (TickSnapshot snap : data.history) {
			long age = ctx.tick() - snap.tick;
			if (age >= WINDOW) {
				continue;
			}
			if (!snap.consecutive || snap.pitch < MIN_PITCH) {
				return; // Fenster nicht durchgängig im Scaffold-Muster
			}
			if (oldest == null) {
				oldest = snap;
			}
			distance += snap.deltaXZ;
			hadAirBelow |= !snap.blockBelowSolid;
			checked++;
		}
		if (checked < WINDOW - 1 || oldest == null || distance < MIN_DISTANCE) {
			return;
		}
		// Konstante Höhe über das ganze Fenster (Brücken-Ebene)
		if (Math.abs(now.y - oldest.y) > 0.1) {
			return;
		}
		// Bewegungsrichtung vs. Blickrichtung: Rückwärtslaufen
		double moveYaw = Math.toDegrees(Math.atan2(now.z - oldest.z, now.x - oldest.x)) - 90.0;
		float moveVsLook = MathHelper.wrapDegrees((float) (moveYaw - now.yaw));
		if (Math.abs(moveVsLook) < 120.0f) {
			return;
		}

		if (data.cooldownPassed("scaffold", ctx.tick(), WINDOW)) {
			// Stärkerer Verdacht, wenn unter der Laufstrecke eben noch Luft war
			double amount = hadAirBelow ? 14 : 8;
			ctx.flag(this, data, amount, hadAirBelow
					? I18n.translate("ultima_anticheat.detail.scaffold.air")
					: I18n.translate("ultima_anticheat.detail.scaffold.pattern"));
		}
	}
}
