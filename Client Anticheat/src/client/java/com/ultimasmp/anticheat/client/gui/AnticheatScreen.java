package com.ultimasmp.anticheat.client.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ultimasmp.anticheat.client.UltimaAnticheatClient;
import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.config.ModConfig;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.suspicion.PlayerSuspicion;
import com.ultimasmp.anticheat.client.suspicion.Severity;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Haupt-GUI der Mod (Umschalttaste: standardmäßig Left Alt, umbelegbar).
 *
 * Drei Tabs:
 *  - Verdächtige:    aktuelle Verdachtsliste; Klick auf eine Zeile öffnet die
 *                    Detail-Ansicht (alle Modul-Scores, Bestätigungen anderer
 *                    Mod-Nutzer, Ignorieren-Knopf für die Freunde-Whitelist)
 *  - Verlauf:        alle bisherigen Benachrichtigungen zum Nachlesen
 *  - Einstellungen:  jedes Detection-Modul einzeln an/aus + Schwellen-Slider,
 *                    dazu globale Schalter (Meldungen, Sound, Team, HUD)
 */
public class AnticheatScreen extends Screen {
	private enum Tab {
		SUSPECTS("ultima_anticheat.gui.tab.suspects"),
		HISTORY("ultima_anticheat.gui.tab.history"),
		SETTINGS("ultima_anticheat.gui.tab.settings");

		final String translationKey;

		Tab(String translationKey) {
			this.translationKey = translationKey;
		}
	}

	/** Eine anzeigbare Zeile im Verdächtigen-Tab (Snapshot, 1x pro Sekunde erneuert). */
	private record SuspectRow(PlayerSuspicion suspicion, String worstId, double worstScore, int confirmations) {
	}

	private static final int ROW_HEIGHT = 26;
	private static final int SETTINGS_ROW_HEIGHT = 26;

	/** Zuletzt geöffneter Tab bleibt über Screen-Neuöffnungen erhalten. */
	private static Tab currentTab = Tab.SUSPECTS;

	private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

	private int panelX, panelY, panelW, panelH;
	private int contentX, contentY, contentW, contentH;
	private double scrollSuspects, scrollHistory, scrollSettings;

