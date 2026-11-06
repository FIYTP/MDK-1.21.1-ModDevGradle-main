package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.client.gui.PainVisualOverlay;
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
 * 3A 医疗用品 3D 素材库 (MedicalAssetLibrary)
 *
 * 功能职责：
 * 1. 提供高精细度参数化 3D 医疗器械模型（卷式纱布绷带、以色列战术加压绷带、盐酸吗啡自动注射器、
 *    枸橼酸芬太尼精密微量注射器、CAT 旋转止血带、通用医用注射器、铝塑骨折夹板、外用消毒瓶/口服药瓶）。
 * 2. 支持在「长按左键人体治疗面板」与「阿片类药物剂量推注滑块面板」中实时渲染 360° 旋转的 3D 立体模型，
 *    并在调节剂量滑块时实时驱动 3D 注射器活塞推杆与内部药液柱液位变化。
 */
public final class MedicalAssetLibrary {

    public enum AssetType {
        BANDAGE_ROLL,       // 3D 卷式医用绷带
        PRESSURE_BANDAGE,   // 3D 以色列战术加压绷带
        MORPHINE_INJECTOR,  // 3D 盐酸吗啡军规自动注射器（琥珀金药液窗 + 战术防滑环 + 顶部橙色击发帽）
        FENTANYL_SYRINGE,   // 3D 枸橼酸芬太尼精密微量注射器（透明微管 + 冰青色药液 + 双翼指托 + 品红高危环 + 十字推杆）
        TOURNIQUET,         // 3D CAT 战术旋转止血带
        SYRINGE,            // 3D 通用医用注射器
        SPLINT,             // 3D 卷式骨折固定夹板
        MEDICINE_BOTTLE     // 3D 外用消毒液 / 口服药瓶
    }

    public static class Line3D {
        public final float x1, y1, z1;
        public final float x2, y2, z2;
        public final int r, g, b, a;
        public final float width;

