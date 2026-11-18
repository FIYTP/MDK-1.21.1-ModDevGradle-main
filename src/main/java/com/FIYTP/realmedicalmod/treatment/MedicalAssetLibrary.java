package com.FIYTP.realmedicalmod.treatment;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D 全息投影医疗器械渲染库 (MedicalAssetLibrary —— 稳定可靠全息线框投影版)
 *
 * 采用纯三维透视投影线框 ({@link Line3D}) 与全息扫描线渲染左键快捷面板与定量注射面板左侧的 3D 旋转器械动画，
 * 简单、清晰、零贴图错位，并支持随剂量滑块实时联动推进内部活塞推杆与药液液位环。
 */
public final class MedicalAssetLibrary {

    public enum AssetType {
        BANDAGE_ROLL,
        PRESSURE_BANDAGE,
        MORPHINE_INJECTOR,
        FENTANYL_SYRINGE,
        ADRENALINE_INJECTOR,
        NALOXONE_INJECTOR,
        TOURNIQUET,
        VASCULAR_SUTURE_KIT,
        BALLISTIC_EXTRACTION_KIT,
        PERFORATION_REPAIR_KIT,
        FASCIOTOMY_KIT,
        ORTHOPEDIC_KIT,
        CHEST_DRAIN_KIT,
        DECOMPRESSION_NEEDLE,
        TRANEXAMIC_ACID,
        ACTIVATED_CHARCOAL,
        ACETYLCYSTEINE,
        POVIDONE_IODINE,
        AMOXICILLIN,
        MEROPENEM,
        SYRINGE,
        SPLINT,
        MEDICINE_BOTTLE
    }

    public static final class Line3D {
        public final float x1, y1, z1;
        public final float x2, y2, z2;
        public final int color;
        public final float width;

        public Line3D(float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.color = color;
            this.width = width;
        }
    }

    private static final Map<ResourceLocation, AssetType> CUSTOM_ITEM_ASSETS = new HashMap<>();
    private static final Map<AssetType, List<Line3D>> GEOMETRY_CACHE = new HashMap<>();

    private MedicalAssetLibrary() {}

    public static void registerCustomItemAsset(ResourceLocation itemId, AssetType assetType) {
        if (itemId != null && assetType != null) {
            CUSTOM_ITEM_ASSETS.put(itemId, assetType);
        }
    }

