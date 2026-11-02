package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 3D 全息大脑扫描视图渲染器（阶段 7 升级版）
 * 包含：
 * 1. 3D 旋转左右双半球大脑参数化模型、脑沟脑回起伏褶皱、多层全息外晕
 * 2. 环绕轨道发光粒子流（Orbiting Hologram Particles）
 * 3. 背景数据星云粒子群（Cyber Nebula Data Cloud）
 * 4. 大脑表面 3D 浮动文字标签打字机流式展现（Typewriter reveal effect）
 * 5. 5 级脑健康自适应颜色与动态脉冲/故障撕裂
 * 6. 下方科幻诊断指标（巨大 LCD 读数、状态徽标、病理详细参数）
 */
public class BrainScan3DRenderer {

    // 脑表面顶点数据结构
    public static class BrainVertex {
        public final float x, y, z;
        public final float nx, ny, nz;
        public final float hemisphere;
        public final float foldFactor;

        public BrainVertex(float x, float y, float z, float nx, float ny, float nz, float hemisphere, float foldFactor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.hemisphere = hemisphere;
            this.foldFactor = foldFactor;
        }
    }

    // 脑表面浮动文字标签（支持打字机效果）
    public static class FloatingBrainLabel {
        public final String fullText;
        public final float ox, oy, oz; // 归一化球坐标/表面局部坐标
        public final int category;     // 0 = 物品, 1 = 词汇, 2 = 乱码
        public final long createTime;
        public float animOffset;

        public FloatingBrainLabel(String text, float ox, float oy, float oz, int category) {
            this.fullText = text;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.category = category;
            this.createTime = System.currentTimeMillis();
            this.animOffset = (float) (Math.random() * Math.PI * 2);
        }

        public String getDisplayText() {
            long age = System.currentTimeMillis() - createTime;
            if (category == 2) {
                // 乱码直接展示
                return fullText;
            }
            // 每 60ms 揭晓一个字符
            int visibleChars = Math.min(fullText.length(), (int) (age / 60L) + 1);
            return fullText.substring(0, visibleChars);
        }
    }

    // 轨道环绕粒子
    private static class OrbitParticle {
        float angle;
        float speed;
        float orbitRadiusX;
        float orbitRadiusY;
        float orbitRadiusZ;
        float tiltX;
        float tiltY;
        float size;
        float alpha;

        OrbitParticle(Random rand) {
            this.angle = rand.nextFloat() * (float) Math.PI * 2.0f;
            this.speed = 0.8f + rand.nextFloat() * 1.5f;
            this.orbitRadiusX = 48.0f + rand.nextFloat() * 24.0f;
            this.orbitRadiusY = 32.0f + rand.nextFloat() * 18.0f;
            this.orbitRadiusZ = 45.0f + rand.nextFloat() * 25.0f;
            this.tiltX = (rand.nextFloat() - 0.5f) * 0.8f;
            this.tiltY = (rand.nextFloat() - 0.5f) * 0.8f;
            this.size = 1.0f + rand.nextFloat() * 1.8f;
            this.alpha = 0.3f + rand.nextFloat() * 0.6f;
        }

        void update(float dt) {
            angle = (angle + speed * dt) % ((float) Math.PI * 2.0f);
        }
    }

    // 背景星云数据粒子
    private static class NebulaParticle {
        float x, y;
        float vx, vy;
        float alpha;
        float maxAlpha;
        float size;

        NebulaParticle(Random rand) {
            respawn(rand, true);
        }

        void respawn(Random rand, boolean randomAlpha) {
            this.x = (rand.nextFloat() - 0.5f) * 160.0f;
            this.y = (rand.nextFloat() - 0.5f) * 120.0f;
            this.vx = (rand.nextFloat() - 0.5f) * 4.0f;
            this.vy = (rand.nextFloat() - 0.5f) * 4.0f - 2.0f; // 整体微弱向上飘移
            this.maxAlpha = 0.15f + rand.nextFloat() * 0.35f;
            this.alpha = randomAlpha ? rand.nextFloat() * maxAlpha : 0.0f;
            this.size = 1.0f + rand.nextFloat() * 1.5f;
        }

