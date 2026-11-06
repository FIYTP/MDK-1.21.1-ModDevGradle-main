package com.FIYTP.realmedicalmod.network;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.treatment.TreatmentRouter;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 医疗用品部位施救数据包 (TreatmentApplyPacket)
 * 客户端在长按左键面板上将鼠标移至目标肢体完成施救时发送至服务端。
 */
public record TreatmentApplyPacket(int handOrdinal, int limbOrdinal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TreatmentApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RealMedicalMod.MODID, "treatment_apply"));

    public static final StreamCodec<ByteBuf, TreatmentApplyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TreatmentApplyPacket::handOrdinal,
            ByteBufCodecs.VAR_INT, TreatmentApplyPacket::limbOrdinal,
            TreatmentApplyPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final TreatmentApplyPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                InteractionHand[] hands = InteractionHand.values();
                MedicalData.Limb[] limbs = MedicalData.Limb.values();
                if (packet.handOrdinal >= 0 && packet.handOrdinal < hands.length
                        && packet.limbOrdinal >= 0 && packet.limbOrdinal < limbs.length) {
                    TreatmentRouter.applyServerTreatment(
                            serverPlayer,
                            hands[packet.handOrdinal],
                            limbs[packet.limbOrdinal]
                    );
                }
            }
        });
    }
}