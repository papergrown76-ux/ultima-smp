package com.ultimasmp.anticheat.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 *  - Strikte Validierung aller Felder (Namen, Detection-ID, Score)
 *  - Der Absender wird server-seitig gesetzt und kann nicht gefälscht werden
 */
public final class ServerReportRelay {
	private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
	private static final Pattern DETECTION_ID_PATTERN = Pattern.compile("^[a-z_]{1,32}$");

	private static final long MIN_GAP_MS = 2000;
	private static final long WINDOW_MS = 60_000;
	private static final int MAX_PER_WINDOW = 20;

	/** Zustand des Rate-Limiters pro meldendem Spieler. */
	private static final class RateState {
		long lastMs;
		final ArrayDeque<Long> window = new ArrayDeque<>();
	}

	// Nur vom Server-Thread benutzt (Fabric ruft Play-Payload-Handler dort auf).
	private static final Map<UUID, RateState> RATE = new HashMap<>();

	private ServerReportRelay() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ReportC2SPayload.ID, (payload, context) -> {
			ServerPlayerEntity sender = context.player();
			if (!isValid(payload)) {
				return; // Ungültige/manipulierte Meldung still verwerfen
			}

			long now = System.currentTimeMillis();
			RateState state = RATE.computeIfAbsent(sender.getUuid(), u -> new RateState());
			if (now - state.lastMs < MIN_GAP_MS) {
				return; // Zu schnell hintereinander -> Spam-Schutz
			}
			state.window.removeIf(t -> now - t > WINDOW_MS);
			if (state.window.size() >= MAX_PER_WINDOW) {
				return; // Minuten-Limit erreicht
			}
			state.lastMs = now;
			state.window.add(now);

			// Absender-Identität wird hier vom Server gesetzt (fälschungssicher).
			ReportS2CPayload out = new ReportS2CPayload(
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

			// Speicher begrenzen: alte Rate-Limiter-Einträge gelegentlich entsorgen
			if (RATE.size() > 512) {
				RATE.entrySet().removeIf(e -> now - e.getValue().lastMs > WINDOW_MS * 5);
			}
		});
	}

	private static boolean isValid(ReportC2SPayload payload) {
		return payload.reportedUuid() != null
				&& payload.reportedName() != null && NAME_PATTERN.matcher(payload.reportedName()).matches()
				&& payload.detectionId() != null && DETECTION_ID_PATTERN.matcher(payload.detectionId()).matches()
				&& payload.score() >= 0 && payload.score() <= 100;
	}
}
