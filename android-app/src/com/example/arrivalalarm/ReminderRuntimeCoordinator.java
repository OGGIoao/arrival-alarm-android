package com.example.arrivalalarm;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

/** Coordinates user commands and the foreground service without exposing runtime state to the UI. */
public final class ReminderRuntimeCoordinator {
    public static final int RESTORE_NOTIFICATION_ID = 300;

    private static final String CHANNEL_RESTORE = "manual_restore";

    private ReminderRuntimeCoordinator() {}

    public static final class Status {
        public final AppPrefs.ReminderTask task;
        public final ReminderStateLogic.State state;

        private Status(AppPrefs.ReminderTask task, ReminderStateLogic.State state) {
            this.task = task;
            this.state = state;
        }
    }

    public static Status reconcile(Context context, ReminderRuntime.Snapshot runtime, long nowElapsedRealtime) {
        AppPrefs prefs = new AppPrefs(context);
        AppPrefs.ReminderTask task = prefs.getTask();
        ReminderStateLogic.State state = ReminderStateLogic.classify(task, runtime, nowElapsedRealtime);
        if (state == ReminderStateLogic.State.NEEDS_RESUME && task != null && task.isActive()) {
            task.markInterrupted();
            prefs.saveTask(task);
            context.stopService(new Intent(context, TrackingService.class));
            task = prefs.getTask();
            state = ReminderStateLogic.classify(task, runtime, nowElapsedRealtime);
        }
        return new Status(task, state);
    }

    public static boolean hasCorePermissions(Context context) {
        boolean location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean notifications = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        return location && notifications;
    }

    public static boolean startReminder(Context context) {
        if (!hasCorePermissions(context)) return false;
        return startPersistedReminder(context);
    }

    public static boolean resumeReminder(Context context) {
        if (!hasCorePermissions(context)) return false;
        AppPrefs prefs = new AppPrefs(context);
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null) return false;
        task.resume();
        prefs.saveTask(task);
        cancelRestoreNotification(context);
        return startPersistedReminder(context);
    }

    public static void stopReminder(Context context) {
        new AppPrefs(context).clearTask();
        cancelRestoreNotification(context);
        context.stopService(new Intent(context, TrackingService.class));
    }

    public static void ensureRestoreNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_RESTORE,
                "任务恢复提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("提示用户在重启后手动恢复到站提醒");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    public static void showRestoreNotification(Context context, AppPrefs.ReminderTask task, String reason) {
        if (task == null) return;
        ensureRestoreNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 301, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent resumeIntent = new Intent(context, ActionReceiver.class)
                .setAction(ActionReceiver.ACTION_RESUME_REMINDER);
        PendingIntent resume = PendingIntent.getBroadcast(context, 302, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String suffix = reason == null || reason.isEmpty() ? "" : " " + reason;
        Notification notification = new Notification.Builder(context, CHANNEL_RESTORE)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle("到站提醒已中断")
                .setContentText(task.destination.name + " 需要手动恢复")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "监控被系统终止或应用重启后不会自动恢复。" + suffix
                                + "请点按“恢复”重新启动监控，也可打开应用查看状态。"))
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "恢复", resume).build())
                .build();
        manager.notify(RESTORE_NOTIFICATION_ID, notification);
    }

    public static void cancelRestoreNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(RESTORE_NOTIFICATION_ID);
    }

    private static boolean startPersistedReminder(Context context) {
        AppPrefs.ReminderTask task = new AppPrefs(context).getTask();
        if (task == null) return false;
        TrackingService.markStartRequested();
        try {
            Intent service = new Intent(context, TrackingService.class)
                    .setAction(TrackingService.ACTION_START_REMINDER);
            context.startForegroundService(service);
            return true;
        } catch (RuntimeException blocked) {
            task.markInterrupted();
            new AppPrefs(context).saveTask(task);
            TrackingService.markStartFailed();
            showRestoreNotification(context, task, "系统暂时不允许启动定位服务。");
            return false;
        }
    }
}
