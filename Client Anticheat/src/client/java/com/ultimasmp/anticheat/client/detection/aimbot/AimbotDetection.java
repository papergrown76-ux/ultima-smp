package com.ultimasmp.anticheat.client.detection.aimbot;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;
import com.ultimasmp.anticheat.client.util.MathUtil;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.math.Vec3d;

/**
 * Aimbot-/Rotation-Snapping-Erkennung: abrupte, unnatürlich präzise
 * Blickwinkel-Sprünge, die direkt auf dem Kopf eines anderen Spielers landen.
 *
 * Muster: In einem einzigen Tick dreht sich der Blick um einen großen Winkel
 * und zeigt danach fast exakt auf einen Spieler, auf den er vorher NICHT
 * gezeigt hat. Menschen "gleiten" auf ein Ziel zu, Aimbots springen.
 */
public class AimbotDetection implements DetectionModule {
	/** Mindest-Rotationssprung (Grad in einem Tick), um überhaupt zu prüfen. */
	private static final double MIN_SNAP_DEG = 30.0;
	/** Wie präzise der Blick nach dem Sprung auf dem Ziel liegen muss. */
	private static final double ON_TARGET_DEG = 2.5;
	/** Wie weit der Blick vor dem Sprung daneben gelegen haben muss. */
	private static final double PREV_OFF_TARGET_DEG = 12.0;
	/** Maximale Zieldistanz für die Prüfung. */
	private static final double MAX_TARGET_DISTANCE = 8.0;

	@Override
	public String id() {
		return "aimbot";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.aimbot");
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		TickSnapshot now = data.latest();
		if (now == null || !now.consecutive) {
			return;
		}
		double rotDelta = Math.abs(now.deltaYaw) + Math.abs(now.deltaPitch);
		if (rotDelta < MIN_SNAP_DEG) {
			return; // Früh raus: kein Sprung -> keine weitere Rechenzeit
		}

		Vec3d eye = player.getEyePos();
		Vec3d lookNow = MathUtil.directionFromRotation(now.yaw, now.pitch);
		Vec3d lookBefore = MathUtil.directionFromRotation(now.yaw - now.deltaYaw, now.pitch - now.deltaPitch);

		for (AbstractClientPlayerEntity target : ctx.world().getPlayers()) {
			if (target == player || target == ctx.client().player) {
				continue;
			}
			Vec3d toHead = target.getEyePos().subtract(eye);
			if (toHead.length() > MAX_TARGET_DISTANCE) {
				continue;
			}
			double offsetNow = MathUtil.angleBetweenDeg(lookNow, toHead);
			double offsetBefore = MathUtil.angleBetweenDeg(lookBefore, toHead);
			if (offsetNow < ON_TARGET_DEG && offsetBefore > PREV_OFF_TARGET_DEG
					&& data.cooldownPassed("aimbot", ctx.tick(), 5)) {
				double amount = Math.min(25.0, rotDelta / 6.0 + 8.0);
				ctx.flag(this, data, amount,
						I18n.translate("ultima_anticheat.detail.aimbot.snap", rotDelta, target.getName().getString()));
				return;
			}
		}
	}
}
