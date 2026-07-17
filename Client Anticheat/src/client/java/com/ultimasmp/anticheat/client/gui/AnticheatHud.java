package com.ultimasmp.anticheat.client.gui;

import java.util.List;
import java.util.Map;

import com.ultimasmp.anticheat.client.UltimaAnticheatClient;
import com.ultimasmp.anticheat.client.detection.DetectionModule;
import com.ultimasmp.anticheat.client.suspicion.PlayerSuspicion;
import com.ultimasmp.anticheat.client.suspicion.Severity;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.resource.language.I18n;

/**
 * Kleines, immer sichtbares HUD oben rechts: zeigt die bis zu drei
 * auffälligsten Spieler, deren Verdachtswert die Modul-Schwelle erreicht.
 * So sieht man Warnungen im PvP-Alltag, ohne das GUI öffnen zu müssen.
 */
public class AnticheatHud implements HudElement {
	private static final int MAX_ENTRIES = 3;
	private static final int PADDING = 4;

	@Override
	public void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!UltimaAnticheatClient.CONFIG.config().hudEnabled
				|| client.player == null
				|| client.options.hudHidden
				|| client.currentScreen instanceof AnticheatScreen) {
			return;
		}

		// Nur Einträge über der Schwelle ihres (aktivierten) Moduls anzeigen
		List<PlayerSuspicion> all = UltimaAnticheatClient.SUSPICION.sortedSnapshot();
		TextRenderer textRenderer = client.textRenderer;
		int screenWidth = context.getScaledWindowWidth();

		int shown = 0;
		int y = PADDING + 12;
		boolean headerDrawn = false;
		for (PlayerSuspicion s : all) {
			if (shown >= MAX_ENTRIES) {
				break;
			}
			String worstId = "";
			double worstScore = 0;
			for (Map.Entry<String, Double> e : s.scores.entrySet()) {
				if (!UltimaAnticheatClient.CONFIG.module(e.getKey()).enabled) {
					continue;
				}
				if (e.getValue() > worstScore) {
					worstScore = e.getValue();
					worstId = e.getKey();
				}
			}
			if (worstId.isEmpty()) {
				continue;
			}
			int threshold = UltimaAnticheatClient.CONFIG.module(worstId).threshold;
			if (worstScore < threshold) {
				continue;
			}
			DetectionModule module = UltimaAnticheatClient.DETECTIONS.byId(worstId);
			if (module == null) {
				continue;
			}

			if (!headerDrawn) {
				String header = I18n.translate("ultima_anticheat.hud.title");
				context.drawText(textRenderer, header,
						screenWidth - PADDING - textRenderer.getWidth(header), PADDING, 0xFF55FFFF, true);
				headerDrawn = true;
			}

			Severity severity = Severity.of(worstScore, threshold);
			String line = s.name + " " + module.displayName() + " " + (int) Math.round(worstScore);
			int width = textRenderer.getWidth(line);
			context.fill(screenWidth - PADDING - width - 2, y - 1,
					screenWidth - PADDING + 2, y + 9, 0x66000000);
			context.drawText(textRenderer, line, screenWidth - PADDING - width, y, severity.color, true);
			y += 11;
			shown++;
		}
	}
}
