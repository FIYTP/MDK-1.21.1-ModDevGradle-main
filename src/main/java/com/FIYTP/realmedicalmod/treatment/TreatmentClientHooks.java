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
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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

@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public class TreatmentClientHooks {

    private static final int C_BG_DARK        = 0xE6060D16;
    private static final int C_NEON_CYAN      = 0xFF00E5FF;
    private static final int C_SAFE_GREEN     = 0xFF00E676;
    private static final int C_WARNING_YELLOW = 0xFFFFB300;
    private static final int C_ALERT_RED      = 0xFFFF2A4B;
    private static final int C_MAGENTA_HAZARD = 0xFFFF1E8A;
    private static final int C_ADRENALINE_GOLD = 0xFFFFD600;
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

    // 延迟到 ClientTick 打开界面，严禁在 RenderGuiLayerEvent 渲染中途调用 mc.setScreen
    private static boolean pendingOpenDoseScreen = false;
    private static InteractionHand pendingDoseHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingDoseLimb = null;
    private static boolean pendingOpenSutureScreen = false;
    private static InteractionHand pendingSutureHand = InteractionHand.MAIN_HAND;
    private static MedicalData.Limb pendingSutureLimb = null;

    private record LimbSlotLayout(
            MedicalData.Limb limb,
            String cnName,
            float offsetX,
            float offsetY,
            float width,
            float height
    ) {}

    private static final LimbSlotLayout[] LIMB_LAYOUTS = new LimbSlotLayout[] {
            new LimbSlotLayout(MedicalData.Limb.HEAD,      "头部",   0.0f, -72.0f, 44.0f, 36.0f),
            new LimbSlotLayout(MedicalData.Limb.CHEST,     "胸腔",   0.0f, -22.0f, 52.0f, 52.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_ARM,  "左臂", -52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_ARM, "右臂",  52.0f, -20.0f, 38.0f, 54.0f),
            new LimbSlotLayout(MedicalData.Limb.LEFT_LEG,  "左腿", -24.0f,  52.0f, 40.0f, 62.0f),
            new LimbSlotLayout(MedicalData.Limb.RIGHT_LEG, "右腿",  24.0f,  52.0f, 40.0f, 62.0f)
    };

    public static void openBandageScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            activatePanel(mc, hand != null ? hand : InteractionHand.MAIN_HAND);
        }
    }

    public static void openVascularSutureScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
        if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                || data.isCardiacArrest() || data.isInVFib()) {
            return;
        }
        InteractionHand useHand = hand != null ? hand : InteractionHand.MAIN_HAND;
        ItemStack stack = mc.player.getItemInHand(useHand);
        if (!TreatmentRouter.isSutureKitItem(stack)) return;

        MedicalData.Limb target = preselectedLimb;
        if (target == null) {
            for (MedicalData.Limb limb : MedicalData.Limb.values()) {
                if (TreatmentRouter.canTreatLimb(stack, data, limb)) {
                    target = limb;
                    break;
                }
            }
        }
        if (target == null || !TreatmentRouter.canTreatLimb(stack, data, target)) {
            return;
        }

        if (panelActive) {
            panelActive = false;
            hoveredLimb = null;
            lastHoveredLimb = null;
            channelProgress = 0.0f;
        }
        mc.setScreen(new VascularSutureScreen(useHand, target));
    }

    public static void openOpioidDoseScreen(InteractionHand hand, MedicalData.Limb preselectedLimb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
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
            case ADRENALINE_INJECTOR -> C_ADRENALINE_GOLD;
            case NALOXONE_INJECTOR -> C_NEON_CYAN;
            case TOURNIQUET -> C_ALERT_RED;
            case VASCULAR_SUTURE_KIT -> C_SAFE_GREEN;
            case SYRINGE -> 0xFF40C4FF;
            case SPLINT -> 0xFF69F0AE;
            case MEDICINE_BOTTLE -> 0xFFFFD54F;
            default -> C_NEON_CYAN;
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
            if (!panelActive && postApplyCooldownTicks <= 0) {
                activatePanel(mc, medHand);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (pendingOpenDoseScreen) {
            pendingOpenDoseScreen = false;
            if (player != null && mc.screen == null) {
                openOpioidDoseScreen(pendingDoseHand, pendingDoseLimb);
            }
            return;
        }
        if (pendingOpenSutureScreen) {
            pendingOpenSutureScreen = false;
            if (player != null && mc.screen == null) {
                openVascularSutureScreen(pendingSutureHand, pendingSutureLimb);
            }
            return;
        }

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
            if (!panelActive && postApplyCooldownTicks <= 0) {
                activatePanel(mc, medHand);
            } else if (panelActive) {
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
                        if (TreatmentRouter.isSutureKitItem(stack)) {
                            MedicalData.Limb chosen = hoveredLimb;
                            InteractionHand handCopy = activeHand;
                            deactivatePanel();
                            openVascularSutureScreen(handCopy, chosen);
                            return;
                        } else if (TreatmentRouter.isOpioidItem(stack)) {
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
        postApplyCooldownTicks = 10;
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
        if (data == null) return;

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

        int panelW = 290;
        int panelH = 230;
        int px = (int) (cx - panelW * 0.5f);
        int py = (int) (cy - panelH * 0.5f);

        g.fill(px, py, px + panelW, py + panelH, C_BG_DARK);
        drawTacticalCornerBrackets(g, px, py, panelW, panelH, themeColor);

        boolean isOpioid = TreatmentRouter.isOpioidItem(stack);
        String itemTitle = stack.getHoverName().getString();
        g.drawString(font, itemTitle, px + 10, py + 9, themeColor, false);
        g.fill(px + 8, py + 20, px + panelW - 8, py + 21, withAlpha(themeColor, 0.45f));

        MedicalAssetLibrary.render3DMedicalItem(g, px + 42.0f, py + 76.0f, 34.0f, stack, now);

        if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
            float remRatio = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            int barW = 64;
            int barX = px + 10;
            int barY = py + 118;
            int durColor = remRatio > 0.5f ? C_SAFE_GREEN : (remRatio > 0.25f ? C_WARNING_YELLOW : C_ALERT_RED);
            g.fill(barX, barY, barX + barW, barY + 4, 0xAA081522);
            g.fill(barX, barY, barX + Math.round(barW * remRatio), barY + 4, durColor);
        }

        hoveredLimb = null;
        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            float sx = cx + slot.offsetX() - slot.width() * 0.5f;
            float sy = cy + slot.offsetY() - slot.height() * 0.5f + 6.0f;
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

        drawAnatomicalSkeletonLines(g, cx, cy + 6.0f, withAlpha(themeColor, 0.3f));

        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            drawLimbSlot(g, font, data, stack, slot, cx, cy + 6.0f, slot.limb() == hoveredLimb, themeColor, now);
        }

        if (hoveredLimb != null && postApplyCooldownTicks <= 0) {
            boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, hoveredLimb);
            if (canTreat) {
                float durationSec = Math.max(0.25f, TreatmentRouter.getChannelDurationSeconds(stack));
                channelProgress += dtSeconds / durationSec;
                if (channelProgress >= 1.0f) {
                    if (TreatmentRouter.isSutureKitItem(stack)) {
                        pendingSutureHand = activeHand;
                        pendingSutureLimb = hoveredLimb;
                        pendingOpenSutureScreen = true;
                        deactivatePanel();
                        return;
                    } else if (isOpioid) {
                        pendingDoseHand = activeHand;
                        pendingDoseLimb = hoveredLimb;
                        pendingOpenDoseScreen = true;
                        deactivatePanel();
                        return;
                    } else {
                        triggerApplyPacket(activeHand, hoveredLimb, 100);
                        deactivatePanel();
                        return;
                    }
                }
            } else {
                channelProgress = 0.0f;
            }
        } else {
            channelProgress = 0.0f;
        }

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
        if (limbData == null) return;
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

        g.drawCenteredString(font, slot.cnName(), (x0 + x1) / 2, (y0 + y1) / 2 - 4, C_TEXT_WHITE);
    }

    private static void drawAnatomicalSkeletonLines(GuiGraphics g, float cx, float cy, int lineColor) {
        g.fill((int) cx - 1, (int) (cy - 54), (int) cx + 1, (int) (cy - 48), lineColor);
        g.fill((int) (cx - 33), (int) (cy - 34), (int) (cx + 33), (int) (cy - 33), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx + 18), (int) (cy + 5), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx - 17), (int) (cy + 21), lineColor);
        g.fill((int) (cx + 17), (int) (cy + 4), (int) (cx + 18), (int) (cy + 21), lineColor);
    }

    private static void drawTacticalCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int len = 10;
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

        if (progress > 0.02f) {
            PoseStack pose = g.pose();
            Matrix4f mat = pose.last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            int segments = 24;
            float rInner = 9.0f;
            float rOuter = 12.0f;
            float maxAngle = Mth.TWO_PI * Mth.clamp(progress, 0.02f, 1.0f);

            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                float a = -Mth.HALF_PI + maxAngle * t;
                float cos = Mth.cos(a);
                float sin = Mth.sin(a);
                buf.addVertex(mat, x + cos * rInner, y + sin * rInner, 0.0f).setColor(0, 230, 118, 220);
                buf.addVertex(mat, x + cos * rOuter, y + sin * rOuter, 0.0f).setColor(0, 229, 255, 255);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }
    }

    // =========================================================================
    // 阿片类镇痛剂定量推注界面 (极简无废话玩家版)
    // =========================================================================
    public static class DoseSelectionScreen extends Screen {

        private final InteractionHand hand;
        private MedicalData.Limb selectedLimb;
        private int selectedDoseUnits = 30;
        private boolean draggingSlider = false;

        private float sliderDriftAccum = 0.0f;
        private long lastFrameTimeMs = 0L;

        private int winX, winY, winW = 296, winH = 158;
        private int sliderX, sliderY, sliderW, sliderH = 16;

        public DoseSelectionScreen(InteractionHand hand, MedicalData.Limb initialLimb) {
            super(Component.literal("注射"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.selectedLimb = initialLimb;
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.sliderX = this.winX + 86;
            this.sliderY = this.winY + 76;
            this.sliderW = this.winW - 98; // 198px

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                ItemStack stack = mc.player.getItemInHand(this.hand);
                int remUnits = Math.max(1, TreatmentRouter.getRemainingOpioidUnits(stack));
                boolean isFentanyl = TreatmentRouter.isFentanylItem(stack);

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
            float remRatio = remUnits / (float) maxUnits;

            float maxDrugAmount = isFentanyl
                    ? MedicalConfig.FENTANYL_MAX_MCG.get().floatValue()
                    : MedicalConfig.MORPHINE_MAX_MG.get().floatValue();
            String unitStr = isFentanyl ? "mcg" : "mg";

            float concPerFull = isFentanyl
                    ? MedicalConfig.FENTANYL_CONCENTRATION_PER_FULL_DOSE.get().floatValue()
                    : MedicalConfig.MORPHINE_CONCENTRATION_PER_FULL_DOSE.get().floatValue();
            float currentConc = (data instanceof MedicalDataImpl impl)
                    ? impl.getOpioidConcentrationFloat()
                    : (float) data.getOpioidConcentration();

            long nowMs = System.currentTimeMillis();
            float dtSec = (lastFrameTimeMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameTimeMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameTimeMs = nowMs;

            int addictionLevel = data.getAddictionLevel();
            int addictionTier = ModItems.getAddictionTier(addictionLevel);

            boolean sliderSelfMoving = false;
            if (addictionTier > 0 && remUnits > 1) {
                sliderSelfMoving = true;
                float driftSpeed = switch (addictionTier) {
                    case 1 -> MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T1.get().floatValue();
                    case 2 -> MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T2.get().floatValue();
                    default -> MedicalConfig.ADDICTION_SLIDER_DRIFT_SPEED_T3.get().floatValue();
                };
                float minSwing = (addictionTier == 1) ? 0.35f : (addictionTier == 2 ? 0.52f : 0.72f);
                float wave = 0.5f + 0.5f * (0.72f * Mth.sin(nowMs * 0.0042f) + 0.28f * Mth.cos(nowMs * 0.0115f));
                float targetUnits = Mth.lerp(wave, remUnits * minSwing, (float) remUnits);

                if (!this.draggingSlider || addictionTier >= 2) {
                    float dragResist = this.draggingSlider ? (addictionTier >= 3 ? 0.75f : 0.42f) : 1.0f;
                    float curPos = this.selectedDoseUnits + this.sliderDriftAccum;
                    float diff = targetUnits - curPos;
                    float dir = (Math.abs(diff) < 1.5f) ? (Mth.sin(nowMs * 0.009f) >= 0 ? 1.0f : -1.0f) : Math.signum(diff);
                    this.sliderDriftAccum += dir * driftSpeed * dragResist * dtSec;
                    if (Math.abs(this.sliderDriftAccum) >= 1.0f) {
                        int step = (int) this.sliderDriftAccum;
                        this.sliderDriftAccum -= step;
                        this.selectedDoseUnits = Mth.clamp(this.selectedDoseUnits + step, 1, remUnits);
                    }
                }
            }

            float doseFraction = this.selectedDoseUnits / (float) maxUnits;
            float chosenAmount = doseFraction * maxDrugAmount;
            float projectedConc = Mth.clamp(currentConc + doseFraction * concPerFull, 0.0f, 100.0f);

            int respDepStart = MedicalConfig.OPIOID_RESP_DEPRESSION_START.get();
            int overdoseThresh = MedicalConfig.OPIOID_OVERDOSE_THRESHOLD.get();
            int apneaThresh = MedicalConfig.OPIOID_APNEA_THRESHOLD.get();

            boolean isFatalApnea = projectedConc >= apneaThresh;
            boolean isOverdose = projectedConc >= overdoseThresh;
            boolean isMildDep = projectedConc >= respDepStart;

            boolean isSafeZoneBlind = false;
            if (addictionTier > 0) {
                double blindChance = switch (addictionTier) {
                    case 1 -> MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T1.get();
                    case 2 -> MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T2.get();
                    default -> MedicalConfig.ADDICTION_BLIND_ZONE_CHANCE_T3.get();
                };
                double phase = (nowMs % 6000L) / 6000.0;
                isSafeZoneBlind = (phase < blindChance);
            }

            g.fill(0, 0, this.width, this.height, 0x8802060C);

            int accentColor = isSafeZoneBlind ? 0xFFE040FB
                    : (isFatalApnea ? C_ALERT_RED
                    : (isOverdose ? 0xFFFF5252 : (isFentanyl ? C_MAGENTA_HAZARD : C_WARNING_YELLOW)));

            // 1. 面板框 (296 x 158)
            g.fill(winX, winY, winX + winW, winY + winH, 0xF2060D17);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, accentColor);
            g.fill(winX + 1, winY, winX + winW - 1, winY + 1, withAlpha(accentColor, 0.65f));
            g.fill(winX + 1, winY + winH - 1, winX + winW - 1, winY + winH, withAlpha(accentColor, 0.65f));

            String header = isFentanyl ? "枸橼酸芬太尼" : "盐酸吗啡";
            g.drawString(this.font, header, winX + 10, winY + 7, accentColor, false);
            g.fill(winX + 8, winY + 18, winX + winW - 8, winY + 19, withAlpha(accentColor, 0.40f));

            // 2. 左侧 3D 注射器预览框 (宽 68)
            int leftBoxX = winX + 10;
            int leftBoxY = winY + 24;
            int leftBoxW = 68;
            int leftBoxH = winH - 34;
            g.fill(leftBoxX, leftBoxY, leftBoxX + leftBoxW, leftBoxY + leftBoxH, 0xAA030810);
            drawTacticalCornerBrackets(g, leftBoxX, leftBoxY, leftBoxW, leftBoxH, withAlpha(accentColor, 0.7f));

            MedicalAssetLibrary.render3DOpioidSyringeWithPlunger(
                    g,
                    leftBoxX + leftBoxW * 0.5f,
                    leftBoxY + 52.0f,
                    36.0f,
                    isFentanyl,
                    remRatio,
                    doseFraction,
                    nowMs
            );

            String remText = isFentanyl
                    ? String.format("%.0f mcg", remRatio * maxDrugAmount)
                    : String.format("%.1f mg", remRatio * maxDrugAmount);
            g.drawCenteredString(this.font, remText, leftBoxX + leftBoxW / 2, leftBoxY + 106, C_TEXT_WHITE);

            // 3. 右上：6 大部位标签 (宽 31，间距 2，总宽 196 <= 198)
            int rightX = sliderX;
            int ty = winY + 28;
            MedicalData.Limb[] limbs = MedicalData.Limb.values();
            int tabW = 31;
            int tabH = 16;
            for (int i = 0; i < limbs.length; i++) {
                MedicalData.Limb limb = limbs[i];
                MedicalData.LimbData ld = data.getLimb(limb);
                boolean amputated = ld != null && ld.isAmputated();
                boolean selected = (limb == this.selectedLimb);
                int tx = rightX + i * (tabW + 2);
                boolean hov = !amputated && mouseX >= tx && mouseX <= tx + tabW && mouseY >= ty && mouseY <= ty + tabH;

                int bg = amputated ? 0x55202020 : (selected ? withAlpha(C_NEON_CYAN, 0.35f) : (hov ? 0x88102838 : 0x66081420));
                int bd = amputated ? 0xFF404040 : (selected ? C_NEON_CYAN : (ld != null && ld.getPainLevel() > 0 ? C_WARNING_YELLOW : 0xFF37474F));
                g.fill(tx, ty, tx + tabW, ty + tabH, bg);
                g.fill(tx, ty, tx + tabW, ty + 1, bd);
                g.fill(tx, ty + tabH - 1, tx + tabW, ty + tabH, bd);
                g.drawCenteredString(this.font, getLimbDisplayName(limb), tx + tabW / 2, ty + 4, amputated ? 0xFF666666 : C_TEXT_WHITE);
            }

            // 4. 中央：剂量滑块 (宽 198)
            int labelY = sliderY - 13;
            String doseReadout = isFentanyl
                    ? String.format("%.0f %s", chosenAmount, unitStr)
                    : String.format("%.1f %s", chosenAmount, unitStr);
            g.drawString(this.font, doseReadout, sliderX, labelY, C_TEXT_WHITE, false);

            int zoneColor = isSafeZoneBlind ? 0xFFE040FB
                    : (isFatalApnea ? C_ALERT_RED : (isOverdose ? 0xFFFF5252 : (isMildDep ? C_WARNING_YELLOW : C_SAFE_GREEN)));
            if (!isSafeZoneBlind) {
                String zoneBadge = (isFatalApnea || isOverdose) ? "过量" : (isMildDep ? "偏高" : "安全");
                g.drawString(this.font, zoneBadge, sliderX + sliderW - this.font.width(zoneBadge), labelY, zoneColor, false);
            }

            g.fill(sliderX - 1, sliderY - 1, sliderX + sliderW + 1, sliderY + sliderH + 1, 0xFF0A1926);
            int segments = 40;
            for (int s = 0; s < segments; s++) {
                float u0 = (s / (float) segments) * remUnits;
                float u1 = ((s + 1) / (float) segments) * remUnits;
                int sx0 = sliderX + Math.round((s / (float) segments) * sliderW);
                int sx1 = sliderX + Math.round(((s + 1) / (float) segments) * sliderW);

                int segCol;
                if (isSafeZoneBlind) {
                    float wave = 0.5f + 0.5f * Mth.sin(nowMs * 0.008f - s * 0.25f);
                    int r = (int) Mth.lerp(wave, 145, 255);
                    int gr = (int) Mth.lerp(wave, 35, 165);
                    int b = (int) Mth.lerp(wave, 210, 245);
                    segCol = (0x66 << 24) | (r << 16) | (gr << 8) | b;
                } else {
                    float testConc = currentConc + ((u0 + u1) * 0.5f / maxUnits) * concPerFull;
                    if (testConc >= apneaThresh) segCol = 0x77FF1744;
                    else if (testConc >= overdoseThresh) segCol = 0x66FF5252;
                    else if (testConc >= respDepStart) segCol = 0x55FFB300;
                    else segCol = 0x5500E676;
                }
                g.fill(sx0, sliderY, sx1, sliderY + sliderH, segCol);
            }

            float fillRatio = (remUnits <= 1) ? 1.0f : Mth.clamp((float) this.selectedDoseUnits / remUnits, 0.0f, 1.0f);
            int fillW = Math.round(sliderW * fillRatio);
            g.fill(sliderX, sliderY + 2, sliderX + fillW, sliderY + sliderH - 2, withAlpha(zoneColor, 0.85f));

            for (int tick = 0; tick <= 10; tick++) {
                int tx = sliderX + Math.round((tick / 10.0f) * sliderW);
                int th = (tick % 5 == 0) ? 5 : 3;
                g.fill(tx, sliderY + sliderH - th, tx + 1, sliderY + sliderH, 0xAAFFFFFF);
            }

            int knobX = sliderX + fillW;
            if (sliderSelfMoving) {
                g.fill(knobX - 5, sliderY - 3, knobX + 5, sliderY + sliderH + 3, withAlpha(0xFFE040FB, 0.65f));
            }
            g.fill(knobX - 3, sliderY - 2, knobX + 3, sliderY + sliderH + 2, 0xFFFFFFFF);
            g.fill(knobX - 2, sliderY - 1, knobX + 2, sliderY + sliderH + 1, zoneColor);

            // 4 个快捷比例按钮
            int[] presets = new int[]{25, 50, 75, 100};
            String[] presetLabels = new String[]{"1/4", "1/2", "3/4", "全量"};
            int presetY = sliderY + sliderH + 8;
            int presetBtnW = (sliderW - 9) / 4;
            for (int i = 0; i < presets.length; i++) {
                int pVal = Math.max(1, Math.round(remUnits * (presets[i] / 100.0f)));
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

            // 5. 底部按钮
            int btnY = winY + winH - 24;
            int confirmW = 114;
            int cancelW = 68;
            int confirmX = sliderX;
            int cancelX = sliderX + sliderW - cancelW;

            boolean hovConfirm = mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 16;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 16;

            int confCol = isSafeZoneBlind ? 0xFFE040FB : ((isFatalApnea || isOverdose) ? C_ALERT_RED : C_SAFE_GREEN);
            g.fill(confirmX, btnY, confirmX + confirmW, btnY + 16, hovConfirm ? withAlpha(confCol, 0.42f) : withAlpha(confCol, 0.24f));
            drawTacticalCornerBrackets(g, confirmX, btnY, confirmW, 16, confCol);
            g.drawCenteredString(this.font, "注射", confirmX + confirmW / 2, btnY + 4, C_TEXT_WHITE);

            g.fill(cancelX, btnY, cancelX + cancelW, btnY + 16, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, btnY, cancelW, 16, 0xFF90A4AE);
            g.drawCenteredString(this.font, "取消", cancelX + cancelW / 2, btnY + 4, C_TEXT_DIM);

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

                if (mouseX >= sliderX - 6 && mouseX <= sliderX + sliderW + 6
                        && mouseY >= sliderY - 4 && mouseY <= sliderY + sliderH + 4) {
                    this.draggingSlider = true;
                    updateSliderFromMouse(mouseX);
                    MedicalSoundHelper.playHoverSound();
                    return true;
                }

                int rightX = sliderX;
                int ty = winY + 28;
                int tabW = 31, tabH = 16;
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

                int[] presets = new int[]{25, 50, 75, 100};
                int presetY = sliderY + sliderH + 8;
                int presetBtnW = (sliderW - 9) / 4;
                for (int i = 0; i < presets.length; i++) {
                    int bx = sliderX + i * (presetBtnW + 3);
                    if (mouseX >= bx && mouseX <= bx + presetBtnW && mouseY >= presetY && mouseY <= presetY + 14) {
                        int pVal = Math.max(1, Math.round(remUnits * (presets[i] / 100.0f)));
                        this.selectedDoseUnits = Mth.clamp(pVal, 1, remUnits);
                        MedicalSoundHelper.playHoverSound();
                        return true;
                    }
                }

                int btnY = winY + winH - 24;
                int confirmW = 114;
                int cancelW = 68;
                int confirmX = sliderX;
                int cancelX = sliderX + sliderW - cancelW;

                if (mouseX >= confirmX && mouseX <= confirmX + confirmW && mouseY >= btnY && mouseY <= btnY + 16) {
                    confirmInjection();
                    return true;
                }
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + 16) {
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
    // =========================================================================
    // 野战血管缝合手术交互界面 (VascularSutureScreen —— 3A级微创血管外科手术模拟)
    // =========================================================================
    public static class VascularSutureScreen extends Screen {

        private static final class SurgicalParticle {
            boolean active;
            float x, y;
            float vx, vy;
            float size;
            float age, maxAge;
            int color;
            boolean isStain;
            boolean isSpark;
        }

        private final InteractionHand hand;
        private final MedicalData.Limb limb;
        private int initialBleedRate = 600;
        private boolean isMainArtery = false;
        private boolean surgeryCompleted = false;
        private boolean incisionOpened = false;

        // 0 = 扩创暴露并选择路径, 1 = 路径A(血管结扎闭塞术), 2 = 路径B(血管吻合桥接术)
        private int chosenPath = 0;
        private int subStep = 0;

        // 公共第 1 步：左右齿轮牵开器扩创 (0.0 -> 1.0) + 弹簧棘轮锁定
        private float leftRetract = 0.0f;
        private float rightRetract = 0.0f;
        private boolean leftLocked = false;
        private boolean rightLocked = false;
        private int lastLeftTooth = 0;
        private int lastRightTooth = 0;
        private int draggingRetractor = 0; // 0=无, 1=左拉钩, 2=右拉钩
        private float pathCardSlideAnim = 0.0f;

        // 路径 A-2 & 路径 B-2：上下断端血管夹闭与闭合动画
        private boolean topClamped = false;
        private boolean botClamped = false;
        private float topClampAnim = 0.0f;
        private float botClampAnim = 0.0f;

        // 路径 A-3：环绕绕线 + 死结张力保持
        private boolean windingDrag = false;
        private float lastWindAngle = 0.0f;
        private float totalWindProgress = 0.0f;
        private int lastWindTickStep = 0;
        private boolean pullingKnot = false;
        private float knotTension = 0.0f;
        private float knotHoldProgress = 0.0f;
        private int lastTensionTickStep = 0;

        // 路径 B-3：人工血管桥接管拖拽与角度微调
        private boolean draggingGraft = false;
        private float graftX = 0.0f;
        private float graftY = 0.0f;
        private float graftAngleDeg = 28.0f;
        private boolean graftInserted = false;
        private float graftSnapAnim = 0.0f;

        // 路径 B-4：环周等距微创吻合 (针数由 MedicalConfig.SUTURE_ANASTOMOSIS_STITCH_COUNT 驱动)
        private boolean[] stitchesDone = new boolean[6];
        private float[] stitchTightenAnim = new float[6];
        private int activeStitchIdx = -1;

        // 手术完成后的 3A 血流重灌注 / 结扎收尾动画
        private boolean playingCompletionFinale = false;
        private int pendingCompletionActionCode = 0;
        private float completionAnimProgress = 0.0f;

        // 实时粒子系统、ECG心电动脉波与受击震屏反馈
        private final SurgicalParticle[] particles = new SurgicalParticle[72];
        private final float[] ecgWaveHistory = new float[76];
        private float ecgSampleAccum = 0.0f;
        private float sprayAccum = 0.0f;
        private float mistakeFlashAlpha = 0.0f;
        private float screenShakeX = 0.0f;
        private float screenShakeY = 0.0f;
        private float screenShakeAmp = 0.0f;

        private long lastFrameMs = 0L;
        private float heartPhase = 0.0f;
        private float cursorTremorX = 0.0f;
        private float cursorTremorY = 0.0f;
        private float smoothMouseX = 0.0f;
        private float smoothMouseY = 0.0f;
        private float cursorTiltDeg = 0.0f;

        private int winX, winY, winW = 316, winH = 214;
        private int viewX, viewY, viewW = 224, viewH = 164;

        public VascularSutureScreen(InteractionHand hand, MedicalData.Limb limb) {
            super(Component.literal("血管缝合"));
            this.hand = hand != null ? hand : InteractionHand.MAIN_HAND;
            this.limb = limb != null ? limb : MedicalData.Limb.LEFT_LEG;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = new SurgicalParticle();
            }
        }

        private void playUiSound(SoundEvent sound, float pitch, float volume) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            }
        }

        private void spawnParticle(float x, float y, float vx, float vy, float size, float maxAge, int color, boolean isStain, boolean isSpark) {
            for (SurgicalParticle p : this.particles) {
                if (!p.active) {
                    p.active = true;
                    p.x = x;
                    p.y = y;
                    p.vx = vx;
                    p.vy = vy;
                    p.size = size;
                    p.age = 0.0f;
                    p.maxAge = maxAge;
                    p.color = color;
                    p.isStain = isStain;
                    p.isSpark = isSpark;
                    return;
                }
            }
        }

        private void triggerSurgicalMistake(float x, float y) {
            this.mistakeFlashAlpha = 0.85f;
            this.screenShakeAmp = 6.5f;
            playUiSound(SoundEvents.PLAYER_HURT, 1.15f, 0.55f);
            for (int i = 0; i < 18; i++) {
                float ang = (float) (Math.random() * Math.PI * 2.0);
                float spd = 28.0f + (float) (Math.random() * 68.0);
                spawnParticle(
                        x + (float) (Math.random() * 6.0 - 3.0),
                        y + (float) (Math.random() * 6.0 - 3.0),
                        Mth.cos(ang) * spd,
                        Mth.sin(ang) * spd - 12.0f,
                        2.2f + (float) (Math.random() * 2.0),
                        0.45f + (float) (Math.random() * 0.35),
                        0xFFFF1744,
                        true,
                        false
                );
            }
            NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 203));
        }

        private void spawnClampSparks(float x, float y, int color) {
            for (int i = 0; i < 12; i++) {
                float ang = (i / 12.0f) * Mth.TWO_PI + (float) (Math.random() * 0.25);
                float spd = 22.0f + (float) (Math.random() * 34.0);
                spawnParticle(x, y, Mth.cos(ang) * spd, Mth.sin(ang) * spd, 1.6f, 0.28f, color, false, true);
            }
        }

        @Override
        protected void init() {
            super.init();
            this.winX = (this.width - this.winW) / 2;
            this.winY = (this.height - this.winH) / 2;
            this.viewX = this.winX + 82;
            this.viewY = this.winY + 26;
            this.graftX = this.viewX + 38.0f;
            this.graftY = this.viewY + this.viewH * 0.54f;

            int stitchCount = Math.max(2, Math.min(12, MedicalConfig.SUTURE_ANASTOMOSIS_STITCH_COUNT.get()));
            this.stitchesDone = new boolean[stitchCount];
            this.stitchTightenAnim = new float[stitchCount];

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
                if (data != null) {
                    MedicalData.LimbData ld = data.getLimb(this.limb);
                    if (ld != null) {
                        this.initialBleedRate = Math.max(ld.getBleeding(), ModItems.getTourniquetClampedBleed(ld));
                        this.isMainArtery = this.initialBleedRate > ModItems.getSutureArterialThreshold()
                                || ModItems.isLimbArterialLigated(ld);
                    }
                }
                // 手术包自带止血带：打开手术时立即通知服务端用内置止血带夹闭该肢体出血
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 200));
            }
            MedicalSoundHelper.playOpenSound();
            playUiSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.92f, 0.75f);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public void onClose() {
            if (!this.surgeryCompleted && this.incisionOpened) {
                NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(this.hand.ordinal(), this.limb.ordinal(), 204));
            }
            super.onClose();
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                super.onClose();
                return;
            }
            ItemStack stack = mc.player.getItemInHand(this.hand);
            if (!TreatmentRouter.isSutureKitItem(stack)) {
                super.onClose();
                return;
            }
            MedicalData data = mc.player.getData(MedicalDataCapability.MEDICAL_DATA);
            if (data == null || data.getConsciousness() < ModItems.getSutureMinConsciousness()
                    || data.isCardiacArrest() || data.isInVFib()) {
                this.onClose();
                return;
            }

            long nowMs = System.currentTimeMillis();
            float dt = (lastFrameMs == 0L) ? 0.016f : Mth.clamp((nowMs - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
            lastFrameMs = nowMs;

            // 完成手术后的 0.65 秒 3A 血流重灌注/收尾动画
            if (this.playingCompletionFinale) {
                this.completionAnimProgress += dt / 0.65f;
                if (this.completionAnimProgress >= 1.0f) {
                    this.surgeryCompleted = true;
                    NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(
                            this.hand.ordinal(), this.limb.ordinal(), this.pendingCompletionActionCode));
                    MedicalSoundHelper.playLimbSelectSound();
                    this.onClose();
                    return;
                }
            }

            float hr = Mth.clamp(data.getHeartRate(), 40.0f, 220.0f);
            float prevPhase = this.heartPhase;
            this.heartPhase = (this.heartPhase + dt * (hr / 60.0f)) % 1.0f;
            if (this.heartPhase < prevPhase && (!this.topClamped || !this.botClamped)) {
                playUiSound(SoundEvents.WARDEN_HEARTBEAT, this.isMainArtery ? 1.18f : 0.95f, this.isMainArtery ? 0.42f : 0.22f);
            }
            float beatWave = computeArterialPulseWave(this.heartPhase);

            // 采样实时心电/动脉血压波形历史
            this.ecgSampleAccum += dt;
            while (this.ecgSampleAccum >= 0.018f) {
                this.ecgSampleAccum -= 0.018f;
                System.arraycopy(this.ecgWaveHistory, 1, this.ecgWaveHistory, 0, this.ecgWaveHistory.length - 1);
                this.ecgWaveHistory[this.ecgWaveHistory.length - 1] = beatWave;
            }

            // 手部生理抖动（完全由 MedicalConfig 配置驱动）
            float painRatio = Mth.clamp(data.getPerceivedPain() / Math.max(10.0f, MedicalConfig.MAX_PERCEIVED_PAIN.get().floatValue()), 0.0f, 1.0f);
            float maxPainShake = MedicalConfig.SUTURE_CURSOR_PAIN_SHAKE_MAX.get().floatValue();
            float wdShake = ModItems.isInWithdrawalMovementRestriction(data)
                    ? MedicalConfig.SUTURE_CURSOR_WITHDRAWAL_SHAKE.get().floatValue() : 0.0f;
            float shakeAmp = painRatio * maxPainShake + wdShake;
            this.cursorTremorX = (Mth.sin(nowMs * 0.017f) * 0.6f + Mth.cos(nowMs * 0.031f) * 0.4f) * shakeAmp;
            this.cursorTremorY = (Mth.cos(nowMs * 0.021f) * 0.6f + Mth.sin(nowMs * 0.027f) * 0.4f) * shakeAmp;

            float targetMx = mouseX + this.cursorTremorX;
            float targetMy = mouseY + this.cursorTremorY;
            if (this.smoothMouseX == 0.0f && this.smoothMouseY == 0.0f) {
                this.smoothMouseX = targetMx;
                this.smoothMouseY = targetMy;
            } else {
                float vx = (targetMx - this.smoothMouseX) / Math.max(0.004f, dt);
                this.cursorTiltDeg = Mth.lerp(Mth.clamp(dt * 14.0f, 0.0f, 1.0f), this.cursorTiltDeg, Mth.clamp(vx * 0.035f, -18.0f, 18.0f));
                this.smoothMouseX = Mth.lerp(Mth.clamp(dt * 28.0f, 0.0f, 1.0f), this.smoothMouseX, targetMx);
                this.smoothMouseY = Mth.lerp(Mth.clamp(dt * 28.0f, 0.0f, 1.0f), this.smoothMouseY, targetMy);
            }

            // 震屏衰减与失误红光衰减
            if (this.screenShakeAmp > 0.01f) {
                this.screenShakeAmp = Math.max(0.0f, this.screenShakeAmp - dt * 22.0f);
                this.screenShakeX = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
                this.screenShakeY = (float) (Math.random() * 2.0 - 1.0) * this.screenShakeAmp;
            } else {
                this.screenShakeX = 0.0f;
                this.screenShakeY = 0.0f;
            }
            if (this.mistakeFlashAlpha > 0.005f) {
                this.mistakeFlashAlpha = Math.max(0.0f, this.mistakeFlashAlpha - dt * 2.4f);
            }

            g.fill(0, 0, this.width, this.height, 0x9602060C);

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.screenShakeX, this.screenShakeY, 0.0f);

            int themeCol = this.isMainArtery ? C_ALERT_RED : C_SAFE_GREEN;
            g.fill(winX, winY, winX + winW, winY + winH, 0xF4050B14);
            drawTacticalCornerBrackets(g, winX, winY, winW, winH, themeCol);
            g.fill(winX + 8, winY + 19, winX + winW - 8, winY + 20, withAlpha(themeCol, 0.45f));

            g.drawString(this.font, getLimbDisplayName(this.limb), winX + 10, winY + 7, C_TEXT_WHITE, false);

            // 顶部步骤进度指示条（带流光高亮）
            int totalSteps = (chosenPath == 1) ? 3 : (chosenPath == 2 ? 4 : 2);
            int curStepIdx = (chosenPath == 0) ? 0 : this.subStep;
            for (int s = 0; s < totalSteps; s++) {
                int sx = viewX + s * 36;
                int sy = winY + 8;
                boolean done = (s < curStepIdx) || this.playingCompletionFinale;
                boolean active = (s == curStepIdx) && !this.playingCompletionFinale;
                int sCol = done ? C_SAFE_GREEN : (active ? C_NEON_CYAN : 0xFF1E2B36);
                g.fill(sx - 1, sy - 1, sx + 31, sy + 7, 0xFF0A141E);
                g.fill(sx, sy, sx + 30, sy + 6, sCol);
                if (active) {
                    int glintOffset = (int) ((nowMs / 18L) % 26L);
                    g.fill(sx + glintOffset, sy, sx + Math.min(30, glintOffset + 6), sy + 6, 0xAAFFFFFF);
                }
            }

            // 左侧：3D 手术盒全息投影 + 内置止血带缺血倒计时发光条 + 血管类型特征标识
            int leftX = winX + 10;
            int leftY = viewY;
            int leftW = 64;
            int leftH = viewH;
            g.fill(leftX, leftY, leftX + leftW, leftY + leftH, 0xB8030810);
            drawTacticalCornerBrackets(g, leftX, leftY, leftW, leftH, withAlpha(themeCol, 0.72f));
            MedicalAssetLibrary.render3DMedicalItem(g, leftX + leftW * 0.5f, leftY + 50.0f, 31.0f, stack, nowMs);

            // 左侧中部：动脉/非动脉双路径动态脉冲图腾
            int pulseBoxY = leftY + 94;
            g.fill(leftX + 6, pulseBoxY, leftX + leftW - 6, pulseBoxY + 34, 0x8806101A);
            drawVesselTypeIndicator(g, leftX + leftW * 0.5f, pulseBoxY + 17.0f, beatWave, nowMs);

            // 左侧底部：内置止血带安全缺血倒计时条
            MedicalData.LimbData ld = data.getLimb(this.limb);
            int tqSec = ld != null ? ModItems.getTourniquetTimer(ld) : 0;
            float tqRatio = Mth.clamp(tqSec / (float) Math.max(1, ModItems.getTourniquetNecrosisSeconds()), 0.0f, 1.0f);
            int tqBarCol = tqRatio > 0.75f ? C_ALERT_RED : (tqRatio > 0.45f ? C_WARNING_YELLOW : C_NEON_CYAN);
            g.fill(leftX + 7, leftY + leftH - 16, leftX + leftW - 7, leftY + leftH - 10, 0xFF0A1622);
            int tqFillW = Math.round((leftW - 14) * (1.0f - tqRatio));
            g.fill(leftX + 7, leftY + leftH - 16, leftX + 7 + tqFillW, leftY + leftH - 10, tqBarCol);

            // 中央手术创腔主视窗
            g.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xFF120408);

            float cx = viewX + viewW * 0.5f;
            float cy = viewY + viewH * 0.54f;

            // 渲染多层解剖创腔、实时血流粒子、手术器械交互与心电波形带
            g.enableScissor(viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
            renderSurgicalCavityAndVessel(g, cx, cy, beatWave, dt, targetMx, targetMy, nowMs);
            renderBloodAndSparkParticles(g, dt);
            renderHemodynamicOscilloscope(g, beatWave);
            if (this.mistakeFlashAlpha > 0.01f) {
                drawViewportVignette(g, viewX, viewY, viewW, viewH, withAlpha(C_ALERT_RED, this.mistakeFlashAlpha * 0.55f));
            }
            g.disableScissor();

            drawTacticalCornerBrackets(g, viewX, viewY, viewW, viewH, this.mistakeFlashAlpha > 0.1f ? C_ALERT_RED : 0xFF546E7A);

            // 底部取消按钮
            int cancelW = 54, cancelH = 14;
            int cancelX = viewX + viewW - cancelW;
            int cancelY = winY + winH - 19;
            boolean hovCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
            g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, hovCancel ? 0x8837474F : 0x551C2833);
            drawTacticalCornerBrackets(g, cancelX, cancelY, cancelW, cancelH, 0xFF90A4AE);
            g.drawCenteredString(this.font, "×", cancelX + cancelW / 2, cancelY + 3, C_TEXT_DIM);

            // 渲染跟随鼠标的 3A 矢量拟真外科手术器械与准星
            renderActiveSurgicalInstrumentCursor(g, this.smoothMouseX, this.smoothMouseY, cx, cy, beatWave, nowMs);

            pose.popPose();
            super.render(g, mouseX, mouseY, partialTick);
        }

        private float computeArterialPulseWave(float phase) {
            // 拟真动脉压力波：快速收缩期主峰 (0.0~0.20) + 重搏切迹与重搏波 (0.20~0.38) + 舒张期平静窗 (0.38~1.0)
            if (phase < 0.18f) {
                return Mth.sin((phase / 0.18f) * (float) Math.PI);
            } else if (phase < 0.34f) {
                float sub = (phase - 0.18f) / 0.16f;
                return 0.38f * Mth.sin(sub * (float) Math.PI);
            }
            return 0.04f * Mth.sin((phase - 0.34f) * Mth.PI);
        }

        private void drawVesselTypeIndicator(GuiGraphics g, float x, float y, float beatWave, long nowMs) {
            Matrix4f mat = g.pose().last().pose();
            float r = this.isMainArtery ? (8.5f + beatWave * 3.8f) : (7.0f + Mth.sin(nowMs * 0.005f) * 1.0f);
            int outerCol = this.isMainArtery ? C_ALERT_RED : C_SAFE_GREEN;
            drawRingArc(mat, x, y, r - 2.0f, r, 0.0f, Mth.TWO_PI, withAlpha(outerCol, 0.85f), 20);
            drawRingArc(mat, x, y, 0.0f, r - 3.2f, 0.0f, Mth.TWO_PI, this.isMainArtery ? 0xCCFF1744 : 0xCC00E676, 16);
        }

        private void renderHemodynamicOscilloscope(GuiGraphics g, float beatWave) {
            int scopeX = viewX + 4;
            int scopeY = viewY + 4;
            int scopeW = viewW - 8;
            int scopeH = 20;
            g.fill(scopeX, scopeY, scopeX + scopeW, scopeY + scopeH, 0xCC040A12);
            g.fill(scopeX, scopeY + scopeH - 1, scopeX + scopeW, scopeY + scopeH, 0x55263238);

            float calmMax = MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX.get().floatValue();
            if (this.chosenPath == 2 && this.subStep == 1) {
                // 在路径 B-1 高亮显示舒张期安全下夹阈值带
                int safeTopY = Math.round(scopeY + scopeH - 3 - calmMax * (scopeH - 6));
                g.fill(scopeX + 1, safeTopY, scopeX + scopeW - 1, scopeY + scopeH - 2, 0x2E00E676);
                g.fill(scopeX + 1, safeTopY, scopeX + scopeW - 1, safeTopY + 1, 0x7700E676);
            }

            Matrix4f mat = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            int n = this.ecgWaveHistory.length;
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                float px = scopeX + 3.0f + t * (scopeW - 6.0f);
                float val = this.ecgWaveHistory[i];
                float py = scopeY + scopeH - 3.5f - val * (scopeH - 6.5f);
                boolean inCalm = val < calmMax;
                int col = (this.chosenPath == 2 && this.subStep == 1)
                        ? (inCalm ? C_SAFE_GREEN : C_ALERT_RED)
                        : (this.isMainArtery ? 0xFFFF5252 : C_NEON_CYAN);
                int alpha = Math.round((0.25f + 0.75f * t) * 255.0f);
                int r = (col >> 16) & 0xFF;
                int gr = (col >> 8) & 0xFF;
                int b = col & 0xFF;
                buf.addVertex(mat, px, py - 0.9f, 0.0f).setColor(r, gr, b, alpha);
                buf.addVertex(mat, px, py + 0.9f, 0.0f).setColor(r, gr, b, alpha);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void renderBloodAndSparkParticles(GuiGraphics g, float dt) {
            Matrix4f mat = g.pose().last().pose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean hasAny = false;

            for (SurgicalParticle p : this.particles) {
                if (!p.active) continue;
                p.age += dt;
                if (p.age >= p.maxAge) {
                    p.active = false;
                    continue;
                }
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.isSpark) {
                    p.vx *= (1.0f - dt * 5.5f);
                    p.vy *= (1.0f - dt * 5.5f);
                } else {
                    p.vy += 85.0f * dt;
                    p.vx *= (1.0f - dt * 3.2f);
                    if (p.isStain && p.age > p.maxAge * 0.45f) {
                        p.vx *= 0.85f;
                        p.vy *= 0.85f;
                    }
                }

                float lifeRatio = 1.0f - (p.age / p.maxAge);
                float half = p.size * (p.isSpark ? lifeRatio : (0.65f + 0.35f * lifeRatio));
                int a = Mth.clamp(Math.round(lifeRatio * 235.0f), 0, 255);
                int r = (p.color >> 16) & 0xFF;
                int gr = (p.color >> 8) & 0xFF;
                int b = p.color & 0xFF;

                buf.addVertex(mat, p.x - half, p.y + half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + half, p.y + half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x + half, p.y - half, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, p.x - half, p.y - half, 0.0f).setColor(r, gr, b, a);
                hasAny = true;
            }

            MeshData mesh = buf.build();
            if (mesh != null && hasAny) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void renderSurgicalCavityAndVessel(GuiGraphics g, float cx, float cy, float beatWave,
                                                   float dt, float mx, float my, long nowMs) {
            float openThresh = MedicalConfig.SUTURE_RETRACTOR_OPEN_THRESHOLD.get().floatValue();

            // 牵开器弹簧物理回弹与棘轮锁定
            if (this.draggingRetractor != 1 && !this.leftLocked) {
                if (this.leftRetract >= openThresh) {
                    this.leftLocked = true;
                    this.leftRetract = 1.0f;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.45f, 0.70f);
                    spawnClampSparks(viewX + 20.0f, cy, C_SAFE_GREEN);
                } else {
                    this.leftRetract = Math.max(0.0f, this.leftRetract - dt * 1.65f);
                }
            }
            if (this.draggingRetractor != 2 && !this.rightLocked) {
                if (this.rightRetract >= openThresh) {
                    this.rightLocked = true;
                    this.rightRetract = 1.0f;
                    playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.45f, 0.70f);
                    spawnClampSparks(viewX + viewW - 20.0f, cy, C_SAFE_GREEN);
                } else {
                    this.rightRetract = Math.max(0.0f, this.rightRetract - dt * 1.65f);
                }
            }

            float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
            if (this.topClamped) this.topClampAnim = Math.min(1.0f, this.topClampAnim + dt * 8.0f);
            if (this.botClamped) this.botClampAnim = Math.min(1.0f, this.botClampAnim + dt * 8.0f);

            Matrix4f mat = g.pose().last().pose();

            // 1. 深层创腔横纹肌束背景层
            for (int band = -5; band <= 5; band++) {
                float by = cy + band * 12.5f;
                int shade = (Math.abs(band) % 2 == 0) ? 0xFF240810 : 0xFF1B050B;
                g.fill(viewX + 2, Math.round(by - 5), viewX + viewW - 2, Math.round(by + 5), shade);
            }

            // 计算上下血管断端实时坐标（含生理搏动与组织痉挛游走）
            float driftAmp = MedicalConfig.SUTURE_STUMP_DRIFT_AMPLITUDE.get().floatValue();
            float stumpDriftX = (this.chosenPath == 1 && this.subStep == 1)
                    ? (Mth.sin(nowMs * 0.0042f) * 0.7f + Mth.cos(nowMs * 0.0071f) * 0.3f) * driftAmp
                    : 0.0f;
            float topX = cx + (this.topClamped ? 0.0f : stumpDriftX);
            float botX = cx - (this.botClamped ? 0.0f : stumpDriftX);

            float topStumpEnd = cy - 19.0f + (this.topClamped ? 0.0f : beatWave * (this.isMainArtery ? 4.5f : 1.5f));
            float botStumpStart = cy + 19.0f - (this.botClamped ? 0.0f : beatWave * (this.isMainArtery ? 4.5f : 1.5f));

            // 结扎收紧时血管颈部收缩变形
            float neckPinch = (this.chosenPath == 1 && this.subStep == 2) ? (this.knotTension * 4.5f) : 0.0f;
            float vesselRadius = this.isMainArtery ? (9.5f + (this.topClamped ? 0.0f : beatWave * 2.8f)) : 6.5f;

            int outerWallCol = this.isMainArtery ? 0xFFD50032 : 0xFF7B0D40;
            int innerLumenCol = this.isMainArtery ? 0xFFFF3D57 : 0xFF4A0726;
            int specHighlightCol = this.isMainArtery ? 0x77FF8A80 : 0x55F48FB1;

            // 完成路径 A 强行结扎动脉时的远端缺血发绀变色演示
            int botOuterCol = (this.playingCompletionFinale && this.chosenPath == 1 && this.isMainArtery)
                    ? 0xFF4A148C : outerWallCol;
            int botInnerCol = (this.playingCompletionFinale && this.chosenPath == 1 && this.isMainArtery)
                    ? 0xFF311B92 : innerLumenCol;

            // 绘制上断端立体血管束 (带内膜管腔与高光反射带)
            drawTaperedVesselSegment(mat, cx, viewY + 24.0f, topX, topStumpEnd,
                    vesselRadius, Math.max(2.5f, vesselRadius - neckPinch), outerWallCol, innerLumenCol, specHighlightCol);

            // 绘制下断端立体血管束
            drawTaperedVesselSegment(mat, botX, botStumpStart, cx, viewY + viewH - 6.0f,
                    Math.max(2.5f, vesselRadius - neckPinch), vesselRadius, botOuterCol, botInnerCol, specHighlightCol);

            // 2. 断端动态喷血粒子与静脉漫溢涟漪
            if (openRatio > 0.25f && (!this.topClamped || !this.botClamped) && !this.playingCompletionFinale) {
                this.sprayAccum += dt;
                if (this.isMainArtery) {
                    float interval = (beatWave > 0.40f) ? 0.018f : 0.065f;
                    while (this.sprayAccum >= interval) {
                        this.sprayAccum -= interval;
                        float speedScale = 0.45f + 0.95f * beatWave;
                        if (!this.topClamped) {
                            float ang = Mth.HALF_PI + (float) (Math.random() * 0.85 - 0.425);
                            float spd = (45.0f + (float) (Math.random() * 75.0)) * speedScale;
                            spawnParticle(topX + (float) (Math.random() * 6.0 - 3.0), topStumpEnd,
                                    Mth.cos(ang) * spd, Mth.sin(ang) * spd,
                                    1.8f + beatWave * 1.4f, 0.38f, 0xFFFF1744, true, false);
                        }
                        if (!this.botClamped) {
                            float ang = -Mth.HALF_PI + (float) (Math.random() * 0.75 - 0.375);
                            float spd = (28.0f + (float) (Math.random() * 45.0)) * speedScale;
                            spawnParticle(botX + (float) (Math.random() * 6.0 - 3.0), botStumpStart,
                                    Mth.cos(ang) * spd, Mth.sin(ang) * spd,
                                    1.5f + beatWave * 1.0f, 0.32f, 0xFFD50000, true, false);
                        }
                    }
                } else {
                    // 非动脉大出血 (500~1000 mL/min)：暗红静脉血池波纹漫溢
                    float ripplePhase = (nowMs % 1400L) / 1400.0f;
                    drawRingArc(mat, cx, cy, 6.0f + ripplePhase * 22.0f, 8.5f + ripplePhase * 22.0f,
                            0.0f, Mth.TWO_PI, withAlpha(0xFFAD1457, (1.0f - ripplePhase) * 0.65f), 24);
                    while (this.sprayAccum >= 0.08f) {
                        this.sprayAccum -= 0.08f;
                        float ang = (float) (Math.random() * Math.PI * 2.0);
                        spawnParticle(cx + Mth.cos(ang) * 8.0f, cy + Mth.sin(ang) * 8.0f,
                                Mth.cos(ang) * 18.0f, Mth.sin(ang) * 18.0f,
                                2.0f, 0.45f, 0xFF880E4F, true, false);
                    }
                }
            }

            // 3. 已锁定的血管止血钳 (Path A) 或 金色哈巴狗动脉微血管夹 (Path B)
            float releaseScale = this.playingCompletionFinale ? (1.0f - this.completionAnimProgress) : 1.0f;
            if (this.topClamped && releaseScale > 0.05f) {
                renderClampedInstrumentOnVessel(g, mat, topX, topStumpEnd - 5.0f, true, this.chosenPath == 2, this.topClampAnim * releaseScale);
            }
            if (this.botClamped && releaseScale > 0.05f) {
                renderClampedInstrumentOnVessel(g, mat, botX, botStumpStart + 5.0f, false, this.chosenPath == 2, this.botClampAnim * releaseScale);
            }

            // 4. 各阶段核心互动与微创器械渲染
            if (this.chosenPath == 1) {
                renderPathALigationStage(g, mat, cx, cy, topX, botX, topStumpEnd, botStumpStart, vesselRadius, beatWave, dt, mx, my, nowMs);
            } else if (this.chosenPath == 2) {
                renderPathBAnastomosisStage(g, mat, cx, cy, topStumpEnd, botStumpStart, vesselRadius, beatWave, dt, mx, my, nowMs);
            }

            // 5. 表层肌肉切口组织瓣与胶原牵引丝（随牵开器拉开向两侧弹性退缩）
            float gapHalfW = 5.0f + openRatio * 74.0f;
            float flapLeftEdge = cx - gapHalfW;
            float flapRightEdge = cx + gapHalfW;
            renderRetractedMuscleFlaps(g, mat, flapLeftEdge, flapRightEdge, cy, openRatio);

            // 6. 第 0 阶段：齿轮牵开器锁轨与双路径全息选择卡
            if (this.chosenPath == 0) {
                renderRetractorAndPathCards(g, mat, cx, cy, flapLeftEdge, flapRightEdge, openRatio, dt, mx, my, nowMs);
            }
        }

        private void renderPathALigationStage(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                              float topX, float botX, float topStumpEnd, float botStumpStart,
                                              float vesselRadius, float beatWave, float dt, float mx, float my, long nowMs) {
            float clampRadius = MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS.get().floatValue();
            if (this.subStep == 1) {
                // A-1：游走断端动态锁定环
                float pulseR = clampRadius + Mth.sin(nowMs * 0.012f) * 1.8f;
                if (!this.topClamped) {
                    boolean nearTop = Math.hypot(mx - topX, my - (topStumpEnd - 4.0f)) <= clampRadius;
                    drawRingArc(mat, topX, topStumpEnd - 4.0f, pulseR - 2.0f, pulseR, 0.0f, Mth.TWO_PI,
                            nearTop ? C_SAFE_GREEN : C_NEON_CYAN, 24);
                }
                if (!this.botClamped) {
                    boolean nearBot = Math.hypot(mx - botX, my - (botStumpStart + 4.0f)) <= clampRadius;
                    drawRingArc(mat, botX, botStumpStart + 4.0f, pulseR - 2.0f, pulseR, 0.0f, Mth.TWO_PI,
                            nearBot ? C_SAFE_GREEN : C_NEON_CYAN, 24);
                }
                if (this.topClamped && this.botClamped) {
                    this.subStep = 2;
                }
            } else if (this.subStep == 2) {
                float reqDeg = MedicalConfig.SUTURE_WINDING_REQUIRED_DEGREES.get().floatValue();
                if (this.totalWindProgress < 1.0f) {
                    if (this.windingDrag) {
                        float ang = (float) Math.atan2(my - cy, mx - cx);
                        float delta = Mth.wrapDegrees((ang - this.lastWindAngle) * Mth.RAD_TO_DEG);
                        if (Math.abs(delta) < 70.0f) {
                            this.totalWindProgress = Mth.clamp(this.totalWindProgress + Math.abs(delta) / reqDeg, 0.0f, 1.0f);
                            int tickStep = (int) (this.totalWindProgress * 16.0f);
                            if (tickStep > this.lastWindTickStep) {
                                this.lastWindTickStep = tickStep;
                                playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.15f + this.totalWindProgress * 0.55f, 0.45f);
                                spawnParticle(mx, my, 0.0f, -10.0f, 1.4f, 0.22f, C_NEON_CYAN, false, true);
                            }
                        }
                        this.lastWindAngle = ang;
                    }

                    // 绘制 3D 环绕引导轨道与实时牵引缝合线
                    float orbitR = 28.0f;
                    drawRingArc(mat, cx, cy, orbitR - 1.5f, orbitR + 1.5f, 0.0f, Mth.TWO_PI, 0x4400E5FF, 32);
                    drawRingArc(mat, cx, cy, orbitR - 2.2f, orbitR + 2.2f, -Mth.HALF_PI,
                            Mth.TWO_PI * this.totalWindProgress, C_SAFE_GREEN, 32);

                    // 绘制已缠绕在上下断端颈部的外科缝合线圈
                    int coils = Math.max(1, Math.round(this.totalWindProgress * 7.0f));
                    for (int c = 0; c < coils; c++) {
                        float wyTop = topStumpEnd - 12.0f + c * 1.6f;
                        float wyBot = botStumpStart + 4.0f + c * 1.6f;
                        drawLineSegment(mat, cx - vesselRadius - 3.0f, wyTop, cx + vesselRadius + 3.0f, wyTop + 1.0f, 1.6f, C_NEON_CYAN);
                        drawLineSegment(mat, cx - vesselRadius - 3.0f, wyBot, cx + vesselRadius + 3.0f, wyBot + 1.0f, 1.6f, C_NEON_CYAN);
                    }

                    // 绘制从血管断端连向玩家持针钳的动态悬链缝线
                    if (this.windingDrag) {
                        drawCurvedSutureThread(mat, cx, topStumpEnd - 6.0f, (cx + mx) * 0.5f, (cy + my) * 0.5f + 10.0f, mx, my, C_NEON_CYAN);
                    }
                } else if (!this.playingCompletionFinale) {
                    // 死结张力保持博弈（血压脉搏会反向冲击张力）
                    float minT = MedicalConfig.SUTURE_KNOT_TENSION_MIN.get().floatValue();
                    float maxT = MedicalConfig.SUTURE_KNOT_TENSION_MAX.get().floatValue();
                    float snapT = MedicalConfig.SUTURE_KNOT_TENSION_SNAP.get().floatValue();
                    float holdSec = MedicalConfig.SUTURE_KNOT_HOLD_SECONDS.get().floatValue();

                    if (this.pullingKnot) {
                        this.knotTension += dt * (0.92f + beatWave * 0.25f);
                    } else {
                        this.knotTension = Math.max(0.0f, this.knotTension - dt * (0.58f + beatWave * 0.18f));
                    }

                    if (this.knotTension >= snapT) {
                        // 用力过猛绷断缝线！喷射蓝色断线碎屑并触发创口撕裂失误
                        playUiSound(SoundEvents.ITEM_BREAK, 1.35f, 0.80f);
                        for (int k = 0; k < 14; k++) {
                            float ang = (float) (Math.random() * Math.PI * 2.0);
                            spawnParticle(cx, cy, Mth.cos(ang) * 55.0f, Mth.sin(ang) * 55.0f, 1.8f, 0.35f, C_NEON_CYAN, false, true);
                        }
                        this.knotTension = 0.0f;
                        this.knotHoldProgress = 0.0f;
                        this.totalWindProgress = 0.35f;
                        this.pullingKnot = false;
                        triggerSurgicalMistake(cx, cy);
                    } else if (this.knotTension >= minT && this.knotTension <= maxT) {
                        this.knotHoldProgress += dt / Math.max(0.15f, holdSec);
                        int holdStep = (int) (this.knotHoldProgress * 8.0f);
                        if (holdStep > this.lastTensionTickStep) {
                            this.lastTensionTickStep = holdStep;
                            playUiSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9f + this.knotHoldProgress * 0.7f, 0.50f);
                        }
                        if (this.knotHoldProgress >= 1.0f) {
                            this.playingCompletionFinale = true;
                            this.pendingCompletionActionCode = 201;
                            this.completionAnimProgress = 0.0f;
                            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.45f, 0.65f);
                            spawnClampSparks(cx, cy, C_SAFE_GREEN);
                            return;
                        }
                    } else {
                        this.knotHoldProgress = Math.max(0.0f, this.knotHoldProgress - dt * 0.55f);
                    }

                    // 绘制环形死结张力仪表盘与紧绷的双股缝合线
                    drawCurvedSutureThread(mat, cx - vesselRadius, topStumpEnd - 6.0f, cx, cy, mx, my,
                            (this.knotTension > maxT) ? C_ALERT_RED : C_SAFE_GREEN);
                    drawCurvedSutureThread(mat, cx + vesselRadius, botStumpStart + 6.0f, cx, cy, mx, my,
                            (this.knotTension > maxT) ? C_ALERT_RED : C_SAFE_GREEN);

                    float arcR = 30.0f;
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR, -Mth.HALF_PI, Mth.TWO_PI, 0x660A1926, 32);
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR,
                            -Mth.HALF_PI + Mth.TWO_PI * minT, Mth.TWO_PI * (maxT - minT), 0x8800E676, 16);
                    drawRingArc(mat, cx, cy, arcR - 3.5f, arcR,
                            -Mth.HALF_PI + Mth.TWO_PI * maxT, Mth.TWO_PI * (1.0f - maxT), 0x88FF1744, 12);
                    int tCol = (this.knotTension >= minT && this.knotTension <= maxT)
                            ? C_SAFE_GREEN : (this.knotTension > maxT ? C_ALERT_RED : C_NEON_CYAN);
                    drawRingArc(mat, cx, cy, arcR - 4.2f, arcR + 0.8f,
                            -Mth.HALF_PI, Mth.TWO_PI * Mth.clamp(this.knotTension, 0.0f, 1.0f), tCol, 32);
                    // 内圈锁死确认进度金环
                    if (this.knotHoldProgress > 0.01f) {
                        drawRingArc(mat, cx, cy, arcR - 8.5f, arcR - 5.5f,
                                -Mth.HALF_PI, Mth.TWO_PI * Mth.clamp(this.knotHoldProgress, 0.0f, 1.0f), C_ADRENALINE_GOLD, 28);
                    }
                }
            }
        }

        private void renderPathBAnastomosisStage(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                                 float topStumpEnd, float botStumpStart, float vesselRadius,
                                                 float beatWave, float dt, float mx, float my, long nowMs) {
            float calmMax = MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX.get().floatValue();
            float alignTol = MedicalConfig.SUTURE_GRAFT_ALIGN_TOLERANCE_DEG.get().floatValue();
            float snapDist = MedicalConfig.SUTURE_GRAFT_SNAP_DISTANCE.get().floatValue();

            if (this.subStep == 1) {
                // B-1：舒张期同步脉冲波环（随心动周期收缩扩张，舒张期变绿可下夹）
                boolean calmWindow = (beatWave < calmMax);
                int ringCol = calmWindow ? C_SAFE_GREEN : C_ALERT_RED;
                float dynR = 9.0f + beatWave * 7.5f;
                if (!this.topClamped) {
                    drawRingArc(mat, cx, topStumpEnd - 5.0f, dynR - 2.0f, dynR, 0.0f, Mth.TWO_PI, ringCol, 24);
                    drawRingArc(mat, cx, topStumpEnd - 5.0f, 8.5f, 9.5f, 0.0f, Mth.TWO_PI, 0x66FFFFFF, 20);
                }
                if (!this.botClamped) {
                    drawRingArc(mat, cx, botStumpStart + 5.0f, dynR - 2.0f, dynR, 0.0f, Mth.TWO_PI, ringCol, 24);
                    drawRingArc(mat, cx, botStumpStart + 5.0f, 8.5f, 9.5f, 0.0f, Mth.TWO_PI, 0x66FFFFFF, 20);
                }
                if (this.topClamped && this.botClamped) {
                    this.subStep = 2;
                }
            } else if (this.subStep == 2) {
                // B-2：拖拽波纹状 ePTFE 人工血管桥接管并用滚轮旋转对齐轴线
                boolean posAligned = Math.hypot(this.graftX - cx, this.graftY - cy) <= snapDist;
                boolean angAligned = Math.abs(this.graftAngleDeg) <= alignTol;
                boolean fullyAligned = posAligned && angAligned;

                // 靶心与轴向激光对齐辅助线
                drawLineSegment(mat, cx, topStumpEnd - 4.0f, cx, botStumpStart + 4.0f, 1.0f,
                        fullyAligned ? C_SAFE_GREEN : 0x5500E5FF);
                drawRingArc(mat, cx, cy, snapDist - 1.5f, snapDist, 0.0f, Mth.TWO_PI,
                        posAligned ? C_SAFE_GREEN : 0x6600E5FF, 24);

                if (this.draggingGraft) {
                    this.graftX = mx;
                    this.graftY = my;
                }

                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(this.graftX, this.graftY, 0.0f);
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(this.graftAngleDeg));
                renderCorrugatedPtfeGraft(g, -21.0f, 21.0f, fullyAligned ? C_SAFE_GREEN : (angAligned ? C_NEON_CYAN : C_WARNING_YELLOW), 0.0f);
                pose.popPose();
            } else if (this.subStep == 3) {
                // B-3：已嵌入 PTFE 人工血管 -> 环周等距微创缝合 + 血流重灌注收尾
                this.graftSnapAnim = Math.min(1.0f, this.graftSnapAnim + dt * 6.0f);
                float reperf = this.playingCompletionFinale ? this.completionAnimProgress : 0.0f;
                PoseStack pose = g.pose();
                pose.pushPose();
                pose.translate(cx, cy, 0.0f);
                renderCorrugatedPtfeGraft(g, topStumpEnd - cy - 2.0f, botStumpStart - cy + 2.0f, C_SAFE_GREEN, reperf);
                pose.popPose();

                int stitchCount = this.stitchesDone.length;
                boolean allDone = true;
                for (int i = 0; i < stitchCount; i++) {
                    float sx = getStitchX(cx, i, stitchCount);
                    float syTop = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount);
                    float syBot = syTop + 10.0f;

                    if (this.stitchesDone[i]) {
                        this.stitchTightenAnim[i] = Math.min(1.0f, this.stitchTightenAnim[i] + dt * 7.0f);
                        float tight = this.stitchTightenAnim[i];
                        drawLineSegment(mat, sx, syTop, sx, syBot, 1.6f, C_NEON_CYAN);
                        // 缝合外科线结小十字高光
                        float midY = (syTop + syBot) * 0.5f;
                        drawLineSegment(mat, sx - 2.2f * tight, midY - 1.2f, sx + 2.2f * tight, midY + 1.2f, 1.2f, C_SAFE_GREEN);
                    } else {
                        allDone = false;
                        boolean active = (this.activeStitchIdx == i);
                        int entryCol = active ? C_SAFE_GREEN : C_WARNING_YELLOW;
                        drawRingArc(mat, sx, syTop, 1.2f, 3.2f, 0.0f, Mth.TWO_PI, entryCol, 12);
                        drawRingArc(mat, sx, syBot, 1.2f, 3.2f, 0.0f, Mth.TWO_PI, C_NEON_CYAN, 12);
                        if (active) {
                            // 渲染从穿刺入针点经过弯曲缝合针到鼠标的实时贝塞尔缝合线
                            drawCurvedSutureThread(mat, sx, syTop, (sx + mx) * 0.5f + 6.0f, (syTop + my) * 0.5f, mx, my, C_NEON_CYAN);
                        }
                    }
                }

                if (allDone && !this.playingCompletionFinale) {
                    this.playingCompletionFinale = true;
                    this.pendingCompletionActionCode = 202;
                    this.completionAnimProgress = 0.0f;
                    playUiSound(SoundEvents.PLAYER_LEVELUP, 1.55f, 0.70f);
                    spawnClampSparks(cx, cy, C_SAFE_GREEN);
                }
            }
        }

        private void renderCorrugatedPtfeGraft(GuiGraphics g, float yTop, float yBot, int cuffColor, float reperfusionWave) {
            int top = Math.round(yTop);
            int bot = Math.round(yBot);
            // 管体阴影与外层 ePTFE 医疗白材质
            g.fill(-10, top, 10, bot, 0xFFCFD8DC);
            g.fill(-8, top, 8, bot, 0xFFECEFF1);
            g.fill(-4, top, 2, bot, 0xFFFFFFFF);

            // 若处于手术完成重灌注阶段，鲜红动脉血从上至下充盈半透明人工血管管腔
            if (reperfusionWave > 0.01f) {
                int flowBottom = Math.round(Mth.lerp(Mth.clamp(reperfusionWave, 0.0f, 1.0f), top, bot));
                g.fill(-7, top, 7, flowBottom, 0xAAFF1744);
                g.fill(-3, top, 3, flowBottom, 0xCCFF5252);
            }

            // 螺旋波纹抗折环 (Crimped Rings)
            for (int ry = top + 3; ry <= bot - 3; ry += 4) {
                g.fill(-10, ry, 10, ry + 1, 0xFF90A4AE);
                g.fill(-8, ry + 1, 8, ry + 2, 0xFFB0BEC5);
            }
            // 上下吻合口密封法兰环
            g.fill(-11, top, 11, top + 2, cuffColor);
            g.fill(-11, bot - 2, 11, bot, cuffColor);
        }

        private void renderClampedInstrumentOnVessel(GuiGraphics g, Matrix4f mat, float x, float y,
                                                     boolean isTop, boolean isBulldogGold, float anim) {
            float slide = (1.0f - Mth.clamp(anim, 0.0f, 1.0f)) * 22.0f;
            float dir = isTop ? -1.0f : 1.0f;
            float cx = x + dir * slide;
            if (isBulldogGold) {
                // 金色哈巴狗动脉弹簧微血管夹 (Bulldog Micro-Clamp)
                int ix = Math.round(cx);
                int iy = Math.round(y);
                g.fill(ix - 16, iy - 3, ix + 14, iy + 3, 0xFFFFB300);
                g.fill(ix - 14, iy - 2, ix + 12, iy + 1, 0xFFFFEA00);
                // 尾部弹簧圈
                drawRingArc(mat, cx + 15.0f, y, 2.5f, 5.0f, 0.0f, Mth.TWO_PI, C_ADRENALINE_GOLD, 14);
            } else {
                // 蚊式不锈钢止血钳 (Mosquito Hemostat)
                drawLineSegment(mat, cx - 15.0f, y - 1.5f, cx + 38.0f, y + dir * 18.0f, 2.6f, 0xFFCFD8DC);
                drawLineSegment(mat, cx - 15.0f, y + 1.5f, cx + 38.0f, y + dir * 12.0f, 2.2f, 0xFF90A4AE);
                g.fill(Math.round(cx - 15), Math.round(y - 3), Math.round(cx + 12), Math.round(y + 3), 0xFFECEFF1);
            }
        }

        private void renderRetractedMuscleFlaps(GuiGraphics g, Matrix4f mat, float flapLeftEdge, float flapRightEdge, float cy, float openRatio) {
            int lEdge = Math.round(flapLeftEdge);
            int rEdge = Math.round(flapRightEdge);
            int topY = viewY + 25;
            int botY = viewY + viewH - 2;

            if (lEdge > viewX + 2) {
                g.fill(viewX + 2, topY, lEdge, botY, 0xF4340B13);
                g.fill(Math.max(viewX + 2, lEdge - 6), topY, lEdge, botY, 0xFF5D1322);
                g.fill(Math.max(viewX + 2, lEdge - 2), topY, lEdge, botY, 0xFFAD1E37);
            }
            if (rEdge < viewX + viewW - 2) {
                g.fill(rEdge, topY, viewX + viewW - 2, botY, 0xF4340B13);
                g.fill(rEdge, topY, Math.min(viewX + viewW - 2, rEdge + 6), botY, 0xFF5D1322);
                g.fill(rEdge, topY, Math.min(viewX + viewW - 2, rEdge + 2), botY, 0xFFAD1E37);
            }

            // 切口上下两端与肌肉边缘的弹性牵引胶原纤维丝
            if (openRatio > 0.05f && openRatio < 0.95f) {
                for (int i = -2; i <= 2; i++) {
                    float fy = cy + i * 16.0f;
                    drawLineSegment(mat, flapLeftEdge, fy, flapLeftEdge + 8.0f, fy + i * 2.5f, 0.9f, 0x66F48FB1);
                    drawLineSegment(mat, flapRightEdge, fy, flapRightEdge - 8.0f, fy + i * 2.5f, 0.9f, 0x66F48FB1);
                }
            }
        }

        private void renderRetractorAndPathCards(GuiGraphics g, Matrix4f mat, float cx, float cy,
                                                 float flapLeftEdge, float flapRightEdge, float openRatio,
                                                 float dt, float mx, float my, long nowMs) {
            // 顶部不锈钢棘轮齿条锁轨
            int rackY = viewY + 28;
            g.fill(viewX + 14, rackY, viewX + viewW - 14, rackY + 4, 0xFF78909C);
            for (int tx = viewX + 18; tx < viewX + viewW - 18; tx += 6) {
                g.fill(tx, rackY - 2, tx + 2, rackY, 0xFFCFD8DC);
            }

            // 左右目标棘轮卡槽
            int hookY = Math.round(cy);
            drawRingArc(mat, viewX + 18.0f, hookY, 8.0f, 11.0f, 0.0f, Mth.TWO_PI,
                    this.leftLocked ? C_SAFE_GREEN : 0x6600E5FF, 20);
            drawRingArc(mat, viewX + viewW - 18.0f, hookY, 8.0f, 11.0f, 0.0f, Mth.TWO_PI,
                    this.rightLocked ? C_SAFE_GREEN : 0x6600E5FF, 20);

            // 左右四爪外科牵开器 (Weitlaner Retractor Claws)
            int leftHookX = Math.round(flapLeftEdge - 5.0f);
            int rightHookX = Math.round(flapRightEdge + 5.0f);
            renderRetractorClaw(g, mat, leftHookX, hookY, true, this.leftLocked, this.draggingRetractor == 1);
            renderRetractorClaw(g, mat, rightHookX, hookY, false, this.rightLocked, this.draggingRetractor == 2);

            if (this.leftLocked && this.rightLocked) {
                this.incisionOpened = true;
                this.pathCardSlideAnim = Math.min(1.0f, this.pathCardSlideAnim + dt * 5.0f);
                float slideOffset = (1.0f - this.pathCardSlideAnim) * 28.0f;

                int cardW = 82, cardH = 38;
                int cardAY = Math.round(viewY + viewH - cardH - 8 + slideOffset);
                int cardAX = Math.round(cx - cardW - 10);
                int cardBX = Math.round(cx + 10);

                boolean hovA = mx >= cardAX && mx <= cardAX + cardW && my >= cardAY && my <= cardAY + cardH;
                boolean hovB = mx >= cardBX && mx <= cardBX + cardW && my >= cardAY && my <= cardAY + cardH;

                // 推荐路径高亮（500~1000 推荐路径A，>1000 动脉出血推荐路径B）
                int colA = !this.isMainArtery ? C_SAFE_GREEN : (hovA ? C_WARNING_YELLOW : 0xFF607D8B);
                int colB = this.isMainArtery ? C_SAFE_GREEN : (hovB ? C_NEON_CYAN : 0xFF607D8B);

                // 路径 A 卡片（血管结扎图腾）
                g.fill(cardAX, cardAY, cardAX + cardW, cardAY + cardH, hovA ? 0xE6102838 : 0xCC061018);
                drawTacticalCornerBrackets(g, cardAX, cardAY, cardW, cardH, colA);
                int axMid = cardAX + cardW / 2;
                int ayMid = cardAY + cardH / 2;
                g.fill(axMid - 5, cardAY + 6, axMid + 5, cardAY + cardH - 6, 0xFFAD1457);
                drawRingArc(mat, axMid, ayMid, 6.0f, 8.5f, 0.0f, Mth.TWO_PI, C_NEON_CYAN, 18);
                g.fill(axMid - 14, ayMid - 2, axMid + 14, ayMid + 2, C_NEON_CYAN);

                // 路径 B 卡片（双金夹 + PTFE 人工血管桥接图腾）
                g.fill(cardBX, cardAY, cardBX + cardW, cardAY + cardH, hovB ? 0xE6102838 : 0xCC061018);
                drawTacticalCornerBrackets(g, cardBX, cardAY, cardW, cardH, colB);
                int bxMid = cardBX + cardW / 2;
                g.fill(bxMid - 6, cardAY + 4, bxMid + 6, cardAY + cardH - 4, 0xFFFF1744);
                g.fill(bxMid - 7, cardAY + 10, bxMid + 7, cardAY + cardH - 10, 0xFFECEFF1);
                g.fill(bxMid - 12, cardAY + 6, bxMid + 12, cardAY + 8, C_ADRENALINE_GOLD);
                g.fill(bxMid - 12, cardAY + cardH - 8, bxMid + 12, cardAY + cardH - 6, C_ADRENALINE_GOLD);
            }
        }

        private void renderRetractorClaw(GuiGraphics g, Matrix4f mat, int hx, int hy, boolean isLeft, boolean locked, boolean dragging) {
            int metalCol = locked ? C_SAFE_GREEN : (dragging ? C_NEON_CYAN : 0xFFECEFF1);
            // 连接顶轨的竖直钢臂
            drawLineSegment(mat, hx, viewY + 30.0f, hx, hy, 2.4f, 0xFF90A4AE);
            // 四爪弯钩
            for (int c = -1; c <= 1; c++) {
                int cy = hy + c * 6;
                g.fill(hx - 6, cy - 1, hx + 6, cy + 1, metalCol);
                int tipX = isLeft ? (hx + 6) : (hx - 6);
                g.fill(tipX - 1, cy - 2, tipX + 1, cy + 2, 0xFFFFFFFF);
            }
            // 牵引指环手柄
            drawRingArc(mat, hx + (isLeft ? -7.0f : 7.0f), hy, 4.5f, 7.0f, 0.0f, Mth.TWO_PI, metalCol, 16);
        }

        private void renderActiveSurgicalInstrumentCursor(GuiGraphics g, float mx, float my,
                                                          float cx, float cy, float beatWave, long nowMs) {
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(mx, my, 0.0f);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(this.cursorTiltDeg));
            Matrix4f mat = pose.last().pose();

            if (this.chosenPath == 1 && this.subStep == 1) {
                // 蚊式止血钳跟随准星（靠近血管断端时钳口自动张开）
                float jawAngle = 10.0f;
                drawLineSegment(mat, 0.0f, 0.0f, 22.0f, 18.0f + jawAngle * 0.4f, 2.2f, 0xFFECEFF1);
                drawLineSegment(mat, 0.0f, 0.0f, 18.0f, 22.0f - jawAngle * 0.4f, 2.2f, 0xFFB0BEC5);
                drawRingArc(mat, 24.0f, 20.0f, 3.0f, 4.8f, 0.0f, Mth.TWO_PI, C_NEON_CYAN, 12);
            } else if (this.chosenPath == 2 && this.subStep == 1) {
                // 金色哈巴狗微血管夹施夹钳跟随准星
                boolean calm = beatWave < MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX.get().floatValue();
                drawLineSegment(mat, -6.0f, -2.0f, 8.0f, -2.0f, 2.4f, C_ADRENALINE_GOLD);
                drawLineSegment(mat, -6.0f, 2.0f, 8.0f, 2.0f, 2.4f, C_ADRENALINE_GOLD);
                drawLineSegment(mat, 8.0f, 0.0f, 24.0f, 16.0f, 2.0f, calm ? C_SAFE_GREEN : 0xFFCFD8DC);
            } else if ((this.chosenPath == 1 && this.subStep == 2) || (this.chosenPath == 2 && this.subStep == 3)) {
                // 显微持针钳 + 弯月形外科缝合针 (Curved Surgical Needle & Castroviejo Holder)
                drawLineSegment(mat, 2.0f, 2.0f, 22.0f, 20.0f, 2.4f, 0xFFCFD8DC);
                drawLineSegment(mat, 4.0f, 0.0f, 24.0f, 18.0f, 1.8f, 0xFF00E5FF);
                drawRingArc(mat, -4.0f, -2.0f, 5.5f, 7.2f, -0.6f, 2.4f, 0xFFFFFFFF, 14);
            }

            pose.popPose();
            drawTacticalCursor(g, mx, my, 0.0f, C_NEON_CYAN);
        }

        private void drawTaperedVesselSegment(Matrix4f mat, float x0, float y0, float x1, float y1,
                                              float r0, float r1, int outerCol, int innerCol, int specCol) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            // 外膜管壁
            drawQuad(mat, x0 - r0, y0, x0 + r0, y0, x1 + r1, y1, x1 - r1, y1, outerCol);
            // 内膜管腔
            drawQuad(mat, x0 - r0 * 0.62f, y0, x0 + r0 * 0.62f, y0, x1 + r1 * 0.62f, y1, x1 - r1 * 0.62f, y1, innerCol);
            // 湿润血管壁镜面高光带
            drawQuad(mat, x0 - r0 * 0.35f, y0, x0 - r0 * 0.12f, y0, x1 - r1 * 0.12f, y1, x1 - r1 * 0.35f, y1, specCol);
        }

        private void drawQuad(Matrix4f mat, float x0, float y0, float x1, float y1,
                              float x2, float y2, float x3, float y3, int color) {
            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int gr = (color >>> 8) & 0xFF;
            int b = color & 0xFF;
            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            buf.addVertex(mat, x0, y0, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x3, y3, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x2, y2, 0.0f).setColor(r, gr, b, a);
            buf.addVertex(mat, x1, y1, 0.0f).setColor(r, gr, b, a);
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void drawLineSegment(Matrix4f mat, float x0, float y0, float x1, float y1, float halfThickness, int color) {
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = Mth.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) return;
            float nx = -dy / len * halfThickness;
            float ny = dx / len * halfThickness;
            drawQuad(mat, x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, color);
        }

        private void drawCurvedSutureThread(Matrix4f mat, float x0, float y0, float ctrlX, float ctrlY, float x1, float y1, int color) {
            int steps = 14;
            float prevX = x0;
            float prevY = y0;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float u = 1.0f - t;
                float curX = u * u * x0 + 2.0f * u * t * ctrlX + t * t * x1;
                float curY = u * u * y0 + 2.0f * u * t * ctrlY + t * t * y1;
                drawLineSegment(mat, prevX, prevY, curX, curY, 0.95f, color);
                prevX = curX;
                prevY = curY;
            }
        }

        private void drawRingArc(Matrix4f mat, float cx, float cy, float rInner, float rOuter,
                                 float startAngle, float sweepAngle, int color, int segments) {
            if (Math.abs(sweepAngle) < 0.002f) return;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            int a = (color >>> 24) & 0xFF;
            int r = (color >>> 16) & 0xFF;
            int gr = (color >>> 8) & 0xFF;
            int b = color & 0xFF;

            Tesselator tess = Tesselator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                float ang = startAngle + sweepAngle * t;
                float cos = Mth.cos(ang);
                float sin = Mth.sin(ang);
                buf.addVertex(mat, cx + cos * rInner, cy + sin * rInner, 0.0f).setColor(r, gr, b, a);
                buf.addVertex(mat, cx + cos * rOuter, cy + sin * rOuter, 0.0f).setColor(r, gr, b, a);
            }
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        private void drawViewportVignette(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x, y, x + w, y + 8, color);
            g.fill(x, y + h - 8, x + w, y + h, color);
            g.fill(x, y + 8, x + 8, y + h - 8, color);
            g.fill(x + w - 8, y + 8, x + w, y + h - 8, color);
        }

        private float getStitchX(float cx, int idx, int totalStitches) {
            int perRow = Math.max(1, totalStitches / 2);
            int col = idx % perRow;
            float span = 14.0f;
            float offset = (perRow == 1) ? 0.0f : ((col / (float) (perRow - 1)) - 0.5f) * span;
            return cx + offset;
        }

        private float getStitchTopY(float topStumpEnd, float botStumpStart, int idx, int totalStitches) {
            int perRow = Math.max(1, totalStitches / 2);
            return (idx < perRow) ? (topStumpEnd - 5.0f) : (botStumpStart - 5.0f);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.playingCompletionFinale) return true;
            if (button == 1) {
                this.onClose();
                return true;
            }
            if (button == 0) {
                float mx = (float) mouseX + this.cursorTremorX;
                float my = (float) mouseY + this.cursorTremorY;

                int cancelW = 54, cancelH = 14;
                int cancelX = viewX + viewW - cancelW;
                int cancelY = winY + winH - 19;
                if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH) {
                    this.onClose();
                    return true;
                }

                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.54f;
                float topStumpEnd = cy - 19.0f;
                float botStumpStart = cy + 19.0f;

                if (this.chosenPath == 0) {
                    float openRatio = (this.leftRetract + this.rightRetract) * 0.5f;
                    float gapHalfW = 5.0f + openRatio * 74.0f;
                    float leftHookX = cx - gapHalfW - 5.0f;
                    float rightHookX = cx + gapHalfW + 5.0f;

                    if (!this.leftLocked && Math.abs(mx - leftHookX) <= 16.0f && Math.abs(my - cy) <= 22.0f) {
                        this.draggingRetractor = 1;
                        playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.6f);
                        return true;
                    }
                    if (!this.rightLocked && Math.abs(mx - rightHookX) <= 16.0f && Math.abs(my - cy) <= 22.0f) {
                        this.draggingRetractor = 2;
                        playUiSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.6f);
                        return true;
                    }

                    if (this.leftLocked && this.rightLocked) {
                        int cardW = 82, cardH = 38;
                        int cardAY = viewY + viewH - cardH - 8;
                        int cardAX = Math.round(cx - cardW - 10);
                        int cardBX = Math.round(cx + 10);

                        if (mx >= cardAX && mx <= cardAX + cardW && my >= cardAY && my <= cardAY + cardH) {
                            this.chosenPath = 1;
                            this.subStep = 1;
                            MedicalSoundHelper.playLimbSelectSound();
                            return true;
                        }
                        if (mx >= cardBX && mx <= cardBX + cardW && my >= cardAY && my <= cardAY + cardH) {
                            this.chosenPath = 2;
                            this.subStep = 1;
                            MedicalSoundHelper.playLimbSelectSound();
                            return true;
                        }
                    }
                } else if (this.chosenPath == 1) {
                    if (this.subStep == 1) {
                        long nowMs = System.currentTimeMillis();
                        float driftAmp = MedicalConfig.SUTURE_STUMP_DRIFT_AMPLITUDE.get().floatValue();
                        float clampRadius = MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS.get().floatValue();
                        float driftX = (Mth.sin(nowMs * 0.0042f) * 0.7f + Mth.cos(nowMs * 0.0071f) * 0.3f) * driftAmp;
                        if (!this.topClamped && Math.hypot(mx - (cx + driftX), my - (topStumpEnd - 4.0f)) <= clampRadius) {
                            this.topClamped = true;
                            this.topClampAnim = 0.0f;
                            playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.55f, 0.75f);
                            spawnClampSparks(cx + driftX, topStumpEnd - 4.0f, C_NEON_CYAN);
                            return true;
                        }
                        if (!this.botClamped && Math.hypot(mx - (cx - driftX), my - (botStumpStart + 4.0f)) <= clampRadius) {
                            this.botClamped = true;
                            this.botClampAnim = 0.0f;
                            playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.55f, 0.75f);
                            spawnClampSparks(cx - driftX, botStumpStart + 4.0f, C_NEON_CYAN);
                            return true;
                        }
                        triggerSurgicalMistake(mx, my);
                    } else if (this.subStep == 2) {
                        if (this.totalWindProgress < 1.0f) {
                            this.windingDrag = true;
                            this.lastWindAngle = (float) Math.atan2(my - cy, mx - cx);
                        } else {
                            this.pullingKnot = true;
                            playUiSound(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.25f, 0.50f);
                        }
                        return true;
                    }
                } else if (this.chosenPath == 2) {
                    if (this.subStep == 1) {
                        float beatWave = computeArterialPulseWave(this.heartPhase);
                        float calmMax = MedicalConfig.SUTURE_BULLDOG_CALM_BEAT_MAX.get().floatValue();
                        float clampRadius = MedicalConfig.SUTURE_STUMP_CLAMP_RADIUS.get().floatValue();
                        if (beatWave < calmMax) {
                            if (!this.topClamped && Math.hypot(mx - cx, my - (topStumpEnd - 5.0f)) <= clampRadius + 1.5f) {
                                this.topClamped = true;
                                this.topClampAnim = 0.0f;
                                playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.65f, 0.75f);
                                spawnClampSparks(cx, topStumpEnd - 5.0f, C_ADRENALINE_GOLD);
                                return true;
                            }
                            if (!this.botClamped && Math.hypot(mx - cx, my - (botStumpStart + 5.0f)) <= clampRadius + 1.5f) {
                                this.botClamped = true;
                                this.botClampAnim = 0.0f;
                                playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.65f, 0.75f);
                                spawnClampSparks(cx, botStumpStart + 5.0f, C_ADRENALINE_GOLD);
                                return true;
                            }
                        }
                        triggerSurgicalMistake(mx, my);
                    } else if (this.subStep == 2) {
                        if (Math.hypot(mx - this.graftX, my - this.graftY) <= 26.0) {
                            this.draggingGraft = true;
                            MedicalSoundHelper.playHoverSound();
                            return true;
                        }
                    } else if (this.subStep == 3) {
                        int stitchCount = this.stitchesDone.length;
                        for (int i = 0; i < stitchCount; i++) {
                            if (this.stitchesDone[i]) continue;
                            float sx = getStitchX(cx, i, stitchCount);
                            float syTop = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount);
                            if (Math.hypot(mx - sx, my - syTop) <= 8.5) {
                                this.activeStitchIdx = i;
                                playUiSound(SoundEvents.SHEEP_SHEAR, 1.65f, 0.45f);
                                spawnParticle(sx, syTop, 0.0f, -8.0f, 1.5f, 0.25f, 0xFFFF1744, false, false);
                                return true;
                            }
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (float) mouseX + this.cursorTremorX;
                float cx = viewX + viewW * 0.5f;
                if (this.chosenPath == 0) {
                    if (this.draggingRetractor == 1 && !this.leftLocked) {
                        this.leftRetract = Mth.clamp((cx - mx) / 76.0f, 0.0f, 1.0f);
                        int tooth = (int) (this.leftRetract * 10.0f);
                        if (tooth != this.lastLeftTooth) {
                            this.lastLeftTooth = tooth;
                            playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.85f + this.leftRetract * 0.55f, 0.45f);
                        }
                        return true;
                    }
                    if (this.draggingRetractor == 2 && !this.rightLocked) {
                        this.rightRetract = Mth.clamp((mx - cx) / 76.0f, 0.0f, 1.0f);
                        int tooth = (int) (this.rightRetract * 10.0f);
                        if (tooth != this.lastRightTooth) {
                            this.lastRightTooth = tooth;
                            playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.85f + this.rightRetract * 0.55f, 0.45f);
                        }
                        return true;
                    }
                }
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && !this.playingCompletionFinale) {
                float mx = (float) mouseX + this.cursorTremorX;
                float my = (float) mouseY + this.cursorTremorY;
                float cx = viewX + viewW * 0.5f;
                float cy = viewY + viewH * 0.54f;
                float topStumpEnd = cy - 19.0f;
                float botStumpStart = cy + 19.0f;

                this.draggingRetractor = 0;
                this.windingDrag = false;
                this.pullingKnot = false;

                if (this.chosenPath == 2 && this.subStep == 2 && this.draggingGraft) {
                    this.draggingGraft = false;
                    float alignTol = MedicalConfig.SUTURE_GRAFT_ALIGN_TOLERANCE_DEG.get().floatValue();
                    float snapDist = MedicalConfig.SUTURE_GRAFT_SNAP_DISTANCE.get().floatValue();
                    if (Math.abs(this.graftAngleDeg) <= alignTol && Math.hypot(this.graftX - cx, this.graftY - cy) <= snapDist) {
                        this.graftInserted = true;
                        this.subStep = 3;
                        playUiSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.35f, 0.70f);
                        spawnClampSparks(cx, cy, C_SAFE_GREEN);
                    }
                    return true;
                }

                if (this.chosenPath == 2 && this.subStep == 3 && this.activeStitchIdx >= 0) {
                    int i = this.activeStitchIdx;
                    this.activeStitchIdx = -1;
                    int stitchCount = this.stitchesDone.length;
                    float sx = getStitchX(cx, i, stitchCount);
                    float syBot = getStitchTopY(topStumpEnd, botStumpStart, i, stitchCount) + 10.0f;
                    if (Math.hypot(mx - sx, my - syBot) <= 9.5) {
                        this.stitchesDone[i] = true;
                        this.stitchTightenAnim[i] = 0.0f;
                        playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.45f, 0.65f);
                        spawnClampSparks(sx, syBot, C_NEON_CYAN);
                    } else {
                        triggerSurgicalMistake(mx, my);
                    }
                    return true;
                }
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.chosenPath == 2 && this.subStep == 2 && scrollY != 0) {
                float step = Screen.hasShiftDown() ? 2.0f : 4.5f;
                this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg + (scrollY > 0 ? -step : step));
                playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (this.chosenPath == 2 && this.subStep == 2) {
                if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
                    this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg - 4.0f);
                    playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
                    this.graftAngleDeg = Mth.wrapDegrees(this.graftAngleDeg + 4.0f);
                    playUiSound(SoundEvents.TRIPWIRE_CLICK_ON, 1.35f, 0.28f);
                    return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}