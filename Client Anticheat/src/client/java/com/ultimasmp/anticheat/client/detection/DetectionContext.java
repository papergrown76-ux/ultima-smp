package com.ultimasmp.anticheat.client.detection;

import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.suspicion.SuspicionManager;
import com.ultimasmp.anticheat.client.track.PlayerTrackData;
import com.ultimasmp.anticheat.client.track.PlayerTracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/**
 * Alles, was ein Detection-Modul für einen Check braucht: Welt, aktueller
 * Tick, Tracking-Daten und der Zugriff auf das Verdachtswert-System.
 */
public record DetectionContext(MinecraftClient client, ClientWorld world, long tick,
							   PlayerTracker tracker, SuspicionManager suspicion,
							   ConfigManager configManager) {

	/** Bequemer Weg für Module, einen Verdacht zu melden. */
	public void flag(DetectionModule module, PlayerTrackData data, double amount, String detail) {
		suspicion.flag(data.uuid, data.name, module.id(), module.displayName(), amount, detail);
	}
}
