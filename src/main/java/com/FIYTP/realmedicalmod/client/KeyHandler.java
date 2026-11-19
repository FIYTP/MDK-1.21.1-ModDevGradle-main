package com.FIYTP.realmedicalmod.client;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.gui.MedicalScreen;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端按键与昏迷交互锁死处理器 (KeyHandler)
 *
 * 功能职责：
 * 1. 处理 H 键打开/关闭医疗面板 (MedicalScreen)。
 * 2. 当玩家失去意识 (consciousness <= 0) 或心脏骤停时：
 *    - 禁止打开物品栏及任何容器界面 (AbstractContainerScreen)，若已打开则立即自动关闭；
 *    - 锁定当前手持快捷栏槽位 (selectedHotbarSlot)，拦截鼠标滚轮切换与数字键 1~9 切换手持物品；
 *    - 拦截 F 键主副手互换、Q 键丢弃物品以及左右键交互操作。
 */
@EventBusSubscriber(modid = "realmedicalmod", value = Dist.CLIENT)
public class KeyHandler {

    public static final String KEY_CATEGORY = "key.category.realmedicalmod";
    public static final String KEY_OPEN_MEDICAL = "key.realmedicalmod.open_medical";

    public static final KeyMapping OPEN_MEDICAL_KEY = new KeyMapping(
            KEY_OPEN_MEDICAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY
    );

    /** 记录玩家陷入昏迷瞬间的快捷栏槽位，昏迷期间强制锁定不可切换 */
    private static int lockedHotbarSlot = -1;

    private static boolean isUnconscious(Player player) {
        if (player == null) return false;
        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        return data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MEDICAL_KEY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (OPEN_MEDICAL_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (mc.screen instanceof MedicalScreen) {
                    mc.player.closeContainer();
                } else if (mc.screen == null && !ModItems.safeCfgBool(MedicalConfig.WRISTBAND_ENABLED, true)) {
                    mc.setScreen(new MedicalScreen());
                }
            }
        }
    }

    /**
     * 昏迷时禁止打开背包/容器物品栏界面
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isUnconscious(mc.player)) {
            if (event.getNewScreen() instanceof AbstractContainerScreen<?>) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 昏迷时禁止通过鼠标滚轮切换手持物品
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null && isUnconscious(mc.player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 昏迷时禁止左右键攻击/使用物品/鼠标中键拾取切换物品
     */
    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isUnconscious(mc.player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * 每客户端 Tick 強制锁定昏迷玩家的快捷栏槽位、清空物品栏/副手/丢弃按键队列，并关闭已打开的背包
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            lockedHotbarSlot = -1;
            return;
        }

        if (isUnconscious(player)) {
            if (lockedHotbarSlot < 0 || lockedHotbarSlot > 8) {
                lockedHotbarSlot = player.getInventory().selected;
            } else {
                player.getInventory().selected = lockedHotbarSlot;
            }

            // 若昏迷时背包/容器界面正处于打开状态，立即关闭
            if (mc.screen instanceof AbstractContainerScreen<?>) {
                player.closeContainer();
            }

            // 消耗并清空打开物品栏 (E)、副手切换 (F)、丢弃物品 (Q)、跳跃 (Space) 与快捷栏 (1~9) 的按键点击计数
            while (mc.options.keyInventory.consumeClick()) {}
            mc.options.keyInventory.setDown(false);

            while (mc.options.keySwapOffhand.consumeClick()) {}
            mc.options.keySwapOffhand.setDown(false);

            while (mc.options.keyDrop.consumeClick()) {}
            mc.options.keyDrop.setDown(false);

            while (mc.options.keyJump.consumeClick()) {}
            mc.options.keyJump.setDown(false);

            for (KeyMapping hotbarKey : mc.options.keyHotbarSlots) {
                while (hotbarKey.consumeClick()) {}
                hotbarKey.setDown(false);
            }
        } else {
            lockedHotbarSlot = player.getInventory().selected;
        }
    }
}