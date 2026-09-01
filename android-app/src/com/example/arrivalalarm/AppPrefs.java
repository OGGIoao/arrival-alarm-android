package com.example.arrivalalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AppPrefs {
    private static final String FILE = "arrival_alarm_private";
    private static final String TASK = "task";
    private static final String RECENTS = "recents";
    private static final String FIXED_ALARMS = "fixed_alarms";
    private static final String RADIUS = "radius";
    private static final String JOURNEY_ENABLED = "journey_enabled";
    private static final String JOURNEY_SEEN = "journey_seen";
    private static final String APP_WATCH_ENABLED = "app_watch_enabled";
    private static final String TRANSIT_WATCH_ENABLED = "transit_watch_enabled";
    private static final String STATION_WATCH_ENABLED = "station_watch_enabled";
    private static final String LAST_AUTO_PROMPT_AT = "last_auto_prompt_at";
    private static final long AUTO_PROMPT_COOLDOWN_MS = 3L * 60L * 60L * 1000L;
    private static final String JOURNEY_STATE = "journey_state";
    private static final String ANCHOR_LAT = "anchor_lat";
    private static final String ANCHOR_LNG = "anchor_lng";
    private static final String CONFIRM_LAT = "confirm_lat";
    private static final String CONFIRM_LNG = "confirm_lng";
    private static final String CONFIRM_TIME = "confirm_time";
    private static final String STILL_SINCE = "still_since";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int getRadius() { return CoreLogic.clampRadius(prefs.getInt(RADIUS, CoreLogic.DEFAULT_RADIUS_METERS)); }
    public void setRadius(int radius) { prefs.edit().putInt(RADIUS, CoreLogic.clampRadius(radius)).apply(); }

    public void saveTask(ReminderTask task) {
        prefs.edit().putString(TASK, task.toJson().toString()).apply();
    }

    public ReminderTask getTask() {
        String raw = prefs.getString(TASK, null);
        if (raw == null) return null;
        try { return ReminderTask.fromJson(new JSONObject(raw)); }
        catch (JSONException ignored) { return null; }
    }

    public void clearTask() { prefs.edit().remove(TASK).apply(); }

    public List<Destination> getRecents() {
        List<Destination> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(RECENTS, "[]"));
            for (int i = 0; i < array.length() && result.size() < 10; i++) {
                Destination place = Destination.fromJson(array.getJSONObject(i));
                if (place != null) result.add(place);
            }
        } catch (JSONException ignored) { }
        return result;
    }

    public void addRecent(Destination destination) {
        List<Destination> current = getRecents();
        JSONArray array = new JSONArray();
        array.put(destination.toJson());
        for (Destination item : current) {
            if (!item.id.equals(destination.id) && array.length() < 10) array.put(item.toJson());
        }
        prefs.edit().putString(RECENTS, array.toString()).apply();
    }

    public List<FixedAlarm> getFixedAlarms() {
        List<FixedAlarm> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(FIXED_ALARMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                FixedAlarm alarm = FixedAlarm.fromJson(array.getJSONObject(i));
                if (alarm != null) result.add(alarm);
            }
        } catch (JSONException ignored) { }
        return result;
    }

    public FixedAlarm getFixedAlarm(String id) {
        if (id == null) return null;
        for (FixedAlarm alarm : getFixedAlarms()) if (id.equals(alarm.id)) return alarm;
        return null;
    }

    public void saveFixedAlarm(FixedAlarm alarm) {
        List<FixedAlarm> alarms = getFixedAlarms();
        JSONArray array = new JSONArray();
        boolean replaced = false;
        for (FixedAlarm item : alarms) {
            if (item.id.equals(alarm.id)) {
                array.put(alarm.toJson());
                replaced = true;
            } else array.put(item.toJson());
        }
        if (!replaced) array.put(alarm.toJson());
        prefs.edit().putString(FIXED_ALARMS, array.toString()).apply();
    }

    public void removeFixedAlarm(String id) {
        JSONArray array = new JSONArray();
        for (FixedAlarm alarm : getFixedAlarms()) if (!alarm.id.equals(id)) array.put(alarm.toJson());
        prefs.edit().putString(FIXED_ALARMS, array.toString()).apply();
    }

    public boolean isJourneyEnabled() { return prefs.getBoolean(JOURNEY_ENABLED, false); }
    public void disableDeferredAutomation() {
        prefs.edit()
                .putBoolean(JOURNEY_ENABLED, false)
                .putBoolean(JOURNEY_SEEN, true)
                .putBoolean(APP_WATCH_ENABLED, false)
                .putBoolean(TRANSIT_WATCH_ENABLED, false)
                .putBoolean(STATION_WATCH_ENABLED, false)
                .putString(JOURNEY_STATE, CoreLogic.JourneyDetector.DISABLED)
                .remove(ANCHOR_LAT)
                .remove(ANCHOR_LNG)
                .remove(CONFIRM_LAT)
                .remove(CONFIRM_LNG)
                .remove(CONFIRM_TIME)
                .remove(STILL_SINCE)
                .apply();
    }
    public void setJourneyEnabled(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit().putBoolean(JOURNEY_ENABLED, enabled);
        if (!enabled) editor.putString(JOURNEY_STATE, CoreLogic.JourneyDetector.DISABLED).remove(ANCHOR_LAT).remove(ANCHOR_LNG);
        editor.apply();
    }
    public boolean hasSeenJourneyPrompt() { return prefs.getBoolean(JOURNEY_SEEN, false); }
    public void setJourneyPromptSeen() { prefs.edit().putBoolean(JOURNEY_SEEN, true).apply(); }
    public boolean isAppWatchEnabled() { return prefs.getBoolean(APP_WATCH_ENABLED, false); }
    public void setAppWatchEnabled(boolean enabled) { prefs.edit().putBoolean(APP_WATCH_ENABLED, enabled).apply(); }
    public boolean isTransitWatchEnabled() { return prefs.getBoolean(TRANSIT_WATCH_ENABLED, false); }
    public void setTransitWatchEnabled(boolean enabled) { prefs.edit().putBoolean(TRANSIT_WATCH_ENABLED, enabled).apply(); }
    public boolean isStationWatchEnabled() { return prefs.getBoolean(STATION_WATCH_ENABLED, false); }
    public void setStationWatchEnabled(boolean enabled) { prefs.edit().putBoolean(STATION_WATCH_ENABLED, enabled).apply(); }
    public boolean canShowAutoPrompt(long now) { return now - prefs.getLong(LAST_AUTO_PROMPT_AT, 0L) >= AUTO_PROMPT_COOLDOWN_MS; }
    public void markAutoPrompt(long now) { prefs.edit().putLong(LAST_AUTO_PROMPT_AT, now).apply(); }
    public String getJourneyState() { return prefs.getString(JOURNEY_STATE, isJourneyEnabled() ? CoreLogic.JourneyDetector.ANCHORED : CoreLogic.JourneyDetector.DISABLED); }
    public void setJourneyState(String state) { prefs.edit().putString(JOURNEY_STATE, state).apply(); }
    public boolean hasAnchor() { return prefs.contains(ANCHOR_LAT) && prefs.contains(ANCHOR_LNG); }
    public double getAnchorLat() { return Double.longBitsToDouble(prefs.getLong(ANCHOR_LAT, 0L)); }
    public double getAnchorLng() { return Double.longBitsToDouble(prefs.getLong(ANCHOR_LNG, 0L)); }
    public void setAnchor(double lat, double lng) {
        prefs.edit().putLong(ANCHOR_LAT, Double.doubleToRawLongBits(lat)).putLong(ANCHOR_LNG, Double.doubleToRawLongBits(lng)).apply();
    }
    public void setJourneyRuntime(CoreLogic.JourneyDetector detector) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(JOURNEY_STATE, detector.state)
                .putLong(ANCHOR_LAT, Double.doubleToRawLongBits(detector.anchorLat))
                .putLong(ANCHOR_LNG, Double.doubleToRawLongBits(detector.anchorLng))
                .putLong(STILL_SINCE, detector.stillSince);
        if (detector.confirmStart != null) {
            editor.putLong(CONFIRM_LAT, Double.doubleToRawLongBits(detector.confirmStart.latitude))
                    .putLong(CONFIRM_LNG, Double.doubleToRawLongBits(detector.confirmStart.longitude))
                    .putLong(CONFIRM_TIME, detector.confirmStart.timestamp);
        } else {
            editor.remove(CONFIRM_LAT).remove(CONFIRM_LNG).remove(CONFIRM_TIME);
        }
        editor.apply();
    }
    public CoreLogic.JourneyDetector getJourneyDetector() {
        if (!hasAnchor()) return null;
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(getAnchorLat(), getAnchorLng());
        detector.state = getJourneyState();
        detector.stillSince = prefs.getLong(STILL_SINCE, -1L);
        if (prefs.contains(CONFIRM_LAT) && prefs.contains(CONFIRM_LNG)) {
            detector.confirmStart = new CoreLogic.LocationFix(
                    Double.longBitsToDouble(prefs.getLong(CONFIRM_LAT, 0L)),
                    Double.longBitsToDouble(prefs.getLong(CONFIRM_LNG, 0L)),
                    50f, -1f, prefs.getLong(CONFIRM_TIME, 0L));
        }
        return detector;
    }

    public void clearAll() { prefs.edit().clear().apply(); }

    public static final class Destination {
        public final String id;
        public final String name;
        public final String address;
        public final String city;
        public final double latitude;
        public final double longitude;

        public Destination(String id, String name, String address, String city, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.address = address == null ? "" : address;
            this.city = city == null ? "" : city;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("id", id).put("name", name).put("address", address).put("city", city)
                        .put("latitude", latitude).put("longitude", longitude);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static Destination fromJson(JSONObject json) {
            try {
                return new Destination(json.getString("id"), json.getString("name"), json.optString("address"), json.optString("city"),
                        json.getDouble("latitude"), json.getDouble("longitude"));
            } catch (JSONException ignored) { return null; }
        }
    }

    public static final class ReminderTask {
        public static final String STATUS_ACTIVE = "active";
        public static final String STATUS_ALERTING = "alerting";
        public static final String STATUS_INTERRUPTED = "interrupted";

        public final String id;
        public final Destination destination;
        public final int radiusMeters;
        public String status;
        public final long startedAt;
        public long triggeredAt;
        public double lastDistanceMeters;

        public ReminderTask(String id, Destination destination, int radiusMeters, String status, long startedAt) {
            this.id = id;
            this.destination = destination;
            this.radiusMeters = CoreLogic.clampRadius(radiusMeters);
            this.status = status;
            this.startedAt = startedAt;
            this.lastDistanceMeters = Double.NaN;
        }

        public boolean isActive() { return STATUS_ACTIVE.equals(status); }
        public boolean isAlerting() { return STATUS_ALERTING.equals(status); }
        public boolean isInterrupted() { return STATUS_INTERRUPTED.equals(status); }
        public void markInterrupted() {
            if (isActive()) status = STATUS_INTERRUPTED;
        }
        public void resume() {
            status = STATUS_ACTIVE;
        }

        JSONObject toJson() {
            try {
                JSONObject json = new JSONObject().put("id", id).put("destination", destination.toJson()).put("radiusMeters", radiusMeters)
                        .put("status", status).put("startedAt", startedAt).put("triggeredAt", triggeredAt);
                if (!Double.isNaN(lastDistanceMeters) && !Double.isInfinite(lastDistanceMeters)) json.put("lastDistanceMeters", lastDistanceMeters);
                return json;
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static ReminderTask fromJson(JSONObject json) throws JSONException {
            Destination destination = Destination.fromJson(json.getJSONObject("destination"));
            if (destination == null) return null;
            ReminderTask task = new ReminderTask(json.getString("id"), destination, json.getInt("radiusMeters"), json.getString("status"), json.getLong("startedAt"));
            task.triggeredAt = json.optLong("triggeredAt", 0L);
            task.lastDistanceMeters = json.optDouble("lastDistanceMeters", Double.NaN);
            return task;
        }
    }

    public static final class FixedAlarm {
        public final String id;
        public final Destination destination;
        public final int hour;
        public final int minute;
        public final int daysMask;
        public final boolean enabled;

        public FixedAlarm(String id, Destination destination, int hour, int minute, int daysMask, boolean enabled) {
            this.id = id;
            this.destination = destination;
            this.hour = Math.max(0, Math.min(23, hour));
            this.minute = Math.max(0, Math.min(59, minute));
            this.daysMask = daysMask;
            this.enabled = enabled;
        }

        JSONObject toJson() {
            try {
                return new JSONObject().put("id", id).put("destination", destination.toJson())
                        .put("hour", hour).put("minute", minute).put("daysMask", daysMask).put("enabled", enabled);
            } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        }

        static FixedAlarm fromJson(JSONObject json) {
            try {
                Destination destination = Destination.fromJson(json.getJSONObject("destination"));
                if (destination == null) return null;
                return new FixedAlarm(json.getString("id"), destination, json.getInt("hour"), json.getInt("minute"),
                        json.getInt("daysMask"), json.optBoolean("enabled", true));
            } catch (JSONException ignored) { return null; }
        }
    }
}
