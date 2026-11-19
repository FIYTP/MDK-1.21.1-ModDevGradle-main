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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S 手环全息投影一键快捷施救数据包 (WristQuickTreatPacket)
 *
 * 允许玩家在抬腕检视手环全息投影时，直接点击右侧外科诊断抽屉中的推荐器材进行背包直用施救，
 * 无需手动切出物品栏槽位。
 *
 * @param inventorySlot 背包槽位索引 (-1 表示徒手操作，配合 doseUnits == -1 解开止血带 / -2 拆下夹板)
 * @param limbOrdinal   目标肢体枚举下标
 * @param doseUnits     操作剂量单位 (默认 100；-1=解止血带，-2=拆夹板)
 */
public record WristQuickTreatPacket(int inventorySlot, int limbOrdinal, int doseUnits) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WristQuickTreatPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(RealMedicalMod.MODID, "wrist_quick_treat"));

    public static final StreamCodec<ByteBuf, WristQuickTreatPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WristQuickTreatPacket::inventorySlot,
            ByteBufCodecs.VAR_INT, WristQuickTreatPacket::limbOrdinal,
            ByteBufCodecs.VAR_INT, WristQuickTreatPacket::doseUnits,
            WristQuickTreatPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final WristQuickTreatPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                MedicalData.Limb[] limbs = MedicalData.Limb.values();
                if (packet.limbOrdinal >= 0 && packet.limbOrdinal < limbs.length) {
                    TreatmentRouter.applyServerTreatmentFromSlot(
                            serverPlayer,
                            packet.inventorySlot,
                            limbs[packet.limbOrdinal],
                            packet.doseUnits
                    );
                }
            }
        });
    }
}