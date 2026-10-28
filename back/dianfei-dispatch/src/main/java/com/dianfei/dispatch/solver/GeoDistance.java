package com.dianfei.dispatch.solver;

/**
 * 地理距离（F4）：Haversine 球面距离，片区尺度精度足够。
 * 开放问题（memory.md）：高德驾车距离矩阵接入后，本类退化为其降级兜底 ——
 * 替换点收敛在 travelMinutes/meters 两个静态方法，求解内核零改动。
 */
public final class GeoDistance {

    /** 城市道路平均时速 km/h（含红灯，经验值） */
    private static final double SPEED_KMH = 25.0;

    private static final double EARTH_RADIUS_M = 6371000.0;

    private GeoDistance() {
    }

    /** 两点球面距离（米） */
    public static double meters(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** 两点行驶耗时（分钟，向上取整） */
    public static long travelMinutes(double lng1, double lat1, double lng2, double lat2) {
        double km = meters(lng1, lat1, lng2, lat2) / 1000.0;
        return (long) Math.ceil(km / SPEED_KMH * 60);
    }
}
