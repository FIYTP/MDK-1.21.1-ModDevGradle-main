package com.FIYTP.realmedicalmod.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 超声脉冲特效管理器 (Ultrasound Pulse Manager) — 取代普通点击涟漪
 * 点击处发出声呐式圆环波前; 波前扫过肢体包围盒时, 该肢体边缘短暂发光描边,
 * 像真的在给组织做超声探测。
 */
public class ClickRippleManager {
    /** 波前扫过肢体时触发的描边脉冲 (边缘发光盒) */
    public interface LimbHighlightConsumer {
        void highlightBox(int x, int y, int w, int h);
    }

    private static class Ripple {
        float x;
        float y;
        float radius;
        float maxRadius;
        float alpha;
        int color;
        /** 已触发描边的肢体索引集合 (位掩码, 防重复触发) */
        long limbFiredMask = 0L;
        /** 肢体包围盒快照 [x,y,w,h, ...]×N (点击瞬间捕获) */
        int[] limbBoxes;

        Ripple(float x, float y, float maxRadius, int color, int[] limbBoxes) {
            this.x = x;
            this.y = y;
            this.radius = 2.0f;
            this.maxRadius = maxRadius;
            this.alpha = 1.0f;
            this.color = color;
            this.limbBoxes = limbBoxes;
        }

        boolean update(float deltaSeconds) {
            float speed = maxRadius / 0.45f; // 450ms 内扩散完毕 (比旧涟漪稍慢, 更像声呐)
            radius += speed * deltaSeconds;
            alpha = 1.0f - (radius / maxRadius);
            return alpha > 0.0f;
        }

        void render(GuiGraphics graphics, LimbHighlightConsumer highlighter) {
            if (alpha <= 0.0f) return;
            int a = (int) (alpha * 200);
            int drawColor = (a << 24) | (color & 0x00FFFFFF);

            // ---- 超声波前: 主圆环波阵面 ----
            int segments = 24;
            for (int i = 0; i < segments; i++) {
                double a1 = i * (2.0 * Math.PI / segments);
                double a2 = (i + 1) * (2.0 * Math.PI / segments);
                int x1 = (int) (x + Math.cos(a1) * radius);
                int y1 = (int) (y + Math.sin(a1) * radius);
                int x2 = (int) (x + Math.cos(a2) * radius);
                int y2 = (int) (y + Math.sin(a2) * radius);

                graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, drawColor);
            }
            // 前导弧: 波前前方 3px 的 4 段稀疏微弧 (声呐扫描束)
            int leadSegs = 4;
            double leadRot = (System.currentTimeMillis() / 300.0) % (2 * Math.PI);
            for (int i = 0; i < leadSegs; i++) {
                double am = leadRot + i * (2.0 * Math.PI / leadSegs);
                int lx = (int) (x + Math.cos(am) * (radius + 3));
                int ly = (int) (y + Math.sin(am) * (radius + 3));
                graphics.fill(lx, ly, lx + 1, ly + 1, ((a / 2) << 24) | (color & 0x00FFFFFF));
            }

            // ---- 波前与肢体包围盒求交: 刚接触时触发边缘描边 ----
            if (limbBoxes != null && highlighter != null) {
                for (int li = 0; li * 4 < limbBoxes.length; li++) {
                    if (li >= 64) break;
                    if ((limbFiredMask & (1L << li)) != 0) continue;
                    int bx = limbBoxes[li * 4];
                    int by = limbBoxes[li * 4 + 1];
                    int bw = limbBoxes[li * 4 + 2];
                    int bh = limbBoxes[li * 4 + 3];
                    // 点击点到包围盒中心距离 ≤ 当前波前半径 → 触发
                    float cx = bx + bw / 2.0f;
                    float cy = by + bh / 2.0f;
                    float dist = (float) Math.hypot(cx - x, cy - y);
                    if (dist <= radius) {
                        limbFiredMask |= (1L << li);
                        highlighter.highlightBox(bx, by, bw, bh);
                    }
                }
            }
        }
    }

    /** 描边高亮脉冲 (波前触发后的肢体边缘发光, 独立生命周期 0.55s) */
    private static class LimbGlow {
        int x, y, w, h;
        float life; // 1 → 0
        static final float DURATION = 0.55f;

        LimbGlow(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.life = 1.0f;
        }

        boolean update(float dt) {
            life -= dt / DURATION;
            return life > 0.0f;
        }

        void render(GuiGraphics g) {
            int a = (int) (life * 150);
            int col = (a << 24) | 0x00B2EBF2; // 超声青白
            // 四边描边 (发光盒)
            g.fill(x - 1, y - 1, x + w + 1, y, col);           // 上
            g.fill(x - 1, y + h, x + w + 1, y + h + 1, col);   // 下
            g.fill(x - 1, y, x, y + h, col);                   // 左
            g.fill(x + w, y, x + w + 1, y + h, col);           // 右
            // 内部扫掠微光 (声呐扫过组织的回波, 十字扫掠)
            int inner = (int) ((1.0f - life) * Math.min(w, h));
            int innerA = (int) (life * 40);
            int sweepCol = (innerA << 24) | 0x00E1F5FE;
            g.fill(x, y + inner / 2, x + w, y + inner / 2 + 1, sweepCol);
            g.fill(x + inner / 2, y, x + inner / 2 + 1, y + h, sweepCol);
        }
    }

    private final List<Ripple> ripples = new ArrayList<>();
    private final List<LimbGlow> glows = new ArrayList<>();

    /** 兼容旧调用: 无肢体感知的普通脉冲 */
    public void addRipple(float x, float y, float maxRadius, int color) {
        addUltrasoundPulse(x, y, maxRadius, color, null);
    }

    /**
     * 超声脉冲: 点击处发出声呐波前, 波前扫过 limbBoxes 中的包围盒时逐个触发边缘描边。
     * limbBoxes 为 [x,y,w,h]×N 扁平数组 (点击瞬间捕获坐标快照)。
     */
    public void addUltrasoundPulse(float x, float y, float maxRadius, int color, int[] limbBoxes) {
        if (ripples.size() < 10) {
            ripples.add(new Ripple(x, y, maxRadius, color, limbBoxes));
        }
    }

    public void updateAndRender(GuiGraphics graphics, float deltaSeconds) {
        // 先渲染已触发的肢体描边 (在波前之下)
        Iterator<LimbGlow> git = glows.iterator();
        while (git.hasNext()) {
            LimbGlow lg = git.next();
            if (lg.update(deltaSeconds)) {
                lg.render(graphics);
            } else {
                git.remove();
            }
        }

        Iterator<Ripple> it = ripples.iterator();
        while (it.hasNext()) {
            Ripple r = it.next();
            if (r.update(deltaSeconds)) {
                // 波前扫到肢体 → 立即加入描边脉冲列表
                r.render(graphics, (bx, by, bw, bh) -> glows.add(new LimbGlow(bx, by, bw, bh)));
            } else {
                it.remove();
            }
        }
    }
}
