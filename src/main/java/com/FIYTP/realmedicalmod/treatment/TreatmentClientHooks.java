package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.RealMedicalMod;
import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.capability.MedicalDataCapability;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
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
import net.minecraft.client.renderer.GameRenderer;
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
 * 3A 级医疗用品左键长按战术人体部位治疗面板 (Left-Click Hold Tactical Body Treatment HUD)
 *
 * 交互流程：
 * 1. 手持任意受 {@link TreatmentRouter} 支持的医疗用品（如绷带、加压绷带及后续新增医疗器械）。
 * 2. 按住鼠标左键（Attack 键）：
 *    - 立即拦截原版左键挥击与挖掘方块行为；
 *    - 通过 {@code MouseHandlerMixin} 锁定玩家摄像机视角（移动鼠标不再转动视角）；
 *    - 呼出全息战术人体部位面板，实时渲染 3D 旋转医疗用品立体模型（{@link MedicalAssetLibrary}），
 *      并将鼠标的移动增量映射为面板上的全息准星游标。
 * 3. 将准星游标移至需要治疗的对应肢体（HEAD / CHEST / LEFT_ARM / RIGHT_ARM / LEFT_LEG / RIGHT_LEG）：
 *    - 若该肢体符合当前医疗用品的适用指征，自动开始环状读条对该肢体使用医疗用品；
 *    - 读条完成（或进度超过 18% 后松开左键）直接触发服务端治疗包 {@link TreatmentApplyPacket}，无复杂小游戏。
 * 4. 任何时候松开鼠标左键：
 *    - 立即关闭面板并恢复正常视角转动控制。
 */
@EventBusSubscriber(modid = RealMedicalMod.MODID, value = Dist.CLIENT)
public class TreatmentClientHooks {

    // ==================== 自包含 3A 医疗面板配色常量 ====================
    private static final int C_BG_DARK        = 0xE6060D16;
    private static final int C_NEON_CYAN      = 0xFF00E5FF;
    private static final int C_SAFE_GREEN     = 0xFF00E676;
    private static final int C_WARNING_YELLOW = 0xFFFFB300;
    private static final int C_ALERT_RED      = 0xFFFF2A4B;
    private static final int C_TEXT_WHITE     = 0xFFF0F8FF;
    private static final int C_TEXT_DIM       = 0xFF8AA4B8;

    private static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    // ==================== 面板交互与相机锁定状态 ====================
    private static boolean panelActive = false;
    private static InteractionHand activeHand = InteractionHand.MAIN_HAND;

    /** 面板内的虚拟全息游标坐标（GUI 缩放坐标系） */
    private static float virtualCursorX = 0.0f;
    private static float virtualCursorY = 0.0f;

    /** 当前鼠标悬停的目标肢体 */
    private static MedicalData.Limb hoveredLimb = null;
    private static MedicalData.Limb lastHoveredLimb = null;

    /** 悬停在有效受伤肢体上的治疗进度 (0.0 ~ 1.0) */
    private static float channelProgress = 0.0f;
    private static long lastRenderTimeMs = 0L;

    /** 成功对某个肢体完成一次治疗后的短暂冷却（防止瞬间连消多个物品） */
    private static int postApplyCooldownTicks = 0;

    // ==================== 六大肢体在人体战术面板上的相对布局 ====================
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

