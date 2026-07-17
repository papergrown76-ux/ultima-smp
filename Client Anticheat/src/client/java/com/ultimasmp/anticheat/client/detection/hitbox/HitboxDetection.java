package com.ultimasmp.anticheat.client.detection.hitbox;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.AttackEvent;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;

/**
 * Hitbox-Erweiterungs-Erkennung: auffällig hohe Trefferquote bei
 * ungewöhnlichen Winkeln.
 *
 * Wer regelmäßig trifft, obwohl der Blick messbar am Opfer vorbeigeht,
 * vergrößert vermutlich die Hitboxen client-seitig. Ausgewertet wird das
 * Verhältnis "schiefer" Treffer über die letzten zugeordneten Angriffe.
 */
public class HitboxDetection implements DetectionModule {
	/** Ab dieser Blick-Abweichung gilt ein Treffer als "schief". */
	private static final double ODD_ANGLE_DEG = 15.0;
	/** Mindestanzahl zugeordneter Treffer für eine Aussage. */
	private static final int MIN_HITS = 6;
	/** Analysefenster in Ticks (15 Sekunden). */
	private static final int WINDOW_TICKS = 300;

	@Override
	public String id() {
		return "hitbox";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.hitbox");
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		// Nur auswerten, wenn dieser Tick einen neuen Treffer gebracht hat
		boolean newHit = false;
		for (AttackEvent event : ctx.tracker().eventsThisTick()) {
			if (event.attackerUuid().equals(data.uuid)) {
				newHit = true;
				break;
			}
		}
		if (!newHit) {
			return;
		}

		int total = 0;
		int odd = 0;
		double offsetSum = 0;
		for (AttackEvent event : data.attackEvents) {
			if (ctx.tick() - event.tick() > WINDOW_TICKS) {
				continue;
			}
			total++;
			offsetSum += event.lookOffsetDeg();
			if (event.lookOffsetDeg() > ODD_ANGLE_DEG) {
				odd++;
			}
		}
		if (total < MIN_HITS) {
			return;
		}

		double oddRatio = (double) odd / total;
		double avgOffset = offsetSum / total;
		if (oddRatio > 0.5 && data.cooldownPassed("hitbox_ratio", ctx.tick(), 100)) {
			ctx.flag(this, data, 15,
					I18n.translate("ultima_anticheat.detail.hitbox.ratio", oddRatio * 100, ODD_ANGLE_DEG));
		} else if (avgOffset > 20.0 && data.cooldownPassed("hitbox_avg", ctx.tick(), 100)) {
			ctx.flag(this, data, 10,
					I18n.translate("ultima_anticheat.detail.hitbox.avg", avgOffset));
		}
	}
}
