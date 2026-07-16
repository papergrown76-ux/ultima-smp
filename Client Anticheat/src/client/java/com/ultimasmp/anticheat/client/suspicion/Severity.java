package com.ultimasmp.anticheat.client.suspicion;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.util.Formatting;

/**
 * Schweregrad einer Verdachtsmeldung, farblich gestaffelt:
 * Gelb = Verdacht, Orange = wahrscheinlich, Rot = sehr wahrscheinlich.
 */
public enum Severity {
	SUSPICION("ultima_anticheat.severity.suspicion", Formatting.YELLOW, 0xFFFFE05C),
	LIKELY("ultima_anticheat.severity.likely", Formatting.GOLD, 0xFFFFA033),
	VERY_LIKELY("ultima_anticheat.severity.very_likely", Formatting.RED, 0xFFFF5555);

	private final String translationKey;
	public final Formatting formatting;
	/** ARGB-Farbe für die GUI. */
	public final int color;

	Severity(String translationKey, Formatting formatting, int color) {
		this.translationKey = translationKey;
		this.formatting = formatting;
		this.color = color;
	}

	/** Lokalisierter Anzeigename. */
	public String label() {
		return I18n.translate(translationKey);
	}

	/** Staffelung relativ zur konfigurierten Benachrichtigungs-Schwelle. */
	public static Severity of(double score, int threshold) {
		if (score >= threshold + 35) {
			return VERY_LIKELY;
		}
		if (score >= threshold + 15) {
			return LIKELY;
		}
		return SUSPICION;
	}
}
