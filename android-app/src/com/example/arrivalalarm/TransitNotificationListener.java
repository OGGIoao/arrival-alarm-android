package com.example.arrivalalarm;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Optional local-only signal. Notification contents are filtered in memory and never persisted. */
public final class TransitNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || !TransitSignalMatcher.isSupportedPackage(sbn.getPackageName())) return;
        AppPrefs prefs = new AppPrefs(this);
        if (!prefs.isTransitWatchEnabled() || prefs.getTask() != null || !prefs.canShowAutoPrompt(System.currentTimeMillis())) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        Bundle extras = notification.extras;
        String title = string(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = string(extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = string(extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT));
        if (!TransitSignalMatcher.matches(sbn.getPackageName(), title, text)) return;
        prefs.markAutoPrompt(System.currentTimeMillis());
        CoreLogic.JourneyDetector detector = prefs.getJourneyDetector();
        if (detector != null) {
            detector.state = CoreLogic.JourneyDetector.PROMOTED;
            prefs.setJourneyRuntime(detector);
        }
        JourneyPrompt.show(this, "检测到导航或进站信号", "别忘了设置到站提醒");
    }

    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
}
