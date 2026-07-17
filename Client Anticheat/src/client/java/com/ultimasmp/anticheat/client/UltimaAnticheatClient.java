package com.ultimasmp.anticheat.client;

import org.lwjgl.glfw.GLFW;

import com.ultimasmp.anticheat.UltimaAnticheatMod;
import com.ultimasmp.anticheat.client.config.ConfigManager;
import com.ultimasmp.anticheat.client.detection.DetectionContext;
import com.ultimasmp.anticheat.client.detection.DetectionManager;
import com.ultimasmp.anticheat.client.gui.AnticheatHud;
import com.ultimasmp.anticheat.client.gui.AnticheatScreen;
import com.ultimasmp.anticheat.client.notify.NotificationManager;
import com.ultimasmp.anticheat.client.share.ShareManager;
import com.ultimasmp.anticheat.client.suspicion.SuspicionManager;
import com.ultimasmp.anticheat.client.track.PlayerTracker;
import com.ultimasmp.anticheat.client.track.ServerLagMonitor;
import com.ultimasmp.anticheat.network.ReportS2CPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;

/**
 * Client-Einstiegspunkt: verdrahtet alle Bausteine, treibt pro Client-Tick
 * das Tracking und die Detection-Module an und schaltet das GUI um
 * (Standard-Taste: Left Alt, in den Minecraft-Optionen umbelegbar).
 */
public class UltimaAnticheatClient implements ClientModInitializer {
	// Zentrale Instanzen; GUI und HUD greifen darauf zu.
	public static ConfigManager CONFIG;
	public static PlayerTracker TRACKER;
	public static SuspicionManager SUSPICION;
	public static NotificationManager NOTIFICATIONS;
	public static ShareManager SHARE;
	public static DetectionManager DETECTIONS;
	public static ServerLagMonitor LAG_MONITOR;
	/** Umschalttaste für das GUI (Standard: Left Alt, umbelegbar). */
	public static KeyBinding TOGGLE_BINDING;

	private boolean toggleKeyWasDown;
	private static long tickCounter;
	private boolean wasInWorld;

	/** Aktueller Beobachtungs-Tick (für GUI-Anzeigen wie die Lag-Warnung). */
	public static long currentTick() {
		return tickCounter;
	}

	@Override
	public void onInitializeClient() {
		CONFIG = new ConfigManager();
		CONFIG.load();

		NOTIFICATIONS = new NotificationManager(CONFIG);
		SHARE = new ShareManager(CONFIG);
		SUSPICION = new SuspicionManager(CONFIG, NOTIFICATIONS, SHARE);
		DETECTIONS = new DetectionManager(CONFIG);
		TRACKER = new PlayerTracker(DETECTIONS);
		LAG_MONITOR = new ServerLagMonitor();
		SHARE.wire(SUSPICION, NOTIFICATIONS, DETECTIONS);

		// Umschalttaste: Standard Left Alt, über die Steuerungs-Optionen umbelegbar
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("ultima_anticheat", "main"));
		TOGGLE_BINDING = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.ultima_anticheat.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, category));

		// Mini-HUD mit den Top-Verdächtigen registrieren
		HudElementRegistry.addLast(Identifier.of("ultima_anticheat", "suspects"), new AnticheatHud());

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
				LAG_MONITOR.reset();
				wasInWorld = false;
			}
			return;
		}
		wasInWorld = true;
		tickCounter++;

		LAG_MONITOR.update(client.world, tickCounter);
		TRACKER.update(client, tickCounter);
		DetectionContext ctx = new DetectionContext(client, client.world, tickCounter,
				TRACKER, SUSPICION, CONFIG);
		DETECTIONS.runChecks(ctx, LAG_MONITOR.isUnstable(tickCounter));
		SUSPICION.tick(tickCounter);
	}

	/**
	 * Die gebundene Taste wird per GLFW-Flanke abgefragt statt über
	 * KeyBinding#wasPressed: So funktioniert das Schließen auch, während das
	 * eigene GUI geöffnet ist (Screens schlucken normale KeyBinding-Events).
	 * Hinweis: Damit sind Tastatur-Tasten unterstützt; Maustasten als Bindung
	 * werden bewusst nicht unterstützt.
	 */
	private void handleToggleKey(MinecraftClient client) {
		int keyCode = KeyBindingHelper.getBoundKeyOf(TOGGLE_BINDING).getCode();
		long window = client.getWindow().getHandle();
		boolean down = keyCode > 0 && GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
		if (down && !toggleKeyWasDown) {
			if (client.currentScreen == null) {
				client.setScreen(new AnticheatScreen());
			} else if (client.currentScreen instanceof AnticheatScreen) {
				client.setScreen(null);
			}
			// In anderen Screens (Chat, Inventar, ...) macht die Taste nichts.
		}
		toggleKeyWasDown = down;
	}
}
