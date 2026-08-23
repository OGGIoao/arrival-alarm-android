package com.example.arrivalalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public final class FixedAlarmPrompt {
    public static final String CHANNEL_ID = "fixed_commute_alarm";

    private FixedAlarmPrompt() { }

    public static void show(Context context, AppPrefs.FixedAlarm alarm, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "固定地点闹钟", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("固定通勤日程需要确认或补充权限时提醒");
        manager.createNotificationChannel(channel);
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(FixedAlarmReceiver.EXTRA_FIXED_ALARM_ID, alarm.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = alarm.id.hashCode() & 0x7fffffff;
        PendingIntent content = PendingIntent.getActivity(context, requestCode, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle("该出发去 " + alarm.destination.name + " 了")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(new Notification.Action.Builder(null, "立即开启", content).build())
                .build();
        manager.notify(requestCode, notification);
    }
}
