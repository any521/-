package com.dianfei.ai.verify;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * 感知哈希（pHash）工具：32x32 灰度 → DCT → 取左上 8x8 低频（含 DC）→ 中值二值化 → 64bit hex。
 * 用于 F1 第一层防重：同图/近似图（裁剪/压缩/轻微调色）哈希汉明距离小，
 * 与 sys_config ai.verify.phash-hamming 阈值比较判定重复提交。
 */
public final class PHashUtil {

    private static final int SAMPLE = 32;
    private static final int LOW_FREQ = 8;

    private PHashUtil() {
    }

    /** 从图片 URL 计算 pHash（16 位 hex）；下载/解码失败返回 null（跳过防重，不阻塞主链路） */
    public static String fromUrl(String imageUrl, int timeoutMs) {
        try {
            // data URL（前端拍照/上传直传）：data:[mediatype][;base64],<data>，URLConnection 无法打开，需解码
            if (imageUrl != null && imageUrl.startsWith("data:")) {
                int comma = imageUrl.indexOf(',');
                if (comma < 0) {
                    return null;
                }
                String meta = imageUrl.substring(5, comma);
                String data = imageUrl.substring(comma + 1);
                byte[] bytes = meta.contains(";base64")
                        ? Base64.getDecoder().decode(data)
                        : URLDecoder.decode(data, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
                try (InputStream in = new ByteArrayInputStream(bytes)) {
                    BufferedImage image = ImageIO.read(in);
                    return image == null ? null : compute(image);
                }
            }
            URI uri = URI.create(imageUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getUserInfo() != null || uri.getHost() == null || isBlockedHost(uri.getHost())) {
                return null;
            }
            URLConnection conn = uri.toURL().openConnection();
            if (conn instanceof HttpURLConnection http) {
                http.setInstanceFollowRedirects(false);
            }
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            try (InputStream in = conn.getInputStream()) {
                BufferedImage image = ImageIO.read(in);
                return image == null ? null : compute(image);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlockedHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.equals("localhost") || normalized.equals("metadata.google.internal")
                || normalized.equals("metadata.amazonaws.com")) {
            return true;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 计算 BufferedImage 的 64bit pHash，输出 16 位小写 hex */
    public static String compute(BufferedImage source) {
        // 1. 缩放到 32x32 灰度
        BufferedImage scaled = new BufferedImage(SAMPLE, SAMPLE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.drawImage(source, 0, 0, SAMPLE, SAMPLE, null);
        g.dispose();
        double[][] pixels = new double[SAMPLE][SAMPLE];
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                pixels[y][x] = scaled.getRaster().getSample(x, y, 0);
            }
        }
        // 2. 二维 DCT，取左上 8x8 低频块
        double[][] dct = dct2d(pixels);
        double[] lowFreq = new double[LOW_FREQ * LOW_FREQ];
        for (int y = 0; y < LOW_FREQ; y++) {
            System.arraycopy(dct[y], 0, lowFreq, y * LOW_FREQ, LOW_FREQ);
        }
        // 3. 与中值比较生成 64bit（用中值而非均值，抗 DC 分量偏置）
        double[] sorted = lowFreq.clone();
        Arrays.sort(sorted);
        double median = (sorted[31] + sorted[32]) / 2.0;
        long bits = 0L;
        for (int i = 0; i < lowFreq.length; i++) {
            if (lowFreq[i] > median) {
                bits |= 1L << i;
            }
        }
        return String.format("%016x", bits);
    }

    /** 两个 pHash 的汉明距离；任一为空返回 Integer.MAX_VALUE（视为不重复） */
    public static int hammingDistance(String hashA, String hashB) {
        if (hashA == null || hashB == null || hashA.length() != 16 || hashB.length() != 16) {
            return Integer.MAX_VALUE;
        }
        long a = Long.parseUnsignedLong(hashA, 16);
        long b = Long.parseUnsignedLong(hashB, 16);
        return Long.bitCount(a ^ b);
    }

    private static double[][] dct2d(double[][] input) {
        int n = input.length;
        double[][] output = new double[n][n];
        double[] cos = new double[n * n];
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                cos[k * n + i] = Math.cos((2 * i + 1) * k * Math.PI / (2.0 * n));
            }
        }
        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                double sum = 0;
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        sum += input[y][x] * cos[u * n + y] * cos[v * n + x];
                    }
                }
                double cu = u == 0 ? Math.sqrt(1.0 / n) : Math.sqrt(2.0 / n);
                double cv = v == 0 ? Math.sqrt(1.0 / n) : Math.sqrt(2.0 / n);
                output[u][v] = cu * cv * sum;
            }
        }
        return output;
    }
}
