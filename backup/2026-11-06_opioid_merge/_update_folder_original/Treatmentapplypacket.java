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
 * C2S 医疗用品部位施救与阿片类药物定量推注数据包 (TreatmentApplyPacket)
 *
 * @param handOrdinal 手部枚举下标 (MAIN_HAND / OFF_HAND)
 * @param limbOrdinal 目标肢体枚举下标 (HEAD / CHEST / LEFT_ARM / RIGHT_ARM / LEFT_LEG / RIGHT_LEG)
 * @param doseUnits   滑块选定的推注剂量单位 (1 ~ 100；普通非定量医疗品固定传 100)
 */
public record TreatmentApplyPacket(int handOrdinal, int limbOrdinal, int doseUnits) implements CustomPacketPayload {

    /** 兼容旧版 2 参数构造调用（默认满剂量 100 单位） */
    public TreatmentApplyPacket(int handOrdinal, int limbOrdinal) {
        this(handOrdinal, limbOrdinal, 100);
    }

    public static final CustomPacketPayload.Type<TreatmentApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RealMedicalMod.MODID, "treatment_apply"));

    public static final StreamCodec<ByteBuf, TreatmentApplyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TreatmentApplyPacket::handOrdinal,
            ByteBufCodecs.VAR_INT, TreatmentApplyPacket::limbOrdinal,
            ByteBufCodecs.VAR_INT, TreatmentApplyPacket::doseUnits,
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
                            limbs[packet.limbOrdinal],
                            packet.doseUnits
                    );
                }
            }
        });
    }
}