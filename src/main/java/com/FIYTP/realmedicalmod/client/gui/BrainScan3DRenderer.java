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
 * 3A 级全息脑部扫描与头部创伤综合诊断终端 (BrainScan3DRenderer - 零外部符号依赖自包含版)
 *
 * 1. 解剖级四分结构 3D 脑模型（左右大脑皮层半球 + 小脑 + 脑干）与 44 条白质神经突触脉冲束
 * 2. 3D 病理病灶空间映射（颅内血肿、头部外出血、内出血、脑膜炎病灶信标）
 * 3. 完整补全头部 (HEAD) 缺失的外出血、内出血、疼痛、感染、烧伤、状态徽标与颅内专科指标
 * 4. 内置 SPLIT / 3D HOLO / CLINICAL 三视图切换与硬件 Scissor 裁剪平滑滚轮滚动
 */
public class BrainScan3DRenderer {

    // 内置 3A 医疗全息语义配色表（彻底脱离外部 HudColors 符号差异）
    public static final int C_NEON_CYAN = 0xFF00F0FF;
    public static final int C_HOLO_BLUE = 0xFF00A0FF;
    public static final int C_ALERT_RED = 0xFFFF2040;
    public static final int C_WARN_YELLOW = 0xFFFFD000;
    public static final int C_SAFE_GREEN = 0xFF00FF80;
    public static final int C_VITAL_BP = 0xFFFF9900;
    public static final int C_VITAL_PAIN = 0xFFFF0055;
    public static final int C_VITAL_OXYGEN = 0xFF00D2FF;
    public static final int C_VITAL_HEART = 0xFFFF2A55;
    public static final int C_TEXT_LABEL = 0xFF00D2FF;

