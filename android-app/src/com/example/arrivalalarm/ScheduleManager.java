package com.example.arrivalalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.TimeZone;

public final class ScheduleManager {
    private ScheduleManager() { }

    public static void scheduleAll(Context context) {
        AppPrefs prefs = new AppPrefs(context);
        for (AppPrefs.FixedAlarm alarm : prefs.getFixedAlarms()) {
            if (alarm.enabled) schedule(context, alarm);
            else cancel(context, alarm.id);
        }
    }

    public static void schedule(Context context, AppPrefs.FixedAlarm alarm) {
        if (!alarm.enabled || alarm.daysMask == 0) { cancel(context, alarm.id); return; }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long trigger = ScheduleLogic.nextTriggerMillis(alarm.hour, alarm.minute, alarm.daysMask,
                System.currentTimeMillis(), TimeZone.getDefault());
        PendingIntent operation = operation(context, alarm.id, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation);
                return;
            } catch (SecurityException revoked) {
                // Exact-alarm access can be revoked between the check and the call; keep an inexact alarm instead.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation);
    }

    public static void cancel(Context context, String id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent operation = operation(context, id, PendingIntent.FLAG_NO_CREATE);
        if (manager != null && operation != null) manager.cancel(operation);
    }

    public static boolean hasExactAlarmAccess(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < 31 || manager == null || manager.canScheduleExactAlarms();
    }

    private static PendingIntent operation(Context context, String id, int flag) {
        Intent intent = new Intent(context, FixedAlarmReceiver.class).setAction(FixedAlarmReceiver.ACTION_TRIGGER)
                .putExtra(FixedAlarmReceiver.EXTRA_FIXED_ALARM_ID, id);
        return PendingIntent.getBroadcast(context, id.hashCode() & 0x7fffffff, intent, flag | PendingIntent.FLAG_IMMUTABLE);
    }
}
