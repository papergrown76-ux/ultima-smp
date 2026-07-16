package com.ultimasmp.anticheat.client;

import org.lwjgl.glfw.GLFW;

import com.ultimasmp.anticheat.UltimaAnticheatMod;
import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionManager;
import com.ultimasmp.anticheat.client.gui.AnticheatScreen;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.share.ShareManager;
import com.ultimasmp.anticheat.client.suspicion.SuspicionManager;
import com.ultimasmp.anticheat.client.track.PlayerTracker;
import com.ultimasmp.anticheat.network.ReportS2CPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-Einstiegspunkt: verdrahtet alle Bausteine, treibt pro Client-Tick
 * das Tracking und die Detection-Module an und schaltet mit Left Alt das GUI.
 */
public class UltimaAnticheatClient implements ClientModInitializer {
	// Zentrale Instanzen; die GUI greift darauf zu.
	public static ConfigManager CONFIG;
	public static PlayerTracker TRACKER;
	public static SuspicionManager SUSPICION;
	public static NotificationManager NOTIFICATIONS;
	public static ShareManager SHARE;
	public static DetectionManager DETECTIONS;

	/** GUI-Umschalttaste (fest: Left Alt). */
	private static final int TOGGLE_KEY = GLFW.GLFW_KEY_LEFT_ALT;

	private boolean toggleKeyWasDown;
	private long tickCounter;
	private boolean wasInWorld;

	@Override
	public void onInitializeClient() {
		CONFIG = new ConfigManager();
		CONFIG.load();

		NOTIFICATIONS = new NotificationManager(CONFIG);
		SHARE = new ShareManager(CONFIG);
		SUSPICION = new SuspicionManager(CONFIG, NOTIFICATIONS, SHARE);
		DETECTIONS = new DetectionManager(CONFIG);
		TRACKER = new PlayerTracker(DETECTIONS);
		SHARE.wire(SUSPICION, NOTIFICATIONS, DETECTIONS);

		// Vom Server weitergeleitete Meldungen anderer Mod-Nutzer empfangen
		ClientPlayNetworking.registerGlobalReceiver(ReportS2CPayload.ID,
				(payload, context) -> context.client().execute(() -> SHARE.onReceive(payload, context.client())));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

		UltimaAnticheatMod.LOGGER.info("Ultima Client-Anticheat (Client) bereit – GUI mit Left Alt");
	}

	private void onTick(MinecraftClient client) {
		handleToggleKey(client);

		if (client.world == null || client.player == null) {
			// Welt verlassen: Beobachtungsdaten verwerfen
			if (wasInWorld) {
				TRACKER.reset();
				SUSPICION.clear();
				wasInWorld = false;
			}
			return;
		}
		wasInWorld = true;
		tickCounter++;

		TRACKER.update(client, tickCounter);
		DetectionContext ctx = new DetectionContext(client, client.world, tickCounter,
				TRACKER, SUSPICION, CONFIG);
		DETECTIONS.runChecks(ctx);
		SUSPICION.tick(tickCounter);
	}

	/**
	 * Left Alt per GLFW-Flanke abfragen. Bewusst kein KeyBinding: Die Taste
	 * soll laut Anforderung fest belegt sein und auch funktionieren, während
	 * das eigene GUI geöffnet ist.
	 */
	private void handleToggleKey(MinecraftClient client) {
		long window = client.getWindow().getHandle();
		boolean down = GLFW.glfwGetKey(window, TOGGLE_KEY) == GLFW.GLFW_PRESS;
		if (down && !toggleKeyWasDown) {
			if (client.currentScreen == null) {
				client.setScreen(new AnticheatScreen());
			} else if (client.currentScreen instanceof AnticheatScreen) {
				client.setScreen(null);
			}
			// In anderen Screens (Chat, Inventar, ...) macht Left Alt nichts.
		}
		toggleKeyWasDown = down;
	}
}