    /**
     * 兼容旧版接口：如果外部调用 openBandageScreen，直接激活左键战术肢体面板
     */
    public static void openBandageScreen(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            activatePanel(mc, hand != null ? hand : InteractionHand.MAIN_HAND);
        }
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
            case TOURNIQUET -> C_WARNING_YELLOW;
            case SYRINGE -> 0xFF40C4FF;
            case SPLINT -> 0xFF69F0AE;
            case MEDICINE_BOTTLE -> 0xFFFFD54F;
        };
    }

    /**
     * 供 {@code MouseHandlerMixin} 查询：当前是否正在按住左键操作人体医疗面板（需要锁定相机视角）。
     */
    public static boolean isLockingCameraView() {
        return panelActive;
    }

    /**
     * 供 {@code MouseHandlerMixin} 注入调用：当视角被锁定时，将原始鼠标移动增量转化为面板游标位移。
     */
    public static void onMouseDelta(double rawDX, double rawDY) {
        if (!panelActive) return;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // 适配 GUI 缩放比例的灵敏度
        float sensitivity = 0.42f;
        virtualCursorX = Mth.clamp(virtualCursorX + (float) rawDX * sensitivity, sw * 0.5f - 145.0f, sw * 0.5f + 145.0f);
        virtualCursorY = Mth.clamp(virtualCursorY + (float) rawDY * sensitivity, sh * 0.5f - 125.0f, sh * 0.5f + 125.0f);
    }

    /**
     * 拦截手持医疗用品时的左键攻击/挖掘触发，防止挥拳打人或敲碎方块。
     */
    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

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
            // 抑制原版破坏方块动作（使用公开的 MultiPlayerGameMode#stopDestroyBlock 接口）
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
        } else {
            if (panelActive) {
                // 如果玩家已将鼠标移到某个可治疗的受伤肢体上并松开左键，支持快捷瞬发完成！
                if (hoveredLimb != null && channelProgress >= 0.18f && postApplyCooldownTicks <= 0) {
                    ItemStack stack = player.getItemInHand(activeHand);
                    MedicalData data = player.getData(MedicalDataCapability.MEDICAL_DATA);
                    if (TreatmentRouter.canTreatLimb(stack, data, hoveredLimb)) {
                        triggerApplyPacket(activeHand, hoveredLimb);
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

    private static void triggerApplyPacket(InteractionHand hand, MedicalData.Limb limb) {
        NetworkHandler.sendTreatmentToServer(new TreatmentApplyPacket(hand.ordinal(), limb.ordinal()));
        MedicalSoundHelper.playLimbSelectSound();
        channelProgress = 0.0f;
        postApplyCooldownTicks = 8; // 0.4秒防连发冷却
    }

    // ==================== 3A 战术人体全息治疗面板渲染 ====================
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!panelActive) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) return;

        // 统一在 hotbar 层之后渲染一次，避免在每个 GUI Layer 重复绘制
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

        // 1. 绘制战术背景暗角与全息扫描框
        int panelW = 300;
        int panelH = 244;
        int px = (int) (cx - panelW * 0.5f);
        int py = (int) (cy - panelH * 0.5f);

        g.fill(px, py, px + panelW, py + panelH, C_BG_DARK);
        drawTacticalCornerBrackets(g, px, py, panelW, panelH, themeColor);

        // 顶部标题栏：当前医疗用品名称与操作提示
        String title = "TACTICAL LIMB TREATMENT // 战术肢体医疗终端";
        g.drawString(font, title, px + 10, py + 8, themeColor, false);
        g.fill(px + 8, py + 19, px + panelW - 8, py + 20, withAlpha(themeColor, 0.45f));

        // 左侧实时渲染 3D 旋转医疗用品模型 (MedicalAssetLibrary) 与物品效力信息
        MedicalAssetLibrary.render3DMedicalItem(g, px + 44.0f, py + 72.0f, 22.0f, stack, now);
        g.renderItem(stack, px + 10, py + 24);
        String itemTitle = stack.getHoverName().getString() + " [" + assetType.name() + "]";
        g.drawString(font, itemTitle, px + 30, py + 28, C_TEXT_WHITE, true);

        List<String> summaries = TreatmentRouter.getItemEffectSummary(stack);
        int infoY = py + 112;
        for (int i = 0; i < Math.min(3, summaries.size()); i++) {
            g.drawString(font, summaries.get(i), px + 10, infoY + i * 11, C_TEXT_DIM, false);
        }

        // 2. 检测游标悬停肢体并绘制 6 大人体解剖部位槽位
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

        // 绘制解剖连接骨架线
        drawAnatomicalSkeletonLines(g, cx, cy + 8.0f, withAlpha(themeColor, 0.3f));

        // 逐个绘制肢体卡片
        for (LimbSlotLayout slot : LIMB_LAYOUTS) {
            drawLimbSlot(g, font, data, stack, slot, cx, cy + 8.0f, slot.limb() == hoveredLimb, themeColor, now);
        }

        // 3. 更新悬停肢体的自动读条进度（悬停在可治疗的肢体上即刻读条，满格自动应用）
        if (hoveredLimb != null && postApplyCooldownTicks <= 0) {
            boolean canTreat = TreatmentRouter.canTreatLimb(stack, data, hoveredLimb);
            if (canTreat) {
                float durationSec = Math.max(0.25f, TreatmentRouter.getChannelDurationSeconds(stack));
                channelProgress += dtSeconds / durationSec;
                if (channelProgress >= 1.0f) {
                    triggerApplyPacket(activeHand, hoveredLimb);
                }
            } else {
                channelProgress = 0.0f;
            }
        } else {
            channelProgress = 0.0f;
        }

        // 4. 底部状态提示栏
        int footerY = py + panelH - 18;
        g.fill(px + 8, footerY - 4, px + panelW - 8, footerY - 3, withAlpha(themeColor, 0.35f));
        String statusHint;
        int hintColor;
        if (hoveredLimb == null) {
            statusHint = "按住左键移动准星至受伤部位即可包扎/治疗 | 松开左键退出";
            hintColor = C_TEXT_DIM;
        } else if (TreatmentRouter.canTreatLimb(stack, data, hoveredLimb)) {
            int pct = Mth.clamp((int) (channelProgress * 100.0f), 0, 100);
            statusHint = "正在对 [" + getLimbDisplayName(hoveredLimb) + "] 使用 "
                    + stack.getHoverName().getString() + " (" + pct + "%) — 松开左键可立即完成";
            hintColor = C_SAFE_GREEN;
        } else {
            statusHint = "[" + getLimbDisplayName(hoveredLimb) + "] 当前无需使用该医疗用品";
            hintColor = C_WARNING_YELLOW;
        }
        g.drawCenteredString(font, statusHint, (int) cx, footerY, hintColor);

        // 5. 绘制全息战术准星游标与环状读条进度圈
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
        boolean openWound = limbData.hasOpenWound();
        boolean fracture = limbData.hasFracture() || limbData.hasDislocation();
        boolean bandaged = limbData.isBandaged();

        int x0 = (int) (centerX + slot.offsetX() - slot.width() * 0.5f);
        int y0 = (int) (centerY + slot.offsetY() - slot.height() * 0.5f);
        int x1 = (int) (x0 + slot.width());
        int y1 = (int) (y0 + slot.height());

        // 根据伤情和可治疗状态决定边框与底色
        int borderColor;
        int fillColor;
        if (canTreat) {
            float pulse = 0.65f + 0.35f * Mth.sin(nowMs * 0.01f);
            borderColor = hovered ? C_SAFE_GREEN : withAlpha(C_ALERT_RED, pulse);
            fillColor = hovered ? withAlpha(C_SAFE_GREEN, 0.28f) : withAlpha(C_ALERT_RED, 0.20f);
        } else if (bleed > 0 || openWound || fracture) {
            borderColor = hovered ? C_WARNING_YELLOW : withAlpha(C_WARNING_YELLOW, 0.75f);
            fillColor = withAlpha(C_WARNING_YELLOW, 0.14f);
        } else {
            borderColor = hovered ? themeColor : withAlpha(themeColor, 0.42f);
            fillColor = hovered ? withAlpha(themeColor, 0.18f) : 0x660A1624;
        }

        g.fill(x0, y0, x1, y1, fillColor);
        // 四周边框
        g.fill(x0, y0, x1, y0 + 1, borderColor);
        g.fill(x0, y1 - 1, x1, y1, borderColor);
        g.fill(x0, y0, x0 + 1, y1, borderColor);
        g.fill(x1 - 1, y0, x1, y1, borderColor);

        // 如果正在对该肢体读条，填充底部向上升起的能量条
        if (hovered && channelProgress > 0.01f) {
            int barH = (int) ((y1 - y0 - 2) * Mth.clamp(channelProgress, 0.0f, 1.0f));
            g.fill(x0 + 1, y1 - 1 - barH, x1 - 1, y1 - 1, withAlpha(C_SAFE_GREEN, 0.35f));
        }

        // 肢体名称与伤情短标签
        g.drawCenteredString(font, slot.cnName(), (x0 + x1) / 2, y0 + 4, C_TEXT_WHITE);

        String subStatus;
        int subColor;
        if (bleed > 0) {
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
        } else {
            subStatus = "正常";
            subColor = C_TEXT_DIM;
        }
        g.drawCenteredString(font, subStatus, (x0 + x1) / 2, y0 + 16, subColor);
    }

    private static void drawAnatomicalSkeletonLines(GuiGraphics g, float cx, float cy, int lineColor) {
        // 头部到胸腔
        g.fill((int) cx - 1, (int) (cy - 54), (int) cx + 1, (int) (cy - 48), lineColor);
        // 肩部横线
        g.fill((int) (cx - 33), (int) (cy - 34), (int) (cx + 33), (int) (cy - 33), lineColor);
        // 胸腔到盆骨与双腿
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx + 18), (int) (cy + 5), lineColor);
        g.fill((int) (cx - 18), (int) (cy + 4), (int) (cx - 17), (int) (cy + 21), lineColor);
        g.fill((int) (cx + 17), (int) (cy + 4), (int) (cx + 18), (int) (cy + 21), lineColor);
    }

    private static void drawTacticalCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int len = 12;
        // 左上
        g.fill(x, y, x + len, y + 2, color);
        g.fill(x, y, x + 2, y + len, color);
        // 右上
        g.fill(x + w - len, y, x + w, y + 2, color);
        g.fill(x + w - 2, y, x + w, y + len, color);
        // 左下
        g.fill(x, y + h - 2, x + len, y + h, color);
        g.fill(x, y + h - len, x + 2, y + h, color);
        // 右下
        g.fill(x + w - len, y + h - 2, x + w, y + h, color);
        g.fill(x + w - 2, y + h - len, x + w, y + h, color);
    }

    private static void drawTacticalCursor(GuiGraphics g, float x, float y, float progress, int themeColor) {
        int ix = Math.round(x);
        int iy = Math.round(y);
        int c = (progress > 0.01f) ? C_SAFE_GREEN : themeColor;

        // 中心十字星
        g.fill(ix - 6, iy, ix - 2, iy + 1, c);
        g.fill(ix + 3, iy, ix + 7, iy + 1, c);
        g.fill(ix, iy - 6, ix + 1, iy - 2, c);
        g.fill(ix, iy + 3, ix + 1, iy + 7, c);
        g.fill(ix, iy, ix + 1, iy + 1, C_TEXT_WHITE);

        // 环状读条进度圈（OpenGL 几何圆弧）
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
}