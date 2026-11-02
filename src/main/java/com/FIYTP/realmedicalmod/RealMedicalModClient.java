package com.FIYTP.realmedicalmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = RealMedicalMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public class RealMedicalModClient {

    public RealMedicalModClient(ModContainer container) {
        RealMedicalMod.LOGGER.info("真实医疗系统 - 客户端已加载");
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        RealMedicalMod.LOGGER.info("真实医疗系统 - 客户端设置完成");
    }
}