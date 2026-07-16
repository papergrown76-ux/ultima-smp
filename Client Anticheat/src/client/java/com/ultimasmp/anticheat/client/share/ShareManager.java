package com.ultimasmp.anticheat.client.share;

import java.util.UUID;
import java.util.regex.Pattern;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.detection.DetectionManager;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.suspicion.Severity;
import com.ultimasmp.anticheat.client.suspicion.SuspicionManager;
import com.ultimasmp.anticheat.network.AnticheatProtocol;
import com.ultimasmp.anticheat.network.RateLimiter;
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

	/** Senden: global mind. 3 s Abstand zwischen zwei eigenen Meldungen. */
	private final RateLimiter sendGlobal = new RateLimiter(3000, 0, 0);
	/** Senden: pro Ziel+Modul mind. 60 s Abstand (derselbe Verdacht spammt nicht). */
	private final RateLimiter sendPerKey = new RateLimiter(60_000, 0, 0);
	/** Empfangen: pro Melder mind. 1 s Abstand, max. 30 Meldungen/Minute. */
	private final RateLimiter recvPerReporter = new RateLimiter(1000, 60_000, 30);
	/** Benachrichtigung über fremde Meldungen: pro Melder+Ziel mind. 60 s. */
	private final RateLimiter remoteNotify = new RateLimiter(60_000, 0, 0);

	private final ConfigManager configManager;
	private NotificationManager notifications;
	private SuspicionManager suspicion;
	private DetectionManager detections;

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
		// Wichtig: erst den spezifischen Limiter prüfen, dann den globalen —
		// sonst "verbraucht" eine ohnehin blockierte Meldung das globale Budget.
		if (!sendPerKey.tryAcquire(reportedUuid + ":" + moduleId, now)) {
			return;
		}
		if (!sendGlobal.tryAcquire("global", now)) {
			return;
		}
		ClientPlayNetworking.send(new ReportC2SPayload(AnticheatProtocol.VERSION,
				reportedUuid, reportedName, moduleId, MathHelper.clamp(score, 0, 100)));
	}

	/** Verarbeitet eine vom Server weitergeleitete Meldung (Client-Thread). */
	public void onReceive(ReportS2CPayload payload, MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		// Fremde Protokollversionen sowie eigene/kaputte Meldungen ignorieren
		if (payload.protocolVersion() != AnticheatProtocol.VERSION
				|| payload.reporterUuid() == null || payload.reportedUuid() == null
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
		// Meldungen über Spieler auf der eigenen Whitelist ignorieren
		if (configManager.isIgnored(payload.reportedName())) {
			return;
		}

		// Client-seitiger Spam-Schutz zusätzlich zum Server-Relay
		long now = System.currentTimeMillis();
		if (!recvPerReporter.tryAcquire(payload.reporterUuid(), now)) {
			return;
		}

		int score = MathHelper.clamp(payload.score(), 0, 100);
		suspicion.addRemoteReport(payload.reporterUuid(), payload.reporterName(),
				payload.reportedUuid(), payload.reportedName(), module.id(), score);

		if (remoteNotify.tryAcquire(payload.reporterUuid() + ":" + payload.reportedUuid(), now)) {
			Severity severity = Severity.of(score, configManager.module(module.id()).threshold);
			notifications.notifyRemote(payload.reporterName(), payload.reportedName(),
					module.displayName(), score, severity);
		}
	}
}
