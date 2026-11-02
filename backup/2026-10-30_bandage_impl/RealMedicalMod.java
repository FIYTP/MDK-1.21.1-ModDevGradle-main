package com.FIYTP.realmedicalmod;

import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(RealMedicalMod.MODID)
public class RealMedicalMod {
    public static final String MODID = "realmedicalmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealMedicalMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, MedicalConfig.SPEC);
        MedicalDataCapability.ATTACHMENT_TYPES.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(NetworkHandler::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        LOGGER.info("真实医疗系统模组已加载！");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("真实医疗系统准备就绪");
    }

    private void onServerStarted(ServerStartedEvent event) {
        // ★ 执行命令：将安全摔落距离改为5格 ★
        event.getServer().getCommands().performPrefixedCommand(
                event.getServer().createCommandSourceStack(),
                "attribute @a minecraft:safe_fall_distance base set 5.0"
        );
        LOGGER.info("已将安全摔落距离设置为5格");
    }
}