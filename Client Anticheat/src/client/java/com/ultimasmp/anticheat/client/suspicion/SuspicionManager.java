package com.ultimasmp.anticheat.client.suspicion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.config.ModConfig;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.share.ShareManager;

/**
 * Zentrales Verdachtswert-System.
 *
 * Jedes Detection-Modul erhöht über {@link #flag} den Verdachtswert (0-100)
 * eines Spielers. Die Werte fallen über Zeit langsam wieder ab, um einzelne
 * False Positives auszublenden. Beim Überschreiten der konfigurierten
 * Schwelle wird benachrichtigt und (optional) an andere Mod-Nutzer gemeldet.
 */
public class SuspicionManager {
	/** Abbau pro Sekunde. */
	private static final double DECAY_PER_SECOND = 1.0;
	/** Mindestabstand zwischen zwei Benachrichtigungen zum selben Spieler+Modul. */
	private static final long NOTIFY_COOLDOWN_MS = 15_000;

	private final ConfigManager configManager;
	private final NotificationManager notifications;
	private final ShareManager share;

	private final Map<UUID, PlayerSuspicion> suspicions = new HashMap<>();

	public SuspicionManager(ConfigManager configManager, NotificationManager notifications, ShareManager share) {
		this.configManager = configManager;
		this.notifications = notifications;
		this.share = share;
	}

	/**
	 * Erhöht den Verdachtswert eines Spielers für ein Modul.
	 *
	 * @param amount Erhöhung des Verdachtswerts (wird bei 100 gekappt)
	 * @param detail kurze Beschreibung der Auffälligkeit (für die Benachrichtigung)
	 */
	public void flag(UUID uuid, String name, String moduleId, String moduleDisplayName,
			double amount, String detail) {
		PlayerSuspicion s = suspicions.computeIfAbsent(uuid, u -> new PlayerSuspicion(u, name));
		s.name = name;

		double score = Math.min(100.0, s.score(moduleId) + amount);
		s.scores.put(moduleId, score);
		s.lastDetectionId = moduleId;
		long now = System.currentTimeMillis();
		s.lastFlagMs = now;

		ModConfig.ModuleSetting setting = configManager.module(moduleId);
		if (score >= setting.threshold) {
			Severity severity = Severity.of(score, setting.threshold);
			Long lastNotify = s.lastNotifyMs.get(moduleId);
			if (lastNotify == null || now - lastNotify >= NOTIFY_COOLDOWN_MS) {
				s.lastNotifyMs.put(moduleId, now);
				notifications.notifyLocal(name, moduleDisplayName, (int) Math.round(score), severity, detail);
			}
			// Teilen hat eigenes Rate-Limiting im ShareManager
			share.maybeShare(uuid, name, moduleId, (int) Math.round(score));
		}
	}

	/** Von anderen Mod-Nutzern empfangene Meldung eintragen. */
	public void addRemoteReport(UUID reporterUuid, String reporterName,
			UUID reportedUuid, String reportedName, String detectionId, int score) {
		PlayerSuspicion s = suspicions.computeIfAbsent(reportedUuid, u -> new PlayerSuspicion(u, reportedName));
		s.name = reportedName;
		// Bewusst NICHT auf den lokalen Verdachtswert addieren: fremde Meldungen
		// werden nur als Bestätigung angezeigt, damit sich Meldungen nicht
		// gegenseitig aufschaukeln (Feedback-Schleife).
		s.remoteReports.put(reporterUuid,
				new PlayerSuspicion.RemoteReport(reporterName, detectionId, score, System.currentTimeMillis()));
	}

	/** Einmal pro Tick aufrufen; baut die Werte sekündlich ab. */
	public void tick(long tick) {
		if (tick % 20 != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		for (PlayerSuspicion s : suspicions.values()) {
			s.scores.replaceAll((id, score) -> Math.max(0.0, score - DECAY_PER_SECOND));
			s.scores.values().removeIf(score -> score <= 0.0);
		}
		suspicions.values().removeIf(s -> s.isStale(now));
	}

	public PlayerSuspicion get(UUID uuid) {
		return suspicions.get(uuid);
	}

	/** Sortierte Momentaufnahme für die GUI (höchster Verdacht zuerst). */
	public List<PlayerSuspicion> sortedSnapshot() {
		List<PlayerSuspicion> list = new ArrayList<>(suspicions.values());
		list.sort(Comparator.comparingDouble(PlayerSuspicion::maxScore).reversed());
		return list;
	}

	/** Eintrag sofort entfernen (z. B. wenn ein Spieler ignoriert wird). */
	public void remove(UUID uuid) {
		suspicions.remove(uuid);
	}

	public void clear() {
		suspicions.clear();
	}
}
