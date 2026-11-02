package com.FIYTP.realmedicalmod.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Random;

/**
 * 科技数据流与十六进制监控装饰条
 * 在卡片边界或空白区域滚动显示二进制/十六进制内存地址与实时校验码，增强 3A 沉浸感
 */
public final class SciFiDataStreamRenderer {
    private SciFiDataStreamRenderer() {}

    private static final String[] HEX_SNIPPETS = {
            "0x7FFA2B", "MEM_OK", "SYS_SYNC", "BIO_VTL",
            "CRC_PASS", "0x00FF8C", "NEURO_LINK", "NODE_01",
            "RX_BUFFER", "DATA_STREAM", "PORT_9876", "STAT_RDY"
    };

    /**
     * 在卡片底部或顶部渲染水平滚动科技状态小字
     */
    public static void renderHorizontalDataTicker(GuiGraphics graphics, Font font, int x, int y, int width, long timeMs) {
        int streamColor = 0x5500ADB5; // 柔和暗青
        // 基于时间滚动
        int offset = (int) ((timeMs / 60) % 300);

        // 构造简明状态
        String line = " // SYS_DIAGNOSTICS // " + HEX_SNIPPETS[(int)((timeMs / 2000) % HEX_SNIPPETS.length)]
                + " // BUFFER_RATE 60Hz // ENCRYPTED_CHANNEL //";

        int textW = font.width(line);
        int drawX = x + width - (offset % (width + textW));
        graphics.drawString(font, line, Math.max(x, drawX), y, streamColor, false);
    }

    /**
     * 在垂直边界渲染竖向二进制/十六进制微流
     */
    public static void renderVerticalHexStream(GuiGraphics graphics, Font font, int x, int y, int height, long timeMs) {
        int streamColor = 0x4400E5FF;
        int lines = height / 10;
        int seed = (int) (timeMs / 1000);

        for (int i = 0; i < lines; i++) {
            // 每行显示 4 字符十六进制
            int val = ((seed + i * 37) * 1103515245 + 12345) & 0xFFFF;
            String hex = String.format("%04X", val);
            graphics.drawString(font, hex, x, y + i * 10, streamColor, false);
        }
    }
}
