package com.FIYTP.realmedicalmod.client.gui;

import com.FIYTP.realmedicalmod.capability.MedicalData;
import com.FIYTP.realmedicalmod.client.sound.MedicalSoundHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 3A 级右侧医疗诊断面板自适应与滚动兼容引擎 (MedicalDetailPanelRenderer)
 *
 * 专用于解决「信息过多导致挤占心电图、文字重叠或溢出面板边界」的问题：
 * 1. 硬件级 enableScissor 裁剪滚动视口：
 *    上方诊断指标区与下方双通道心电示波器 (ECG) 严格分区隔离，指标再多也绝不压扁或挤掉心电图。
 * 2. 丝滑阻尼滚轮滚动 + 3A 全息滚动条：
 *    支持鼠标滚轮平滑上下滚动查看全部创伤、血胸、SIRS、烧伤、血流动力学参数。
 * 3. 自适应双列紧凑网格 (AUTO 2-COLUMN COMPACT GRID)：
 *    支持一键切换（或根据窗口高度自动激活）双列紧凑遥测卡片布局，纵向空间节省 50%。
 * 4. 底部示波器可折叠停靠舱 (`[ECG ▼/▲]`)：
 *    在小屏幕或高 GUI 缩放比下可一键折叠/展开底部心电图，瞬间释放 95px 诊断视口高度。
 */
public class MedicalDetailPanelRenderer {

    @FunctionalInterface
    public interface OscilloscopeDrawer {
        void draw(GuiGraphics graphics, int x, int y, int width, int height, MedicalData data);
    }

    private float scrollOffset = 0.0f;
    private float scrollTarget = 0.0f;
    private float maxScroll = 0.0f;
    private long lastFrameMs = 0L;

    private boolean compactTwoColumn = true;
    private boolean ecgCollapsed = false;
    private MedicalData.Limb lastLimb = null;

    // 交互热区记录
    private int viewportX, viewportY, viewportW, viewportH;
    private int btnCompactX, btnCompactY, btnCompactW, btnCompactH;
    private int btnEcgX, btnEcgY, btnEcgW, btnEcgH;

    /**
     * 渲染非 HEAD 分支（全身概览、胸部、四肢）的 3A 右侧详情面板
     */
    public void renderLimbOrOverviewPanel(GuiGraphics g, Font font,
                                          int panelX, int panelY, int panelWidth, int panelHeight,
                                          MedicalData data, MedicalData.Limb selectedLimb,
                                          int mouseX, int mouseY, long now,
                                          OscilloscopeDrawer ecgDrawer) {
        if (lastLimb != selectedLimb) {
            lastLimb = selectedLimb;
            scrollOffset = 0.0f;
            scrollTarget = 0.0f;
        }

        if (lastFrameMs == 0L) lastFrameMs = now;
        float dt = Mth.clamp((now - lastFrameMs) / 1000.0f, 0.001f, 0.1f);
        lastFrameMs = now;

        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
        scrollOffset = Mth.lerp(Math.min(1.0f, dt * 14.0f), scrollOffset, scrollTarget);

        // 1. 绘制右上角布局兼容控制按钮：[2-COL // 双列] 与 [ECG ▼/▲]
        drawHeaderControls(g, font, panelX, panelY, panelWidth, mouseX, mouseY);

        // 2. 计算底部示波器停靠高度与上方可滚动视口高度
        int bottomPad = 10;
        int desiredEcgH = ecgCollapsed ? 18 : Math.min(92, Math.max(58, (int) (panelHeight * 0.34f)));
        if (panelHeight < 155) {
            desiredEcgH = 18; // 极矮窗口下自动收起为紧凑心电状态栏
        }

        int ecgY = panelY + panelHeight - desiredEcgH - bottomPad;
        int contentX = panelX + 14;
        int contentW = panelWidth - 28;

        this.viewportX = contentX;
        this.viewportY = panelY + 26;
        this.viewportW = contentW;
        this.viewportH = Math.max(36, ecgY - this.viewportY - 6);

        // 3. 在硬件 Scissor 裁剪视口内绘制肢体详情或全身生理概览
        g.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

        int innerW = viewportW - 7;
        int startY = (int) (viewportY - scrollOffset);
        int endY;

        if (selectedLimb != null) {
            endY = drawLimbTelemetryContent(g, font, data, selectedLimb, viewportX, startY, innerW);
        } else {
            endY = drawSystemOverviewContent(g, font, data, viewportX, startY, innerW);
        }

        int totalContentH = endY - startY;
        this.maxScroll = Math.max(0.0f, totalContentH - viewportH + 4);

        g.disableScissor();

        // 4. 若内容超出视口，绘制右侧全息滚动条与上下边缘羽化渐变提示
        if (this.maxScroll > 1.0f) {
            BrainScan3DRenderer.drawHolographicScrollbar(
                    g, viewportX + viewportW - 3, viewportY, 3, viewportH,
                    scrollOffset, maxScroll, totalContentH
            );
            if (scrollOffset > 2.0f) {
                g.fillGradient(viewportX, viewportY, viewportX + innerW, viewportY + 8, 0xCC050B12, 0x00050B12);
            }
            if (scrollOffset < maxScroll - 2.0f) {
                g.fillGradient(viewportX, viewportY + viewportH - 8, viewportX + innerW, viewportY + viewportH, 0x00050B12, 0xCC050B12);
            }
        }

        // 5. 绘制底部固定停靠的双通道示波器 (ECG + PLETH)
        if (desiredEcgH >= 48 && ecgDrawer != null) {
            ecgDrawer.draw(g, contentX, ecgY, contentW, desiredEcgH, data);
        } else {
            // 折叠态：单行紧凑心电遥测条
            drawCollapsedEcgStrip(g, font, contentX, ecgY, contentW, desiredEcgH, data);
        }
    }

