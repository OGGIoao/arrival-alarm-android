package com.example.arrivalalarm;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ActionReceiver extends BroadcastReceiver {
    public static final String ACTION_ARRIVED = "com.example.arrivalalarm.ARRIVED";
    public static final String ACTION_STOP_REMINDER = "com.example.arrivalalarm.NOTIFICATION_STOP";
    public static final String ACTION_DISMISS_JOURNEY = "com.example.arrivalalarm.DISMISS_JOURNEY";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_DISMISS_JOURNEY.equals(action)) {
            AppPrefs prefs = new AppPrefs(context);
            CoreLogic.JourneyDetector detector = prefs.getJourneyDetector();
            if (detector != null) {
                detector.state = CoreLogic.JourneyDetector.DISMISSED;
                prefs.setJourneyRuntime(detector);
            }
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(JourneyPrompt.NOTIFICATION_ID);
            return;
        }
        if (ACTION_ARRIVED.equals(action) || ACTION_STOP_REMINDER.equals(action)) {
            Intent service = new Intent(context, TrackingService.class).setAction(TrackingService.ACTION_STOP_REMINDER);
            context.startService(service);
        }
    }
}
