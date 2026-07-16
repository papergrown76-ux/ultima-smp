package com.ultimasmp.anticheat.client.detection.killaura;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.AttackEvent;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Killaura-Erkennung.
 *
 * Indikatoren (jeweils auf heuristisch zugeordnete Treffer bezogen):
 *  - Angriffe außerhalb des Sichtfelds (Blick zeigt weit am Opfer vorbei)
 *  - unnatürlich große Rotationssprünge unmittelbar vor dem Schlag
 *  - Treffer auf mehrere verschiedene Ziele innerhalb weniger Ticks
 */
public class KillauraDetection implements DetectionModule {
	/** Blick-Abweichung, ab der ein Treffer als "außerhalb des Sichtfelds" gilt. */
	private static final double OUT_OF_FOV_DEG = 40.0;
	private static final double FAR_OUT_OF_FOV_DEG = 60.0;
	/** Rotationssprung (Grad/Tick) kurz vor dem Schlag, der als Snap gilt. */
	private static final double PRE_SWING_SNAP_DEG = 45.0;
	/** Fenster (Ticks) für Mehrfachziel-Erkennung. */
	private static final int MULTI_TARGET_WINDOW = 4;

	@Override
	public String id() {
		return "killaura";
	}

	@Override
	public String displayName() {
		return "Killaura";
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		// Nur die in diesem Tick neu zugeordneten Treffer dieses Spielers betrachten
		for (AttackEvent event : ctx.tracker().eventsThisTick()) {
			if (!event.attackerUuid().equals(data.uuid)) {
				continue;
			}

			if (event.lookOffsetDeg() > FAR_OUT_OF_FOV_DEG) {
				ctx.flag(this, data, 22,
						String.format("Treffer %.0f° außerhalb des Sichtfelds", event.lookOffsetDeg()));
			} else if (event.lookOffsetDeg() > OUT_OF_FOV_DEG) {
				ctx.flag(this, data, 14,
						String.format("Treffer %.0f° neben der Blickrichtung", event.lookOffsetDeg()));
			}

			if (event.rotDeltaPreSwing() > PRE_SWING_SNAP_DEG) {
				ctx.flag(this, data, 8,
						String.format("Rotationssprung %.0f°/Tick vor dem Schlag", event.rotDeltaPreSwing()));
			}

			// Mehrere verschiedene Opfer in extrem kurzer Zeit?
			Set<UUID> victims = new HashSet<>();
			for (AttackEvent past : data.attackEvents) {
				if (ctx.tick() - past.tick() <= MULTI_TARGET_WINDOW) {
					victims.add(past.victimUuid());
				}
			}
			if (victims.size() >= 2 && data.cooldownPassed("killaura_multi", ctx.tick(), 10)) {
				ctx.flag(this, data, 18, victims.size() + " Ziele in " + MULTI_TARGET_WINDOW + " Ticks");
			}
		}
	}
}
