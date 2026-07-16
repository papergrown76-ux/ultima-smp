package com.ultimasmp.anticheat.client.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ultimasmp.anticheat.UltimaAnticheatMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Lädt und speichert die Mod-Konfiguration als JSON-Datei im Config-Ordner,
 * damit alle Einstellungen einen Neustart überleben.
 */
public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve("ultima_anticheat.json");
	private ModConfig config = new ModConfig();

	public ModConfig config() {
		return config;
	}

	/** Liefert die Einstellung eines Moduls; legt fehlende Einträge mit Defaults an. */
	public ModConfig.ModuleSetting module(String id) {
		return config.modules.computeIfAbsent(id, k -> new ModConfig.ModuleSetting());
	}

	/** Steht der Spieler auf der Freunde-Whitelist? */
	public boolean isIgnored(String playerName) {
		return config.ignoredPlayers.contains(playerName.toLowerCase(java.util.Locale.ROOT));
	}

	/** Whitelist-Eintrag umschalten und sofort speichern. */
	public void toggleIgnored(String playerName) {
		String key = playerName.toLowerCase(java.util.Locale.ROOT);
		if (!config.ignoredPlayers.remove(key)) {
			config.ignoredPlayers.add(key);
		}
		save();
	}

	public void load() {
		try {
			if (Files.exists(file)) {
				ModConfig loaded = GSON.fromJson(Files.readString(file), ModConfig.class);
				if (loaded != null) {
					if (loaded.modules == null) {
						loaded.modules = new java.util.LinkedHashMap<>();
					}
					if (loaded.ignoredPlayers == null) {
						loaded.ignoredPlayers = new java.util.ArrayList<>();
					}
					config = loaded;
				}
			}
		} catch (Exception e) {
			UltimaAnticheatMod.LOGGER.warn("Konnte Konfiguration nicht laden, verwende Defaults", e);
			config = new ModConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(config));
		} catch (Exception e) {
			UltimaAnticheatMod.LOGGER.warn("Konnte Konfiguration nicht speichern", e);
		}
	}
}
