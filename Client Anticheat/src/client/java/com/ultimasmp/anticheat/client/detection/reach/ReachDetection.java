package com.ultimasmp.anticheat.client.detection.reach;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.AttackEvent;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.network.PlayerListEntry;

/**
 * Reach-Erkennung: Schaden auf Distanzen deutlich über dem Vanilla-Limit
 * (~3.0 Blöcke im Survival, gemessen Auge -> Hitbox).
 *
 * Da der Client fremde Positionen nur interpoliert sieht und Latenz die
 * wahrgenommene Distanz verzerrt, wird großzügig Toleranz aufgeschlagen —
 * lieber ein Cheater weniger gemeldet als ein Ehrlicher zu viel.
 */
public class ReachDetection implements DetectionModule {
	/** Basis-Schwelle inkl. Interpolations-Toleranz (Vanilla ~3.0-3.1). */
	private static final double BASE_THRESHOLD = 3.45;
	/** Zusätzliche Toleranz bei hoher Latenz des Angreifers. */
	private static final double HIGH_PING_BONUS = 0.15;
	/** Darüber ist es eher ein Teleport-/Sync-Artefakt als ein Reach-Hack. */
	private static final double IGNORE_ABOVE = 6.0;

	@Override
	public String id() {
		return "reach";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.reach");
	}

	@Override
	public boolean movementSensitive() {
		return true;
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		for (AttackEvent event : ctx.tracker().eventsThisTick()) {
			if (!event.attackerUuid().equals(data.uuid)) {
				continue;
			}
			double threshold = BASE_THRESHOLD;
			PlayerListEntry entry = ctx.client().getNetworkHandler() == null ? null
					: ctx.client().getNetworkHandler().getPlayerListEntry(data.uuid);
			if (entry != null && entry.getLatency() > 100) {
				threshold += HIGH_PING_BONUS;
			}

			double distance = event.distance();
			if (distance > threshold && distance < IGNORE_ABOVE) {
				// Je weiter über der Schwelle, desto stärker steigt der Verdacht
				double amount = Math.min(30.0, (distance - threshold) * 30.0 + 8.0);
				ctx.flag(this, data, amount, I18n.translate("ultima_anticheat.detail.reach.hit", distance));
			}
		}
	}
}
