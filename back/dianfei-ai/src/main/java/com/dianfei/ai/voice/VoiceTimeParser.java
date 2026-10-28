package com.dianfei.ai.voice;

import cn.hutool.core.util.StrUtil;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F7 预约时间标准化（05 章 5.5：slots.appoint_time 为自然语言，Java 侧标准化）：
 * 覆盖口语高频表达（今/明/后天 + 上午/下午/晚上 + 具体点数、周 X），
 * 解析结果早于当前时间时顺延一天；完全无法解析返回 null 由上层追问。
 */
public final class VoiceTimeParser {

    private static final LocalTime MORNING = LocalTime.of(9, 0);
    private static final LocalTime AFTERNOON = LocalTime.of(15, 0);
    private static final LocalTime EVENING = LocalTime.of(19, 0);
    private static final LocalTime DEFAULT = LocalTime.of(10, 0);

    private static final Pattern HOUR = Pattern.compile("(\\d{1,2})\\s*[点时]");

    private VoiceTimeParser() {
    }

    /** 自然语言 → 预约时间；无法识别返回 null（驱动多轮追问） */
    public static LocalDateTime parse(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        LocalDate date = null;
        if (text.contains("后天")) {
            date = today.plusDays(2);
        } else if (text.contains("明天") || text.contains("明日")) {
            date = today.plusDays(1);
        } else if (text.contains("今天") || text.contains("今日")) {
            date = today;
        } else {
            DayOfWeek dow = weekOf(text);
            if (dow != null) {
                date = today.with(TemporalAdjusters.nextOrSame(dow));
            }
        }
        LocalTime time = timeOf(text);
        if (date == null && time == null) {
            return null;
        }
        LocalDateTime result = LocalDateTime.of(
                date == null ? today : date, time == null ? DEFAULT : time);
        // 口语"今天下午"已过时/仅说"上午 10 点"：顺延到最近未来同一时段
        while (!result.isAfter(LocalDateTime.now())) {
            result = result.plusDays(1);
        }
        return result;
    }

    private static DayOfWeek weekOf(String text) {
        if (text.contains("周六") || text.contains("星期六")) {
            return DayOfWeek.SATURDAY;
        }
        if (text.contains("周日") || text.contains("星期天") || text.contains("星期日")) {
            return DayOfWeek.SUNDAY;
        }
        if (text.contains("周五") || text.contains("星期五")) {
            return DayOfWeek.FRIDAY;
        }
        return null;
    }

    private static LocalTime timeOf(String text) {
        Matcher m = HOUR.matcher(text);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            if ((text.contains("下午") || text.contains("晚") || text.contains("傍晚")) && hour < 12) {
                hour += 12;
            }
            if (hour >= 0 && hour <= 23) {
                return LocalTime.of(hour, 0);
            }
        }
        if (text.contains("上午") || text.contains("早")) {
            return MORNING;
        }
        if (text.contains("下午") || text.contains("中午")) {
            return AFTERNOON;
        }
        if (text.contains("晚")) {
            return EVENING;
        }
        return null;
    }
}