    public static AssetType resolveAssetType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return AssetType.TOURNIQUET;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CUSTOM_ITEM_ASSETS.containsKey(id)) {
            return CUSTOM_ITEM_ASSETS.get(id);
        }
        if (id != null) {
            String path = id.getPath();
            if (path.contains("naloxone")) return AssetType.NALOXONE_INJECTOR;
            if (path.contains("tranexamic")) return AssetType.TRANEXAMIC_ACID;
            if (path.contains("activated_charcoal")) return AssetType.ACTIVATED_CHARCOAL;
            if (path.contains("acetylcysteine")) return AssetType.ACETYLCYSTEINE;
            if (path.contains("povidone_iodine")) return AssetType.POVIDONE_IODINE;
            if (path.contains("amoxicillin")) return AssetType.AMOXICILLIN;
            if (path.contains("meropenem")) return AssetType.MEROPENEM;
            if (path.contains("splint")) return AssetType.SPLINT;
            if (path.contains("ibuprofen")) return AssetType.MEDICINE_BOTTLE;
            if (path.contains("decompression_needle")) return AssetType.DECOMPRESSION_NEEDLE;
            if (path.contains("chest_drain")) return AssetType.CHEST_DRAIN_KIT;
            if (path.contains("orthopedic")) return AssetType.ORTHOPEDIC_KIT;
            if (path.contains("fasciotomy")) return AssetType.FASCIOTOMY_KIT;
            if (path.contains("ballistic_extraction")) return AssetType.BALLISTIC_EXTRACTION_KIT;
            if (path.contains("perforation_repair")) return AssetType.PERFORATION_REPAIR_KIT;
            if (path.contains("suture_kit")) return AssetType.VASCULAR_SUTURE_KIT;
            if (path.contains("tourniquet")) return AssetType.TOURNIQUET;
            if (path.contains("adrenaline")) return AssetType.ADRENALINE_INJECTOR;
            if (path.contains("fentanyl")) return AssetType.FENTANYL_SYRINGE;
            if (path.contains("morphine")) return AssetType.MORPHINE_INJECTOR;
            if (path.contains("pressure_bandage")) return AssetType.PRESSURE_BANDAGE;
        }
        if (stack.is(MedicalItemTags.OPIOID)) {
            return AssetType.MORPHINE_INJECTOR;
        }
        if (stack.is(MedicalItemTags.TOURNIQUET)) {
            return AssetType.TOURNIQUET;
        }
        if (stack.is(MedicalItemTags.SYRINGE)) {
            return AssetType.SYRINGE;
        }
        if (stack.is(MedicalItemTags.SPLINT) || stack.is(MedicalItemTags.SPLINT_EFFECT)) {
            return AssetType.SPLINT;
        }
        if (stack.is(MedicalItemTags.TOPICAL) || stack.is(MedicalItemTags.ORAL)) {
            return AssetType.MEDICINE_BOTTLE;
        }
        return AssetType.BANDAGE_ROLL;
    }

    /**
     * 在屏幕指定中心渲染 360° 旋转的 3D 全息投影医疗器械模型
     */
    public static void render3DMedicalItem(GuiGraphics g, float centerX, float centerY, float scale,
                                           ItemStack stack, long nowMs) {
        AssetType type = resolveAssetType(stack);
        if (type == AssetType.MORPHINE_INJECTOR || type == AssetType.FENTANYL_SYRINGE
                || type == AssetType.TRANEXAMIC_ACID || type == AssetType.ACETYLCYSTEINE || type == AssetType.MEROPENEM) {
            float remRatio = 1.0f;
            if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                remRatio = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            }
            if (type == AssetType.TRANEXAMIC_ACID) {
                render3DTxaSyringeWithPlunger(g, centerX, centerY, scale, remRatio, 0.0f, nowMs);
            } else if (type == AssetType.ACETYLCYSTEINE) {
                render3DNacSyringeWithPlunger(g, centerX, centerY, scale, remRatio, 0.0f, nowMs);
            } else if (type == AssetType.MEROPENEM) {
                render3DMeropenemSyringeWithPlunger(g, centerX, centerY, scale, remRatio, 0.0f, nowMs);
            } else {
                render3DOpioidSyringeWithPlunger(g, centerX, centerY, scale, type == AssetType.FENTANYL_SYRINGE, remRatio, 0.0f, nowMs);
            }
            return;
        }
        List<Line3D> lines = GEOMETRY_CACHE.computeIfAbsent(type, MedicalAssetLibrary::buildGeometry);
        renderLines3D(g, lines, centerX, centerY, scale, nowMs);
    }

    /**
     * 在定量注射面板左侧渲染带动态活塞推杆与药液液位全息环的 3D 全息投影注射器
     */
    public static void render3DOpioidSyringeWithPlunger(GuiGraphics g, float centerX, float centerY, float scale,
                                                        boolean isFentanyl, float remainingRatio, float selectedDoseRatio, long nowMs) {
        List<Line3D> lines = isFentanyl
                ? buildDynamicFentanylSyringe(remainingRatio, selectedDoseRatio)
                : buildDynamicMorphineAutoinjector(remainingRatio, selectedDoseRatio);
        renderLines3D(g, lines, centerX, centerY, scale, nowMs);
    }

    /**
     * 在定量注射面板左侧渲染带动态活塞推杆与紫红药液液位全息环的 3D 氨甲环酸注射器
     */
    public static void render3DTxaSyringeWithPlunger(GuiGraphics g, float centerX, float centerY, float scale,
                                                     float remainingRatio, float selectedDoseRatio, long nowMs) {
        List<Line3D> lines = buildDynamicTranexamicAcidSyringe(remainingRatio, selectedDoseRatio);
        renderLines3D(g, lines, centerX, centerY, scale, nowMs);
    }

    public static void render3DNacSyringeWithPlunger(GuiGraphics g, float centerX, float centerY, float scale,
                                                     float remainingRatio, float selectedDoseRatio, long nowMs) {
        List<Line3D> lines = buildDynamicNacSyringe(remainingRatio, selectedDoseRatio);
        renderLines3D(g, lines, centerX, centerY, scale, nowMs);
    }

    public static void render3DMeropenemSyringeWithPlunger(GuiGraphics g, float centerX, float centerY, float scale,
                                                           float remainingRatio, float selectedDoseRatio, long nowMs) {
        List<Line3D> lines = buildDynamicMeropenemSyringe(remainingRatio, selectedDoseRatio);
        renderLines3D(g, lines, centerX, centerY, scale, nowMs);
    }

    private static void renderLines3D(GuiGraphics g, List<Line3D> lines, float centerX, float centerY, float scale, long nowMs) {
        if (lines == null || lines.isEmpty()) return;

        // 全息投影底座扫描线光环
        float scanOffset = ((nowMs % 2400L) / 2400.0f) * 2.0f - 1.0f;
        int scanY = Math.round(centerY + scanOffset * scale * 0.85f);
        g.fill(Math.round(centerX - scale * 0.72f), scanY, Math.round(centerX + scale * 0.72f), scanY + 1, 0x3800E5FF);

        float yaw = (nowMs % 7500L) / 7500.0f * Mth.TWO_PI;
        float pitch = 0.35f + 0.10f * Mth.sin(nowMs * 0.0022f);
        float roll = -0.32f;

        float cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        float cp = Mth.cos(pitch), sp = Mth.sin(pitch);
        float cr = Mth.cos(roll), sr = Mth.sin(roll);

        Matrix4f pose = g.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVerts = false;

        for (Line3D ln : lines) {
            float[] p1 = project(ln.x1, ln.y1, ln.z1, centerX, centerY, scale, cy, sy, cp, sp, cr, sr);
            float[] p2 = project(ln.x2, ln.y2, ln.z2, centerX, centerY, scale, cy, sy, cp, sp, cr, sr);

            float avgZ = (p1[2] + p2[2]) * 0.5f;
            float depthShade = Mth.clamp(0.76f + 0.28f * avgZ, 0.42f, 1.15f);

            int a = (ln.color >>> 24) & 0xFF;
            int r = Mth.clamp((int) (((ln.color >>> 16) & 0xFF) * depthShade), 0, 255);
            int gr = Mth.clamp((int) (((ln.color >>> 8) & 0xFF) * depthShade), 0, 255);
            int b = Mth.clamp((int) ((ln.color & 0xFF) * depthShade), 0, 255);

            // 外层柔和全息辉光
            int glowA = Math.max(18, a / 4);
            if (emitQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1], ln.width * 2.2f, r, gr, b, glowA)) {
                hasVerts = true;
            }
            // 核心全息高亮线条
            if (emitQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1], ln.width, r, gr, b, a)) {
                hasVerts = true;
            }
        }

        if (hasVerts) {
            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static float[] project(float x, float y, float z, float cx, float cy, float scale,
                                   float cosY, float sinY, float cosP, float sinP, float cosR, float sinR) {
        float x1 = x * cosY - z * sinY;
        float z1 = x * sinY + z * cosY;
        float y2 = y * cosP - z1 * sinP;
        float z2 = y * sinP + z1 * cosP;
        float x3 = x1 * cosR - y2 * sinR;
        float y3 = x1 * sinR + y2 * cosR;

        float fov = 3.0f / (3.0f - z2 * 0.45f);
        return new float[]{cx + x3 * scale * fov, cy + y3 * scale * fov, z2};
    }

    private static boolean emitQuadSegment(BufferBuilder buf, Matrix4f pose,
                                           float x1, float y1, float x2, float y2,
                                           float width, int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.05f) return false;

        float nx = -dy / len * (width * 0.5f);
        float ny = dx / len * (width * 0.5f);

        buf.addVertex(pose, x1 + nx, y1 + ny, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, x2 + nx, y2 + ny, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, x2 - nx, y2 - ny, 0.0f).setColor(r, g, b, a);
        buf.addVertex(pose, x1 - nx, y1 - ny, 0.0f).setColor(r, g, b, a);
        return true;
    }

    private static List<Line3D> buildGeometry(AssetType type) {
        List<Line3D> out = new ArrayList<>();
        switch (type) {
            case BANDAGE_ROLL -> buildBandageRoll(out, false);
            case PRESSURE_BANDAGE -> buildBandageRoll(out, true);
            case MORPHINE_INJECTOR -> out.addAll(buildDynamicMorphineAutoinjector(1.0f, 0.0f));
            case FENTANYL_SYRINGE -> out.addAll(buildDynamicFentanylSyringe(1.0f, 0.0f));
            case ADRENALINE_INJECTOR -> buildAdrenalineInjector(out);
            case NALOXONE_INJECTOR -> buildNaloxoneInjector(out);
            case TOURNIQUET -> buildTourniquet(out);
            case VASCULAR_SUTURE_KIT -> buildVascularSutureKit(out);
            case FASCIOTOMY_KIT -> buildFasciotomyKit(out);
            case ORTHOPEDIC_KIT -> buildOrthopedicKit(out);
            case CHEST_DRAIN_KIT -> buildChestDrainKit(out);
            case DECOMPRESSION_NEEDLE -> buildDecompressionNeedle(out);
            case TRANEXAMIC_ACID -> buildTranexamicAcidInjector(out);
            case ACTIVATED_CHARCOAL -> buildActivatedCharcoalBottle(out);
            case ACETYLCYSTEINE -> out.addAll(buildDynamicNacSyringe(1.0f, 0.0f));
            case POVIDONE_IODINE -> buildPovidoneIodineSpray(out);
            case AMOXICILLIN -> buildAmoxicillinBottle(out);
            case MEROPENEM -> out.addAll(buildDynamicMeropenemSyringe(1.0f, 0.0f));
            case SYRINGE -> buildSyringe(out);
            case SPLINT -> buildSplint(out);
            case MEDICINE_BOTTLE -> buildMedicineBottle(out);
        }
        return out;
    }

    private static void addCylinderY(List<Line3D> out, float radius, float yTop, float yBot,
                                     int segments, int ringColor, int pillarColor, float width) {
        for (int i = 0; i < segments; i++) {
            float a1 = (i / (float) segments) * Mth.TWO_PI;
            float a2 = ((i + 1) / (float) segments) * Mth.TWO_PI;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float x2 = Mth.cos(a2) * radius;
            float z2 = Mth.sin(a2) * radius;

            out.add(new Line3D(x1, yTop, z1, x2, yTop, z2, ringColor, width));
            out.add(new Line3D(x1, yBot, z1, x2, yBot, z2, ringColor, width));
            if (i % 2 == 0) {
                out.add(new Line3D(x1, yTop, z1, x1, yBot, z1, pillarColor, width * 0.85f));
            }
        }
    }

    private static void addRingY(List<Line3D> out, float radius, float y, int segments, int color, float width) {
        for (int i = 0; i < segments; i++) {
            float a1 = (i / (float) segments) * Mth.TWO_PI;
            float a2 = ((i + 1) / (float) segments) * Mth.TWO_PI;
            out.add(new Line3D(
                    Mth.cos(a1) * radius, y, Mth.sin(a1) * radius,
                    Mth.cos(a2) * radius, y, Mth.sin(a2) * radius,
                    color, width
            ));
        }
    }

    // ==================== 1. 盐酸吗啡军规自动注射器 (3D 全息投影模型) ====================
    private static List<Line3D> buildDynamicMorphineAutoinjector(float remainingRatio, float selectedDoseRatio) {
        List<Line3D> out = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        int cCapOrange = 0xFFFF6D00;
        int cBodySteel = 0xFF90A4AE;
        int cGripDark  = 0xFF64B5F6;
        int cGlass     = 0xCCE1F5FE;
        int cAmberLiq  = 0xFFFFB300;
        int cDoseGlow  = 0xFFFFEA00;
        int cNeedle    = 0xFFFFFFFF;

        // 1. 顶部保险插销与安全橙击发帽
        addCylinderY(out, 0.17f, -0.78f, -0.56f, 10, cCapOrange, cCapOrange, 1.5f);
        addRingY(out, 0.09f, -0.82f, 8, cNeedle, 1.3f);

        // 2. 上段军规套筒与防滑握环
        addCylinderY(out, 0.24f, -0.56f, -0.02f, 12, cBodySteel, cGripDark, 1.4f);
        addRingY(out, 0.255f, -0.42f, 12, cAmberLiq, 1.7f);
        addRingY(out, 0.255f, -0.22f, 12, cGripDark, 1.4f);

        // 3. 下段高硼硅观察窗与动态琥珀药液全息环
        float windowTop = -0.02f;
        float windowBot = 0.44f;
        addCylinderY(out, 0.22f, windowTop, windowBot, 12, cGlass, 0x8881D4FA, 1.25f);

        for (int k = 1; k <= 3; k++) {
            float gy = Mth.lerp(k / 4.0f, windowTop, windowBot);
            out.add(new Line3D(-0.23f, gy, 0.0f, -0.14f, gy, 0.0f, cNeedle, 1.2f));
            out.add(new Line3D(0.14f, gy, 0.0f, 0.23f, gy, 0.0f, cNeedle, 1.2f));
        }

        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float plungerY = Mth.lerp(effectivePlungerRatio, windowBot - 0.03f, windowTop + 0.04f);

        // 内部动态推进弹簧推杆与活塞圆盘
        out.add(new Line3D(0.0f, windowTop, 0.0f, 0.0f, plungerY, 0.0f, cNeedle, 1.8f));
        addRingY(out, 0.17f, plungerY, 10, 0xFFCFD8DC, 1.8f);

        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, windowBot - 0.02f, windowTop + 0.04f);
            float afterInjectY = Mth.lerp(effectivePlungerRatio, windowBot - 0.02f, windowTop + 0.04f);

            int rings = 5;
            for (int r = 0; r <= rings; r++) {
                float ry = Mth.lerp(r / (float) rings, liqTopY, windowBot - 0.02f);
                boolean inSelectedSlice = (ry <= afterInjectY + 0.005f) && (selectedDoseRatio > 0.005f);
                int col = inSelectedSlice ? cDoseGlow : cAmberLiq;
                addRingY(out, 0.165f, ry, 10, col, inSelectedSlice ? 1.8f : 1.3f);
            }
        }

        // 4. 底部护针座与穿刺针头
        addCylinderY(out, 0.18f, 0.44f, 0.58f, 10, cGripDark, cBodySteel, 1.4f);
        addRingY(out, 0.08f, 0.64f, 8, cBodySteel, 1.4f);
        out.add(new Line3D(0.0f, 0.58f, 0.0f, 0.0f, 0.90f, 0.0f, cNeedle, 1.9f));

        return out;
    }

    // ==================== 2. 枸橼酸芬太尼精密微量注射器 (3D 全息投影模型) ====================
    private static List<Line3D> buildDynamicFentanylSyringe(float remainingRatio, float selectedDoseRatio) {
        List<Line3D> out = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        int cBarrelGlass = 0xDD80D8FF;
        int cFentanylLiq = 0xFF00E5FF;
        int cDosePulse   = 0xFFFF4081;
        int cHazardRing  = 0xFFF50057;
        int cPlungerRod  = 0xFFB3E5FC;
        int cStopper     = 0xFF90A4AE;
        int cNeedle      = 0xFFFFFFFF;

        float barrelTop = -0.32f;
        float barrelBot = 0.46f;
        float barrelRad = 0.19f;

        // 1. 针筒尾部宽翼指托
        out.add(new Line3D(-0.42f, barrelTop, 0.0f, 0.42f, barrelTop, 0.0f, cPlungerRod, 2.0f));
        out.add(new Line3D(0.0f, barrelTop, -0.26f, 0.0f, barrelTop, 0.26f, cPlungerRod, 1.5f));
        addRingY(out, 0.24f, barrelTop, 12, cPlungerRod, 1.6f);

        // 2. 高硼硅针筒与品红警示环
        addCylinderY(out, barrelRad, barrelTop, barrelBot, 12, cBarrelGlass, 0x9940C4FF, 1.3f);
        addRingY(out, barrelRad + 0.015f, barrelTop + 0.07f, 12, cHazardRing, 1.8f);
        addRingY(out, barrelRad + 0.015f, barrelBot - 0.06f, 12, cHazardRing, 1.8f);

        for (int k = 1; k <= 5; k++) {
            float gy = Mth.lerp(k / 6.0f, barrelTop + 0.08f, barrelBot - 0.06f);
            float tickLen = (k % 2 == 0) ? 0.09f : 0.05f;
            out.add(new Line3D(-barrelRad, gy, 0.0f, -barrelRad + tickLen, gy, 0.0f, cNeedle, 1.2f));
            out.add(new Line3D(barrelRad - tickLen, gy, 0.0f, barrelRad, gy, 0.0f, cNeedle, 1.2f));
        }

        // 3. 随滑块实时推进的十字推杆与拇指推盘
        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float stopperY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.04f, barrelTop + 0.06f);
        float rodTopY = stopperY - 0.54f;

        addRingY(out, 0.22f, rodTopY, 10, cHazardRing, 1.8f);
        out.add(new Line3D(-0.22f, rodTopY, 0.0f, 0.22f, rodTopY, 0.0f, cPlungerRod, 1.8f));
        out.add(new Line3D(0.0f, rodTopY, 0.0f, 0.0f, stopperY, 0.0f, cPlungerRod, 1.8f));
        out.add(new Line3D(-0.08f, rodTopY, 0.0f, -0.08f, stopperY, 0.0f, 0xAA81D4FA, 1.1f));
        out.add(new Line3D(0.08f, rodTopY, 0.0f, 0.08f, stopperY, 0.0f, 0xAA81D4FA, 1.1f));

        addRingY(out, barrelRad * 0.86f, stopperY - 0.03f, 10, cStopper, 1.8f);
        addRingY(out, barrelRad * 0.86f, stopperY, 10, cStopper, 1.8f);

        // 4. 冰青色药液全息环
        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            float afterInjectY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.03f, barrelTop + 0.06f);

            int rings = 6;
            for (int r = 0; r <= rings; r++) {
                float ry = Mth.lerp(r / (float) rings, liqTopY, barrelBot - 0.03f);
                boolean inSelectedSlice = (ry <= afterInjectY + 0.005f) && (selectedDoseRatio > 0.005f);
                int col = inSelectedSlice ? cDosePulse : cFentanylLiq;
                addRingY(out, barrelRad * 0.80f, ry, 10, col, inSelectedSlice ? 1.8f : 1.3f);
            }
        }

        // 5. 鲁尔锁锥头与穿刺针
        addCylinderY(out, 0.08f, barrelBot, barrelBot + 0.14f, 8, cPlungerRod, cBarrelGlass, 1.4f);
        out.add(new Line3D(0.0f, barrelBot + 0.14f, 0.0f, 0.0f, barrelBot + 0.46f, 0.0f, cNeedle, 1.8f));

        return out;
    }

    // ==================== 2B. 氨甲环酸抗纤溶止血注射器 (3D 动态推杆全息投影模型) ====================
    private static List<Line3D> buildDynamicTranexamicAcidSyringe(float remainingRatio, float selectedDoseRatio) {
        List<Line3D> out = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        int cBarrelGlass = 0xDDCE93D8;
        int cTxaLiquid   = 0xFFE040FB;
        int cDosePulse   = 0xFFFF80AB;
        int cVioletRing  = 0xFFAA00FF;
        int cPlungerRod  = 0xFFE1BEE7;
        int cStopper     = 0xFF7B1FA2;
        int cNeedle      = 0xFFFFFFFF;

        float barrelTop = -0.34f;
        float barrelBot = 0.46f;
        float barrelRad = 0.21f;

        // 1. 针筒尾部双侧防滑翼形指托与战术识别环
        out.add(new Line3D(-0.44f, barrelTop, 0.0f, 0.44f, barrelTop, 0.0f, cPlungerRod, 2.0f));
        out.add(new Line3D(0.0f, barrelTop, -0.26f, 0.0f, barrelTop, 0.26f, cPlungerRod, 1.5f));
        addRingY(out, 0.25f, barrelTop, 12, cVioletRing, 1.7f);

        // 2. 高硼硅玻璃针筒与紫红双色识别环
        addCylinderY(out, barrelRad, barrelTop, barrelBot, 12, cBarrelGlass, 0x99BA68C8, 1.35f);
        addRingY(out, barrelRad + 0.015f, barrelTop + 0.07f, 12, cVioletRing, 1.9f);
        addRingY(out, barrelRad + 0.015f, barrelTop + 0.14f, 12, cTxaLiquid, 1.8f);
        addRingY(out, barrelRad + 0.015f, barrelBot - 0.06f, 12, cVioletRing, 1.9f);

        for (int k = 1; k <= 5; k++) {
            float gy = Mth.lerp(k / 6.0f, barrelTop + 0.10f, barrelBot - 0.06f);
            float tickLen = (k % 2 == 0) ? 0.09f : 0.05f;
            out.add(new Line3D(-barrelRad, gy, 0.0f, -barrelRad + tickLen, gy, 0.0f, cNeedle, 1.2f));
            out.add(new Line3D(barrelRad - tickLen, gy, 0.0f, barrelRad, gy, 0.0f, cNeedle, 1.2f));
        }

        // 3. 随滑块实时推进的十字透明推杆与紫晶拇指推盘
        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float stopperY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.04f, barrelTop + 0.06f);
        float rodTopY = stopperY - 0.54f;

        addRingY(out, 0.23f, rodTopY, 10, cTxaLiquid, 1.9f);
        out.add(new Line3D(-0.23f, rodTopY, 0.0f, 0.23f, rodTopY, 0.0f, cPlungerRod, 1.8f));
        out.add(new Line3D(0.0f, rodTopY, 0.0f, 0.0f, stopperY, 0.0f, cPlungerRod, 1.8f));
        out.add(new Line3D(-0.08f, rodTopY, 0.0f, -0.08f, stopperY, 0.0f, 0xAACE93D8, 1.1f));
        out.add(new Line3D(0.08f, rodTopY, 0.0f, 0.08f, stopperY, 0.0f, 0xAACE93D8, 1.1f));

        addRingY(out, barrelRad * 0.86f, stopperY - 0.03f, 10, cStopper, 1.8f);
        addRingY(out, barrelRad * 0.86f, stopperY, 10, cStopper, 1.8f);

        // 4. 氨甲环酸药液全息环（高亮当前选定推注量切片）
        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            float afterInjectY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.03f, barrelTop + 0.06f);

            int rings = 6;
            for (int r = 0; r <= rings; r++) {
                float ry = Mth.lerp(r / (float) rings, liqTopY, barrelBot - 0.03f);
                boolean inSelectedSlice = (ry <= afterInjectY + 0.005f) && (selectedDoseRatio > 0.005f);
                int col = inSelectedSlice ? cDosePulse : cTxaLiquid;
                addRingY(out, barrelRad * 0.80f, ry, 10, col, inSelectedSlice ? 1.85f : 1.35f);
            }
        }

        // 5. 鲁尔锁螺纹金属针座与穿刺针
        addCylinderY(out, 0.09f, barrelBot, barrelBot + 0.14f, 8, cPlungerRod, cBarrelGlass, 1.4f);
        out.add(new Line3D(0.0f, barrelBot + 0.14f, 0.0f, 0.0f, barrelBot + 0.46f, 0.0f, cNeedle, 1.85f));

        return out;
    }

    private static void buildBandageRoll(List<Line3D> out, boolean isPressure) {
        int mainCol = isPressure ? 0xFF66BB6A : 0xFFE0F7FA;
        int subCol  = isPressure ? 0xFF43A047 : 0xFF4DD0E1;
        int accent  = isPressure ? 0xFFFFCA28 : 0xFFFF5252;

        addCylinderY(out, 0.56f, -0.45f, 0.45f, 16, mainCol, subCol, 1.5f);
        addCylinderY(out, 0.22f, -0.46f, 0.46f, 12, accent, subCol, 1.2f);
        addRingY(out, 0.39f, -0.45f, 14, subCol, 1.1f);
        addRingY(out, 0.39f, 0.45f, 14, subCol, 1.1f);

        if (isPressure) {
            out.add(new Line3D(-0.18f, -0.48f, -0.64f, 0.18f, -0.48f, -0.64f, accent, 2.0f));
            out.add(new Line3D(-0.18f, 0.48f, -0.64f, 0.18f, 0.48f, -0.64f, accent, 2.0f));
            out.add(new Line3D(-0.18f, -0.48f, -0.64f, -0.18f, 0.48f, -0.64f, accent, 2.0f));
            out.add(new Line3D(0.18f, -0.48f, -0.64f, 0.18f, 0.48f, -0.64f, accent, 2.0f));
        } else {
            out.add(new Line3D(-0.14f, 0.0f, -0.58f, 0.14f, 0.0f, -0.58f, accent, 2.0f));
            out.add(new Line3D(0.0f, -0.14f, -0.58f, 0.0f, 0.14f, -0.58f, accent, 2.0f));
        }
    }


    private static void buildAdrenalineInjector(List<Line3D> out) {
        int cBlueCap   = 0xFF2979FF;
        int cGoldBody  = 0xFFFFD600;
        int cRedBand   = 0xFFFF1744;
        int cOrangeTip = 0xFFFF6D00;
        int cNeedle    = 0xFFFFFFFF;

        // 顶部蓝帽 + 柠檬金笔身 + 红十字闪电环 + 底部橙色伸缩护针锥
        addCylinderY(out, 0.20f, -0.78f, -0.52f, 10, cBlueCap, cBlueCap, 1.5f);
        addCylinderY(out, 0.24f, -0.52f, 0.42f, 12, cGoldBody, 0xFFFFEA00, 1.4f);
        addRingY(out, 0.255f, -0.36f, 12, cRedBand, 1.8f);
        addRingY(out, 0.255f, 0.08f, 12, cBlueCap, 1.4f);
        addCylinderY(out, 0.19f, 0.42f, 0.64f, 10, cOrangeTip, cOrangeTip, 1.5f);
        out.add(new Line3D(0.0f, 0.64f, 0.0f, 0.0f, 0.92f, 0.0f, cNeedle, 1.9f));
    }

    private static void buildNaloxoneInjector(List<Line3D> out) {
        int cTealCap   = 0xFF00BFA5;
        int cWhiteBody = 0xFFE0F7FA;
        int cCyanLiq   = 0xFF00E5FF;
        int cNeedle    = 0xFFFFFFFF;

        // 顶部推杆环 + 指托翼 + 翡翠青白针筒 + 药液环 + 穿刺针
        addRingY(out, 0.20f, -0.82f, 10, cTealCap, 1.6f);
        out.add(new Line3D(0.0f, -0.82f, 0.0f, 0.0f, -0.36f, 0.0f, cNeedle, 1.7f));
        out.add(new Line3D(-0.42f, -0.36f, 0.0f, 0.42f, -0.36f, 0.0f, cTealCap, 2.0f));
        addCylinderY(out, 0.21f, -0.36f, 0.44f, 12, cWhiteBody, cTealCap, 1.4f);
        addRingY(out, 0.225f, -0.14f, 12, cTealCap, 1.8f);
        addRingY(out, 0.16f, 0.10f, 10, cCyanLiq, 1.6f);
        addRingY(out, 0.16f, 0.26f, 10, cCyanLiq, 1.6f);
        addCylinderY(out, 0.12f, 0.44f, 0.60f, 8, cTealCap, cWhiteBody, 1.4f);
        out.add(new Line3D(0.0f, 0.60f, 0.0f, 0.0f, 0.90f, 0.0f, cNeedle, 1.9f));
    }

    private static void buildVascularSutureKit(List<Line3D> out) {
        int cCase   = 0xFF81C784;
        int cSteel  = 0xFFE0F7FA;
        int cGold   = 0xFFFFD600;
        int cRed    = 0xFFFF1744;
        int cBlue   = 0xFF00E5FF;

        // 下层器械盒底座
        out.add(new Line3D(-0.62f, 0.35f, -0.42f,  0.62f, 0.35f, -0.42f, cCase, 1.6f));
        out.add(new Line3D(-0.62f, 0.35f,  0.42f,  0.62f, 0.35f,  0.42f, cCase, 1.6f));
        out.add(new Line3D(-0.62f, 0.35f, -0.42f, -0.62f, 0.35f,  0.42f, cCase, 1.6f));
        out.add(new Line3D( 0.62f, 0.35f, -0.42f,  0.62f, 0.35f,  0.42f, cCase, 1.6f));
        out.add(new Line3D(-0.62f, 0.12f, -0.42f,  0.62f, 0.12f, -0.42f, cCase, 1.4f));
        out.add(new Line3D(-0.62f, 0.12f,  0.42f,  0.62f, 0.12f,  0.42f, cCase, 1.4f));
        out.add(new Line3D(-0.62f, 0.12f, -0.42f, -0.62f, 0.12f,  0.42f, cCase, 1.4f));
        out.add(new Line3D( 0.62f, 0.12f, -0.42f,  0.62f, 0.12f,  0.42f, cCase, 1.4f));

        // 翻开式上盖与内置环形止血带
        out.add(new Line3D(-0.62f, 0.12f, -0.42f, -0.62f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D( 0.62f, 0.12f, -0.42f,  0.62f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D(-0.62f, -0.62f, -0.64f, 0.62f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D(-0.38f, -0.42f, -0.52f, -0.06f, -0.42f, -0.52f, cRed, 1.8f));
        out.add(new Line3D(-0.38f, -0.10f, -0.52f, -0.06f, -0.10f, -0.52f, cRed, 1.8f));
        out.add(new Line3D(-0.38f, -0.42f, -0.52f, -0.38f, -0.10f, -0.52f, cRed, 1.8f));
        out.add(new Line3D(-0.06f, -0.42f, -0.52f, -0.06f, -0.10f, -0.52f, cRed, 1.8f));

        // 内部止血钳、蓝色缝线轴与白色桥接血管
        out.add(new Line3D(-0.42f, 0.08f, -0.26f, -0.12f, 0.08f, 0.30f, cSteel, 1.8f));
        out.add(new Line3D(-0.12f, 0.08f, -0.26f, -0.42f, 0.08f, 0.30f, cSteel, 1.8f));
        addRingY(out, 0.15f, 0.06f, 10, cBlue, 1.6f);
        out.add(new Line3D(0.12f, 0.05f, -0.28f, 0.48f, 0.05f, 0.12f, cGold, 2.0f));
    }

    private static void buildTourniquet(List<Line3D> out) {
        int cStrap   = 0xFFB0BEC5;
        int cEdge    = 0xFF78909C;
        int cCrimson = 0xFFFF1744;
        int cRod     = 0xFF00E5FF;
        int cTime    = 0xFFFFFFFF;
        int cBuckle  = 0xFFE0F7FA;

        // 1. 16 段高精双层中空尼龙束带环 + 内衬红带
        addCylinderY(out, 0.62f, -0.22f, 0.22f, 16, cStrap, cEdge, 1.6f);
        addRingY(out, 0.63f, -0.24f, 16, cEdge, 1.2f);
        addRingY(out, 0.63f,  0.24f, 16, cEdge, 1.2f);
        addCylinderY(out, 0.50f, -0.17f, 0.17f, 16, cCrimson, cCrimson, 1.35f);

        // 2. 正面弧形稳定背板与双侧 C 型锁扣槽
        out.add(new Line3D(-0.36f, -0.25f, -0.66f, 0.36f, -0.25f, -0.66f, cEdge, 1.6f));
        out.add(new Line3D(-0.36f,  0.25f, -0.66f, 0.36f,  0.25f, -0.66f, cEdge, 1.6f));
        out.add(new Line3D(-0.34f, -0.22f, -0.66f, -0.34f, 0.22f, -0.78f, cRod, 1.7f));
        out.add(new Line3D( 0.34f, -0.22f, -0.66f,  0.34f, 0.22f, -0.78f, cRod, 1.7f));
        out.add(new Line3D(-0.36f, -0.20f, -0.80f,  0.36f, -0.20f, -0.80f, cTime, 2.0f));

        // 3. 22.5° 倾斜加压绞棒 + 红帽防滑握端
        out.add(new Line3D(-0.62f, -0.18f, -0.76f, 0.62f, 0.18f, -0.76f, cRod, 2.4f));
        out.add(new Line3D(-0.62f, -0.12f, -0.76f, 0.62f, 0.24f, -0.76f, cRod, 1.5f));
        out.add(new Line3D(-0.68f, -0.20f, -0.76f, -0.58f, -0.16f, -0.76f, cCrimson, 2.8f));
        out.add(new Line3D( 0.58f,  0.16f, -0.76f,  0.68f,  0.20f, -0.76f, cCrimson, 2.8f));

        // 4. 侧翼镂空日字金属扣 + 穿出式红尾拉带
        out.add(new Line3D(0.58f, -0.24f, -0.28f, 0.68f, -0.24f, 0.08f, cBuckle, 1.8f));
        out.add(new Line3D(0.58f,  0.24f, -0.28f, 0.68f,  0.24f, 0.08f, cBuckle, 1.8f));
        out.add(new Line3D(0.63f, -0.24f, -0.10f, 0.63f,  0.24f, -0.10f, cBuckle, 1.5f));
        out.add(new Line3D(0.65f, -0.18f, -0.05f, 0.92f, -0.18f, 0.22f, cCrimson, 1.9f));
        out.add(new Line3D(0.65f,  0.18f, -0.05f, 0.92f,  0.18f, 0.22f, cCrimson, 1.9f));
        out.add(new Line3D(0.92f, -0.18f,  0.22f, 0.92f,  0.18f, 0.22f, cCrimson, 2.2f));
    }

    private static void buildSyringe(List<Line3D> out) {
        out.addAll(buildDynamicMorphineAutoinjector(1.0f, 0.0f));
    }

    private static void buildSplint(List<Line3D> out) {
        addCylinderY(out, 0.35f, -0.75f, 0.75f, 8, 0xFFFFB74D, 0xFFFFA726, 1.5f);
    }

    private static List<Line3D> buildDynamicNacSyringe(float remainingRatio, float selectedDoseRatio) {
        List<Line3D> out = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        int cBarrelGlass = 0xDD80CBC4;
        int cNacLiquid   = 0xFF00E676;
        int cDosePulse   = 0xFF69F0AE;
        int cTealRing    = 0xFF00BFA5;
        int cPlungerRod  = 0xFFB2DFDB;
        int cStopper     = 0xFF00695C;
        int cNeedle      = 0xFFFFFFFF;

        float barrelTop = -0.34f;
        float barrelBot = 0.46f;
        float barrelRad = 0.21f;

        out.add(new Line3D(-0.44f, barrelTop, 0.0f, 0.44f, barrelTop, 0.0f, cPlungerRod, 2.0f));
        addRingY(out, 0.25f, barrelTop, 12, cTealRing, 1.7f);
        addCylinderY(out, barrelRad, barrelTop, barrelBot, 12, cBarrelGlass, 0x994DB6AC, 1.35f);
        addRingY(out, barrelRad + 0.015f, barrelTop + 0.07f, 12, cNacLiquid, 1.9f);
        addRingY(out, barrelRad + 0.015f, barrelBot - 0.06f, 12, cNacLiquid, 1.9f);

        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float stopperY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.04f, barrelTop + 0.06f);
        float rodTopY = stopperY - 0.54f;

        addRingY(out, 0.23f, rodTopY, 10, cNacLiquid, 1.9f);
        out.add(new Line3D(0.0f, rodTopY, 0.0f, 0.0f, stopperY, 0.0f, cPlungerRod, 1.8f));
        addRingY(out, barrelRad * 0.86f, stopperY, 10, cStopper, 1.8f);

        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            float afterInjectY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            int rings = 6;
            for (int r = 0; r <= rings; r++) {
                float ry = Mth.lerp(r / (float) rings, liqTopY, barrelBot - 0.03f);
                boolean inSelectedSlice = (ry <= afterInjectY + 0.005f) && (selectedDoseRatio > 0.005f);
                int col = inSelectedSlice ? cDosePulse : cNacLiquid;
                addRingY(out, barrelRad * 0.80f, ry, 10, col, inSelectedSlice ? 1.85f : 1.35f);
            }
        }

        addCylinderY(out, 0.09f, barrelBot, barrelBot + 0.14f, 8, 0xFFFFD54F, cBarrelGlass, 1.4f);
        out.add(new Line3D(0.0f, barrelBot + 0.14f, 0.0f, 0.0f, barrelBot + 0.46f, 0.0f, cNeedle, 1.85f));
        return out;
    }

    private static List<Line3D> buildDynamicMeropenemSyringe(float remainingRatio, float selectedDoseRatio) {
        List<Line3D> out = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        int cBarrelGlass = 0xDD90CAF9;
        int cMeroLiquid  = 0xFF00E5FF;
        int cDosePulse   = 0xFFFFD54F;
        int cGoldRing    = 0xFFFFCA28;
        int cPlungerRod  = 0xFFE3F2FD;
        int cStopper     = 0xFF1565C0;
        int cNeedle      = 0xFFFFFFFF;

        float barrelTop = -0.34f;
        float barrelBot = 0.46f;
        float barrelRad = 0.22f;

        out.add(new Line3D(-0.46f, barrelTop, 0.0f, 0.46f, barrelTop, 0.0f, cGoldRing, 2.0f));
        addRingY(out, 0.26f, barrelTop, 12, cGoldRing, 1.8f);
        addCylinderY(out, barrelRad, barrelTop, barrelBot, 12, cBarrelGlass, 0x9964B5F6, 1.35f);
        addRingY(out, barrelRad + 0.015f, barrelTop + 0.07f, 12, cGoldRing, 1.9f);
        addRingY(out, barrelRad + 0.015f, (barrelTop + barrelBot) * 0.5f, 12, 0xFFFF7043, 1.8f);
        addRingY(out, barrelRad + 0.015f, barrelBot - 0.06f, 12, cGoldRing, 1.9f);

        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float stopperY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.04f, barrelTop + 0.06f);
        float rodTopY = stopperY - 0.54f;

        addRingY(out, 0.24f, rodTopY, 10, cGoldRing, 1.9f);
        out.add(new Line3D(0.0f, rodTopY, 0.0f, 0.0f, stopperY, 0.0f, cPlungerRod, 1.8f));
        addRingY(out, barrelRad * 0.86f, stopperY, 10, cStopper, 1.8f);

        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            float afterInjectY = Mth.lerp(effectivePlungerRatio, barrelBot - 0.03f, barrelTop + 0.06f);
            int rings = 6;
            for (int r = 0; r <= rings; r++) {
                float ry = Mth.lerp(r / (float) rings, liqTopY, barrelBot - 0.03f);
                boolean inSelectedSlice = (ry <= afterInjectY + 0.005f) && (selectedDoseRatio > 0.005f);
                int col = inSelectedSlice ? cDosePulse : cMeroLiquid;
                addRingY(out, barrelRad * 0.80f, ry, 10, col, inSelectedSlice ? 1.85f : 1.35f);
            }
        }

        addCylinderY(out, 0.10f, barrelBot, barrelBot + 0.14f, 8, cGoldRing, cBarrelGlass, 1.4f);
        out.add(new Line3D(0.0f, barrelBot + 0.14f, 0.0f, 0.0f, barrelBot + 0.46f, 0.0f, cNeedle, 1.85f));
        return out;
    }

    private static void buildActivatedCharcoalBottle(List<Line3D> out) {
        int cCharcoal = 0xFF78909C;
        int cYellow   = 0xFFFFCA28;
        int cRed      = 0xFFFF5252;
        int cGreen    = 0xFF00E676;

        addCylinderY(out, 0.44f, -0.18f, 0.66f, 14, cCharcoal, 0xFF455A64, 1.5f);
        addRingY(out, 0.45f, 0.05f, 14, cRed, 1.8f);
        addRingY(out, 0.45f, 0.42f, 14, cGreen, 1.7f);
        addCylinderY(out, 0.28f, -0.62f, -0.26f, 12, cYellow, 0xFFFFB300, 1.65f);
    }

    private static void buildPovidoneIodineSpray(List<Line3D> out) {
        int cAmber = 0xFFFF8F00;
        int cBrown = 0xFFD84315;
        int cWhite = 0xFFE0F7FA;

        addCylinderY(out, 0.38f, -0.10f, 0.68f, 14, cAmber, cBrown, 1.5f);
        addRingY(out, 0.39f, 0.15f, 14, 0xFFFFD54F, 1.8f);
        addCylinderY(out, 0.22f, -0.58f, -0.10f, 10, cWhite, cWhite, 1.5f);
        out.add(new Line3D(0.0f, -0.44f, -0.22f, 0.0f, -0.44f, -0.38f, cBrown, 2.2f));
    }

    private static void buildAmoxicillinBottle(List<Line3D> out) {
        int cWhite = 0xFFE1F5FE;
        int cBlue  = 0xFF2979FF;
        int cCyan  = 0xFF00E5FF;

        addCylinderY(out, 0.42f, -0.14f, 0.66f, 14, cWhite, cCyan, 1.5f);
        addRingY(out, 0.43f, 0.08f, 14, cBlue, 1.8f);
        addRingY(out, 0.43f, 0.40f, 14, cCyan, 1.8f);
        addCylinderY(out, 0.28f, -0.58f, -0.22f, 12, cBlue, cCyan, 1.65f);
    }

    private static void buildMedicineBottle(List<Line3D> out) {
        addCylinderY(out, 0.46f, -0.15f, 0.68f, 14, 0xFF29B6F6, 0xFF0288D1, 1.5f);
        addCylinderY(out, 0.28f, -0.62f, -0.34f, 12, 0xFFFFCA28, 0xFFFFB300, 1.6f);
    }

    private static void buildDecompressionNeedle(List<Line3D> out) {
        int cRedCap   = 0xFFFF1744;
        int cValve    = 0xFF00E5FF;
        int cSlateHub = 0xFF90A4AE;
        int cGoldRing = 0xFFFFD54F;
        int cCatheter = 0xFFB2EBF2;
        int cSteel    = 0xFFFFFFFF;

        // 1. 顶部战术红防尘击发帽与单向排气顶阀 (-0.82 ~ -0.54)
        addCylinderY(out, 0.16f, -0.82f, -0.54f, 10, cRedCap, cRedCap, 1.55f);
        addRingY(out, 0.10f, -0.85f, 8, cSteel, 1.35f);

        // 2. 透明聚碳酸酯单向活瓣回气腔 (-0.54 ~ -0.18)
        addCylinderY(out, 0.19f, -0.54f, -0.18f, 12, cValve, 0xAA40C4FF, 1.45f);
        addRingY(out, 0.14f, -0.36f, 10, 0xFF00B0FF, 1.85f);

        // 3. 防滑翼形指托、14G 红色标识环与金色鲁尔锁座 (-0.18 ~ 0.12)
        out.add(new Line3D(-0.36f, -0.16f, 0.0f, 0.36f, -0.16f, 0.0f, cSlateHub, 1.9f));
        addCylinderY(out, 0.18f, -0.16f, 0.04f, 10, cSlateHub, cSlateHub, 1.4f);
        addRingY(out, 0.195f, -0.06f, 10, cRedCap, 1.8f);
        addCylinderY(out, 0.12f, 0.04f, 0.14f, 8, cGoldRing, cGoldRing, 1.5f);

        // 4. 带厘米深度刻度的长效留置套管与 14G 穿刺钢针 (0.14 ~ 0.92)
        addCylinderY(out, 0.065f, 0.14f, 0.68f, 8, cCatheter, 0x9980DEEA, 1.35f);
        for (int k = 1; k <= 4; k++) {
            float ty = Mth.lerp(k / 5.0f, 0.18f, 0.64f);
            addRingY(out, 0.072f, ty, 6, cSteel, 1.2f);
        }
        out.add(new Line3D(0.0f, 0.14f, 0.0f, 0.0f, 0.92f, 0.0f, cSteel, 1.95f));
    }

    private static void buildChestDrainKit(List<Line3D> out) {
        int cBottle = 0xFF80DEEA;
        int cWater  = 0xFF00B0FF;
        int cBlood  = 0xFFFF1744;
        int cTube   = 0xFFE0F7FA;

        // 三腔胸腔水封引流瓶外框与双隔板
        float x0 = -0.58f, x1 = 0.58f, y0 = -0.48f, y1 = 0.52f, z0 = -0.28f, z1 = 0.28f;
        out.add(new Line3D(x0, y0, z0, x1, y0, z0, cBottle, 1.5f));
        out.add(new Line3D(x1, y0, z0, x1, y0, z1, cBottle, 1.5f));
        out.add(new Line3D(x1, y0, z1, x0, y0, z1, cBottle, 1.5f));
        out.add(new Line3D(x0, y0, z1, x0, y0, z0, cBottle, 1.5f));
        out.add(new Line3D(x0, y1, z0, x1, y1, z0, cBottle, 1.5f));
        out.add(new Line3D(x0, y0, z0, x0, y1, z0, cBottle, 1.5f));
        out.add(new Line3D(x1, y0, z0, x1, y1, z0, cBottle, 1.5f));
        out.add(new Line3D(-0.19f, y0, 0.0f, -0.19f, y1, 0.0f, cBottle, 1.3f));
        out.add(new Line3D( 0.19f, y0, 0.0f,  0.19f, y1, 0.0f, cBottle, 1.3f));

        // 水封液位与积血腔液位
        out.add(new Line3D(-0.54f, 0.12f, 0.0f, -0.22f, 0.12f, 0.0f, cWater, 2.0f));
        out.add(new Line3D(-0.16f, 0.24f, 0.0f,  0.16f, 0.24f, 0.0f, cWater, 2.0f));
        out.add(new Line3D( 0.22f, 0.02f, 0.0f,  0.54f, 0.02f, 0.0f, cBlood, 2.2f));

        // 顶部提手与右侧盘绕硅胶引流管
        out.add(new Line3D(-0.36f, -0.48f, 0.0f, -0.36f, -0.68f, 0.0f, cTube, 1.6f));
        out.add(new Line3D( 0.36f, -0.48f, 0.0f,  0.36f, -0.68f, 0.0f, cTube, 1.6f));
        out.add(new Line3D(-0.36f, -0.68f, 0.0f,  0.36f, -0.68f, 0.0f, cTube, 1.8f));
        addCylinderY(out, 0.12f, -0.38f, 0.42f, 8, cTube, cWater, 1.4f);
    }

    private static void buildOrthopedicKit(List<Line3D> out) {
        int cCase   = 0xFF546E7A;
        int cCyan   = 0xFF00E5FF;
        int cGold   = 0xFFFFD600;
        int cSteel  = 0xFFECEFF1;

        // 1. 钛灰硬壳手术箱底座与翻盖
        float x0 = -0.76f, x1 = 0.76f, y0 = 0.06f, y1 = 0.36f, z0 = -0.52f, z1 = 0.52f;
        out.add(new Line3D(x0, y0, z0, x1, y0, z0, cCase, 1.5f));
        out.add(new Line3D(x1, y0, z0, x1, y0, z1, cCase, 1.5f));
        out.add(new Line3D(x1, y0, z1, x0, y0, z1, cCase, 1.5f));
        out.add(new Line3D(x0, y0, z1, x0, y0, z0, cCase, 1.5f));
        out.add(new Line3D(x0, y1, z0, x1, y1, z0, cCase, 1.5f));
        out.add(new Line3D(x1, y1, z0, x1, y1, z1, cCase, 1.5f));
        out.add(new Line3D(x1, y1, z1, x0, y1, z1, cCase, 1.5f));
        out.add(new Line3D(x0, y1, z1, x0, y1, z0, cCase, 1.5f));

        // 2. 双杆钛合金外固定支架 (Ex-Fix Frame) + 4 枚 Schanz 骨螺钉
        out.add(new Line3D(-0.58f, -0.18f, -0.12f, 0.58f, -0.18f, -0.12f, cCyan, 2.0f));
        out.add(new Line3D(-0.58f, -0.06f,  0.12f, 0.58f, -0.06f,  0.12f, cCyan, 2.0f));
        for (int i = 0; i < 4; i++) {
            float px = -0.45f + i * 0.30f;
            out.add(new Line3D(px, -0.26f, -0.18f, px, 0.14f, 0.18f, cSteel, 1.7f));
            addRingY(out, 0.07f, -0.18f, 8, cGold, 1.5f);
        }

        // 3. 镍钛记忆合金肋骨环抱器 (NiTi Rib Claw)
        out.add(new Line3D(-0.48f, -0.44f, -0.42f, 0.48f, -0.44f, -0.42f, cGold, 1.8f));
        for (int c = -2; c <= 2; c++) {
            float cx = c * 0.18f;
            out.add(new Line3D(cx, -0.56f, -0.44f, cx, -0.32f, -0.38f, cCyan, 1.5f));
        }
    }

    private static void buildFasciotomyKit(List<Line3D> out) {
        int cCase  = 0xFFFFB300;
        int cCyan  = 0xFF00E5FF;
        int cGold  = 0xFFFFEA00;
        int cRed   = 0xFFFF2A4B;
        int cWhite = 0xFFF0F8FF;

        // 下层战术减压手术托盘外框
        out.add(new Line3D(-0.66f, 0.35f, -0.44f,  0.66f, 0.35f, -0.44f, cCase, 1.6f));
        out.add(new Line3D(-0.66f, 0.35f,  0.44f,  0.66f, 0.35f,  0.44f, cCase, 1.6f));
        out.add(new Line3D(-0.66f, 0.35f, -0.44f, -0.66f, 0.35f,  0.44f, cCase, 1.6f));
        out.add(new Line3D( 0.66f, 0.35f, -0.44f,  0.66f, 0.35f,  0.44f, cCase, 1.6f));
        out.add(new Line3D(-0.66f, 0.12f, -0.44f,  0.66f, 0.12f, -0.44f, cCase, 1.4f));
        out.add(new Line3D(-0.66f, 0.12f,  0.44f,  0.66f, 0.12f,  0.44f, cCase, 1.4f));
        out.add(new Line3D(-0.66f, 0.12f, -0.44f, -0.66f, 0.12f,  0.44f, cCase, 1.4f));
        out.add(new Line3D( 0.66f, 0.12f, -0.44f,  0.66f, 0.12f,  0.44f, cCase, 1.4f));

        // 翻开式上盖与电子室压表盘框
        out.add(new Line3D(-0.66f, 0.12f, -0.44f, -0.66f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D( 0.66f, 0.12f, -0.44f,  0.66f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D(-0.66f, -0.62f, -0.64f, 0.66f, -0.62f, -0.64f, cCase, 1.6f));
        out.add(new Line3D(-0.42f, -0.44f, -0.54f, -0.06f, -0.44f, -0.54f, cRed, 1.8f));
        out.add(new Line3D(-0.42f, -0.12f, -0.54f, -0.06f, -0.12f, -0.54f, cRed, 1.8f));
        out.add(new Line3D(-0.42f, -0.44f, -0.54f, -0.42f, -0.12f, -0.54f, cRed, 1.8f));
        out.add(new Line3D(-0.06f, -0.44f, -0.54f, -0.06f, -0.12f, -0.54f, cRed, 1.8f));

        // 内部 #15 圆刃解剖手术刀
        out.add(new Line3D(-0.48f, 0.06f, -0.34f, -0.48f, 0.06f, 0.38f, cWhite, 1.8f));
        out.add(new Line3D(-0.54f, 0.06f, -0.34f, -0.42f, 0.06f, -0.18f, cCyan, 1.5f));
        // 内部双极电凝止血镊 (V形双臂 + 金色电极尖端)
        out.add(new Line3D(-0.10f, 0.05f, 0.36f, -0.18f, 0.05f, -0.22f, cCyan, 1.5f));
        out.add(new Line3D(-0.10f, 0.05f, 0.36f, -0.02f, 0.05f, -0.22f, cCyan, 1.5f));
        out.add(new Line3D(-0.18f, 0.05f, -0.22f, -0.14f, 0.05f, -0.36f, cGold, 1.7f));
        out.add(new Line3D(-0.02f, 0.05f, -0.22f, -0.06f, 0.05f, -0.36f, cGold, 1.7f));
        // 硅胶减张鞋带环线圈
        addRingY(out, 0.18f, 0.06f, 12, cGold, 1.6f);
    }

    private static void buildTranexamicAcidInjector(List<Line3D> out) {
        int cViolet = 0xFFE040FB;
        int cGlass  = 0xCCE0F7FA;
        int cLiquid = 0xFFFF80AB;
        int cNeedle = 0xFFF0F8FF;

        // 刻度针筒外管与内部抗纤溶药液柱
        addCylinderY(out, 0.24f, -0.50f, 0.46f, 12, cGlass, cViolet, 1.4f);
        addCylinderY(out, 0.18f, -0.10f, 0.42f, 10, cLiquid, cLiquid, 1.25f);
        // 紫红色止血标识环
        addRingY(out, 0.26f, 0.08f, 12, cViolet, 1.9f);
        addRingY(out, 0.26f, 0.16f, 12, cViolet, 1.9f);
        // 顶部推杆十字柄与指托翼
        out.add(new Line3D(-0.42f, -0.50f, 0.0f, 0.42f, -0.50f, 0.0f, cGlass, 1.8f));
        out.add(new Line3D(0.0f, -0.88f, 0.0f, 0.0f, -0.10f, 0.0f, cNeedle, 1.6f));
        addRingY(out, 0.20f, -0.88f, 10, cViolet, 1.7f);
        // 下端鲁尔锁定头与穿刺针
        addCylinderY(out, 0.10f, 0.46f, 0.62f, 8, cNeedle, cGlass, 1.4f);
        out.add(new Line3D(0.0f, 0.62f, 0.0f, 0.0f, 0.94f, 0.0f, cNeedle, 1.8f));
    }
}