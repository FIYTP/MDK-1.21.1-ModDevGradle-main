package com.FIYTP.realmedicalmod.network;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");

        // S2C: 脑损伤视觉/控制效果包
        registrar.playToClient(
                BrainDamageEffectPacket.TYPE,
                BrainDamageEffectPacket.STREAM_CODEC,
                (packet, context) -> {
                    context.enqueueWork(() -> {
                        BrainDamageClientState.handleEffectPacket(packet);
                    });
                }
        );

        // C2S: 医疗用品部位施救请求包
        registrar.playToServer(
                TreatmentApplyPacket.TYPE,
                TreatmentApplyPacket.STREAM_CODEC,
                TreatmentApplyPacket::handleServer
        );

    }

    public static void sendToPlayer(ServerPlayer player, BrainDamageEffectPacket packet) {
        if (player != null && packet != null) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static void sendTreatmentToServer(TreatmentApplyPacket packet) {
        if (packet != null) {
            PacketDistributor.sendToServer(packet);
        }
    }

}
