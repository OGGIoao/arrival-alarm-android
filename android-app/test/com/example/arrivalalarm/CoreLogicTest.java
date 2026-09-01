package com.example.arrivalalarm;

import java.util.Calendar;
import java.util.TimeZone;

public final class CoreLogicTest {
    private static int passed;

    public static void main(String[] args) {
        testDistance();
        testRadius();
        testArrivalTriggersOnFirstReliableFix();
        testOutsideRadiusDoesNotTrigger();
        testLowAccuracyRejected();
        testJourneyBySpeed();
        testWalkingDoesNotPrompt();
        testJourneyRearms();
        testPlainCoordinates();
        testReversedPlainCoordinates();
        testAmapDestinationLink();
        testAmapMobileLink();
        testBaiduMarkerLink();
        testEncodedMapLink();
        testInvalidCoordinatesRejected();
        testTransitSignal();
        testUnrelatedNotificationRejected();
        testUnsupportedPackageRejected();
        testStationSignal();
        testBusStopRejected();
        testFixedAlarmLaterToday();
        testFixedAlarmSkipsWeekend();
        testEverydayAlarmAdvances();
        testReminderStateMachine();
        testReminderProviderRegistry();
        testAmapCoordinateConversion();
        testRejectsBrokenSystemPlace();
        testAcceptsRelevantPlaceSuggestion();
        testRecoversTruncatedSystemPlaceName();
        testRepeatedSuggestionStateRendersOnce();
        testSuggestionGateResets();
        testFastSearchDebounce();
        System.out.println("CoreLogicTest: " + passed + " tests passed");
    }

    private static void testDistance() {
        double distance = CoreLogic.distanceMeters(0, 0, 1, 0);
        check(distance > 111_000 && distance < 112_000, "distance");
    }

    private static void testRadius() {
        check(CoreLogic.clampRadius(100) == 300, "min radius");
        check(CoreLogic.clampRadius(9000) == 5000, "max radius");
    }

