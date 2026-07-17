package com.ultimasmp.anticheat.client.notify;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.suspicion.Severity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Zeigt Verdachtsmeldungen im Spiel an (Chat-Zeile + Actionbar-Overlay,
 * optional Sound) und sammelt alle Meldungen in einer Verlaufsliste, die im
 * GUI nachträglich eingesehen werden kann.
 */
public class NotificationManager {
	private static final int MAX_HISTORY = 100;
	private static final String PREFIX = "[AC] ";

	/** Ein Eintrag der Verlaufsliste. source ist leer bei lokalen Meldungen. */
	public record HistoryEntry(long timeMs, String playerName, String moduleName,
							   int score, Severity severity, String source) {
	}

	private final ConfigManager configManager;
	private final ArrayDeque<HistoryEntry> history = new ArrayDeque<>();

	public NotificationManager(ConfigManager configManager) {
		this.configManager = configManager;
	}

	/** Meldung aus einer eigenen (lokalen) Erkennung. */
	public void notifyLocal(String playerName, String moduleName, int score, Severity severity, String detail) {
		addHistory(new HistoryEntry(System.currentTimeMillis(), playerName, moduleName, score, severity, ""));
		if (!configManager.config().notificationsEnabled) {
			return;
		}
		Text chat = Text.literal(PREFIX).formatted(Formatting.AQUA)
				.append(Text.literal(playerName + " ").formatted(Formatting.WHITE))
				.append(Text.literal(I18n.translate("ultima_anticheat.notify.suspicion_of", moduleName))
						.formatted(severity.formatting))
				.append(Text.literal(" (" + I18n.translate("ultima_anticheat.notify.score", score, severity.label()) + ")")
						.formatted(Formatting.GRAY))
				.append(detail == null || detail.isEmpty()
						? Text.literal("")
						: Text.literal(" – " + detail).formatted(Formatting.DARK_GRAY));
		Text actionBar = Text.literal(PREFIX).formatted(Formatting.AQUA)
				.append(Text.literal(playerName + ": " + moduleName + " (" + score + ")").formatted(severity.formatting));
		show(chat, actionBar);
	}

	/** Meldung, die ein anderer Mod-Nutzer geteilt hat. */
	public void notifyRemote(String reporterName, String playerName, String moduleName, int score, Severity severity) {
		addHistory(new HistoryEntry(System.currentTimeMillis(), playerName, moduleName, score, severity, reporterName));
		if (!configManager.config().notificationsEnabled) {
			return;
		}
		Text chat = Text.literal(PREFIX).formatted(Formatting.AQUA)
				.append(Text.literal(playerName + " ").formatted(Formatting.WHITE))
				.append(Text.literal(I18n.translate("ultima_anticheat.notify.suspicion_of", moduleName))
						.formatted(severity.formatting))
				.append(Text.literal(" (" + score + ") ").formatted(Formatting.GRAY))
				.append(Text.literal(I18n.translate("ultima_anticheat.notify.reported_by", reporterName))
						.formatted(Formatting.DARK_AQUA));
		Text actionBar = Text.literal(PREFIX).formatted(Formatting.AQUA)
				.append(Text.literal(playerName + ": " + moduleName + " – " + reporterName).formatted(severity.formatting));
		show(chat, actionBar);
	}

	private void show(Text chat, Text actionBar) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		client.player.sendMessage(chat, false);      // Chat-Zeile (nur lokal sichtbar)
		client.player.sendMessage(actionBar, true);  // Actionbar-Overlay
		if (configManager.config().soundEnabled) {
			client.getSoundManager().play(
					PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.6f));
		}
	}

	private void addHistory(HistoryEntry entry) {
		history.addFirst(entry); // Neueste zuerst
		while (history.size() > MAX_HISTORY) {
			history.removeLast();
		}
	}

	/** Kopie der Verlaufsliste (neueste zuerst) für die GUI. */
	public List<HistoryEntry> historySnapshot() {
		return new ArrayList<>(history);
	}
}
