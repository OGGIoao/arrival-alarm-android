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
        testAmapCoordinateConversion();
        testBaiduCoordinateConversion();
        testOverseasCoordinatesUnchanged();
        testShareCoordinateSystems();
        testRejectsBrokenSystemPlace();
        testAcceptsRelevantPlaceSuggestion();
        testRecoversTruncatedSystemPlaceName();
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
        check(result != null && result.hasCoordinates() && close(result.longitude, 121.4557) && "上海站".equals(result.name)
                && result.coordinateSystem == CoordinateTransform.CoordinateSystem.GCJ02, "amap destination");
        double[] gcj = CoordinateTransform.wgs84ToGcj02(result.wgs84()[0], result.wgs84()[1]);
        check(near(gcj[0], 31.2495) && near(gcj[1], 121.4557) && !near(result.wgs84()[0], 31.2495), "amap destination wgs84");
    }

    private static void testAmapMobileLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("androidamap://viewMap?poiname=上海站&lat=31.2495&lon=121.4557&dev=0");
        check(result != null && result.hasCoordinates() && "上海站".equals(result.name)
                && result.coordinateSystem == CoordinateTransform.CoordinateSystem.GCJ02, "amap mobile");
        PlaceInputParser.Result gps = PlaceInputParser.parse("androidamap://viewMap?poiname=上海站&lat=31.2495&lon=121.4557&dev=1");
        check(gps != null && gps.coordinateSystem == CoordinateTransform.CoordinateSystem.WGS84
                && close(gps.wgs84()[0], 31.2495), "amap mobile dev=1 is wgs84");
    }

    private static void testBaiduMarkerLink() {
        PlaceInputParser.Result result = PlaceInputParser.parse("baidumap://map/marker?location=31.2495,121.4557&title=上海站");
        check(result != null && result.hasCoordinates() && close(result.latitude, 31.2495) && "上海站".equals(result.name)
                && result.coordinateSystem == CoordinateTransform.CoordinateSystem.BD09, "baidu marker");
        double[] wgs = result.wgs84();
        double[] bd = CoordinateTransform.gcj02ToBd09(CoordinateTransform.wgs84ToGcj02(wgs[0], wgs[1])[0],
                CoordinateTransform.wgs84ToGcj02(wgs[0], wgs[1])[1]);
        check(near(bd[0], 31.2495) && near(bd[1], 121.4557) && !near(wgs[1], 121.4557), "baidu marker wgs84");
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

    private static void testAmapCoordinateConversion() {
        double wgsLat = 39.908823;
        double wgsLng = 116.397470;
        double[] gcj = CoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng);
        double[] restored = CoordinateTransform.gcj02ToWgs84(gcj[0], gcj[1]);
        check(Math.abs(restored[0] - wgsLat) < 0.00002 && Math.abs(restored[1] - wgsLng) < 0.00002,
                "amap coordinate conversion");
    }

    private static void testBaiduCoordinateConversion() {
        double wgsLat = 39.908823;
        double wgsLng = 116.397470;
        double[] gcj = CoordinateTransform.wgs84ToGcj02(wgsLat, wgsLng);
        double[] bd = CoordinateTransform.gcj02ToBd09(gcj[0], gcj[1]);
        double[] backToGcj = CoordinateTransform.bd09ToGcj02(bd[0], bd[1]);
        double[] restored = CoordinateTransform.bd09ToWgs84(bd[0], bd[1]);
        check(Math.abs(bd[1] - wgsLng) > 0.005 && near(backToGcj[0], gcj[0]) && near(backToGcj[1], gcj[1]),
                "baidu gcj round trip");
        check(near(restored[0], wgsLat) && near(restored[1], wgsLng), "baidu wgs round trip");
    }

    private static void testOverseasCoordinatesUnchanged() {
        double[] gcj = CoordinateTransform.gcj02ToWgs84(48.858370, 2.294481);
        double[] bd = CoordinateTransform.bd09ToWgs84(48.858370, 2.294481);
        double[] toBd = CoordinateTransform.gcj02ToBd09(48.858370, 2.294481);
        check(close(gcj[0], 48.858370) && close(gcj[1], 2.294481) && close(bd[0], 48.858370) && close(bd[1], 2.294481)
                && close(toBd[0], 48.858370) && close(toBd[1], 2.294481), "overseas coordinates unchanged");
        PlaceInputParser.Result result = PlaceInputParser.parse("baidumap://map/marker?location=48.858370,2.294481&title=Tour");
        check(result != null && close(result.wgs84()[0], 48.858370) && close(result.wgs84()[1], 2.294481), "overseas share unchanged");
    }

    private static void testShareCoordinateSystems() {
        check(PlaceInputParser.parse("上海站 31.2495,121.4557").coordinateSystem == CoordinateTransform.CoordinateSystem.WGS84,
                "manual coordinates are wgs84");
        PlaceInputParser.Result tencent = PlaceInputParser.parse("qqmap://map/marker?marker=coord:31.2495,121.4557;title:上海站");
        check(tencent != null && tencent.hasCoordinates() && tencent.coordinateSystem == CoordinateTransform.CoordinateSystem.GCJ02,
                "tencent is gcj02");
        PlaceInputParser.Result tencentGps = PlaceInputParser.parse("https://map.qq.com/?type=marker&marker=coord:31.2495,121.4557&coord_type=1");
        check(tencentGps != null && tencentGps.coordinateSystem == CoordinateTransform.CoordinateSystem.WGS84, "tencent coord_type=1");
        PlaceInputParser.Result baiduGcj = PlaceInputParser.parse("baidumap://map/marker?location=31.2495,121.4557&coord_type=gcj02&title=上海站");
        check(baiduGcj != null && baiduGcj.coordinateSystem == CoordinateTransform.CoordinateSystem.GCJ02, "baidu coord_type=gcj02");
        PlaceInputParser.Result baiduWgs = PlaceInputParser.parse("https://map.baidu.com/?latlng=31.2495,121.4557&coordtype=wgs84ll");
        check(baiduWgs != null && baiduWgs.coordinateSystem == CoordinateTransform.CoordinateSystem.WGS84, "baidu coordtype=wgs84ll");
        PlaceInputParser.Result amapWgs = PlaceInputParser.parse("https://uri.amap.com/marker?position=121.4557,31.2495&coordinate=wgs84");
        check(amapWgs != null && amapWgs.coordinateSystem == CoordinateTransform.CoordinateSystem.WGS84, "amap coordinate=wgs84");
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

    private static Calendar date(TimeZone zone, int year, int month, int day, int hour, int minute) {
        Calendar result = Calendar.getInstance(zone);
        result.set(year, month, day, hour, minute, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private static boolean close(double first, double second) { return Math.abs(first - second) < 0.000001; }

    /** Within about 2 m; the GCJ-02 inverse used here is a single-step approximation. */
    private static boolean near(double first, double second) { return Math.abs(first - second) < 0.00002; }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
    }
}
