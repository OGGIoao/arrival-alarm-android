package com.example.arrivalalarm;

/** Pure decision table for the persisted reminder and the current service runtime. */
public final class ReminderStateLogic {
    private ReminderStateLogic() {}

    public enum State {
        EMPTY,
        STARTING,
        MONITORING,
        WAITING_LOCATION,
        NEEDS_RESUME,
        ALERTING
    }

    public static State classify(AppPrefs.ReminderTask task, ReminderRuntime.Snapshot runtime,
                                 long nowElapsedRealtime) {
        if (task == null) return State.EMPTY;
        if (task.isAlerting()) return State.ALERTING;
        if (task.isInterrupted()) return State.NEEDS_RESUME;
        if (!task.isActive()) return State.NEEDS_RESUME;
        if (runtime == null) return State.NEEDS_RESUME;
        if (runtime.state == ReminderRuntime.State.STARTING) {
            return runtime.isStartFresh(nowElapsedRealtime) ? State.STARTING : State.NEEDS_RESUME;
        }
        if (runtime.state == ReminderRuntime.State.FAILED || runtime.state == ReminderRuntime.State.STOPPED) {
            return State.NEEDS_RESUME;
        }
        if (!runtime.isHeartbeatFresh(nowElapsedRealtime)) return State.NEEDS_RESUME;
        if (runtime.state == ReminderRuntime.State.FOREGROUND) {
            return nowElapsedRealtime - runtime.stateSinceElapsedRealtime <= ReminderRuntime.START_TIMEOUT_MS
                    ? State.WAITING_LOCATION : State.NEEDS_RESUME;
        }
        if (runtime.state == ReminderRuntime.State.LOCATION_READY) {
            if (runtime.registeredProviderCount == 0) return State.NEEDS_RESUME;
            return runtime.lastLocationElapsedRealtime > 0L ? State.MONITORING : State.WAITING_LOCATION;
        }
        return State.NEEDS_RESUME;
    }
}
