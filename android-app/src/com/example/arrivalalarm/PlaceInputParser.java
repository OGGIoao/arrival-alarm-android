package com.example.arrivalalarm;

import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only text explicitly supplied to this app. It performs no network access. */
public final class PlaceInputParser {
    private static final String NUMBER = "([+-]?\\d{1,3}(?:\\.\\d+)?)";
    private static final Pattern LAT_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dlat|lat|latitude)=" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern LNG_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dlon|dlng|lng|lon|longitude)=" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTINATION_LNG_LAT = Pattern.compile("(?:[?&#;]|\\b)(?:to|destination|dest)=" + NUMBER + "[,，]" + NUMBER + "(?:[,，]([^&#;\\s]+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_LNG_LAT = Pattern.compile("(?:position|center)=" + NUMBER + "[,，]" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCATION_LAT_LNG = Pattern.compile("(?:location|coord)[:=]" + NUMBER + "[,，]" + NUMBER, Pattern.CASE_INSENSITIVE);
    private static final Pattern AT_LAT_LNG = Pattern.compile("@" + NUMBER + "[,，]" + NUMBER);
    private static final Pattern PAIR = Pattern.compile(NUMBER + "\\s*[,，]\\s*" + NUMBER);
    private static final Pattern URL = Pattern.compile("(?:https?|geo|amapuri|androidamap|baidumap|qqmap)://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_PARAM = Pattern.compile("(?:[?&#;]|\\b)(?:dname|poiname|title|name|address|addr)[:=]([^&#;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUERY_LABEL = Pattern.compile("(?:[?&#;]|\\b)q=" + NUMBER + "[,，]" + NUMBER + "\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);

    private PlaceInputParser() {}

    public static Result parse(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return null;
        String decoded = decodeTwice(raw);
        String lower = decoded.toLowerCase(Locale.ROOT);
        String source = sourceLabel(lower);

        Double lat = null;
        Double lng = null;
        String embeddedName = "";

        Matcher latParam = LAT_PARAM.matcher(decoded);
        Matcher lngParam = LNG_PARAM.matcher(decoded);
        if (latParam.find() && lngParam.find()) {
            lat = number(latParam.group(1));
            lng = number(lngParam.group(1));
        } else {
            Matcher destination = DESTINATION_LNG_LAT.matcher(decoded);
            Matcher position = POSITION_LNG_LAT.matcher(decoded);
            Matcher location = LOCATION_LAT_LNG.matcher(decoded);
            Matcher at = AT_LAT_LNG.matcher(decoded);
            if (destination.find()) {
                lng = number(destination.group(1));
                lat = number(destination.group(2));
                embeddedName = cleanName(destination.group(3));
            } else if (position.find()) {
                lng = number(position.group(1));
                lat = number(position.group(2));
            } else if (location.find()) {
                lat = number(location.group(1));
                lng = number(location.group(2));
            } else if (at.find()) {
                lat = number(at.group(1));
                lng = number(at.group(2));
            } else {
                Matcher pair = PAIR.matcher(decoded);
                if (pair.find()) {
                    double first = number(pair.group(1));
                    double second = number(pair.group(2));
                    if (valid(first, second)) {
                        lat = first;
                        lng = second;
                    } else if (valid(second, first)) {
                        lat = second;
                        lng = first;
                    }
                }
            }
        }
        if (!valid(lat, lng)) { lat = null; lng = null; }

        String name = embeddedName;
        Matcher label = QUERY_LABEL.matcher(decoded);
        if (name.isEmpty() && label.find()) name = cleanName(label.group(3));
        Matcher named = NAME_PARAM.matcher(decoded);
        if (name.isEmpty() && named.find()) name = cleanName(named.group(1));
        if (name.isEmpty()) {
            name = URL.matcher(decoded).replaceAll(" ");
            name = PAIR.matcher(name).replaceAll(" ");
            name = cleanName(name);
        }

        if (name.isEmpty() && lat == null) return null;
        return new Result(name, lat, lng, source);
    }

    private static String sourceLabel(String lower) {
        if (lower.contains("amap.com") || lower.contains("amapuri://") || lower.contains("androidamap://")) return "高德地图分享";
        if (lower.contains("baidu.com") || lower.contains("baidumap://")) return "百度地图分享";
        if (lower.contains("map.qq.com") || lower.contains("qqmap://")) return "腾讯地图分享";
        if (lower.contains("didi") || lower.contains("didiglobal")) return "打车软件分享";
        if (lower.contains("://")) return "外部链接";
        return "手动输入";
    }

    private static String decodeTwice(String value) {
        String result = value;
        for (int i = 0; i < 2; i++) {
            try {
                String decoded = URLDecoder.decode(result, "UTF-8");
                if (decoded.equals(result)) break;
                result = decoded;
            } catch (Exception ignored) { break; }
        }
        return result;
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String result = value.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("(?:复制|打开|查看|来自|分享)(?:链接|到)?", " ")
                .replaceAll("(?:高德|百度|腾讯)地图(?:APP)?", " ")
                .replaceAll("^[,，;；:：\\s]+|[,，;；:：\\s]+$", "")
                .replaceAll("\\s+", " ").trim();
        if (result.length() > 100) result = result.substring(0, 100).trim();
        return result;
    }

    private static Double number(String value) {
        try { return value == null ? null : Double.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean valid(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    public static final class Result {
        public final String name;
        public final Double latitude;
        public final Double longitude;
        public final String source;

        Result(String name, Double latitude, Double longitude, String source) {
            this.name = name == null ? "" : name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source == null ? "手动输入" : source;
        }

        public boolean hasCoordinates() { return latitude != null && longitude != null; }
    }
}
