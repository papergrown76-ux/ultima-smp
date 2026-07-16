package com.ultimasmp.anticheat.client.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistente Einstellungen der Mod. Wird per Gson als JSON im
 * Minecraft-Config-Ordner gespeichert (ultima_anticheat.json).
 */
public class ModConfig {
	/** Benachrichtigungen (Chat + Actionbar) anzeigen? */
	public boolean notificationsEnabled = true;
	/** Zusätzlich einen Sound bei neuen Verdachtsmeldungen abspielen? */
	public boolean soundEnabled = true;
	/** Eigene Verdachtsmeldungen mit anderen Mod-Nutzern teilen? */
	public boolean shareEnabled = true;

	/** Einstellungen pro Detection-Modul, Schlüssel = Modul-ID (z. B. "killaura"). */
	public Map<String, ModuleSetting> modules = new LinkedHashMap<>();

	public static class ModuleSetting {
		/** Deaktivierte Module werden komplett übersprungen (keine Rechenzeit). */
		public boolean enabled = true;
		/** Verdachtswert (0-100), ab dem benachrichtigt und geteilt wird. */
		public int threshold = 50;
	}
}
