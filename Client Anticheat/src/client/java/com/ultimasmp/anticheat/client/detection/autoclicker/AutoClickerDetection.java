package com.ultimasmp.anticheat.client.detection.autoclicker;

import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.util.MathUtil;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;

/**
 * AutoClicker-/CPS-Anomalie-Erkennung.
 *
 * Grundlage sind die beobachteten Armschwung-Animationen. Zwei Muster:
 *  - unmöglich hohe Klickrate (über ~22 CPS hält kein Mensch)
 *  - hohe Klickrate bei unmenschlich gleichmäßigen Intervallen
 *    (menschliches Klicken hat immer Jitter; Bots takten konstant)
 */
public class AutoClickerDetection implements DetectionModule {
	/** Mindestanzahl Intervalle, bevor eine Aussage getroffen wird. */
	private static final int MIN_INTERVALS = 8;
	/** Analysefenster in Ticks (6 Sekunden). */
	private static final int WINDOW_TICKS = 120;

	@Override
	public String id() {
		return "autoclicker";
	}

	@Override
	public String displayName() {
		return I18n.translate("ultima_anticheat.module.autoclicker");
	}

	@Override
	public void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data) {
		// Nur rechnen, wenn in diesem Tick ein neuer Schwung dazukam
		if (data.lastSwingTick() != ctx.tick()) {
			return;
		}

		// Intervalle zwischen aufeinanderfolgenden Schwüngen im Fenster sammeln
		double[] intervals = new double[data.swingTicks.size()];
		int count = 0;
		long prev = Long.MIN_VALUE;
		for (long swingTick : data.swingTicks) {
			if (ctx.tick() - swingTick > WINDOW_TICKS) {
				continue;
			}
			if (prev != Long.MIN_VALUE) {
				intervals[count++] = swingTick - prev;
			}
			prev = swingTick;
		}
		if (count < MIN_INTERVALS) {
			return;
		}

		double sum = 0;
		for (int i = 0; i < count; i++) {
			sum += intervals[i];
		}
		double meanInterval = sum / count;      // Ticks pro Klick
		double cps = 20.0 / meanInterval;       // Klicks pro Sekunde
		double stdDev = MathUtil.stdDev(intervals, count);

		if (cps > 22.0 && data.cooldownPassed("autoclicker", ctx.tick(), 20)) {
			ctx.flag(this, data, 20, I18n.translate("ultima_anticheat.detail.autoclicker.high", cps));
		} else if (cps >= 10.0 && stdDev < 0.45 && data.cooldownPassed("autoclicker", ctx.tick(), 20)) {
			ctx.flag(this, data, 12, I18n.translate("ultima_anticheat.detail.autoclicker.uniform", cps, stdDev));
		} else if (cps >= 8.0 && stdDev < 0.25 && data.cooldownPassed("autoclicker", ctx.tick(), 20)) {
			ctx.flag(this, data, 10, I18n.translate("ultima_anticheat.detail.autoclicker.nojitter", cps));
		}
	}
}
