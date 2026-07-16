package com.ultimasmp.anticheat.network;

/**
 * Versionierung des Mod-zu-Mod-Protokolls. Bei inkompatiblen Änderungen am
 * Payload-Format die Version erhöhen — Empfänger verwerfen fremde Versionen
 * still, statt Datenmüll zu interpretieren.
 */
public final class AnticheatProtocol {
	public static final int VERSION = 1;

	private AnticheatProtocol() {
	}
}
