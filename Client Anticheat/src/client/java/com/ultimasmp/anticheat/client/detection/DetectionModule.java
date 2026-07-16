package com.ultimasmp.anticheat.client.detection;

import com.ultimasmp.anticheat.client.track.PlayerTrackData;

import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Ein einzelnes Erkennungsmodul. Jedes Modul lebt in einem eigenen Package
 * (z. B. detection.killaura) und kann im GUI einzeln aktiviert/deaktiviert
 * werden. Deaktivierte Module werden vom {@link DetectionManager} komplett
 * übersprungen und verbrauchen keine Rechenzeit.
 */
public interface DetectionModule {
	/** Stabile ID (klein, nur a-z/_): Schlüssel für Config und Netzwerkprotokoll. */
	String id();

	/** Anzeigename für GUI und Benachrichtigungen. */
	String displayName();

	/**
	 * Wird pro Client-Tick für jeden beobachteten fremden Spieler aufgerufen
	 * (nur wenn das Modul aktiviert ist).
	 *
	 * @param player der beobachtete Spieler (nie der eigene)
	 * @param data   die vom PlayerTracker erhobenen Verlaufsdaten dieses Spielers
	 */
	void check(DetectionContext ctx, AbstractClientPlayerEntity player, PlayerTrackData data);
}
