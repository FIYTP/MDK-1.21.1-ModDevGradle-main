package com.FIYTP.realmedicalmod.capability;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class MedicalDataCapability {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "realmedicalmod");

    public static final Codec<MedicalData> MEDICAL_DATA_CODEC =
            CompoundTag.CODEC.xmap(
                    nbt -> {
                        MedicalDataImpl data = new MedicalDataImpl();
                        data.deserializeNBT(nbt);
                        return data;
                    },
                    data -> ((MedicalDataImpl) data).serializeNBT()
            );

    // 修复：显式编解码
    public static final StreamCodec<ByteBuf, MedicalData> MEDICAL_DATA_STREAM_CODEC =
            new StreamCodec<ByteBuf, MedicalData>() {
                @Override
                public MedicalData decode(ByteBuf buf) {
                    CompoundTag tag = ByteBufCodecs.COMPOUND_TAG.decode(buf);
                    MedicalDataImpl data = new MedicalDataImpl();
                    data.deserializeNBT(tag);
                    return data;
                }

                @Override
                public void encode(ByteBuf buf, MedicalData data) {
                    CompoundTag tag = ((MedicalDataImpl) data).serializeNBT();
                    ByteBufCodecs.COMPOUND_TAG.encode(buf, tag);
                }
            };

    public static final Supplier<AttachmentType<MedicalData>> MEDICAL_DATA =
            ATTACHMENT_TYPES.register(
                    "medical_data",
                    () -> AttachmentType.<MedicalData>builder(MedicalDataImpl::new)
                            .serialize(MEDICAL_DATA_CODEC)
                            .sync(MEDICAL_DATA_STREAM_CODEC)
                            .build()
            );
}