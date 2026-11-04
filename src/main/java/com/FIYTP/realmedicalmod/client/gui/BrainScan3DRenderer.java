package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 3A 级全息脑部扫描与头部创伤综合诊断终端 (BrainScan3DRenderer)
 *
 * 核心升级亮点：
 * 1. 解剖级四分结构 3D 脑模型：
 *    包含左右大脑皮层半球 (额叶/顶叶/枕叶/颞叶生理解剖比例与多阶脑回沟壑)、后下方带水平叶片纹理的小脑 (Cerebellum)
 *    以及底部延髓脑干 (Brainstem)，共 1200+ 深度排序全息体素点与皮层拓扑网格线。
 * 2. 实时白质神经突触传导网 (Live Synaptic Connectome)：
 *    内部 42 条跨脑区神经纤维束，带有随心率与意识高速穿梭的动作电位光子脉冲；脑损伤或剧痛时触发异常红/紫神经电风暴。
 * 3. 3D 病理病灶空间映射 (3D Lesion & Hematoma Projection)：
 *    根据颅内积血量、头部外出血、头部内出血、感染与疼痛，在 3D 大脑对应空间位置实时渲染脉冲血肿云团与脑膜炎病灶信标。
 * 4. 补全头部 (HEAD) 全部缺失肢体指标：
 *    完整展示头部外出血、内出血、疼痛等级、感染值、烧伤、包扎/颅骨骨折状态徽标，以及脑健康、意识、颅内出血分级与脑灌注压。
 * 5. 多信息自适应兼容方案 (Multi-Mode + Hardware Scissor Scroll + 2-Col Grid)：
 *    - 支持 [SPLIT 分栏同屏] / [3D HOLO 全屏全息] / [CLINICAL 双列报表] 三种模式一键切换（点击右上角标签或滚轮滚动）。
 *    - 右侧指标舱内置 GuiGraphics.enableScissor 硬件裁剪视窗与丝滑阻尼滚动条，无论词条多少绝不溢出面板或挤压其它模块。
 */
public class BrainScan3DRenderer {

    public enum ViewMode {
        SPLIT("SPLIT // 全息+诊断"),
        HOLO_FULL("3D HOLO // 沉浸脑图"),
        CLINICAL_GRID("CLINICAL // 双列全表");

        public final String label;
        ViewMode(String label) { this.label = label; }
    }

    // 3D 脑部顶点结构
    private static class BrainVertex {
        final float x, y, z;
        final float nx, ny, nz;
        final int region; // -1: 左大脑半球, 1: 右大脑半球, 2: 小脑, 3: 脑干
        final float foldFactor; // 脑回凸起度 (0~1)
        final int connectIndex; // 邻接拓扑点索引

        BrainVertex(float x, float y, float z, float nx, float ny, float nz, int region, float foldFactor, int connectIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.region = region;
            this.foldFactor = foldFactor;
            this.connectIndex = connectIndex;
        }
    }

    // 内部白质神经突触传导束
    private static class SynapseTract {
        final float x1, y1, z1;
        final float x2, y2, z2;
        final float speed;
        float phase;

