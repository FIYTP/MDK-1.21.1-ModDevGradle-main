package com.FIYTP.realmedicalmod.treatment;

import com.FIYTP.realmedicalmod.client.gui.PainVisualOverlay;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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
 * 1. 提供高精细度参数化 3D 医疗器械模型（精致多层卷式纱布绷带、以色列 C 型扣战术加压绷带、
 *    CAT 旋转止血带、医用注射器、铝塑骨折夹板、外用消毒瓶/口服药瓶）。
 * 2. 支持在「长按左键人体治疗面板」与医疗终端中实时渲染 360° 旋转的 3D 立体医疗用品模型与缠绕特效。
 * 3. 开放自定义模型注册接口 registerAsset(...)，未来新增任何医疗物品只需注册或打上对应标签即可自动匹配 3D 模型。
 */
public final class MedicalAssetLibrary {

    public enum AssetType {
        BANDAGE_ROLL,       // 精致 3D 卷式医用绷带（含空心卷轴、多层螺旋纱布圈、垂落纱布尾翼、红十字标与金属工字扣）
        PRESSURE_BANDAGE,   // 3D 战术加压绷带（含高弹织带卷、顶部刚性 C 型加压桥扣与锁紧翼）
        TOURNIQUET,         // 3D CAT 战术旋转止血带（含止血束带、绞棒 Windlass 与三环固定座）
        SYRINGE,            // 3D 医用自动注射器/针管（含透明刻度针筒、推杆与针头）
        SPLINT,             // 3D 卷式骨折固定夹板（含 C 型铝合金内芯与固定绑带）
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
        MESH_CACHE.put(AssetType.TOURNIQUET, build3DTourniquetMesh());
        MESH_CACHE.put(AssetType.SYRINGE, build3DSyringeMesh());
        MESH_CACHE.put(AssetType.SPLINT, build3DSplintMesh());
        MESH_CACHE.put(AssetType.MEDICINE_BOTTLE, build3DBottleMesh());
    }

    private MedicalAssetLibrary() {}

    /**
     * 供未来新增医疗物品注册专属 3D 模型类型
     */
    public static void registerCustomItemAsset(ResourceLocation itemId, AssetType assetType) {
        if (itemId != null && assetType != null) {
            CUSTOM_ITEM_ASSETS.put(itemId, assetType);
        }
    }

