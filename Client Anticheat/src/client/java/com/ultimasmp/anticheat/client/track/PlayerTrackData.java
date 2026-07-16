package com.ultimasmp.anticheat.client.track;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.util.math.Vec3d;

/**
 * Beobachtungsdaten zu einem einzelnen (fremden) Spieler: Verlauf der letzten
 * Ticks, Schlag-Zeitpunkte, zugeordnete Treffer und abgeleitete Zähler.
 */
public class PlayerTrackData {
	/** Wie viele Ticks Verlauf vorgehalten werden (3 Sekunden). */
	public static final int HISTORY_SIZE = 60;

	public final UUID uuid;
	public String name;
	/** Client-Tick, in dem der Spieler zuletzt gesehen wurde. */
	public long lastSeenTick;

	/** Verlauf der letzten Ticks; neuester Eintrag am Ende. */
	public final ArrayDeque<TickSnapshot> history = new ArrayDeque<>(HISTORY_SIZE + 1);

	/** Ticks, in denen ein Armschwung begonnen hat (für CPS-Analyse). */
	public final ArrayDeque<Long> swingTicks = new ArrayDeque<>();
	public boolean prevSwinging;
	public int prevSwingTicks;

	/** Diesem Spieler (als Angreifer) zugeordnete Treffer. */
	public final ArrayDeque<AttackEvent> attackEvents = new ArrayDeque<>();

	/** Anti-Knockback: Zustand nach einem erlittenen Treffer. */
	public boolean pendingKnockbackCheck;
	public long hurtTick;
	public Vec3d posAtHurt = Vec3d.ZERO;
	/** Horizontale Richtung, in die der Rückstoß erwartet wird (vom Angreifer weg). */
	public Vec3d expectedKnockbackDir = Vec3d.ZERO;

	// Abgeleitete Zähler (siehe PlayerTracker.update)
	public int airTicks;
	public int useItemTicks;
	public int waterSurfaceTicks;

	/** Pro-Modul-Zeitmarken, z. B. für Flag-Cooldowns (Schlüssel frei wählbar). */
	public final Map<String, Long> timers = new HashMap<>();

	public PlayerTrackData(UUID uuid, String name) {
		this.uuid = uuid;
		this.name = name;
	}

	/** Neuester Snapshot oder null, wenn noch keiner erhoben wurde. */
	public TickSnapshot latest() {
		return history.peekLast();
	}

	/**
	 * Cooldown-Helfer: liefert true (und merkt sich den Tick), wenn seit dem
	 * letzten Aufruf mit diesem Schlüssel mindestens minGap Ticks vergangen sind.
	 */
	public boolean cooldownPassed(String key, long tick, long minGap) {
		Long last = timers.get(key);
		if (last != null && tick - last < minGap) {
			return false;
		}
		timers.put(key, tick);
		return true;
	}

	/** Tick des letzten Armschwungs oder Long.MIN_VALUE. */
	public long lastSwingTick() {
		Long last = swingTicks.peekLast();
		return last == null ? Long.MIN_VALUE : last;
	}

	public void addSnapshot(TickSnapshot snapshot) {
		history.addLast(snapshot);
		while (history.size() > HISTORY_SIZE) {
			history.removeFirst();
		}
	}

	public void addSwing(long tick) {
		swingTicks.addLast(tick);
		while (swingTicks.size() > 60) {
			swingTicks.removeFirst();
		}
	}

	public void addAttackEvent(AttackEvent event) {
		attackEvents.addLast(event);
		while (attackEvents.size() > 30) {
			attackEvents.removeFirst();
		}
	}

	/** Entfernt veraltete Schlag-/Treffer-Daten außerhalb des Analysefensters. */
	public void pruneOld(long tick) {
		for (Iterator<Long> it = swingTicks.iterator(); it.hasNext(); ) {
			if (tick - it.next() > 200) {
				it.remove();
			} else {
				break;
			}
		}
		for (Iterator<AttackEvent> it = attackEvents.iterator(); it.hasNext(); ) {
			if (tick - it.next().tick() > 400) {
				it.remove();
			} else {
				break;
			}
		}
	}
}
