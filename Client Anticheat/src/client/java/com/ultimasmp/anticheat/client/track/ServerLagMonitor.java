package com.ultimasmp.anticheat.client.track;

import net.minecraft.client.world.ClientWorld;

/**
 * Erkennt Server-Lag anhand der Welt-Zeit.
 *
 * Der Client zählt die Welt-Zeit lokal pro Tick hoch; das sekündliche
 * Zeit-Sync-Paket des Servers korrigiert sie. Läuft der Server unrund,
 * springt die Zeit dabei (Delta != 1 pro Client-Tick). Da laggende Server
 * Spieler-Positionen stauchen/teleportieren, würden Bewegungs-Checks dann
 * massenhaft False Positives produzieren — deshalb werden sie in einem
 * Fenster nach jedem erkannten Sprung pausiert.
 */
public class ServerLagMonitor {
	/** Wie lange nach einem Zeitsprung Bewegungs-Checks pausieren (Ticks, 3 s). */
	private static final int UNSTABLE_WINDOW_TICKS = 60;
	/** Aufwärmphase nach Weltbeitritt (Ticks). */
	private static final int WARMUP_TICKS = 40;

	private long lastWorldTime = Long.MIN_VALUE;
	private long unstableUntilTick = Long.MIN_VALUE;
	private long firstTick = Long.MIN_VALUE;

	public void update(ClientWorld world, long tick) {
		if (firstTick == Long.MIN_VALUE) {
			firstTick = tick;
		}
		long worldTime = world.getTime();
		if (lastWorldTime != Long.MIN_VALUE) {
			long delta = worldTime - lastWorldTime;
			// delta == 1 ist der Normalfall; alles andere ist eine Zeitkorrektur
			// (Server hinkt hinterher oder holt auf) oder ein Dimensionswechsel.
			if (delta != 1) {
				unstableUntilTick = tick + UNSTABLE_WINDOW_TICKS;
			}
		}
		lastWorldTime = worldTime;
	}

	/** true, solange Bewegungsdaten nicht vertrauenswürdig sind. */
	public boolean isUnstable(long tick) {
		return tick < firstTick + WARMUP_TICKS || tick <= unstableUntilTick;
	}

	public void reset() {
		lastWorldTime = Long.MIN_VALUE;
		unstableUntilTick = Long.MIN_VALUE;
		firstTick = Long.MIN_VALUE;
	}
}