        void update(float dt, Random rand) {
            x += vx * dt;
            y += vy * dt;
            alpha += dt * 0.2f;
            if (alpha > maxAlpha || Math.abs(x) > 85.0f || Math.abs(y) > 65.0f) {
                respawn(rand, false);
            }
        }
    }

    // 预生成的几何网格
    private static final List<BrainVertex> VERTICES = new ArrayList<>();
    private static final List<int[]> LINE_INDICES = new ArrayList<>();
    private static final List<int[]> TRIANGLE_INDICES = new ArrayList<>();
    private static boolean isMeshInitialized = false;

    // 抽象心理/神经系统词汇库
    private static final String[] PSYCHO_TERMS = {
            "MEMORY", "FEAR", "PANIC", "NEURON", "SYNAPSE", "VOID", "PAIN",
            "SIGNAL_LOST", "ANXIETY", "PRESSURE", "OXYGEN_LOW", "DYSPNEA",
            "CEREBRAL", "ISCHEMIA", "VERTIGO", "REFLEX", "HYPOXIA", "AMNESIA"
    };

    // 乱码符号库
    private static final String[] GLITCH_BLOCKS = {
            "▚", "▞", "▜", "▟", "▖", "▗", "▘", "▝", "░", "▒", "▓", "█"
    };

    // 表面文字系统状态
    private final List<FloatingBrainLabel> activeLabels = new ArrayList<>();
    private long lastTextUpdateTime = 0L;
    private final Random textRandom = new Random();

    // 粒子系统
    private final List<OrbitParticle> orbitParticles = new ArrayList<>();
    private final List<NebulaParticle> nebulaParticles = new ArrayList<>();

    // 旋转与动画状态
    private float rotationAngle = 0.0f;
    private float scanlineYRatio = 0.0f; // 0.0 ~ 1.0 扫描线位置

    public BrainScan3DRenderer() {
        initMeshIfNeeded();
        Random rand = new Random(42);
        for (int i = 0; i < 28; i++) {
            orbitParticles.add(new OrbitParticle(rand));
        }
        for (int i = 0; i < 40; i++) {
            nebulaParticles.add(new NebulaParticle(rand));
        }
    }

    /**
     * 静态初始化大脑 3D 参数化网格
     */
    public static synchronized void initMeshIfNeeded() {
        if (isMeshInitialized) return;

        final int uSegments = 16;
        final int vSegments = 14;

        // 生成左右两个半球
        // hemi = -1 为左半球，hemi = 1 为右半球
        for (int hemi = -1; hemi <= 1; hemi += 2) {
            int baseVertexIndex = VERTICES.size();

            for (int iv = 0; iv <= vSegments; iv++) {
                float v = (float) iv / vSegments; // 0.0 (北极) ~ 1.0 (南极)
                float theta = v * (float) Math.PI;

                for (int iu = 0; iu <= uSegments; iu++) {
                    float u = (float) iu / uSegments; // 0.0 ~ 1.0
                    float phi = (hemi == 1) ? (u * (float) Math.PI) : ((float) Math.PI + u * (float) Math.PI);

                    // 基础椭球半径
                    float rx = 32.0f;
                    float ry = 28.0f;
                    float rz = 38.0f;

                    // 脑沟脑回起伏算法：多阶谐波扰动
                    float fold1 = (float) Math.sin(theta * 7.5f) * (float) Math.cos(phi * 8.0f);
                    float fold2 = (float) Math.sin(theta * 14.0f + phi * 6.0f) * 0.5f;
                    float foldFactor = fold1 * 0.7f + fold2 * 0.3f; // [-1.0, 1.0] 凹凸系数

                    float rScale = 1.0f + foldFactor * 0.12f;

                    // 左右半球分离位移（脑纵裂纵深感）
                    float hemiGap = hemi * 3.5f;

                    float vx = (float) (Math.sin(theta) * Math.sin(phi)) * rx * rScale + hemiGap;
                    float vy = -(float) Math.cos(theta) * ry * rScale; // 负号让北极在上方
                    float vz = (float) (Math.sin(theta) * Math.cos(phi)) * rz * rScale;

                    Vector3f normal = new Vector3f(vx - hemiGap, vy, vz).normalize();
                    VERTICES.add(new BrainVertex(vx, vy, vz, normal.x, normal.y, normal.z, hemi, foldFactor));
                }
            }

            // 构建半球网格索引
            int rowSize = uSegments + 1;
            for (int iv = 0; iv < vSegments; iv++) {
                for (int iu = 0; iu < uSegments; iu++) {
                    int i0 = baseVertexIndex + iv * rowSize + iu;
                    int i1 = i0 + 1;
                    int i2 = baseVertexIndex + (iv + 1) * rowSize + iu;
                    int i3 = i2 + 1;

                    // 经纬线框段 (线框渲染)
                    LINE_INDICES.add(new int[]{i0, i1});
                    LINE_INDICES.add(new int[]{i0, i2});
                    if (iv == vSegments - 1) LINE_INDICES.add(new int[]{i2, i3});
                    if (iu == uSegments - 1) LINE_INDICES.add(new int[]{i1, i3});

                    // 表面三角形 (半透明填充与光照)
                    TRIANGLE_INDICES.add(new int[]{i0, i2, i1});
                    TRIANGLE_INDICES.add(new int[]{i1, i2, i3});
                }
            }
        }

        isMeshInitialized = true;
    }