	/** Aktuell in der Detail-Ansicht angezeigter Spieler (null = Liste). */
	private UUID selectedUuid;
	private List<SuspectRow> rows = new ArrayList<>();
	private int ticksSinceRebuild;

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
				selectedUuid = null;
				clearAndInit();
			}).dimensions(tabX, panelY + 20, tabW, 20).build();
			button.active = currentTab != t;
			addDrawableChild(button);
			tabX += tabW + 4;
		}

		switch (currentTab) {
			case SUSPECTS -> {
				if (selectedUuid == null) {
					initSuspectRows();
				} else {
					initDetailWidgets();
				}
			}
			case SETTINGS -> initSettingsWidgets();
			case HISTORY -> {
			}
		}
	}

	private Text tabTitle(Tab tab) {
		Text label = Text.translatable(tab.translationKey);
		return currentTab == tab ? label.copy().formatted(Formatting.AQUA) : label;
	}

	// ------------------------------------------------------------------
	// Verdächtigen-Liste: Daten-Snapshot + unsichtbare Klick-Widgets
	// ------------------------------------------------------------------

	/** Baut den Zeilen-Snapshot und legt für sichtbare Zeilen Klickflächen an. */
	private void initSuspectRows() {
		ConfigManager configManager = UltimaAnticheatClient.CONFIG;
		long now = System.currentTimeMillis();
		rows = new ArrayList<>();

		for (PlayerSuspicion s : UltimaAnticheatClient.SUSPICION.sortedSnapshot()) {
			// Nur Werte aktivierter Module anzeigen; deaktivierte Module
			// erscheinen weder hier noch in Notifications.
			String worstId = "";
			double worstScore = 0;
			for (Map.Entry<String, Double> e : s.scores.entrySet()) {
				if (UltimaAnticheatClient.DETECTIONS.byId(e.getKey()) == null
						|| !configManager.module(e.getKey()).enabled) {
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
			rows.add(new SuspectRow(s, worstId, worstScore, confirmations));
		}

		int y = contentY - (int) scrollSuspects;
		for (SuspectRow row : rows) {
			if (y + ROW_HEIGHT >= contentY && y <= contentY + contentH - 4) {
				final UUID uuid = row.suspicion().uuid;
				addDrawableChild(new RowClickWidget(contentX, y, contentW, ROW_HEIGHT - 2, () -> {
					selectedUuid = uuid;
					clearAndInit();
				}));
			}
			y += ROW_HEIGHT;
		}
	}

	/** Widgets der Detail-Ansicht: Zurück + Ignorieren. */
	private void initDetailWidgets() {
		int buttonY = panelY + panelH - 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable("ultima_anticheat.gui.back"), b -> {
			selectedUuid = null;
			clearAndInit();
		}).dimensions(contentX, buttonY, 90, 20).build());

		PlayerSuspicion s = UltimaAnticheatClient.SUSPICION.get(selectedUuid);
		if (s != null) {
			final String playerName = s.name;
			boolean ignored = UltimaAnticheatClient.CONFIG.isIgnored(playerName);
			Text label = Text.translatable(ignored
					? "ultima_anticheat.gui.unignore"
					: "ultima_anticheat.gui.ignore");
			addDrawableChild(ButtonWidget.builder(label, b -> {
				UltimaAnticheatClient.CONFIG.toggleIgnored(playerName);
				if (UltimaAnticheatClient.CONFIG.isIgnored(playerName)) {
					// Ignorierte Spieler sofort aus der Verdachtsliste nehmen
					UltimaAnticheatClient.SUSPICION.remove(selectedUuid);
					selectedUuid = null;
				}
				clearAndInit();
			}).dimensions(contentX + 96, buttonY, 150, 20).build());
		}
	}

	// ------------------------------------------------------------------
	// Einstellungs-Tab
	// ------------------------------------------------------------------

	/**
	 * Baut die Widgets des Einstellungs-Tabs auf. Wird bei jedem Scrollen neu
	 * aufgerufen; nur im sichtbaren Bereich liegende Widgets werden angelegt.
	 */
	private void initSettingsWidgets() {
		ConfigManager configManager = UltimaAnticheatClient.CONFIG;
		ModConfig config = configManager.config();

		int y = contentY - (int) scrollSettings;

		// Globale Schalter in einer Reihe
		int quarterW = (contentW - 12) / 4;
		addSettingsButton(contentX, y, quarterW,
				globalToggleText("ultima_anticheat.gui.notifications", config.notificationsEnabled), b -> {
					config.notificationsEnabled = !config.notificationsEnabled;
					configManager.save();
					b.setMessage(globalToggleText("ultima_anticheat.gui.notifications", config.notificationsEnabled));
				});
		addSettingsButton(contentX + (quarterW + 4), y, quarterW,
				globalToggleText("ultima_anticheat.gui.sound", config.soundEnabled), b -> {
					config.soundEnabled = !config.soundEnabled;
					configManager.save();
					b.setMessage(globalToggleText("ultima_anticheat.gui.sound", config.soundEnabled));
				});
		addSettingsButton(contentX + (quarterW + 4) * 2, y, quarterW,
				globalToggleText("ultima_anticheat.gui.team", config.shareEnabled), b -> {
					config.shareEnabled = !config.shareEnabled;
					configManager.save();
					b.setMessage(globalToggleText("ultima_anticheat.gui.team", config.shareEnabled));
				});
		addSettingsButton(contentX + (quarterW + 4) * 3, y, quarterW,
				globalToggleText("ultima_anticheat.gui.hud", config.hudEnabled), b -> {
					config.hudEnabled = !config.hudEnabled;
					configManager.save();
					b.setMessage(globalToggleText("ultima_anticheat.gui.hud", config.hudEnabled));
				});
		y += SETTINGS_ROW_HEIGHT + 4;

		// Eine Zeile pro Detection-Modul: Toggle + Schwellen-Slider
		for (DetectionModule module : UltimaAnticheatClient.DETECTIONS.modules()) {
			ModConfig.ModuleSetting setting = configManager.module(module.id());

			ButtonWidget toggle = ButtonWidget.builder(onOff(setting.enabled), b -> {
				setting.enabled = !setting.enabled;
				configManager.save();
				b.setMessage(onOff(setting.enabled));
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
	private void addSettingsWidget(ClickableWidget widget, int y) {
		if (y >= contentY - 20 && y <= contentY + contentH - 4) {
			addDrawableChild(widget);
		}
	}

	private Text globalToggleText(String translationKey, boolean on) {
		return Text.translatable(translationKey).copy().append(": ").append(onOff(on));
	}

	private Text onOff(boolean on) {
		return on
				? Text.translatable("ultima_anticheat.gui.on").formatted(Formatting.GREEN)
				: Text.translatable("ultima_anticheat.gui.off").formatted(Formatting.RED);
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		// Panel hier zeichnen, damit die Widgets (in render) darüber liegen
		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0101018);
		context.fill(panelX, panelY, panelX + panelW, panelY + 16, 0xFF1B2733);
		context.drawText(textRenderer, "Ultima Client-Anticheat", panelX + 6, panelY + 4, 0xFF55FFFF, false);
		String keyName = UltimaAnticheatClient.TOGGLE_BINDING == null ? "Alt"
				: UltimaAnticheatClient.TOGGLE_BINDING.getBoundKeyLocalizedText().getString();
		String hint = I18n.translate("ultima_anticheat.gui.close_hint", keyName);
		context.drawText(textRenderer, hint,
				panelX + panelW - 6 - textRenderer.getWidth(hint), panelY + 4, 0xFF808890, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		context.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
		switch (currentTab) {
			case SUSPECTS -> {
				if (selectedUuid == null) {
					renderSuspects(context, mouseX, mouseY);
				} else {
					renderDetail(context);
				}
			}
			case HISTORY -> renderHistory(context);
			case SETTINGS -> renderSettingsLabels(context);
		}
		context.disableScissor();

		// Lag-Hinweis: Bewegungs-Checks sind gerade pausiert
		if (UltimaAnticheatClient.LAG_MONITOR != null
				&& UltimaAnticheatClient.LAG_MONITOR.isUnstable(UltimaAnticheatClient.currentTick())) {
			String warning = I18n.translate("ultima_anticheat.gui.lag_paused");
			context.drawText(textRenderer, warning,
					panelX + panelW - 6 - textRenderer.getWidth(warning),
					panelY + panelH - 12, 0xFFFFA033, false);
		}
	}

	private void renderSuspects(DrawContext context, int mouseX, int mouseY) {
		if (rows.isEmpty()) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.empty_suspects"),
					contentX + 4, contentY + 8, 0xFF9098A0, false);
			return;
		}
		int y = contentY - (int) scrollSuspects;
		for (SuspectRow row : rows) {
			if (y + ROW_HEIGHT >= contentY && y <= contentY + contentH) {
				boolean hovered = mouseX >= contentX && mouseX <= contentX + contentW
						&& mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
				renderSuspectRow(context, row, y, hovered);
			}
			y += ROW_HEIGHT;
		}
	}

	private void renderSuspectRow(DrawContext context, SuspectRow row, int y, boolean hovered) {
		PlayerSuspicion s = row.suspicion();
		DetectionModule module = row.worstId().isEmpty() ? null
				: UltimaAnticheatClient.DETECTIONS.byId(row.worstId());
		int threshold = row.worstId().isEmpty() ? 50
				: UltimaAnticheatClient.CONFIG.module(row.worstId()).threshold;
		Severity severity = Severity.of(row.worstScore(), threshold);

		context.fill(contentX, y, contentX + contentW, y + ROW_HEIGHT - 2,
				hovered ? 0x60304050 : 0x40202830);

		// Zeile 1: Name, Verdachtsart, Score
		context.drawText(textRenderer, s.name, contentX + 4, y + 3, 0xFFFFFFFF, false);
		if (module != null) {
			context.drawText(textRenderer, module.displayName(), contentX + 130, y + 3, severity.color, false);
			context.drawText(textRenderer,
					I18n.translate("ultima_anticheat.gui.score", (int) Math.round(row.worstScore())),
					contentX + 230, y + 3, severity.color, false);
		}

		// Zeile 2: Schweregrad, Bestätigungen, Zeitstempel
		int y2 = y + 14;
		if (module != null) {
			context.drawText(textRenderer, severity.label(), contentX + 4, y2, severity.color, false);
		}
		if (row.confirmations() >= 2) {
			context.drawText(textRenderer,
					I18n.translate("ultima_anticheat.gui.confirmed_multi", row.confirmations()),
					contentX + 130, y2, 0xFF55FF88, false);
		} else if (row.confirmations() == 1) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.confirmed_one"),
					contentX + 130, y2, 0xFF55FFFF, false);
		}
		if (s.lastFlagMs > 0) {
			String time = timeFormat.format(new Date(s.lastFlagMs));
			context.drawText(textRenderer, time,
					contentX + contentW - 6 - textRenderer.getWidth(time), y2, 0xFF808890, false);
		}
	}

	/** Detail-Ansicht eines Spielers: alle Modul-Scores + Bestätigungen. */
	private void renderDetail(DrawContext context) {
		PlayerSuspicion s = UltimaAnticheatClient.SUSPICION.get(selectedUuid);
		if (s == null) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.empty_suspects"),
					contentX + 4, contentY + 8, 0xFF9098A0, false);
			return;
		}
		int y = contentY + 2;
		String title = I18n.translate("ultima_anticheat.gui.detail.title", s.name);
		if (UltimaAnticheatClient.CONFIG.isIgnored(s.name)) {
			title += " (" + I18n.translate("ultima_anticheat.gui.ignored_badge") + ")";
		}
		context.drawText(textRenderer, title, contentX + 4, y, 0xFF55FFFF, false);
		y += 14;

		// Alle Scores aktivierter Module
		boolean anyScore = false;
		for (Map.Entry<String, Double> e : s.scores.entrySet()) {
			DetectionModule module = UltimaAnticheatClient.DETECTIONS.byId(e.getKey());
			if (module == null || !UltimaAnticheatClient.CONFIG.module(e.getKey()).enabled
					|| e.getValue() < 0.5) {
				continue;
			}
			anyScore = true;
			int threshold = UltimaAnticheatClient.CONFIG.module(e.getKey()).threshold;
			Severity severity = Severity.of(e.getValue(), threshold);
			String line = module.displayName() + ": "
					+ I18n.translate("ultima_anticheat.gui.score", (int) Math.round(e.getValue()))
					+ " (" + severity.label() + ")";
			context.drawText(textRenderer, line, contentX + 8, y, severity.color, false);
			y += 11;
		}
		if (!anyScore) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.detail.none"),
					contentX + 8, y, 0xFF9098A0, false);
			y += 11;
		}

		// Bestätigungen anderer Mod-Nutzer
		y += 4;
		context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.detail.reports"),
				contentX + 4, y, 0xFFFFFFFF, false);
		y += 12;
		long now = System.currentTimeMillis();
		int shown = 0;
		for (Map.Entry<UUID, PlayerSuspicion.RemoteReport> e : s.remoteReports.entrySet()) {
			PlayerSuspicion.RemoteReport report = e.getValue();
			if (now - report.timeMs() > PlayerSuspicion.REMOTE_REPORT_TTL_MS || shown >= 6) {
				continue;
			}
			DetectionModule module = UltimaAnticheatClient.DETECTIONS.byId(report.detectionId());
			String moduleName = module == null ? report.detectionId() : module.displayName();
			String line = report.reporterName() + ": " + moduleName + " (" + report.score() + ") "
					+ timeFormat.format(new Date(report.timeMs()));
			context.drawText(textRenderer, line, contentX + 8, y, 0xFF55FFFF, false);
			y += 11;
			shown++;
		}
		if (shown == 0) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.detail.none"),
					contentX + 8, y, 0xFF9098A0, false);
		}
	}

	private void renderHistory(DrawContext context) {
		List<NotificationManager.HistoryEntry> history = UltimaAnticheatClient.NOTIFICATIONS.historySnapshot();
		if (history.isEmpty()) {
			context.drawText(textRenderer, I18n.translate("ultima_anticheat.gui.empty_history"),
					contentX + 4, contentY + 8, 0xFF9098A0, false);
			return;
		}
		int y = contentY - (int) scrollHistory;
		for (NotificationManager.HistoryEntry entry : history) {
			if (y + 12 >= contentY && y <= contentY + contentH) {
				String line = "[" + timeFormat.format(new Date(entry.timeMs())) + "] "
						+ entry.playerName() + " – " + entry.moduleName()
						+ " (" + entry.score() + ", " + entry.severity().label() + ")"
						+ (entry.source().isEmpty() ? ""
							: " · " + I18n.translate("ultima_anticheat.notify.reported_by", entry.source()));
				context.drawText(textRenderer, line, contentX + 4, y, entry.severity().color, false);
			}
			y += 12;
		}
	}

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
	// Laufende Aktualisierung & Scrolling
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		// Verdachtsliste lebt: Snapshot + Klickflächen jede Sekunde erneuern
		if (currentTab == Tab.SUSPECTS) {
			if (selectedUuid != null && UltimaAnticheatClient.SUSPICION.get(selectedUuid) == null) {
				selectedUuid = null; // Eintrag ist inzwischen abgeklungen
				clearAndInit();
				return;
			}
			if (selectedUuid == null && ++ticksSinceRebuild >= 20) {
				ticksSinceRebuild = 0;
				clearAndInit();
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		double step = verticalAmount * 14;
		switch (currentTab) {
			case SUSPECTS -> {
				if (selectedUuid != null) {
					return true; // Detail-Ansicht scrollt nicht
				}
				scrollSuspects = clampScroll(scrollSuspects - step, rows.size() * ROW_HEIGHT);
				clearAndInit(); // Klickflächen an neue Positionen setzen
			}
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

	// ------------------------------------------------------------------
	// Hilfs-Widgets
	// ------------------------------------------------------------------

	/** Unsichtbare Klickfläche über einer Listenzeile (öffnet die Detail-Ansicht). */
	private static class RowClickWidget extends ClickableWidget {
		private final Runnable action;

		RowClickWidget(int x, int y, int w, int h, Runnable action) {
			super(x, y, w, h, Text.empty());
			this.action = action;
		}

		@Override
		public void onClick(Click click, boolean doubled) {
			action.run();
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			// Nichts zeichnen – die Zeile selbst wird vom Screen gerendert
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			// Keine Narration für die unsichtbare Klickfläche
		}
	}

	/** Slider für die Benachrichtigungs-Schwelle (10-100) eines Moduls. */
	private class ThresholdSlider extends SliderWidget {
		private final String moduleId;

		ThresholdSlider(int x, int y, int w, int h, String moduleId, int current) {
			super(x, y, w, h, Text.literal(I18n.translate("ultima_anticheat.gui.threshold", current)),
					(current - 10) / 90.0);
			this.moduleId = moduleId;
		}

		private int threshold() {
			return (int) Math.round(10 + value * 90);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(I18n.translate("ultima_anticheat.gui.threshold", threshold())));
		}

		@Override
		protected void applyValue() {
			UltimaAnticheatClient.CONFIG.module(moduleId).threshold = threshold();
			UltimaAnticheatClient.CONFIG.save();
		}
	}
}
