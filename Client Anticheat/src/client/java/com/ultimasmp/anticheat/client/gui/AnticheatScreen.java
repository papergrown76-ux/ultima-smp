package com.ultimasmp.anticheat.client.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.ultimasmp.anticheat.client.UltimaAnticheatClient;
import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.config.ModConfig;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.suspicion.PlayerSuspicion;
import com.ultimasmp.anticheat.client.suspicion.Severity;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Haupt-GUI der Mod (Toggle: Left Alt).
 *
 * Drei Tabs:
 *  - Verdächtige:    aktuelle Verdachtsliste mit Score, Schweregrad, Zeit und
 *                    Bestätigungen anderer Mod-Nutzer
 *  - Verlauf:        alle bisherigen Benachrichtigungen zum Nachlesen
 *  - Einstellungen:  jedes Detection-Modul einzeln an/aus + Schwellen-Slider,
 *                    dazu globale Schalter (Notifications, Sound, Team-Meldungen)
 */
public class AnticheatScreen extends Screen {
	private enum Tab {
		SUSPECTS("Verdächtige"),
		HISTORY("Verlauf"),
		SETTINGS("Einstellungen");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static final int ROW_HEIGHT = 26;
	private static final int SETTINGS_ROW_HEIGHT = 26;

	/** Zuletzt geöffneter Tab bleibt über Screen-Neuöffnungen erhalten. */
	private static Tab currentTab = Tab.SUSPECTS;

	private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

	private int panelX, panelY, panelW, panelH;
	private int contentX, contentY, contentW, contentH;
	private double scrollSuspects, scrollHistory, scrollSettings;

	public AnticheatScreen() {
		super(Text.literal("Ultima Client-Anticheat"));
	}

	@Override
	protected void init() {
		panelW = Math.min(width - 24, 480);
		panelH = Math.min(height - 24, 280);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;

		contentX = panelX + 8;
		contentY = panelY + 44;
		contentW = panelW - 16;
		contentH = panelH - 52;

		// Tab-Leiste
		int tabW = (panelW - 16 - 8) / 3;
		int tabX = panelX + 8;
		for (Tab tab : Tab.values()) {
			final Tab t = tab;
			ButtonWidget button = ButtonWidget.builder(tabTitle(t), b -> {
				currentTab = t;
				clearAndInit();
			}).dimensions(tabX, panelY + 20, tabW, 20).build();
			button.active = currentTab != t;
			addDrawableChild(button);
			tabX += tabW + 4;
		}

		if (currentTab == Tab.SETTINGS) {
			initSettingsWidgets();
		}
	}

	private Text tabTitle(Tab tab) {
		return currentTab == tab
				? Text.literal(tab.label).formatted(Formatting.AQUA)
				: Text.literal(tab.label);
	}

	/**
	 * Baut die Widgets des Einstellungs-Tabs auf. Wird bei jedem Scrollen neu
	 * aufgerufen; nur im sichtbaren Bereich liegende Widgets werden angelegt.
	 */
	private void initSettingsWidgets() {
		ConfigManager configManager = UltimaAnticheatClient.CONFIG;
		ModConfig config = configManager.config();

		int y = contentY - (int) scrollSettings;

		// Globale Schalter in einer Reihe
		int thirdW = (contentW - 8) / 3;
		addSettingsButton(contentX, y, thirdW,
				globalToggleText("Meldungen", config.notificationsEnabled), b -> {
					config.notificationsEnabled = !config.notificationsEnabled;
					configManager.save();
					b.setMessage(globalToggleText("Meldungen", config.notificationsEnabled));
				});
		addSettingsButton(contentX + thirdW + 4, y, thirdW,
				globalToggleText("Sound", config.soundEnabled), b -> {
					config.soundEnabled = !config.soundEnabled;
					configManager.save();
					b.setMessage(globalToggleText("Sound", config.soundEnabled));
				});
		addSettingsButton(contentX + (thirdW + 4) * 2, y, thirdW,
				globalToggleText("Team", config.shareEnabled), b -> {
					config.shareEnabled = !config.shareEnabled;
					configManager.save();
					b.setMessage(globalToggleText("Team", config.shareEnabled));
				});
		y += SETTINGS_ROW_HEIGHT + 4;

		// Eine Zeile pro Detection-Modul: Toggle + Schwellen-Slider
		for (DetectionModule module : UltimaAnticheatClient.DETECTIONS.modules()) {
			ModConfig.ModuleSetting setting = configManager.module(module.id());

			ButtonWidget toggle = ButtonWidget.builder(moduleToggleText(setting), b -> {
				setting.enabled = !setting.enabled;
				configManager.save();
				b.setMessage(moduleToggleText(setting));
			}).dimensions(contentX + contentW - 170, y, 50, 20).build();
			addSettingsWidget(toggle, y);

			ThresholdSlider slider = new ThresholdSlider(contentX + contentW - 115, y, 115, 20,
					module.id(), setting.threshold);
			addSettingsWidget(slider, y);

			y += SETTINGS_ROW_HEIGHT;
		}
	}

