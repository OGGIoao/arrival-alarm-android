package com.example.arrivalalarm;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.util.Locale;
import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START_REMINDER = "com.example.arrivalalarm.START_REMINDER";
    public static final String ACTION_STOP_REMINDER = "com.example.arrivalalarm.STOP_REMINDER";
    public static final String ACTION_ENABLE_JOURNEY = "com.example.arrivalalarm.ENABLE_JOURNEY";
    public static final String ACTION_DISABLE_JOURNEY = "com.example.arrivalalarm.DISABLE_JOURNEY";
    public static final String ACTION_CLEAR_ALL = "com.example.arrivalalarm.CLEAR_ALL";
    public static final String ACTION_STATUS = "com.example.arrivalalarm.STATUS";
    public static final String PERMISSION_STATUS = BuildConfig.APPLICATION_ID + ".permission.INTERNAL_STATUS";
    public static final String EXTRA_FOCUS_DESTINATION = "focus_destination";

    private static final String CHANNEL_TRACKING = "tracking";
    private static final String CHANNEL_ALARM = "arrival_alarm";
    private static final int FOREGROUND_ID = 100;
    private static final long REMINDER_HEARTBEAT_INTERVAL_MS = 60_000L;
    private static volatile ReminderRuntime.Snapshot runtimeSnapshot = ReminderRuntime.Snapshot.stopped();
    private static volatile long runtimeGeneration;
    private static final Set<String> TRAVEL_PACKAGES = new HashSet<>(Arrays.asList(
            "com.autonavi.minimap", "com.baidu.BaiduMap", "com.tencent.map",
            "com.sdu.didi.psnger", "com.sdu.didi.gsui", "com.sankuai.meituan",
            "com.caocaokeji.user", "com.t3go.passenger", "com.huaxiaozhu.rider"));
    private static final Set<String> BROWSER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.chrome", "com.android.browser", "com.microsoft.emmx",
            "com.huawei.browser", "com.heytap.browser", "com.vivo.browser",
            "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.UCMobile"));

    private AppPrefs prefs;
    private LocationManager locationManager;
    private NotificationManager notifications;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CoreLogic.ArrivalTracker arrivalTracker;
    private CoreLogic.JourneyDetector journeyDetector;
    private Ringtone ringtone;
    private int alarmCount;
    private long lastUsageCheckAt;
    private long lastStationLookupAt;
    private boolean stationLookupInFlight;
    private final AsyncResultGate stationLookupResults = new AsyncResultGate();
    private Future<?> stationLookupFuture;
    private final ExecutorService stationLookupExecutor = Executors.newSingleThreadExecutor();
    private final ReminderProviderRegistry providerRegistry = new ReminderProviderRegistry();
    private long serviceGeneration;
    private boolean acceptingLocationCallbacks;
    private int locationRecoveryAttempt;
    private long locationRecoveryStartedElapsedRealtime;
    private boolean locationRecoveryForcePeriodic;

    private final Runnable usageCheck = new Runnable() {
        @Override public void run() {
            if (prefs.getTask() == null && prefs.isJourneyEnabled() && prefs.isAppWatchEnabled()) checkRecentlyOpenedTravelApp();
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable alarmPulse = new Runnable() {
        @Override public void run() {
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || !task.isAlerting() || alarmCount >= 10) {
                stopAlarmSound();
                return;
            }
            alarmCount++;
            playAlarmPulse();
            handler.postDelayed(this, 30_000L);
        }
    };

    private final Runnable reminderHeartbeat = new Runnable() {
        @Override public void run() {
            if (!ownsCurrentRuntime()) return;
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || task.isInterrupted()) return;
            long now = SystemClock.elapsedRealtime();
            if (runtimeSnapshot.state == ReminderRuntime.State.LOCATION_READY
                    && runtimeSnapshot.registeredProviderCount > 0
                    && (runtimeSnapshot.lastLocationElapsedRealtime == 0L
                    ? !runtimeSnapshot.isWaitingForFirstLocationWithinTimeout(now)
                    : !runtimeSnapshot.isLocationFresh(now))) {
                startLocationRecovery(now);
            } else {
                publishRuntime(runtimeSnapshot.heartbeat(now));
            }
            if (ownsCurrentRuntime() && runtimeSnapshot.state != ReminderRuntime.State.STOPPED
                    && runtimeSnapshot.state != ReminderRuntime.State.FAILED) {
                handler.postDelayed(this, REMINDER_HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private final Runnable locationRecoveryRetry = new Runnable() {
        @Override public void run() {
            if (!ownsCurrentRuntime() || runtimeSnapshot.state != ReminderRuntime.State.RECOVERING) return;
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task == null || (!task.isActive() && !task.isStarting())) return;
            if (runtimeSnapshot.lastLocationElapsedRealtime > 0L
                    && runtimeSnapshot.lastLocationElapsedRealtime >= locationRecoveryStartedElapsedRealtime) {
                finishLocationRecovery(task);
                return;
            }
            if (LocationRecoveryPolicy.hasAttemptsRemaining(locationRecoveryAttempt)) {
                attemptLocationRecovery(SystemClock.elapsedRealtime());
            } else {
                failReminder(ReminderRuntime.Failure.LOCATION_STALE,
                        "定位多次自动恢复失败，请检查定位开关或系统省电设置。");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        serviceGeneration = runtimeGeneration;
        runtimeSnapshot = ReminderRuntime.Snapshot.starting(SystemClock.elapsedRealtime());
        prefs = new AppPrefs(this);
        prefs.disableDeferredAutomation();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceGeneration = runtimeGeneration;
        cancelStationLookup();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP_REMINDER.equals(action)) {
            prefs.clearTask();
            arrivalTracker = null;
            stopAlarmSound();
            restoreModeOrStop();
        } else if (ACTION_ENABLE_JOURNEY.equals(action)) {
            prefs.disableDeferredAutomation();
            if (prefs.getTask() == null) stopEverything();
        } else if (ACTION_DISABLE_JOURNEY.equals(action)) {
            prefs.setJourneyEnabled(false);
            journeyDetector = null;
            if (prefs.getTask() == null) stopEverything();
        } else if (ACTION_CLEAR_ALL.equals(action)) {
            prefs.clearAll();
            stopEverything();
        } else {
            AppPrefs.ReminderTask task = prefs.getTask();
            if (task != null) startReminderMode(task);
            else stopEverything();
        }
        sendStatus();
        return START_NOT_STICKY;
    }

    private void startReminderMode(AppPrefs.ReminderTask task) {
        handler.removeCallbacks(usageCheck);
        resetLocationRecovery();
        journeyDetector = null;
        if (task.isInterrupted()) {
            stopEverything();
            return;
        }
        startReminderHeartbeat();
        if (task.isAlerting()) {
            removeLocationUpdates();
            try {
                startForeground(FOREGROUND_ID, buildAlarmNotification(task));
            } catch (RuntimeException failed) {
                failReminder(ReminderRuntime.Failure.FOREGROUND_START, "系统暂时不允许启动定位服务。");
                return;
            }
            publishRuntime(ReminderRuntime.Snapshot.foreground(
                    SystemClock.elapsedRealtime(), SystemClock.elapsedRealtime()));
            startAlarmSound();
            return;
        }
        arrivalTracker = new CoreLogic.ArrivalTracker(task.destination.latitude, task.destination.longitude, task.radiusMeters);
        try {
            startForeground(FOREGROUND_ID, buildTrackingNotification(task));
        } catch (RuntimeException failed) {
            failReminder(ReminderRuntime.Failure.FOREGROUND_START, "系统暂时不允许启动定位服务。");
            return;
        }
        publishRuntime(ReminderRuntime.Snapshot.foreground(
                SystemClock.elapsedRealtime(), SystemClock.elapsedRealtime()));
        requestLocationUpdates(true);
    }

    private void startJourneyMode() {
        if (prefs.getTask() != null) return;
        journeyDetector = prefs.getJourneyDetector();
        try {
            startForeground(FOREGROUND_ID, buildJourneyNotification());
        } catch (RuntimeException failed) {
            prefs.setJourneyEnabled(false);
            publishRuntime(ReminderRuntime.Snapshot.failed(
                    ReminderRuntime.Failure.FOREGROUND_START, SystemClock.elapsedRealtime()));
            stopEverything();
            return;
        }
        requestLocationUpdates(false);
        handler.removeCallbacks(usageCheck);
        lastUsageCheckAt = System.currentTimeMillis() - 45_000L;
        handler.post(usageCheck);
    }

    private void restoreModeOrStop() {
        stopEverything();
    }

    private void requestLocationUpdates(boolean precise) {
        requestLocationUpdates(precise, false, false, false);
    }

    private void requestLocationUpdates(boolean precise, boolean recovery) {
        requestLocationUpdates(precise, recovery, recovery, false);
    }

    private void requestLocationUpdates(boolean precise, boolean recovery,
                                        boolean forcePeriodic, boolean preserveRuntime) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            failReminder(ReminderRuntime.Failure.LOCATION_PERMISSION, "请重新允许精确定位。");
            return;
        }
        if (locationManager == null) {
            failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备没有可用的定位服务。");
            return;
        }
        if (!preserveRuntime) removeLocationUpdates();
        long minTime = forcePeriodic ? 15_000L : (precise ? 5_000L : 60_000L);
        float minDistance = forcePeriodic ? 0f : (precise ? 0f : 100f);
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTime, minDistance, this, Looper.getMainLooper());
                providerRegistry.add(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                long networkMinTime = forcePeriodic ? 15_000L : (precise ? 15_000L : minTime);
                float networkMinDistance = forcePeriodic ? 0f : (precise ? 0f : minDistance);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, networkMinTime, networkMinDistance, this, Looper.getMainLooper());
                providerRegistry.add(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
            failReminder(ReminderRuntime.Failure.LOCATION_PERMISSION, "请重新允许精确定位。");
            return;
        } catch (IllegalArgumentException ignored) {
            if (providerRegistry.count() == 0) {
                if (task != null && (task.isActive() || task.isStarting())) {
                    if (!recovery) {
                        startLocationRecovery(SystemClock.elapsedRealtime());
                    } else if (LocationRecoveryPolicy.hasAttemptsRemaining(locationRecoveryAttempt)) {
                        scheduleLocationRecoveryRetry(locationRecoveryAttempt);
                    } else {
                        failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备暂时无法建立定位监听。");
                    }
                } else {
                    failReminder(ReminderRuntime.Failure.LOCATION_LISTENER, "设备暂时无法建立定位监听。");
                }
                return;
            }
        }
        if (providerRegistry.count() == 0) {
            failReminder(ReminderRuntime.Failure.NO_LOCATION_PROVIDER, "请打开定位后再恢复守候。");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        acceptingLocationCallbacks = true;
        if (recovery && task != null && task.isActive()) {
            locationRecoveryForcePeriodic = forcePeriodic;
            publishRuntime(ReminderRuntime.Snapshot.recovering(
                    providerRegistry.count(), locationRecoveryAttempt, now, now,
                    runtimeSnapshot.lastLocationElapsedRealtime));
            notifications.notify(FOREGROUND_ID, buildRecoveryNotification(task, locationRecoveryAttempt));
        } else {
            if (recovery && task != null) {
                locationRecoveryAttempt = 0;
                locationRecoveryStartedElapsedRealtime = 0L;
                handler.removeCallbacks(locationRecoveryRetry);
                locationRecoveryForcePeriodic = forcePeriodic;
            } else if (!recovery) {
                locationRecoveryForcePeriodic = false;
            }
            if (task != null) {
                task.markActive();
                prefs.saveTask(task);
            }
            if (!preserveRuntime) {
                publishRuntime(ReminderRuntime.Snapshot.locationReady(providerRegistry.count(), now, now, 0L));
            }
        }
    }

    private void startLocationRecovery(long nowElapsedRealtime) {
        if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) return;
        locationRecoveryAttempt = 0;
        attemptLocationRecovery(nowElapsedRealtime);
    }

    private void attemptLocationRecovery(long nowElapsedRealtime) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null || (!task.isActive() && !task.isStarting())) return;
        locationRecoveryAttempt++;
        locationRecoveryStartedElapsedRealtime = nowElapsedRealtime;
        publishRuntime(ReminderRuntime.Snapshot.recovering(
                providerRegistry.count(), locationRecoveryAttempt, nowElapsedRealtime,
                nowElapsedRealtime, runtimeSnapshot.lastLocationElapsedRealtime));
        notifications.notify(FOREGROUND_ID, buildRecoveryNotification(task, locationRecoveryAttempt));
        requestLocationUpdates(true, true);
        if (ownsCurrentRuntime() && runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            scheduleLocationRecoveryRetry(locationRecoveryAttempt);
        }
    }

    private void scheduleLocationRecoveryRetry(int attempt) {
        handler.removeCallbacks(locationRecoveryRetry);
        handler.postDelayed(locationRecoveryRetry, LocationRecoveryPolicy.delayAfterAttempt(attempt));
    }

    private void finishLocationRecovery(AppPrefs.ReminderTask task) {
        locationRecoveryAttempt = 0;
        locationRecoveryStartedElapsedRealtime = 0L;
        locationRecoveryForcePeriodic = false;
        handler.removeCallbacks(locationRecoveryRetry);
        notifications.notify(FOREGROUND_ID, buildTrackingNotification(task));
    }

    private void resetLocationRecovery() {
        locationRecoveryAttempt = 0;
        locationRecoveryStartedElapsedRealtime = 0L;
        locationRecoveryForcePeriodic = false;
        handler.removeCallbacks(locationRecoveryRetry);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || !ownsCurrentRuntime() || !acceptingLocationCallbacks) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null || !task.isActive() || providerRegistry.count() == 0) return;
        long nowElapsedRealtime = SystemClock.elapsedRealtime();
        boolean restoreNormalLocationProfile = false;
        if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            restoreNormalLocationProfile = locationRecoveryForcePeriodic;
            finishLocationRecovery(task);
        }
        publishRuntime(runtimeSnapshot.locationReceived(nowElapsedRealtime));
        if (restoreNormalLocationProfile) {
            requestLocationUpdates(true, false, false, true);
        }
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
        float speed = location.hasSpeed() ? location.getSpeed() : -1f;
        long timestamp = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
        CoreLogic.LocationFix fix = new CoreLogic.LocationFix(location.getLatitude(), location.getLongitude(), accuracy, speed, timestamp);
        processReminder(task, fix);
    }

    @Override public void onProviderEnabled(String provider) {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (ownsCurrentRuntime() && task != null && task.isActive()) {
            requestLocationUpdates(true, runtimeSnapshot.state == ReminderRuntime.State.RECOVERING);
        }
    }
    @Override public void onProviderDisabled(String provider) {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (!ownsCurrentRuntime() || !acceptingLocationCallbacks || task == null || !task.isActive()) return;
        if (!providerRegistry.remove(provider)) return;
        if (providerRegistry.count() == 0) {
            failReminder(ReminderRuntime.Failure.NO_LOCATION_PROVIDER, "请打开定位后再恢复守候。");
        } else if (runtimeSnapshot.state == ReminderRuntime.State.RECOVERING) {
            publishRuntime(ReminderRuntime.Snapshot.recovering(
                    providerRegistry.count(), locationRecoveryAttempt,
                    locationRecoveryStartedElapsedRealtime,
                    runtimeSnapshot.lastHeartbeatElapsedRealtime,
                    runtimeSnapshot.lastLocationElapsedRealtime));
        } else {
            publishRuntime(ReminderRuntime.Snapshot.locationReady(
                    providerRegistry.count(), runtimeSnapshot.stateSinceElapsedRealtime,
                    runtimeSnapshot.lastHeartbeatElapsedRealtime, runtimeSnapshot.lastLocationElapsedRealtime));
        }
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void processReminder(AppPrefs.ReminderTask task, CoreLogic.LocationFix fix) {
        if (arrivalTracker == null) arrivalTracker = new CoreLogic.ArrivalTracker(task.destination.latitude, task.destination.longitude, task.radiusMeters);
        CoreLogic.ArrivalResult result = arrivalTracker.update(fix);
        task.lastDistanceMeters = result.distanceMeters;
        prefs.saveTask(task);
        notifications.notify(FOREGROUND_ID, buildTrackingNotification(task));
        sendStatus();
        if (result.triggered) triggerArrival(task);
    }

    private void triggerArrival(AppPrefs.ReminderTask task) {
        task.status = AppPrefs.ReminderTask.STATUS_ALERTING;
        task.triggeredAt = System.currentTimeMillis();
        prefs.saveTask(task);
        removeLocationUpdates();
        try {
            startForeground(FOREGROUND_ID, buildAlarmNotification(task));
        } catch (RuntimeException ignored) {
            // The service is already foreground; retain the alarm notification if the update is rejected.
            if (notifications != null) notifications.notify(FOREGROUND_ID, buildAlarmNotification(task));
        }
        publishRuntime(ReminderRuntime.Snapshot.foreground(
                SystemClock.elapsedRealtime(), SystemClock.elapsedRealtime()));
        startAlarmSound();
        sendStatus();
    }

    private void processJourney(CoreLogic.LocationFix fix) {
        if (journeyDetector == null) {
            if (prefs.hasAnchor()) journeyDetector = prefs.getJourneyDetector();
            else {
                journeyDetector = new CoreLogic.JourneyDetector(fix.latitude, fix.longitude);
                prefs.setAnchor(fix.latitude, fix.longitude);
                prefs.setJourneyState(CoreLogic.JourneyDetector.ANCHORED);
            }
        }
        boolean shouldPrompt = journeyDetector.update(fix);
        prefs.setJourneyRuntime(journeyDetector);
        if (shouldPrompt) showJourneyPrompt();
        maybeCheckNearbyStation(fix);
    }

    private void maybeCheckNearbyStation(final CoreLogic.LocationFix fix) {
        long now = System.currentTimeMillis();
        if (!prefs.isStationWatchEnabled() || stationLookupInFlight || !Geocoder.isPresent()) return;
        if (fix.accuracy > 150f || now - lastStationLookupAt < 5L * 60L * 1000L || !prefs.canShowAutoPrompt(now)) return;
        lastStationLookupAt = now;
        stationLookupInFlight = true;
        final long serviceGenerationAtStart = serviceGeneration;
        final long lookupToken = stationLookupResults.begin();
        stationLookupFuture = stationLookupExecutor.submit(new Runnable() {
            @Override public void run() {
                String station = null;
                try {
                    List<Address> results = new Geocoder(TrackingService.this, Locale.SIMPLIFIED_CHINESE)
                            .getFromLocation(fix.latitude, fix.longitude, 3);
                    if (results != null) {
                        for (Address address : results) {
                            if (address == null) continue;
                            String line = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : "";
                            station = StationSignalMatcher.findStation(address.getFeatureName(), line,
                                    address.getThoroughfare(), address.getSubLocality(), address.getLocality());
                            if (station != null) break;
                        }
                    }
                } catch (IOException | IllegalArgumentException | SecurityException ignored) { }
                final String matchedStation = station;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (!isServiceGenerationCurrent(serviceGenerationAtStart)
                                || !stationLookupResults.isCurrent(lookupToken)) return;
                        stationLookupInFlight = false;
                        stationLookupFuture = null;
                        if (matchedStation == null
                                || !prefs.isJourneyEnabled()
                                || !prefs.isStationWatchEnabled()
                                || prefs.getTask() != null
                                || !prefs.canShowAutoPrompt(System.currentTimeMillis())) return;
                        if (journeyDetector != null) {
                            journeyDetector.state = CoreLogic.JourneyDetector.PROMOTED;
                            prefs.setJourneyRuntime(journeyDetector);
                        }
                        showJourneyPrompt("已靠近" + matchedStation, "要设置到站提醒吗？");
                    }
                });
            }
        });
    }

    private void cancelStationLookup() {
        stationLookupResults.invalidate();
        stationLookupInFlight = false;
        if (stationLookupFuture != null) {
            stationLookupFuture.cancel(true);
            stationLookupFuture = null;
        }
    }

    private boolean isServiceGenerationCurrent(long generation) {
        return ownsCurrentRuntime() && generation == serviceGeneration;
    }

    private void showJourneyPrompt() {
        showJourneyPrompt("看起来你正在乘车", "要设置一个到站提醒吗？");
    }

    private void showJourneyPrompt(String title, String message) {
        long now = System.currentTimeMillis();
        if (!prefs.canShowAutoPrompt(now)) return;
        prefs.markAutoPrompt(now);
        JourneyPrompt.show(this, title, message);
    }

    private void checkRecentlyOpenedTravelApp() {
        if (!hasUsageAccess() || journeyDetector == null) return;
        if (!CoreLogic.JourneyDetector.ANCHORED.equals(journeyDetector.state)
                && !CoreLogic.JourneyDetector.CONFIRMING.equals(journeyDetector.state)) return;
        long now = System.currentTimeMillis();
        UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        if (manager == null) return;
        UsageEvents events = manager.queryEvents(lastUsageCheckAt, now);
        lastUsageCheckAt = now;
        if (events == null) return;
        UsageEvents.Event event = new UsageEvents.Event();
        String matchedPackage = null;
        boolean browserOpened = false;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND || type == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (TRAVEL_PACKAGES.contains(event.getPackageName())) matchedPackage = event.getPackageName();
                if (BROWSER_PACKAGES.contains(event.getPackageName())) browserOpened = true;
            }
        }
        boolean browserDuringTrip = browserOpened && CoreLogic.JourneyDetector.CONFIRMING.equals(journeyDetector.state);
        if (matchedPackage == null && !browserDuringTrip) return;
        journeyDetector.state = CoreLogic.JourneyDetector.PROMOTED;
        prefs.setJourneyRuntime(journeyDetector);
        if (matchedPackage != null) showJourneyPrompt("已打开地图或打车软件", "别忘了设置到站提醒");
        else showJourneyPrompt("出行中打开了浏览器", "如果正在查看出行网站，请设置到站提醒");
    }

    private boolean hasUsageAccess() {
        AppOpsManager manager = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (manager == null) return false;
        return manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private Notification buildTrackingNotification(AppPrefs.ReminderTask task) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_STOP_REMINDER);
        PendingIntent stop = PendingIntent.getBroadcast(this, 11, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String detail = Double.isNaN(task.lastDistanceMeters) ? "等待定位 · " + formatRadius(task.radiusMeters) + " 时提醒"
                : "距离约 " + formatDistance(task.lastDistanceMeters) + " · 持续守候中";
        return new Notification.Builder(this, CHANNEL_TRACKING)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle("正在守候 " + task.destination.name)
                .setContentText(detail)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private Notification buildRecoveryNotification(AppPrefs.ReminderTask task, int attempt) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 10, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_STOP_REMINDER);
        PendingIntent stop = PendingIntent.getBroadcast(this, 11, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_TRACKING)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle("正在恢复 " + task.destination.name)
                .setContentText("定位暂时中断，正在自动重试 " + attempt + "/" + LocationRecoveryPolicy.MAX_ATTEMPTS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .build();
    }

    private Notification buildJourneyNotification() {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 20, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_TRACKING)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(0, 122, 255))
                .setContentTitle("乘车提示待命")
                .setContentText(prefs.isAppWatchEnabled() ? "低频检测移动和出行 App，只在本机处理" : "低频检测移动，只在本机处理")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(content)
                .build();
    }

    private Notification buildAlarmNotification(AppPrefs.ReminderTask task) {
        Intent alarm = new Intent(this, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 40, alarm, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent arrivedIntent = new Intent(this, ActionReceiver.class).setAction(ActionReceiver.ACTION_ARRIVED);
        PendingIntent arrived = PendingIntent.getBroadcast(this, 41, arrivedIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ALARM)
                .setSmallIcon(com.example.arrivalalarm.R.drawable.ic_notification)
                .setColor(Color.rgb(242, 187, 98))
                .setContentTitle(task.destination.name + " 快到了")
                .setContentText("请确认随身物品，准备下车")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(content)
                .addAction(new Notification.Action.Builder(null, "我已到达", arrived).build())
                .build();
    }

    private void createChannels() {
        NotificationChannel tracking = new NotificationChannel(CHANNEL_TRACKING, "行程监控", NotificationManager.IMPORTANCE_LOW);
        tracking.setDescription("用户主动启动到站提醒后持续显示");
        tracking.setShowBadge(false);
        notifications.createNotificationChannel(tracking);

        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes alarmAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
        NotificationChannel alarm = new NotificationChannel(CHANNEL_ALARM, "到站闹钟", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("接近目的地时发出声音和振动");
        alarm.enableVibration(true);
        alarm.setVibrationPattern(new long[]{0, 500, 180, 500, 180, 900});
        alarm.setSound(alarmUri, alarmAttributes);
        alarm.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notifications.createNotificationChannel(alarm);
        notifications.deleteNotificationChannel(JourneyPrompt.CHANNEL_ID);

    }

    private void startAlarmSound() {
        handler.removeCallbacks(alarmPulse);
        alarmCount = 0;
        handler.post(alarmPulse);
    }

    private void playAlarmPulse() {
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (ringtone == null) {
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (Build.VERSION.SDK_INT >= 28) ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        }
        if (ringtone != null && !ringtone.isPlaying()) ringtone.play();
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0, 500, 180, 500, 180, 900};
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        }
    }

    private void stopAlarmSound() {
        handler.removeCallbacks(alarmPulse);
        alarmCount = 0;
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
        ringtone = null;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.cancel();
    }

    private void removeLocationUpdates() {
        acceptingLocationCallbacks = false;
        providerRegistry.clear();
        if (locationManager == null) return;
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void stopEverything() {
        cancelStationLookup();
        handler.removeCallbacks(usageCheck);
        resetLocationRecovery();
        stopReminderHeartbeat();
        removeLocationUpdates();
        stopAlarmSound();
        stopForeground(true);
        publishRuntime(ReminderRuntime.Snapshot.stopped());
        stopSelf();
    }

    private void startReminderHeartbeat() {
        handler.removeCallbacks(reminderHeartbeat);
        handler.postDelayed(reminderHeartbeat, REMINDER_HEARTBEAT_INTERVAL_MS);
    }

    private void stopReminderHeartbeat() {
        handler.removeCallbacks(reminderHeartbeat);
    }

    private void sendStatus() {
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName())
                .putExtra("runtime_state", runtimeSnapshot.state.name())
                .putExtra("registered_provider_count", runtimeSnapshot.registeredProviderCount)
                .putExtra("state_since_elapsed", runtimeSnapshot.stateSinceElapsedRealtime)
                .putExtra("last_heartbeat_elapsed", runtimeSnapshot.lastHeartbeatElapsedRealtime)
                .putExtra("last_location_elapsed", runtimeSnapshot.lastLocationElapsedRealtime)
                .putExtra("recovery_attempt", runtimeSnapshot.recoveryAttempt)
                .putExtra("failure", runtimeSnapshot.failure.name()), PERMISSION_STATUS);
    }

    private void failReminder(ReminderRuntime.Failure failure, String reason) {
        if (!ownsCurrentRuntime()) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task != null && !task.isAlerting()) {
            task.markInterrupted();
            prefs.saveTask(task);
            ReminderRuntimeCoordinator.showRestoreNotification(this, task, reason);
        }
        publishRuntime(ReminderRuntime.Snapshot.failed(failure, SystemClock.elapsedRealtime()));
        stopEverything();
    }

    private void publishRuntime(ReminderRuntime.Snapshot snapshot) {
        if (!ownsCurrentRuntime()) return;
        runtimeSnapshot = snapshot;
        sendStatus();
    }

    private boolean ownsCurrentRuntime() {
        return serviceGeneration == runtimeGeneration;
    }

    private String formatRadius(int meters) { return meters < 1000 ? meters + " 米" : (meters % 1000 == 0 ? String.valueOf(meters / 1000) : String.format(Locale.CHINA, "%.1f", meters / 1000f)) + " 公里"; }
    private String formatDistance(double meters) { return meters < 1000 ? Math.round(meters / 10) * 10 + " 米" : String.format(Locale.CHINA, meters >= 10_000 ? "%.0f 公里" : "%.1f 公里", meters / 1000); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        cancelStationLookup();
        stationLookupExecutor.shutdownNow();
        handler.removeCallbacks(usageCheck);
        resetLocationRecovery();
        stopReminderHeartbeat();
        removeLocationUpdates();
        stopAlarmSound();
        if (ownsCurrentRuntime()) {
            runtimeSnapshot = ReminderRuntime.Snapshot.stopped();
            sendStatus();
        }
        super.onDestroy();
    }

    public static void markStartRequested() {
        runtimeGeneration++;
        runtimeSnapshot = ReminderRuntime.Snapshot.starting(SystemClock.elapsedRealtime());
    }

    public static void markStartFailed() {
        runtimeGeneration++;
        runtimeSnapshot = ReminderRuntime.Snapshot.failed(
                ReminderRuntime.Failure.FOREGROUND_START, SystemClock.elapsedRealtime());
    }

    public static ReminderRuntime.Snapshot getRuntimeSnapshot() { return runtimeSnapshot; }
}
