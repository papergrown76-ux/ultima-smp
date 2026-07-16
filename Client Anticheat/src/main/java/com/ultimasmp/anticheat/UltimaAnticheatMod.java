package com.ultimasmp.anticheat;

import com.ultimasmp.anticheat.network.ReportC2SPayload;
import com.ultimasmp.anticheat.network.ReportS2CPayload;
import com.ultimasmp.anticheat.network.ServerReportRelay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gemeinsamer Einstiegspunkt (Client UND Server).
 *
 * Die eigentliche Anticheat-Logik ist rein client-seitig (siehe
 * com.ultimasmp.anticheat.client). Hier wird nur der Netzwerkkanal für den
 * Austausch von Verdachtsmeldungen zwischen Mod-Nutzern registriert sowie —
 * falls die Mod auf einem Server läuft — das Relay dafür.
 */
public class UltimaAnticheatMod implements ModInitializer {
	public static final String MOD_ID = "ultima_anticheat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Payload-Typen müssen auf beiden Seiten bekannt sein
		PayloadTypeRegistry.playC2S().register(ReportC2SPayload.ID, ReportC2SPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ReportS2CPayload.ID, ReportS2CPayload.CODEC);

		// Relay: leitet Meldungen zwischen Mod-Nutzern weiter (nur aktiv,
		// wenn ein Server diese Mod geladen hat)
		ServerReportRelay.register();

		LOGGER.info("Ultima Client-Anticheat initialisiert (Netzwerkkanal registriert)");
	}
}
