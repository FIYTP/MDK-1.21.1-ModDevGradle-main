package com.FIYTP.realmedicalmod.treatment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * medical_potency 数据组件：效果标签 id → 数值。
 * 例：{"realmedicalmod:hemostasis": 500} 表示该绷带可吸收 500mL 出血。
 * 玩法数值（容量、疼痛缓解量、药效强度）均由此承载，整合包可用组件调整。
 */
public record MedicalPotency(Map<ResourceLocation, Float> values) {

    public static final Codec<MedicalPotency> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                            .fieldOf("values")
                            .forGetter(MedicalPotency::values)
            ).apply(instance, MedicalPotency::new)
    );

    public static final StreamCodec<ByteBuf, MedicalPotency> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public static final MedicalPotency EMPTY = new MedicalPotency(Map.of());

    /** 读取某效果标签的数值，无则回退默认值。 */
    public float get(ResourceLocation effect, float fallback) {
        return values.getOrDefault(effect, fallback);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public static MedicalPotency of(ResourceLocation effect, float value) {
        Map<ResourceLocation, Float> map = new HashMap<>();
        map.put(effect, value);
        return new MedicalPotency(Map.copyOf(map));
    }
}
