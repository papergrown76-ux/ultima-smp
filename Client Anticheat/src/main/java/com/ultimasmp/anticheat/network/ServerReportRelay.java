package com.ultimasmp.anticheat.network;

import java.util.regex.Pattern;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Server-seitiges Relay für Verdachtsmeldungen.
 *
 * Läuft nur, wenn die Mod auch auf einem Fabric-Server (oder im integrierten
 * LAN-Server) installiert ist. Der Server leitet eingehende Meldungen an alle
 * anderen Spieler weiter, die den Kanal ebenfalls registriert haben — also nur
 * an Mod-Nutzer. Für alle anderen Spieler bleibt der Kanal unsichtbar.
 *
 * Missbrauchsschutz:
 *  - Mindestabstand zwischen zwei Meldungen desselben Spielers (2 Sekunden)
 *  - Maximal 20 Meldungen pro Spieler und Minute
 *  - Strikte Validierung aller Felder (Protokoll-Version, Namen, ID, Score)
 *  - Der Absender wird server-seitig gesetzt und kann nicht gefälscht werden
 */
public final class ServerReportRelay {
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final Pattern DETECTION_ID_PATTERN = Pattern.compile("^[a-z_]{1,32}$");

	// Nur vom Server-Thread benutzt (Fabric ruft Play-Payload-Handler dort auf).
	private static final RateLimiter RATE = new RateLimiter(2000, 60_000, 20);

	private ServerReportRelay() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ReportC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity sender = context.player();
			if (!isValid(payload)) {
				return; // Ungültige/manipulierte Meldung still verwerfen
			}
			if (!RATE.tryAcquire(sender.getUuid(), System.currentTimeMillis())) {
				return; // Spam-Schutz
			}

			// Absender-Identität wird hier vom Server gesetzt (fälschungssicher).
			ReportS2CPayload out = new ReportS2CPayload(
					AnticheatProtocol.VERSION,
					sender.getUuid(),
					sender.getName().getString(),
					payload.reportedUuid(),
					payload.reportedName(),
					payload.detectionId(),
					MathHelper.clamp(payload.score(), 0, 100)
			);

			for (ServerPlayerEntity player : context.server().getPlayerManager().getPlayerList()) {
				if (player == sender) {
					continue; // Nicht an den Melder selbst zurückschicken
				}
				// Nur an Clients senden, die den Kanal kennen (= Mod installiert)
				if (ServerPlayNetworking.canSend(player, ReportS2CPayload.ID)) {
					ServerPlayNetworking.send(player, out);
				}
			}
		});
	}

	private static boolean isValid(ReportC2SPayload payload) {
		return payload.protocolVersion() == AnticheatProtocol.VERSION
				&& payload.reportedUuid() != null
				&& payload.reportedName() != null && NAME_PATTERN.matcher(payload.reportedName()).matches()
				&& payload.detectionId() != null && DETECTION_ID_PATTERN.matcher(payload.detectionId()).matches()
				&& payload.score() >= 0 && payload.score() <= 100;
	}
}