    /**
     * 核心渲染主入口
     */
    public void render(GuiGraphics graphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, MedicalData data, float deltaSeconds) {
        initMeshIfNeeded();

        int brainHealth = data.getBrainHealth();
        int consciousness = data.getConsciousness();

        // 1. 更新自转与扫描周期
        float rotationSpeed = 22.0f; // 度/秒
        rotationAngle = (rotationAngle + deltaSeconds * rotationSpeed) % 360.0f;

        float scanSpeed = 0.45f; // 周期约 2.2 秒
        scanlineYRatio = (scanlineYRatio + deltaSeconds * scanSpeed) % 1.0f;

        // 更新粒子状态
        for (OrbitParticle p : orbitParticles) {
            p.update(deltaSeconds);
        }
        for (NebulaParticle np : nebulaParticles) {
            np.update(deltaSeconds, textRandom);
        }

        // 2. 更新表面附着文字标签
        updateFloatingLabelsIfNeeded(brainHealth);

        // 3. 计算大脑中心位置（位于卡片中上部）
        int centerX = panelX + panelWidth / 2;
        int centerY = panelY + 95;

        BrainColorTheme theme = getBrainColorTheme(brainHealth);

        // 4. 渲染背景数据星云粒子
        renderNebulaCloud(graphics, centerX, centerY, theme);

        // 5. 渲染外圈多层全息呼吸光晕
        renderHolographicHalo(graphics, centerX, centerY, theme);

        // 6. 渲染 3D 大脑线框模型与纵裂褶皱
        render3DBrainModel(graphics, centerX, centerY, theme, brainHealth, deltaSeconds);

        // 7. 渲染环绕轨道发光粒子
        renderOrbitParticles(graphics, centerX, centerY, theme);

        // 8. 渲染从下向上的全息扫描线光束
        renderScanningBeam(graphics, centerX, centerY, theme);

        // 9. 渲染大脑表面 3D 浮动文字（带打字机效果与背部遮挡剔除）
        renderFloatingLabels(graphics, font, centerX, centerY, theme, brainHealth);

        // 10. 渲染下方诊断信息面板（巨大百分比、意识徽标、状态词条）
        renderBottomMetrics(graphics, font, panelX, panelY, panelWidth, panelHeight, brainHealth, consciousness, data);
    }

    /**
     * 5 级脑健康主题配色与脉冲状态
     */
    public static class BrainColorTheme {
        public final int baseColorHex; // 主色 0xRRGGBB
        public final float pulseIntensity;
        public final float glitchChance;
        public final String statusBadge;
        public final int badgeColor;

        public BrainColorTheme(int baseColorHex, float pulseIntensity, float glitchChance, String statusBadge, int badgeColor) {
            this.baseColorHex = baseColorHex;
            this.pulseIntensity = pulseIntensity;
            this.glitchChance = glitchChance;
            this.statusBadge = statusBadge;
            this.badgeColor = badgeColor;
        }
    }

