package com.ultimasmp.anticheat.network;

import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

/**
 * Server -> Client: Eine vom Server weitergeleitete Verdachtsmeldung eines
 * anderen Mod-Nutzers. Der Melder (reporter) wird vom Server anhand der
 * tatsächlichen Verbindung gesetzt und ist damit fälschungssicher.
 */
public record ReportS2CPayload(int protocolVersion, UUID reporterUuid, String reporterName,
							   UUID reportedUuid, String reportedName,
							   String detectionId, int score) implements CustomPayload {
	public static final CustomPayload.Id<ReportS2CPayload> ID =
			new CustomPayload.Id<>(Identifier.of("ultima_anticheat", "report_s2c"));

	public static final PacketCodec<RegistryByteBuf, ReportS2CPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, ReportS2CPayload::protocolVersion,
			Uuids.PACKET_CODEC, ReportS2CPayload::reporterUuid,
			PacketCodecs.STRING, ReportS2CPayload::reporterName,
			Uuids.PACKET_CODEC, ReportS2CPayload::reportedUuid,
			PacketCodecs.STRING, ReportS2CPayload::reportedName,
			PacketCodecs.STRING, ReportS2CPayload::detectionId,
			PacketCodecs.VAR_INT, ReportS2CPayload::score,
			ReportS2CPayload::new
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
