package com.example.arrivalalarm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StationSignalMatcher {
    private static final Pattern EXPLICIT = Pattern.compile("([\\p{IsHan}A-Za-z0-9·]{1,20}(?:地铁站|火车站|高铁站|铁路客运站))");
    private static final Pattern NAMED_STATION = Pattern.compile("([\\p{IsHan}A-Za-z0-9·]{2,16}站)");
    private static final String[] EXCLUDED = {
            "公交站", "汽车站", "加油站", "收费站", "卫生站", "工作站", "服务站", "驿站", "回收站"
    };

    private StationSignalMatcher() {}

    public static String findStation(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            Matcher explicit = EXPLICIT.matcher(value);
            if (explicit.find()) return shorten(explicit.group(1));
            Matcher named = NAMED_STATION.matcher(value);
            while (named.find()) {
                String candidate = named.group(1);
                if (!excluded(candidate)) return shorten(candidate);
            }
        }
        return null;
    }

    private static boolean excluded(String value) {
        for (String word : EXCLUDED) if (value.endsWith(word)) return true;
        return false;
    }

    private static String shorten(String value) {
        if (value == null || value.length() <= 12) return value;
        int cut = -1;
        for (char marker : new char[]{'省', '市', '区', '县', '镇', '乡'}) {
            int index = value.lastIndexOf(marker);
            if (index > cut && value.length() - index >= 4) cut = index;
        }
        return cut >= 0 ? value.substring(cut + 1) : value;
    }
}