        public Line3D(float x1, float y1, float z1, float x2, float y2, float z2,
                      int r, int g, int b, int a, float width) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.r = r; this.g = g; this.b = b; this.a = a;
            this.width = width;
        }
    }

    private static final Map<AssetType, List<Line3D>> MESH_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, AssetType> CUSTOM_ITEM_ASSETS = new HashMap<>();

    static {
        MESH_CACHE.put(AssetType.BANDAGE_ROLL, build3DBandageMesh(false));
        MESH_CACHE.put(AssetType.PRESSURE_BANDAGE, build3DBandageMesh(true));
        MESH_CACHE.put(AssetType.MORPHINE_INJECTOR, build3DOpioidSyringeMesh(false, 1.0f, 0.4f));
        MESH_CACHE.put(AssetType.FENTANYL_SYRINGE, build3DOpioidSyringeMesh(true, 1.0f, 0.35f));
        MESH_CACHE.put(AssetType.TOURNIQUET, build3DTourniquetMesh());
        MESH_CACHE.put(AssetType.SYRINGE, build3DSyringeMesh());
        MESH_CACHE.put(AssetType.SPLINT, build3DSplintMesh());
        MESH_CACHE.put(AssetType.MEDICINE_BOTTLE, build3DBottleMesh());
    }

    private MedicalAssetLibrary() {}

    public static void registerCustomItemAsset(ResourceLocation itemId, AssetType assetType) {
        if (itemId != null && assetType != null) {
            CUSTOM_ITEM_ASSETS.put(itemId, assetType);
        }
    }

    public static AssetType resolveAssetType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return AssetType.BANDAGE_ROLL;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CUSTOM_ITEM_ASSETS.containsKey(id)) {
            return CUSTOM_ITEM_ASSETS.get(id);
        }
        if (id != null) {
            String path = id.getPath();
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

    public static void render3DMedicalItem(GuiGraphics g, float centerX, float centerY, float scale,
                                           ItemStack stack, long nowMs) {
        AssetType type = resolveAssetType(stack);
        if (type == AssetType.MORPHINE_INJECTOR || type == AssetType.FENTANYL_SYRINGE) {
            float remRatio = 1.0f;
            if (stack != null && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                remRatio = Mth.clamp((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(), 0.0f, 1.0f);
            }
            render3DOpioidSyringeWithPlunger(g, centerX, centerY, scale, type == AssetType.FENTANYL_SYRINGE, remRatio, 0.0f, nowMs);
            return;
        }
        List<Line3D> lines = MESH_CACHE.getOrDefault(type, MESH_CACHE.get(AssetType.BANDAGE_ROLL));
        renderLineMesh3D(g, lines, centerX, centerY, scale, nowMs);
    }

    /**
     * 在剂量推注滑块界面中实时渲染带动态活塞推杆与药液液位的 3D 吗啡/芬太尼注射器
     *
     * @param isFentanyl        true=枸橼酸芬太尼精密微量注射器，false=盐酸吗啡自动注射器
     * @param remainingRatio    当前注射器内剩余药量占满容量的比例 (0.0 ~ 1.0)
     * @param selectedDoseRatio 玩家在滑块上选定的本次拟推注剂量占满容量的比例 (0.0 ~ remainingRatio)
     */
    public static void render3DOpioidSyringeWithPlunger(GuiGraphics g, float centerX, float centerY, float scale,
                                                        boolean isFentanyl, float remainingRatio, float selectedDoseRatio, long nowMs) {
        List<Line3D> dynamicLines = build3DOpioidSyringeMesh(isFentanyl, remainingRatio, selectedDoseRatio);
        renderLineMesh3D(g, dynamicLines, centerX, centerY, scale, nowMs);
    }

    private static void renderLineMesh3D(GuiGraphics g, List<Line3D> lines, float centerX, float centerY, float scale, long nowMs) {
        if (lines == null || lines.isEmpty()) return;

        float yaw = (nowMs % 7200L) / 7200.0f * ((float) Math.PI * 2.0f);
        float pitch = 0.36f + 0.10f * Mth.sin(nowMs * 0.002f);
        float roll = -0.24f;

        float cy = Mth.cos(yaw), sy = Mth.sin(yaw);
        float cp = Mth.cos(pitch), sp = Mth.sin(pitch);
        float cr = Mth.cos(roll), sr = Mth.sin(roll);

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Line3D ln : lines) {
            float[] p1 = transform3D(ln.x1, ln.y1, ln.z1, centerX, centerY, scale, cy, sy, cp, sp, cr, sr);
            float[] p2 = transform3D(ln.x2, ln.y2, ln.z2, centerX, centerY, scale, cy, sy, cp, sp, cr, sr);

            float depth = Mth.clamp(((p1[2] + p2[2]) * 0.5f + 1.4f) / 2.6f, 0.30f, 1.15f);
            int alpha = Mth.clamp((int) (ln.a * depth), 15, 255);
            float w = ln.width * (0.75f + 0.35f * depth);

            PainVisualOverlay.addQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1], w, ln.r, ln.g, ln.b, alpha, alpha);
        }

        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static float[] transform3D(float x, float y, float z, float cx, float cy, float scale,
                                       float cosY, float sinY, float cosP, float sinP, float cosR, float sinR) {
        float x1 = x * cosR - y * sinR;
        float y1 = x * sinR + y * cosR;
        float x2 = x1 * cosY - z * sinY;
        float z2 = x1 * sinY + z * cosY;
        float y3 = y1 * cosP - z2 * sinP;
        float z3 = y1 * sinP + z2 * cosP;

        float fov = 3.0f / (3.0f - z3 * 0.45f);
        return new float[]{cx + x2 * scale * fov, cy + y3 * scale * fov, z3};
    }

    /**
     * 构建高细节 3D 盐酸吗啡自动注射器 / 枸橼酸芬太尼精密微量注射器网格
     * 包含：
     * - 16 段圆柱玻璃/金属针筒外壁与精密容量刻度环
     * - 内部动态药液柱（区分「推注后保留药液」与「本次滑块选定拟推注高亮药液段」）
     * - 动态随滑块推进的橡胶密封活塞与十字推杆 / 自动击发顶帽
     * - 双侧指托翼 (Finger Flange) 与品红/琥珀毒麻警示环
     * - 前端鲁尔锁 (Luer-Lock) 锥头与超细穿刺针
     */
    private static List<Line3D> build3DOpioidSyringeMesh(boolean isFentanyl, float remainingRatio, float selectedDoseRatio) {
        List<Line3D> list = new ArrayList<>();
        remainingRatio = Mth.clamp(remainingRatio, 0.0f, 1.0f);
        selectedDoseRatio = Mth.clamp(selectedDoseRatio, 0.0f, remainingRatio);

        float barrelTopY = -0.52f;
        float barrelBotY = 0.42f;
        float barrelRadius = isFentanyl ? 0.22f : 0.27f;
        int segs = 16;

        // 配色定义：吗啡为军规钛金+琥珀橙药液；芬太尼为高透冷白玻璃+冰蓝青药液+品红高危环
        int bodyR = isFentanyl ? 185 : 145;
        int bodyG = isFentanyl ? 235 : 165;
        int bodyB = isFentanyl ? 255 : 155;

        int liqR = isFentanyl ? 0 : 255;
        int liqG = isFentanyl ? 225 : 168;
        int liqB = isFentanyl ? 255 : 25;

        int bandR = isFentanyl ? 255 : 255;
        int bandG = isFentanyl ? 35 : 140;
        int bandB = isFentanyl ? 135 : 0;

        // 1. 针筒圆柱上下端环与 4 道容量刻度圈
        int rings = 5;
        for (int r = 0; r <= rings; r++) {
            float y = Mth.lerp(r / (float) rings, barrelTopY, barrelBotY);
            boolean isEnd = (r == 0 || r == rings);
            boolean isHazardRing = (r == 1);
            int cr = isHazardRing ? bandR : bodyR;
            int cg = isHazardRing ? bandG : bodyG;
            int cb = isHazardRing ? bandB : bodyB;
            int ca = isEnd || isHazardRing ? 245 : 135;
            float cw = isEnd || isHazardRing ? 1.25f : 0.75f;

            for (int i = 0; i < segs; i++) {
                float a1 = (i / (float) segs) * Mth.TWO_PI;
                float a2 = ((i + 1) / (float) segs) * Mth.TWO_PI;
                list.add(new Line3D(
                        Mth.cos(a1) * barrelRadius, y, Mth.sin(a1) * barrelRadius,
                        Mth.cos(a2) * barrelRadius, y, Mth.sin(a2) * barrelRadius,
                        cr, cg, cb, ca, cw
                ));
            }
        }

        // 2. 针筒纵向母线棱柱
        for (int i = 0; i < segs; i += 2) {
            float a = (i / (float) segs) * Mth.TWO_PI;
            float x = Mth.cos(a) * barrelRadius;
            float z = Mth.sin(a) * barrelRadius;
            list.add(new Line3D(x, barrelTopY, z, x, barrelBotY, z, bodyR, bodyG, bodyB, 155, 0.8f));
        }

        // 3. 内部药液柱（底部为 barrelBotY，液面高度随 remainingRatio 动态变化）
        if (remainingRatio > 0.01f) {
            float liqTopY = Mth.lerp(remainingRatio, barrelBotY, barrelTopY + 0.06f);
            float afterDoseRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
            float splitY = Mth.lerp(afterDoseRatio, barrelBotY, barrelTopY + 0.06f);
            float liqRad = barrelRadius * 0.76f;

            // 推注后剩余药液段（常规药液色）
            if (afterDoseRatio > 0.01f) {
                for (int i = 0; i < segs; i += 2) {
                    float a = (i / (float) segs) * Mth.TWO_PI;
                    float x = Mth.cos(a) * liqRad;
                    float z = Mth.sin(a) * liqRad;
                    list.add(new Line3D(x, splitY, z, x, barrelBotY, z, liqR, liqG, liqB, 215, 1.15f));
                }
            }
            // 本次滑块选定即将推入体内的药液段（高亮脉冲闪烁色）
            if (selectedDoseRatio > 0.005f) {
                int selR = isFentanyl ? 255 : 255;
                int selG = isFentanyl ? 90 : 230;
                int selB = isFentanyl ? 185 : 90;
                for (int i = 0; i < segs; i++) {
                    float a1 = (i / (float) segs) * Mth.TWO_PI;
                    float a2 = ((i + 1) / (float) segs) * Mth.TWO_PI;
                    list.add(new Line3D(
                            Mth.cos(a1) * liqRad, liqTopY, Mth.sin(a1) * liqRad,
                            Mth.cos(a2) * liqRad, liqTopY, Mth.sin(a2) * liqRad,
                            selR, selG, selB, 250, 1.35f
                    ));
                    if (i % 2 == 0) {
                        float x = Mth.cos(a1) * liqRad;
                        float z = Mth.sin(a1) * liqRad;
                        list.add(new Line3D(x, liqTopY, z, x, splitY, z, selR, selG, selB, 240, 1.3f));
                    }
                }
            }
        }

        // 4. 动态活塞与顶部推杆 / 击发结构（随 selectedDoseRatio 向下推进！）
        float effectivePlungerRatio = Math.max(0.0f, remainingRatio - selectedDoseRatio);
        float plungerY = Mth.lerp(effectivePlungerRatio, barrelBotY - 0.04f, barrelTopY + 0.04f);
        float rodTopY = plungerY - 0.44f;

        // 橡胶密封活塞环
        float stopperRad = barrelRadius * 0.86f;
        for (int i = 0; i < segs; i++) {
            float a1 = (i / (float) segs) * Mth.TWO_PI;
            float a2 = ((i + 1) / (float) segs) * Mth.TWO_PI;
            list.add(new Line3D(
                    Mth.cos(a1) * stopperRad, plungerY, Mth.sin(a1) * stopperRad,
                    Mth.cos(a2) * stopperRad, plungerY, Mth.sin(a2) * stopperRad,
                    255, 255, 255, 240, 1.3f
            ));
        }
        // 十字形推杆与顶部拇指推盘
        list.add(new Line3D(-0.08f, rodTopY, 0.0f, -0.08f, plungerY, 0.0f, 210, 240, 255, 230, 1.1f));
        list.add(new Line3D(0.08f, rodTopY, 0.0f, 0.08f, plungerY, 0.0f, 210, 240, 255, 230, 1.1f));
        list.add(new Line3D(0.0f, rodTopY, -0.08f, 0.0f, plungerY, -0.08f, 210, 240, 255, 230, 1.1f));
        list.add(new Line3D(0.0f, rodTopY, 0.08f, 0.0f, plungerY, 0.08f, 210, 240, 255, 230, 1.1f));

        // 顶部按压圆盘 + 双侧指托翼 (Finger Flange)
        float thumbRad = isFentanyl ? 0.26f : 0.24f;
        for (int i = 0; i < 12; i++) {
            float a1 = (i / 12.0f) * Mth.TWO_PI;
            float a2 = ((i + 1) / 12.0f) * Mth.TWO_PI;
            list.add(new Line3D(
                    Mth.cos(a1) * thumbRad, rodTopY, Mth.sin(a1) * thumbRad,
                    Mth.cos(a2) * thumbRad, rodTopY, Mth.sin(a2) * thumbRad,
                    bandR, bandG, bandB, 250, 1.45f
            ));
        }
        // 指托翼 (位于 barrelTopY)
        float flangeSpan = barrelRadius + 0.22f;
        list.add(new Line3D(-flangeSpan, barrelTopY, -0.10f, flangeSpan, barrelTopY, -0.10f, bodyR, bodyG, bodyB, 235, 1.3f));
        list.add(new Line3D(-flangeSpan, barrelTopY, 0.10f, flangeSpan, barrelTopY, 0.10f, bodyR, bodyG, bodyB, 235, 1.3f));
        list.add(new Line3D(-flangeSpan, barrelTopY, -0.10f, -flangeSpan, barrelTopY, 0.10f, bodyR, bodyG, bodyB, 235, 1.3f));
        list.add(new Line3D(flangeSpan, barrelTopY, -0.10f, flangeSpan, barrelTopY, 0.10f, bodyR, bodyG, bodyB, 235, 1.3f));

        // 5. 底部鲁尔锁金属锥头 (Luer-Lock Hub) 与超细不锈钢穿刺针头
        float hubBotY = barrelBotY + 0.16f;
        float needleTipY = barrelBotY + 0.56f;
        for (int i = 0; i < 8; i++) {
            float a = (i / 8.0f) * Mth.TWO_PI;
            list.add(new Line3D(
                    Mth.cos(a) * (barrelRadius * 0.65f), barrelBotY, Mth.sin(a) * (barrelRadius * 0.65f),
                    Mth.cos(a) * 0.07f, hubBotY, Mth.sin(a) * 0.07f,
                    200, 225, 245, 235, 1.0f
            ));
        }
        list.add(new Line3D(0.0f, hubBotY, 0.0f, 0.0f, needleTipY, 0.0f, 235, 248, 255, 255, 1.35f));

        return list;
    }

    private static List<Line3D> build3DBandageMesh(boolean isPressure) {
        List<Line3D> list = new ArrayList<>();

        int mainR = isPressure ? 110 : 238;
        int mainG = isPressure ? 235 : 246;
        int mainB = isPressure ? 195 : 255;

        int subR = isPressure ? 45 : 175;
        int subG = isPressure ? 175 : 205;
        int subB = isPressure ? 150 : 225;

        int coreR = isPressure ? 90 : 210;
        int coreG = isPressure ? 120 : 170;
        int coreB = isPressure ? 140 : 120;

        float halfLen = 0.62f;
        float outerRad = 0.68f;
        float innerRad = 0.24f;
        int segs = 28;

        int spiralSteps = 72;
        for (int side = -1; side <= 1; side += 2) {
            float z = side * halfLen;
            for (int i = 0; i < spiralSteps; i++) {
                float t1 = i / (float) spiralSteps;
                float t2 = (i + 1) / (float) spiralSteps;
                float ang1 = t1 * ((float) Math.PI * 8.0f);
                float ang2 = t2 * ((float) Math.PI * 8.0f);
                float rad1 = Mth.lerp(t1, innerRad, outerRad);
                float rad2 = Mth.lerp(t2, innerRad, outerRad);

                list.add(new Line3D(
                        Mth.cos(ang1) * rad1, Mth.sin(ang1) * rad1, z,
                        Mth.cos(ang2) * rad2, Mth.sin(ang2) * rad2, z,
                        mainR, mainG, mainB, 215, 0.95f
                ));
            }
            for (int i = 0; i < 20; i++) {
                float a1 = (i / 20.0f) * ((float) Math.PI * 2.0f);
                float a2 = ((i + 1) / 20.0f) * ((float) Math.PI * 2.0f);
                list.add(new Line3D(
                        Mth.cos(a1) * innerRad, Mth.sin(a1) * innerRad, z * 1.06f,
                        Mth.cos(a2) * innerRad, Mth.sin(a2) * innerRad, z * 1.06f,
                        coreR, coreG, coreB, 235, 1.15f
                ));
            }
        }

        int rings = 5;
        for (int r = 0; r <= rings; r++) {
            float z = Mth.lerp(r / (float) rings, -halfLen, halfLen);
            boolean isEnd = (r == 0 || r == rings);
            for (int i = 0; i < segs; i++) {
                float a1 = (i / (float) segs) * ((float) Math.PI * 2.0f);
                float a2 = ((i + 1) / (float) segs) * ((float) Math.PI * 2.0f);
                list.add(new Line3D(
                        Mth.cos(a1) * outerRad, Mth.sin(a1) * outerRad, z,
                        Mth.cos(a2) * outerRad, Mth.sin(a2) * outerRad, z,
                        isEnd ? mainR : subR, isEnd ? mainG : subG, isEnd ? mainB : subB,
                        isEnd ? 245 : 140, isEnd ? 1.25f : 0.7f
                ));
            }
        }
        for (int i = 0; i < segs; i += 2) {
            float a = (i / (float) segs) * ((float) Math.PI * 2.0f);
            float x = Mth.cos(a) * outerRad;
            float y = Mth.sin(a) * outerRad;
            list.add(new Line3D(x, y, -halfLen, x, y, halfLen, subR, subG, subB, 130, 0.65f));
        }

        int tailSteps = 8;
        float prevTx = outerRad;
        float prevTy = 0.0f;
        for (int s = 1; s <= tailSteps; s++) {
            float t = s / (float) tailSteps;
            float tx = outerRad + t * 0.28f + Mth.sin(t * 4.5f) * 0.08f;
            float ty = t * 0.78f;
            float tzSpan = halfLen * (0.88f - 0.10f * t);

            list.add(new Line3D(prevTx, prevTy, -tzSpan, tx, ty, -tzSpan, mainR, mainG, mainB, 220, 1.0f));
            list.add(new Line3D(prevTx, prevTy, tzSpan, tx, ty, tzSpan, mainR, mainG, mainB, 220, 1.0f));
            list.add(new Line3D(tx, ty, -tzSpan, tx, ty, tzSpan, subR, subG, subB, 175, 0.8f));

            prevTx = tx;
            prevTy = ty;
        }

        if (!isPressure) {
            float cyTop = -outerRad - 0.03f;
            list.add(new Line3D(-0.18f, cyTop, 0.0f, 0.18f, cyTop, 0.0f, 255, 35, 55, 250, 1.8f));
            list.add(new Line3D(0.0f, cyTop, -0.18f, 0.0f, cyTop, 0.18f, 255, 35, 55, 250, 1.8f));
        } else {
            float barY = -outerRad - 0.14f;
            list.add(new Line3D(-0.32f, -outerRad, -0.45f, -0.22f, barY, -0.45f, 255, 200, 40, 250, 1.5f));
            list.add(new Line3D(-0.22f, barY, -0.45f, 0.28f, barY, -0.45f, 255, 200, 40, 250, 1.6f));
            list.add(new Line3D(-0.22f, barY, 0.45f, 0.28f, barY, 0.45f, 255, 200, 40, 250, 1.6f));
            list.add(new Line3D(-0.22f, barY, -0.45f, -0.22f, barY, 0.45f, 255, 200, 40, 250, 1.6f));
            list.add(new Line3D(0.28f, barY, -0.45f, 0.28f, barY, 0.45f, 255, 200, 40, 250, 1.6f));
        }

        return list;
    }

    private static List<Line3D> build3DTourniquetMesh() {
        List<Line3D> list = new ArrayList<>();
        int segs = 24;
        float rad = 0.65f;
        for (int i = 0; i < segs; i++) {
            float a1 = (i / (float) segs) * ((float) Math.PI * 2.0f);
            float a2 = ((i + 1) / (float) segs) * ((float) Math.PI * 2.0f);
            list.add(new Line3D(Mth.cos(a1) * rad, Mth.sin(a1) * rad, -0.25f, Mth.cos(a2) * rad, Mth.sin(a2) * rad, -0.25f, 255, 145, 0, 240, 1.3f));
            list.add(new Line3D(Mth.cos(a1) * rad, Mth.sin(a1) * rad, 0.25f, Mth.cos(a2) * rad, Mth.sin(a2) * rad, 0.25f, 255, 145, 0, 240, 1.3f));
        }
        list.add(new Line3D(-0.75f, -rad - 0.15f, 0.0f, 0.75f, -rad - 0.15f, 0.0f, 255, 40, 40, 255, 2.0f));
        return list;
    }

    private static List<Line3D> build3DSyringeMesh() {
        return build3DOpioidSyringeMesh(false, 0.75f, 0.0f);
    }

    private static List<Line3D> build3DSplintMesh() {
        List<Line3D> list = new ArrayList<>();
        list.add(new Line3D(-0.45f, -0.75f, -0.2f, -0.45f, 0.75f, -0.2f, 0, 255, 140, 235, 1.5f));
        list.add(new Line3D(0.45f, -0.75f, -0.2f, 0.45f, 0.75f, -0.2f, 0, 255, 140, 235, 1.5f));
        list.add(new Line3D(-0.45f, -0.5f, -0.2f, 0.45f, -0.5f, -0.2f, 0, 220, 255, 200, 1.1f));
        list.add(new Line3D(-0.45f, 0.0f, -0.2f, 0.45f, 0.0f, -0.2f, 0, 220, 255, 200, 1.1f));
        list.add(new Line3D(-0.45f, 0.5f, -0.2f, 0.45f, 0.5f, -0.2f, 0, 220, 255, 200, 1.1f));
        return list;
    }

    private static List<Line3D> build3DBottleMesh() {
        List<Line3D> list = new ArrayList<>();
        list.add(new Line3D(-0.35f, -0.35f, 0.0f, 0.35f, -0.35f, 0.0f, 0, 230, 255, 230, 1.3f));
        list.add(new Line3D(-0.35f, 0.65f, 0.0f, 0.35f, 0.65f, 0.0f, 0, 230, 255, 230, 1.3f));
        list.add(new Line3D(-0.35f, -0.35f, 0.0f, -0.35f, 0.65f, 0.0f, 0, 230, 255, 230, 1.3f));
        list.add(new Line3D(0.35f, -0.35f, 0.0f, 0.35f, 0.65f, 0.0f, 0, 230, 255, 230, 1.3f));
        list.add(new Line3D(-0.18f, -0.65f, 0.0f, 0.18f, -0.65f, 0.0f, 255, 200, 40, 245, 1.5f));
        return list;
    }
}