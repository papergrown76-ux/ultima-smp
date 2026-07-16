package com.ultimasmp.anticheat.client.track;

import java.util.UUID;

/**
 * Ein heuristisch zugeordneter Nahkampf-Treffer: Spieler A (attacker) hat sehr
 * wahrscheinlich Spieler B (victim) geschlagen. Die Zuordnung basiert auf
 * Schwung-Animation, Distanz und Blickrichtung — sie ist eine Schätzung, da
 * der Client die Angriffs-Pakete anderer Spieler nicht sehen kann.
 *
 * @param distance         Distanz Augen des Angreifers -> Hitbox des Opfers (Blöcke)
 * @param lookOffsetDeg    Winkel zwischen Blickrichtung des Angreifers und dem Opfer
 * @param rotDeltaPreSwing größte Rotationsänderung des Angreifers kurz vor dem Schlag
 */
public record AttackEvent(UUID attackerUuid, UUID victimUuid, String victimName, long tick,
						  double distance, double lookOffsetDeg, double rotDeltaPreSwing) {
}
