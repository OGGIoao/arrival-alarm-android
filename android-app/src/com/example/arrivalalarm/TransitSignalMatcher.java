package com.example.arrivalalarm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Matches only a small allow-list of travel apps and never stores notification text. */
public final class TransitSignalMatcher {
    private static final Set<String> PACKAGES = new HashSet<>(Arrays.asList(
            "com.autonavi.minimap", "com.baidu.BaiduMap", "com.tencent.map",
            "com.sdu.didi.psnger", "com.sdu.didi.gsui", "com.MobileTicket",
            "com.eg.android.AlipayGphone", "com.tencent.mm", "com.unionpay",
            "com.unionpay.tsmservice", "com.huawei.wallet", "com.miui.tsmclient",
            "com.finshell.wallet", "com.coloros.wallet", "com.vivo.wallet",
            "com.samsung.android.spay", "com.caocaokeji.user", "com.t3go.passenger"));

    private static final String[] STRONG_SIGNALS = {
            "刷卡成功", "乘车成功", "进站成功", "检票成功", "已进站", "已检票",
            "开始导航", "正在导航", "行程开始", "已上车", "司机已到达"
    };
    private static final String[] TRANSIT_WORDS = {
            "地铁", "公交", "交通卡", "乘车码", "铁路", "列车", "车次", "高铁", "火车", "车站"
    };
    private static final String[] ACTION_WORDS = {
            "进站", "检票", "刷卡", "乘车", "出发", "开车", "候车", "行程", "提醒"
    };

    private TransitSignalMatcher() {}

    public static boolean isSupportedPackage(String packageName) {
        return packageName != null && PACKAGES.contains(packageName);
    }

    public static boolean matches(String packageName, String title, String text) {
        if (!isSupportedPackage(packageName)) return false;
        String combined = safe(title) + " " + safe(text);
        for (String signal : STRONG_SIGNALS) if (combined.contains(signal)) return true;
        return containsAny(combined, TRANSIT_WORDS) && containsAny(combined, ACTION_WORDS);
    }

    private static boolean containsAny(String value, String[] words) {
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
