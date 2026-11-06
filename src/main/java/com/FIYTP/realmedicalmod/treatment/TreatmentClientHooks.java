package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.capability.MedicalDataImpl;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.network.NetworkHandler;
import com.FIYTP.realmedicalmod.network.TreatmentApplyPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 3A 级医疗用品左键长按战术人体面板 + 阿片类药物定量推注滑块界面 (TreatmentClientHooks)
 *
 * 交互流程：
 * 1. 普通医疗用品（卷式绷带、加压绷带等）：
 *    按住鼠标左键呼出人体部位面板 -> 移至受伤部位读条（或松开左键）直接完成包扎。
 * 2. 阿片类强效镇痛针剂（盐酸吗啡 morphine / 枸橼酸芬太尼 fentanyl）：
 *    - 方式 A：直接点击鼠标右键（{@link BandageItem#use}）；
 *    - 方式 B：按住鼠标左键在人体面板上选中目标肢体；
 *    均会立即呼出「阿片类药物定量推注滑块界面 ({@link DoseSelectionScreen})」，
 *    让玩家通过交互式滑块（支持拖拽、点击轨道、滚轮微调、方向键与快捷预设）精确控制注入剂量，
 *    实时显示镇痛预期与延髓呼吸抑制/致命过量警告！
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public class TreatmentClientHooks {

    private static final int C_BG_DARK        = 0xE6060D16;
    private static final int C_NEON_CYAN      = 0xFF00E5FF;
    private static final int C_SAFE_GREEN     = 0xFF00E676;
    private static final int C_WARNING_YELLOW = 0xFFFFB300;
    private static final int C_ALERT_RED      = 0xFFFF2A4B;
    private static final int C_MAGENTA_HAZARD = 0xFFFF1E8A;
    private static final int C_TEXT_WHITE     = 0xFFF0F8FF;
    private static final int C_TEXT_DIM       = 0xFF8AA4B8;

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static boolean panelActive = false;
    private static InteractionHand activeHand = InteractionHand.MAIN_HAND;
    private static float virtualCursorX = 0.0f;
    private static float virtualCursorY = 0.0f;
    private static MedicalData.Limb hoveredLimb = null;
    private static MedicalData.Limb lastHoveredLimb = null;
    private static float channelProgress = 0.0f;
    private static long lastRenderTimeMs = 0L;
    private static int postApplyCooldownTicks = 0;

    private record LimbSlotLayout(
            MedicalData.Limb limb,
            String cnName,
            String codeName,
            float offsetX,
            float offsetY,
            float width,
            float height
    ) {}

    private static final LimbSlotLayout[] LIMB_LAYOUTS = new LimbSlotLayout[] {
            new LimbSlotLayout(MedicalData.Limb.HEAD,      "头部", "HEAD",   0.0f, -72.0f, 44.0f, 36.0f),
            new LimbSlotLayout(MedicalData.Limb.CHEST,     "胸腔", "THORAX", 0.0f, -22.0f, 52.0f, 52.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_ARM,  "左臂", "L-ARM", -52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_ARM, "右臂", "R-ARM",  52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_LEG,  "左腿", "L-LEG", -24.0f,  52.0f, 40.0f, 62.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_LEG, "右腿", "R-LEG",  24.0f,  52.0f, 40.0f, 62.0f)
    };

    public static void openBandageScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            activatePanel(mc, hand != null ? hand : InteractionHand.MAIN_HAND);
        }
    }

    /**
     * 打开 3A 阿片类药物定量推注滑块界面 (供右键使用或左键部位面板选定后调用)
     */
    public static void openOpioidDoseScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 意识归零或心脏骤停时禁止自我推注
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isOpioidItem(stack)) return;

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new DoseSelectionScreen(useHand, preselectedLimb));
    }

    private static String getLimbDisplayName(MedicalData.Limb limb) {
        if (limb == null) return "";
        return switch (limb) {
            case HEAD -> "头部";
            case CHEST -> "胸腔";
            case LEFT_ARM -> "左臂";
            case RIGHT_ARM -> "右臂";
            case LEFT_LEG -> "左腿";
            case RIGHT_LEG -> "右腿";
        };
    }

    private static int getAssetAccentColor(MedicalAssetLibrary.AssetType assetType) {
        if (assetType == null) return C_NEON_CYAN;
        return switch (assetType) {
            case BANDAGE_ROLL -> C_NEON_CYAN;
            case PRESSURE_BANDAGE -> C_SAFE_GREEN;
            case MORPHINE_INJECTOR -> C_WARNING_YELLOW;
            case FENTANYL_SYRINGE -> C_MAGENTA_HAZARD;
            case TOURNIQUET -> C_WARNING_YELLOW;
            case SYRINGE -> 0xFF40C4FF;
            case SPLINT -> 0xFF69F0AE;
            case MEDICINE_BOTTLE -> 0xFFFFD54F;
        };
    }

    public static boolean isLockingCameraView() {
        return panelActive;
    }

    public static void onMouseDelta(double rawDX, double rawDY) {
        if (!panelActive) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float sensitivity = 0.42f;
        virtualCursorX = Mth.clamp(virtualCursorX + (float) rawDX * sensitivity, sw * 0.5f - 145.0f, sw * 0.5f + 145.0f);
        virtualCursorY = Mth.clamp(virtualCursorY + (float) rawDY * sensitivity, sh * 0.5f - 125.0f, sh * 0.5f + 125.0f);
    }

    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        InteractionHand medHand = TreatmentRouter.findActiveMedicalHand(mc.player);
        if (medHand != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!panelActive) {
                activatePanel(mc, medHand);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) {
            if (panelActive) deactivatePanel();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data != null && (data.getConsciousness() <= 0 || data.isCardiacArrest())) {
            if (panelActive) deactivatePanel();
            return;
        }

        if (postApplyCooldownTicks > 0) {
            postApplyCooldownTicks--;
        }

        long window = mc.getWindow().getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        InteractionHand medHand = TreatmentRouter.findActiveMedicalHand(player);

        if (leftDown && medHand != null) {
            if (!panelActive) {
                activatePanel(mc, medHand);
            } else {
                activeHand = medHand;
            }
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        } else {
            if (panelActive) {
                if (hoveredLimb != null && channelProgress >= 0.15f && postApplyCooldownTicks <= 0) {
                    ItemStack stack = player.getItemInHand(activeHand);
                    if (TreatmentRouter.canTreatLimb(stack, data, hoveredLimb)) {
                        if (TreatmentRouter.isOpioidItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openOpioidDoseScreen(handCopy, chosen);
                            return;
                        } else {
                            triggerApplyPacket(activeHand, hoveredLimb, 100);
                        }
                    }
                }
                deactivatePanel();
            }
        }
    }

    private static void activatePanel(Minecraft mc, InteractionHand hand) {
        panelActive = true;
        activeHand = hand;
        hoveredLimb = null;
        lastHoveredLimb = null;
        channelProgress = 0.0f;
        virtualCursorX = mc.getWindow().getGuiScaledWidth() * 0.5f;
        virtualCursorY = mc.getWindow().getGuiScaledHeight() * 0.5f;
        lastRenderTimeMs = System.currentTimeMillis();
        MedicalSoundHelper.playOpenSound();
    }

    private static void deactivatePanel() {
        panelActive = false;
        hoveredLimb = null;
        lastHoveredLimb = null;
        channelProgress = 0.0f;
        MedicalSoundHelper.playCloseSound();
    }

    private static void triggerApplyPacket(InteractionHand hand, MedicalData.Limb limb, int doseUnits) {
        NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(hand.ordinal(), limb.ordinal(), doseUnits));
        MedicalSoundHelper.playLimbSelectSound();
        channelProgress = 0.0f;
        postApplyCooldownTicks = 8;
    }

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!panelActive) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        String layerName = event.getName().getPath();
        if (!"hotbar".equals(layerName)) {
            return;
        }

        ItemStack stack = player.getItemInHand(activeHand);
        if (!TreatmentRouter.isMedicalItem(stack)) {
            deactivatePanel();
            return;
        }

        MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
        MedicalAssetLibrary.AssetType assetType = MedicalAssetLibrary.resolveAssetType(stack);
        int themeColor = getAssetAccentColor(assetType);

        long now = System.currentTimeMillis();
        float dtSeconds = (lastRenderTimeMs == 0L) ? 0.016f : Mth.clamp((now - lastRenderTimeMs) / 1000.0f, 0.001f, 0.1f);
        lastRenderTimeMs = now;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;

        int panelW = 300;
        int panelH = 244;
        int px = (int) (cx - panelW * 0.5f);
        int py = (int) (cy - panelH * 0.5f);

        g.fill(px, py, px + panelW, py + panelH, C_BG_DARK);
        drawTacticalCornerBrackets(g, px, py, panelW, panelH, themeColor);

        boolean isOpioid = TreatmentRouter.isOpioidItem(stack);
        String title = isOpioid ? "选择注射部位 (随后调节剂量滑块)" : "部位施救";
        g.drawString(font, title, px + 10, py + 8, themeColor, false);
        g.fill(px + 8, py + 19, px + panelW - 8, py + 20, withAlpha(themeColor, 0.45f));

        MedicalAssetLibrary.render3DMedicalItem(g, px + 44.0f, py + 70.0f, 22.0f, stack, now);
        g.renderItem(stack, px + 10, py + 24);
        g.renderItemDecorations(font, stack, px + 10, py + 24);
        String itemTitle = stack.getHoverName().getString();
        g.drawString(font, itemTitle, px + 30, py + 28, C_TEXT_WHITE, true);

        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            float remRatio = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            int barW = 68;
            int barX = px + 10;
            int barY = py + 103;
            int durColor = remRatio > 0.5f ? C_SAFE_GREEN : (remRatio > 0.25f ? C_WARNING_YELLOW : C_ALERT_RED);
            g.fill(barX, barY, barX + barW, barY + 4, 0xAA081522);
            g.fill(barX, barY, barX + Math.round(barW * remRatio), barY + 4, durColor);
        }

        List<String> summaries = TreatmentRouter.getItemEffectSummary(stack);
        int infoY = py + 112;
        for (int i = 0; i < Math.min(3, summaries.size()); i++) {
            g.drawString(font, summaries.get(i), px + 10, infoY + i * 11, C_TEXT_DIM, false);
        }

        hoveredLimb = null;
        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            float sx = cx + slot.offsetX() - slot.width() * 0.5f;
            float sy = cy + slot.offsetY() - slot.height() * 0.5f + 8.0f;
            boolean isHovered = virtualCursorX >= sx && virtualCursorX <= sx + slot.width()
                    && virtualCursorY >= sy && virtualCursorY <= sy + slot.height();
            if (isHovered) {
                hoveredLimb = slot.limb();
            }
        }

        if (hoveredLimb != lastHoveredLimb) {
            if (hoveredLimb != null) {
                MedicalSoundHelper.playHoverSound();
            }
            channelProgress = 0.0f;
            lastHoveredLimb = hoveredLimb;
        }

        drawAnatomicalSkeletonLines(g, cx, cy + 8.0f, withAlpha(themeColor, 0.3f));

        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            drawLimbSlot(g, font, data, stack, slot, cx, cy + 8.0f, slot.limb() == hoveredLimb, themeColor, now);
        }

        if (hoveredLimb != null && postApplyCooldownTicks <= 0) {
            boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, hoveredLimb);
            if (canTreat) {
                float durationSec = Math.max(0.25f, TreatmentRouter.getChannelDurationSeconds(stack));
                channelProgress += dtSeconds / durationSec;
                if (channelProgress >= 1.0f) {
                    if (isOpioid) {
                        MedicalData.Limb chosen = hoveredLimb;
                        InteractionHand handCopy = activeHand;
                        deactivatePanel();
                        openOpioidDoseScreen(handCopy, chosen);
                        return;
                    } else {
                        triggerApplyPacket(activeHand, hoveredLimb, 100);
                    }
                }
            } else {
                channelProgress = 0.0f;
            }
        } else {
            channelProgress = 0.0f;
        }

        int footerY = py + panelH - 18;
        g.fill(px + 8, footerY - 4, px + panelW - 8, footerY - 3, withAlpha(themeColor, 0.35f));
        String statusHint;
        int hintColor;
        if (hoveredLimb == null) {
            statusHint = isOpioid ? "移至部位进入剂量滑块设定 | 或直接右键打开" : "移至受伤部位施救 | 松开左键退出";
            hintColor = C_TEXT_DIM;
        } else if (TreatmentRouter.canTreatLimb(stack, data, hoveredLimb)) {
            int pct = Mth.clamp((int) (channelProgress * 100.0f), 0, 100);
            statusHint = isOpioid
                    ? "选定 [" + getLimbDisplayName(hoveredLimb) + "] " + pct + "% (松开弹出剂量滑块)"
                    : "正在处理 [" + getLimbDisplayName(hoveredLimb) + "] " + pct + "% (松开立即完成)";
            hintColor = C_SAFE_GREEN;
        } else {
            statusHint = "[" + getLimbDisplayName(hoveredLimb) + "] 无需处理";
            hintColor = C_WARNING_YELLOW;
        }
        g.drawCenteredString(font, statusHint, (int) cx, footerY, hintColor);

        drawTacticalCursor(g, virtualCursorX, virtualCursorY, channelProgress, themeColor);
    }

    private static void drawLimbSlot(
            GuiGraphics g,
            Font font,
            MedicalData data,
            ItemStack stack,
            LimbSlotLayout slot,
            float centerX,
            float centerY,
            boolean hovered,
            int themeColor,
            long nowMs
    ) {
        MedicalData.LimbData limbData = data.getLimb(slot.limb());
        boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, slot.limb());
        int bleed = limbData.getBleeding();
        int pain = limbData.getPainLevel();
        boolean openWound = limbData.hasOpenWound();
        boolean fracture = limbData.hasFracture() || limbData.hasDislocation();
        boolean bandaged = limbData.isBandaged();

        int x0 = (int) (centerX + slot.offsetX() - slot.width() * 0.5f);
        int y0 = (int) (centerY + slot.offsetY() - slot.height() * 0.5f);
        int x1 = (int) (x0 + slot.width());
        int y1 = (int) (y0 + slot.height());

        int borderColor;
        int fillColor;
        if (canTreat && (pain > 0 || bleed > 0 || openWound || fracture)) {
            float pulse = 0.65f + 0.35f * Mth.sin(nowMs * 0.01f);
            borderColor = hovered ? C_SAFE_GREEN : withAlpha(C_ALERT_RED, pulse);
            fillColor = hovered ? withAlpha(C_SAFE_GREEN, 0.28f) : withAlpha(C_ALERT_RED, 0.20f);
        } else if (canTreat) {
            borderColor = hovered ? C_SAFE_GREEN : withAlpha(themeColor, 0.65f);
            fillColor = hovered ? withAlpha(C_SAFE_GREEN, 0.22f) : 0x77081524;
        } else {
            borderColor = hovered ? themeColor : withAlpha(themeColor, 0.42f);
            fillColor = hovered ? withAlpha(themeColor, 0.18f) : 0x660A1624;
        }

        g.fill(x0, y0, x1, y1, fillColor);
        g.fill(x0, y0, x1, y0 + 1, borderColor);
        g.fill(x0, y1 - 1, x1, y1, borderColor);
        g.fill(x0, y0, x0 + 1, y1, borderColor);
        g.fill(x1 - 1, y0, x1, y1, borderColor);

        if (hovered && channelProgress > 0.01f) {
            int barH = (int) ((y1 - y0 - 2) * Mth.clamp(channelProgress, 0.0f, 1.0f));
            g.fill(x0 + 1, y1 - 1 - barH, x1 - 1, y1 - 1, withAlpha(C_SAFE_GREEN, 0.35f));
        }

        g.drawCenteredString(font, slot.cnName(), (x0 + x1) / 2, y0 + 4, C_TEXT_WHITE);

        String subStatus;
        int subColor;
        if (TreatmentRouter.isOpioidItem(stack) && pain > 0) {
            subStatus = "痛感 " + pain + "/10";
            subColor = pain >= 7 ? C_ALERT_RED : C_WARNING_YELLOW;
        } else if (bleed > 0) {
            subStatus = "出血 " + bleed;
            subColor = C_ALERT_RED;
        } else if (openWound && !bandaged) {
            subStatus = "开放创口";
            subColor = C_WARNING_YELLOW;
        } else if (bandaged) {
            subStatus = "已包扎";
            subColor = C_SAFE_GREEN;
        } else if (fracture) {
            subStatus = limbData.hasSplint() ? "已固定" : "骨折";
            subColor = limbData.hasSplint() ? C_SAFE_GREEN : C_WARNING_YELLOW;
        } else if (pain > 0) {
            subStatus = "疼痛 " + pain;
            subColor = C_WARNING_YELLOW;
        } else {
            subStatus = "正常";
            subColor = C_TEXT_DIM;
        }
        g.drawCenteredString(font, subStatus, (x0 + x1) / 2, y0 + 16, subColor);
    }

    private static void drawAnatomicalSkeletonLines(GuiGraphics g, float cx, float cy, int lineColor) {
        g.fill((int) cx - 1, (int) (cy - 54), (int) cx + 1, (int) (cy - 48), lineColor);
        g.fill((int) (cx - 33), (int) (cy - 34), (int) (cx + 33), (int) (cy - 33), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx + 18), (int) (cy + 5), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx - 17), (int) (cy + 21), lineColor);
        g.fill((int) (cx + 17), (int) (cy + 4), (int) (cx + 18), (int) (cy + 21), lineColor);
    }

    private static void drawTacticalCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int len = 12;
        g.fill(x, y, x + len, y + 2, color);
        g.fill(x, y, x + 2, y + len, color);
        g.fill(x + w - len, y, x + w, y + 2, color);
        g.fill(x + w - 2, y, x + w, y + len, color);
        g.fill(x, y + h - 2, x + len, y + h, color);
        g.fill(x, y + h - len, x + 2, y + h, color);
        g.fill(x + w - len, y + h - 2, x + w, y + h, color);
        g.fill(x + w - 2, y + h - len, x + w, y + h, color);
    }

    private static void drawTacticalCursor(GuiGraphics g, float x, float y, float progress, int themeColor) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int c = (progress > 0.01f) ? C_SAFE_GREEN : themeColor;

        g.fill(ix - 6, iy, ix - 2, iy + 1, c);
        g.fill(ix + 3, iy, ix + 7, iy + 1, c);
        g.fill(ix, iy - 6, ix + 1, iy - 2, c);
        g.fill(ix, iy + 3, ix + 1, iy + 7, c);
        g.fill(ix, iy, ix + 1, iy + 1, C_TEXT_WHITE);

        if (progress > 0.01f) {
            PoseStack pose = g.pose();
            Matrix4f mat = pose.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            int segments = 28;
            float rInner = 9.0f;
            float rOuter = 12.0f;
            float maxAngle = Mth.TWO_PI * Mth.clamp(progress, 0.0f, 1.0f);

            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float a = -Mth.HALF_PI + maxAngle * t;
                float cos = Mth.cos(a);
                float sin = Mth.sin(a);
                buf.addVertex(mat, x + cos * rInner, y + sin * rInner, 0.0f).setColor(0, 230, 118, 220);
                buf.addVertex(mat, x + cos * rOuter, y + sin * rOuter, 0.0f).setColor(0, 229, 255, 255);
            }
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }
    }

    // =========================================================================
    // 3A 级阿片类镇痛剂定量推注滑块界面 (DoseSelectionScreen)
    // =========================================================================
    public static class DoseSelectionScreen extends Screen {

        private final InteractionHand hand;
        private MedicalData.Limb selectedLimb;
        private int selectedDoseUnits = 30;
        private boolean draggingSlider = false;

        // 布局缓存
        private int winX, winY, winW = 336, winH = 228;
        private int sliderX, sliderY, sliderW, sliderH = 18;

        public DoseSelectionScreen(InteractionHand hand, MedicalData.Limb initialLimb) {
            super(Component.literal("阿片类药物定量推注控制"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.selectedLimb = initialLimb;
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.sliderX = this.winX + 106;
            this.sliderY = this.winY + 94;
            this.sliderW = this.winW - 120;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                boolean isFentanyl = TreatmentRouter.isFentanylItem(stack);

                // 若未指定肢体，自动挑选当前疼痛最高且未截肢的部位作为默认注射部位
                if (this.selectedLimb == null && data != null) {
                    MedicalData.Limb best = MedicalData.Limb.LEFT_ARM;
                    int maxP = -1;
                    for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                        MedicalData.LimbData ld = data.getLimb(limb);
                        if (ld != null && !ld.isAmputated() && ld.getPainLevel() > maxP) {
                            maxP = ld.getPainLevel();
                            best = limb;
                        }
                    }
                    this.selectedLimb = best;
                }
                if (this.selectedLimb == null) {
                    this.selectedLimb = MedicalData.Limb.LEFT_ARM;
                }

                // 默认初始推荐滑块位置：芬太尼默认 30%（安全强效窗），吗啡默认 45%
                int defaultUnits = isFentanyl ? 30 : 45;
                this.selectedDoseUnits = Mth.clamp(defaultUnits, 1, remUnits);
            }
            MedicalSoundHelper.playOpenSound();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // 使用自定义全息半透明背景，避免原版全屏高斯模糊遮挡生理特效
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                this.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isOpioidItem(stack)) {
                this.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() <= 0 || data.isCardiacArrest()) {
                this.onClose();
                return;
            }

            int maxUnits = Math.max(1, TreatmentRouter.getMaxOpioidUnits(stack));
            int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
            this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits, 1, remUnits);

            boolean isFentanyl = TreatmentRouter.isFentanylItem(stack);
            float doseFraction = this.selectedDoseUnits / (float) maxUnits;
            float remRatio = remUnits / (float) maxUnits;

            // 从 MedicalConfig 读取药理参数计算实时预测
            float maxDrugAmount = isFentanyl
                    ? MedicalConfig.FENTANYL_MAX_MCG.get().floatValue()
                    : MedicalConfig.MORPHINE_MAX_MG.get().floatValue();
            String unitStr = isFentanyl ? "mcg" : "mg";
            float chosenAmount = doseFraction * maxDrugAmount;

            float concPerFull = isFentanyl
                    ? MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE.get().floatValue()
                    : MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE.get().floatValue();
            float currentConc = (data instanceof MedicalDataImpl impl)
                    ? impl.getOpioidConcentrationFloat()
                    : (float) data.getOpioidConcentration();
            float projectedConc = Mth.clamp(currentConc + doseFraction * concPerFull, 0.0f, 100.0f);

            int respDepStart = MedicalConfig.OPIOID_RESP_DEPRESSION_START.get();
            int overdoseThresh = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
            int apneaThresh = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();

            boolean isFatalApnea = projectedConc >= apneaThresh;
            boolean isOverdose = projectedConc >= overdoseThresh;
            boolean isMildDep = projectedConc >= respDepStart;

            long nowMs = System.currentTimeMillis();
            float pulse = 0.55f + 0.45f * Mth.sin(nowMs * 0.012f);

            // 全屏暗化衬底
            g.fill(0, 0, this.width, this.height, 0x8802060C);

            int accentColor = isFatalApnea ? C_ALERT_RED
                    : (isOverdose ? 0xFFFF5252 : (isFentanyl ? C_MAGENTA_HAZARD : C_WARNING_YELLOW));

            // 1. 绘制主面板边框与背景
            g.fill(winX, winY, winX + winW, winY + winH, 0xF2060D17);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, accentColor);
            g.fill(winX + 1, winY, winX + winW - 1, winY + 1, withAlpha(accentColor, 0.65f));
            g.fill(winX + 1, winY + winH - 1, winX + winW - 1, winY + winH, withAlpha(accentColor, 0.65f));

            // 顶部标题栏
            String header = isFentanyl
                    ? "枸橼酸芬太尼微量注射器 // 定量推注控制 (高危管制)"
                    : "盐酸吗啡自动注射器 // 定量推注控制";
            g.drawString(this.font, header, winX + 10, winY + 8, accentColor, false);
            g.fill(winX + 8, winY + 20, winX + winW - 8, winY + 21, withAlpha(accentColor, 0.40f));

            // 2. 左侧：实时 3D 动态活塞注射器预览舱 (活塞随滑块实时推进！)
            int leftBoxX = winX + 10;
            int leftBoxY = winY + 26;
            int leftBoxW = 88;
            int leftBoxH = winH - 36;
            g.fill(leftBoxX, leftBoxY, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0xAA030810);
            drawTacticalCornerBrackets(g, leftBoxX, leftBoxY, leftBoxW, leftBoxH, withAlpha(accentColor, 0.7f));

            MedicalAssetLibrary.render3DOpioidSyringeWithPlunger(
                    g,
                    leftBoxX + leftBoxW * 0.5f,
                    leftBoxY + 66.0f,
                    44.0f,
                    isFentanyl,
                    remRatio,
                    doseFraction,
                    nowMs
            );

            String remText = isFentanyl
                    ? String.format("本支剩余: %.0f mcg", remRatio * maxDrugAmount)
                    : String.format("本支剩余: %.1f mg", remRatio * maxDrugAmount);
            g.drawCenteredString(this.font, remText, leftBoxX + leftBoxW / 2, leftBoxY + 132, C_TEXT_WHITE);

            float afterRemRatio = Math.max(0.0f, remRatio - doseFraction);
            String afterText = isFentanyl
                    ? String.format("推后余量: %.0f mcg", afterRemRatio * maxDrugAmount)
                    : String.format("推后余量: %.1f mg", afterRemRatio * maxDrugAmount);
            g.drawCenteredString(this.font, afterText, leftBoxX + leftBoxW / 2, leftBoxY + 145, C_TEXT_DIM);

            String hintWheel = "滚轮/方向键微调";
            g.drawCenteredString(this.font, hintWheel, leftBoxX + leftBoxW / 2, leftBoxY + 168, 0xFF64B5F6);

            // 3. 右上：目标注射部位选择栏 (6 大部位快速切换标签)
            int rightX = winX + 106;
            int limbY = winY + 26;
            g.drawString(this.font, "注射部位:", rightX, limbY + 3, C_TEXT_DIM, false);

            MedicalData.Limb[] limbs = MedicalData.Limb.values();
            int tabW = 36;
            int tabH = 15;
            for (int i = 0; i < limbs.length; i++) {
                MedicalData.Limb limb = limbs[i];
                MedicalData.LimbData ld = data.getLimb(limb);
                boolean amputated = ld != null && ld.isAmputated();
                boolean selected = (limb == this.selectedLimb);
                int tx = rightX + i * (tabW + 2);
                int ty = limbY + 14;
                boolean hov = !amputated && mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + tabH;

                int bg = amputated ? 0x55202020 : (selected ? withAlpha(C_NEON_CYAN, 0.35f) : (hov ? 0x88102838 : 0x66081420));
                int bd = amputated ? 0xFF404040 : (selected ? C_NEON_CYAN : (ld != null && ld.getPainLevel() > 0 ? C_WARNING_YELLOW : 0xFF37474F));
                g.fill(tx, ty, tx + tabW, ty + tabH, bg);
                g.fill(tx, ty, tx + tabW, ty + 1, bd);
                g.fill(tx, ty + tabH - 1, tx + tabW, ty + tabH, bd);
                String lName = getLimbDisplayName(limb) + (ld != null && ld.getPainLevel() > 0 ? ("(" + ld.getPainLevel() + ")") : "");
                g.drawCenteredString(this.font, lName, tx + tabW / 2, ty + 4, amputated ? 0xFF666666 : C_TEXT_WHITE);
            }

            // 4. 中央核心：交互式剂量推注滑块 (Interactive Dose Slider)
            int labelY = sliderY - 14;
            String doseReadout = isFentanyl
                    ? String.format("推注剂量: %.0f %s (%d%%)", chosenAmount, unitStr, this.selectedDoseUnits)
                    : String.format("推注剂量: %.1f %s (%d%%)", chosenAmount, unitStr, this.selectedDoseUnits);
            g.drawString(this.font, doseReadout, sliderX, labelY, C_TEXT_WHITE, false);

            String zoneBadge = isFatalApnea ? "[ ☠ 呼吸骤停禁区 ]"
                    : (isOverdose ? "[ ⚠ 重度过量危象 ]"
                    : (isMildDep ? "[ 呼吸抑制警戒 ]" : "[ ✓ 安全治疗窗 ]"));
            int zoneColor = isFatalApnea ? C_ALERT_RED : (isOverdose ? 0xFFFF5252 : (isMildDep ? C_WARNING_YELLOW : C_SAFE_GREEN));
            g.drawString(this.font, zoneBadge, sliderX + sliderW - this.font.width(zoneBadge), labelY, zoneColor, false);

            // 绘制三色风险分区滑块底轨（根据每一刻度推算的血药浓度着色）
            g.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, 0xFF0A1926);
            int segments = 50;
            for (int s = 0; s < segments; s++) {
                float u0 = (s / (float) segments) * remUnits;
                float u1 = ((s + 1) / (float) segments) * remUnits;
                int sx0 = sliderX + Math.round((s / (float) segments) * sliderW);
                int sx1 = sliderX + Math.round(((s + 1) / (float) segments) * sliderW);
                float testConc = currentConc + ((u0 + u1) * 0.5f / maxUnits) * concPerFull;

                int segCol;
                if (testConc >= apneaThresh) {
                    segCol = 0x77FF1744;
                } else if (testConc >= overdoseThresh) {
                    segCol = 0x66FF5252;
                } else if (testConc >= respDepStart) {
                    segCol = 0x55FFB300;
                } else {
                    segCol = 0x5500E676;
                }
                g.fill(sx0, sliderY, sx1, sliderY + sliderH, segCol);
            }

            // 绘制当前已选中的剂量填充条
            float fillRatio = (remUnits <= 1) ? 1.0f : Mth.clamp((float) this.selectedDoseUnits / remUnits, 0.0f, 1.0f);
            int fillW = Math.round(sliderW * fillRatio);
            int activeFillCol = withAlpha(zoneColor, 0.85f);
            g.fill(sliderX, sliderY + 2, sliderX + fillW, sliderY + sliderH - 2, activeFillCol);

            // 绘制刻度尺竖线
            for (int tick = 0; tick <= 10; tick++) {
                int tx = sliderX + Math.round((tick / 10.0f) * sliderW);
                int th = (tick % 5 == 0) ? 6 : 3;
                g.fill(tx, sliderY + sliderH - th, tx + 1, sliderY + sliderH, 0xAAFFFFFF);
            }

            // 绘制滑块拖拽手柄 (Slider Thumb Knob)
            int knobX = sliderX + fillW;
            g.fill(knobX - 4, sliderY - 3, knobX + 4, sliderY + sliderH + 3, 0xFFFFFFFF);
            g.fill(knobX - 3, sliderY - 2, knobX + 3, sliderY + sliderH + 2, zoneColor);

            // 4 个快捷剂量预设按钮 (20% / 40% / 70% / 100%)
            int[] presets = new int[]{20, 40, 70, 100};
            String[] presetLabels = new String[]{"20% 微量", "40% 标准", "70% 强效", "100% 全支"};
            int presetY = sliderY + sliderH + 6;
            int presetBtnW = (sliderW - 9) / 4;
            for (int i = 0; i < presets.length; i++) {
                int pVal = Math.min(remUnits, presets[i]);
                int bx = sliderX + i * (presetBtnW + 3);
                boolean hov = mouseX >= bx && mouseX <= bx + presetBtnW && mouseY >= presetY && mouseY <= presetY + 14;
                boolean active = (this.selectedDoseUnits == pVal);
                int pBg = active ? withAlpha(zoneColor, 0.35f) : (hov ? 0x88142C42 : 0x66081420);
                int pBd = active ? zoneColor : 0xFF37474F;
                g.fill(bx, presetY, bx + presetBtnW, presetY + 14, pBg);
                g.fill(bx, presetY, bx + presetBtnW, presetY + 1, pBd);
                g.fill(bx, presetY + 13, bx + presetBtnW, presetY + 14, pBd);
                g.drawCenteredString(this.font, presetLabels[i], bx + presetBtnW / 2, presetY + 3, C_TEXT_WHITE);
            }

            // 5. 实时临床药理与生理后果预测面板 (Clinical Telemetry & Overdose Warning)
            int teleY = presetY + 19;
            int teleH = 56;
            int teleBorder = (isFatalApnea || isOverdose)
                    ? withAlpha(C_ALERT_RED, 0.55f + 0.45f * pulse)
                    : 0x88264653;
            g.fill(sliderX, teleY, sliderX + sliderW, teleY + teleH, 0xCC040A12);
            g.fill(sliderX, teleY, sliderX + sliderW, teleY + 1, teleBorder);
            g.fill(sliderX, teleY + teleH - 1, sliderX + sliderW, teleY + teleH, teleBorder);

            MedicalData.LimbData targetLd = data.getLimb(this.selectedLimb);
            int curLimbPain = targetLd != null ? targetLd.getPainLevel() : 0;
            float targetReliefMax = isFentanyl
                    ? MedicalConfig.FENTANYL_TARGET_PAIN_RELIEF.get().floatValue()
                    : MedicalConfig.MORPHINE_TARGET_PAIN_RELIEF.get().floatValue();
            int afterLimbPain = Math.max(0, curLimbPain - Math.max(1, Math.round(targetReliefMax * doseFraction)));

            int curPerceived = data.getPerceivedPain();
            double fullConc = Math.max(1.0, MedicalConfig.OPIOID_ANALGESIA_FULL_CONCENTRATION.get());
            double maxRelief = MedicalConfig.OPIOID_CENTRAL_ANALGESIA_MAX_REDUCTION.get();
            double exp = Math.max(0.2, MedicalConfig.OPIOID_ANALGESIA_CURVE_EXPONENT.get());
            double blockRatio = Math.min(1.0, projectedConc / fullConc);
            double blockFactor = Math.max(0.0, 1.0 - Math.pow(blockRatio, exp) * MedicalConfig.OPIOID_PAIN_TRANSMISSION_BLOCK.get());
            int afterPerceived = (int) Math.max(0, Math.round(curPerceived * blockFactor - maxRelief * Math.pow(blockRatio, exp) * 0.45));

            String line1 = String.format("预计镇痛: [%s] 疼痛 %d -> %d | 全身总体疼痛 %d -> %d",
                    getLimbDisplayName(this.selectedLimb), curLimbPain, afterLimbPain, curPerceived, afterPerceived);
            g.drawString(this.font, line1, sliderX + 6, teleY + 5, C_SAFE_GREEN, false);

            // 计算预计呼吸频率
            int curResp = data.getRespirationRate();
            int projResp;
            if (isFatalApnea) {
                projResp = 0;
            } else if (projectedConc > respDepStart) {
                float minFactor = MedicalConfig.OPIOID_MIN_DEPRESSED_RESP_FACTOR.get().floatValue();
                float depT = Mth.clamp((projectedConc - respDepStart) / Math.max(1.0f, (float) (apneaThresh - respDepStart)), 0.0f, 1.0f);
                float factor = Mth.lerp(depT * depT, 1.0f, minFactor);
                projResp = Math.max(1, Math.round(Math.max(12, curResp) * factor));
            } else {
                projResp = Math.max(12, curResp);
            }

            String line2 = String.format("血药浓度: %d%% -> %d%% | 预计呼吸频率: %d -> %d 次/分",
                    Math.round(currentConc), Math.round(projectedConc), curResp, projResp);
            g.drawString(this.font, line2, sliderX + 6, teleY + 18, zoneColor, false);

            String line3;
            String line4;
            if (isFatalApnea) {
                line3 = "☠ 致命过量警告：该剂量将直接麻痹延髓呼吸中枢，导致呼吸完全停止 (0 rpm)！";
                line4 = "   后果：血氧断崖暴跌、深度昏迷锁死、缺氧性室颤与不可逆脑死亡！";
            } else if (isOverdose) {
                line3 = "⚠ 重度过量警告：该剂量已超出安全上限，将引发重度呼吸抑制与迷走心动过缓！";
                line4 = "   后果：意识快速丧失陷入昏迷、低血氧脑损伤及心脏停搏高风险！";
            } else if (isMildDep) {
                line3 = "提示：进入轻中度呼吸抑制区间，呼吸频率将有所减慢并伴随镇静嗜睡。";
                line4 = "   建议：若非极重度创伤剧痛，可适当调低滑块以维持最佳呼吸代偿。";
            } else {
                line3 = "✓ 安全治疗窗：强效阻断痛觉神经传导，解除剧痛交感风暴且不引发严重呼吸抑制。";
                line4 = "   剩余药液将保留在注射器内，可随时再次调出滑块分次推注。";
            }
            g.drawString(this.font, line3, sliderX + 6, teleY + 31, isFatalApnea || isOverdose ? C_ALERT_RED : C_TEXT_WHITE, false);
            g.drawString(this.font, line4, sliderX + 6, teleY + 42, isFatalApnea || isOverdose ? 0xFFFF8A80 : C_TEXT_DIM, false);

            // 6. 底部「确认推注」与「取消」按钮
            int btnY = winY + winH - 24;
            int confirmW = 132;
            int cancelW = 72;
            int confirmX = sliderX;
            int cancelX = sliderX + sliderW - cancelW;

            boolean hovConfirm = mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 18;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 18;

            int confCol = (isFatalApnea || isOverdose) ? C_ALERT_RED : C_SAFE_GREEN;
            g.fill(confirmX, btnY, confirmX + confirmW, btnY + 18, hovConfirm ? withAlpha(confCol, 0.42f) : withAlpha(confCol, 0.24f));
            drawTacticalCornerBrackets(g, confirmX, btnY, confirmW, 18, confCol);
            String confirmText = (isFatalApnea || isOverdose) ? "⚠ 强行推注过量剂量" : "✓ 确认推注设定剂量";
            g.drawCenteredString(this.font, confirmText, confirmX + confirmW / 2, btnY + 5, C_TEXT_WHITE);

            g.fill(cancelX, btnY, cancelX + cancelW, btnY + 18, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, btnY, cancelW, 18, 0xFF90A4AE);
            g.drawCenteredString(this.font, "取消 (ESC)", cancelX + cancelW / 2, btnY + 5, C_TEXT_DIM);

            super.render(g, mouseX, mouseY, partialTick);
        }

        private void updateSliderFromMouse(double mouseX) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            ItemStack stack = mc.player.getItemInHand(this.hand);
            int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
            float ratio = Mth.clamp((float) (mouseX - this.sliderX) / (float) Math.max(1, this.sliderW), 0.0f, 1.0f);
            this.selectedDoseUnits = Mth.clamp(Math.round(1 + ratio * (remUnits - 1)), 1, remUnits);
        }

        private void confirmInjection() {
            if (this.selectedLimb != null && this.selectedDoseUnits > 0) {
                triggerApplyPacket(this.hand, this.selectedLimb, this.selectedDoseUnits);
            }
            this.onClose();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return false;
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));

                // 1. 检查滑块点击与拖拽
                if (mouseX >= sliderX - 6 && mouseX <= sliderX + sliderW + 6
                        && mouseY >= sliderY - 4 && mouseY <= sliderY + sliderH + 4) {
                    this.draggingSlider = true;
                    updateSliderFromMouse(mouseX);
                    MedicalSoundHelper.playHoverSound();
                    return true;
                }

                // 2. 检查 6 大肢体部位标签点击
                int rightX = winX + 106;
                int ty = winY + 26 + 14;
                int tabW = 36, tabH = 15;
                MedicalData.Limb[] limbs = MedicalData.Limb.values();
                for (int i = 0; i < limbs.length; i++) {
                    int tx = rightX + i * (tabW + 2);
                    if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + tabH) {
                        MedicalData.LimbData ld = data != null ? data.getLimb(limbs[i]) : null;
                        if (ld != null && !ld.isAmputated()) {
                            this.selectedLimb = limbs[i];
                            MedicalSoundHelper.playHoverSound();
                        }
                        return true;
                    }
                }

                // 3. 检查 4 个快捷剂量预设按钮
                int[] presets = new int[]{20, 40, 70, 100};
                int presetY = sliderY + sliderH + 6;
                int presetBtnW = (sliderW - 9) / 4;
                for (int i = 0; i < presets.length; i++) {
                    int bx = sliderX + i * (presetBtnW + 3);
                    if (mouseX >= bx && mouseX <= bx + presetBtnW && mouseY >= presetY && mouseY <= presetY + 14) {
                        this.selectedDoseUnits = Mth.clamp(presets[i], 1, remUnits);
                        MedicalSoundHelper.playHoverSound();
                        return true;
                    }
                }

                // 4. 检查确认推注与取消按钮
                int btnY = winY + winH - 24;
                int confirmW = 132;
                int cancelW = 72;
                int confirmX = sliderX;
                int cancelX = sliderX + sliderW - cancelW;

                if (mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 18) {
                    confirmInjection();
                    return true;
                }
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 18) {
                    this.onClose();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.draggingSlider && button == 0) {
                updateSliderFromMouse(mouseX);
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && this.draggingSlider) {
                this.draggingSlider = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && scrollY != 0) {
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                int step = Screen.hasShiftDown() ? 5 : 1;
                int dir = scrollY > 0 ? 1 : -1;
                this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + dir * step, 1, remUnits);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                int step = Screen.hasShiftDown() ? 5 : 1;

                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
                    this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits - step, 1, remUnits);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
                    this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + step, 1, remUnits);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
                    confirmInjection();
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}