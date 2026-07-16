package com.ultimasmp.anticheat.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Einfacher Rate-Limiter pro Schlüssel (bewusst ohne Minecraft-Abhängigkeiten,
 * damit er per Unit-Test abgedeckt werden kann).
 *
 * Zwei Regeln, beide optional:
 *  - Mindestabstand zwischen zwei Ereignissen desselben Schlüssels
 *  - Maximalanzahl Ereignisse pro Zeitfenster
 */
public class RateLimiter {
	private final long minGapMs;
	private final long windowMs;
	private final int maxPerWindow;

	private static final class State {
		long lastMs = Long.MIN_VALUE;
		final ArrayDeque<Long> window = new ArrayDeque<>();
	}

	private final Map<Object, State> states = new HashMap<>();

	/**
	 * @param minGapMs     Mindestabstand in ms (0 = keine Abstandsregel)
	 * @param windowMs     Fensterlänge in ms (0 = keine Fensterregel)
	 * @param maxPerWindow Maximalanzahl pro Fenster (ignoriert, wenn windowMs == 0)
	 */
	public RateLimiter(long minGapMs, long windowMs, int maxPerWindow) {
		this.minGapMs = minGapMs;
		this.windowMs = windowMs;
		this.maxPerWindow = maxPerWindow;
	}

	/**
	 * @return true, wenn das Ereignis erlaubt ist (und gezählt wurde);
	 *         false, wenn es die Limits verletzen würde (nicht gezählt)
	 */
	public boolean tryAcquire(Object key, long nowMs) {
		State state = states.get(key);
		if (state == null) {
			state = new State();
			states.put(key, state);
		}
		if (minGapMs > 0 && state.lastMs != Long.MIN_VALUE && nowMs - state.lastMs < minGapMs) {
			return false;
		}
		if (windowMs > 0) {
			final long cutoff = nowMs - windowMs;
			state.window.removeIf(t -> t < cutoff);
			if (state.window.size() >= maxPerWindow) {
				return false;
			}
			state.window.add(nowMs);
		}
		state.lastMs = nowMs;

		// Speicher begrenzen: uralte Einträge gelegentlich entsorgen
		if (states.size() > 1024) {
			final long staleBefore = nowMs - Math.max(windowMs, minGapMs) * 5;
			states.values().removeIf(s -> s.lastMs < staleBefore);
		}
		return true;
	}
}