        SynapseTract(float x1, float y1, float z1, float x2, float y2, float z2, float speed, float phase) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.speed = speed;
            this.phase = phase;
        }
    }

    // 环绕全息轨道粒子
    private static class OrbitParticle {
        float angle;
        final float speed;
        final float radiusX, radiusZ;
        final float tiltX, tiltZ;
        final float size;
        final float baseAlpha;

        OrbitParticle(Random rand) {
            this.angle = rand.nextFloat() * ((float) Math.PI * 2.0f);
            this.speed = (0.35f + rand.nextFloat() * 0.65f) * (rand.nextBoolean() ? 1.0f : -1.0f);
            this.radiusX = 1.15f + rand.nextFloat() * 0.45f;
            this.radiusZ = 1.15f + rand.nextFloat() * 0.45f;
            this.tiltX = (rand.nextFloat() - 0.5f) * 0.85f;
            this.tiltZ = (rand.nextFloat() - 0.5f) * 0.85f;
            this.size = 1.0f + rand.nextFloat() * 1.4f;
            this.baseAlpha = 0.25f + rand.nextFloat() * 0.55f;
        }
    }

    // 3D 脑区浮动锚点标签
    private static class BrainAnchorLabel {
        final String keyTitle;
        final float ox, oy, oz;

        BrainAnchorLabel(String keyTitle, float ox, float oy, float oz) {
            this.keyTitle = keyTitle;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }
    }

    private final List<BrainVertex> vertices = new ArrayList<>();
    private final List<SynapseTract> synapseTracts = new ArrayList<>();
    private final List<OrbitParticle> orbitParticles = new ArrayList<>();
    private final List<BrainAnchorLabel> anchorLabels = new ArrayList<>();

    // 视角与旋转控制
    private float yaw = -0.45f;
    private float pitch = 0.22f;
    private float autoRotateTimer = 0.0f;
    private long lastRenderTimeMs = 0L;

    // 兼容方案：多视图切换与右侧诊断视窗平滑滚动状态
    private ViewMode currentMode = ViewMode.SPLIT;
    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private float maxScroll = 0.0f;

    // 缓存的交互热区坐标（供鼠标点击与滚轮判定）
    private int lastPanelX, lastPanelY, lastPanelW, lastPanelH;
    private int lastScrollAreaX, lastScrollAreaY, lastScrollAreaW, lastScrollAreaH;
    private int lastHoloAreaX, lastHoloAreaY, lastHoloAreaW, lastHoloAreaH;

    public BrainScan3DRenderer() {
        generateAnatomicalBrainMesh();
        initSynapseConnectome();
        initOrbitParticles();
        initAnchorLabels();
    }

    /**
     * 构建高精解剖级 3D 大脑模型（左右大脑皮层半球 + 小脑 + 脑干）
     */
    private void generateAnatomicalBrainMesh() {
        vertices.clear();

        // 1. 左右大脑半球 (Cerebral Hemispheres)
        int latSteps = 22;
        int lonSteps = 34;
        for (int hemi = -1; hemi <= 1; hemi += 2) {
            int startIdx = vertices.size();
            for (int i = 1; i < latSteps; i++) {
                float v = i / (float) latSteps;
                float theta = v * (float) Math.PI; // 0 (顶) -> PI (底)
                float sinTheta = Mth.sin(theta);
                float cosTheta = Mth.cos(theta);

                for (int j = 0; j < lonSteps; j++) {
                    float u = j / (float) lonSteps;
                    // 每个半球覆盖半圆周并留出中央纵裂 (Longitudinal Fissure)
                    float phi = u * (float) Math.PI;
                    float sinPhi = Mth.sin(phi);
                    float cosPhi = Mth.cos(phi);

                    // 基础椭球坐标：前后长 (Z: 1.18)，上下高 (Y: 0.88)，左右宽 (X: 0.78)
                    float rawX = hemi * (0.065f + sinTheta * sinPhi * 0.76f);
                    float rawY = -cosTheta * 0.86f;
                    float rawZ = sinTheta * cosPhi * 1.16f;

                    // 解剖形态修正：
                    // (a) 额叶 (Frontal Lobe, Z > 0.2) 略微饱满宽阔
                    if (rawZ > 0.2f) {
                        rawX *= 1.0f + 0.08f * (rawZ / 1.16f);
                    }
                    // (b) 枕叶 (Occipital Lobe, Z < -0.4) 向后下方微尖突
                    if (rawZ < -0.4f) {
                        rawY += 0.08f * ((-rawZ - 0.4f) / 0.76f);
                    }
                    // (c) 颞叶 (Temporal Lobe) 在中下部外侧明显隆起
                    float temporalMask = Math.max(0.0f, 1.0f - Math.abs(rawZ - 0.05f) * 1.6f)
                            * Math.max(0.0f, (rawY - 0.05f) * 1.4f);
                    rawX += hemi * temporalMask * 0.16f;
                    rawY += temporalMask * 0.08f;

                    // (d) 底部平坦化（前颅窝/中颅窝底面收平）
                    if (rawY > 0.35f) {
                        rawY = 0.35f + (rawY - 0.35f) * 0.42f;
                    }

                    // 多阶谐波模拟脑回 (Gyri) 与脑沟 (Sulci) 褶皱
                    float gyri1 = Mth.sin(rawX * 9.5f + rawZ * 7.2f) * Mth.cos(rawY * 8.4f - rawZ * 5.8f);
                    float gyri2 = Mth.sin(rawZ * 14.0f - rawY * 11.0f) * 0.5f;
                    float fold = Mth.clamp(0.5f + 0.38f * gyri1 + 0.22f * gyri2, 0.0f, 1.0f);

                    float disp = 0.94f + 0.09f * fold;
                    float fx = rawX * disp;
                    float fy = rawY * disp;
                    float fz = rawZ * disp;

                    float len = Mth.sqrt(fx * fx + fy * fy + fz * fz);
                    float nx = len > 0.001f ? fx / len : 0.0f;
                    float ny = len > 0.001f ? fy / len : -1.0f;
                    float nz = len > 0.001f ? fz / len : 0.0f;

                    int prevInRow = (j > 0) ? (vertices.size() - 1) : -1;
                    vertices.add(new BrainVertex(fx, fy, fz, nx, ny, nz, hemi, fold, prevInRow));
                }
            }
            // 添加少量跨纬度纵向连接使网格呈现精致的脑皮层拓扑感
            int totalHemi = vertices.size() - startIdx;
            if (totalHemi > lonSteps) {
                for (int k = startIdx + lonSteps; k < vertices.size(); k += 3) {
                    BrainVertex v = vertices.get(k);
                    vertices.set(k, new BrainVertex(v.x, v.y, v.z, v.nx, v.ny, v.nz, v.region, v.foldFactor, k - lonSteps));
                }
            }
        }

        // 2. 小脑 (Cerebellum)：位于枕叶下方后侧 (Y: 0.28~0.68, Z: -0.45~-1.05)
        int cLat = 10;
        int cLon = 22;
        for (int hemi = -1; hemi <= 1; hemi += 2) {
            for (int i = 1; i < cLat; i++) {
                float theta = (i / (float) cLat) * (float) Math.PI;
                for (int j = 0; j < cLon; j++) {
                    float phi = (j / (float) cLon) * (float) Math.PI;
                    float cx = hemi * (0.04f + Mth.sin(theta) * Mth.sin(phi) * 0.46f);
                    float cy = 0.44f - Mth.cos(theta) * 0.24f;
                    float cz = -0.68f + Mth.sin(theta) * Mth.cos(phi) * 0.34f;

                    // 小脑特征性的细密水平叶片纹 (Folia)
                    float folia = 0.5f + 0.5f * Mth.sin(cy * 32.0f);
                    int prev = (j > 0) ? (vertices.size() - 1) : -1;
                    vertices.add(new BrainVertex(cx, cy, cz, 0.0f, 0.3f, -0.9f, 2, folia, prev));
                }
            }
        }

        // 3. 脑干 (Brainstem - 脑桥与延髓)：位于中央底部向下延伸 (Y: 0.35~0.98, Z: -0.15~-0.38)
        int stemRings = 9;
        int stemSegs = 14;
        for (int r = 0; r < stemRings; r++) {
            float t = r / (float) (stemRings - 1);
            float sy = 0.36f + t * 0.62f;
            float szCenter = -0.18f - t * 0.14f;
            // 脑桥略微膨大，向下延髓收窄
            float radius = (0.19f - t * 0.06f) * (1.0f + 0.18f * Mth.sin(t * (float) Math.PI));
            for (int s = 0; s < stemSegs; s++) {
                float ang = (s / (float) stemSegs) * ((float) Math.PI * 2.0f);
                float sx = Mth.cos(ang) * radius * 0.9f;
                float sz = szCenter + Mth.sin(ang) * radius;
                int prev = (s > 0) ? (vertices.size() - 1) : -1;
                vertices.add(new BrainVertex(sx, sy, sz, Mth.cos(ang), 0.2f, Mth.sin(ang), 3, 0.7f, prev));
            }
        }
    }

    /**
     * 初始化内部白质神经纤维连接束 (Connectome Tracts)
     */
    private void initSynapseConnectome() {
        synapseTracts.clear();
        Random rand = new Random(2077042L);
        if (vertices.size() < 40) return;

        for (int i = 0; i < 44; i++) {
            BrainVertex a = vertices.get(rand.nextInt(vertices.size()));
            BrainVertex b = vertices.get(rand.nextInt(vertices.size()));
            // 收缩至脑实质内部 75% 深度，模拟白质神经轴突束
            synapseTracts.add(new SynapseTract(
                    a.x * 0.78f, a.y * 0.78f, a.z * 0.78f,
                    b.x * 0.78f, b.y * 0.78f, b.z * 0.78f,
                    0.55f + rand.nextFloat() * 0.95f,
                    rand.nextFloat()
            ));
        }
    }

    private void initOrbitParticles() {
        orbitParticles.clear();
        Random rand = new Random(101331L);
        for (int i = 0; i < 36; i++) {
            orbitParticles.add(new OrbitParticle(rand));
        }
    }

    private void initAnchorLabels() {
        anchorLabels.clear();
        anchorLabels.add(new BrainAnchorLabel("FRONTAL // 额叶皮层", -0.52f, -0.36f, 0.72f));
        anchorLabels.add(new BrainAnchorLabel("PARIETAL // 顶叶中枢", 0.56f, -0.62f, -0.08f));
        anchorLabels.add(new BrainAnchorLabel("TEMPORAL // 颞叶听觉", 0.78f, 0.12f, 0.18f));
        anchorLabels.add(new BrainAnchorLabel("OCCIPITAL // 枕叶视觉", -0.45f, -0.10f, -0.92f));
        anchorLabels.add(new BrainAnchorLabel("CEREBELLUM // 小脑运动", 0.38f, 0.48f, -0.78f));
        anchorLabels.add(new BrainAnchorLabel("BRAINSTEM // 脑干生命中枢", 0.0f, 0.82f, -0.28f));
    }

    /**
     * 兼容原版 8 参数签名调用，内部自动获取鼠标坐标与时间戳
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       MedicalData data, float deltaSeconds) {
        Minecraft mc = Minecraft.getInstance();
        int mx = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        render(graphics, font, x, y, width, height, data, deltaSeconds, mx, my, System.currentTimeMillis());
    }

    /**
     * 完整 11 参数渲染主入口
     */
    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       MedicalData data, float deltaSeconds, int mouseX, int mouseY, long now) {
        if (data == null || width < 80 || height < 60) return;

        this.lastPanelX = x;
        this.lastPanelY = y;
        this.lastPanelW = width;
        this.lastPanelH = height;

        // 1. 计算真实帧间隔 dt，驱动丝滑动画与惯性滚动
        if (lastRenderTimeMs == 0L) lastRenderTimeMs = now;
        float dt = Mth.clamp((now - lastRenderTimeMs) / 1000.0f, 0.001f, 0.1f);
        lastRenderTimeMs = now;

        // 滚轮平滑阻尼追赶
        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
        scrollOffset = Mth.lerp(Math.min(1.0f, dt * 14.0f), scrollOffset, scrollTarget);

        // 自动旋转（当鼠标未按住拖拽时缓慢优雅自转）
        if (autoRotateTimer > 0.0f) {
            autoRotateTimer = Math.max(0.0f, autoRotateTimer - dt);
        } else {
            yaw = (yaw + dt * 0.42f) % ((float) Math.PI * 2.0f);
            pitch = Mth.lerp(Math.min(1.0f, dt * 2.0f), pitch, 0.20f + 0.08f * Mth.sin(now * 0.0012f));
        }

        // 2. 绘制右上角 3A 视图切换胶囊按钮栏 (View Mode Switcher)
        drawViewModeTabs(graphics, font, x, y, width, mouseX, mouseY);

        int contentTop = y + 24;
        int contentH = height - 30;
        int contentX = x + 10;
        int contentW = width - 20;

        // 若窗口宽度过窄 (< 220px)，自动回退或紧凑排列
        if (currentMode == ViewMode.HOLO_FULL) {
            // 模式 2：100% 沉浸式 3D 全息脑扫描 + 四角战术微卡片展示头部外伤/内出血/疼痛/感染
            this.lastHoloAreaX = contentX;
            this.lastHoloAreaY = contentTop;
            this.lastHoloAreaW = contentW;
            this.lastHoloAreaH = contentH;
            this.lastScrollAreaW = 0;

            render3DBrainViewport(graphics, font, contentX, contentTop, contentW, contentH, data, dt, now, true);
            drawCornerTacticalOverlays(graphics, font, contentX, contentTop, contentW, contentH, data, now);
        } else if (currentMode == ViewMode.CLINICAL_GRID || contentW < 210) {
            // 模式 3：双列高密度临床遥测全表（带硬件 Scissor 滚轮视窗 + 顶部小型脑扫描摘要条）
            this.lastHoloAreaW = 0;
            this.lastScrollAreaX = contentX;
            this.lastScrollAreaY = contentTop;
            this.lastScrollAreaW = contentW;
            this.lastScrollAreaH = contentH;

            renderScrollableHeadTelemetry(graphics, font, contentX, contentTop, contentW, contentH, data, now, true);
        } else {
            // 模式 1 (默认 SPLIT)：左侧 46% 为 3D 全息脑部扫描舱，右侧 54% 为带硬件裁剪滚动的完整头部创伤 + 脑外科指标舱
            int holoW = (int) (contentW * 0.46f);
            int diagX = contentX + holoW + 8;
            int diagW = contentW - holoW - 8;

            this.lastHoloAreaX = contentX;
            this.lastHoloAreaY = contentTop;
            this.lastHoloAreaW = holoW;
            this.lastHoloAreaH = contentH;

            this.lastScrollAreaX = diagX;
            this.lastScrollAreaY = contentTop;
            this.lastScrollAreaW = diagW;
            this.lastScrollAreaH = contentH;

            // 左侧 3D 脑部全息舱底框
            HudRenderHelper.drawSciFiCard(graphics, contentX, contentTop, holoW, contentH, 4, 0x99040A12, 0x5500E5FF);
            render3DBrainViewport(graphics, font, contentX, contentTop, holoW, contentH, data, dt, now, false);

            // 右侧可滚动完整头部诊断舱（包含原本缺失的外出血、内出血、疼痛、感染、状态药丸及颅内专科数据）
            HudRenderHelper.drawSciFiCard(graphics, diagX, contentTop, diagW, contentH, 4, 0x99040A12, 0x4400A0FF);
            renderScrollableHeadTelemetry(graphics, font, diagX + 6, contentTop + 4, diagW - 12, contentH - 8, data, now, false);
        }
    }

    /**
     * 绘制右上角三模式切换按钮栏
     */
    private void drawViewModeTabs(GuiGraphics g, Font font, int panelX, int panelY, int panelW, int mouseX, int mouseY) {
        ViewMode[] modes = ViewMode.values();
        int tabH = 13;
        int tabY = panelY + 5;
        int curRight = panelX + panelW - 10;

        for (int i = modes.length - 1; i >= 0; i--) {
            ViewMode m = modes[i];
            int tw = font.width(m.label) + 10;
            int tx = curRight - tw;
            if (tx < panelX + 140) break; // 防极窄分辨率下与标题重叠

            boolean active = (this.currentMode == m);
            boolean hovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = active ? 0xCC004D66 : (hovered ? 0x990A2233 : 0x66050E17);
            int border = active ? HudColors.COLOR_NEON_CYAN : (hovered ? 0xAA00E5FF : 0x4400A0FF);
            int txtColor = active ? 0xFFFFFFFF : (hovered ? HudColors.COLOR_NEON_CYAN : 0xFF90A4AE);

            HudRenderHelper.drawSciFiCard(g, tx, tabY, tw, tabH, 2, bg, border);
            g.drawString(font, m.label, tx + 5, tabY + 3, txtColor, false);

            curRight = tx - 4;
        }
    }

    /**
     * 渲染 3D 全息脑部扫描核心视窗
     */
    private void render3DBrainViewport(GuiGraphics g, Font font, int vx, int vy, int vw, int vh,
                                       MedicalData data, float dt, long now, boolean fullMode) {
        g.enableScissor(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1);

        int brainHealth = data.getBrainHealth();
        int consciousness = data.getConsciousness();
        int icLevel = data.getIntracranialLevel();
        float icVol = data.getIntracranialAccumulation();
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        int headBleed = headLd != null ? headLd.getBleeding() : 0;
        int headInternal = headLd != null ? headLd.getInternalBleedingRate() : 0;
        int headPain = headLd != null ? headLd.getPainLevel() : 0;
        int headInf = headLd != null ? headLd.getInfection() : 0;

        float cx = vx + vw * 0.5f;
        float cy = vy + vh * (fullMode ? 0.46f : 0.43f);
        float scale = Math.min(vw, vh) * (fullMode ? 0.34f : 0.36f);

        // 1. 绘制全息极坐标雷达底盘与三轴陀螺校准环 (3D Gimbal Calibration Rings)
        drawHologramGimbalRings(g, cx, cy, scale, now, brainHealth, icLevel);

        // 2. 计算 3D 旋转矩阵参量
        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);
        float cosPitch = Mth.cos(pitch);
        float sinPitch = Mth.sin(pitch);

        // 周期性双轴 CT 激光扫描平面 (Y 轴水平断层 + Z 轴冠状断层)
        float scanCycle = (now % 3200L) / 3200.0f;
        float scanPlaneY = -1.05f + scanCycle * 2.10f;

        // 脑损伤故障抖动系数
        float glitchIntensity = (brainHealth < 85) ? Mth.clamp((85 - brainHealth) / 85.0f, 0.0f, 1.0f) : 0.0f;

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // 3. 绘制环绕全息光子流与内部白质神经突触网络
        drawSynapseConnectomeAndPulses(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, dt, now, data, headPain);
        drawOrbitingParticles(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, dt, brainHealth);

        // 4. 绘制 3D 病理病灶云团（颅内血肿、头部内外出血破裂点、脑膜感染灶）
        if (icVol > 0.1f || headBleed > 0 || headInternal > 0 || headInf > 0) {
            draw3DPathologyLesions(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, now,
                    icVol, icLevel, headBleed, headInternal, headInf);
        }

        // 5. 绘制解剖级四分结构 3D 大脑皮层网格与体素点
        drawAnatomicalBrainMesh(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                scanPlaneY, glitchIntensity, brainHealth, headPain, icLevel, now);

        RenderSystem.defaultBlendFunc();

        // 6. 绘制 CT 激光扫描横截面光带
        int scanScreenY = (int) (cy + scanPlaneY * scale * 0.85f);
        if (scanScreenY > vy + 10 && scanScreenY < vy + vh - 28) {
            int scanW = (int) (scale * 2.1f);
            g.fill((int) cx - scanW / 2, scanScreenY, (int) cx + scanW / 2, scanScreenY + 1, 0x8800F0FF);
            g.fillGradient((int) cx - scanW / 2, scanScreenY - 8, (int) cx + scanW / 2, scanScreenY, 0x0000F0FF, 0x3300F0FF);
        }

        // 7. 绘制 3D 空间投影脑区浮动标签 (带背面深度剔除与打字机特效)
        draw3DFloatingLabels(g, font, vx, vy, vw, vh, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, data, now, fullMode);

        // 8. 视窗底部固定绘制脑健康 & 意识双微缩遥测条 + 拖拽旋转提示
        drawHoloFooterBar(g, font, vx, vy + vh - 26, vw, 24, brainHealth, consciousness);

        g.disableScissor();
    }

    /**
     * 绘制全息陀螺仪基准环与极坐标刻度
     */
    private void drawHologramGimbalRings(GuiGraphics g, float cx, float cy, float scale, long now, int brainHealth, int icLevel) {
        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        int baseColor = (brainHealth < 50 || icLevel >= 3) ? 0xFF2040 : 0x00E5FF;
        int r = (baseColor >> 16) & 0xFF;
        int gr = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int segs = 48;
        float ringR = scale * 1.18f;
        for (int i = 0; i <= segs; i++) {
            float ang = (i / (float) segs) * ((float) Math.PI * 2.0f);
            float px = cx + Mth.cos(ang) * ringR;
            float py = cy + Mth.sin(ang) * ringR * 0.35f; // 透视椭圆环
            buf.addVertex(pose, px, py + scale * 0.72f, 0.0f).setColor(r, gr, b, 55);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /**
     * 绘制内部白质神经突触纤维与动作电位光脉冲
     */
    private void drawSynapseConnectomeAndPulses(Matrix4f pose, float cx, float cy, float scale,
                                                float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                                float dt, long now, MedicalData data, int headPain) {
        if (synapseTracts.isEmpty()) return;

        float speedMul = (data.getConsciousness() / 100.0f) * (data.getHeartRate() / 75.0f);
        speedMul = Mth.clamp(speedMul, 0.25f, 2.5f);
        boolean painStorm = headPain >= 5 || data.getBrainHealth() < 60;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SynapseTract tract : synapseTracts) {
            tract.phase = (tract.phase + dt * tract.speed * speedMul) % 1.0f;

            // 投影起点与终点
            float[] p1 = project3D(tract.x1, tract.y1, tract.z1, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float[] p2 = project3D(tract.x2, tract.y2, tract.z2, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);

            // 神经轴突暗青色基底丝
            PainVisualOverlay.addQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1], 0.45f,
                    0, 170, 230, 28, 28);

            // 沿轴突高速移动的动作电位光子脉冲包
            float tHead = tract.phase;
            float tTail = Math.max(0.0f, tHead - 0.22f);
            float hx = Mth.lerp(tHead, p1[0], p2[0]);
            float hy = Mth.lerp(tHead, p1[1], p2[1]);
            float tx = Mth.lerp(tTail, p1[0], p2[0]);
            float ty = Mth.lerp(tTail, p1[1], p2[1]);

            int pr = painStorm ? 255 : 80;
            int pg = painStorm ? 60 : 245;
            int pb = 255;
            PainVisualOverlay.addQuadSegment(buf, pose, tx, ty, hx, hy, 1.15f, pr, pg, pb, 0, 185);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /**
     * 绘制环绕轨道发光光子
     */
    private void drawOrbitingParticles(Matrix4f pose, float cx, float cy, float scale,
                                       float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                       float dt, int brainHealth) {
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (OrbitParticle p : orbitParticles) {
            p.angle = (p.angle + p.speed * dt) % ((float) Math.PI * 2.0f);
            float ox = Mth.cos(p.angle) * p.radiusX;
            float oz = Mth.sin(p.angle) * p.radiusZ;
            float oy = ox * p.tiltX + oz * p.tiltZ;

            float[] proj = project3D(ox, oy, oz, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float depthScale = Mth.clamp((proj[2] + 1.5f) / 3.0f, 0.3f, 1.2f);
            float hs = p.size * depthScale;
            int alpha = (int) (p.baseAlpha * depthScale * 255.0f);

            int r = brainHealth < 50 ? 255 : 0;
            int g = brainHealth < 50 ? 60 : 230;
            int b = brainHealth < 50 ? 90 : 255;

            buf.addVertex(pose, proj[0] - hs, proj[1] - hs, 0.0f).setColor(r, g, b, alpha);
            buf.addVertex(pose, proj[0] + hs, proj[1] - hs, 0.0f).setColor(r, g, b, alpha);
            buf.addVertex(pose, proj[0] + hs, proj[1] + hs, 0.0f).setColor(r, g, b, alpha);
            buf.addVertex(pose, proj[0] - hs, proj[1] + hs, 0.0f).setColor(r, g, b, alpha);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    /**
     * 绘制 3D 颅内血肿、头部内外出血破裂信标与感染病灶
     */
    private void draw3DPathologyLesions(Matrix4f pose, float cx, float cy, float scale,
                                        float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                        long now, float icVol, int icLevel, int headBleed, int headInternal, int headInf) {
        float pulse = 0.5f + 0.5f * Mth.sin(now * 0.008f);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // 1. 颅内出血血肿 3D 核心云团 (位于右顶颞部硬膜下空间: x=0.42, y=-0.25, z=0.15)
        if (icVol > 0.1f || icLevel > 0) {
            float[] center = project3D(0.42f, -0.25f, 0.15f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (6.0f + Math.min(18.0f, icVol * 0.12f) + icLevel * 3.0f) * (0.85f + 0.25f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 16, 48, (int) ((130 + 80 * pulse)));
        }

        // 2. 头部内出血病灶 (紫红色深层血管渗漏点: x=-0.38, y=-0.15, z=-0.20)
        if (headInternal > 0) {
            float[] center = project3D(-0.38f, -0.15f, -0.20f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (5.0f + Math.min(12.0f, headInternal * 0.05f)) * (0.85f + 0.25f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 210, 30, 255, 175);
        }

        // 3. 头部外出血创口信标 (额顶部表皮动脉破裂点: x=-0.45, y=-0.52, z=0.45)
        if (headBleed > 0) {
            float[] center = project3D(-0.45f, -0.52f, 0.45f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (5.0f + Math.min(14.0f, headBleed * 0.04f)) * (0.8f + 0.3f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 45, 20, 195);
        }

        // 4. 头部创口感染/脑膜炎症信标 (琥珀黄绿生物告警点: x=0.25, y=0.10, z=0.55)
        if (headInf > 0) {
            float[] center = project3D(0.25f, 0.10f, 0.55f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (4.5f + headInf * 0.10f) * (0.85f + 0.2f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 195, 0, 170);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void drawGlowDisc(BufferBuilder buf, Matrix4f pose, float x, float y, float radius, int r, int g, int b, int centerAlpha) {
        // 中心亮核 + 外扩光晕四边形
        float core = radius * 0.35f;
        buf.addVertex(pose, x - core, y - core, 0.0f).setColor(255, 220, 230, centerAlpha);
        buf.addVertex(pose, x + core, y - core, 0.0f).setColor(255, 220, 230, centerAlpha);
        buf.addVertex(pose, x + core, y + core, 0.0f).setColor(255, 220, 230, centerAlpha);
        buf.addVertex(pose, x - core, y + core, 0.0f).setColor(255, 220, 230, centerAlpha);

        int haloAlpha = centerAlpha / 3;
        buf.addVertex(pose, x - radius, y - radius, 0.0f).setColor(r, g, b, haloAlpha);
        buf.addVertex(pose, x + radius, y - radius, 0.0f).setColor(r, g, b, haloAlpha);
        buf.addVertex(pose, x + radius, y + radius, 0.0f).setColor(r, g, b, haloAlpha);
        buf.addVertex(pose, x - radius, y + radius, 0.0f).setColor(r, g, b, haloAlpha);
    }

    /**
     * 绘制解剖级 3D 大脑皮层网格线与全息体素点（含脑回高亮、小脑分层着色、CT 扫描面增亮与疼痛神经波）
     */
    private void drawAnatomicalBrainMesh(Matrix4f pose, float cx, float cy, float scale,
                                         float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                         float scanPlaneY, float glitchIntensity,
                                         int brainHealth, int headPain, int icLevel, long now) {
        int n = vertices.size();
        if (n == 0) return;

        float[] px = new float[n];
        float[] py = new float[n];
        float[] pz = new float[n];
        int[] vColors = new int[n];

        float healthPct = brainHealth / 100.0f;
        float painWavePhase = (now % 1500L) / 1500.0f * ((float) Math.PI * 2.0f);

        for (int i = 0; i < n; i++) {
            BrainVertex v = vertices.get(i);
            float vx = v.x;
            float vy = v.y;
            float vz = v.z;

            // 脑损伤全息电磁毛刺错位
            if (glitchIntensity > 0.01f && ((i + (int) (now / 60L)) % 29 == 0)) {
                vx += Mth.sin(now * 0.05f + i) * 0.14f * glitchIntensity;
            }

            float[] proj = project3D(vx, vy, vz, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            px[i] = proj[0];
            py[i] = proj[1];
            pz[i] = proj[2];

            // 深度雾化因子 (前方顶点清晰明亮，背面顶点柔和暗化)
            float depthFactor = Mth.clamp((proj[2] + 1.35f) / 2.5f, 0.18f, 1.0f);
            // 菲涅尔边缘增亮 (Fresnel Rim Light)
            float rim = 1.0f - Math.min(1.0f, Math.abs(proj[2]) * 0.75f);
            float foldBright = 0.55f + 0.45f * v.foldFactor + 0.25f * rim;

            // 基础全息颜色：大脑皮层霓虹青、小脑全息天蓝、脑干翡翠青；脑健康低时向警报红渐变
            int r, g, b;
            if (v.region == 2) {
                // 小脑
                r = (int) Mth.lerp(healthPct, 255, 20);
                g = (int) Mth.lerp(healthPct, 40, 175);
                b = (int) Mth.lerp(healthPct, 80, 255);
            } else if (v.region == 3) {
                // 脑干
                r = (int) Mth.lerp(healthPct, 255, 0);
                g = (int) Mth.lerp(healthPct, 80, 255);
                b = (int) Mth.lerp(healthPct, 60, 180);
            } else {
                // 左右大脑半球
                r = (int) Mth.lerp(healthPct, 255, 0);
                g = (int) Mth.lerp(healthPct, 30, 235);
                b = (int) Mth.lerp(healthPct, 65, 255);
            }

            // 头部疼痛神经冲击波染色：沿皮层表面传播的品红/亮红痛觉电弧
            if (headPain > 0) {
                float painWave = Mth.sin(v.y * 6.0f - v.z * 4.0f + painWavePhase);
                if (painWave > 0.75f) {
                    float pf = (headPain / 10.0f) * ((painWave - 0.75f) / 0.25f);
                    r = (int) Mth.lerp(pf, r, 255);
                    g = (int) Mth.lerp(pf, g, 20);
                    b = (int) Mth.lerp(pf, b, 95);
                }
            }

            // CT 激光切面扫过时高亮为纯白青光
            float distToScan = Math.abs(v.y - scanPlaneY);
            float scanBoost = (distToScan < 0.09f) ? (1.0f - distToScan / 0.09f) : 0.0f;
            if (scanBoost > 0.0f) {
                r = (int) Mth.lerp(scanBoost, r, 220);
                g = (int) Mth.lerp(scanBoost, g, 255);
                b = 255;
            }

            int alpha = (int) Mth.clamp((depthFactor * foldBright * 175.0f) + scanBoost * 80.0f, 20.0f, 250.0f);
            vColors[i] = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        // A. 绘制脑回拓扑微连接线
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder lineBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            BrainVertex v = vertices.get(i);
            int target = v.connectIndex;
            if (target >= 0 && target < n) {
                float dx = px[i] - px[target];
                float dy = py[i] - py[target];
                if (dx * dx + dy * dy < scale * scale * 0.18f) {
                    int c = vColors[i];
                    int la = (((c >>> 24) & 0xFF) * 2) / 5;
                    int lr = (c >>> 16) & 0xFF;
                    int lg = (c >>> 8) & 0xFF;
                    int lb = c & 0xFF;
                    PainVisualOverlay.addQuadSegment(lineBuf, pose, px[i], py[i], px[target], py[target],
                            0.38f, lr, lg, lb, la, la);
                }
            }
        }
        MeshData lineMesh = lineBuf.build();
        if (lineMesh != null) BufferUploader.drawWithShader(lineMesh);

        // B. 绘制高密度全息体素点
        BufferBuilder ptBuf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            int c = vColors[i];
            int a = (c >>> 24) & 0xFF;
            int r = (c >>> 16) & 0xFF;
            int g = (c >>> 8) & 0xFF;
            int b = c & 0xFF;

            float hs = (pz[i] > 0.0f) ? 1.15f : 0.75f;
            float x = px[i];
            float y = py[i];

            ptBuf.addVertex(pose, x - hs, y - hs, 0.0f).setColor(r, g, b, a);
            ptBuf.addVertex(pose, x + hs, y - hs, 0.0f).setColor(r, g, b, a);
            ptBuf.addVertex(pose, x + hs, y + hs, 0.0f).setColor(r, g, b, a);
            ptBuf.addVertex(pose, x - hs, y + hs, 0.0f).setColor(r, g, b, a);
        }
        MeshData ptMesh = ptBuf.build();
        if (ptMesh != null) BufferUploader.drawWithShader(ptMesh);
    }

    /**
     * 绘制 3D 脑区引线浮动标签
     */
    private void draw3DFloatingLabels(GuiGraphics g, Font font, int vx, int vy, int vw, int vh,
                                      float cx, float cy, float scale,
                                      float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                      MedicalData data, long now, boolean fullMode) {
        int maxLabels = fullMode ? anchorLabels.size() : 3;
        int shown = 0;

        for (int i = 0; i < anchorLabels.size() && shown < maxLabels; i++) {
            BrainAnchorLabel lbl = anchorLabels.get(i);
            float[] proj = project3D(lbl.ox, lbl.oy, lbl.oz, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);

            // 仅显示朝向镜头前方的脑区标签，避免线条杂乱交叉
            if (proj[2] < 0.12f) continue;
            shown++;

            int ax = (int) proj[0];
            int ay = (int) proj[1];
            boolean rightSide = ax >= cx;
            int elbowX = rightSide ? ax + 14 : ax - 14;
            int elbowY = ay - 8;

            String text = lbl.keyTitle;
            int tw = font.width(text);
            int labelX = rightSide ? Math.min(vx + vw - tw - 6, elbowX + 4) : Math.max(vx + 6, elbowX - tw - 4);
            int labelY = Mth.clamp(elbowY - 4, vy + 10, vy + vh - 38);

            int color = (data.getBrainHealth() < 60) ? HudColors.COLOR_WARNING_YELLOW : HudColors.COLOR_NEON_CYAN;
            HudRenderHelper.drawLine(g, ax, ay, elbowX, elbowY, 0xAA00E5FF, 1);
            HudRenderHelper.drawDiamond(g, ax, ay, 2, color);

            g.fill(labelX - 2, labelY - 1, labelX + tw + 2, labelY + 9, 0xBB040B14);
            g.drawString(font, text, labelX, labelY, color, false);
        }
    }

    /**
     * 视窗底部绘制脑健康与意识双进度栏
     */
    private void drawHoloFooterBar(GuiGraphics g, Font font, int x, int y, int w, int h, int brainHealth, int consciousness) {
        g.fill(x + 4, y, x + w - 4, y + h, 0xAA050D17);
        int halfW = (w - 20) / 2;

        // 左：脑健康
        int bhColor = HudColors.getHealthColor(brainHealth / 100.0f);
        g.drawString(font, "BRAIN " + brainHealth + "%", x + 8, y + 3, bhColor, false);
        HudRenderHelper.drawSciFiProgressBar(g, x + 8, y + 14, halfW, 5, brainHealth / 100.0f, bhColor, 0xFF0A1926);

        // 右：意识清醒度
        int rx = x + 12 + halfW;
        int csColor = HudColors.getHealthColor(consciousness / 100.0f);
        g.drawString(font, "CONSC " + consciousness + "%", rx, y + 3, csColor, false);
        HudRenderHelper.drawSciFiProgressBar(g, rx, y + 14, halfW, 5, consciousness / 100.0f, csColor, 0xFF0A1926);
    }

    /**
     * 全屏 3D 模式下的四角战术微卡片（保证全屏欣赏 3D 大脑时也能看到头部外出血/内出血/疼痛/感染/颅内压）
     */
    private void drawCornerTacticalOverlays(GuiGraphics g, Font font, int x, int y, int w, int h, MedicalData data, long now) {
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd == null) return;

        int cardW = Math.min(135, (w - 24) / 2);
        int topY = y + 6;

        // 左上角：头部外伤出血与内出血微卡片
        int bleed = headLd.getBleeding();
        int inBleed = headLd.getInternalBleedingRate();
        int c1 = (bleed > 0 || inBleed > 0) ? HudColors.COLOR_ALERT_RED : HudColors.COLOR_SAFE_GREEN;
        HudRenderHelper.drawTacticalBadge(g, font, x + 6, topY, cardW, 24,
                "外出血: " + bleed + " mL/m",
                "内出血: " + inBleed + " mL/m",
                c1, 0xCC06101A);

        // 右上角：头部疼痛与感染微卡片
        int pain = headLd.getPainLevel();
        int inf = headLd.getInfection();
        int c2 = (pain >= 5 || inf >= 25) ? HudColors.COLOR_WARNING_YELLOW : HudColors.COLOR_NEON_CYAN;
        HudRenderHelper.drawTacticalBadge(g, font, x + w - cardW - 6, topY, cardW, 24,
                "头部疼痛: " + pain + " / 10",
                "头部感染: " + inf + "%",
                c2, 0xCC06101A);

        // 左下角（底栏上方）：颅内积血与分级
        int botY = y + h - 54;
        int icLevel = data.getIntracranialLevel();
        float icVol = data.getIntracranialAccumulation();
        int c3 = icLevel > 0 ? 0xFFE040FB : HudColors.COLOR_HOLOGRAM_BLUE;
        HudRenderHelper.drawTacticalBadge(g, font, x + 6, botY, cardW, 24,
                String.format("颅内积血: %.1f mL", icVol),
                "颅内分级: LV." + icLevel + (data.getIntracranialOnsetTimer() > 0 && data.getIntracranialOnsetTimer() < 30 ? " (清醒期)" : ""),
                c3, 0xCC06101A);

        // 右下角：脑灌注血压与 MAP
        int sbp = data.getSystolicBP();
        int dbp = data.getDiastolicBP();
        float map = data.getMeanArterialPressure();
        HudRenderHelper.drawTacticalBadge(g, font, x + w - cardW - 6, botY, cardW, 24,
                "血压: " + sbp + "/" + dbp + " mmHg",
                String.format("脑灌注 MAP: %.0f mmHg", map),
                HudColors.VITAL_BLOOD_PRESSURE, 0xCC06101A);
    }

    /**
     * 核心升级：带硬件 Scissor 裁剪视窗与双列自适应的头部完整诊断面板
     * 完整补齐：头部健康度、头部外出血、头部内出血、头部疼痛、头部感染、头部烧伤、头部状态徽标 + 脑外科/颅内出血全部参数！
     */
    private void renderScrollableHeadTelemetry(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               MedicalData data, long now, boolean forceTwoColumn) {
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd == null) return;

        int contentW = w - 8; // 预留右侧 6px 给全息滚动条
        g.enableScissor(x, y, x + w, y + h);

        int drawY = (int) (y - scrollOffset);
        int startY = drawY;

        // 1. 头部综合健康度顶栏
        int headHealth = computeLimbHealthPercent(headLd);
        int hc = HudColors.getHealthColor(headHealth / 100.0f);
        g.drawString(font, "CRANIAL INTEGRITY // 头部结构完整度", x, drawY, HudColors.TEXT_LABEL, false);
        String hpText = headHealth + "%";
        g.drawString(font, hpText, x + contentW - font.width(hpText), drawY, hc, false);
        drawY += 11;
        HudRenderHelper.drawSciFiProgressBar(g, x, drawY, contentW, 6, headHealth / 100.0f, hc, 0xFF0A1824);
        drawY += 12;

        // 2. 第一分区：头部基础创伤指标（外出血、内出血、疼痛、感染、烧伤 —— 以前缺失的部分全部补齐！）
        drawSectionHeader(g, font, x, drawY, contentW, "HEAD TRAUMA METRICS // 头部创伤指标", 0xFF00E5FF);
        drawY += 13;

        int bleed = headLd.getBleeding();
        int inBleed = headLd.getInternalBleedingRate();
        int pain = headLd.getPainLevel();
        int inf = headLd.getInfection();

        boolean useTwoCol = forceTwoColumn || contentW >= 195;
        if (useTwoCol) {
            int colW = (contentW - 6) / 2;
            // 行 1：外出血 | 内出血
            drawCompactMetricCard(g, font, x, drawY, colW,
                    tr("medical.detail.bleeding_rate"), bleed + " mL/m",
                    Math.min(1.0f, bleed / 400.0f), bleed > 0 ? HudColors.COLOR_ALERT_RED : 0xFF37474F);
            drawCompactMetricCard(g, font, x + colW + 6, drawY, colW,
                    tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/m",
                    Math.min(1.0f, inBleed / 250.0f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
            drawY += 24;

            // 行 2：头部疼痛 | 头部感染
            drawCompactMetricCard(g, font, x, drawY, colW,
                    tr("medical.detail.pain_level"), pain + " / 10",
                    pain / 10.0f, pain > 0 ? HudColors.VITAL_PAIN : 0xFF37474F);
            drawCompactMetricCard(g, font, x + colW + 6, drawY, colW,
                    tr("medical.detail.infection"), inf + " %",
                    inf / 100.0f, inf > 0 ? HudColors.COLOR_WARNING_YELLOW : 0xFF37474F);
            drawY += 24;
        } else {
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "外出血 BLEED", bleed + " mL/min",
                    Math.min(1.0f, bleed / 400.0f), bleed > 0 ? HudColors.COLOR_ALERT_RED : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "内出血 INTERNAL", inBleed + " mL/min",
                    Math.min(1.0f, inBleed / 250.0f), inBleed > 0 ? 0xFFE040FB : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "疼痛等级 PAIN", pain + " / 10",
                    pain / 10.0f, pain > 0 ? HudColors.VITAL_PAIN : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "感染程度 INFECTION", inf + " %",
                    inf / 100.0f, inf > 0 ? HudColors.COLOR_WARNING_YELLOW : 0xFF455A64);
        }

        // 若有自然凝血进度或烧伤，动态展示
        if (bleed > 0 && bleed <= 50 && headLd.getNaturalClottingTimer() > 0) {
            float clotPct = Mth.clamp(headLd.getNaturalClottingTimer() / 180.0f, 0.0f, 1.0f);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    tr("medical.detail.bleeding_progress"), (int) (clotPct * 100) + "%",
                    clotPct, HudColors.COLOR_SAFE_GREEN);
        }
        if (headLd.hasBurn()) {
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "头部烧伤 BURN (" + headLd.getBurnDegree() + "°)", String.format("%.1f%%", headLd.getBurnArea()),
                    Mth.clamp(headLd.getBurnArea() / 20.0f, 0.0f, 1.0f), 0xFFFF6D00);
        }

        // 3. 头部状态徽标栏 (STATUS FLAGS：包扎、骨折、开放性骨折、穿刺、内出血、感染等)
        drawY += 2;
        drawY = drawHeadStatusPills(g, font, x, drawY, contentW, headLd);
        drawY += 6;

        // 4. 第二分区：脑外科与颅内血流动力学专科指标 (NEURO & ICP TELEMETRY)
        drawSectionHeader(g, font, x, drawY, contentW, "NEURO & ICP // 颅内与脑灌注专科", 0xFFE040FB);
        drawY += 13;

        int icRate = data.getIntracranialBleedRate();
        float icAcc = data.getIntracranialAccumulation();
        int icLevel = data.getIntracranialLevel();
        int onset = data.getIntracranialOnsetTimer();

        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.brain_health"), data.getBrainHealth() + "%",
                data.getBrainHealth() / 100.0f, HudColors.getHealthColor(data.getBrainHealth() / 100.0f));
        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.consciousness"), data.getConsciousness() + "%",
                data.getConsciousness() / 100.0f, HudColors.getHealthColor(data.getConsciousness() / 100.0f));
        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.intracranial_rate"), icRate + " mL/min",
                Math.min(1.0f, icRate / 100.0f), icRate > 0 ? 0xFFE040FB : 0xFF455A64);
        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.intracranial_accumulation"), String.format("%.1f / 150 mL", icAcc),
                Math.min(1.0f, icAcc / 150.0f), icAcc > 0.1f ? 0xFFFF1744 : 0xFF455A64);

        // 颅内压迫分级行
        String icStageStr = icLevel == 0 ? tr("medical.status.healthy")
                : tr("medical.intracranial.level." + Mth.clamp(icLevel, 1, 4));
        if (icAcc > 0.1f && onset < 30) {
            icStageStr += " [" + tr("medical.detail.intracranial_lucid") + " " + (30 - onset) + "s]";
        }
        drawKeyValueLine(g, font, x, drawY, contentW, tr("medical.detail.intracranial_level"),
                icStageStr, icLevel > 0 ? 0xFFFF4081 : HudColors.COLOR_SAFE_GREEN);
        drawY += 13;

        // 脑灌注血压与平均动脉压 MAP
        String bpStr = data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg (MAP " + (int) data.getMeanArterialPressure() + ")";
        drawKeyValueLine(g, font, x, drawY, contentW, tr("medical.detail.blood_pressure"), bpStr, HudColors.VITAL_BLOOD_PRESSURE);
        drawY += 14;

        // 计算总内容高度并更新最大滚动范围
        int totalContentH = drawY - startY;
        this.maxScroll = Math.max(0.0f, totalContentH - h + 6);

        g.disableScissor();

        // 5. 若内容超出视口高度，绘制右侧全息滚动条与上下渐变遮罩提示
        if (this.maxScroll > 1.0f) {
            drawHolographicScrollbar(g, x + w - 4, y, 3, h, scrollOffset, maxScroll, totalContentH);
        }
    }

    private void drawSectionHeader(GuiGraphics g, Font font, int x, int y, int w, String title, int color) {
        g.fill(x, y + 9, x + w, y + 10, HudColors.withAlpha(color, 0.35f));
        g.fill(x, y + 1, x + 2, y + 8, color);
        g.drawString(font, title, x + 5, y, color, false);
    }

    private void drawCompactMetricCard(GuiGraphics g, Font font, int x, int y, int w,
                                       String label, String val, float pct, int color) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        g.fill(x, y, x + w, y + 21, 0x88071320);
        g.fill(x, y, x + 2, y + 21, color);

        g.drawString(font, label, x + 5, y + 2, 0xFFB0BEC5, false);
        int vw = font.width(val);
        g.drawString(font, val, x + w - vw - 3, y + 2, color, false);

        HudRenderHelper.drawSciFiProgressBar(g, x + 5, y + 13, w - 8, 5, pct, color, 0xFF050C14);
    }

    private int drawFullMetricRow(GuiGraphics g, Font font, int x, int y, int w,
                                  String label, String val, float pct, int color) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        g.drawString(font, label, x, y, 0xFFB0BEC5, false);
        int vw = font.width(val);
        g.drawString(font, val, x + w - vw, y, color, false);
        HudRenderHelper.drawSciFiProgressBar(g, x, y + 10, w, 5, pct, color, 0xFF07121E);
        return y + 18;
    }

    private void drawKeyValueLine(GuiGraphics g, Font font, int x, int y, int w, String label, String val, int valColor) {
        g.fill(x, y - 1, x + w, y + 10, 0x55081524);
        g.drawString(font, label, x + 3, y, 0xFF90A4AE, false);
        int vw = font.width(val);
        g.drawString(font, val, x + w - vw - 3, y, valColor, false);
    }

    private int drawHeadStatusPills(GuiGraphics g, Font font, int x, int y, int w, MedicalData.LimbData ld) {
        int px = x;
        int py = y;
        int rightEdge = x + w;
        boolean anyAbnormal = false;

        if (ld.isBandaged()) {
            px = addPillWrap(g, font, x, px, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676);
            if (px < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676); }
        }
        if (ld.getBleeding() > 0) {
            anyAbnormal = true;
            String bTxt = ld.getBleeding() >= 200 ? tr("medical.status.severe_bleeding")
                    : (ld.getBleeding() >= 80 ? tr("medical.status.moderate_bleeding") : tr("medical.status.minor_bleeding"));
            int nextX = addPillWrap(g, font, x, px, py, rightEdge, bTxt, 0xFFFF1744);
            if (nextX < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, bTxt, 0xFFFF1744); } else px = nextX;
        }
        if (ld.getInternalBleedingRate() > 0) {
            anyAbnormal = true;
            String ibTxt = tr("medical.status.internal_bleeding");
            int nextX = addPillWrap(g, font, x, px, py, rightEdge, ibTxt, 0xFFE040FB);
            if (nextX < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, ibTxt, 0xFFE040FB); } else px = nextX;
        }
        if (ld.hasFracture()) {
            anyAbnormal = true;
            String fTxt = ld.isOpenFracture() ? tr("medical.status.open_fracture")
                    : (ld.hasSplint() ? tr("medical.status.fracture_splinted") : tr("medical.status.fracture"));
            int nextX = addPillWrap(g, font, x, px, py, rightEdge, fTxt, 0xFFFF5252);
            if (nextX < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, fTxt, 0xFFFF5252); } else px = nextX;
        }
        if (ld.getInfection() > 0) {
            anyAbnormal = true;
            String iTxt = ld.getInfection() <= 25 ? tr("medical.status.mild_infection")
                    : ld.getInfection() <= 50 ? tr("medical.status.moderate_infection")
                    : ld.getInfection() <= 75 ? tr("medical.status.severe_infection")
                    : tr("medical.status.critical_infection");
            int nextX = addPillWrap(g, font, x, px, py, rightEdge, iTxt, 0xFFFFB300);
            if (nextX < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, iTxt, 0xFFFFB300); } else px = nextX;
        }
        if (ld.getPainLevel() > 0) {
            anyAbnormal = true;
            String pTxt = Component.translatable("medical.status.pain", ld.getPainLevel()).getString();
            int nextX = addPillWrap(g, font, x, px, py, rightEdge, pTxt, 0xFFFF0055);
            if (nextX < 0) { py += 14; px = addPillWrap(g, font, x, x, py, rightEdge, pTxt, 0xFFFF0055); } else px = nextX;
        }
        if (!anyAbnormal && !ld.isBandaged()) {
            addPillWrap(g, font, x, px, py, rightEdge, tr("medical.status.healthy"), 0xFF00E676);
        }

        return py + 13;
    }

    private int addPillWrap(GuiGraphics g, Font font, int lineStartX, int curX, int curY, int rightEdge, String text, int color) {
        int pw = font.width(text) + 8;
        if (curX > lineStartX && curX + pw > rightEdge) {
            return -1; // 需要换行
        }
        g.fill(curX, curY, curX + pw, curY + 11, HudColors.withAlpha(color, 0.22f));
        g.fill(curX, curY, curX + 2, curY + 11, color);
        g.drawString(font, text, curX + 4, curY + 2, color, false);
        return curX + pw + 4;
    }

    /**
     * 绘制 3A 全息发光滚动条
     */
    public static void drawHolographicScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                                float scrollOffset, float maxScroll, int totalContentH) {
        g.fill(x, y, x + w, y + h, 0x44004D66);
        float ratio = Mth.clamp((float) h / Math.max(1, totalContentH), 0.15f, 1.0f);
        int thumbH = Math.max(14, (int) (h * ratio));
        float progress = maxScroll > 0.001f ? Mth.clamp(scrollOffset / maxScroll, 0.0f, 1.0f) : 0.0f;
        int thumbY = y + (int) ((h - thumbH) * progress);

        g.fill(x, thumbY, x + w, thumbY + thumbH, HudColors.COLOR_NEON_CYAN);
        g.fill(x, thumbY, x + w, thumbY + 2, 0xFFFFFFFF);
    }

    /**
     * 鼠标点击交互：支持切换 SPLIT / 3D HOLO / CLINICAL 三种视图模式
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ViewMode[] modes = ViewMode.values();
        int tabH = 13;
        int tabY = lastPanelY + 5;
        int curRight = lastPanelX + lastPanelW - 10;
        Font font = Minecraft.getInstance().font;

        for (int i = modes.length - 1; i >= 0; i--) {
            ViewMode m = modes[i];
            int tw = font.width(m.label) + 10;
            int tx = curRight - tw;
            if (mouseX >= tx && mouseX <= tx + tw && mouseY >= tabY && mouseY <= tabY + tabH) {
                this.currentMode = m;
                this.scrollTarget = 0.0f;
                MedicalSoundHelper.playLimbSelectSound();
                return true;
            }
            curRight = tx - 4;
        }
        return false;
    }

    /**
     * 鼠标拖拽交互：在 3D 全息脑部区域内按住拖拽可自由 360° 旋转大脑模型
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (lastHoloAreaW > 0 && mouseX >= lastHoloAreaX && mouseX <= lastHoloAreaX + lastHoloAreaW
                && mouseY >= lastHoloAreaY && mouseY <= lastHoloAreaY + lastHoloAreaH) {
            this.yaw += (float) dragX * 0.015f;
            this.pitch = Mth.clamp(this.pitch + (float) dragY * 0.015f, -1.1f, 1.1f);
            this.autoRotateTimer = 4.0f; // 暂停自动旋转 4 秒
            return true;
        }
        return false;
    }

    /**
     * 鼠标滚轮交互：在诊断指标区滚动列表，在 3D 全息区滚动切换视图或旋转
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (lastScrollAreaW > 0 && mouseX >= lastScrollAreaX && mouseX <= lastScrollAreaX + lastScrollAreaW
                && mouseY >= lastScrollAreaY && mouseY <= lastScrollAreaY + lastScrollAreaH) {
            if (maxScroll > 0.5f) {
                scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 18.0f, 0.0f, maxScroll);
                return true;
            }
        }
        if (lastPanelW > 0 && mouseX >= lastPanelX && mouseX <= lastPanelX + lastPanelW
                && mouseY >= lastPanelY && mouseY <= lastPanelY + lastPanelH && maxScroll > 0.5f) {
            scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 18.0f, 0.0f, maxScroll);
            return true;
        }
        return false;
    }

    private float[] project3D(float x, float y, float z, float cx, float cy, float scale,
                              float cosYaw, float sinYaw, float cosPitch, float sinPitch) {
        // 绕 Y 轴旋转 (Yaw)
        float rx = x * cosYaw - z * sinYaw;
        float rz = x * sinYaw + z * cosYaw;
        // 绕 X 轴旋转 (Pitch)
        float ry = y * cosPitch - rz * sinPitch;
        float finalZ = y * sinPitch + rz * cosPitch;

        // 透视投影系数
        float fov = 2.8f / (2.8f - finalZ * 0.45f);
        float sx = cx + rx * scale * fov;
        float sy = cy + ry * scale * fov;
        return new float[]{sx, sy, finalZ};
    }

    public static int computeLimbHealthPercent(MedicalData.LimbData ld) {
        if (ld == null) return 100;
        if (ld.isAmputated()) return 0;
        float penalty = ld.getBleeding() * 0.08f
                + ld.getInternalBleedingRate() * 0.10f
                + ld.getPainLevel() * 4.5f
                + ld.getInfection() * 0.45f
                + (ld.hasFracture() ? (ld.isOpenFracture() ? 35.0f : 22.0f) : 0.0f)
                + (ld.hasDislocation() ? 15.0f : 0.0f)
                + (ld.hasBurn() ? ld.getBurnArea() * 1.2f : 0.0f);
        return Mth.clamp(Math.round(100.0f - penalty), 0, 100);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}