    private void drawHeaderControls(GuiGraphics g, Font font, int panelX, int panelY, int panelW, int mouseX, int mouseY) {
        int btnY = panelY + 5;
        int btnH = 13;

        String ecgLabel = ecgCollapsed ? "ECG ▲" : "ECG ▼";
        this.btnEcgW = font.width(ecgLabel) + 10;
        this.btnEcgH = btnH;
        this.btnEcgX = panelX + panelW - btnEcgW - 10;
        this.btnEcgY = btnY;

        boolean hovEcg = mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH;
        HudRenderHelper.drawSciFiCard(g, btnEcgX, btnEcgY, btnEcgW, btnEcgH, 2,
                 !ecgCollapsed ? 0xAA003B4D : 0x66050E17,
                hovEcg ? HudColors.COLOR_NEON_CYAN : 0x5500E5FF);
        g.drawString(font, ecgLabel, btnEcgX + 5, btnEcgY + 3, !ecgCollapsed ? HudColors.COLOR_NEON_CYAN : 0xFF90A4AE, false);

        String gridLabel = compactTwoColumn ? "2-COL // 双列" : "1-COL // 单列";
        this.btnCompactW = font.width(gridLabel) + 10;
        this.btnCompactH = btnH;
        this.btnCompactX = btnEcgX - btnCompactW - 4;
        this.btnCompactY = btnY;

        if (this.btnCompactX > panelX + 150) {
            boolean hovGrid = mouseX >= btnCompactX && mouseX <= btnCompactX + btnCompactW && mouseY >= btnCompactY && mouseY <= btnCompactY + btnCompactH;
            HudRenderHelper.drawSciFiCard(g, btnCompactX, btnCompactY, btnCompactW, btnCompactH, 2,
                    compactTwoColumn ? 0xAA004D66 : 0x66050E17,
                    hovGrid ? HudColors.COLOR_NEON_CYAN : 0x5500E5FF);
            g.drawString(font, gridLabel, btnCompactX + 5, btnCompactY + 3, compactTwoColumn ? 0xFFFFFFFF : 0xFF90A4AE, false);
        }
    }

