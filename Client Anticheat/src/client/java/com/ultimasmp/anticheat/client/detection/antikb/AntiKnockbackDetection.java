package com.ultimasmp.anticheat.client.detection.antikb;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.TickSnapshot;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Anti-Knockback-Erkennung: ausbleibender oder stark reduzierter Rückstoß
 * nach einem erlittenen Nahkampf-Treffer.
 *
 * Beim zugeordneten Treffer merkt sich der Tracker Position und erwartete
 * Rückstoß-Richtung (vom Angreifer weg). Einige Ticks später wird gemessen,
 * wie weit sich das Opfer tatsächlich in diese Richtung bewegt hat. Wände
 * hinter dem Opfer und Schild-Blocken werden ausgeschlossen.
 */
public class AntiKnockbackDetection implements DetectionModule {
	/** Nach so vielen Ticks wird der Rückstoß gemessen. */
	private static final int MEASURE_DELAY = 4;
	/** Erwartete Mindest-Verschiebung in Rückstoß-Richtung (Blöcke). */
	private static final double MIN_DISPLACEMENT = 0.08;

	@Override
	public String id() {
		return "anti_knockback";
	}

	@Override
	public String displayName() {
		return "Anti-Knockback";
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		if (!data.pendingKnockbackCheck || ctx.tick() - data.hurtTick < MEASURE_DELAY) {
			return;
		}
		data.pendingKnockbackCheck = false;

		TickSnapshot now = data.latest();
		if (now == null || data.expectedKnockbackDir.lengthSquared() < 1.0e-6) {
			return;
		}
		// Schild-Blocken reduziert Rückstoß legal
		if (now.usingItem || player.isBlocking()) {
			return;
		}
		// Wand im Rücken? Dann kann sich das Opfer nicht wegbewegen.
		if (isBlockedInDirection(ctx, player, data.expectedKnockbackDir)) {
			return;
		}

		// Verschiebung seit dem Treffer, projiziert auf die Rückstoß-Richtung
		Vec3d moved = player.getPos().subtract(data.posAtHurt);
		double alongKb = moved.x * data.expectedKnockbackDir.x + moved.z * data.expectedKnockbackDir.z;
		double vertical = moved.y;

		if (alongKb < MIN_DISPLACEMENT && vertical < 0.05
				&& data.cooldownPassed("antikb", ctx.tick(), 10)) {
			ctx.flag(this, data, 15, String.format(
					"nur %.2f Blöcke Rückstoß nach Treffer", Math.max(0, alongKb)));
		}
	}

	/** Prüft, ob in Rückstoß-Richtung direkt ein solider Block steht. */
	private boolean isBlockedInDirection(DetectionContext ctx, AbstractClientPlayerEntity player, Vec3d dir) {
		Vec3d base = player.getPos().add(dir.multiply(0.8));
		for (double yOffset : new double[]{0.1, 1.0}) { // Fuß- und Körperhöhe
			BlockPos pos = BlockPos.ofFloored(base.x, player.getY() + yOffset, base.z);
			if (!ctx.world().getBlockState(pos).getCollisionShape(ctx.world(), pos).isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
