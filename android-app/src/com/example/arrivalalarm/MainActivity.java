package com.example.arrivalalarm;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class MainActivity extends Activity {
    private static final int REQUEST_CORE_PERMISSIONS = 1001;
    private int GREEN;
    private int INK;
    private int MUTED;
    private int PAPER;
    private int SURFACE;
    private int SOFT_BLUE;
    private int SEPARATOR;
    private int DANGER;
    private int SOFT_RED;

    private AppPrefs prefs;
    private EditText destinationInput;
    private LinearLayout activeCard;
    private TextView activeName;
    private TextView activeMeta;
    private LinearLayout recentsList;
    private LinearLayout suggestionsList;
    private TextView journeyStatus;
    private TextView fixedAlarmStatus;
    private AppPrefs.Destination pendingDestination;
    private boolean pendingJourneyEnable;
    private BroadcastReceiver statusReceiver;
    private int suggestionGeneration;
    private Runnable suggestionDebounce;
    private Future<?> suggestionFuture;
    private final ScheduledThreadPoolExecutor placeSearchExecutor = new ScheduledThreadPoolExecutor(2);
    private final Map<String, List<AppPrefs.Destination>> suggestionCache = new LinkedHashMap<String, List<AppPrefs.Destination>>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<AppPrefs.Destination>> eldest) { return size() > 32; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        placeSearchExecutor.setRemoveOnCancelPolicy(true);
        configurePalette();
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        buildUi();
        handleIntent(getIntent());
        if (!prefs.hasSeenJourneyPrompt()) showJourneyOptIn();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        statusReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { refreshUi(); }
        };
        IntentFilter filter = new IntentFilter(TrackingService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, filter);
        refreshUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (statusReceiver != null) unregisterReceiver(statusReceiver);
        statusReceiver = null;
    }

    @Override
    protected void onDestroy() {
        if (suggestionDebounce != null && destinationInput != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        placeSearchExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(PAPER);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(12), dp(20), dp(38));
        applySafeArea(root);
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("别坐过站", 17, Typeface.BOLD, INK);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView settingsButton = text("设置", 16, Typeface.NORMAL, GREEN);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setBackground(roundRect(SOFT_BLUE, 18, Color.TRANSPARENT));
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showSettings();
            }
        });
        applyPressFeedback(settingsButton);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(64), dp(38)));
        root.addView(header, matchWrap());

        TextView eyebrow = text("到站提醒", 12, Typeface.BOLD, GREEN);
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setLetterSpacing(0.04f);
        eyebrow.setBackground(roundRect(SOFT_BLUE, 12, Color.TRANSPARENT));
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(dp(76), dp(28));
        eyebrowParams.topMargin = dp(22);
        root.addView(eyebrow, eyebrowParams);
        TextView title = text("要去哪里？", 34, Typeface.BOLD, INK);
        title.setLetterSpacing(-0.025f);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        root.addView(title, titleParams);
        TextView tagline = text("它不带你去，只负责提醒你别坐过站。", 15, Typeface.NORMAL, MUTED);
        tagline.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams taglineParams = matchWrap();
        taglineParams.topMargin = dp(6);
        taglineParams.bottomMargin = dp(22);
        root.addView(tagline, taglineParams);

        LinearLayout search = row();
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(8), dp(6), dp(7), dp(6));
        search.setBackground(roundRect(SURFACE, 18, SEPARATOR));
        search.setElevation(dp(1));
        destinationInput = new EditText(this);
        destinationInput.setSingleLine(true);
        destinationInput.setTextSize(16);
        destinationInput.setTextColor(INK);
        destinationInput.setHintTextColor(MUTED);
        destinationInput.setHint("输入简称、车站或地址");
        destinationInput.setBackgroundColor(Color.TRANSPARENT);
        destinationInput.setPadding(dp(12), 0, dp(8), 0);
        destinationInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        destinationInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) { searchDestination(); return true; }
                return false;
            }
        });
        destinationInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { updatePlaceSuggestions(value.toString()); }
            @Override public void afterTextChanged(Editable value) { }
        });
        Button start = primaryButton("开始");
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                searchDestination();
            }
        });
        search.addView(destinationInput, new LinearLayout.LayoutParams(0, dp(54), 1));
        search.addView(start, new LinearLayout.LayoutParams(dp(72), dp(44)));
        root.addView(search, matchWrap());

        suggestionsList = column();
        suggestionsList.setVisibility(View.GONE);
        suggestionsList.setPadding(dp(6), dp(6), dp(6), dp(6));
        suggestionsList.setBackground(roundRect(SURFACE, 16, SEPARATOR));
        suggestionsList.setElevation(dp(2));
        LinearLayout.LayoutParams suggestionParams = matchWrap();
        suggestionParams.topMargin = dp(6);
        root.addView(suggestionsList, suggestionParams);

        activeCard = row();
        activeCard.setGravity(Gravity.CENTER_VERTICAL);
        activeCard.setPadding(dp(18), dp(17), dp(12), dp(17));
        activeCard.setBackground(roundRect(SURFACE, 20, SEPARATOR));
        activeCard.setElevation(dp(1));
        LinearLayout activeCopy = column();
        TextView activeLabel = text("●  正在守候", 12, Typeface.BOLD, GREEN);
        activeName = text("—", 19, Typeface.BOLD, INK);
        activeMeta = text("等待定位", 13, Typeface.NORMAL, MUTED);
        activeName.setPadding(0, dp(4), 0, dp(3));
        activeCopy.addView(activeLabel, matchWrap());
        activeCopy.addView(activeName, matchWrap());
        activeCopy.addView(activeMeta, matchWrap());
        Button stop = dangerButton("停止");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                stopReminder();
            }
        });
        activeCard.addView(activeCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        activeCard.addView(stop, new LinearLayout.LayoutParams(dp(66), dp(40)));
        LinearLayout.LayoutParams activeParams = matchWrap();
        activeParams.topMargin = dp(14);
        root.addView(activeCard, activeParams);

        TextView automaticTitle = sectionTitle("自动提醒");
        LinearLayout.LayoutParams automaticTitleParams = matchWrap();
        automaticTitleParams.topMargin = dp(30);
        automaticTitleParams.bottomMargin = dp(10);
        root.addView(automaticTitle, automaticTitleParams);

        LinearLayout automaticCard = row();
        automaticCard.setGravity(Gravity.CENTER_VERTICAL);
        automaticCard.setPadding(dp(14), dp(12), dp(12), dp(12));
        automaticCard.setBackground(roundRect(SURFACE, 18, SEPARATOR));
        automaticCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showSettings();
            }
        });
        applyPressFeedback(automaticCard);
        TextView automaticIcon = text("✦", 18, Typeface.BOLD, GREEN);
        automaticIcon.setGravity(Gravity.CENTER);
        automaticIcon.setBackground(roundRect(SOFT_BLUE, 11, Color.TRANSPARENT));
        LinearLayout automaticCopy = column();
        TextView automaticName = text("出行检测", 16, Typeface.BOLD, INK);
        journeyStatus = text("检查状态中", 13, Typeface.NORMAL, MUTED);
        journeyStatus.setPadding(0, dp(2), 0, 0);
        automaticCopy.addView(automaticName, matchWrap());
        automaticCopy.addView(journeyStatus, matchWrap());
        TextView chevron = text("›", 28, Typeface.NORMAL, MUTED);
        chevron.setGravity(Gravity.CENTER);
        automaticCard.addView(automaticIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams automaticCopyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        automaticCopyParams.leftMargin = dp(12);
        automaticCard.addView(automaticCopy, automaticCopyParams);
        automaticCard.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(40)));
        root.addView(automaticCard, matchWrap());

        TextView fixedTitle = sectionTitle("固定地点闹钟");
        LinearLayout.LayoutParams fixedTitleParams = matchWrap();
        fixedTitleParams.topMargin = dp(30);
        fixedTitleParams.bottomMargin = dp(10);
        root.addView(fixedTitle, fixedTitleParams);

        LinearLayout fixedCard = row();
        fixedCard.setGravity(Gravity.CENTER_VERTICAL);
        fixedCard.setPadding(dp(14), dp(12), dp(12), dp(12));
        fixedCard.setBackground(roundRect(SURFACE, 18, SEPARATOR));
        fixedCard.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showFixedAlarms();
        });
        applyPressFeedback(fixedCard);
        TextView fixedIcon = text("时", 16, Typeface.BOLD, GREEN);
        fixedIcon.setGravity(Gravity.CENTER);
        fixedIcon.setBackground(roundRect(SOFT_BLUE, 11, Color.TRANSPARENT));
        LinearLayout fixedCopy = column();
        TextView fixedName = text("通勤预设", 16, Typeface.BOLD, INK);
        fixedAlarmStatus = text("未设置 · 点按添加", 13, Typeface.NORMAL, MUTED);
        fixedAlarmStatus.setPadding(0, dp(2), 0, 0);
        fixedCopy.addView(fixedName, matchWrap());
        fixedCopy.addView(fixedAlarmStatus, matchWrap());
        TextView fixedChevron = text("›", 28, Typeface.NORMAL, MUTED);
        fixedChevron.setGravity(Gravity.CENTER);
        fixedCard.addView(fixedIcon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams fixedCopyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        fixedCopyParams.leftMargin = dp(12);
        fixedCard.addView(fixedCopy, fixedCopyParams);
        fixedCard.addView(fixedChevron, new LinearLayout.LayoutParams(dp(24), dp(40)));
        root.addView(fixedCard, matchWrap());

        TextView recentTitle = sectionTitle("最近地点");
        LinearLayout.LayoutParams recentTitleParams = matchWrap();
        recentTitleParams.topMargin = dp(30);
        recentTitleParams.bottomMargin = dp(10);
        root.addView(recentTitle, recentTitleParams);
        HorizontalScrollView recentsScroll = new HorizontalScrollView(this);
        recentsScroll.setHorizontalScrollBarEnabled(false);
        recentsScroll.setClipToPadding(false);
        recentsList = row();
        recentsScroll.addView(recentsList, wrapWrap());
        root.addView(recentsScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        TextView privacy = text("隐私与能力边界  ·  设置与记录仅存本机", 12, Typeface.NORMAL, MUTED);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(30), 0, dp(12));
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showPrivacy();
            }
        });
        applyPressFeedback(privacy);
        root.addView(privacy, matchWrap());

        setContentView(scroll);
        refreshUi();
        animateEntrance(root);
    }

    private void searchDestination() {
        final String query = destinationInput.getText().toString().trim();
        if (query.isEmpty()) { destinationInput.requestFocus(); return; }
        suggestionGeneration++;
        if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        hideKeyboard();
        final ShareParser.ParsedShare parsed = ShareParser.parseText(query);
        if (parsed != null && parsed.hasCoordinates()) {
            confirmCoordinateDestination(parsed);
            return;
        }
        if (!hasPlaceSearchProvider()) {
            toast("设备没有可用的地点搜索服务");
            return;
        }
        renderSuggestionMessage(!developerAmapKey().isEmpty() ? "高德正在确认地点…" : "地图正在确认地点…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppPrefs.Destination> results = queryPlaces(query, 7);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!query.equals(destinationInput.getText().toString().trim())) return;
                        if (results.isEmpty()) {
                            renderSuggestionMessage("没有找到可靠地点，请换个关键词");
                            toast("没有找到可靠地点");
                            return;
                        }
                        showDestinationChoices(results, "请选择同名地点", place -> chooseSuggestedDestination(place));
                    }
                });
            }
        }, "private-place-search").start();
    }

    private interface DestinationChoice { void select(AppPrefs.Destination destination); }

    private void showDestinationChoices(final List<AppPrefs.Destination> places, String title, final DestinationChoice choice) {
        if (places.isEmpty()) { toast("搜索结果缺少有效坐标，请换个关键词"); return; }
        if (places.size() == 1) { choice.select(places.get(0)); return; }
        showStyledDialog(new AlertDialog.Builder(this).setTitle(title).setItems(destinationLabels(places),
                (dialog, which) -> choice.select(places.get(which))));
    }

    private boolean hasPlaceSearchProvider() {
        return true;
    }

    private List<AppPrefs.Destination> queryPlaces(String query, int maxResults) {
        List<AppPrefs.Destination> results = new ArrayList<>();
        String amapKey = developerAmapKey();
        if (!amapKey.isEmpty()) {
            try { results.addAll(AmapPlaceSearch.search(amapKey, query, getLastKnownLocation(), maxResults)); }
            catch (IOException ignored) { }
        }
        if (results.isEmpty()) {
            try { results.addAll(PhotonPlaceSearch.search(query, getLastKnownLocation(), maxResults)); }
            catch (IOException ignored) { }
        }
        if (results.isEmpty() && Geocoder.isPresent()) {
            try { results.addAll(destinationsFromAddresses(query, findAddresses(query, maxResults))); }
            catch (IOException ignored) { }
        }
        return results;
    }

    private String developerAmapKey() {
        String key = DeveloperConfig.AMAP_WEB_KEY == null ? "" : DeveloperConfig.AMAP_WEB_KEY.trim();
        return key.startsWith("__") ? "" : key;
    }

    private List<Address> findAddresses(String query, int maxResults) throws IOException {
        Geocoder geocoder = new Geocoder(this, Locale.SIMPLIFIED_CHINESE);
        List<Address> found = new ArrayList<>();
        Location nearby = getLastKnownLocation();
        if (nearby != null) {
            List<Address> local = geocoder.getFromLocationName(query, maxResults,
                    Math.max(-90, nearby.getLatitude() - 1.2), Math.max(-180, nearby.getLongitude() - 1.5),
                    Math.min(90, nearby.getLatitude() + 1.2), Math.min(180, nearby.getLongitude() + 1.5));
            if (local != null) found.addAll(local);
        }
        if (found.isEmpty()) {
            List<Address> global = geocoder.getFromLocationName(query, maxResults);
            if (global != null) found.addAll(global);
        }
        return found;
    }

    private Location getLastKnownLocation() {
        if (!hasLocationPermission()) return null;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) return null;
        try {
            Location gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) return network;
            if (network == null) return gps;
            return gps.getTime() >= network.getTime() ? gps : network;
        } catch (SecurityException ignored) { return null; }
    }

    private List<AppPrefs.Destination> destinationsFromAddresses(String query, List<Address> addresses) {
        List<AppPrefs.Destination> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Address address : addresses) {
            if (address == null || !address.hasLatitude() || !address.hasLongitude()) continue;
            double latitude = address.getLatitude();
            double longitude = address.getLongitude();
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) continue;
            String name = address.getFeatureName();
            if (name == null || name.trim().isEmpty()) name = query;
            String line = address.getMaxAddressLineIndex() >= 0 ? address.getAddressLine(0) : "";
            String city = address.getLocality();
            if (city == null || city.isEmpty()) city = address.getAdminArea() == null ? "" : address.getAdminArea();
            boolean recoveredName = PlaceQuality.shouldRecoverQueryName(query, name, city, line);
            if (recoveredName) name = query;
            if (!PlaceQuality.isRelevant(query, name, city, line)) continue;
            String id = (recoveredName ? "inferred-" : "") + idFor(name, latitude, longitude);
            if (!seen.add(id)) continue;
            places.add(new AppPrefs.Destination(id, name, line, city, latitude, longitude));
        }
        Collections.sort(places, new Comparator<AppPrefs.Destination>() {
            @Override public int compare(AppPrefs.Destination first, AppPrefs.Destination second) {
                return Integer.compare(PlaceQuality.score(query, second.name, second.city, second.address),
                        PlaceQuality.score(query, first.name, first.city, first.address));
            }
        });
        return places;
    }

    private String[] destinationLabels(List<AppPrefs.Destination> places) {
        List<String> labels = new ArrayList<>();
        for (AppPrefs.Destination place : places) {
            labels.add(place.name + (place.city.isEmpty() ? "" : " · " + place.city)
                    + (place.address.isEmpty() ? "" : "\n" + place.address));
        }
        return labels.toArray(new String[0]);
    }

    private void updatePlaceSuggestions(String raw) {
        final String query = raw.trim();
        final int generation = ++suggestionGeneration;
        if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        if (suggestionsList == null || query.isEmpty()) {
            if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
            return;
        }
        List<AppPrefs.Destination> local = localPlaceSuggestions(query);
        String cacheKey = query.toLowerCase(Locale.CHINA);
        List<AppPrefs.Destination> cached = suggestionCache.get(cacheKey);
        if (cached != null) local = mergePlaces(local, cached, 6);
        renderPlaceSuggestions(local);
        boolean amapEnabled = !developerAmapKey().isEmpty();
        if (cached != null) return;
        if (query.length() < 2 && !amapEnabled) {
            if (local.isEmpty()) renderSuggestionMessage("再输入一个字，自动推荐相关地点");
            return;
        }
        if (!hasPlaceSearchProvider()) {
            if (local.isEmpty()) renderSuggestionMessage("设备地点服务不可用，可输入更完整的地址");
            return;
        }
        if (local.isEmpty()) renderSuggestionMessage(amapEnabled ? "高德正在智能联想…" : "地图正在智能联想…");
        suggestionDebounce = new Runnable() {
            @Override public void run() {
                if (generation != suggestionGeneration) return;
                suggestionFuture = placeSearchExecutor.submit(new Runnable() {
                    @Override public void run() {
                        final List<AppPrefs.Destination> remote = queryPlaces(query, 4);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (generation != suggestionGeneration || !query.equals(destinationInput.getText().toString().trim())) return;
                                suggestionCache.put(cacheKey, new ArrayList<>(remote));
                                List<AppPrefs.Destination> merged = mergePlaces(localPlaceSuggestions(query), remote, 4);
                                if (merged.isEmpty()) renderSuggestionMessage("暂未找到，试试“城市 + 地点名”");
                                else renderPlaceSuggestions(merged);
                            }
                        });
                    }
                });
            }
        };
        destinationInput.postDelayed(suggestionDebounce, amapEnabled ? 100 : 180);
    }

    private List<AppPrefs.Destination> mergePlaces(List<AppPrefs.Destination> first,
                                                    List<AppPrefs.Destination> second, int maxResults) {
        List<AppPrefs.Destination> merged = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (AppPrefs.Destination item : first) if (ids.add(item.id) && merged.size() < maxResults) merged.add(item);
        for (AppPrefs.Destination item : second) if (ids.add(item.id) && merged.size() < maxResults) merged.add(item);
        return merged;
    }

    private List<AppPrefs.Destination> localPlaceSuggestions(String query) {
        List<AppPrefs.Destination> results = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String needle = query.toLowerCase(Locale.CHINA);
        List<AppPrefs.Destination> candidates = new ArrayList<>(prefs.getRecents());
        for (AppPrefs.FixedAlarm alarm : prefs.getFixedAlarms()) candidates.add(alarm.destination);
        for (AppPrefs.Destination item : candidates) {
            String text = (item.name + " " + item.city + " " + item.address).toLowerCase(Locale.CHINA);
            if (text.contains(needle) && ids.add(item.id)) results.add(item);
            if (results.size() >= 4) break;
        }
        return results;
    }

    private void renderPlaceSuggestions(List<AppPrefs.Destination> places) {
        if (suggestionsList == null) return;
        suggestionsList.removeAllViews();
        suggestionsList.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        if (places.isEmpty()) return;
        boolean amap = places.get(0).id.startsWith("amap-");
        boolean photon = places.get(0).id.startsWith("photon-");
        boolean inferred = places.get(0).id.startsWith("inferred-");
        TextView header = text(amap ? "高德智能推荐" : photon ? "OpenStreetMap 地点推荐"
                : inferred ? "系统推测 · 请核对地址" : "相关地点", 12, Typeface.BOLD, MUTED);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(12), 0);
        suggestionsList.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        for (int i = 0; i < places.size(); i++) {
            final AppPrefs.Destination place = places.get(i);
            LinearLayout item = row();
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(5), dp(8), dp(5));
            TextView marker = text("●", 13, Typeface.BOLD, GREEN);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(roundRect(SOFT_BLUE, 12, Color.TRANSPARENT));
            LinearLayout copy = column();
            TextView name = text(place.name, 15, Typeface.BOLD, INK);
            name.setMaxLines(1);
            String details = place.city + (!place.city.isEmpty() && !place.address.isEmpty() ? " · " : "") + place.address;
            TextView detail = text(details.isEmpty() ? "地点坐标已确认" : details, 12, Typeface.NORMAL, MUTED);
            detail.setMaxLines(1);
            detail.setPadding(0, dp(2), 0, 0);
            copy.addView(name, matchWrap());
            copy.addView(detail, matchWrap());
            TextView select = text("选用", 13, Typeface.BOLD, GREEN);
            select.setGravity(Gravity.CENTER);
            item.addView(marker, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            copyParams.leftMargin = dp(10);
            item.addView(copy, copyParams);
            item.addView(select, new LinearLayout.LayoutParams(dp(48), dp(44)));
            item.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                suggestionGeneration++;
                destinationInput.setText("");
                suggestionsList.setVisibility(View.GONE);
                chooseSuggestedDestination(place);
            });
            applyPressFeedback(item);
            suggestionsList.addView(item, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));
            if (i < places.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(SEPARATOR);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(56);
                suggestionsList.addView(divider, dividerParams);
            }
        }
    }

    private void renderSuggestionMessage(String message) {
        if (suggestionsList == null) return;
        suggestionsList.removeAllViews();
        suggestionsList.setVisibility(View.VISIBLE);
        TextView status = text(message, 13, Typeface.NORMAL, MUTED);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), 0, dp(12), 0);
        suggestionsList.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
    }

    private void chooseSuggestedDestination(final AppPrefs.Destination place) {
        if (!place.id.startsWith("inferred-")) { beginDestination(place); return; }
        String message = place.name + "\n" + (place.city.isEmpty() ? "" : place.city + "\n")
                + (place.address.isEmpty() ? "系统只返回了坐标，请谨慎核对。" : place.address)
                + "\n\n名称由输入关键词恢复，坐标来自手机系统地点服务。";
        showStyledDialog(new AlertDialog.Builder(this).setTitle("核对系统推测地点")
                .setMessage(message).setNegativeButton("取消", null)
                .setPositiveButton("确认使用", (dialog, which) -> beginDestination(place)));
    }

    private void beginDestination(final AppPrefs.Destination destination) {
        if (prefs.getTask() != null) {
            showStyledDialog(new AlertDialog.Builder(this).setTitle("替换当前提醒？").setMessage("开始新目的地会停止正在进行的提醒。")
                    .setNegativeButton("取消", null).setPositiveButton("替换", (dialog, which) -> requestStart(destination)));
        } else requestStart(destination);
    }

    private void requestStart(AppPrefs.Destination destination) {
        pendingDestination = destination;
        if (!hasCorePermissions()) { requestCorePermissions(); return; }
        startPendingDestination();
    }

    private void startPendingDestination() {
        if (pendingDestination == null) return;
        long now = System.currentTimeMillis();
        AppPrefs.ReminderTask task = new AppPrefs.ReminderTask("task-" + now, pendingDestination, prefs.getRadius(), "active", now);
        prefs.saveTask(task);
        prefs.addRecent(pendingDestination);
        Intent service = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START_REMINDER);
        startForegroundService(service);
        destinationInput.setText("");
        pendingDestination = null;
        refreshUi();
        toast("已开始守候 " + task.destination.name);
        maybeRequestFullScreenIntentAccess();
    }

    private void stopReminder() {
        Intent service = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_STOP_REMINDER);
        startService(service);
        prefs.clearTask();
        refreshUi();
        toast("已停止提醒");
    }

    private void requestCorePermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(permissions.toArray(new String[0]), REQUEST_CORE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CORE_PERMISSIONS) return;
        if (!hasLocationPermission()) {
            pendingDestination = null;
            pendingJourneyEnable = false;
            toast("必须允许精确定位才能提醒到站");
            return;
        }
        if (!hasNotificationPermission()) {
            pendingDestination = null;
            pendingJourneyEnable = false;
            toast("必须允许通知才能弹出到站闹钟");
            return;
        }
        if (pendingJourneyEnable) { pendingJourneyEnable = false; enableJourneyService(); }
        else startPendingDestination();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCorePermissions() { return hasLocationPermission() && hasNotificationPermission(); }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String fixedAlarmId = intent.getStringExtra(FixedAlarmReceiver.EXTRA_FIXED_ALARM_ID);
        if (fixedAlarmId != null) {
            intent.removeExtra(FixedAlarmReceiver.EXTRA_FIXED_ALARM_ID);
            AppPrefs.FixedAlarm alarm = prefs.getFixedAlarm(fixedAlarmId);
            if (alarm != null) {
                beginDestination(alarm.destination);
                return;
            }
        }
        if (intent.getBooleanExtra(TrackingService.EXTRA_FOCUS_DESTINATION, false)) {
            CoreLogic.JourneyDetector detector = prefs.getJourneyDetector();
            if (detector != null && CoreLogic.JourneyDetector.PROMOTED.equals(detector.state)) {
                detector.state = CoreLogic.JourneyDetector.WAITING;
                prefs.setJourneyRuntime(detector);
            }
            destinationInput.requestFocus();
            destinationInput.postDelayed(new Runnable() {
                @Override public void run() { showKeyboard(); }
            }, 250);
        }
        ShareParser.ParsedShare shared = ShareParser.fromIntent(intent);
        if (shared == null) {
            if (ShareParser.isTravelWebsite(intent)) {
                destinationInput.requestFocus();
                destinationInput.postDelayed(new Runnable() {
                    @Override public void run() { showKeyboard(); }
                }, 250);
                toast("检测到出行网站，请输入或分享目的地");
                return;
            }
            if (Intent.ACTION_SEND.equals(intent.getAction())) toast("未能识别分享链接，请直接输入地点名称");
            return;
        }
        if (shared.hasCoordinates()) {
            confirmCoordinateDestination(shared);
        } else if (!shared.name.isEmpty()) {
            destinationInput.setText(shared.name);
            destinationInput.setSelection(shared.name.length());
            searchDestination();
        }
    }

    private void confirmCoordinateDestination(final ShareParser.ParsedShare parsed) {
        final String name = parsed.name.isEmpty() ? "分享的目的地" : parsed.name;
        final double latitude = parsed.latitude;
        final double longitude = parsed.longitude;
        destinationInput.setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                String foundAddress = "";
                if (Geocoder.isPresent()) {
                    try {
                        List<Address> found = new Geocoder(MainActivity.this, Locale.SIMPLIFIED_CHINESE)
                                .getFromLocation(latitude, longitude, 1);
                        if (found != null && !found.isEmpty() && found.get(0).getMaxAddressLineIndex() >= 0) {
                            foundAddress = found.get(0).getAddressLine(0);
                        }
                    } catch (IOException | IllegalArgumentException ignored) { }
                }
                final String address = foundAddress;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        destinationInput.setEnabled(true);
                        String coordinate = String.format(Locale.CHINA, "%.6f, %.6f", latitude, longitude);
                        String verification = address.isEmpty() ? "系统无法反查地址，请确认地点名称和坐标。" : "系统反查：" + address;
                        String message = name + "\n" + verification + "\n来源：" + parsed.source + "\n坐标：" + coordinate;
                        showStyledDialog(new AlertDialog.Builder(MainActivity.this).setTitle("核对目的地")
                                .setMessage(message)
                                .setNegativeButton("取消", null)
                                .setPositiveButton("确认并开始", (dialog, which) -> {
                                    String savedAddress = address.isEmpty() ? parsed.source : address;
                                    beginDestination(new AppPrefs.Destination(idFor(name, latitude, longitude), name, savedAddress, "", latitude, longitude));
                                }));
                    }
                });
            }
        }, "private-coordinate-check").start();
    }

    private void showJourneyOptIn() {
        prefs.setJourneyPromptSeen();
        showStyledDialog(new AlertDialog.Builder(this).setTitle("忘记设置时主动提醒？")
                .setMessage("开启后，应用会用低频定位判断你是否开始乘车，并通过手机系统反查是否靠近地铁站或火车站。地图/打车 App 和进站通知识别可在设置中单独授权，所有判断只在本机完成。")
                .setNegativeButton("以后再说", null)
                .setPositiveButton("开启", (dialog, which) -> {
                    prefs.setStationWatchEnabled(true);
                    requestEnableJourney();
                }));
    }

    private void requestEnableJourney() {
        if (!hasCorePermissions()) {
            pendingJourneyEnable = true;
            requestCorePermissions();
        } else enableJourneyService();
    }

    private void enableJourneyService() {
        prefs.setJourneyEnabled(true);
        Intent service = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_ENABLE_JOURNEY);
        startForegroundService(service);
        refreshUi();
        toast("已开启乘车提示");
    }

    private void disableJourneyService() {
        prefs.setJourneyEnabled(false);
        Intent service = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_DISABLE_JOURNEY);
        startService(service);
        refreshUi();
    }

    private void refreshUi() {
        if (activeCard == null) return;
        AppPrefs.ReminderTask task = prefs.getTask();
        activeCard.setVisibility(task == null ? View.GONE : View.VISIBLE);
        if (task != null) {
            activeName.setText(task.destination.name);
            if ("alerting".equals(task.status)) activeMeta.setText("已触发提醒 · 等待确认");
            else if (!Double.isNaN(task.lastDistanceMeters)) activeMeta.setText("距离约 " + formatDistance(task.lastDistanceMeters) + " · " + formatRadius(task.radiusMeters) + " 时提醒");
            else activeMeta.setText("等待定位 · " + formatRadius(task.radiusMeters) + " 时提醒");
        }
        if (!prefs.isJourneyEnabled()) journeyStatus.setText("已关闭 · 点按开启");
        else if (prefs.isAppWatchEnabled() && !hasUsageAccess()) journeyStatus.setText("需要授权出行 App 识别");
        else if (prefs.isTransitWatchEnabled() && !hasNotificationAccess()) journeyStatus.setText("需要授权进站通知识别");
        else if (prefs.isStationWatchEnabled() || prefs.isAppWatchEnabled() || prefs.isTransitWatchEnabled()) journeyStatus.setText("车站、地图与乘车信号待命");
        else journeyStatus.setText("乘车信号检测中");
        List<AppPrefs.FixedAlarm> fixedAlarms = prefs.getFixedAlarms();
        int enabledCount = 0;
        AppPrefs.FixedAlarm firstEnabled = null;
        for (AppPrefs.FixedAlarm alarm : fixedAlarms) if (alarm.enabled) {
            enabledCount++;
            if (firstEnabled == null) firstEnabled = alarm;
        }
        if (fixedAlarmStatus != null) {
            if (fixedAlarms.isEmpty()) fixedAlarmStatus.setText("未设置 · 点按添加");
            else if (!hasFixedAlarmPermissions()) fixedAlarmStatus.setText("已保存 · 需要完成自动启动权限");
            else if (enabledCount == 0) fixedAlarmStatus.setText(fixedAlarms.size() + " 个日程 · 均已暂停");
            else if (enabledCount == 1) fixedAlarmStatus.setText(firstEnabled.destination.name + " · "
                    + ScheduleLogic.daysLabel(firstEnabled.daysMask) + " " + ScheduleLogic.timeLabel(firstEnabled.hour, firstEnabled.minute));
            else fixedAlarmStatus.setText(enabledCount + " 个日程正在自动预设");
        }
        renderRecents();
    }

    private void renderRecents() {
        if (recentsList == null) return;
        recentsList.removeAllViews();
        List<AppPrefs.Destination> recents = prefs.getRecents();
        if (recents.isEmpty()) {
            TextView empty = text("暂无最近地点", 14, Typeface.NORMAL, MUTED);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            recentsList.addView(empty, new LinearLayout.LayoutParams(dp(150), dp(48)));
            return;
        }
        for (final AppPrefs.Destination place : recents) {
            Button item = secondaryButton(place.name);
            item.setAllCaps(false);
            item.setMaxLines(1);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { beginDestination(place); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(142), dp(48));
            params.rightMargin = dp(8);
            recentsList.addView(item, params);
        }
    }

    private void showFixedAlarms() {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(4), dp(18), dp(10));
        List<AppPrefs.FixedAlarm> alarms = prefs.getFixedAlarms();
        if (alarms.isEmpty()) {
            TextView empty = text("还没有通勤预设。添加后，应用会在指定时间自动开始守候固定目的地。", 14, Typeface.NORMAL, MUTED);
            empty.setLineSpacing(dp(3), 1f);
            empty.setPadding(0, dp(8), 0, dp(18));
            content.addView(empty, matchWrap());
        }
        for (final AppPrefs.FixedAlarm alarm : alarms) {
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(roundRect(PAPER, 15, Color.TRANSPARENT));
            LinearLayout copy = column();
            TextView name = text(alarm.destination.name, 15, Typeface.BOLD, INK);
            TextView schedule = text(ScheduleLogic.daysLabel(alarm.daysMask) + "  "
                    + ScheduleLogic.timeLabel(alarm.hour, alarm.minute) + " 开始守候", 13, Typeface.NORMAL, MUTED);
            schedule.setPadding(0, dp(2), 0, 0);
            copy.addView(name, matchWrap());
            copy.addView(schedule, matchWrap());
            Switch enabled = iosSwitch("", alarm.enabled);
            enabled.setOnCheckedChangeListener((button, value) -> {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                AppPrefs.FixedAlarm changed = new AppPrefs.FixedAlarm(alarm.id, alarm.destination, alarm.hour,
                        alarm.minute, alarm.daysMask, value);
                prefs.saveFixedAlarm(changed);
                if (value) ScheduleManager.schedule(MainActivity.this, changed);
                else ScheduleManager.cancel(MainActivity.this, changed.id);
                refreshUi();
            });
            TextView remove = text("删除", 13, Typeface.BOLD, DANGER);
            remove.setGravity(Gravity.CENTER);
            remove.setOnClickListener(view -> confirmRemoveFixedAlarm(alarm));
            row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(enabled, new LinearLayout.LayoutParams(dp(54), dp(48)));
            row.addView(remove, new LinearLayout.LayoutParams(dp(50), dp(44)));
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(8);
            content.addView(row, rowParams);
        }
        Button add = primaryButton("＋ 添加固定闹钟");
        content.addView(add, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        if (!alarms.isEmpty() && !hasFixedAlarmPermissions()) {
            Button permissions = secondaryButton("完成自动启动权限");
            LinearLayout.LayoutParams permissionParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            permissionParams.topMargin = dp(8);
            content.addView(permissions, permissionParams);
            permissions.setOnClickListener(view -> showFixedPermissionSetup());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content, matchWrap());
        AlertDialog dialog = showStyledDialog(new AlertDialog.Builder(this).setTitle("固定地点闹钟").setView(scroll)
                .setPositiveButton("完成", null));
        add.setOnClickListener(view -> {
            if (prefs.getFixedAlarms().size() >= 6) { toast("最多可保存 6 个固定闹钟"); return; }
            dialog.dismiss();
            showAddFixedAlarm();
        });
    }

    private void showAddFixedAlarm() {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(4), dp(18), dp(4));
        TextView hint = text("选择开始监控的时间，不是预计到站时间。", 13, Typeface.NORMAL, MUTED);
        hint.setPadding(0, dp(2), 0, dp(10));
        EditText place = new EditText(this);
        place.setSingleLine(true);
        place.setTextSize(16);
        place.setTextColor(INK);
        place.setHintTextColor(MUTED);
        place.setHint("公司、家、地铁站或地址");
        place.setPadding(dp(12), 0, dp(12), 0);
        place.setBackground(roundRect(PAPER, 14, Color.TRANSPARENT));
        TimePicker time = new TimePicker(this);
        time.setIs24HourView(true);
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MINUTE, 5);
        time.setHour(now.get(Calendar.HOUR_OF_DAY));
        time.setMinute(now.get(Calendar.MINUTE));
        Switch workdays = iosSwitch("仅工作日（关闭则每天）", true);
        content.addView(hint, matchWrap());
        content.addView(place, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        content.addView(time, matchWrap());
        content.addView(workdays, matchWrap());
        showStyledDialog(new AlertDialog.Builder(this).setTitle("添加通勤预设").setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String query = place.getText().toString().trim();
                    if (query.isEmpty()) { toast("请先输入目的地"); return; }
                    int days = workdays.isChecked() ? ScheduleLogic.WORKDAYS_MASK : ScheduleLogic.EVERY_DAY_MASK;
                    resolveFixedAlarmDestination(query, time.getHour(), time.getMinute(), days);
                }));
    }

    private void resolveFixedAlarmDestination(final String query, final int hour, final int minute, final int daysMask) {
        ShareParser.ParsedShare parsed = ShareParser.parseText(query);
        if (parsed != null && parsed.hasCoordinates()) {
            String name = parsed.name.isEmpty() ? "固定目的地" : parsed.name;
            saveFixedAlarm(new AppPrefs.Destination(idFor(name, parsed.latitude, parsed.longitude), name,
                    parsed.source, "", parsed.latitude, parsed.longitude), hour, minute, daysMask);
            return;
        }
        if (!hasPlaceSearchProvider()) { toast("设备没有可用的地点搜索服务"); return; }
        toast("正在识别地点…");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<AppPrefs.Destination> places = queryPlaces(query, 6);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (places.isEmpty()) { toast("没有找到该地点，请输入城市或更具体的名称"); return; }
                        if (places.size() == 1) { saveFixedAlarm(places.get(0), hour, minute, daysMask); return; }
                        showDestinationChoices(places, "确认固定目的地",
                                destination -> saveFixedAlarm(destination, hour, minute, daysMask));
                    }
                });
            }
        }, "private-fixed-place-search").start();
    }

    private void saveFixedAlarm(AppPrefs.Destination destination, int hour, int minute, int daysMask) {
        AppPrefs.FixedAlarm alarm = new AppPrefs.FixedAlarm("fixed-" + System.currentTimeMillis(), destination,
                hour, minute, daysMask, true);
        prefs.saveFixedAlarm(alarm);
        prefs.addRecent(destination);
        ScheduleManager.schedule(this, alarm);
        refreshUi();
        toast("已保存：" + ScheduleLogic.daysLabel(daysMask) + " " + ScheduleLogic.timeLabel(hour, minute));
        if (!hasFixedAlarmPermissions()) showFixedPermissionSetup();
    }

    private void confirmRemoveFixedAlarm(final AppPrefs.FixedAlarm alarm) {
        showStyledDialog(new AlertDialog.Builder(this).setTitle("删除这个固定闹钟？")
                .setMessage(ScheduleLogic.daysLabel(alarm.daysMask) + " " + ScheduleLogic.timeLabel(alarm.hour, alarm.minute)
                        + " · " + alarm.destination.name)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    ScheduleManager.cancel(MainActivity.this, alarm.id);
                    prefs.removeFixedAlarm(alarm.id);
                    refreshUi();
                    toast("固定闹钟已删除");
                }));
    }

    private boolean hasBackgroundLocation() {
        return Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFixedAlarmPermissions() {
        return hasCorePermissions() && hasBackgroundLocation() && ScheduleManager.hasExactAlarmAccess(this);
    }

    private void showFixedPermissionSetup() {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(4), dp(18), dp(8));
        TextView explanation = text("为了在上班或下班时间自动开始守候，需要完成以下系统授权。未完成时日程仍会保存，并尽量用通知让你点按开启。", 13, Typeface.NORMAL, MUTED);
        explanation.setLineSpacing(dp(3), 1f);
        explanation.setPadding(0, 0, 0, dp(12));
        Button core = secondaryButton(hasCorePermissions() ? "定位与通知  ·  已开启" : "定位与通知  ·  去授权");
        Button background = secondaryButton(hasBackgroundLocation() ? "始终允许位置  ·  已开启" : "始终允许位置  ·  去设置");
        Button exact = secondaryButton(ScheduleManager.hasExactAlarmAccess(this) ? "闹钟和提醒  ·  已开启" : "闹钟和提醒  ·  去设置");
        content.addView(explanation, matchWrap());
        content.addView(core, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        rowParams.topMargin = dp(8);
        content.addView(background, rowParams);
        LinearLayout.LayoutParams exactParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        exactParams.topMargin = dp(8);
        content.addView(exact, exactParams);
        AlertDialog dialog = showStyledDialog(new AlertDialog.Builder(this).setTitle("完成自动启动设置").setView(content)
                .setPositiveButton("稍后完成", null));
        core.setOnClickListener(view -> { dialog.dismiss(); requestCorePermissions(); });
        background.setOnClickListener(view -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) { toast("请在应用权限中把位置改为始终允许"); }
        });
        exact.setOnClickListener(view -> {
            dialog.dismiss();
            if (Build.VERSION.SDK_INT < 31) return;
            try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()))); }
            catch (Exception ignored) { toast("请在特殊应用权限中允许闹钟和提醒"); }
        });
    }

    private void showSettings() {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        final TextView radiusLabel = text("提醒距离  ·  " + formatRadius(prefs.getRadius()), 15, Typeface.BOLD, INK);
        final SeekBar radius = new SeekBar(this);
        radius.setMax(47);
        radius.setProgress((prefs.getRadius() - 300) / 100);
        radius.setProgressTintList(ColorStateList.valueOf(GREEN));
        radius.setThumbTintList(ColorStateList.valueOf(GREEN));
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { radiusLabel.setText("提醒距离  ·  " + formatRadius(300 + progress * 100)); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        final Switch journey = iosSwitch("乘车时提醒设置目的地", prefs.isJourneyEnabled());
        final Switch stationWatch = iosSwitch("靠近地铁站或火车站时提醒", prefs.isStationWatchEnabled());
        final Switch appWatch = iosSwitch("打开地图或打车 App 时提醒", prefs.isAppWatchEnabled());
        final Switch transitWatch = iosSwitch("导航、交通卡和进站通知触发", prefs.isTransitWatchEnabled());
        TextView privacy = text("地点搜索会自动使用地图 POI 服务，不要求用户配置密钥。搜索关键词及可选的附近坐标会发送给地点服务；出行 App 与交通关键词识别仍只在本机处理。", 13, Typeface.NORMAL, MUTED);
        privacy.setLineSpacing(dp(3), 1f);
        privacy.setPadding(0, dp(16), 0, dp(6));
        content.addView(radiusLabel, matchWrap());
        content.addView(radius, matchWrap());
        content.addView(journey, matchWrap());
        content.addView(stationWatch, matchWrap());
        content.addView(appWatch, matchWrap());
        content.addView(transitWatch, matchWrap());
        content.addView(privacy, matchWrap());
        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.addView(content, matchWrap());
        showStyledDialog(new AlertDialog.Builder(this).setTitle("自动提醒与隐私").setView(settingsScroll)
                .setNeutralButton("清空数据", (dialog, which) -> confirmClearData())
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    prefs.setRadius(300 + radius.getProgress() * 100);
                    prefs.setStationWatchEnabled(stationWatch.isChecked());
                    prefs.setAppWatchEnabled(appWatch.isChecked());
                    prefs.setTransitWatchEnabled(transitWatch.isChecked());
                    boolean wantsJourney = journey.isChecked() || stationWatch.isChecked() || appWatch.isChecked() || transitWatch.isChecked();
                    if (wantsJourney && !prefs.isJourneyEnabled()) requestEnableJourney();
                    else if (!wantsJourney && prefs.isJourneyEnabled()) disableJourneyService();
                    if (appWatch.isChecked() && !hasUsageAccess()) explainAndRequestUsageAccess();
                    else if (transitWatch.isChecked() && !hasNotificationAccess()) explainAndRequestNotificationAccess();
                    refreshUi();
                }));
    }

    private void confirmClearData() {
        showStyledDialog(new AlertDialog.Builder(this).setTitle("清空本机数据？").setMessage("当前提醒、最近地点和设置都会删除。")
                .setNegativeButton("取消", null).setPositiveButton("清空", (dialog, which) -> {
                    for (AppPrefs.FixedAlarm alarm : prefs.getFixedAlarms()) ScheduleManager.cancel(MainActivity.this, alarm.id);
                    Intent service = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_CLEAR_ALL);
                    startService(service);
                    prefs.clearAll();
                    refreshUi();
                    toast("本机数据已清空");
                }));
    }

    private void showPrivacy() {
        String message = "只在活动到站提醒或你主动开启乘车提示时使用位置。\n\n" +
                "地点候选默认由 Photon/OpenStreetMap 提供；若开发者构建时配置了高德服务，则优先使用高德。搜索关键词和可选的附近坐标会直接发送给地点服务，结果仅做本机会话缓存；应用没有自己的业务服务器。\n\n" +
                "固定通勤地点、时间和启用状态只保存在本机；系统仅在指定时间唤醒日程。\n\n" +
                "地图和打车地点可由你主动分享。可选的使用情况访问只判断受支持的出行 App 是否刚被打开；可选的通知使用权只在内存中过滤导航、交通卡、进站和检票关键词。不会保存通知正文或完整使用记录。\n\n" +
                "强制结束应用、关闭定位、静音/勿扰或地下无信号时，闹钟可能无法触发。";
        showStyledDialog(new AlertDialog.Builder(this).setTitle("隐私与能力边界").setMessage(message).setPositiveButton("知道了", null));
    }

    private boolean hasUsageAccess() {
        AppOpsManager manager = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        if (manager == null) return false;
        int mode = manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void explainAndRequestUsageAccess() {
        showStyledDialog(new AlertDialog.Builder(this).setTitle("允许本地识别出行 App？")
                .setMessage("系统会授予“使用情况访问”。本应用只检查高德、百度、腾讯地图及常见打车 App 是否刚被打开，用于提示你设置目的地；不读取页面内容，不上传或保存完整使用记录。")
                .setNegativeButton("暂不", null)
                .setPositiveButton("去授权", (dialog, which) -> {
                    try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
                    catch (Exception ignored) { toast("请在系统的使用情况访问页面手动开启"); }
                }));
    }

    private boolean hasNotificationAccess() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void explainAndRequestNotificationAccess() {
        showStyledDialog(new AlertDialog.Builder(this).setTitle("允许本地识别交通信号？")
                .setMessage("系统的通知使用权范围较大。本应用收到通知后先按高德、百度、腾讯、滴滴、12306、手机钱包等来源过滤，再只匹配“导航、进站、检票、刷卡成功”等关键词；正文不写入存储，不上传。实体交通卡独立刷闸机且手机没有通知时无法检测。")
                .setNegativeButton("暂不", null)
                .setPositiveButton("去授权", (dialog, which) -> {
                    try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (Exception ignored) { toast("请在系统的通知使用权页面手动开启"); }
                }));
    }

    private void maybeRequestFullScreenIntentAccess() {
        if (Build.VERSION.SDK_INT < 34) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager.canUseFullScreenIntent()) return;
        showStyledDialog(new AlertDialog.Builder(this).setTitle("允许全屏到站闹钟")
                .setMessage("为了在锁屏时直接弹出到站提醒，请在系统页面允许此应用使用全屏通知。即使不允许，声音、振动和高优先级通知仍会保留。")
                .setNegativeButton("暂不", null)
                .setPositiveButton("去设置", (dialog, which) -> {
                    try {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    } catch (Exception ignored) { toast("请在系统通知设置中允许全屏提醒"); }
                }));
    }

    private void applySafeArea(final View root) {
        final int horizontal = dp(20);
        final int top = dp(12);
        final int bottom = dp(38);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                int leftInset;
                int topInset;
                int rightInset;
                int bottomInset;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    leftInset = bars.left;
                    topInset = bars.top;
                    rightInset = bars.right;
                    bottomInset = bars.bottom;
                } else {
                    leftInset = windowInsets.getStableInsetLeft();
                    topInset = windowInsets.getStableInsetTop();
                    rightInset = windowInsets.getStableInsetRight();
                    bottomInset = windowInsets.getStableInsetBottom();
                }
                view.setPadding(horizontal + leftInset, top + topInset, horizontal + rightInset, bottom + bottomInset);
                return windowInsets;
            }
        });
        root.post(new Runnable() {
            @Override public void run() { root.requestApplyInsets(); }
        });
    }

    private void configurePalette() {
        GREEN = Color.rgb(0, 122, 255);
        INK = Color.rgb(28, 28, 30);
        MUTED = Color.rgb(110, 110, 115);
        PAPER = Color.rgb(242, 242, 247);
        SURFACE = Color.WHITE;
        SOFT_BLUE = Color.rgb(232, 242, 255);
        SEPARATOR = Color.rgb(229, 229, 234);
        DANGER = Color.rgb(255, 59, 48);
        SOFT_RED = Color.rgb(255, 240, 239);
    }

    private TextView sectionTitle(String label) {
        TextView title = text(label, 13, Typeface.BOLD, MUTED);
        title.setLetterSpacing(0.025f);
        return title;
    }

    private Switch iosSwitch(String label, boolean checked) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(15);
        control.setTextColor(INK);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setShowText(false);
        control.setSwitchMinWidth(dp(48));
        control.setMinHeight(dp(48));
        control.setPadding(0, dp(4), 0, dp(4));
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        control.setTrackTintList(new ColorStateList(states, new int[]{GREEN, Color.rgb(199, 199, 204)}));
        control.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.WHITE}));
        control.setChecked(checked);
        control.setOnCheckedChangeListener((button, value) -> button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
        return control;
    }

    private AlertDialog showStyledDialog(AlertDialog.Builder builder) {
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(roundRect(SURFACE, 24, Color.TRANSPARENT));
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GREEN);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(GREEN);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setAllCaps(false);
            }
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(INK);
                message.setLineSpacing(dp(3), 1f);
            }
        });
        dialog.show();
        return dialog;
    }

    private void applyPressFeedback(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View target, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    target.animate().scaleX(0.98f).scaleY(0.98f).setDuration(45).start();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    target.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
    }

    private void animateEntrance(final View root) {
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        root.setAlpha(0f);
        root.setTranslationY(dp(8));
        root.post(new Runnable() {
            @Override public void run() {
                root.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            }
        });
    }

    private String formatRadius(int meters) { return meters < 1000 ? meters + " 米" : (meters % 1000 == 0 ? String.valueOf(meters / 1000) : String.format(Locale.CHINA, "%.1f", meters / 1000f)) + " 公里"; }
    private String formatDistance(double meters) { return meters < 1000 ? Math.round(meters / 10) * 10 + " 米" : String.format(Locale.CHINA, meters >= 10_000 ? "%.0f 公里" : "%.1f 公里", meters / 1000); }
    private String idFor(String name, double lat, double lng) { return Integer.toHexString((name + ":" + lat + ":" + lng).hashCode()); }

    private void hideKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(destinationInput.getWindowToken(), 0); }
    private void showKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(destinationInput, InputMethodManager.SHOW_IMPLICIT); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrapWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private TextView text(String value, int sp, int style, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private Button primaryButton(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextColor(Color.WHITE); button.setTextSize(15); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setBackground(roundRect(GREEN, 14, Color.TRANSPARENT)); button.setStateListAnimator(null); applyPressFeedback(button); return button; }
    private Button secondaryButton(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextColor(GREEN); button.setTextSize(14); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setBackground(roundRect(SURFACE, 14, SEPARATOR)); button.setStateListAnimator(null); applyPressFeedback(button); return button; }
    private Button dangerButton(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextColor(DANGER); button.setTextSize(14); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setBackground(roundRect(SOFT_RED, 13, Color.TRANSPARENT)); button.setStateListAnimator(null); applyPressFeedback(button); return button; }
    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radiusDp)); drawable.setStroke(dp(1), stroke); return drawable; }
}
