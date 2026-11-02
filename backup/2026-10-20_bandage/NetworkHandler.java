package com.FIYTP.realmedicalmod.network;

import com.FIYTP.realmedicalmod.client.BrainDamageClientState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0.0");

        registrar.playToClient(
                BrainDamageEffectPacket.TYPE,
                BrainDamageEffectPacket.STREAM_CODEC,
                (packet, context) -> {
                    context.enqueueWork(() -> {
                        BrainDamageClientState.handleEffectPacket(packet);
                    });
                }
        );
    }

    public static void sendToPlayer(ServerPlayer player, BrainDamageEffectPacket packet) {
        if (player != null && packet != null) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }
}
