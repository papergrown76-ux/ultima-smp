package com.ultimasmp.anticheat.client.share;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.detection.DetectionManager;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.suspicion.Severity;
import com.ultimasmp.anticheat.client.suspicion.SuspicionManager;
import com.ultimasmp.anticheat.network.ReportC2SPayload;
import com.ultimasmp.anticheat.network.ReportS2CPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

/**
 * Austausch von Verdachtsmeldungen zwischen Mod-Nutzern ("mehrere sehen mehr
 * als einer") über einen eigenen Custom-Payload-Kanal — unsichtbar für
 * Spieler ohne die Mod und getrennt vom öffentlichen Chat.
 *
 * Senden und Empfangen sind beidseitig rate-limitiert, damit niemand den
 * Kanal mit (falschen) Beschuldigungen fluten kann.
 */
public class ShareManager {
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

	/** Mindestabstand zwischen zwei eigenen Meldungen (egal zu wem). */
	private static final long SEND_GLOBAL_GAP_MS = 3000;
	/** Mindestabstand pro Ziel+Modul, damit derselbe Verdacht nicht spammt. */
	private static final long SEND_PER_KEY_GAP_MS = 60_000;
	/** Eingehende Meldungen: Mindestabstand pro Melder. */
	private static final long RECV_PER_REPORTER_GAP_MS = 1000;
	/** Benachrichtigung über fremde Meldungen: Abstand pro Melder+Ziel. */
	private static final long REMOTE_NOTIFY_GAP_MS = 60_000;

	private final ConfigManager configManager;
	private NotificationManager notifications;
	private SuspicionManager suspicion;
	private DetectionManager detections;

	private long lastSendMs;
	private final Map<String, Long> lastSendPerKey = new HashMap<>();
	private final Map<UUID, Long> lastRecvPerReporter = new HashMap<>();
	private final Map<String, Long> lastRemoteNotify = new HashMap<>();

	public ShareManager(ConfigManager configManager) {
		this.configManager = configManager;
	}

	/** Späte Verdrahtung, um zyklische Konstruktor-Abhängigkeiten zu vermeiden. */
	public void wire(SuspicionManager suspicion, NotificationManager notifications, DetectionManager detections) {
		this.suspicion = suspicion;
		this.notifications = notifications;
		this.detections = detections;
	}

	/**
	 * Teilt eine eigene Verdachtsmeldung, sofern Sharing aktiv ist, der Server
	 * den Kanal unterstützt und die Rate-Limits eingehalten werden.
	 */
	public void maybeShare(UUID reportedUuid, String reportedName, String moduleId, int score) {
		if (!configManager.config().shareEnabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || reportedUuid.equals(client.player.getUuid())) {
			return;
		}
		// Ohne Relay auf dem Server (Vanilla-Server) ist der Kanal nicht offen
		if (!ClientPlayNetworking.canSend(ReportC2SPayload.ID)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastSendMs < SEND_GLOBAL_GAP_MS) {
			return;
		}
		String key = reportedUuid + ":" + moduleId;
		Long lastForKey = lastSendPerKey.get(key);
		if (lastForKey != null && now - lastForKey < SEND_PER_KEY_GAP_MS) {
			return;
		}
		lastSendMs = now;
		lastSendPerKey.put(key, now);
		ClientPlayNetworking.send(new ReportC2SPayload(reportedUuid, reportedName, moduleId,
				MathHelper.clamp(score, 0, 100)));
	}

	/** Verarbeitet eine vom Server weitergeleitete Meldung (Client-Thread). */
	public void onReceive(ReportS2CPayload payload, MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		// Eigene oder offensichtlich kaputte Meldungen ignorieren
		if (payload.reporterUuid() == null || payload.reportedUuid() == null
				|| payload.reporterUuid().equals(client.player.getUuid())) {
			return;
		}
		if (!NAME_PATTERN.matcher(payload.reporterName()).matches()
				|| !NAME_PATTERN.matcher(payload.reportedName()).matches()) {
			return;
		}

		// Nur Meldungen zu bekannten UND lokal aktivierten Modulen anzeigen
		DetectionModule module = detections.byId(payload.detectionId());
		if (module == null || !configManager.module(module.id()).enabled) {
			return;
		}

		// Client-seitiger Spam-Schutz zusätzlich zum Server-Relay
		long now = System.currentTimeMillis();
		Long lastRecv = lastRecvPerReporter.get(payload.reporterUuid());
		if (lastRecv != null && now - lastRecv < RECV_PER_REPORTER_GAP_MS) {
			return;
		}
		lastRecvPerReporter.put(payload.reporterUuid(), now);

		int score = MathHelper.clamp(payload.score(), 0, 100);
		suspicion.addRemoteReport(payload.reporterUuid(), payload.reporterName(),
				payload.reportedUuid(), payload.reportedName(), module.id(), score);

		String notifyKey = payload.reporterUuid() + ":" + payload.reportedUuid();
		Long lastNotify = lastRemoteNotify.get(notifyKey);
		if (lastNotify == null || now - lastNotify >= REMOTE_NOTIFY_GAP_MS) {
			lastRemoteNotify.put(notifyKey, now);
			Severity severity = Severity.of(score, configManager.module(module.id()).threshold);
			notifications.notifyRemote(payload.reporterName(), payload.reportedName(),
					module.displayName(), score, severity);
		}
	}
}
