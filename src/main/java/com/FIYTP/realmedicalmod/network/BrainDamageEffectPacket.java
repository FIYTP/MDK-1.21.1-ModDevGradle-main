package com.FIYTP.realmedicalmod.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BrainDamageEffectPacket(EffectType effectType, int duration) implements CustomPacketPayload {

    public enum EffectType {
        SHOCK,
        INPUT_INVERT,
        VIEW_INVERT
    }

    public static final Type<BrainDamageEffectPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("realmedicalmod", "brain_damage_effect"));

    public static final StreamCodec<ByteBuf, BrainDamageEffectPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> EffectType.values()[i], Enum::ordinal),
            BrainDamageEffectPacket::effectType,
            ByteBufCodecs.VAR_INT,
            BrainDamageEffectPacket::duration,
            BrainDamageEffectPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
