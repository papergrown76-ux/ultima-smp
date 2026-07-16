package com.ultimasmp.anticheat.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Client -> Server: Ein Mod-Nutzer teilt eine eigene Verdachtsmeldung.
 *
 * Der Absender wird bewusst NICHT im Payload übertragen — der Server kennt
 * ihn ohnehin und setzt ihn beim Weiterleiten selbst ein. So kann niemand
 * Meldungen im Namen anderer Spieler fälschen.
 */
public record ReportC2SPayload(UUID reportedUuid, String reportedName, String detectionId, int score) implements CustomPayload {
	public static final CustomPayload.Id<ReportC2SPayload> ID =
			new CustomPayload.Id<>(Identifier.of("ultima_anticheat", "report_c2s"));

	public static final PacketCodec<RegistryByteBuf, ReportC2SPayload> CODEC = PacketCodec.tuple(
			Uuids.PACKET_CODEC, ReportC2SPayload::reportedUuid,
			PacketCodecs.STRING, ReportC2SPayload::reportedName,
			PacketCodecs.STRING, ReportC2SPayload::detectionId,
			PacketCodecs.VAR_INT, ReportC2SPayload::score,
			ReportC2SPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