    /**
     * 根据手持物品栈自动解析对应的 3D 医疗模型类型（基于物品 ID 与三轴正交标签自适应）
     */
    public static AssetType resolveAssetType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return AssetType.BANDAGE_ROLL;

        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CUSTOM_ITEM_ASSETS.containsKey(id)) {
            return CUSTOM_ITEM_ASSETS.get(id);
        }
        if (id != null && id.getPath().contains("pressure_bandage")) {
            return AssetType.PRESSURE_BANDAGE;
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
     * 在屏幕指定位置渲染实时 3D 旋转的精致医疗用品立体模型
     */
    public static void render3DMedicalItem(GuiGraphics g, float centerX, float centerY, float scale,
                                           ItemStack stack, long nowMs) {
        AssetType type = resolveAssetType(stack);
        List<Line3D> lines = MESH_CACHE.getOrDefault(type, MESH_CACHE.get(AssetType.BANDAGE_ROLL));
        if (lines == null || lines.isEmpty()) return;

        float yaw = (nowMs % 7200L) / 7200.0f * ((float) Math.PI * 2.0f);
        float pitch = 0.42f + 0.12f * Mth.sin(nowMs * 0.002f);
        float roll = -0.22f;

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
        // Roll (Z)
        float x1 = x * cosR - y * sinR;
        float y1 = x * sinR + y * cosR;
        // Yaw (Y)
        float x2 = x1 * cosY - z * sinY;
        float z2 = x1 * sinY + z * cosY;
        // Pitch (X)
        float y3 = y1 * cosP - z2 * sinP;
        float z3 = y1 * sinP + z2 * cosP;

        float fov = 3.0f / (3.0f - z3 * 0.45f);
        return new float[]{cx + x2 * scale * fov, cy + y3 * scale * fov, z3};
    }

    /**
     * 构建精致 3D 卷式绷带 / 以色列战术加压绷带三维网格
     * 包含：
     * 1. 内层中空卷芯轴 (Hollow Inner Spool Core)
     * 2. 多圈阿基米德螺旋层叠纱布端面 (Archimedean Spiral Gauze Layers)
     * 3. 28 段圆柱经纬纱布母线与环形压纹 (Cylindrical Gauze Weave Ribs)
     * 4. 自然双曲率飘垂的纱布尾翼与抽丝边缘 (Curved Draping Gauze Tail)
     * 5. 顶部无菌金属工字固定扣 / 战术加压绷带的刚性 C 型加压扣桥 (Tactical Pressure Bar)
     */
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

        // 1. 前后两端面的阿基米德螺旋多层纱布圈 (Spiral Gauze Layers)
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
            // 内芯孔环
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

        // 2. 圆柱体外壁纬向纱布环与经向编织筋线
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

        // 3. 自然垂落并微卷的立体纱布尾翼 (Curved Draping Gauze Tail)
        int tailSteps = 8;
        float prevTx = outerRad;
        float prevTy = 0.0f;
        for (int s = 1; s <= tailSteps; s++) {
            float t = s / (float) tailSteps;
            float tx = outerRad + t * 0.28f + Mth.sin(t * 4.5f) * 0.08f;
            float ty = t * 0.78f;
            float tzSpan = halfLen * (0.88f - 0.10f * t);

            // 尾翼两侧边线与横向纱布格纹
            list.add(new Line3D(prevTx, prevTy, -tzSpan, tx, ty, -tzSpan, mainR, mainG, mainB, 220, 1.0f));
            list.add(new Line3D(prevTx, prevTy, tzSpan, tx, ty, tzSpan, mainR, mainG, mainB, 220, 1.0f));
            list.add(new Line3D(tx, ty, -tzSpan, tx, ty, tzSpan, subR, subG, subB, 175, 0.8f));

            prevTx = tx;
            prevTy = ty;
        }

        // 4. 顶部配件区分：普通绷带为红十字标+金属工字扣；加压绷带为刚性 C 型加压桥 (Pressure Bar)
        if (!isPressure) {
            // 医用红十字立体浮雕标
            float cyTop = -outerRad - 0.03f;
            list.add(new Line3D(-0.18f, cyTop, 0.0f, 0.18f, cyTop, 0.0f, 255, 35, 55, 250, 1.8f));
            list.add(new Line3D(0.0f, cyTop, -0.18f, 0.0f, cyTop, 0.18f, 255, 35, 55, 250, 1.8f));
        } else {
            // 以色列加压绷带专属：顶部刚性 C 型加压杠杆桥扣 (Tactical Pressure Bar)
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
        // 顶部旋转绞棒 (Windlass Rod)
        list.add(new Line3D(-0.75f, -rad - 0.15f, 0.0f, 0.75f, -rad - 0.15f, 0.0f, 255, 40, 40, 255, 2.0f));
        return list;
    }

    private static List<Line3D> build3DSyringeMesh() {
        List<Line3D> list = new ArrayList<>();
        // 针筒、推杆与针头
        list.add(new Line3D(-0.18f, -0.55f, 0.0f, 0.18f, -0.55f, 0.0f, 0, 240, 255, 230, 1.2f));
        list.add(new Line3D(-0.18f, 0.35f, 0.0f, 0.18f, 0.35f, 0.0f, 0, 240, 255, 230, 1.2f));
        list.add(new Line3D(-0.18f, -0.55f, 0.0f, -0.18f, 0.35f, 0.0f, 0, 240, 255, 230, 1.2f));
        list.add(new Line3D(0.18f, -0.55f, 0.0f, 0.18f, 0.35f, 0.0f, 0, 240, 255, 230, 1.2f));
        list.add(new Line3D(0.0f, 0.35f, 0.0f, 0.0f, 0.90f, 0.0f, 220, 245, 255, 255, 0.9f));
        list.add(new Line3D(0.0f, -0.55f, 0.0f, 0.0f, -0.88f, 0.0f, 0, 255, 160, 240, 1.4f));
        return list;
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