	private void addSettingsButton(int x, int y, int w, Text text, ButtonWidget.PressAction action) {
		ButtonWidget button = ButtonWidget.builder(text, action).dimensions(x, y, w, 20).build();
		addSettingsWidget(button, y);
	}

	/** Fügt ein Widget nur hinzu, wenn seine Zeile im Content-Bereich sichtbar ist. */
	private void addSettingsWidget(net.minecraft.client.gui.widget.ClickableWidget widget, int y) {
		if (y >= contentY - 20 && y <= contentY + contentH - 4) {
			addDrawableChild(widget);
		}
	}

	private Text globalToggleText(String label, boolean on) {
		return Text.literal(label + ": ").append(onOff(on));
	}

	private Text moduleToggleText(ModConfig.ModuleSetting setting) {
		return onOff(setting.enabled);
	}

	private Text onOff(boolean on) {
		return on
				? Text.literal("An").formatted(Formatting.GREEN)
				: Text.literal("Aus").formatted(Formatting.RED);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		// Panel hier zeichnen, damit die Widgets (in render) darüber liegen
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101018);
		context.fill(panelX, panelY, panelX + panelW, panelY + 16, 0xFF1B2733);
		context.drawText(textRenderer, "Ultima Client-Anticheat", panelX + 6, panelY + 4, 0xFF55FFFF, false);
		String hint = "Left Alt: schließen";
		context.drawText(textRenderer, hint,
				panelX + panelW - 6 - textRenderer.getWidth(hint), panelY + 4, 0xFF808890, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
		switch (currentTab) {
			case SUSPECTS -> renderSuspects(context);
			case HISTORY -> renderHistory(context);
			case SETTINGS -> renderSettingsLabels(context);
		}
		context.disableScissor();
	}

	// ------------------------------------------------------------------
	// Tab: Verdächtige
	// ------------------------------------------------------------------

	private void renderSuspects(DrawContext context) {
		ConfigManager configManager = UltimaAnticheatClient.CONFIG;
		List<PlayerSuspicion> list = UltimaAnticheatClient.SUSPICION.sortedSnapshot();
		long now = System.currentTimeMillis();

		int y = contentY - (int) scrollSuspects;
		int shown = 0;
		for (PlayerSuspicion s : list) {
			// Nur Werte aktivierter Module anzeigen; deaktivierte Module
			// erscheinen weder hier noch in Notifications.
			String worstId = "";
			double worstScore = 0;
			for (Map.Entry<String, Double> e : s.scores.entrySet()) {
				DetectionModule module = UltimaAnticheatClient.DETECTIONS.byId(e.getKey());
				if (module == null || !configManager.module(e.getKey()).enabled) {
					continue;
				}
				if (e.getValue() > worstScore) {
					worstScore = e.getValue();
					worstId = e.getKey();
				}
			}
			int confirmations = s.confirmationCount(now);
			if (worstId.isEmpty() && confirmations == 0) {
				continue; // Nichts Anzeigbares
			}
			shown++;

			if (y + ROW_HEIGHT >= contentY && y <= contentY + contentH) {
				renderSuspectRow(context, s, worstId, worstScore, confirmations, now, y);
			}
			y += ROW_HEIGHT;
		}

		if (shown == 0) {
			context.drawText(textRenderer, "Keine Verdachtsfälle – alles ruhig.",
					contentX + 4, contentY + 8, 0xFF9098A0, false);
		}
	}

	private void renderSuspectRow(DrawContext context, PlayerSuspicion s, String worstId,
			double worstScore, int confirmations, long now, int y) {
		DetectionModule module = worstId.isEmpty() ? null : UltimaAnticheatClient.DETECTIONS.byId(worstId);
		int threshold = worstId.isEmpty() ? 50 : UltimaAnticheatClient.CONFIG.module(worstId).threshold;
		Severity severity = Severity.of(worstScore, threshold);

		context.fill(contentX, y, contentX + contentW, y + ROW_HEIGHT - 2, 0x40202830);

		// Zeile 1: Name, Verdachtsart, Score
		context.drawText(textRenderer, s.name, contentX + 4, y + 3, 0xFFFFFFFF, false);
		if (module != null) {
			context.drawText(textRenderer, module.displayName(), contentX + 130, y + 3, severity.color, false);
			String score = String.valueOf((int) Math.round(worstScore));
			context.drawText(textRenderer, "Score " + score, contentX + 230, y + 3, severity.color, false);
		}

		// Zeile 2: Schweregrad, Bestätigungen, Zeitstempel
		int y2 = y + 14;
		if (module != null) {
			context.drawText(textRenderer, severity.label, contentX + 4, y2, severity.color, false);
		}
		if (confirmations >= 2) {
			context.drawText(textRenderer, "Mehrfach bestätigt (von " + confirmations + " Spielern)",
					contentX + 130, y2, 0xFF55FF88, false);
		} else if (confirmations == 1) {
			context.drawText(textRenderer, "bestätigt von 1 Spieler", contentX + 130, y2, 0xFF55FFFF, false);
		}
		if (s.lastFlagMs > 0) {
			String time = timeFormat.format(new Date(s.lastFlagMs));
			context.drawText(textRenderer, time,
					contentX + contentW - 6 - textRenderer.getWidth(time), y2, 0xFF808890, false);
		}
	}

