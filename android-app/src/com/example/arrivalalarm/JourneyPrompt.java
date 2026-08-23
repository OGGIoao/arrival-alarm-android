package com.example.arrivalalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public final class JourneyPrompt {
    public static final String CHANNEL_ID = "journey_prompt";
    public static final int NOTIFICATION_ID = 101;

    private JourneyPrompt() {}

    public static void show(Context context, String title, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "出行提示", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("检测到乘车、导航或进站信号后询问是否设置目的地");
        manager.createNotificationChannel(channel);

        Intent open = new Intent(context, MainActivity.class).putExtra(TrackingService.EXTRA_FOCUS_DESTINATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 30, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dismissIntent = new Intent(context, ActionReceiver.class).setAction(ActionReceiver.ACTION_DISMISS_JOURNEY);
        PendingIntent dismiss = PendingIntent.getBroadcast(context, 31, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(content)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "设置目的地", content).build())
                .addAction(new Notification.Action.Builder(null, "本次不用", dismiss).build())
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