    private BrainColorTheme getBrainColorTheme(int brainHealth) {
        if (brainHealth >= 80) {
            return new BrainColorTheme(0x00F0FF, 0.0f, 0.0f, "[STATUS: OPTIMAL / CLEAR]", 0xFF00F0FF);
        } else if (brainHealth >= 60) {
            return new BrainColorTheme(0x39FF14, 0.2f, 0.0f, "[STATUS: DAZED / MILD]", 0xFF39FF14);
        } else if (brainHealth >= 40) {
            return new BrainColorTheme(0xFFB800, 0.45f, 0.05f, "[STATUS: DELIRIOUS / WARNING]", 0xFFFFB800);
        } else if (brainHealth >= 20) {
            return new BrainColorTheme(0xFF5500, 0.75f, 0.20f, "[STATUS: STUPOR / CRITICAL]", 0xFFFF5500);
        } else {
            return new BrainColorTheme(0xFF003C, 1.0f, 0.50f, "[STATUS: COMA / BRAIN FAILURE]", 0xFFFF003C);
        }
    }

    /**
     * 渲染背景数据星云粒子群
     */
    private void renderNebulaCloud(GuiGraphics graphics, int cx, int cy, BrainColorTheme theme) {
        int r = (theme.baseColorHex >> 16) & 0xFF;
        int g = (theme.baseColorHex >> 8) & 0xFF;
        int b = theme.baseColorHex & 0xFF;

        for (NebulaParticle p : nebulaParticles) {
            int a = (int) (p.alpha * 255.0f);
            if (a <= 0) continue;
            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int px = cx + (int) p.x;
            int py = cy + (int) p.y;
            graphics.fill(px, py, px + (int) p.size, py + (int) p.size, col);
        }
    }

    /**
     * 渲染外圈多层全息呼吸光晕
     */
    private void renderHolographicHalo(GuiGraphics graphics, int cx, int cy, BrainColorTheme theme) {
        float time = System.currentTimeMillis() * 0.003f;
        float pulse = (float) (Math.sin(time) * 0.5f + 0.5f) * theme.pulseIntensity;

        if (theme.glitchChance > 0 && Math.random() < theme.glitchChance * 0.15) {
            pulse = (float) Math.random();
        }

        int r = (theme.baseColorHex >> 16) & 0xFF;
        int g = (theme.baseColorHex >> 8) & 0xFF;
        int b = theme.baseColorHex & 0xFF;

        float[] radii = {56.0f, 66.0f + pulse * 6.0f, 76.0f + pulse * 12.0f};
        int[] alphas = {
                (int) (35 + pulse * 35),
                (int) (20 + pulse * 25),
                (int) (10 + pulse * 18)
        };

        for (int layer = 0; layer < radii.length; layer++) {
            float radius = radii[layer];
            int alpha = alphas[layer];
            int haloCol = (alpha << 24) | (r << 16) | (g << 8) | b;

            final int segments = 36;
            for (int i = 0; i < segments; i += 2) {
                float rad1 = (float) (i * 2 * Math.PI / segments);
                float rad2 = (float) ((i + 1) * 2 * Math.PI / segments);
                int px1 = cx + (int) (Math.cos(rad1) * radius * 1.15f);
                int py1 = cy + (int) (Math.sin(rad1) * radius * 0.85f);
                int px2 = cx + (int) (Math.cos(rad2) * radius * 1.15f);
                int py2 = cy + (int) (Math.sin(rad2) * radius * 0.85f);
                graphics.fill(px1, py1, px2 + 1, py2 + 1, haloCol);
            }
        }
    }

    /**
     * 渲染环绕轨道发光粒子
     */
    private void renderOrbitParticles(GuiGraphics graphics, int cx, int cy, BrainColorTheme theme) {
        int r = (theme.baseColorHex >> 16) & 0xFF;
        int g = (theme.baseColorHex >> 8) & 0xFF;
        int b = theme.baseColorHex & 0xFF;

        for (OrbitParticle p : orbitParticles) {
            float x = (float) Math.cos(p.angle) * p.orbitRadiusX;
            float z = (float) Math.sin(p.angle) * p.orbitRadiusZ;
            float y = (float) Math.sin(p.angle) * p.orbitRadiusY * 0.35f + x * p.tiltX + z * p.tiltY;

            // 深度与辉光
            float depthAlpha = (z + p.orbitRadiusZ) / (p.orbitRadiusZ * 2.0f); // 0.0 ~ 1.0
            int a = (int) (p.alpha * (0.3f + 0.7f * depthAlpha) * 255.0f);
            if (a <= 0) continue;

            int col = (a << 24) | (r << 16) | (g << 8) | b;
            int px = cx + (int) x;
            int py = cy + (int) y;
            graphics.fill(px, py, px + (int) p.size, py + (int) p.size, col);
        }
    }