    private int drawLimbTelemetryContent(GuiGraphics g, Font font, MedicalData data, MedicalData.Limb limb, int x, int y, int w) {
        MedicalData.LimbData ld = data.getLimb(limb);
        if (ld == null) return y;

        // 1. 部位健康度主进度条（修复原版重复绘制两次进度条导致文字重叠的 Bug）
        int health = BrainScan3DRenderer.computeLimbHealthPercent(ld);
        int hc = HudColors.getHealthColor(health / 100.0f);
        g.drawString(font, limb.name() + " INTEGRITY // 部位完整度", x, y, HudColors.TEXT_LABEL, false);
        String hpStr = health + "%";
        g.drawString(font, hpStr, x + w - font.width(hpStr), y, hc, false);
        y += 11;
        HudRenderHelper.drawSciFiProgressBar(g, x, y, w, 6, health / 100.0f, hc, 0xFF0A1824);
        y += 12;

        // 2. 核心创伤指标（支持双列紧凑网格或单列展开）
        int bleed = ld.getBleeding();
        int inBleed = ld.getInternalBleedingRate();
        int pain = ld.getPainLevel();
        int inf = ld.getInfection();

        if (compactTwoColumn && w >= 180) {
            int colW = (w - 6) / 2;
            drawMetricCard(g, font, x, y, colW, tr("medical.detail.bleeding_rate"), bleed + " mL/m",
                    Math.min(1f, bleed / 500f), bleed > 0 ? HudColors.COLOR_ALERT_RED : 0xFF37474F);
            drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/m",
                    Math.min(1f, inBleed / 300f), inBleed > 0 ? 0xFFE040FB : 0xFF37474F);
            y += 24;

            drawMetricCard(g, font, x, y, colW, tr("medical.detail.pain_level"), pain + " / 10",
                    pain / 10f, pain > 0 ? HudColors.VITAL_PAIN : 0xFF37474F);
            drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.infection"), inf + " %",
                    inf / 100f, inf > 0 ? HudColors.COLOR_WARNING_YELLOW : 0xFF37474F);
            y += 24;
        } else {
            if (bleed > 0) {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.bleeding_rate"), bleed + " mL/min",
                        Math.min(1f, bleed / 500f), HudColors.COLOR_ALERT_RED);
            }
            if (inBleed > 0) {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.internal_bleeding_rate"), inBleed + " mL/min",
                        Math.min(1f, inBleed / 300f), 0xFFE040FB);
            }
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.pain_level"), pain + " / 10",
                    pain / 10f, pain > 0 ? HudColors.VITAL_PAIN : 0xFF455A64);
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.infection"), inf + " %",
                    inf / 100f, inf > 0 ? HudColors.COLOR_WARNING_YELLOW : 0xFF455A64);
        }

        if (ld.hasFracture() && ld.hasSplint() && ld.getFractureTimer() > 0) {
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.fracture_healing"), ld.getFractureTimer() + "s",
                    Mth.clamp(1.0f - ld.getFractureTimer() / 300.0f, 0.0f, 1.0f), HudColors.COLOR_SAFE_GREEN);
        }
        if (ld.hasBurn()) {
            y = drawFullRow(g, font, x, y, w, "烧伤创面 BURN (" + ld.getBurnDegree() + "°)",
                    String.format("%.1f%%", ld.getBurnArea()), Mth.clamp(ld.getBurnArea() / 25.0f, 0.0f, 1.0f), 0xFFFF6D00);
        }

        // 3. 状态药丸徽标流式排列
        y = drawLimbStatusPills(g, font, x, y + 2, w, ld);

        // 4. 胸部 (CHEST) 专科指标：血胸、肺挫伤、SIRS、血压、MAP、心输出量 CO、外周阻力 TPR
        if (limb == MedicalData.Limb.CHEST) {
            y += 4;
            g.fill(x, y + 8, x + w, y + 9, 0x4400E5FF);
            g.drawString(font, "THORACIC & HEMODYNAMICS // 胸腔与血流动力学", x, y, HudColors.COLOR_NEON_CYAN, false);
            y += 12;

            float chestBlood = data.getChestBloodAccumulation();
            int hemoLv = data.getHemothoraxLevel();
            float lungContusion = data.getLungContusion();
            int sirsLv = data.getSirsLevel();

            if (compactTwoColumn && w >= 180) {
                int colW = (w - 6) / 2;
                drawMetricCard(g, font, x, y, colW, tr("medical.detail.chest_blood_accumulation"),
                        String.format("%.0f mL (LV.%d)", chestBlood, hemoLv),
                        Math.min(1f, chestBlood / 2000f), hemoLv > 0 ? HudColors.COLOR_ALERT_RED : 0xFF37474F);
                drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.lung_contusion"),
                        String.format("%.0f%%", lungContusion),
                        Mth.clamp(lungContusion / 100f, 0f, 1f), lungContusion > 0 ? HudColors.COLOR_WARNING_YELLOW : 0xFF37474F);
                y += 24;

                drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_pressure"),
                        data.getSystolicBP() + "/" + data.getDiastolicBP() + " (MAP " + (int) data.getMeanArterialPressure() + ")",
                        Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), HudColors.VITAL_BLOOD_PRESSURE);
                drawMetricCard(g, font, x + colW + 6, y, colW, "CO / TPR / SIRS",
                        String.format("%.1fL | %.2f | S%d", data.getCardiacOutput(), data.getPeripheralResistance(), sirsLv),
                        Mth.clamp(data.getCardiacOutput() / 8.0f, 0f, 1f), sirsLv > 0 ? HudColors.COLOR_ALERT_RED : HudColors.COLOR_NEON_CYAN);
                y += 24;
            } else {
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.chest_blood_accumulation"),
                        String.format("%.0f mL (LV.%d)", chestBlood, hemoLv), Math.min(1f, chestBlood / 2000f),
                        hemoLv > 0 ? HudColors.COLOR_ALERT_RED : 0xFF455A64);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_pressure"),
                        data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg (MAP " + (int) data.getMeanArterialPressure() + ")",
                        Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), HudColors.VITAL_BLOOD_PRESSURE);
                y = drawFullRow(g, font, x, y, w, tr("medical.detail.cardiac_output") + " / TPR",
                        String.format("CO %.2f L/m | TPR %.2f", data.getCardiacOutput(), data.getPeripheralResistance()),
                        Mth.clamp(data.getCardiacOutput() / 8.0f, 0f, 1f), HudColors.COLOR_NEON_CYAN);
                if (sirsLv > 0) {
                    y = drawFullRow(g, font, x, y, w, tr("medical.detail.sirs_level"),
                            "STAGE " + sirsLv + " / 4", sirsLv / 4.0f, HudColors.COLOR_ALERT_RED);
                }
            }
        }

        return y;
    }

    private int drawSystemOverviewContent(GuiGraphics g, Font font, MedicalData data, int x, int y, int w) {
        g.drawString(font, "SYSTEMIC TELEMETRY // 全身生命体征总览", x, y, HudColors.COLOR_NEON_CYAN, false);
        y += 13;

        int totalBleed = 0;
        for (MedicalData.Limb limb : MedicalData.Limb.values()) {
            MedicalData.LimbData ld = data.getLimb(limb);
            if (ld != null) {
                if (!ld.isBandaged() && !ld.isTourniqueted()) totalBleed += ld.getBleeding();
                totalBleed += ld.getInternalBleedingRate();
            }
        }

        if (compactTwoColumn && w >= 180) {
            int colW = (w - 6) / 2;
            drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_volume"),
                    data.getBloodVolume() + " / " + data.getMaxBloodVolume() + " mL",
                    data.getBloodVolume() / (float) Math.max(1, data.getMaxBloodVolume()), HudColors.VITAL_HEART_BEAT);
            drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.total_bleeding_rate"),
                    totalBleed + " mL/min",
                    Math.min(1f, totalBleed / 600f), totalBleed > 0 ? HudColors.COLOR_ALERT_RED : HudColors.COLOR_SAFE_GREEN);
            y += 24;

            drawMetricCard(g, font, x, y, colW, tr("medical.detail.brain_health"),
                    data.getBrainHealth() + "%", data.getBrainHealth() / 100f,
                    HudColors.getHealthColor(data.getBrainHealth() / 100f));
            drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.consciousness"),
                    data.getConsciousness() + "%", data.getConsciousness() / 100f,
                    HudColors.getHealthColor(data.getConsciousness() / 100f));
            y += 24;

            drawMetricCard(g, font, x, y, colW, tr("medical.detail.blood_pressure"),
                    data.getSystolicBP() + "/" + data.getDiastolicBP() + " (MAP " + (int) data.getMeanArterialPressure() + ")",
                    Mth.clamp(data.getMeanArterialPressure() / 130f, 0f, 1f), HudColors.VITAL_BLOOD_PRESSURE);
            drawMetricCard(g, font, x + colW + 6, y, colW, tr("medical.detail.perceived_pain"),
                    data.getPerceivedPain() + " / 55",
                    Mth.clamp(data.getPerceivedPain() / 55f, 0f, 1f), HudColors.VITAL_PAIN);
            y += 24;

            drawMetricCard(g, font, x, y, colW, tr("medical.detail.respiration") + " / SpO2",
                    data.getRespirationRate() + " rpm | " + data.getBloodOxygen() + "%",
                    data.getBloodOxygen() / 100f, HudColors.VITAL_OXYGEN);
            drawMetricCard(g, font, x + colW + 6, y, colW, "SIRS / BURN TBSA",
                    "SIRS " + data.getSirsLevel() + " | " + String.format("%.1f%%", data.getBurnTBSA()),
                    Math.max(data.getSirsLevel() / 4f, data.getBurnTBSA() / 100f),
                    (data.getSirsLevel() > 0 || data.getBurnTBSA() > 0) ? HudColors.COLOR_WARNING_YELLOW : 0xFF37474F);
            y += 24;
        } else {
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_volume"),
                    data.getBloodVolume() + " mL", data.getBloodVolume() / 5000f, HudColors.VITAL_HEART_BEAT);
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.total_bleeding_rate"),
                    totalBleed + " mL/min", Math.min(1f, totalBleed / 600f), totalBleed > 0 ? HudColors.COLOR_ALERT_RED : 0xFF455A64);
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.brain_health"),
                    data.getBrainHealth() + "%", data.getBrainHealth() / 100f, HudColors.getHealthColor(data.getBrainHealth() / 100f));
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.consciousness"),
                    data.getConsciousness() + "%", data.getConsciousness() / 100f, HudColors.getHealthColor(data.getConsciousness() / 100f));
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.perceived_pain"),
                    data.getPerceivedPain() + " / 55", data.getPerceivedPain() / 55f, HudColors.VITAL_PAIN);
            y = drawFullRow(g, font, x, y, w, tr("medical.detail.blood_pressure"),
                    data.getSystolicBP() + "/" + data.getDiastolicBP() + " mmHg", data.getMeanArterialPressure() / 130f, HudColors.VITAL_BLOOD_PRESSURE);
        }

        return y;
    }

    private void drawMetricCard(GuiGraphics g, Font font, int x, int y, int w,
                                String label, String val, float pct, int color) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        g.fill(x, y, x + w, y + 21, 0x88071320);
        g.fill(x, y, x + 2, y + 21, color);
        g.drawString(font, label, x + 5, y + 2, 0xFFB0BEC5, false);
        int vw = font.width(val);
        g.drawString(font, val, x + w - vw - 3, y + 2, color, false);
        HudRenderHelper.drawSciFiProgressBar(g, x + 5, y + 13, w - 8, 5, pct, color, 0xFF050C14);
    }

    private int drawFullRow(GuiGraphics g, Font font, int x, int y, int w,
                            String label, String val, float pct, int color) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        g.drawString(font, label, x, y, 0xFFB0BEC5, false);
        int vw = font.width(val);
        g.drawString(font, val, x + w - vw, y, color, false);
        HudRenderHelper.drawSciFiProgressBar(g, x, y + 10, w, 5, pct, color, 0xFF07121E);
        return y + 18;
    }

    private int drawLimbStatusPills(GuiGraphics g, Font font, int x, int y, int w, MedicalData.LimbData ld) {
        int px = x;
        int py = y;
        int rightEdge = x + w;
        boolean abnormal = false;

        if (ld.isAmputated()) {
            drawPill(g, font, px, py, tr("medical.status.amputated"), 0xFFFF1744);
            return py + 14;
        }
        if (ld.isBandaged()) {
            px = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676);
            if (px < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.bandaged"), 0xFF00E676); }
        }
        if (ld.isTourniqueted()) {
            int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.tourniquet"), 0xFFFF9100);
            if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.tourniquet"), 0xFFFF9100); } else px = nx;
        }
        if (ld.getInternalBleedingRate() > 0) {
            abnormal = true;
            int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.internal_bleeding"), 0xFFE040FB);
            if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.internal_bleeding"), 0xFFE040FB); } else px = nx;
        }
        if (ld.hasFracture()) {
            abnormal = true;
            String fTxt = ld.isOpenFracture() ? tr("medical.status.open_fracture")
                    : (ld.hasSplint() ? tr("medical.status.fracture_splinted") : tr("medical.status.fracture"));
            int nx = drawWrappedPill(g, font, x, px, py, rightEdge, fTxt, 0xFFFF1744);
            if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, fTxt, 0xFFFF1744); } else px = nx;
        }
        if (ld.hasDislocation()) {
            abnormal = true;
            int nx = drawWrappedPill(g, font, x, px, py, rightEdge, tr("medical.status.dislocation"), 0xFFFFD600);
            if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, tr("medical.status.dislocation"), 0xFFFFD600); } else px = nx;
        }
        if (ld.getInfection() > 0) {
            abnormal = true;
            String iTxt = ld.getInfection() <= 25 ? tr("medical.status.mild_infection")
                    : ld.getInfection() <= 50 ? tr("medical.status.moderate_infection")
                    : ld.getInfection() <= 75 ? tr("medical.status.severe_infection")
                    : tr("medical.status.critical_infection");
            int nx = drawWrappedPill(g, font, x, px, py, rightEdge, iTxt, 0xFFFFB300);
            if (nx < 0) { py += 14; px = drawWrappedPill(g, font, x, x, py, rightEdge, iTxt, 0xFFFFB300); } else px = nx;
        }
        if (!abnormal && !ld.isBandaged() && !ld.isTourniqueted() && ld.getBleeding() <= 0) {
            drawPill(g, font, px, py, tr("medical.status.healthy"), 0xFF00E676);
        }
        return py + 14;
    }

    private int drawWrappedPill(GuiGraphics g, Font font, int startX, int curX, int curY, int rightEdge, String text, int color) {
        int pw = font.width(text) + 8;
        if (curX > startX && curX + pw > rightEdge) return -1;
        drawPill(g, font, curX, curY, text, color);
        return curX + pw + 4;
    }

    private void drawPill(GuiGraphics g, Font font, int x, int y, String text, int color) {
        int pw = font.width(text) + 8;
        g.fill(x, y, x + pw, y + 11, HudColors.withAlpha(color, 0.22f));
        g.fill(x, y, x + 2, y + 11, color);
        g.drawString(font, text, x + 4, y + 2, color, false);
    }

    private void drawCollapsedEcgStrip(GuiGraphics g, Font font, int x, int y, int w, int h, MedicalData data) {
        HudRenderHelper.drawSciFiCard(g, x, y, w, h, 2, 0xAA040C14, 0x4400E5FF);
        String info = String.format("ECG MONITOR // HR: %d BPM  |  BP: %d/%d mmHg  |  SpO2: %d%%",
                data.getHeartRate(), data.getSystolicBP(), data.getDiastolicBP(), data.getBloodOxygen());
        g.drawString(font, info, x + 6, y + 5, HudColors.COLOR_NEON_CYAN, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= btnEcgX && mouseX <= btnEcgX + btnEcgW && mouseY >= btnEcgY && mouseY <= btnEcgY + btnEcgH) {
            this.ecgCollapsed = !this.ecgCollapsed;
            MedicalSoundHelper.playLimbSelectSound();
            return true;
        }
        if (mouseX >= btnCompactX && mouseX <= btnCompactX + btnCompactW && mouseY >= btnCompactY && mouseY <= btnCompactY + btnCompactH) {
            this.compactTwoColumn = !this.compactTwoColumn;
            this.scrollTarget = 0.0f;
            MedicalSoundHelper.playLimbSelectSound();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        if (viewportW > 0 && mouseX >= viewportX && mouseX <= viewportX + viewportW
                && mouseY >= viewportY && mouseY <= viewportY + viewportH && maxScroll > 0.5f) {
            scrollTarget = Mth.clamp(scrollTarget - (float) deltaY * 18.0f, 0.0f, maxScroll);
            return true;
        }
        return false;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}