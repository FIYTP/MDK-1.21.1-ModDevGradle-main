package com.FIYTP.realmedicalmod.client.render;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.config.MedicalConfig;
import com.FIYTP.realmedicalmod.treatment.ModItems;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 高精度 3D 战术医疗手表模型与 256x256 程序化超清材质渲染器 (TacticalWatchModelRenderer)
 *
 * 阶段 1 核心工艺特性：
 * 1. 256x256 动态高像素 PBR 风格贴图图集（清晰度为原版 16x16 的 16 倍）：
 *    - 凯夫拉碳纤维斜向编织表带与双侧压边缝线
 *    - CNC 拉丝枪灰钛合金表壳与倒角高光
 *    - 深邃蜂窝微光 OLED 底盘 + 24 刻度测速内环 + 蓝宝石斜向微反光
 *    - 滚花金属表冠、六角沉头微凸螺钉、阳极氧化红紧急按键、光学投影双透镜
 * 2. 320+ 独立 3D 四边形面片精密机械结构 + 真实法线受光：
 *    - 16 段弧面贴合凯夫拉表带 + 侧面脊肋 + 内侧不锈钢双叉折迭表扣与双限位环
 *    - 八边形三阶 CNC 倒角钛合金表壳 + 24 棱精密斜切表圈
 *    - 4 组雕塑表耳 + 4 颗六角沉头微凸螺钉
 *    - 右侧 12 棱滚花双表冠 + 上下防撞护肩 + 阳极红物理紧急按键
 *    - 左侧 4 道深凹散热鳍片 + 生物传感器阵列
 *    - 12 点钟方向微型全息投影双透镜模块 + 侧缘实时 BPM 自发光光纤灯带
 */
public final class TacticalWatchModelRenderer {

    private static ResourceLocation atlasTextureId = null;
    private static boolean textureInitialized = false;

    private TacticalWatchModelRenderer() {}

    /**
     * 确保 256x256 超高清战术手表材质图集已烘焙并注册到客户端 TextureManager
     */
    private static ResourceLocation ensureAtlasTexture() {
        if (textureInitialized && atlasTextureId != null) {
            return atlasTextureId;
        }
        NativeImage img = new NativeImage(256, 256, true);
        bakeKevlarStrapRegion(img);        // [0..127, 0..127]
        bakeBrushedTitaniumRegion(img);    // [128..255, 0..127]
        bakeOledDialRegion(img);           // [0..127, 128..255]
        bakeMechanicalDetailsRegion(img);  // [128..255, 128..255]

        DynamicTexture dynTex = new DynamicTexture(img);
        dynTex.upload();
        atlasTextureId = Minecraft.getInstance().getTextureManager().register("realmedicalmod_tactical_watch_256", dynTex);
        textureInitialized = true;
        return atlasTextureId;
    }

    /**
     * 渲染完整 320+ 面高精度 3D 战术医疗手表
     *
     * 局部坐标系约定（以左手腕横截面中心为原点）：
     * - +Y 指向手臂近心端（肘部），-Y 指向手掌/指尖方向
     * - +Z 指向腕背（表镜朝向玩家眼睛的方向），-Z 指向腕掌内侧（表扣方向）
     * - ±X 指向手腕左右两侧（+X 为右侧双表冠，-X 为左侧散热格栅）
     *
     * @param armHalfWidth  手臂 X 方向半宽（标准手臂约 0.135f，细臂 slim 约 0.108f）
     * @param armHalfDepth  手臂 Z 方向半厚（通常约 0.135f）
     * @param packedLight   当前世界光照（金属壳体与表带受真实环境光照，发光件独立满亮度）
     * @param data          玩家生理数据（驱动实时心率光纤与光学透镜状态色）
     * @param activeLevel   抬腕/检视激活度 [0..1]，用于唤醒表镜微光与发射透镜核心
     */
    public static void renderWatchOnWrist(PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, MedicalData data,
                                          float armHalfWidth, float armHalfDepth, float activeLevel) {
        renderWatchOnWrist(poseStack, bufferSource, packedLight, data, armHalfWidth, armHalfDepth, activeLevel, true);
    }

