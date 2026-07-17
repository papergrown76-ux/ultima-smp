package com.ultimasmp.anticheat.client.detection.noslow;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;

/**
 * NoSlow-Erkennung: normale Bewegungsgeschwindigkeit, obwohl der Spieler
 * gerade ein Item benutzt (Essen, Trank, Bogen spannen, Schild blocken).
 *
 * Vanilla bremst die Bewegung während der Item-Benutzung stark ab
 * (Gehen ~2.2 Blöcke/s beim Essen). Wer dabei dauerhaft schneller ist,
 * unterdrückt die Verlangsamung client-seitig.
 */
public class NoSlowDetection implements DetectionModule {
	/** Ab so vielen Ticks Item-Benutzung greift die Prüfung (Anlauf ausblenden). */
	private static final int MIN_USE_TICKS = 6;
	/** Blöcke/Sekunde, die beim Item-Benutzen legal kaum erreichbar sind. */
	private static final double MAX_LEGIT_BPS = 3.6;

	@Override
	public String id() {
		return "noslow";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.noslow");
	}

	@Override
	public boolean movementSensitive() {
		return true;
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		if (data.useItemTicks < MIN_USE_TICKS) {
			return;
		}
		TickSnapshot now = data.latest();
		if (now == null || !now.consecutive || now.riding || now.gliding || now.touchingWater || !now.grounded) {
			return; // Reiten/Elytra/Wasser haben eigene Geschwindigkeiten
		}
		double bps = now.deltaXZ * 20.0;
		if (bps > MAX_LEGIT_BPS && data.cooldownPassed("noslow", ctx.tick(), 10)) {
			double amount = Math.min(20.0, (bps - MAX_LEGIT_BPS) * 5.0 + 6.0);
			ctx.flag(this, data, amount,
					I18n.translate("ultima_anticheat.detail.noslow.fast", bps));
		}
	}
}