	// ------------------------------------------------------------------
	// Tab: Verlauf
	// ------------------------------------------------------------------

	private void renderHistory(DrawContext context) {
		List<NotificationManager.HistoryEntry> history = UltimaAnticheatClient.NOTIFICATIONS.historySnapshot();
		if (history.isEmpty()) {
			context.drawText(textRenderer, "Noch keine Benachrichtigungen.",
					contentX + 4, contentY + 8, 0xFF9098A0, false);
			return;
		}
		int y = contentY - (int) scrollHistory;
		for (NotificationManager.HistoryEntry entry : history) {
			if (y + 12 >= contentY && y <= contentY + contentH) {
				String line = "[" + timeFormat.format(new Date(entry.timeMs())) + "] "
						+ entry.playerName() + " – " + entry.moduleName()
						+ " (" + entry.score() + ", " + entry.severity().label + ")"
						+ (entry.source().isEmpty() ? "" : " · von " + entry.source());
				context.drawText(textRenderer, line, contentX + 4, y, entry.severity().color, false);
			}
			y += 12;
		}
	}

	// ------------------------------------------------------------------
	// Tab: Einstellungen (Labels; die Widgets liegen als Children im Screen)
	// ------------------------------------------------------------------

	private void renderSettingsLabels(DrawContext context) {
		int y = contentY - (int) scrollSettings + SETTINGS_ROW_HEIGHT + 4;
		for (DetectionModule module : UltimaAnticheatClient.DETECTIONS.modules()) {
			if (y + 20 >= contentY && y <= contentY + contentH) {
				boolean enabled = UltimaAnticheatClient.CONFIG.module(module.id()).enabled;
				context.drawText(textRenderer, module.displayName(), contentX + 4, y + 6,
						enabled ? 0xFFFFFFFF : 0xFF707880, false);
			}
			y += SETTINGS_ROW_HEIGHT;
		}
	}

	// ------------------------------------------------------------------
	// Scrolling
	// ------------------------------------------------------------------

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double step = verticalAmount * 14;
		switch (currentTab) {
			case SUSPECTS -> scrollSuspects = clampScroll(scrollSuspects - step,
					UltimaAnticheatClient.SUSPICION.sortedSnapshot().size() * ROW_HEIGHT);
			case HISTORY -> scrollHistory = clampScroll(scrollHistory - step,
					UltimaAnticheatClient.NOTIFICATIONS.historySnapshot().size() * 12);
			case SETTINGS -> {
				int total = (UltimaAnticheatClient.DETECTIONS.modules().size() + 1) * SETTINGS_ROW_HEIGHT + 8;
				scrollSettings = clampScroll(scrollSettings - step, total);
				clearAndInit(); // Widgets an neue Positionen setzen
			}
		}
		return true;
	}

	private double clampScroll(double value, int totalHeight) {
		double max = Math.max(0, totalHeight - contentH);
		return Math.max(0, Math.min(value, max));
	}

	@Override
	public boolean shouldPause() {
		return false; // Beobachtung läuft weiter, während das GUI offen ist
	}

	/** Slider für die Benachrichtigungs-Schwelle (10-100) eines Moduls. */
	private class ThresholdSlider extends SliderWidget {
		private final String moduleId;

		ThresholdSlider(int x, int y, int w, int h, String moduleId, int current) {
			super(x, y, w, h, Text.literal("Schwelle: " + current), (current - 10) / 90.0);
			this.moduleId = moduleId;
		}

		private int threshold() {
			return (int) Math.round(10 + value * 90);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal("Schwelle: " + threshold()));
		}

		@Override
		protected void applyValue() {
			UltimaAnticheatClient.CONFIG.module(moduleId).threshold = threshold();
			UltimaAnticheatClient.CONFIG.save();
		}
	}
}
