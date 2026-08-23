package com.example.arrivalalarm;

import android.content.Intent;
import android.net.Uri;

public final class ShareParser {
    private ShareParser() {}

    public static ParsedShare fromIntent(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri data = intent.getData();
            if ("arrivalalarm".equalsIgnoreCase(data.getScheme())) {
                String name = safe(data.getQueryParameter("name"));
                Double lat = number(data.getQueryParameter("lat"));
                Double lng = number(data.getQueryParameter("lng"));
                if (lat != null && lng != null && valid(lat, lng)) return new ParsedShare(name, lat, lng, "应用深链");
                return name.isEmpty() ? null : new ParsedShare(name, null, null, "应用深链");
            }
            return parseText(data.toString());
        }
        if (Intent.ACTION_SEND.equals(intent.getAction()) && (intent.getType() == null || "text/plain".equals(intent.getType()))) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            return parseText(text);
        }
        if (Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            return parseText(text == null ? null : text.toString());
        }
        return null;
    }

    public static ParsedShare parseText(String text) {
        PlaceInputParser.Result parsed = PlaceInputParser.parse(text);
        if (parsed == null) return null;
        return new ParsedShare(parsed.name, parsed.latitude, parsed.longitude, parsed.source);
    }

    public static boolean isTravelWebsite(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return false;
        String host = intent.getData().getHost();
        if (host == null) return false;
        String value = host.toLowerCase(java.util.Locale.ROOT);
        return value.equals("uri.amap.com") || value.equals("ditu.amap.com") || value.equals("surl.amap.com")
                || value.equals("map.baidu.com") || value.equals("map.qq.com")
                || value.equals("www.12306.cn") || value.equals("m.12306.cn")
                || value.equals("v.didi.cn") || value.equals("page.udache.com");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static Double number(String value) {
        try { return value == null ? null : Double.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean valid(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    public static final class ParsedShare {
        public final String name;
        public final Double latitude;
        public final Double longitude;
        public final String source;

        ParsedShare(String name, Double latitude, Double longitude, String source) {
            this.name = name == null ? "" : name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source == null ? "手动输入" : source;
        }

        public boolean hasCoordinates() { return latitude != null && longitude != null; }
    }
}