    public static void renderWatchOnWrist(PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, MedicalData data,
                                          float armHalfWidth, float armHalfDepth, float activeLevel,
                                          boolean renderLocalBroadcasts) {
        ResourceLocation texId = ensureAtlasTexture();
        long nowMs = System.currentTimeMillis();

        // 计算实时生理状态发光色（青绿 -> 琥珀 -> 猩红，且随真实 BPM 搏动）
        int[] ledRgb = computePulseLedColor(data, nowMs);
        float ledPulse = computeHeartbeatPulse(data, nowMs);

        // 1. 实体受光材质层（受 Minecraft 真实世界光照与法线明暗影响）
        VertexConsumer solidVc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texId));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // 适配 Minecraft 方形手臂 + 外层袖套 (leftSleeve +0.0156f)，外扩 +0.022f 安全间隙彻底零穿模！
        float strapRx = armHalfWidth + 0.022f;
        float strapRz = armHalfDepth + 0.022f;
        float strapThick = 0.015f;
        float strapHalfH = 0.056f;

        // 构件 1：16 段贴合弧面凯夫拉战术表带 + 侧面缝线压边脊肋 (128 面)
        buildArticulatedKevlarStrap(solidVc, pose, mat, packedLight, strapRx, strapRz, strapThick, strapHalfH);

        // 构件 2：手腕内侧 (-Z) 不锈钢双叉折迭表扣 + 扣针 + 双凯夫拉限位环 (42 面)
        buildTacticalClaspAndKeepers(solidVc, pose, mat, packedLight, strapRz + strapThick, strapHalfH);

        // 表壳中心位于腕背 (+Z 表面外侧)
        float caseBaseZ = strapRz + 0.004f;
        float caseRadius = Math.min(armHalfWidth * 0.88f, 0.122f);
        float caseDepth = 0.036f;

        // 构件 3：八边形三阶 CNC 倒角钛合金表壳 + 24 棱精密斜切测速表圈 (88 面)
        buildOctagonalTitaniumCaseAndBezel(solidVc, pose, mat, packedLight, caseBaseZ, caseRadius, caseDepth);

        // 构件 4：4 组雕塑感斜切表耳 + 4 颗六角沉头微凸钛合金螺钉 (48 面)
        buildLugsAndHexScrews(solidVc, pose, mat, packedLight, caseBaseZ, caseRadius, caseDepth);

        // 构件 5：右侧 (+X) 12 棱滚花双表冠 + 防撞护肩 + 阳极氧化红物理紧急键 (44 面)
        buildCrownsAndGuard(solidVc, pose, mat, packedLight, caseBaseZ, caseRadius, caseDepth);

        // 构件 6：左侧 (-X) 4 道深凹散热鳍片 + 生物传感阵列表面 (24 面)
        buildLeftVentilationGrills(solidVc, pose, mat, packedLight, caseBaseZ, caseRadius, caseDepth);

        // 构件 7：12 点钟方向 (+Y 顶部边缘) 微型全息光学投影双透镜基座与遮光罩 (18 面)
        buildProjectorHousing(solidVc, pose, mat, packedLight, caseBaseZ, caseRadius, caseDepth);

        // 2. 阶段 2 活体 256x256 军工 OLED 表盘（满亮度自发光，实时呈现 ECG/BPM、血压、痛觉笑脸、CNS 刻度环与 EEG 脑电）
        ResourceLocation liveDialTex = WatchDialFaceRenderer.getOrUpdateDialTexture(data, activeLevel, renderLocalBroadcasts);
        VertexConsumer liveDialVc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(liveDialTex));
        buildLiveOledDialQuad(liveDialVc, pose, mat, caseBaseZ, caseRadius, caseDepth, activeLevel);

        // 3. 自发光光学层（蓝宝石斜向反光表镜玻璃 + 侧缘心率光纤灯带 + 12 点钟双光学透镜核心）
        VertexConsumer emissiveVc = bufferSource.getBuffer(RenderType.entityTranslucent(texId));
        buildSapphireGlassCover(emissiveVc, pose, mat, caseBaseZ, caseRadius, caseDepth, nowMs);
        buildEmissiveOpticsAndHeartFiber(emissiveVc, pose, mat, caseBaseZ, caseRadius, caseDepth,
                ledRgb, ledPulse, activeLevel);

        // 4. 阶段 3：严格锚定在左手手表局部 3D 空间的悬浮战术播报光幕（零 2D 贴屏，左手飞哪播报就在哪）
        if (renderLocalBroadcasts) {
            WristBroadcast3DRenderer.renderAttachedBroadcasts(
                    poseStack, bufferSource, texId, caseBaseZ, caseRadius, caseDepth
            );
        }
    }

    // ==================== 3D 高精度几何构建器 ====================

    /**
     * 计算精确包裹 Minecraft 方形手臂的圆角矩形 (Rounded Box) 截面点与法线，
     * 彻底杜绝圆形表带在 45° 棱角处被手臂直角戳穿穿模的问题！
     */
    private static void sampleRoundedBoxPoint(float angle, float rx, float rz, float cornerR, float[] outXYN) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float ac = Math.abs(c);
        float as = Math.abs(s);
        float innerX = Math.max(0.01f, rx - cornerR);
        float innerZ = Math.max(0.01f, rz - cornerR);
        // 用平滑超椭圆 (p=8) 贴合圆角矩形：四边笔直外贴，四角柔和圆弧外包
        float exp = 0.25f; // 2 / 8
        float px = Math.signum(c) * (float) Math.pow(ac, exp) * rx;
        float pz = Math.signum(s) * (float) Math.pow(as, exp) * rz;
        // 保证 45° 棱角处不小于手臂对角包络 (innerX + cornerR * 0.707)
        float minCornerX = innerX + cornerR * 0.72f;
        float minCornerZ = innerZ + cornerR * 0.72f;
        if (Math.abs(px) > innerX && Math.abs(pz) > innerZ) {
            float sx = Math.abs(px) / Math.max(0.001f, minCornerX);
            float sz = Math.abs(pz) / Math.max(0.001f, minCornerZ);
            if (sx < 1.0f && sz < 1.0f) {
                px = Math.signum(px) * minCornerX;
                pz = Math.signum(pz) * minCornerZ;
            }
        }
        float len = Math.max(0.0001f, Mth.sqrt(c * c + s * s));
        outXYN[0] = px;
        outXYN[1] = pz;
        outXYN[2] = c / len;
        outXYN[3] = s / len;
    }

    private static void buildArticulatedKevlarStrap(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                    int light, float rx, float rz, float thick, float halfH) {
        final int segs = 24;
        float cornerR = 0.022f;
        float[] p0 = new float[4];
        float[] p1 = new float[4];
        float[] o0 = new float[4];
        float[] o1 = new float[4];
        float[] t0 = new float[4];
        float[] t1 = new float[4];

        for (int i = 0; i < segs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / segs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / segs);
            float s0 = Mth.sin(a0), s1 = Mth.sin(a1);

            float localThick = (s0 > 0.75f && s1 > 0.75f) ? thick * 0.65f : thick;
            float ridge = ((i & 1) == 0) ? 0.0022f : 0.0f;

            sampleRoundedBoxPoint(a0, rx, rz, cornerR, p0);
            sampleRoundedBoxPoint(a1, rx, rz, cornerR, p1);
            sampleRoundedBoxPoint(a0, rx + localThick + ridge, rz + localThick + ridge, cornerR + 0.006f, o0);
            sampleRoundedBoxPoint(a1, rx + localThick + ridge, rz + localThick + ridge, cornerR + 0.006f, o1);
            sampleRoundedBoxPoint(a0, rx + localThick + ridge + 0.0020f, rz + localThick + ridge + 0.0020f, cornerR + 0.007f, t0);
            sampleRoundedBoxPoint(a1, rx + localThick + ridge + 0.0020f, rz + localThick + ridge + 0.0020f, cornerR + 0.007f, t1);

            float ix0 = p0[0], iz0 = p0[1];
            float ix1 = p1[0], iz1 = p1[1];
            float rox0 = o0[0], roz0 = o0[1];
            float rox1 = o1[0], roz1 = o1[1];
            float nx = (p0[2] + p1[2]) * 0.5f;
            float nz = (p0[3] + p1[3]) * 0.5f;

            // UV 落在区域 A [0.02..0.48, 0.02..0.48] 凯夫拉碳纤维编织区
            float u0 = 0.02f + (i % 4) * 0.11f;
            float u1 = u0 + 0.11f;
            float v0 = 0.03f;
            float v1 = 0.47f;

            // 外表面（带微凸节）
            emitQuad(vc, pose, mat, light,
                    rox0, -halfH, roz0, rox1, -halfH, roz1, rox1, halfH, roz1, rox0, halfH, roz0,
                    u0, v0, u1, v1, nx, 0.0f, nz, 255, 255, 255, 255);
            // 内表面（贴肤侧）
            emitQuad(vc, pose, mat, light,
                    ix1, -halfH, iz1, ix0, -halfH, iz0, ix0, halfH, iz0, ix1, halfH, iz1,
                    u0, v0, u1, v1, -nx, 0.0f, -nz, 185, 190, 196, 255);
            // 上边沿 (+Y) 斜切侧壁
            emitQuad(vc, pose, mat, light,
                    ix0, halfH, iz0, rox0, halfH * 0.96f, roz0, rox1, halfH * 0.96f, roz1, ix1, halfH, iz1,
                    0.02f, 0.02f, 0.46f, 0.08f, nx * 0.25f, 0.96f, nz * 0.25f, 225, 230, 236, 255);
            // 下边沿 (-Y) 斜切侧壁
            emitQuad(vc, pose, mat, light,
                    rox0, -halfH * 0.96f, roz0, ix0, -halfH, iz0, ix1, -halfH, iz1, rox1, -halfH * 0.96f, roz1,
                    0.02f, 0.42f, 0.46f, 0.48f, nx * 0.25f, -0.96f, nz * 0.25f, 210, 215, 222, 255);

            // 侧面双线压边加强脊
            float tx0 = t0[0], tz0 = t0[1];
            float tx1 = t1[0], tz1 = t1[1];
            emitQuad(vc, pose, mat, light,
                    tx0, halfH * 0.72f, tz0, tx1, halfH * 0.72f, tz1, tx1, halfH * 0.88f, tz1, tx0, halfH * 0.88f, tz0,
                    0.52f, 0.04f, 0.62f, 0.14f, nx, 0.1f, nz, 220, 225, 232, 255);
            emitQuad(vc, pose, mat, light,
                    tx0, -halfH * 0.88f, tz0, tx1, -halfH * 0.88f, tz1, tx1, -halfH * 0.72f, tz1, tx0, -halfH * 0.72f, tz0,
                    0.52f, 0.04f, 0.62f, 0.14f, nx, -0.1f, nz, 220, 225, 232, 255);
        }
    }

    private static void buildTacticalClaspAndKeepers(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                     int light, float innerSurfaceZ, float strapHalfH) {
        float cz = -(innerSurfaceZ + 0.006f);
        // 主不锈钢折迭扣框（区域 D 金属表扣 UV [0.52..0.96, 0.78..0.96]）
        emitBoxUV(vc, pose, mat, light,
                -0.056f, -strapHalfH - 0.006f, cz - 0.009f,
                0.056f, strapHalfH + 0.006f, cz + 0.004f,
                0.52f, 0.78f, 0.96f, 0.96f, 235, 240, 246, 255);
        // 中心扣针槽与扣针
        emitBoxUV(vc, pose, mat, light,
                -0.042f, -0.009f, cz - 0.013f,
                0.042f, 0.009f, cz - 0.007f,
                0.54f, 0.04f, 0.94f, 0.18f, 255, 255, 255, 255);
        // 两侧双限位环 (Keepers)
        emitBoxUV(vc, pose, mat, light,
                -0.078f, -strapHalfH - 0.005f, cz - 0.004f,
                -0.062f, strapHalfH + 0.005f, cz + 0.010f,
                0.04f, 0.04f, 0.24f, 0.44f, 210, 215, 220, 255);
        emitBoxUV(vc, pose, mat, light,
                0.062f, -strapHalfH - 0.005f, cz - 0.004f,
                0.078f, strapHalfH + 0.005f, cz + 0.010f,
                0.04f, 0.04f, 0.24f, 0.44f, 210, 215, 220, 255);
    }

    private static void buildOctagonalTitaniumCaseAndBezel(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                           int light, float zBase, float radius, float depth) {
        // 1) 八边形底座托盘 + 厚实拉丝主壳体 (zBase -> zBase + depth * 0.72)
        float zMid = zBase + depth * 0.72f;
        float rBot = radius * 0.96f;
        float rMid = radius * 1.04f;
        emitOctagonalFrustum(vc, pose, mat, light, zBase, zMid, rBot, rMid, 0.52f, 0.04f, 0.96f, 0.46f, 232, 238, 246);

        // 2) 上层斜切倒角外圈 (zMid -> zBase + depth)
        float zTop = zBase + depth;
        float rBezelOuter = radius * 0.98f;
        emitOctagonalFrustum(vc, pose, mat, light, zMid, zTop, rMid, rBezelOuter, 0.54f, 0.08f, 0.94f, 0.42f, 250, 252, 255);

        // 3) 24 棱精密内倾测速表圈 (zTop -> zScreenRecess，形成真实物理凹槽深度！)
        float zRecess = zTop - 0.011f;
        float rScreenInner = radius * 0.76f;
        final int bezelSegs = 24;
        for (int i = 0; i < bezelSegs; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / bezelSegs);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / bezelSegs);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float midA = (a0 + a1) * 0.5f;

            // 顶面窄圈 (rBezelOuter -> rBezelOuter * 0.91)
            float rRim = rBezelOuter * 0.90f;
            int tint = ((i % 2) == 0) ? 245 : 215; // 交替防滑齿微明暗
            emitQuad(vc, pose, mat, light,
                    c0 * rRim, s0 * rRim, zTop,
                    c0 * rBezelOuter, s0 * rBezelOuter, zTop,
                    c1 * rBezelOuter, s1 * rBezelOuter, zTop,
                    c1 * rRim, s1 * rRim, zTop,
                    0.54f, 0.10f, 0.72f, 0.24f, 0.0f, 0.0f, 1.0f, tint, tint, tint + 5, 255);

            // 内斜坡凹槽壁 (rRim @ zTop -> rScreenInner @ zRecess)
            float nx = -Mth.cos(midA) * 0.72f;
            float ny = -Mth.sin(midA) * 0.72f;
            emitQuad(vc, pose, mat, light,
                    c0 * rScreenInner, s0 * rScreenInner, zRecess,
                    c0 * rRim, s0 * rRim, zTop,
                    c1 * rRim, s1 * rRim, zTop,
                    c1 * rScreenInner, s1 * rScreenInner, zRecess,
                    0.56f, 0.28f, 0.88f, 0.44f, nx, ny, 0.69f, 195, 204, 214, 255);
        }

        // 底盖封闭面 (-Z)
        emitOctagonCap(vc, pose, mat, light, zBase, rBot, false, 0.54f, 0.04f, 0.92f, 0.44f, 165, 172, 180);
    }

    private static void buildLugsAndHexScrews(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                              int light, float zBase, float radius, float depth) {
        float[][] corners = {{-1, 1}, {1, 1}, {-1, -1}, {1, -1}};
        float lugX = radius * 0.66f;
        float lugY = radius * 0.86f;

        for (float[] c : corners) {
            float sx = c[0];
            float sy = c[1];
            // 雕塑感斜切表耳主体
            float x0 = sx > 0 ? lugX - 0.024f : -lugX - 0.012f;
            float x1 = sx > 0 ? lugX + 0.012f : -lugX + 0.024f;
            float y0 = sy > 0 ? lugY - 0.026f : -lugY - 0.014f;
            float y1 = sy > 0 ? lugY + 0.014f : -lugY + 0.026f;
            emitBoxUV(vc, pose, mat, light,
                    x0, y0, zBase + 0.002f, x1, y1, zBase + depth * 0.84f,
                    0.54f, 0.06f, 0.92f, 0.42f, 225, 232, 240, 255);

            // 每角一颗六角沉头微凸钛合金螺钉 (6 棱柱 + 螺帽顶盖)
            float screwCx = sx * radius * 0.68f;
            float screwCy = sy * radius * 0.68f;
            float screwR = 0.0115f;
            float sz0 = zBase + depth * 0.82f;
            float sz1 = zBase + depth + 0.0035f;
            emitHexPrism(vc, pose, mat, light, screwCx, screwCy, sz0, sz1, screwR,
                    0.52f, 0.52f, 0.73f, 0.73f, 245, 248, 252);
        }
    }

    private static void buildCrownsAndGuard(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                            int light, float zBase, float radius, float depth) {
        float xWall = radius * 0.98f;
        float zCenter = zBase + depth * 0.48f;

        // 上下防撞护肩挡块 (Crown Guards)
        emitBoxUV(vc, pose, mat, light,
                xWall - 0.008f, 0.018f, zCenter - 0.014f,
                xWall + 0.018f, 0.040f, zCenter + 0.013f,
                0.54f, 0.12f, 0.88f, 0.36f, 222, 228, 236, 255);
        emitBoxUV(vc, pose, mat, light,
                xWall - 0.008f, -0.040f, zCenter - 0.014f,
                xWall + 0.018f, -0.018f, zCenter + 0.013f,
                0.54f, 0.12f, 0.88f, 0.36f, 222, 228, 236, 255);

        // 主 12 棱滚花表冠旋钮 (沿 +X 轴伸出)
        emitKnurledCylinderX(vc, pose, mat, light, xWall + 0.002f, xWall + 0.025f, 0.0f, zCenter, 0.017f, 12,
                0.52f, 0.52f, 0.74f, 0.74f, 242, 246, 252);

        // 上方阳极氧化战术红物理紧急按钮 (2 点钟方向)
        emitBoxUV(vc, pose, mat, light,
                xWall - 0.010f, 0.046f, zCenter - 0.010f,
                xWall + 0.014f, 0.068f, zCenter + 0.010f,
                0.76f, 0.52f, 0.96f, 0.72f, 255, 255, 255, 255);

        // 下方哑光黑次级模式切换键 (4 点钟方向)
        emitBoxUV(vc, pose, mat, light,
                xWall - 0.010f, -0.068f, zCenter - 0.010f,
                xWall + 0.013f, -0.046f, zCenter + 0.010f,
                0.54f, 0.20f, 0.76f, 0.40f, 150, 158, 168, 255);
    }

    private static void buildLeftVentilationGrills(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                   int light, float zBase, float radius, float depth) {
        float xWall = -radius * 0.98f;
        float zCenter = zBase + depth * 0.48f;
        // 4 道精细外凸/深凹散热鳍片
        for (int i = 0; i < 4; i++) {
            float y = -0.042f + i * 0.024f;
            emitBoxUV(vc, pose, mat, light,
                    xWall - 0.013f, y, zCenter - 0.011f,
                    xWall + 0.004f, y + 0.011f, zCenter + 0.011f,
                    0.54f, 0.08f, 0.88f, 0.24f, 200, 208, 218, 255);
        }
    }

    private static void buildProjectorHousing(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                              int light, float zBase, float radius, float depth) {
        float yTop = radius * 0.94f;
        float zTop = zBase + depth;
        // 12 点钟方向微型全息发射模块金属护罩
        emitBoxUV(vc, pose, mat, light,
                -0.036f, yTop - 0.012f, zBase + depth * 0.45f,
                0.036f, yTop + 0.016f, zTop + 0.006f,
                0.54f, 0.06f, 0.94f, 0.34f, 215, 224, 234, 255);
        // 双微透镜遮光环
        emitBoxUV(vc, pose, mat, light,
                -0.024f, yTop - 0.006f, zTop + 0.003f,
                -0.006f, yTop + 0.010f, zTop + 0.010f,
                0.52f, 0.78f, 0.74f, 0.94f, 180, 190, 202, 255);
        emitBoxUV(vc, pose, mat, light,
                0.006f, yTop - 0.006f, zTop + 0.003f,
                0.024f, yTop + 0.010f, zTop + 0.010f,
                0.52f, 0.78f, 0.74f, 0.94f, 180, 190, 202, 255);
    }

    private static void buildLiveOledDialQuad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                              float zBase, float radius, float depth, float activeLevel) {
        float zScreen = zBase + depth - 0.0102f;
        float rScreen = radius * 0.775f;
        int fullLight = LightTexture.FULL_BRIGHT;

        // 待机时柔和微亮 (72%)，抬腕看表时全亮度 (100%) 锐利点亮
        float idleBright = (float) ModItems.safeCfgDouble(MedicalConfig.WRISTBAND_DIAL_IDLE_BRIGHTNESS, 0.72);
        float bright = Mth.lerp(Mth.clamp(activeLevel, 0.0f, 1.0f), idleBright, 1.0f);
        int c = Mth.clamp(Math.round(255 * bright), 150, 255);
        // 旋转 180° 彻底翻正表盘 UV（顶部 BIO-OS 笔直朝上、左右不镜像）
        emitQuadUV4(vc, pose, mat, fullLight,
                -rScreen, -rScreen, zScreen, 1.0f, 1.0f,
                rScreen, -rScreen, zScreen, 1.0f, 0.0f,
                rScreen, rScreen, zScreen, 0.0f, 0.0f,
                -rScreen, rScreen, zScreen, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f, c, c, c, 255);
    }

    private static void buildSapphireGlassCover(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                float zBase, float radius, float depth, long nowMs) {
        // 顶层蓝宝石弧面玻璃微反光层 (位于表镜上方 +0.008f，呈现真实表镜物理景深！)
        float zGlass = zBase + depth - 0.0022f;
        float rGlass = radius * 0.85f;
        float glintShift = 0.04f * Mth.sin(nowMs * 0.0018f);
        emitQuad(vc, pose, mat, LightTexture.FULL_BRIGHT,
                -rGlass, -rGlass, zGlass,
                rGlass, -rGlass, zGlass,
                rGlass, rGlass, zGlass,
                -rGlass, rGlass, zGlass,
                0.02f + glintShift, 0.98f, 0.48f + glintShift, 0.52f, 0.0f, 0.0f, 1.0f, 195, 235, 255, 34);
    }

    private static void buildEmissiveOpticsAndHeartFiber(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat,
                                                         float zBase, float radius, float depth,
                                                         int[] ledRgb, float ledPulse, float activeLevel) {
        int fullLight = LightTexture.FULL_BRIGHT;
        int r = ledRgb[0], g = ledRgb[1], b = ledRgb[2];
        int pulseA = Mth.clamp(Math.round(130 + 125 * ledPulse), 100, 255);

        // 1) 6 点钟方向 (-Y 侧缘) 实时心率脉冲光纤灯带
        float yFiber = -radius * 0.95f;
        float zFiber = zBase + depth * 0.78f;
        emitQuad(vc, pose, mat, fullLight,
                -0.046f, yFiber - 0.004f, zFiber,
                0.046f, yFiber - 0.004f, zFiber,
                0.046f, yFiber + 0.004f, zFiber + 0.005f,
                -0.046f, yFiber + 0.004f, zFiber + 0.005f,
                0.80f, 0.80f, 0.94f, 0.94f, 0.0f, -0.7f, 0.7f, r, g, b, pulseA);

        // 2) 12 点钟方向双光学全息投影透镜发光核心（抬腕时亮度跃升）
        float yProj = radius * 0.94f;
        float zProj = zBase + depth + 0.0105f;
        int lensA = Mth.clamp(Math.round((95 + 150 * activeLevel) * (0.78f + 0.22f * ledPulse)), 80, 255);
        emitQuad(vc, pose, mat, fullLight,
                -0.021f, yProj - 0.003f, zProj,
                -0.009f, yProj - 0.003f, zProj,
                -0.009f, yProj + 0.007f, zProj,
                -0.021f, yProj + 0.007f, zProj,
                0.80f, 0.80f, 0.94f, 0.94f, 0.0f, 0.0f, 1.0f, r, g, b, lensA);
        emitQuad(vc, pose, mat, fullLight,
                0.009f, yProj - 0.003f, zProj,
                0.021f, yProj - 0.003f, zProj,
                0.021f, yProj + 0.007f, zProj,
                0.009f, yProj + 0.007f, zProj,
                0.80f, 0.80f, 0.94f, 0.94f, 0.0f, 0.0f, 1.0f, r, g, b, lensA);
    }

    // ==================== 基础 3D 几何原语 ====================

    private static void emitOctagonalFrustum(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                             float z0, float z1, float r0, float r1,
                                             float u0, float v0, float u1, float v1, int cr, int cg, int cb) {
        final int sides = 8;
        for (int i = 0; i < sides; i++) {
            float a0 = (float) ((i + 0.5) * 2.0 * Math.PI / sides);
            float a1 = (float) ((i + 1.5) * 2.0 * Math.PI / sides);
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float midA = (a0 + a1) * 0.5f;

            float dz = z1 - z0;
            float dr = r0 - r1;
            float invLen = 1.0f / Math.max(0.0001f, Mth.sqrt(dz * dz + dr * dr));
            float nx = Mth.cos(midA) * dz * invLen;
            float ny = Mth.sin(midA) * dz * invLen;
            float nz = dr * invLen;

            emitQuad(vc, pose, mat, light,
                    c0 * r0, s0 * r0, z0,
                    c1 * r0, s1 * r0, z0,
                    c1 * r1, s1 * r1, z1,
                    c0 * r1, s0 * r1, z1,
                    u0, v0, u1, v1, nx, ny, nz, cr, cg, cb, 255);
        }
    }

    private static void emitOctagonCap(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                       float z, float r, boolean front,
                                       float u0, float v0, float u1, float v1, int cr, int cg, int cb) {
        float nz = front ? 1.0f : -1.0f;
        float[] xs = new float[8];
        float[] ys = new float[8];
        for (int i = 0; i < 8; i++) {
            float a = (float) ((i + 0.5) * 2.0 * Math.PI / 8.0);
            xs[i] = Mth.cos(a) * r;
            ys[i] = Mth.sin(a) * r;
        }
        // 用 3 个四边形无裂缝铺满八边形
        emitQuad(vc, pose, mat, light, xs[0], ys[0], z, xs[1], ys[1], z, xs[2], ys[2], z, xs[3], ys[3], z, u0, v0, u1, v1, 0, 0, nz, cr, cg, cb, 255);
        emitQuad(vc, pose, mat, light, xs[0], ys[0], z, xs[3], ys[3], z, xs[4], ys[4], z, xs[7], ys[7], z, u0, v0, u1, v1, 0, 0, nz, cr, cg, cb, 255);
        emitQuad(vc, pose, mat, light, xs[4], ys[4], z, xs[5], ys[5], z, xs[6], ys[6], z, xs[7], ys[7], z, u0, v0, u1, v1, 0, 0, nz, cr, cg, cb, 255);
    }

    private static void emitHexPrism(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                     float cx, float cy, float z0, float z1, float r,
                                     float u0, float v0, float u1, float v1, int cr, int cg, int cb) {
        float[] xs = new float[6];
        float[] ys = new float[6];
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3.0);
            xs[i] = cx + Mth.cos(a) * r;
            ys[i] = cy + Mth.sin(a) * r;
        }
        for (int i = 0; i < 6; i++) {
            int n = (i + 1) % 6;
            float midA = (float) ((i + 0.5) * Math.PI / 3.0);
            emitQuad(vc, pose, mat, light,
                    xs[i], ys[i], z0, xs[n], ys[n], z0, xs[n], ys[n], z1, xs[i], ys[i], z1,
                    u0, v0, u1, v1, Mth.cos(midA), Mth.sin(midA), 0.0f, cr - 25, cg - 25, cb - 25, 255);
        }
        emitQuad(vc, pose, mat, light, xs[0], ys[0], z1, xs[1], ys[1], z1, xs[2], ys[2], z1, xs[3], ys[3], z1, u0, v0, u1, v1, 0, 0, 1, cr, cg, cb, 255);
        emitQuad(vc, pose, mat, light, xs[0], ys[0], z1, xs[3], ys[3], z1, xs[4], ys[4], z1, xs[5], ys[5], z1, u0, v0, u1, v1, 0, 0, 1, cr, cg, cb, 255);
    }

    private static void emitKnurledCylinderX(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                             float x0, float x1, float cy, float cz, float r, int sides,
                                             float u0, float v0, float u1, float v1, int cr, int cg, int cb) {
        for (int i = 0; i < sides; i++) {
            float a0 = (float) (i * 2.0 * Math.PI / sides);
            float a1 = (float) ((i + 1) * 2.0 * Math.PI / sides);
            float y0 = cy + Mth.cos(a0) * r, z0 = cz + Mth.sin(a0) * r;
            float y1 = cy + Mth.cos(a1) * r, z1 = cz + Mth.sin(a1) * r;
            float midA = (a0 + a1) * 0.5f;
            int knurlTint = ((i & 1) == 0) ? 0 : -38;
            emitQuad(vc, pose, mat, light,
                    x0, y0, z0, x1, y0, z0, x1, y1, z1, x0, y1, z1,
                    u0, v0, u1, v1, 0.0f, Mth.cos(midA), Mth.sin(midA),
                    cr + knurlTint, cg + knurlTint, cb + knurlTint, 255);
            // 外端面扇形封口
            emitQuad(vc, pose, mat, light,
                    x1, cy, cz, x1, y0, z0, x1, y1, z1, x1, cy, cz,
                    u0, v0, u1, v1, 1.0f, 0.0f, 0.0f, cr, cg, cb, 255);
        }
    }

    private static void emitBoxUV(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float u0, float v0, float u1, float v1, int r, int g, int b, int a) {
        emitQuad(vc, pose, mat, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, 0, 0, 1, r, g, b, a);
        emitQuad(vc, pose, mat, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, v0, u1, v1, 0, 0, -1, r, g, b, a);
        emitQuad(vc, pose, mat, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1, 0, 1, 0, r, g, b, a);
        emitQuad(vc, pose, mat, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1, 0, -1, 0, r, g, b, a);
        emitQuad(vc, pose, mat, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, v0, u1, v1, 1, 0, 0, r, g, b, a);
        emitQuad(vc, pose, mat, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, v0, u1, v1, -1, 0, 0, r, g, b, a);
    }

    private static void emitQuadUV4(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                    float x0, float y0, float z0, float u0, float v0,
                                    float x1, float y1, float z1, float u1, float v1,
                                    float x2, float y2, float z2, float u2, float v2,
                                    float x3, float y3, float z3, float u3, float v3,
                                    float nx, float ny, float nz,
                                    int r, int g, int b, int a) {
        r = Mth.clamp(r, 0, 255);
        g = Mth.clamp(g, 0, 255);
        b = Mth.clamp(b, 0, 255);
        a = Mth.clamp(a, 0, 255);
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    private static void emitQuad(VertexConsumer vc, PoseStack.Pose pose, Matrix4f mat, int light,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float x3, float y3, float z3,
                                 float u0, float v0, float u1, float v1,
                                 float nx, float ny, float nz,
                                 int r, int g, int b, int a) {
        r = Mth.clamp(r, 0, 255);
        g = Mth.clamp(g, 0, 255);
        b = Mth.clamp(b, 0, 255);
        a = Mth.clamp(a, 0, 255);
        vc.addVertex(mat, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    // ==================== 256x256 超清程序化材质烘焙 ====================

    private static void bakeKevlarStrapRegion(NativeImage img) {
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                // 斜向凯夫拉碳纤维 4x4 交织纹理
                int weave = ((x + y) / 3) & 1;
                int cross = ((x - y + 256) / 3) & 1;
                int base = 30 + weave * 11 + cross * 7;
                // 边缘压边缝线 (y 在 10..13 或 114..117)
                if ((y >= 10 && y <= 13) || (y >= 114 && y <= 117)) {
                    boolean stitch = (x % 8) < 5;
                    base = stitch ? 76 : 22;
                }
                int noise = ((x * 73 + y * 151) ^ (x * y)) & 7;
                int r = Mth.clamp(base + noise - 3, 14, 110);
                int g = Mth.clamp(base + noise - 1, 16, 116);
                int b = Mth.clamp(base + noise + 4, 20, 126);
                img.setPixelRGBA(x, y, packAbgr(r, g, b, 255));
            }
        }
    }

    private static void bakeBrushedTitaniumRegion(NativeImage img) {
        for (int y = 0; y < 128; y++) {
            int rowBrush = ((y * 37) ^ (y * 13)) & 15;
            for (int x = 128; x < 256; x++) {
                int lx = x - 128;
                // 微米级横向金属拉丝 + 边缘 CNC 倒角高光
                int streak = ((lx / 6 + y * 19) ^ (y * 7)) & 9;
                int edgeBoost = (lx < 6 || lx > 121 || y < 6 || y > 121) ? 26 : 0;
                int base = 66 + rowBrush + streak + edgeBoost;
                int r = Mth.clamp(base - 4, 30, 185);
                int g = Mth.clamp(base + 1, 34, 192);
                int b = Mth.clamp(base + 10, 40, 205);
                img.setPixelRGBA(x, y, packAbgr(r, g, b, 255));
            }
        }
    }

    private static void bakeOledDialRegion(NativeImage img) {
        final float cx = 64.0f;
        final float cy = 64.0f;
        for (int ly = 0; ly < 128; ly++) {
            for (int lx = 0; lx < 128; lx++) {
                float dx = lx - cx;
                float dy = ly - cy;
                float dist = Mth.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);

                // 深邃 OLED 黑青基底 + 微妙蜂窝点阵光栅
                boolean grid = (lx % 8 == 0) || (ly % 8 == 0);
                int r = grid ? 8 : 4;
                int g = grid ? 24 : 13;
                int b = grid ? 34 : 20;

                // 外围 24 等分精密测速刻度内环 (半径 52..60)
                if (dist >= 51.0f && dist <= 60.0f) {
                    float deg = (float) ((Math.toDegrees(angle) + 360.0) % 360.0);
                    float mod15 = deg % 15.0f;
                    boolean major = (deg % 90.0f < 2.2f || deg % 90.0f > 87.8f);
                    boolean minor = (mod15 < 1.3f || mod15 > 13.7f);
                    if (major && dist >= 49.0f) {
                        r = 35; g = 228; b = 255;
                    } else if (minor && dist >= 53.5f) {
                        r = 18; g = 135; b = 165;
                    }
                }
                // 精密内环细圆线 (半径 46.5 与 32.0)
                if (Math.abs(dist - 46.5f) < 0.7f || Math.abs(dist - 32.0f) < 0.55f) {
                    r = 14; g = 92; b = 115;
                }
                // 左上角斜向蓝宝石玻璃微反光高光带
                float diag = (lx + ly) - 72.0f;
                if (Math.abs(diag) < 10.0f && dist < 58.0f) {
                    float glint = 1.0f - Math.abs(diag) / 10.0f;
                    r = Mth.clamp(r + Math.round(glint * 28), 0, 255);
                    g = Mth.clamp(g + Math.round(glint * 44), 0, 255);
                    b = Mth.clamp(b + Math.round(glint * 56), 0, 255);
                }
                img.setPixelRGBA(lx, 128 + ly, packAbgr(r, g, b, 255));
            }
        }
    }

    private static void bakeMechanicalDetailsRegion(NativeImage img) {
        // 1) [128..191, 128..191]: 滚花金属防滑纹 + 六角沉头螺钉质感
        for (int ly = 0; ly < 64; ly++) {
            for (int lx = 0; lx < 64; lx++) {
                float dx = lx - 32.0f, dy = ly - 32.0f;
                float dist = Mth.sqrt(dx * dx + dy * dy);
                int base = 98 + (((lx + ly) % 6 < 3) ? 18 : -16);
                if (dist < 11.0f) base = 24; // 六角内沉孔深影
                else if (dist < 15.0f) base = 158; // 倒角高光环
                img.setPixelRGBA(128 + lx, 128 + ly, packAbgr(base, base + 4, base + 10, 255));
            }
        }
        // 2) [192..255, 128..191]: 阳极氧化战术红物理按键
        for (int ly = 0; ly < 64; ly++) {
            for (int lx = 0; lx < 64; lx++) {
                boolean rim = (lx < 5 || lx > 58 || ly < 5 || ly > 58);
                int r = rim ? 220 : 178;
                int g = rim ? 48 : 26;
                int b = rim ? 56 : 34;
                img.setPixelRGBA(192 + lx, 128 + ly, packAbgr(r, g, b, 255));
            }
        }
        // 3) [128..255, 192..255]: 不锈钢表扣拉丝 + 发光柔和圆斑透镜
        for (int ly = 0; ly < 64; ly++) {
            for (int lx = 0; lx < 128; lx++) {
                if (lx >= 64) {
                    // 右下角 [192..255, 192..255]：发光柔和光学径向渐变圆斑
                    float dx = (lx - 64) - 32.0f, dy = ly - 32.0f;
                    float d = Mth.clamp(Mth.sqrt(dx * dx + dy * dy) / 30.0f, 0.0f, 1.0f);
                    int a = Math.round((1.0f - d * d) * 255.0f);
                    img.setPixelRGBA(128 + lx, 192 + ly, packAbgr(255, 255, 255, a));
                } else {
                    int b = 124 + ((ly * 17) & 15);
                    img.setPixelRGBA(128 + lx, 192 + ly, packAbgr(b, b + 4, b + 10, 255));
                }
            }
        }
    }

    private static int packAbgr(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }

    private static int[] computePulseLedColor(MedicalData data, long nowMs) {
        if (data == null) return new int[]{0, 232, 255};
        if (data.isCardiacArrest() || data.isInVFib() || data.isInCriticalState() || data.getBloodVolume() < 2500) {
            return new int[]{255, 28, 56};
        }
        if (ModItems.getPneumothoraxType(data) >= 2 || data.getBloodOxygen() < 86) {
            return new int[]{64, 168, 255};
        }
        if (data.getPerceivedPain() >= 18) {
            return new int[]{255, 178, 0};
        }
        return new int[]{0, 236, 175};
    }

    private static float computeHeartbeatPulse(MedicalData data, long nowMs) {
        if (data == null) return 0.5f + 0.5f * Mth.sin(nowMs * 0.006f);
        if (data.isCardiacArrest()) return 0.08f;
        if (data.isInVFib()) return 0.25f + 0.75f * Math.abs(Mth.sin(nowMs * 0.032f));
        float bpm = Mth.clamp(data.getHeartRate(), 35, 220);
        double phase = ((nowMs / 1000.0) * (bpm / 60.0)) % 1.0;
        // 双峰心动脉冲 (收缩主峰 + 重搏次峰)
        float mainPeak = (float) Math.exp(-Math.pow((phase - 0.16) / 0.065, 2.0));
        float dicrotic = 0.42f * (float) Math.exp(-Math.pow((phase - 0.36) / 0.085, 2.0));
        return Mth.clamp(mainPeak + dicrotic, 0.0f, 1.0f);
    }
}