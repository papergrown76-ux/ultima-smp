package com.ultimasmp.anticheat.client.suspicion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gesammelter Verdachtszustand eines Spielers: pro Modul ein Verdachtswert
 * (0-100) plus die von anderen Mod-Nutzern empfangenen Bestätigungen.
 */
public class PlayerSuspicion {
	/** Wie lange fremde Meldungen als "Bestätigung" zählen (10 Minuten). */
	public static final long REMOTE_REPORT_TTL_MS = 10 * 60 * 1000;

	public final UUID uuid;
	public String name;

	/** Verdachtswert pro Modul-ID. */
	public final Map<String, Double> scores = new HashMap<>();
	/** Letzte Benachrichtigung pro Modul-ID (Spam-Schutz). */
	public final Map<String, Long> lastNotifyMs = new HashMap<>();

	/** Modul der letzten Auffälligkeit und Zeitpunkt (für die GUI-Anzeige). */
	public String lastDetectionId = "";
	public long lastFlagMs;

	/** Fremde Meldungen: Melder-UUID -> letzte Meldung. */
	public final Map<UUID, RemoteReport> remoteReports = new HashMap<>();

	public record RemoteReport(String reporterName, String detectionId, int score, long timeMs) {
	}

	public PlayerSuspicion(UUID uuid, String name) {
		this.uuid = uuid;
		this.name = name;
	}

	public double score(String moduleId) {
		return scores.getOrDefault(moduleId, 0.0);
	}

	/** Höchster Verdachtswert über alle Module. */
	public double maxScore() {
		double max = 0;
		for (double s : scores.values()) {
			max = Math.max(max, s);
		}
		return max;
	}

	/** Modul-ID mit dem höchsten Verdachtswert (oder leer). */
	public String worstModuleId() {
		String worst = "";
		double max = 0;
		for (Map.Entry<String, Double> e : scores.entrySet()) {
			if (e.getValue() > max) {
				max = e.getValue();
				worst = e.getKey();
			}
		}
		return worst;
	}

	/** Anzahl unterschiedlicher Melder innerhalb der Gültigkeitsdauer. */
	public int confirmationCount(long nowMs) {
		Set<UUID> reporters = new HashSet<>();
		for (Map.Entry<UUID, RemoteReport> e : remoteReports.entrySet()) {
			if (nowMs - e.getValue().timeMs() <= REMOTE_REPORT_TTL_MS) {
				reporters.add(e.getKey());
			}
		}
		return reporters.size();
	}

	/** true, wenn weder lokale Werte noch gültige fremde Meldungen übrig sind. */
	public boolean isStale(long nowMs) {
		return maxScore() < 0.5
				&& confirmationCount(nowMs) == 0
				&& nowMs - lastFlagMs > REMOTE_REPORT_TTL_MS;
	}
}
