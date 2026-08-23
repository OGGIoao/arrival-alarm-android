package com.example.arrivalalarm;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class ScheduleLogic {
    public static final int EVERY_DAY_MASK = bits(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY);
    public static final int WORKDAYS_MASK = bits(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY);

    private ScheduleLogic() { }

    public static long nextTriggerMillis(int hour, int minute, int daysMask, long now, TimeZone zone) {
        if (daysMask == 0) throw new IllegalArgumentException("At least one day is required");
        Calendar candidate = Calendar.getInstance(zone);
        candidate.setTimeInMillis(now);
        candidate.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        candidate.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);
        for (int offset = 0; offset <= 7; offset++) {
            if (offset > 0) candidate.add(Calendar.DAY_OF_YEAR, 1);
            int day = candidate.get(Calendar.DAY_OF_WEEK);
            if ((daysMask & (1 << day)) != 0 && candidate.getTimeInMillis() > now) return candidate.getTimeInMillis();
        }
        throw new IllegalStateException("Unable to find next trigger");
    }

    public static String daysLabel(int mask) {
        if (mask == WORKDAYS_MASK) return "工作日";
        if (mask == EVERY_DAY_MASK) return "每天";
        String[] labels = {"", "日", "一", "二", "三", "四", "五", "六"};
        StringBuilder result = new StringBuilder("周");
        for (int day = Calendar.MONDAY; day <= Calendar.SATURDAY; day++) if ((mask & (1 << day)) != 0) result.append(labels[day]);
        if ((mask & (1 << Calendar.SUNDAY)) != 0) result.append(labels[Calendar.SUNDAY]);
        return result.length() == 1 ? "未选择" : result.toString();
    }

    public static String timeLabel(int hour, int minute) {
        return String.format(Locale.CHINA, "%02d:%02d", hour, minute);
    }

    private static int bits(int... days) {
        int mask = 0;
        for (int day : days) mask |= 1 << day;
        return mask;
    }
}
