package com.example.arrivalalarm;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PlaceQuality {
    private PlaceQuality() { }

    public static boolean isRelevant(String query, String name, String city, String address) {
        String q = normalize(query);
        String n = normalize(name);
        String combined = normalize((city == null ? "" : city) + (name == null ? "" : name)
                + (address == null ? "" : address));
        if (q.isEmpty() || n.isEmpty()) return false;
        if (q.length() == 1) return n.equals(q);
        if (n.length() < 2) return false;
        if (n.equals(q) || n.contains(q) || combined.contains(q)) return true;
        if (q.contains(n) && n.length() >= Math.max(2, q.length() / 2)) return true;
        return characterCoverage(q, combined) >= 0.80;
    }

    public static boolean shouldRecoverQueryName(String query, String name, String city, String address) {
        String q = normalize(query);
        String n = normalize(name);
        String c = normalize(city);
        String a = normalize(address);
        if (q.length() < 3 || n.isEmpty() || n.length() >= 2 || !q.endsWith(n)) return false;
        String cityCore = c.endsWith("市") || c.endsWith("省") ? c.substring(0, c.length() - 1) : c;
        return !cityCore.isEmpty() && (q.contains(cityCore) || a.contains(cityCore));
    }

    public static int score(String query, String name, String city, String address) {
        String q = normalize(query);
        String n = normalize(name);
        String combined = normalize((city == null ? "" : city) + (name == null ? "" : name)
                + (address == null ? "" : address));
        if (n.equals(q)) return 100;
        if (n.startsWith(q)) return 90;
        if (n.contains(q)) return 80;
        if (combined.contains(q)) return 70;
        if (q.contains(n) && n.length() >= 2) return 60;
        return (int) Math.round(characterCoverage(q, combined) * 50.0);
    }

    static String normalize(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.CHINA);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) result.append(c);
        }
        return result.toString();
    }

    private static double characterCoverage(String query, String candidate) {
        if (query.isEmpty()) return 0;
        Set<Character> required = new HashSet<>();
        for (int i = 0; i < query.length(); i++) required.add(query.charAt(i));
        int matches = 0;
        for (Character c : required) if (candidate.indexOf(c) >= 0) matches++;
        return required.isEmpty() ? 0 : matches / (double) required.size();
    }
}
