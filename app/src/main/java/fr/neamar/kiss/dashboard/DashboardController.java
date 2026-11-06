package fr.neamar.kiss.dashboard;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.ContextCompat;
import java.text.DateFormat;
import java.util.*;
import fr.neamar.kiss.*;
import fr.neamar.kiss.db.*;
import fr.neamar.kiss.pojo.*;
import fr.neamar.kiss.result.Result;
import fr.neamar.kiss.searcher.*;

/** Accessible presentation over KISS's existing provider, search, result and history pipeline. */
public final class DashboardController {
    private final MainActivity activity;
    private final SharedPreferences preferences;
    private final LinearLayout root;
    private LinearLayout content;
    private ScrollView scroll;
    private EditText query;
    private TextView letterBubble;
    private boolean searchMode;
    private boolean alphabetMode;
    private boolean registered;
    private float textSp;
    private final java.util.concurrent.ExecutorService calendarExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private List<String> calendarLines = Collections.singletonList("Loading calendar…");
    private boolean destroyed;
    private final Runnable refreshHome = () -> { if (!searchMode && !destroyed) renderHome(); };
    private static final int BG = Color.rgb(8, 7, 12);
    private static final int CARD = Color.rgb(29, 25, 37);
    private static final int ACCENT = Color.rgb(216, 190, 255);
    private final DataSetObserver observer = new DataSetObserver() {
        @Override public void onChanged() {
            if (searchMode && !alphabetMode) renderSearchResults();
            else if (!searchMode) { root.removeCallbacks(refreshHome); root.postDelayed(refreshHome, 100); }
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!searchMode) renderHome();
        }
    };

    public DashboardController(MainActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("accessible-dashboard", Context.MODE_PRIVATE);
        textSp = preferences.getFloat("text-size", 28);
        ViewGroup host = activity.findViewById(android.R.id.content);
        host.getChildAt(0).setVisibility(View.GONE);
        root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setFitsSystemWindows(true);
        host.addView(root, new ViewGroup.LayoutParams(-1, -1));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + bars.bottom);
            return insets;
        });
        activity.adapter.registerDataSetObserver(observer);
        showHome();
    }

    private DataHandler data() { return KissApplication.getApplication(activity).getDataHandler(); }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable background(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); return d; }
    private TextView text(CharSequence value, float size, boolean bold) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(Color.WHITE);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(8), dp(6), dp(8), dp(6)); return v;
    }
    private TextView button(String label, Runnable action) {
        TextView v = text(label, Math.max(20, textSp - 4), true); v.setMinHeight(dp(56));
        v.setGravity(Gravity.CENTER); v.setBackground(background(CARD)); v.setOnClickListener(w -> action.run());
        v.setFocusable(true); return v;
    }
    private void addCard(LinearLayout card) {
        card.setPadding(dp(6), dp(6), dp(6), dp(6)); card.setBackground(background(CARD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(8);
        content.addView(card, lp);
    }
    public void resume() {
        if (!registered) {
            IntentFilter filter = new IntentFilter(DashboardNotifications.CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF); filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(MainActivity.LOAD_OVER); filter.addAction(MainActivity.REFRESH_FAVORITES);
            ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            registered = true;
        }
        loadCalendar();
        if (!searchMode) renderHome();
    }
    public void pause() { root.removeCallbacks(refreshHome); if (registered) { activity.unregisterReceiver(receiver); registered = false; } }
    public void destroy() { destroyed = true; pause(); calendarExecutor.shutdownNow(); activity.adapter.unregisterDataSetObserver(observer); }
    public boolean back() { if (searchMode) { showHome(); return true; } return activity.isKissDefaultLauncher(); }
    public void showHome() {
        searchMode = false; alphabetMode = false;
        activity.searchEditText.setText("");
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.removeAllViews();
        LinearLayout header = row(); header.addView(text("Home", textSp + 8, true), new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Controls", this::showControls)); root.addView(header);
        root.addView(button("Search apps and more", this::showSearch));
        scroll = new PullDownScrollView(activity); content = column(); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        renderHome();
    }
    private void renderHome() {
        if (searchMode || content == null) return;
        int offset = scroll.getScrollY(); content.removeAllViews();
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard.isKeyguardLocked()) {
            if (root.getChildCount() > 3) root.removeViewAt(3);
            content.addView(text("Unlock to view notifications", textSp, true)); return;
        }
        StatusBarNotification[] notifications = DashboardNotifications.active();
        Map<String, Integer> ranks = DashboardNotifications.ranks(notifications);
        Arrays.sort(notifications, Comparator.comparingInt(n -> ranks.getOrDefault(n.getKey(), Integer.MAX_VALUE)));
        if (notifications.length == 0) {
            LinearLayout card = column(); card.addView(text("Notifications", textSp, true));
            card.addView(text("No available notifications. Notification access is optional and keeps content on this phone.", textSp, false));
            card.addView(button("Review notification access", () -> open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))); addCard(card);
        }
        for (StatusBarNotification sbn : notifications) {
            Notification n = sbn.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            LinearLayout card = column();
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (body == null) body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            String appName = sbn.getPackageName();
            try { appName = activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(appName, 0)).toString(); }
            catch (PackageManager.NameNotFoundException ignored) { }
            card.addView(text(appName + (title == null ? "" : " · " + title), textSp, true));
            if (body != null) card.addView(text(body, textSp, false));
            LinearLayout actions = row();
            if (n.contentIntent != null) actions.addView(button("Open", () -> send(n.contentIntent)));
            if (sbn.isClearable()) actions.addView(button("Dismiss", () -> DashboardNotifications.dismiss(sbn.getKey())));
            card.addView(actions); addCard(card);
        }
        renderTasks(); renderCalendar(); renderDock();
        scroll.post(() -> scroll.scrollTo(0, offset));
    }
    private void renderTasks() {
        LinearLayout card = column(); card.addView(text("Tasks", textSp, true));
        String tasks = preferences.getString("tasks", "");
        for (String task : tasks.split("\n")) {
            if (task.isEmpty()) continue;
            CheckBox check = new CheckBox(activity); check.setText(task); check.setTextSize(textSp); check.setTextColor(Color.WHITE); check.setMinHeight(dp(56));
            check.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    List<String> remaining = new ArrayList<>(Arrays.asList(preferences.getString("tasks", "").split("\n")));
                    remaining.remove(task); preferences.edit().putString("tasks", String.join("\n", remaining)).apply(); renderHome();
                }
            }); card.addView(check);
        }
        card.addView(button("Add task", () -> {
            EditText input = new EditText(activity); input.setTextSize(textSp); input.setHint("Task title");
            new AlertDialog.Builder(activity).setTitle("Add a local task").setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Add", (d, w) -> { String value = input.getText().toString().trim().replace('\n', ' ');
                    if (!value.isEmpty()) { preferences.edit().putString("tasks", preferences.getString("tasks", "") + "\n" + value).apply(); renderHome(); }
                }).show();
        })); addCard(card);
    }
    private void renderCalendar() {
        LinearLayout card = column(); card.addView(text("Upcoming", textSp, true));
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            card.addView(text("Calendar access is optional. Events are read only.", textSp, false));
            card.addView(button("Allow calendar access", () -> androidx.core.app.ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_CALENDAR}, 741)));
        } else {
            for (String line : calendarLines) card.addView(text(line, textSp, false));
        }
        addCard(card);
    }
    private void loadCalendar() {
        if (destroyed || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        calendarExecutor.execute(() -> {
            List<String> lines = new ArrayList<>();
            try (Cursor c = CalendarContract.Instances.query(activity.getContentResolver(), new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN}, System.currentTimeMillis(), System.currentTimeMillis() + 7L * 86400000)) {
                int count = 0;
                while (c != null && c.moveToNext() && count++ < 5) {
                    lines.add(c.getString(0) + "\n" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(c.getLong(1))));
                }
                if (lines.isEmpty()) lines.add("No events in the next seven days");
            } catch (SecurityException | IllegalArgumentException e) { lines.add("Calendar unavailable"); }
            root.post(() -> { if (!destroyed) { calendarLines = lines; if (!searchMode) renderHome(); } });
        });
    }
    private void renderDock() {
        if (root.getChildCount() > 3) root.removeViewAt(3);
        HorizontalScrollView dockScroll = new HorizontalScrollView(activity); LinearLayout dock = row(); dockScroll.addView(dock);
        Set<String> used = new HashSet<>(); int limit = DashboardPolicy.favoriteCount(activity.getResources().getConfiguration().screenWidthDp);
        for (Pojo pojo : data().getFavorites()) {
            if (!(pojo instanceof AppPojo) || pojo.isDisabled() || used.size() >= limit) continue;
            used.add(pojo.id); dock.addView(appIcon(pojo));
        }
        if (used.isEmpty()) dock.addView(button("Choose favorites", this::showSearch));
        dock.addView(text("│", 30, false)); int count = 0;
        for (ValuedHistoryRecord record : DBHelper.getHistory(activity, 50, HistoryMode.RECENCY)) {
            Pojo pojo = data().getPojo(record.record);
            if (!(pojo instanceof AppPojo) || pojo.isDisabled() || ((AppPojo) pojo).isExcluded() || ((AppPojo) pojo).isExcludedFromHistory() || used.contains(pojo.id)) continue;
            used.add(pojo.id); dock.addView(appIcon(pojo)); if (++count == 2) break;
        }
        root.addView(dockScroll);
    }
    private View appIcon(Pojo pojo) {
        Result<?> result = Result.fromPojo(activity, pojo); ImageView image = new ImageView(activity);
        image.setImageDrawable(result.getDrawable(activity)); image.setContentDescription(pojo.getName());
        image.setPadding(dp(8), dp(8), dp(8), dp(8)); image.setBackground(background(CARD));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64)); lp.setMargins(dp(3), dp(4), dp(3), dp(4)); image.setLayoutParams(lp);
        image.setFocusable(true); image.setOnClickListener(v -> { result.launch(activity, v, activity); showHome(); });
        image.setOnLongClickListener(v -> { new AlertDialog.Builder(activity).setTitle(pojo.getName()).setItems(new String[]{"Remove favorite"}, (d, w) -> { data().removeFromFavorites(pojo.id); renderHome(); }).show(); return true; }); return image;
    }
    public void sharedSearch(String value) { showSearch(); query.setText(value); }
    public void permissionChanged() { loadCalendar(); if (!searchMode) renderHome(); }
    public void showSearch() {
        searchMode = true; alphabetMode = false; root.removeAllViews();
        LinearLayout header = row(); header.addView(button("Home", this::showHome)); query = new EditText(activity);
        query.setSingleLine(true); query.setTextSize(textSp); query.setTextColor(Color.WHITE); query.setHintTextColor(ACCENT); query.setHint("Search apps and more");
        header.addView(query, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        LinearLayout body = row(); scroll = new ScrollView(activity); content = column(); scroll.addView(content);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        TextView index = text("A\nB\nC\nD\nE\nF\nG\nH\nI\nJ\nK\nL\nM\nN\nO\nP\nQ\nR\nS\nT\nU\nV\nW\nX\nY\nZ", 16, true);
        index.setGravity(Gravity.CENTER); index.setBackground(background(CARD)); index.setContentDescription("Slide to select a letter. Use All apps for accessible alphabetical browsing.");
        body.addView(index, new LinearLayout.LayoutParams(dp(56), -1));
        index.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                showAlphabet((char) ('A' + DashboardPolicy.letterIndex(event.getY(), v.getHeight()))); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) { if (letterBubble != null) letterBubble.setVisibility(View.GONE); v.performClick(); return true; }
            return false;
        });
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(button("All apps", () -> showAlphabet((char) 0)));
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                alphabetMode = false; activity.searchEditText.setText(s);
                if (s.length() == 0) activity.showHistory();
            }
            public void afterTextChanged(Editable s) { }
        });
        activity.searchEditText.setText(""); activity.showHistory(); renderSearchResults();
        query.requestFocus();
        query.post(() -> ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(query, InputMethodManager.SHOW_IMPLICIT));
    }
    private void renderSearchResults() {
        if (!searchMode || alphabetMode || content == null) return;
        content.removeAllViews(); content.addView(text(query.getText().length() == 0 ? "Recent" : "Results", textSp, true));
        for (int i = activity.adapter.getCount() - 1; i >= 0; i--) addResult(activity.adapter.getItem(i));
    }
    private void addResult(Result<?> result) {
        LinearLayout item = row(); ImageView icon = new ImageView(activity); icon.setImageDrawable(result.getDrawable(activity));
        item.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        item.addView(text(result.getPojo().getName(), textSp, false), new LinearLayout.LayoutParams(0, -2, 1));
        item.setMinimumHeight(dp(64)); item.setFocusable(true); item.setContentDescription(result.getPojo().getName());
        item.setOnClickListener(v -> { result.launch(activity, v, activity); if (!(result.getPojo() instanceof TagDummyPojo)) showHome(); });
        item.setOnLongClickListener(v -> { Pojo pojo = result.getPojo(); boolean favorite = data().hasFavorite(pojo.id);
            new AlertDialog.Builder(activity).setTitle(pojo.getName()).setItems(new String[]{favorite ? "Remove favorite" : "Add favorite"}, (d, which) -> {
                if (favorite) data().removeFromFavorites(pojo.id); else data().addToFavorites(pojo.id);
            }).show(); return true;
        }); addCard(item);
    }
    private void showAlphabet(char letter) {
        alphabetMode = true; content.removeAllViews(); letterBubble = text(letter == 0 ? "All apps" : String.valueOf(letter), textSp + 24, true); letterBubble.setTextColor(ACCENT); content.addView(letterBubble);
        List<AppPojo> available = data().getApplicationsWithoutExcluded(); if (available == null) return;
        List<AppPojo> apps = new ArrayList<>(available); apps.sort(Comparator.comparing(Pojo::getName, String.CASE_INSENSITIVE_ORDER));
        for (AppPojo app : apps) if (!app.getName().isEmpty() && Character.toUpperCase(app.getName().charAt(0)) >= letter) addResult(Result.fromPojo(activity, app));
        scroll.scrollTo(0, 0);
    }
    private void showControls() {
        LinearLayout controls = column(); controls.setPadding(dp(16), dp(16), dp(16), dp(16)); controls.setBackgroundColor(BG);
        controls.addView(button("Wi-Fi · Open settings", () -> open(new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS))));
        controls.addView(button("Bluetooth · Open settings", () -> open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
        controls.addView(button("Brightness · Open settings", () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
        controls.addView(text("Text size", textSp, true)); SeekBar size = new SeekBar(activity); size.setMax(40); size.setProgress((int) textSp - 20);
        size.setContentDescription("Launcher text size, 20 to 60 scaled pixels");
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { if (user) { textSp = 20 + progress; preferences.edit().putFloat("text-size", textSp).apply(); } }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        }); controls.addView(size);
        controls.addView(button("KISS settings", () -> open(new Intent(activity, SettingsActivity.class))));
        controls.addView(button("More system settings", () -> open(new Intent(Settings.ACTION_SETTINGS))));
        ScrollView container = new ScrollView(activity); container.addView(controls);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Quick controls").setView(container).setPositiveButton("Close", null).create();
        dialog.setOnDismissListener(d -> { if (searchMode) showSearch(); else showHome(); }); dialog.show();
    }
    private void open(Intent intent) { try { activity.startActivity(intent); } catch (android.content.ActivityNotFoundException | SecurityException e) { Toast.makeText(activity, "This action is unavailable on this device", Toast.LENGTH_LONG).show(); } }
    private void send(PendingIntent intent) { try { intent.send(); } catch (PendingIntent.CanceledException e) { Toast.makeText(activity, "This notification has expired", Toast.LENGTH_SHORT).show(); } }
    private final class PullDownScrollView extends ScrollView {
        private float startX, startY; private boolean atTop;
        PullDownScrollView(Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { startX = event.getX(); startY = event.getY(); atTop = getScrollY() == 0; }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && DashboardPolicy.opensSearch(atTop, event.getX() - startX, event.getY() - startY, dp(100))) {
                MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL); super.dispatchTouchEvent(cancel); cancel.recycle(); showSearch(); return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