    public static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255.0f), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    public static int getHealthColor(float pct) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        if (pct > 0.5f) {
            return lerpColor(C_WARN_YELLOW, C_SAFE_GREEN, (pct - 0.5f) * 2.0f);
        } else {
            return lerpColor(C_ALERT_RED, C_WARN_YELLOW, pct * 2.0f);
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public enum ViewMode {
        SPLIT("分屏"),
        HOLO_FULL("全息"),
        CLINICAL_GRID("列表");

        public final String label;
        ViewMode(String label) { this.label = label; }
    }

    private static class BrainVertex {
        final float x, y, z;
        final float nx, ny, nz;
        final int region; // -1: 左半球, 1: 右半球, 2: 小脑, 3: 脑干
        final float foldFactor;
        final int connectIndex;

        BrainVertex(float x, float y, float z, float nx, float ny, float nz, int region, float foldFactor, int connectIndex) {
            this.x = x; this.y = y; this.z = z;
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.region = region;
            this.foldFactor = foldFactor;
            this.connectIndex = connectIndex;
        }
    }

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

    private static class BrainAnchorLabel {
        final String keyTitle;
        final float ox, oy, oz;

        BrainAnchorLabel(String keyTitle, float ox, float oy, float oz) {
            this.keyTitle = keyTitle;
            this.ox = ox; this.oy = oy; this.oz = oz;
        }
    }

    private final List<BrainVertex> vertices = new ArrayList<>();
    private final List<SynapseTract> synapseTracts = new ArrayList<>();
    private final List<OrbitParticle> orbitParticles = new ArrayList<>();
    private final List<BrainAnchorLabel> anchorLabels = new ArrayList<>();

    private float yaw = -0.45f;
    private float pitch = 0.22f;
    private float autoRotateTimer = 0.0f;
    private long lastRenderTimeMs = 0L;

    private ViewMode currentMode = ViewMode.SPLIT;
    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private float maxScroll = 0.0f;

    private int lastPanelX, lastPanelY, lastPanelW, lastPanelH;
    private int lastScrollAreaX, lastScrollAreaY, lastScrollAreaW, lastScrollAreaH;
    private int lastHoloAreaX, lastHoloAreaY, lastHoloAreaW, lastHoloAreaH;

    public BrainScan3DRenderer() {
        generateAnatomicalBrainMesh();
        initSynapseConnectome();
        initOrbitParticles();
        initAnchorLabels();
    }

    private void generateAnatomicalBrainMesh() {
        vertices.clear();
        int latSteps = 22;
        int lonSteps = 34;
        for (int hemi = -1; hemi <= 1; hemi += 2) {
            int startIdx = vertices.size();
            for (int i = 1; i < latSteps; i++) {
                float v = i / (float) latSteps;
                float theta = v * (float) Math.PI;
                float sinTheta = Mth.sin(theta);
                float cosTheta = Mth.cos(theta);

                for (int j = 0; j < lonSteps; j++) {
                    float u = j / (float) lonSteps;
                    float phi = u * (float) Math.PI;
                    float sinPhi = Mth.sin(phi);
                    float cosPhi = Mth.cos(phi);

                    float rawX = hemi * (0.065f + sinTheta * sinPhi * 0.76f);
                    float rawY = -cosTheta * 0.86f;
                    float rawZ = sinTheta * cosPhi * 1.16f;

                    if (rawZ > 0.2f) {
                        rawX *= 1.0f + 0.08f * (rawZ / 1.16f);
                    }
                    if (rawZ < -0.4f) {
                        rawY += 0.08f * ((-rawZ - 0.4f) / 0.76f);
                    }
                    float temporalMask = Math.max(0.0f, 1.0f - Math.abs(rawZ - 0.05f) * 1.6f)
                            * Math.max(0.0f, (rawY - 0.05f) * 1.4f);
                    rawX += hemi * temporalMask * 0.16f;
                    rawY += temporalMask * 0.08f;

                    if (rawY > 0.35f) {
                        rawY = 0.35f + (rawY - 0.35f) * 0.42f;
                    }

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
            int totalHemi = vertices.size() - startIdx;
            if (totalHemi > lonSteps) {
                for (int k = startIdx + lonSteps; k < vertices.size(); k += 3) {
                    BrainVertex v = vertices.get(k);
                    vertices.set(k, new BrainVertex(v.x, v.y, v.z, v.nx, v.ny, v.nz, v.region, v.foldFactor, k - lonSteps));
                }
            }
        }

        // 小脑 (Cerebellum)
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
                    float folia = 0.5f + 0.5f * Mth.sin(cy * 32.0f);
                    int prev = (j > 0) ? (vertices.size() - 1) : -1;
                    vertices.add(new BrainVertex(cx, cy, cz, 0.0f, 0.3f, -0.9f, 2, folia, prev));
                }
            }
        }

        // 脑干 (Brainstem)
        int stemRings = 9;
        int stemSegs = 14;
        for (int r = 0; r < stemRings; r++) {
            float t = r / (float) (stemRings - 1);
            float sy = 0.36f + t * 0.62f;
            float szCenter = -0.18f - t * 0.14f;
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

    private void initSynapseConnectome() {
        synapseTracts.clear();
        Random rand = new Random(2077042L);
        if (vertices.size() < 40) return;

        for (int i = 0; i < 44; i++) {
            BrainVertex a = vertices.get(rand.nextInt(vertices.size()));
            BrainVertex b = vertices.get(rand.nextInt(vertices.size()));
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
        anchorLabels.add(new BrainAnchorLabel("额叶", -0.52f, -0.36f, 0.72f));
        anchorLabels.add(new BrainAnchorLabel("顶叶", 0.56f, -0.62f, -0.08f));
        anchorLabels.add(new BrainAnchorLabel("颞叶", 0.78f, 0.12f, 0.18f));
        anchorLabels.add(new BrainAnchorLabel("枕叶", -0.45f, -0.10f, -0.92f));
        anchorLabels.add(new BrainAnchorLabel("小脑", 0.38f, 0.48f, -0.78f));
        anchorLabels.add(new BrainAnchorLabel("脑干", 0.0f, 0.82f, -0.28f));
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       MedicalData data, float deltaSeconds) {
        Minecraft mc = Minecraft.getInstance();
        int mx = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
        int my = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        render(graphics, font, x, y, width, height, data, deltaSeconds, mx, my, System.currentTimeMillis());
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                       MedicalData data, float deltaSeconds, int mouseX, int mouseY, long now) {
        if (data == null || width < 80 || height < 60) return;

        this.lastPanelX = x;
        this.lastPanelY = y;
        this.lastPanelW = width;
        this.lastPanelH = height;

        if (lastRenderTimeMs == 0L) lastRenderTimeMs = now;
        float dt = Mth.clamp((now - lastRenderTimeMs) / 1000.0f, 0.001f, 0.1f);
        lastRenderTimeMs = now;

        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
        scrollOffset = Mth.lerp(Math.min(1.0f, dt * 14.0f), scrollOffset, scrollTarget);

        if (autoRotateTimer > 0.0f) {
            autoRotateTimer = Math.max(0.0f, autoRotateTimer - dt);
        } else {
            yaw = (yaw + dt * 0.42f) % ((float) Math.PI * 2.0f);
            pitch = Mth.lerp(Math.min(1.0f, dt * 2.0f), pitch, 0.20f + 0.08f * Mth.sin(now * 0.0012f));
        }

        drawViewModeTabs(graphics, font, x, y, width, mouseX, mouseY);

        int contentTop = y + 24;
        int contentH = height - 30;
        int contentX = x + 10;
        int contentW = width - 20;

        if (currentMode == ViewMode.HOLO_FULL) {
            this.lastHoloAreaX = contentX;
            this.lastHoloAreaY = contentTop;
            this.lastHoloAreaW = contentW;
            this.lastHoloAreaH = contentH;
            this.lastScrollAreaW = 0;

            render3DBrainViewport(graphics, font, contentX, contentTop, contentW, contentH, data, dt, now, true);
            drawCornerTacticalOverlays(graphics, font, contentX, contentTop, contentW, contentH, data);
        } else if (currentMode == ViewMode.CLINICAL_GRID || contentW < 210) {
            this.lastHoloAreaW = 0;
            this.lastScrollAreaX = contentX;
            this.lastScrollAreaY = contentTop;
            this.lastScrollAreaW = contentW;
            this.lastScrollAreaH = contentH;

            renderScrollableHeadTelemetry(graphics, font, contentX, contentTop, contentW, contentH, data, true);
        } else {
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

            HudRenderHelper.drawSciFiCard(graphics, contentX, contentTop, holoW, contentH, 4, 0x99040A12, 0x5500E5FF);
            render3DBrainViewport(graphics, font, contentX, contentTop, holoW, contentH, data, dt, now, false);

            HudRenderHelper.drawSciFiCard(graphics, diagX, contentTop, diagW, contentH, 4, 0x99040A12, 0x4400A0FF);
            renderScrollableHeadTelemetry(graphics, font, diagX + 6, contentTop + 4, diagW - 12, contentH - 8, data, false);
        }
    }

    private void drawViewModeTabs(GuiGraphics g, Font font, int panelX, int panelY, int panelW, int mouseX, int mouseY) {
        ViewMode[] modes = ViewMode.values();
        int tabH = 13;
        int tabY = panelY + 5;
        int curRight = panelX + panelW - 10;

        for (int i = modes.length - 1; i >= 0; i--) {
            ViewMode m = modes[i];
            int tw = font.width(m.label) + 10;
            int tx = curRight - tw;
            if (tx < panelX + 140) break;

            boolean active = (this.currentMode == m);
            boolean hovered = mouseX >= tx && mouseX <= tx + tw && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = active ? 0xCC004D66 : (hovered ? 0x990A2233 : 0x66050E17);
            int border = active ? C_NEON_CYAN : (hovered ? 0xAA00E5FF : 0x4400A0FF);
            int txtColor = active ? 0xFFFFFFFF : (hovered ? C_NEON_CYAN : 0xFF90A4AE);

            HudRenderHelper.drawSciFiCard(g, tx, tabY, tw, tabH, 2, bg, border);
            g.drawString(font, m.label, tx + 5, tabY + 3, txtColor, false);

            curRight = tx - 4;
        }
    }

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

        drawHologramGimbalRings(g, cx, cy, scale, brainHealth, icLevel);

        float cosYaw = Mth.cos(yaw);
        float sinYaw = Mth.sin(yaw);
        float cosPitch = Mth.cos(pitch);
        float sinPitch = Mth.sin(pitch);

        float scanCycle = (now % 3200L) / 3200.0f;
        float scanPlaneY = -1.05f + scanCycle * 2.10f;
        float glitchIntensity = (brainHealth < 85) ? Mth.clamp((85 - brainHealth) / 85.0f, 0.0f, 1.0f) : 0.0f;

        Matrix4f pose = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        drawSynapseConnectomeAndPulses(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, dt, data, headPain);
        drawOrbitingParticles(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, dt, brainHealth);

        if (icVol > 0.1f || headBleed > 0 || headInternal > 0 || headInf > 0) {
            draw3DPathologyLesions(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, now,
                    icVol, icLevel, headBleed, headInternal, headInf);
        }

        drawAnatomicalBrainMesh(pose, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch,
                scanPlaneY, glitchIntensity, brainHealth, headPain, now);

        RenderSystem.defaultBlendFunc();

        int scanScreenY = (int) (cy + scanPlaneY * scale * 0.85f);
        if (scanScreenY > vy + 10 && scanScreenY < vy + vh - 28) {
            int scanW = (int) (scale * 2.1f);
            g.fill((int) cx - scanW / 2, scanScreenY, (int) cx + scanW / 2, scanScreenY + 1, 0x8800F0FF);
            g.fillGradient((int) cx - scanW / 2, scanScreenY - 8, (int) cx + scanW / 2, scanScreenY, 0x0000F0FF, 0x3300F0FF);
        }

        draw3DFloatingLabels(g, font, vx, vy, vw, vh, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch, data, fullMode);
        drawHoloFooterBar(g, font, vx, vy + vh - 26, vw, 24, brainHealth, consciousness);

        g.disableScissor();
    }

    private void drawHologramGimbalRings(GuiGraphics g, float cx, float cy, float scale, int brainHealth, int icLevel) {
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
            float py = cy + Mth.sin(ang) * ringR * 0.35f;
            buf.addVertex(pose, px, py + scale * 0.72f, 0.0f).setColor(r, gr, b, 55);
        }
        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void drawSynapseConnectomeAndPulses(Matrix4f pose, float cx, float cy, float scale,
                                                float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                                float dt, MedicalData data, int headPain) {
        if (synapseTracts.isEmpty()) return;

        float speedMul = (data.getConsciousness() / 100.0f) * (data.getHeartRate() / 75.0f);
        speedMul = Mth.clamp(speedMul, 0.25f, 2.5f);
        boolean painStorm = headPain >= 5 || data.getBrainHealth() < 60;

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SynapseTract tract : synapseTracts) {
            tract.phase = (tract.phase + dt * tract.speed * speedMul) % 1.0f;

            float[] p1 = project3D(tract.x1, tract.y1, tract.z1, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float[] p2 = project3D(tract.x2, tract.y2, tract.z2, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);

            PainVisualOverlay.addQuadSegment(buf, pose, p1[0], p1[1], p2[0], p2[1], 0.45f,
                    0, 170, 230, 28, 28);

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

    private void draw3DPathologyLesions(Matrix4f pose, float cx, float cy, float scale,
                                        float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                        long now, float icVol, int icLevel, int headBleed, int headInternal, int headInf) {
        float pulse = 0.5f + 0.5f * Mth.sin(now * 0.008f);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        if (icVol > 0.1f || icLevel > 0) {
            float[] center = project3D(0.42f, -0.25f, 0.15f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (6.0f + Math.min(18.0f, icVol * 0.12f) + icLevel * 3.0f) * (0.85f + 0.25f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 16, 48, (int) (130 + 80 * pulse));
        }
        if (headInternal > 0) {
            float[] center = project3D(-0.38f, -0.15f, -0.20f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (5.0f + Math.min(12.0f, headInternal * 0.05f)) * (0.85f + 0.25f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 210, 30, 255, 175);
        }
        if (headBleed > 0) {
            float[] center = project3D(-0.45f, -0.52f, 0.45f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (5.0f + Math.min(14.0f, headBleed * 0.04f)) * (0.8f + 0.3f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 45, 20, 195);
        }
        if (headInf > 0) {
            float[] center = project3D(0.25f, 0.10f, 0.55f, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            float radius = (4.5f + headInf * 0.10f) * (0.85f + 0.2f * pulse);
            drawGlowDisc(buf, pose, center[0], center[1], radius, 255, 195, 0, 170);
        }

        MeshData mesh = buf.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private void drawGlowDisc(BufferBuilder buf, Matrix4f pose, float x, float y, float radius, int r, int g, int b, int centerAlpha) {
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

    private void drawAnatomicalBrainMesh(Matrix4f pose, float cx, float cy, float scale,
                                         float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                         float scanPlaneY, float glitchIntensity,
                                         int brainHealth, int headPain, long now) {
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

            if (glitchIntensity > 0.01f && ((i + (int) (now / 60L)) % 29 == 0)) {
                vx += Mth.sin(now * 0.05f + i) * 0.14f * glitchIntensity;
            }

            float[] proj = project3D(vx, vy, vz, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);
            px[i] = proj[0];
            py[i] = proj[1];
            pz[i] = proj[2];

            float depthFactor = Mth.clamp((proj[2] + 1.35f) / 2.5f, 0.18f, 1.0f);
            float rim = 1.0f - Math.min(1.0f, Math.abs(proj[2]) * 0.75f);
            float foldBright = 0.55f + 0.45f * v.foldFactor + 0.25f * rim;

            int r, g, b;
            if (v.region == 2) {
                r = (int) Mth.lerp(healthPct, 255, 20);
                g = (int) Mth.lerp(healthPct, 40, 175);
                b = (int) Mth.lerp(healthPct, 80, 255);
            } else if (v.region == 3) {
                r = (int) Mth.lerp(healthPct, 255, 0);
                g = (int) Mth.lerp(healthPct, 80, 255);
                b = (int) Mth.lerp(healthPct, 60, 180);
            } else {
                r = (int) Mth.lerp(healthPct, 255, 0);
                g = (int) Mth.lerp(healthPct, 30, 235);
                b = (int) Mth.lerp(healthPct, 65, 255);
            }

            if (headPain > 0) {
                float painWave = Mth.sin(v.y * 6.0f - v.z * 4.0f + painWavePhase);
                if (painWave > 0.75f) {
                    float pf = (headPain / 10.0f) * ((painWave - 0.75f) / 0.25f);
                    r = (int) Mth.lerp(pf, r, 255);
                    g = (int) Mth.lerp(pf, g, 20);
                    b = (int) Mth.lerp(pf, b, 95);
                }
            }

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

    private void draw3DFloatingLabels(GuiGraphics g, Font font, int vx, int vy, int vw, int vh,
                                      float cx, float cy, float scale,
                                      float cosYaw, float sinYaw, float cosPitch, float sinPitch,
                                      MedicalData data, boolean fullMode) {
        int maxLabels = fullMode ? anchorLabels.size() : 3;
        int shown = 0;

        for (int i = 0; i < anchorLabels.size() && shown < maxLabels; i++) {
            BrainAnchorLabel lbl = anchorLabels.get(i);
            float[] proj = project3D(lbl.ox, lbl.oy, lbl.oz, cx, cy, scale, cosYaw, sinYaw, cosPitch, sinPitch);

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

            int color = (data.getBrainHealth() < 60) ? C_WARN_YELLOW : C_NEON_CYAN;
            HudRenderHelper.drawLine(g, ax, ay, elbowX, elbowY, 0xAA00E5FF, 1);
            HudRenderHelper.drawDiamond(g, ax, ay, 2, color);

            g.fill(labelX - 2, labelY - 1, labelX + tw + 2, labelY + 9, 0xBB040B14);
            g.drawString(font, text, labelX, labelY, color, false);
        }
    }

    private void drawHoloFooterBar(GuiGraphics g, Font font, int x, int y, int w, int h, int brainHealth, int consciousness) {
        g.fill(x + 4, y, x + w - 4, y + h, 0xAA050D17);
        int halfW = (w - 20) / 2;

        int bhColor = getHealthColor(brainHealth / 100.0f);
        g.drawString(font, "脑健康 " + brainHealth + "%", x + 8, y + 3, bhColor, false);
        HudRenderHelper.drawSciFiProgressBar(g, x + 8, y + 14, halfW, 5, brainHealth / 100.0f, bhColor, 0xFF0A1926);

        int rx = x + 12 + halfW;
        int csColor = getHealthColor(consciousness / 100.0f);
        g.drawString(font, "意识 " + consciousness + "%", rx, y + 3, csColor, false);
        HudRenderHelper.drawSciFiProgressBar(g, rx, y + 14, halfW, 5, consciousness / 100.0f, csColor, 0xFF0A1926);
    }

    private void drawCornerTacticalOverlays(GuiGraphics g, Font font, int x, int y, int w, int h, MedicalData data) {
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd == null) return;

        int cardW = Math.min(135, (w - 24) / 2);
        int topY = y + 6;

        int bleed = headLd.getBleeding();
        int inBleed = headLd.getInternalBleedingRate();
        int c1 = (bleed > 0 || inBleed > 0) ? C_ALERT_RED : C_SAFE_GREEN;
        HudRenderHelper.drawTacticalBadge(g, font, x + 6, topY, cardW, 24,
                "外出血: " + bleed + " mL/m",
                "内出血: " + inBleed + " mL/m",
                c1, 0xCC06101A);

        int pain = headLd.getPainLevel();
        int inf = headLd.getInfection();
        int c2 = (pain >= 5 || inf >= 25) ? C_WARN_YELLOW : C_NEON_CYAN;
        HudRenderHelper.drawTacticalBadge(g, font, x + w - cardW - 6, topY, cardW, 24,
                "头部疼痛: " + pain + " / 10",
                "头部感染: " + inf + "%",
                c2, 0xCC06101A);

        int botY = y + h - 54;
        int icLevel = data.getIntracranialLevel();
        float icVol = data.getIntracranialAccumulation();
        int c3 = icLevel > 0 ? 0xFFE040FB : C_HOLO_BLUE;
        HudRenderHelper.drawTacticalBadge(g, font, x + 6, botY, cardW, 24,
                String.format("颅内积血: %.1f mL", icVol),
                "颅内分级: LV." + icLevel + (data.getIntracranialOnsetTimer() > 0 && data.getIntracranialOnsetTimer() < 30 ? " (清醒期)" : ""),
                c3, 0xCC06101A);

        int sbp = data.getSystolicBP();
        int dbp = data.getDiastolicBP();
        float map = data.getMeanArterialPressure();
        HudRenderHelper.drawTacticalBadge(g, font, x + w - cardW - 6, botY, cardW, 24,
                "血压: " + sbp + "/" + dbp + " mmHg",
                String.format("脑灌注 MAP: %.0f mmHg", map),
                C_VITAL_BP, 0xCC06101A);
    }

    private void renderScrollableHeadTelemetry(GuiGraphics g, Font font, int x, int y, int w, int h,
                                               MedicalData data, boolean forceTwoColumn) {
        MedicalData.LimbData headLd = data.getLimb(MedicalData.Limb.HEAD);
        if (headLd == null) return;

        int contentW = w - 8;
        g.enableScissor(x, y, x + w, y + h);

        int drawY = (int) (y - scrollOffset);
        int startY = drawY;

        int headHealth = computeLimbHealthPercent(headLd);
        int hc = getHealthColor(headHealth / 100.0f);
        g.drawString(font, "头部完整度", x, drawY, C_TEXT_LABEL, false);
        String hpText = headHealth + "%";
        g.drawString(font, hpText, x + contentW - font.width(hpText), drawY, hc, false);
        drawY += 11;
        HudRenderHelper.drawSciFiProgressBar(g, x, drawY, contentW, 6, headHealth / 100.0f, hc, 0xFF0A1824);
        drawY += 12;

        drawSectionHeader(g, font, x, drawY, contentW, "头部创伤", 0xFF00E5FF);
        drawY += 13;

        int bleed = headLd.getBleeding();
        int inBleed = headLd.getInternalBleedingRate();
        int pain = headLd.getPainLevel();
        int inf = headLd.getInfection();

        boolean useTwoCol = forceTwoColumn || contentW >= 195;
        if (useTwoCol) {
            int colW = (contentW - 6) / 2;
            drawCompactMetricCard(g, font, x, drawY, colW,
                    tr("medical.detail.bleeding_rate"), bleed + " mL/m",
                    Math.min(1.0f, bleed / 400.0f), bleed > 0 ? C_ALERT_RED : 0xFF37474F);
            drawCompactMetricCard(g, font, x + colW + 6, drawY, colW,
                    tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/m",
                    Math.min(1.0f, inBleed / 250.0f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
            drawY += 24;

            drawCompactMetricCard(g, font, x, drawY, colW,
                    tr("medical.detail.pain_level"), pain + " / 10",
                    pain / 10.0f, pain > 0 ? C_VITAL_PAIN : 0xFF37474F);
            drawCompactMetricCard(g, font, x + colW + 6, drawY, colW,
                    tr("medical.detail.infection"), inf + " %",
                    inf / 100.0f, inf > 0 ? C_WARN_YELLOW : 0xFF37474F);
            drawY += 24;
        } else {
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "外出血", bleed + " mL/min",
                    Math.min(1.0f, bleed / 400.0f), bleed > 0 ? C_ALERT_RED : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "内出血", inBleed + " mL/min",
                    Math.min(1.0f, inBleed / 250.0f), inBleed > 0 ? 0xFFE040FB : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "疼痛", pain + " / 10",
                    pain / 10.0f, pain > 0 ? C_VITAL_PAIN : 0xFF455A64);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "感染", inf + " %",
                    inf / 100.0f, inf > 0 ? C_WARN_YELLOW : 0xFF455A64);
        }

        if (bleed > 0 && bleed <= 50 && headLd.getNaturalClottingTimer() > 0) {
            float clotPct = Mth.clamp(headLd.getNaturalClottingTimer() / 180.0f, 0.0f, 1.0f);
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    tr("medical.detail.bleeding_progress"), (int) (clotPct * 100) + "%",
                    clotPct, C_SAFE_GREEN);
        }
        if (headLd.hasBurn()) {
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    "烧伤 (" + headLd.getBurnDegree() + "°)", String.format("%.1f%%", headLd.getBurnArea() * 100.0f),
                    Mth.clamp((headLd.getBurnArea() * 100.0f) / 20.0f, 0.0f, 1.0f), 0xFFFF6D00);
        }

        drawY += 2;
        drawY = drawHeadStatusPills(g, font, x, drawY, contentW, headLd);
        drawY += 6;

        drawSectionHeader(g, font, x, drawY, contentW, "颅内与脑灌注", 0xFFE040FB);
        drawY += 13;

        int icRate = data.getIntracranialBleedRate();
        float icAcc = data.getIntracranialAccumulation();
        int icLevel = data.getIntracranialLevel();
        int onset = data.getIntracranialOnsetTimer();

        if (forceTwoColumn) {
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    tr("medical.detail.brain_health"), data.getBrainHealth() + "%",
                    data.getBrainHealth() / 100.0f, getHealthColor(data.getBrainHealth() / 100.0f));
            drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                    tr("medical.detail.consciousness"), data.getConsciousness() + "%",
                    data.getConsciousness() / 100.0f, getHealthColor(data.getConsciousness() / 100.0f));
        }
        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.intracranial_rate"), icRate + " mL/min",
                Math.min(1.0f, icRate / 100.0f), icRate > 0 ? 0xFFE040FB : 0xFF455A64);
        drawY = drawFullMetricRow(g, font, x, drawY, contentW,
                tr("medical.detail.intracranial_accumulation"), String.format("%.1f / 150 mL", icAcc),
                Math.min(1.0f, icAcc / 150.0f), icAcc > 0.1f ? 0xFFFF1744 : 0xFF455A64);

        String icStageStr = icLevel == 0 ? tr("medical.status.healthy")
                : tr("medical.intracranial.level." + Mth.clamp(icLevel, 1, 4));
        if (icAcc > 0.1f && onset < 30) {
            icStageStr += " [" + tr("medical.detail.intracranial_lucid") + " " + (30 - onset) + "s]";
        }
        drawKeyValueLine(g, font, x, drawY, contentW, tr("medical.detail.intracranial_level"),
                icStageStr, icLevel > 0 ? 0xFFFF4081 : C_SAFE_GREEN);
        drawY += 13;

        String bpStr = data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg (MAP " + (int) data.getMeanArterialPressure() + ")";
        drawKeyValueLine(g, font, x, drawY, contentW, tr("medical.detail.blood_pressure"), bpStr, C_VITAL_BP);
        drawY += 14;

        int totalContentH = drawY - startY;
        this.maxScroll = Math.max(0.0f, totalContentH - h + 6);

        g.disableScissor();

        if (this.maxScroll > 1.0f) {
            drawHolographicScrollbar(g, x + w - 4, y, 3, h, scrollOffset, maxScroll, totalContentH);
        }
    }

    private void drawSectionHeader(GuiGraphics g, Font font, int x, int y, int w, String title, int color) {
        g.fill(x, y + 9, x + w, y + 10, withAlpha(color, 0.35f));
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
            return -1;
        }
        g.fill(curX, curY, curX + pw, curY + 11, withAlpha(color, 0.22f));
        g.fill(curX, curY, curX + 2, curY + 11, color);
        g.drawString(font, text, curX + 4, curY + 2, color, false);
        return curX + pw + 4;
    }

    public static void drawHolographicScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                                float scrollOffset, float maxScroll, int totalContentH) {
        g.fill(x, y, x + w, y + h, 0x44004D66);
        float ratio = Mth.clamp((float) h / Math.max(1, totalContentH), 0.15f, 1.0f);
        int thumbH = Math.max(14, (int) (h * ratio));
        float progress = maxScroll > 0.001f ? Mth.clamp(scrollOffset / maxScroll, 0.0f, 1.0f) : 0.0f;
        int thumbY = y + (int) ((h - thumbH) * progress);

        g.fill(x, thumbY, x + w, thumbY + thumbH, C_NEON_CYAN);
        g.fill(x, thumbY, x + w, thumbY + 2, 0xFFFFFFFF);
    }

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

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (lastHoloAreaW > 0 && mouseX >= lastHoloAreaX && mouseX <= lastHoloAreaX + lastHoloAreaW
                && mouseY >= lastHoloAreaY && mouseY <= lastHoloAreaY + lastHoloAreaH) {
            this.yaw += (float) dragX * 0.015f;
            this.pitch = Mth.clamp(this.pitch + (float) dragY * 0.015f, -1.1f, 1.1f);
            this.autoRotateTimer = 4.0f;
            return true;
        }
        return false;
    }

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
        float rx = x * cosYaw - z * sinYaw;
        float rz = x * sinYaw + z * cosYaw;
        float ry = y * cosPitch - rz * sinPitch;
        float finalZ = y * sinPitch + rz * cosPitch;

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
                + (ld.hasBurn() ? (ld.getBurnArea() * 100.0f) * 1.2f : 0.0f);
        return Mth.clamp(Math.round(100.0f - penalty), 0, 100);
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}