    /**
     * 渲染 3D 大脑线框模型与纵裂褶皱
     */
    private void render3DBrainModel(GuiGraphics graphics, int cx, int cy, BrainColorTheme theme, int brainHealth, float deltaSeconds) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 120.0f);

        // 故障撕裂抖动（严重脑损伤时）
        if (theme.glitchChance > 0 && Math.random() < theme.glitchChance * 0.25) {
            float jitterX = (float) (Math.random() - 0.5) * 6.0f;
            float jitterY = (float) (Math.random() - 0.5) * 4.0f;
            graphics.pose().translate(jitterX, jitterY, 0.0f);
        }

        // 俯仰视角：稍向下倾斜 18 度，增强立体透视
        graphics.pose().mulPose(Axis.XP.rotationDegrees(18.0f));
        // 水平自转
        graphics.pose().mulPose(Axis.YP.rotationDegrees(rotationAngle));

        Matrix4f matrix = graphics.pose().last().pose();

        int r = (theme.baseColorHex >> 16) & 0xFF;
        int g = (theme.baseColorHex >> 8) & 0xFF;
        int b = theme.baseColorHex & 0xFF;

        // 1. 绘制半透明脑回表面多边形 (赋予脑组织质感与深度)
        BufferBuilder quadBuffer = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] tri : TRIANGLE_INDICES) {
            BrainVertex v0 = VERTICES.get(tri[0]);
            BrainVertex v1 = VERTICES.get(tri[1]);
            BrainVertex v2 = VERTICES.get(tri[2]);

            for (BrainVertex v : new BrainVertex[]{v0, v1, v2}) {
                int faceAlpha = (int) Mth.clamp(14 + (v.foldFactor + 1.0f) * 12.0f, 6, 45);
                quadBuffer.addVertex(matrix, v.x, v.y, v.z).setColor(r, g, b, faceAlpha);
            }
        }
        BufferUploader.drawWithShader(quadBuffer.buildOrThrow());

        // 2. 绘制 3D 神经经纬线框与脑回沟壑线
        BufferBuilder lineBuffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] line : LINE_INDICES) {
            BrainVertex v0 = VERTICES.get(line[0]);
            BrainVertex v1 = VERTICES.get(line[1]);

            int lineAlpha = (int) Mth.clamp(80 + (v0.foldFactor + 1.0f) * 45.0f, 40, 200);

            // 扫描光束经过顶点时高亮
            float scanWorldY = (scanlineYRatio - 0.5f) * 70.0f;
            if (Math.abs(v0.y - scanWorldY) < 6.0f || Math.abs(v1.y - scanWorldY) < 6.0f) {
                lineAlpha = Math.min(255, lineAlpha + 90);
            }

            lineBuffer.addVertex(matrix, v0.x, v0.y, v0.z).setColor(r, g, b, lineAlpha);
            lineBuffer.addVertex(matrix, v1.x, v1.y, v1.z).setColor(r, g, b, lineAlpha);
        }
        BufferUploader.drawWithShader(lineBuffer.buildOrThrow());

        graphics.pose().popPose();
        RenderSystem.enableCull();
    }

    /**
     * 渲染从下向上的全息扫描光束
     */
    private void renderScanningBeam(GuiGraphics graphics, int cx, int cy, BrainColorTheme theme) {
        float scanY = cy + 38.0f - (scanlineYRatio * 76.0f);

        int r = (theme.baseColorHex >> 16) & 0xFF;
        int g = (theme.baseColorHex >> 8) & 0xFF;
        int b = theme.baseColorHex & 0xFF;

        int beamWidth = 110;
        int left = cx - beamWidth / 2;
        int right = cx + beamWidth / 2;

        int scanCol1 = (0x77 << 24) | (r << 16) | (g << 8) | b;
        int scanCol2 = (0x22 << 24) | (r << 16) | (g << 8) | b;

        graphics.fill(left, (int) scanY - 1, right, (int) scanY + 1, scanCol1);
        graphics.fill(left + 8, (int) scanY - 3, right - 8, (int) scanY + 3, scanCol2);
    }

    /**
     * 周期性更新大脑表面附着文字标签
     */
    private void updateFloatingLabelsIfNeeded(int brainHealth) {
        long now = System.currentTimeMillis();
        if (now - lastTextUpdateTime < 2200L && !activeLabels.isEmpty()) {
            return;
        }
        lastTextUpdateTime = now;
        activeLabels.clear();

        float itemRatio, termRatio, glitchRatio;
        if (brainHealth >= 80) {
            itemRatio = 0.70f; termRatio = 0.30f; glitchRatio = 0.0f;
        } else if (brainHealth >= 60) {
            itemRatio = 0.50f; termRatio = 0.30f; glitchRatio = 0.20f;
        } else if (brainHealth >= 40) {
            itemRatio = 0.30f; termRatio = 0.30f; glitchRatio = 0.40f;
        } else if (brainHealth >= 20) {
            itemRatio = 0.10f; termRatio = 0.30f; glitchRatio = 0.60f;
        } else {
            itemRatio = 0.0f; termRatio = 0.10f; glitchRatio = 0.90f;
        }

        List<String> inventoryItemNames = getPlayerInventoryNames();

        final int totalLabels = 10;
        for (int i = 0; i < totalLabels; i++) {
            float p = textRandom.nextFloat();
            String labelText;
            int category;

            if (p < itemRatio && !inventoryItemNames.isEmpty()) {
                labelText = inventoryItemNames.get(textRandom.nextInt(inventoryItemNames.size()));
                category = 0;
            } else if (p < itemRatio + termRatio) {
                labelText = PSYCHO_TERMS[textRandom.nextInt(PSYCHO_TERMS.length)];
                category = 1;
            } else {
                StringBuilder sb = new StringBuilder();
                int len = 2 + textRandom.nextInt(4);
                for (int c = 0; c < len; c++) {
                    sb.append(GLITCH_BLOCKS[textRandom.nextInt(GLITCH_BLOCKS.length)]);
                }
                labelText = sb.toString();
                category = 2;
            }

            float phi = textRandom.nextFloat() * (float) Math.PI * 2.0f;
            float theta = 0.3f + textRandom.nextFloat() * ((float) Math.PI - 0.6f);

            float radius = 42.0f;
            float lx = (float) (Math.sin(theta) * Math.sin(phi)) * radius;
            float ly = -(float) Math.cos(theta) * (radius * 0.75f);
            float lz = (float) (Math.sin(theta) * Math.cos(phi)) * (radius * 1.1f);

            activeLabels.add(new FloatingBrainLabel(labelText, lx, ly, lz, category));
        }
    }

    private List<String> getPlayerInventoryNames() {
        List<String> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Inventory inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    String name = stack.getHoverName().getString();
                    if (name.length() > 10) {
                        name = name.substring(0, 8) + "..";
                    }
                    if (!list.contains(name)) {
                        list.add(name);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 渲染大脑表面 3D 浮动文字（带打字机效果与背部遮挡剔除）
     */
    private void renderFloatingLabels(GuiGraphics graphics, Font font, int cx, int cy, BrainColorTheme theme, int brainHealth) {
        Matrix4f transform = new Matrix4f();
        transform.identity();
        transform.rotate(Axis.XP.rotationDegrees(18.0f));
        transform.rotate(Axis.YP.rotationDegrees(rotationAngle));

        for (FloatingBrainLabel label : labelListCopy()) {
            Vector4f localPos = new Vector4f(label.ox, label.oy, label.oz, 1.0f);
            transform.transform(localPos);

            // 背部遮挡剔除
            if (localPos.z < 6.0f) {
                continue;
            }

            float screenX = cx + localPos.x;
            float screenY = cy + localPos.y;

            if (brainHealth < 60) {
                float jitter = (60 - brainHealth) * 0.08f;
                screenX += (float) (Math.random() - 0.5f) * jitter;
                screenY += (float) (Math.random() - 0.5f) * jitter;
            }

            float depthFactor = Mth.clamp(localPos.z / 42.0f, 0.2f, 1.0f);
            int alpha = (int) (depthFactor * 220);

            int textCol;
            if (label.category == 2) {
                textCol = (alpha << 24) | 0xFF3333;
            } else if (label.category == 1) {
                textCol = (alpha << 24) | 0xFFE082;
            } else {
                textCol = (alpha << 24) | 0x80DEEA;
            }

            String displayStr = label.getDisplayText();
            int strW = font.width(displayStr);
            graphics.fill((int) screenX - strW / 2 - 2, (int) screenY - 1, (int) screenX + strW / 2 + 2, (int) screenY + 9, (int) (alpha * 0.45f) << 24);
            graphics.drawString(font, displayStr, (int) screenX - strW / 2, (int) screenY, textCol, false);
        }
    }

    private List<FloatingBrainLabel> labelListCopy() {
        return new ArrayList<>(activeLabels);
    }

    /**
     * 渲染大脑扫描视图下方的生理指标与诊断状态条
     */
    private void renderBottomMetrics(GuiGraphics graphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, int brainHealth, int consciousness, MedicalData data) {
        int baseY = panelY + panelHeight - 110;
        int left = panelX + 16;
        int width = panelWidth - 32;

        graphics.fill(left, baseY, left + width, baseY + 1, 0x5500A8B5);

        String healthPct = brainHealth + "%";
        BrainColorTheme theme = getBrainColorTheme(brainHealth);

        // 绘制大号健康度读数 (分段数码管风格渲染)
        int numW = SegmentDisplayRenderer.renderString(graphics, healthPct, left + 4, baseY + 6, 8, 16, 2, 2, theme.badgeColor, HudColors.withAlpha(theme.badgeColor, 0.15f));

        // 诊断标识牌与意识状态
        int badgeX = left + 75;
        graphics.drawString(font, "CEREBRAL DIAGNOSTICS //", badgeX, baseY + 8, 0xFF90A4AE, false);
        graphics.drawString(font, theme.statusBadge, badgeX, baseY + 18, theme.badgeColor, false);

        String conText = "CONSCIOUSNESS: " + consciousness + "% " + getConsciousnessTag(consciousness);
        graphics.drawString(font, conText, badgeX, baseY + 28, getConsciousnessColor(consciousness), false);

        int lineY = baseY + 45;
        float intracranial = data.getIntracranialAccumulation();
        int icpColor = intracranial > 15.0f ? 0xFFFF1744 : (intracranial > 5.0f ? 0xFFFF9100 : 0xFF00E676);
        drawMiniDataRow(graphics, font, "INTRACRANIAL FLUID (颅内积液):", String.format("%.1f mL", intracranial), icpColor, left, lineY, width);

        lineY += 14;
        int bleedRate = data.getIntracranialBleedRate();
        int bleedColor = bleedRate > 0 ? 0xFFFF1744 : 0xFF00E676;
        drawMiniDataRow(graphics, font, "CEREBRAL BLEED (颅内出血率):", bleedRate + " mL/min", bleedColor, left, lineY, width);

        lineY += 14;
        int brainRegen = data.getBrainHealthRegenTimer();
        String regenStatus = (brainHealth < 100 && data.getBloodOxygen() > 95) ? "ACTIVE (" + brainRegen + "s)" : "IDLE";
        drawMiniDataRow(graphics, font, "NEURAL RECOVERY (自愈状态):", regenStatus, 0xFF81D4FA, left, lineY, width);
    }

    private void drawMiniDataRow(GuiGraphics graphics, Font font, String label, String value, int valColor, int x, int y, int width) {
        graphics.fill(x, y - 1, x + width, y + 11, 0x22121F2C);
        graphics.drawString(font, label, x + 4, y, 0xFFB0BEC5, false);
        int valW = font.width(value);
        graphics.drawString(font, value, x + width - valW - 4, y, valColor, false);
    }

    private String getConsciousnessTag(int value) {
        if (value <= 0) return "[COMA]";
        if (value < 40) return "[SEVERE BLUR]";
        if (value < 80) return "[DAZED]";
        return "[CLEAR]";
    }

    private int getConsciousnessColor(int value) {
        if (value > 80) return 0xFF00E676;
        if (value > 50) return 0xFFFFD54F;
        return 0xFFFF1744;
    }
}
