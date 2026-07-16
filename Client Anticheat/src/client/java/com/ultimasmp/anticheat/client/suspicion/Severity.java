package com.ultimasmp.anticheat.client.suspicion;

import net.minecraft.util.Formatting;

/**
 * Schweregrad einer Verdachtsmeldung, farblich gestaffelt:
 * Gelb = Verdacht, Orange = wahrscheinlich, Rot = sehr wahrscheinlich.
 */
public enum Severity {
	SUSPICION("Verdacht", Formatting.YELLOW, 0xFFFFE05C),
	LIKELY("Wahrscheinlich", Formatting.GOLD, 0xFFFFA033),
	VERY_LIKELY("Sehr wahrscheinlich", Formatting.RED, 0xFFFF5555);

	public final String label;
	public final Formatting formatting;
	/** ARGB-Farbe für die GUI. */
	public final int color;

	Severity(String label, Formatting formatting, int color) {
		this.label = label;
		this.formatting = formatting;
		this.color = color;
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