    private static void testArrivalTriggersOnFirstReliableFix() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 1000);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31.001, 121, 20, 8, 1_000));
        check(result.triggered && "inside-radius".equals(result.reason), "arrival on first reliable fix");
    }

    private static void testOutsideRadiusDoesNotTrigger() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 300);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31.01, 121, 20, 8, 1_000));
        check(!result.triggered && "outside".equals(result.reason), "outside radius");
    }

    private static void testLowAccuracyRejected() {
        CoreLogic.ArrivalTracker tracker = new CoreLogic.ArrivalTracker(31, 121, 300);
        CoreLogic.ArrivalResult result = tracker.update(new CoreLogic.LocationFix(31, 121, 180, 0, 1_000));
        check(!result.triggered && "low-accuracy".equals(result.reason), "accuracy");
    }

    private static void testJourneyBySpeed() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.update(new CoreLogic.LocationFix(31, 121.006, 30, 8, 1_000));
        boolean prompted = detector.update(new CoreLogic.LocationFix(31, 121.010, 30, 8, 51_000));
        check(prompted && CoreLogic.JourneyDetector.PROMOTED.equals(detector.state), "journey speed");
    }

    private static void testWalkingDoesNotPrompt() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.update(new CoreLogic.LocationFix(31, 121.006, 20, 1.2f, 1_000));
        boolean prompted = detector.update(new CoreLogic.LocationFix(31, 121.0065, 20, 1.2f, 51_000));
        check(!prompted, "walking");
    }

    private static void testJourneyRearms() {
        CoreLogic.JourneyDetector detector = new CoreLogic.JourneyDetector(31, 121);
        detector.state = CoreLogic.JourneyDetector.DISMISSED;
        detector.update(new CoreLogic.LocationFix(31.02, 121, 20, 0, 10_000));
        detector.update(new CoreLogic.LocationFix(31.02, 121, 20, 0, 10_000 + CoreLogic.RESET_STILL_MS));
        check(CoreLogic.JourneyDetector.ANCHORED.equals(detector.state), "rearm");
    }

    private static void testPlainCoordinates() {
        PlaceInputParser.Result result = PlaceInputParser.parse("上海站 31.2495,121.4557");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && "上海站".equals(result.name), "plain coordinates");
    }

    private static void testReversedPlainCoordinates() {
        PlaceInputParser.Result result = PlaceInputParser.parse("上海站 121.4557,31.2495");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && close(result.longitude, 121.4557), "reversed plain coordinates");
    }

    private static void testAmapDestinationLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://uri.amap.com/navigation?to=121.4557,31.2495,上海站");
        check(result != null && result.hasCoordinates() && close(result.longitude, 121.4557) && "上海站".equals(result.name), "amap destination");
    }

    private static void testAmapMobileLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("androidamap://viewMap?poiname=上海站&lat=31.2495&lon=121.4557&dev=1");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "amap mobile");
    }

    private static void testBaiduMarkerLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("baidumap://map/marker?location=31.2495,121.4557&title=上海站");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && "上海站".equals(result.name), "baidu marker");
    }

    private static void testEncodedMapLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("https://example.test/?lat=31.2495%26lng=121.4557%26name=%E4%B8%8A%E6%B5%B7%E7%AB%99");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name), "encoded map link");
    }

    private static void testInvalidCoordinatesRejected() {
        PlaceInputParser.Result result = PlaceInputParser.parse("未知位置 191.2,95.1");
        check(result != null && !result.hasCoordinates(), "invalid coordinates");
    }

    private static void testTransitSignal() {
        check(TransitSignalMatcher.matches("com.MobileTicket", "铁路12306", "G1次列车即将检票进站"), "transit notification");
    }

    private static void testUnrelatedNotificationRejected() {
        check(!TransitSignalMatcher.matches("com.eg.android.AlipayGphone", "到账提醒", "收到转账 20 元"), "unrelated notification");
    }

    private static void testUnsupportedPackageRejected() {
        check(!TransitSignalMatcher.matches("com.example.news", "地铁新闻", "列车正在进站"), "unsupported notification package");
    }

    private static void testStationSignal() {
        check("上海虹桥站".equals(StationSignalMatcher.findStation("中国上海市闵行区上海虹桥站")), "rail station signal");
        check("人民广场地铁站".equals(StationSignalMatcher.findStation("人民广场地铁站 1号口")), "metro station signal");
    }

    private static void testBusStopRejected() {
        check(StationSignalMatcher.findStation("人民路公交站") == null, "bus stop rejected");
    }

    private static void testFixedAlarmLaterToday() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar now = date(zone, 2026, Calendar.AUGUST, 24, 7, 30);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.WORKDAYS_MASK, now.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_MONTH) == 24 && result.get(Calendar.HOUR_OF_DAY) == 8, "fixed alarm later today");
    }

    private static void testFixedAlarmSkipsWeekend() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar friday = date(zone, 2026, Calendar.AUGUST, 28, 19, 0);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.WORKDAYS_MASK, friday.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY && result.get(Calendar.DAY_OF_MONTH) == 31, "fixed alarm skips weekend");
    }

    private static void testEverydayAlarmAdvances() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar now = date(zone, 2026, Calendar.AUGUST, 24, 8, 0);
        long next = ScheduleLogic.nextTriggerMillis(8, 0, ScheduleLogic.EVERY_DAY_MASK, now.getTimeInMillis(), zone);
        Calendar result = Calendar.getInstance(zone);
        result.setTimeInMillis(next);
        check(result.get(Calendar.DAY_OF_MONTH) == 25 && result.get(Calendar.HOUR_OF_DAY) == 8, "everyday advances");
    }

    private static void testReminderStateMachine() {
        AppPrefs.Destination destination = new AppPrefs.Destination("task-3", "杭州东站", "", "", 30.3065, 120.2048);
        AppPrefs.ReminderTask task = new AppPrefs.ReminderTask("task-3", destination, 1000,
                AppPrefs.ReminderTask.STATUS_ACTIVE, 1_000L);
        ReminderRuntime.Snapshot starting = ReminderRuntime.Snapshot.starting(120_000L);
        ReminderRuntime.Snapshot ready = ReminderRuntime.Snapshot.locationReady(1, 120_000L, 120_000L, 0L);
        ReminderRuntime.Snapshot located = ready.locationReceived(120_001L);
        ReminderRuntime.Snapshot stale = located.heartbeat(250_000L - ReminderRuntime.HEARTBEAT_TIMEOUT_MS - 1L);
        check(ReminderStateLogic.classify(null, ReminderRuntime.Snapshot.stopped(), 120_000L) == ReminderStateLogic.State.EMPTY,
                "empty reminder state");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.stopped(), 120_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "active task without service needs immediate resume");
        check(ReminderStateLogic.classify(task, starting, 120_001L) == ReminderStateLogic.State.STARTING,
                "start request is not monitoring");
        check(ReminderStateLogic.classify(task, ready, 120_001L) == ReminderStateLogic.State.WAITING_LOCATION,
                "registered listener waits for first location");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.locationReady(0, 120_000L, 120_000L, 0L), 120_001L)
                        == ReminderStateLogic.State.NEEDS_RESUME,
                "no registered provider needs resume");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.foreground(120_000L, 120_000L),
                        120_001L) == ReminderStateLogic.State.WAITING_LOCATION,
                "foreground startup waits briefly");
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.foreground(120_000L, 120_000L),
                        120_000L + ReminderRuntime.START_TIMEOUT_MS + 1L) == ReminderStateLogic.State.NEEDS_RESUME,
                "stalled foreground startup needs resume");
        check(ReminderStateLogic.classify(task, located, 120_001L) == ReminderStateLogic.State.MONITORING,
                "location-ready service is monitoring");
        check(ReminderStateLogic.classify(task, stale, 250_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "stale service heartbeat needs resume");
        task.markInterrupted();
        check(ReminderStateLogic.classify(task, located, 250_000L) == ReminderStateLogic.State.NEEDS_RESUME,
                "interrupted task stays resumable");
        task.resume();
        check(task.isActive() && ReminderStateLogic.classify(task,
                        ReminderRuntime.Snapshot.starting(300_000L), 300_001L) == ReminderStateLogic.State.STARTING,
                "resumed task starts before monitoring");
        task.status = AppPrefs.ReminderTask.STATUS_ALERTING;
        check(ReminderStateLogic.classify(task, ReminderRuntime.Snapshot.stopped(), 300_001L) == ReminderStateLogic.State.ALERTING,
                "alerting task remains alerting");
    }

    private static void testAmapCoordinateConversion() {
        double wgsLat = 39.908823;
        double wgsLng = 116.397470;
        double[] gcj = CoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng);
        double[] restored = CoordinateTransform.gcj02ToWgs84(gcj[0], gcj[1]);
        check(Math.abs(restored[0] - wgsLat) < 0.00002 && Math.abs(restored[1] - wgsLng) < 0.00002,
                "amap coordinate conversion");
    }

    private static void testReminderProviderRegistry() {
        ReminderProviderRegistry registry = new ReminderProviderRegistry();
        check(registry.add("gps") && registry.add("network"), "register providers");
        check(!registry.add("gps") && registry.count() == 2, "duplicate provider is idempotent");
        check(!registry.remove("passive") && registry.count() == 2, "unknown provider does not decrement");
        check(registry.remove("gps") && registry.count() == 1, "disable one provider");
        check(registry.remove("network") && registry.count() == 0, "disable last provider");
        registry.clear();
        check(registry.count() == 0, "clear providers");
    }

    private static void testRejectsBrokenSystemPlace() {
        check(!PlaceQuality.isRelevant("成都sm广场", "场", "成都市", "四川省成都市成华区场"),
                "reject broken system place");
    }

    private static void testAcceptsRelevantPlaceSuggestion() {
        check(PlaceQuality.isRelevant("成都sm广场", "成都SM广场", "成都市", "成华区二环路东二段"),
                "accept exact place");
        check(PlaceQuality.isRelevant("虹桥", "上海虹桥站", "上海市", "闵行区申贵路"),
                "accept partial place");
    }

    private static void testRecoversTruncatedSystemPlaceName() {
        check(PlaceQuality.shouldRecoverQueryName("成都sm广场", "场", "成都市", "四川省成都市成华区场"),
                "recover truncated system place name");
        check(!PlaceQuality.shouldRecoverQueryName("成都sm广场", "广场", "成都市", "成华区"),
                "do not recover normal place name");
    }

    private static void testRepeatedSuggestionStateRendersOnce() {
        InteractionGate gate = new InteractionGate();
        int renders = 0;
        boolean visible = false;
        for (int i = 0; i < 10_000; i++) {
            if (gate.shouldRender("message:地图正在智能联想…", visible, true)) {
                renders++;
                visible = true;
            }
        }
        check(renders == 1, "repeated suggestion state renders once");
    }

    private static void testSuggestionGateResets() {
        InteractionGate gate = new InteractionGate();
        check(gate.shouldRender("places:a|b|", false, true), "first suggestion render");
        check(!gate.shouldRender("places:a|b|", true, true), "same suggestion skipped");
        gate.reset();
        check(gate.shouldRender("places:a|b|", false, true), "suggestion render after reset");
    }

    private static void testFastSearchDebounce() {
        check(InteractionGate.searchDebounceMillis(false) == 120, "default search debounce");
        check(InteractionGate.searchDebounceMillis(true) == 80, "amap search debounce");
    }

    private static Calendar date(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar result = Calendar.getInstance(zone);
        result.set(year, month, day, hour, minute, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private static boolean close(double first, double second) { return Math.abs(first - second) < 0.000001; }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}
