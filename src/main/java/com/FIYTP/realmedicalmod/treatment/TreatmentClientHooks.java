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
            case TOURNIQUET -> C_WARNING_YELLOW;
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
                    if (isOpioid) {
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

        g.drawCenteredString(font, slot.cnName(), (x0 + x1) / 2, y0 + 5, C_TEXT_WHITE);

        String subStatus;
        int subColor;
        if (bleed > 0) {
            subStatus = "流血";
            subColor = C_ALERT_RED;
        } else if (openWound && !bandaged) {
            subStatus = "创口";
            subColor = C_WARNING_YELLOW;
        } else if (fracture && !limbData.hasSplint()) {
            subStatus = "骨折";
            subColor = C_WARNING_YELLOW;
        } else if (pain > 0) {
            subStatus = "疼痛";
            subColor = pain >= 7 ? C_ALERT_RED : C_WARNING_YELLOW;
        } else if (bandaged) {
            subStatus = "已包扎";
            subColor = C_SAFE_GREEN;
        } else {
            subStatus = "";
            subColor = C_TEXT_DIM;
        }
        if (!subStatus.isEmpty()) {
            g.drawCenteredString(font, subStatus, (x0 + x1) / 2, y0 + 17, subColor);
        }
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
}