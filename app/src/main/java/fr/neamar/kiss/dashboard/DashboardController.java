package fr.neamar.kiss.dashboard;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ActivityOptions;
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
    private View dockView;
    private View alphabetRail;
    private View notificationTarget, tasksTarget, calendarTarget;
    private View homeAppTail, homeLastHeading;
    private boolean railDragging;
    private ContinuousRail homeRail;
    private FrameLayout searchFrame;
    private final Map<Character, View> alphabetTargets = new HashMap<>();
    private int renderedColumns = 1;
    private final Set<String> expandedNotifications = new HashSet<>();
    private boolean navigatingTag;
    private boolean searchMode;
    private boolean registered;
    private boolean resumed;
    private View notificationTouchOwner;
    private boolean feedTouching;
    private long feedLastMotion;
    private float textSp;
    private final java.util.concurrent.ExecutorService calendarExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private List<CalendarEntry> calendarLines = Collections.singletonList(new CalendarEntry("Loading calendar…", null));
    private boolean destroyed;
    private final Runnable refreshHome = () -> { if (!searchMode && !destroyed) renderHome(); };
    private static final int BG = Color.rgb(8, 7, 12);
    private static final int CARD = Color.rgb(29, 25, 37);
    private static final int ACCENT = Color.rgb(216, 190, 255);
    private final DataSetObserver observer = new DataSetObserver() {
        @Override public void onChanged() {
            if (searchMode) renderSearchResults();
            else if (!searchMode) { root.removeCallbacks(refreshHome); root.postDelayed(refreshHome, 100); }
        }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!searchMode) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) renderHome();
                else scheduleHomeRefresh();
            } else if (query != null && query.length() == 0) renderSearchResults();
        }
    };

    public DashboardController(MainActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("accessible-dashboard", Context.MODE_PRIVATE);
        textSp = preferences.getFloat("text-size", 28);
        ViewGroup host = activity.findViewById(android.R.id.content);
        host.getChildAt(0).setVisibility(View.GONE);
        root = column();
        root.setBackgroundColor(Color.TRANSPARENT);
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setFitsSystemWindows(true);
        host.addView(root, new ViewGroup.LayoutParams(-1, -1));
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()
                | androidx.core.view.WindowInsetsCompat.Type.ime());
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + bars.bottom);
            return insets;
        });
        root.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (appColumns() != renderedColumns) root.post(() -> { if (searchMode) renderSearchResults(); else renderHome(); });
            else if (!searchMode) root.post(this::updateHomeTailPadding);
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
        resumed = true;
        textSp = preferences.getFloat("text-size", 28);
        if (letterBubble != null) letterBubble.setTextSize(textSp + 24);
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
    public void pause() { resumed = false; feedTouching = false; railDragging = false; notificationTouchOwner = null; root.removeCallbacks(refreshHome); if (registered) { activity.unregisterReceiver(receiver); registered = false; } }
    public void destroy() { destroyed = true; pause(); calendarExecutor.shutdownNow(); activity.adapter.unregisterDataSetObserver(observer); }
    public boolean back() { if (searchMode) { showHome(); return true; } return activity.isKissDefaultLauncher(); }
    public void showHome() {
        searchMode = false; navigatingTag = false;
        if (query != null) query.clearFocus();
        activity.searchEditText.setText("");
        ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
        root.removeAllViews();
        searchFrame = new FrameLayout(activity); LinearLayout homeBody = row();
        scroll = new FeedScrollView(activity); scroll.setVerticalScrollBarEnabled(false); scroll.setHorizontalScrollBarEnabled(false); content = column(); scroll.addView(content);
        homeBody.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        ContinuousRail rail = new ContinuousRail(activity); homeRail = rail;
        rail.addView(iconButton(R.drawable.dashboard_notifications, "Jump to notifications", () -> jumpToSection(notificationTarget)), new LinearLayout.LayoutParams(dp(56), dp(48)));
        rail.addView(iconButton(R.drawable.dashboard_tasks, "Jump to tasks", () -> jumpToSection(tasksTarget)), new LinearLayout.LayoutParams(dp(56), dp(48)));
        rail.addView(iconButton(R.drawable.dashboard_calendar, "Jump to calendar", () -> jumpToSection(calendarTarget)), new LinearLayout.LayoutParams(dp(56), dp(48)));
        alphabetRail = new AlphabetRail(activity); rail.addView(alphabetRail, new LinearLayout.LayoutParams(dp(56), 0, 1));
        ImageButton searchControl = iconButton(android.R.drawable.ic_menu_search, "Search apps and more", this::showSearch);
        searchControl.setOnLongClickListener(v -> { open(new Intent(activity, SettingsActivity.class)); return true; });
        androidx.core.view.ViewCompat.addAccessibilityAction(searchControl, "Launcher settings", (view, arguments) -> { open(new Intent(activity, SettingsActivity.class)); return true; });
        rail.addView(searchControl, new LinearLayout.LayoutParams(dp(56), dp(56)));
        homeBody.addView(rail, new LinearLayout.LayoutParams(dp(56), -1));
        searchFrame.addView(homeBody, new FrameLayout.LayoutParams(-1, -1));
        letterBubble = text("", textSp + 24, true); letterBubble.setTextColor(BG); letterBubble.setBackground(background(ACCENT));
        letterBubble.setGravity(Gravity.CENTER); letterBubble.setVisibility(View.GONE); letterBubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams bubbleLayout = new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.END | Gravity.CENTER_VERTICAL); bubbleLayout.setMarginEnd(dp(64));
        searchFrame.addView(letterBubble, bubbleLayout);
        root.addView(searchFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        renderHome();
    }
    private void jumpToSection(View target) {
        if (searchMode || target == null || !target.isAttachedToWindow()) return;
        letterBubble.setVisibility(View.GONE);
        ScrollView targetScroll = scroll;
        targetScroll.post(() -> {
            if (searchMode || scroll != targetScroll || !target.isAttachedToWindow()) return;
            targetScroll.smoothScrollTo(0, target.getTop());
            android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager) activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager.isTouchExplorationEnabled()) target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            else if (!target.isInTouchMode()) { target.setFocusable(true); target.requestFocus(); }
        });
    }
    private void scheduleHomeRefresh() {
        root.removeCallbacks(refreshHome);
        root.postDelayed(refreshHome, 120);
    }
    private void renderHome() {
        if (searchMode || content == null) return;
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (!keyguard.isKeyguardLocked() && (notificationTouchOwner != null || railDragging
                || feedTouching || android.os.SystemClock.uptimeMillis() - feedLastMotion < 180)) {
            scheduleHomeRefresh(); return;
        }
        root.removeCallbacks(refreshHome);
        int offset = scroll.getScrollY();
        Object anchorTag = null; int anchorDelta = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child.getBottom() > offset && child.getTag() != null) { anchorTag = child.getTag(); anchorDelta = offset - child.getTop(); break; }
        }
        View focused = content.findFocus(); Object focusTag = focused == null ? null : focused.getTag();
        content.removeAllViews(); alphabetTargets.clear();
        notificationTarget = tasksTarget = calendarTarget = null; homeAppTail = homeLastHeading = null; renderedColumns = appColumns();
        if (keyguard.isKeyguardLocked()) {
            if (dockView != null) root.removeView(dockView);
            content.addView(text("Unlock to view notifications", textSp, true)); return;
        }
        notificationTarget = text("Notifications", textSp, true); notificationTarget.setTag("section-notifications"); content.addView(notificationTarget);
        StatusBarNotification[] notifications = DashboardNotifications.active();
        Map<String, Integer> ranks = DashboardNotifications.ranks(notifications);
        Arrays.sort(notifications, Comparator.comparingInt(n -> ranks.getOrDefault(n.getKey(), Integer.MAX_VALUE)));
        if (notifications.length == 0) {
            LinearLayout card = column();
            card.addView(text("No available notifications. Notification access is optional and keeps content on this phone.", textSp, false));
            card.addView(button("Review notification access", () -> open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))); addCard(card);
        }
        for (StatusBarNotification sbn : notifications) {
            Notification n = sbn.getNotification();
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
            LinearLayout card = new NotificationCard(sbn); card.setTag("notification:" + sbn.getKey());
            CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (body == null) body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            String appName = sbn.getPackageName();
            try { appName = activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(appName, 0)).toString(); }
            catch (PackageManager.NameNotFoundException ignored) { }
            LinearLayout notificationHeader = row();
            ImageView notificationIcon = new ImageView(activity);
            notificationIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            notificationIcon.setContentDescription("Notification from " + appName);
            notificationIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.drawable.Drawable icon = null;
            try { icon = activity.getPackageManager().getApplicationIcon(sbn.getPackageName()); }
            catch (PackageManager.NameNotFoundException ignored) { }
            if (icon == null && Build.VERSION.SDK_INT >= 23 && n.getSmallIcon() != null) {
                try { icon = n.getSmallIcon().loadDrawable(activity); } catch (RuntimeException ignored) { }
            }
            android.graphics.drawable.Drawable appIconDrawable = icon != null ? icon : activity.getPackageManager().getDefaultActivityIcon();
            notificationIcon.setImageDrawable(KissApplication.getApplication(activity).getIconsHandler().applyIconMask(activity, appIconDrawable));
            LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconLayout.setMarginEnd(dp(4)); notificationHeader.addView(notificationIcon, iconLayout);
            if (title != null) notificationHeader.addView(text(title, textSp, true), new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(notificationHeader);
            if (body != null) {
                TextView bodyView = text(body, textSp, false);
                boolean expanded = expandedNotifications.contains(sbn.getKey());
                bodyView.setMaxLines(expanded ? Integer.MAX_VALUE : 4);
                bodyView.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);
                card.addView(bodyView);
                TextView toggle = button(expanded ? "Show less" : "Show more", () -> {
                    if (expandedNotifications.contains(sbn.getKey())) expandedNotifications.remove(sbn.getKey());
                    else expandedNotifications.add(sbn.getKey());
                    renderHome();
                });
                toggle.setContentDescription((expanded ? "Collapse" : "Expand") + " notification from " + appName);
                toggle.setVisibility(View.GONE); card.addView(toggle);
                bodyView.post(() -> {
                    android.text.Layout layout = bodyView.getLayout();
                    if (layout == null || !bodyView.isAttachedToWindow()) return;
                    int lines = layout.getLineCount();
                    boolean overflow = lines > 4 || (lines > 0 && layout.getEllipsisCount(lines - 1) > 0);
                    toggle.setVisibility(overflow ? View.VISIBLE : View.GONE);
                });
            }
            boolean media = Notification.CATEGORY_TRANSPORT.equals(n.category) || n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
                || n.extras.containsKey("android.compactActions")
                || String.valueOf(n.extras.getString("android.template", "")).contains("MediaStyle");
            // Call classification uses platform metadata, never the app name or notification prose.
            boolean call = Notification.CATEGORY_CALL.equals(n.category)
                || "android.app.Notification$CallStyle".equals(n.extras.getString("android.template", ""));
            WrappingActions actions = new WrappingActions(activity, media || call);
            if (n.actions != null) for (Notification.Action action : n.actions) {
                if (action.actionIntent == null || action.getRemoteInputs() != null) continue;
                CallActionPolicy.Kind callKind = CallActionPolicy.classify(call, action.title);
                if (callKind != CallActionPolicy.Kind.TEXT) {
                    ImageButton control = new ImageButton(activity);
                    control.setImageResource(callKind == CallActionPolicy.Kind.HANG_UP ? R.drawable.dashboard_call_end
                        : callKind == CallActionPolicy.Kind.MUTE ? R.drawable.dashboard_mic_off
                        : callKind == CallActionPolicy.Kind.SPEAKER ? R.drawable.dashboard_speaker
                        : callKind == CallActionPolicy.Kind.SPEAKER_OFF ? R.drawable.dashboard_speaker_off : R.drawable.dashboard_mic);
                    control.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(callKind == CallActionPolicy.Kind.HANG_UP ? Color.rgb(184, 27, 42) : Color.rgb(65, 72, 87));
                    control.setBackground(new android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x55FFFFFF), circle, null));
                    control.setContentDescription(action.title);
                    control.setPadding(dp(14), dp(14), dp(14), dp(14));
                    control.setOnClickListener(v -> send(action.actionIntent));
                    control.setOnLongClickListener(v -> { Toast.makeText(activity, action.title, Toast.LENGTH_SHORT).show(); return true; });
                    actions.addView(control, new ViewGroup.LayoutParams(dp(56), dp(56)));
                } else if (media && !call) {
                    android.graphics.drawable.Drawable actionIcon = null;
                    Context sourceContext = activity;
                    try { sourceContext = activity.createPackageContext(sbn.getPackageName(), 0); }
                    catch (PackageManager.NameNotFoundException ignored) { }
                    try {
                        if (Build.VERSION.SDK_INT >= 23 && action.getIcon() != null) actionIcon = action.getIcon().loadDrawable(sourceContext);
                        if (actionIcon == null && action.icon != 0) actionIcon = sourceContext.getResources().getDrawable(action.icon);
                    } catch (RuntimeException ignored) { }
                    ImageButton actionButton = new ImageButton(activity);
                    if (actionIcon != null) actionButton.setImageDrawable(actionIcon);
                    else actionButton.setImageResource(mediaFallbackIcon(action, sourceContext));
                    actionButton.setImageTintList(android.content.res.ColorStateList.valueOf(ACCENT));
                    actionButton.setContentDescription(action.title);
                    actionButton.setPadding(dp(14), dp(14), dp(14), dp(14));
                    actionButton.setBackground(interactionBackground());
                    actionButton.setOnClickListener(v -> send(action.actionIntent));
                    actionButton.setOnLongClickListener(v -> { Toast.makeText(activity, action.title, Toast.LENGTH_SHORT).show(); return true; });
                    actions.addView(actionButton, new ViewGroup.LayoutParams(dp(56), dp(56)));
                } else {
                    TextView actionButton = button(String.valueOf(action.title), () -> send(action.actionIntent));
                    actionButton.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                    actions.addView(actionButton);
                }
            }
            if (actions.getChildCount() > 0) card.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            attachNotificationGestures(card, sbn);
            addCard(card);
        }
        int sectionIndex = content.getChildCount(); renderTasks(); tasksTarget = content.getChildAt(sectionIndex); tasksTarget.setTag("section-tasks");
        sectionIndex = content.getChildCount(); renderCalendar(); calendarTarget = content.getChildAt(sectionIndex); calendarTarget.setTag("section-calendar");
        renderAllApps(); renderDock();
        Object savedAnchor = anchorTag; int savedDelta = anchorDelta;
        ScrollView targetScroll = scroll; LinearLayout targetContent = content;
        targetScroll.post(() -> {
            if (scroll != targetScroll || content != targetContent || searchMode) return;
            View anchor = savedAnchor == null ? null : targetContent.findViewWithTag(savedAnchor);
            if (focusTag != null) { View replacement = targetContent.findViewWithTag(focusTag); if (replacement != null) replacement.requestFocus(); }
            targetScroll.scrollTo(0, anchor == null ? offset : anchor.getTop() + savedDelta);
        });
    }
    private int mediaFallbackIcon(Notification.Action action, Context sourceContext) {
        String resourceName = "";
        try {
            int id = action.icon;
            if (Build.VERSION.SDK_INT >= 28 && action.getIcon() != null && action.getIcon().getType() == android.graphics.drawable.Icon.TYPE_RESOURCE)
                id = action.getIcon().getResId();
            if (id != 0) resourceName = sourceContext.getResources().getResourceEntryName(id);
        } catch (RuntimeException ignored) { }
        for (String candidate : new String[]{resourceName, String.valueOf(action.title)}) {
        String name = candidate.toLowerCase(Locale.ROOT);
        if (name.contains("stop")) return R.drawable.dashboard_media_stop;
        if (name.contains("pause")) return android.R.drawable.ic_media_pause;
        if (name.contains("previous") || name.contains("prev")) return android.R.drawable.ic_media_previous;
        if (name.contains("next")) return android.R.drawable.ic_media_next;
        if (name.contains("rewind")) return android.R.drawable.ic_media_rew;
        if (name.contains("forward")) return android.R.drawable.ic_media_ff;
        if (name.contains("play")) return android.R.drawable.ic_media_play;
        }
        // Unknown controls retain their real intent/accessible label instead of inventing a transport action.
        return android.R.drawable.ic_menu_more;
    }
    /** Right-aligned action rows wrap only when their actual measured labels do not fit. */
    private final class WrappingActions extends ViewGroup {
        private final List<List<View>> lines = new ArrayList<>();
        private final List<Integer> widths = new ArrayList<>();
        private final List<Integer> heights = new ArrayList<>();
        private final boolean centered;
        WrappingActions(Context context, boolean centered) { super(context); this.centered = centered; }
        @Override protected LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            lines.clear(); widths.clear(); heights.clear();
            int available = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            List<View> line = new ArrayList<>(); int lineWidth = 0, lineHeight = 0, totalHeight = 0, widest = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); if (child.getVisibility() == GONE) continue;
                LayoutParams lp = child.getLayoutParams();
                int childWidth = lp.width >= 0 ? Math.min(lp.width, available) : available;
                child.measure(MeasureSpec.makeMeasureSpec(childWidth, lp.width >= 0 ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(lp.height >= 0 ? lp.height : 0, lp.height >= 0 ? MeasureSpec.EXACTLY : MeasureSpec.UNSPECIFIED));
                int next = child.getMeasuredWidth() + (line.isEmpty() ? 0 : dp(8));
                if (!line.isEmpty() && lineWidth + next > available) {
                    lines.add(line); widths.add(lineWidth); heights.add(lineHeight);
                    widest = Math.max(widest, lineWidth); totalHeight += lineHeight + dp(4);
                    line = new ArrayList<>(); lineWidth = 0; lineHeight = 0; next = child.getMeasuredWidth();
                }
                line.add(child); lineWidth += next; lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
            }
            if (!line.isEmpty()) {
                lines.add(line); widths.add(lineWidth); heights.add(lineHeight);
                widest = Math.max(widest, lineWidth); totalHeight += lineHeight;
            }
            setMeasuredDimension(resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec),
                resolveSize(totalHeight + getPaddingTop() + getPaddingBottom(), heightSpec));
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int y = getPaddingTop();
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            for (int i = 0; i < lines.size(); i++) {
                int lineLeft = centered ? getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - widths.get(i)) / 2
                    : (rtl ? getPaddingLeft() : getWidth() - getPaddingRight() - widths.get(i));
                int x = rtl ? lineLeft + widths.get(i) : lineLeft;
                for (View child : lines.get(i)) {
                    int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                    if (rtl) { child.layout(x - w, y, x, y + h); x -= w + dp(8); }
                    else { child.layout(x, y, x + w, y + h); x += w + dp(8); }
                }
                y += heights.get(i) + dp(4);
            }
        }
    }
    private void attachNotificationGestures(View card, StatusBarNotification notification) {
        PendingIntent intent = notification.getNotification().contentIntent;
        if (intent != null) card.setOnClickListener(v -> send(intent));
        if (intent != null || notification.isClearable()) card.setFocusable(true);
        if (!notification.isClearable()) return;
        androidx.core.view.ViewCompat.addAccessibilityAction(card, "Dismiss notification", (view, arguments) -> {
            expandedNotifications.remove(notification.getKey());
            DashboardNotifications.dismiss(notification.getKey()); return true;
        });
    }
    private final class NotificationCard extends LinearLayout {
        private final StatusBarNotification notification;
        private float startX, startY;
        private boolean horizontal, vertical;
        private final int slop;
        NotificationCard(StatusBarNotification notification) {
            super(activity); this.notification = notification; setOrientation(VERTICAL);
            slop = android.view.ViewConfiguration.get(activity).getScaledTouchSlop();
            setClickable(true);
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (!notification.isClearable()) return super.onInterceptTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                notificationTouchOwner = this;
                animate().cancel(); setTranslationX(0); setAlpha(1f);
                startX = event.getRawX(); startY = event.getRawY(); horizontal = false; vertical = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                classify(event);
                if (horizontal) return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!horizontal) finishGesture();
            }
            return super.onInterceptTouchEvent(event);
        }
        private void finishGesture() {
            if (notificationTouchOwner == this) notificationTouchOwner = null;
            root.removeCallbacks(refreshHome); root.postDelayed(refreshHome, 400);
        }
        private void classify(MotionEvent event) {
            if (horizontal || vertical) return;
            float dx = Math.abs(event.getRawX() - startX), dy = Math.abs(event.getRawY() - startY);
            if (dx > slop && dx > dy * 1.5f) horizontal = true;
            else if (dy > slop) { vertical = true; getParent().requestDisallowInterceptTouchEvent(false); finishGesture(); }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!notification.isClearable()) return super.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) classify(event);
            if (horizontal) {
                setPressed(false);
                float drag = event.getRawX() - startX;
                setTranslationX(drag);
                setAlpha(Math.max(0.35f, 1f - Math.abs(drag) / Math.max(1, getWidth()) * 0.65f));
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (NotificationSwipePolicy.shouldDismiss(drag, getWidth(), dp(72))) {
                        animate().translationX(Math.copySign(getWidth(), drag)).alpha(0f).setDuration(180).withEndAction(() -> {
                            expandedNotifications.remove(notification.getKey()); DashboardNotifications.dismiss(notification.getKey()); finishGesture();
                            // Restore if the provider rejects dismissal; a normal refresh removes accepted dismissals.
                            postDelayed(() -> { if (isAttachedToWindow()) animate().translationX(0).alpha(1f).setDuration(160).start(); }, 400);
                        }).start();
                    } else animate().translationX(0).alpha(1f).setDuration(160).withEndAction(this::finishGesture).start();
                } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    animate().translationX(0).alpha(1f).setDuration(160).withEndAction(this::finishGesture).start();
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) finishGesture();
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick() { return super.performClick(); }
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
            for (CalendarEntry event : calendarLines) {
                if (event.color == null) { card.addView(text(event.label, textSp, false)); continue; }
                LinearLayout eventRow = row();
                eventRow.setPadding(dp(4), dp(8), dp(4), dp(8));
                View colorBar = new View(activity);
                GradientDrawable capsule = background(event.color); capsule.setCornerRadius(dp(3));
                colorBar.setBackground(capsule);
                colorBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams barLayout = new LinearLayout.LayoutParams(dp(6), -1);
                barLayout.setMargins(0, dp(6), dp(8), dp(6));
                eventRow.addView(colorBar, barLayout);
                eventRow.addView(text(event.label, textSp, false), new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(eventRow, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        addCard(card);
    }
    private static final class CalendarEntry {
        final String label;
        final Integer color;
        CalendarEntry(String label, Integer color) { this.label = label; this.color = color; }
    }
    private void loadCalendar() {
        if (destroyed || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        calendarExecutor.execute(() -> {
            List<CalendarEntry> lines = new ArrayList<>();
            try (Cursor c = CalendarContract.Instances.query(activity.getContentResolver(), new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.DISPLAY_COLOR, CalendarContract.Instances.CALENDAR_COLOR}, System.currentTimeMillis(), System.currentTimeMillis() + 7L * 86400000)) {
                int count = 0;
                while (c != null && c.moveToNext() && count++ < 5) {
                    lines.add(new CalendarEntry(c.getString(0) + "\n" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(c.getLong(1))), CalendarColorPolicy.resolve(c.isNull(2) ? null : c.getInt(2), c.isNull(3) ? null : c.getInt(3), ACCENT)));
                }
                if (lines.isEmpty()) lines.add(new CalendarEntry("No events in the next seven days", null));
            } catch (SecurityException | IllegalArgumentException e) { lines.add(new CalendarEntry("Calendar unavailable", null)); }
            root.post(() -> { if (!destroyed) { calendarLines = lines; if (!searchMode) scheduleHomeRefresh(); } });
        });
    }
    private void renderDock() {
        if (dockView != null) root.removeView(dockView);
        LinearLayout dockContainer = row(); dockView = dockContainer;
        HorizontalScrollView dockScroll = new HorizontalScrollView(activity); LinearLayout dock = row(); dockScroll.addView(dock);
        dockContainer.addView(dockScroll, new LinearLayout.LayoutParams(0, -2, 1));
        Set<String> used = new HashSet<>(); int limit = DashboardPolicy.favoriteCount(activity.getResources().getConfiguration().screenWidthDp);
        for (Pojo pojo : data().getFavorites()) {
            if (!(pojo instanceof AppPojo) || pojo.isDisabled() || used.size() >= limit) continue;
            used.add(pojo.id); dock.addView(appIcon(pojo));
        }
        if (used.isEmpty()) dock.addView(button("Choose favorites", this::showSearch));
        dock.addView(text("│", 30, false)); int count = 0;
        for (AppPojo pojo : recentApps(8)) {
            if (used.contains(pojo.id)) continue;
            used.add(pojo.id); dock.addView(appIcon(pojo)); if (++count == 2) break;
        }
        root.addView(dockContainer);
    }
    private ImageButton iconButton(int icon, String description, Runnable action) {
        ImageButton button = new ImageButton(activity); button.setImageResource(icon);
        button.setImageTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        button.setContentDescription(description); button.setPadding(dp(16), dp(16), dp(16), dp(16));
        button.setBackground(interactionBackground()); button.setOnClickListener(v -> action.run());
        return button;
    }
    private android.graphics.drawable.Drawable interactionBackground() {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, background(0x55D8BEFF));
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55D8BEFF), states, background(Color.WHITE));
    }
    private List<AppPojo> recentApps(int count) {
        List<AppPojo> recent = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (ValuedHistoryRecord record : DBHelper.getHistory(activity, 100, HistoryMode.RECENCY)) {
            Pojo pojo = data().getPojo(record.record);
            if (!(pojo instanceof AppPojo)) continue;
            AppPojo app = (AppPojo) pojo;
            if (app.isDisabled() || app.isExcluded() || app.isExcludedFromHistory() || !seen.add(app.id)) continue;
            recent.add(app); if (recent.size() == count) break;
        }
        return recent;
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
        searchMode = true; navigatingTag = false; root.removeAllViews(); dockView = null;
        LinearLayout header = row();
        query = new EditText(activity);
        query.setSingleLine(true); query.setTextSize(textSp); query.setTextColor(Color.WHITE); query.setHintTextColor(ACCENT); query.setHint("Search apps and more");
        query.setContentDescription("Search apps and more");
        header.addView(query, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(header);
        searchFrame = new FrameLayout(activity); LinearLayout body = row();
        scroll = new ScrollView(activity); content = column(); scroll.addView(content);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        alphabetRail = null;
        searchFrame.addView(body, new FrameLayout.LayoutParams(-1, -1));
        letterBubble = text("", textSp + 24, true); letterBubble.setTextColor(BG); letterBubble.setBackground(background(ACCENT));
        letterBubble.setGravity(Gravity.CENTER); letterBubble.setVisibility(View.GONE); letterBubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams bubbleLayout = new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.END | Gravity.CENTER_VERTICAL); bubbleLayout.setMarginEnd(dp(64));
        searchFrame.addView(letterBubble, bubbleLayout);
        root.addView(searchFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                navigatingTag = false; activity.searchEditText.setText(s);
                if (s.length() == 0) { activity.showHistory(); renderSearchResults(); }

            }
            public void afterTextChanged(Editable s) { }
        });
        activity.searchEditText.setText(""); activity.showHistory(); renderSearchResults();
        query.requestFocus();
        EditText focusedQuery = query;
        query.post(() -> {
            if (searchMode && query == focusedQuery && focusedQuery.isAttachedToWindow())
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(focusedQuery, InputMethodManager.SHOW_IMPLICIT);
        });
    }
    private boolean isWideAppLayout() {
        int width, height;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect bounds = activity.getWindowManager().getCurrentWindowMetrics().getBounds();
            width = bounds.width(); height = bounds.height();
        } else {
            android.graphics.Point size = new android.graphics.Point();
            activity.getWindowManager().getDefaultDisplay().getSize(size);
            width = size.x; height = size.y;
        }
        // Window bounds do not shrink for the IME and their ratio is independent of display scaling.
        return DashboardPolicy.isWideAppLayout(width, height);
    }
    private String columnPreferenceKey() { return isWideAppLayout() ? "app-columns-wide" : "app-columns-narrow"; }
    private int appColumns() {
        boolean wide = isWideAppLayout();
        return DashboardPolicy.columnsForLayout(wide, preferences.getInt(columnPreferenceKey(), 0));
    }
    private void renderAllApps() {
        content.addView(text("All apps", textSp, true));
        List<AppPojo> available = data().getApplicationsWithoutExcluded();
        if (available == null) { content.addView(text("Loading apps…", textSp, false)); return; }
        List<AppPojo> apps = new ArrayList<>(available);
        java.text.Collator collator = java.text.Collator.getInstance();
        apps.sort((a, b) -> collator.compare(a.getName(), b.getName()));
        Map<Character, List<Result<?>>> groups = new TreeMap<>();
        for (AppPojo app : apps) {
            if (app.isDisabled()) continue;
            char letter = DashboardPolicy.initialLetter(app.getName());
            if (letter == 0) letter = '#';
            groups.computeIfAbsent(letter, key -> new ArrayList<>()).add(Result.fromPojo(activity, app));
        }
        for (Map.Entry<Character, List<Result<?>>> group : groups.entrySet()) {
            char letter = group.getKey();
            TextView heading = text(String.valueOf(letter), textSp + 4, true);
            heading.setTag("app-letter:" + letter);
            if (letter == '#') heading.setContentDescription("Numbers, symbols and other alphabets");
            androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
            content.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            if (letter != '#') alphabetTargets.put(letter, heading);
            homeLastHeading = heading;
            addAppGrid(group.getValue(), false);
        }
        if (homeLastHeading != null) {
            homeAppTail = new View(activity); homeAppTail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // Start with ample space so a refresh cannot clamp the final heading before measurement.
            content.addView(homeAppTail, new LinearLayout.LayoutParams(-1, Math.max(0, scroll.getHeight())));
            homeAppTail.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateHomeTailPadding());
            root.post(this::updateHomeTailPadding);
        }
    }
    private void updateHomeTailPadding() {
        if (searchMode || homeAppTail == null || homeLastHeading == null || !homeAppTail.isAttachedToWindow()) return;
        int lastGroupHeight = content.getHeight() - homeLastHeading.getTop() - homeAppTail.getHeight();
        int needed = Math.max(0, scroll.getHeight() - lastGroupHeight);
        if (homeAppTail.getLayoutParams().height != needed) {
            homeAppTail.getLayoutParams().height = needed; homeAppTail.requestLayout();
        }
    }

    private void renderSearchResults() {
        if (!searchMode || content == null || query == null) return;
        content.removeAllViews(); alphabetTargets.clear(); renderedColumns = appColumns();
        letterBubble.setVisibility(View.GONE);
        if (query.length() == 0 && !navigatingTag) {
            List<AppPojo> recent = recentApps(6);
            if (recent.isEmpty()) content.addView(text("Type to search apps and more", textSp, false));
            else {
                content.addView(text("Recent apps", textSp, true));
                List<Result<?>> results = new ArrayList<>();
                for (AppPojo app : recent) results.add(Result.fromPojo(activity, app));
                addAppGrid(results, false);
            }
        } else {
            List<Result<?>> results = new ArrayList<>();
            for (int i = activity.adapter.getCount() - 1; i >= 0; i--) results.add(activity.adapter.getItem(i));
            addAppGrid(results, false);
        }
    }
    private void addAppGrid(List<Result<?>> results, boolean indexLetters) {
        LinearLayout gridRow = null; int column = 0;
        for (Result<?> result : results) {
            if (column == 0) { gridRow = row(); gridRow.setTag("app-row:" + result.getPojo().id); content.addView(gridRow, new LinearLayout.LayoutParams(-1, -2)); }
            View item = appResultView(result);
            gridRow.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
            if (indexLetters) {
                char letter = DashboardPolicy.initialLetter(result.getPojo().getName());
                if (letter != 0 && !alphabetTargets.containsKey(letter)) alphabetTargets.put(letter, gridRow);
            }
            column = (column + 1) % renderedColumns;
        }
        if (gridRow != null && column != 0) gridRow.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1));
    }
    private View appResultView(Result<?> result) {
        LinearLayout item = row(); item.setTag("app-item:" + result.getPojo().id); ImageView icon = new ImageView(activity); icon.setImageDrawable(result.getDrawable(activity));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.setPadding(dp(4), dp(4), dp(4), dp(4)); item.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        FadingLabel label = new FadingLabel(activity); label.setText(result.getPojo().getName()); label.setTextSize(textSp); label.setTextColor(Color.WHITE);
        label.setPadding(dp(8), dp(6), dp(8), dp(6)); label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        item.setMinimumHeight(dp(64)); item.setFocusable(true); item.setContentDescription(result.getPojo().getName());
        item.setBackground(interactionBackground());
        item.setOnClickListener(v -> {
            navigatingTag = result.getPojo() instanceof TagDummyPojo;
            result.launch(activity, v, activity); if (!navigatingTag) showHome();
        });
        item.setOnLongClickListener(v -> { Pojo pojo = result.getPojo(); boolean favorite = data().hasFavorite(pojo.id);
            new AlertDialog.Builder(activity).setTitle(pojo.getName()).setItems(new String[]{favorite ? "Remove favorite" : "Add favorite"}, (d, which) -> {
                if (favorite) data().removeFromFavorites(pojo.id); else data().addToFavorites(pojo.id);
            }).show(); return true;
        }); return item;
    }
    private void showAlphabet(char letter) {
        if (searchMode) return;
        letterBubble.setText(String.valueOf(letter)); letterBubble.setVisibility(View.VISIBLE);
        View target = alphabetTargets.get(letter);
        if (target == null) {
            for (char next = letter; next <= 'Z'; next++) if (alphabetTargets.containsKey(next)) { target = alphabetTargets.get(next); break; }
        }
        if (target != null && target.isAttachedToWindow()) scroll.scrollTo(0, target.getTop());
        else scroll.scrollTo(0, content.getHeight());
    }
    private void open(Intent intent) { try { activity.startActivity(intent); } catch (android.content.ActivityNotFoundException | SecurityException e) { Toast.makeText(activity, "This action is unavailable on this device", Toast.LENGTH_LONG).show(); } }
    /** Called only by explicit card/action clicks while this launcher is resumed. */
    private void send(PendingIntent intent) {
        if (!NotificationLaunchPolicy.canSend(resumed, destroyed, activity.isFinishing())) return;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= 36) {
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
                } else {
                    // API 34–35 lacks ALLOW_IF_VISIBLE. The lifecycle guard above limits this to foreground clicks.
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                }
                intent.send(activity, 0, null, null, null, null, options.toBundle());
            } else {
                intent.send();
            }
        } catch (PendingIntent.CanceledException e) {
            Toast.makeText(activity, "This notification has expired", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, "Android could not open this notification", Toast.LENGTH_SHORT).show();
        }
    }
    private final class FeedScrollView extends ScrollView {
        FeedScrollView(Context context) { super(context); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) feedTouching = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) feedTouching = false;
            feedLastMotion = android.os.SystemClock.uptimeMillis();
            return super.dispatchTouchEvent(event);
        }
        @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
            super.onScrollChanged(x, y, oldX, oldY);
            if (y != oldY) feedLastMotion = android.os.SystemClock.uptimeMillis();
            if (!searchMode && scroll == this && homeRail != null) homeRail.invalidate();
        }
    }
    /** Own the entire section/alphabet pointer stream, while leaving Search as a normal button. */
    private final class ContinuousRail extends LinearLayout {
        private int pointer = -1, selected = -1;
        private boolean scrubbing;
        private float grabOffset;
        private final android.graphics.Paint thumbPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ContinuousRail(Context context) { super(context); setOrientation(VERTICAL); }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (getChildCount() < 5 || event.getY() >= getChildAt(4).getTop()) return super.dispatchTouchEvent(event);
                float thumb = indicatorY();
                scrubbing = Math.abs(event.getY() - thumb) <= dp(22) && Math.abs(event.getX() - getWidth() / 2f) <= dp(24);
                grabOffset = scrubbing ? event.getY() - thumb : 0;
                pointer = event.getPointerId(0); selected = -1; railDragging = true;
                scroll.fling(0); scroll.computeScroll(); // Settle the zero-velocity frame before direct slider jumps.
                getParent().requestDisallowInterceptTouchEvent(true);
                if (scrubbing) scrubTo(event.getY() - grabOffset); else selectAt(event.getY()); return true;
            }
            if (pointer == -1) return super.dispatchTouchEvent(event);
            int index = event.findPointerIndex(pointer);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
                || (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer) || index < 0) {
                pointer = -1; railDragging = false; getParent().requestDisallowInterceptTouchEvent(false);
                if (letterBubble != null) letterBubble.setVisibility(View.GONE);
                root.removeCallbacks(refreshHome); root.postDelayed(refreshHome, 200);
                if (action == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) { if (scrubbing) scrubTo(event.getY(index) - grabOffset); else selectAt(event.getY(index)); }
            return true;
        }
        private float[][] anchors() {
            List<Float> feed = new ArrayList<>(), rail = new ArrayList<>();
            View[] sections = {notificationTarget, tasksTarget, calendarTarget};
            for (int i = 0; i < sections.length; i++) {
                if (sections[i] != null && sections[i].isAttachedToWindow()) {
                    feed.add((float) sections[i].getTop());
                    rail.add((getChildAt(i).getTop() + getChildAt(i).getBottom()) / 2f);
                }
            }
            if (getChildCount() >= 4) {
                View letters = getChildAt(3); int inset = alphabetInset(letters.getHeight());
                for (char letter = 'A'; letter <= 'Z'; letter++) {
                    View heading = alphabetTargets.get(letter);
                    if (heading != null && heading.isAttachedToWindow()) {
                        feed.add((float) heading.getTop());
                        rail.add(letters.getTop() + inset + alphabetRange(letters.getHeight()) * (letter - 'A') / 25f);
                    }
                }
            }
            int end = Math.max(0, content.getHeight() - scroll.getHeight());
            if (!feed.isEmpty() && end > feed.get(feed.size() - 1) && getChildCount() >= 4) {
                View letters = getChildAt(3);
                feed.add((float) end); rail.add((float) (letters.getBottom() - alphabetInset(letters.getHeight())));
            }
            float[] contentPositions = new float[feed.size()], railPositions = new float[rail.size()];
            for (int i = 0; i < feed.size(); i++) { contentPositions[i] = feed.get(i); railPositions[i] = rail.get(i); }
            return new float[][]{contentPositions, railPositions};
        }
        private float indicatorY() {
            float[][] points = anchors();
            return RailScrollMapping.interpolate(scroll.getScrollY(), points[0], points[1]);
        }
        private void scrubTo(float y) {
            float[][] points = anchors();
            scroll.scrollTo(0, Math.round(RailScrollMapping.interpolate(y, points[1], points[0])));
            View letters = getChildAt(3);
            int next = RailNavigationPolicy.index(indicatorY(), getChildAt(0).getBottom(), getChildAt(1).getBottom(), letters.getTop(), letters.getBottom() - dp(24), alphabetInset(letters.getHeight()));
            if (next >= 3) { letterBubble.setText(String.valueOf((char) ('A' + next - 3))); letterBubble.setVisibility(View.VISIBLE); }
            else letterBubble.setVisibility(View.GONE);
            invalidate();
        }
        @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
            super.dispatchDraw(canvas);
            if (searchMode || getChildCount() < 5 || notificationTarget == null || !notificationTarget.isAttachedToWindow()) return;
            float y = indicatorY(), x = getWidth() / 2f, radius = Math.min(dp(20), getWidth() / 2f - dp(3));
            thumbPaint.setColor(ACCENT); thumbPaint.setColorFilter(null);
            canvas.drawCircle(x, y, radius, thumbPaint);
            View letters = getChildAt(3);
            if (y >= letters.getTop()) {
                int index = DashboardPolicy.letterIndex(y - letters.getTop() - alphabetInset(letters.getHeight()), alphabetRange(letters.getHeight()));
                thumbPaint.setColor(BG); thumbPaint.setTextSize(dp(22)); thumbPaint.setTypeface(Typeface.DEFAULT_BOLD); thumbPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
                canvas.drawText(String.valueOf((char) ('A' + index)), x, y - (thumbPaint.ascent() + thumbPaint.descent()) / 2f, thumbPaint);
            } else {
                int save = canvas.save(); android.graphics.Path clip = new android.graphics.Path(); clip.addCircle(x, y, radius, android.graphics.Path.Direction.CW); canvas.clipPath(clip);
                android.graphics.Paint inverse = new android.graphics.Paint(); inverse.setColorFilter(new android.graphics.PorterDuffColorFilter(BG, android.graphics.PorterDuff.Mode.SRC_IN));
                int layer = canvas.saveLayer(x - radius, y - radius, x + radius, y + radius, inverse);
                super.dispatchDraw(canvas); canvas.restoreToCount(layer); canvas.restoreToCount(save);
            }
        }
        private void selectAt(float y) {
            View letters = getChildAt(3);
            int next = RailNavigationPolicy.index(y, getChildAt(0).getBottom(), getChildAt(1).getBottom(), letters.getTop(), letters.getBottom() - dp(24), alphabetInset(letters.getHeight()));
            if (next == selected) return;
            selected = next;
            if (next >= 3) { showAlphabet((char) ('A' + next - 3)); return; }
            View target = next == 0 ? notificationTarget : next == 1 ? tasksTarget : calendarTarget;
            if (target != null && target.isAttachedToWindow()) scroll.scrollTo(0, target.getTop());
            letterBubble.setVisibility(View.GONE);
        }
        @Override public boolean performClick() { return super.performClick(); }
    }
    private int alphabetInset(int height) { return Math.round(Math.min(dp(28), height / 4f) / 2f + dp(4)); }
    private int alphabetRange(int height) { return Math.max(1, height - 2 * alphabetInset(height) - dp(24)); }
    /** A proportional rail never clips A–Z at high Android font scaling. Selected letter is enlarged in content. */
    private final class AlphabetRail extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private int selected;
        AlphabetRail(Context context) {
            super(context); setBackgroundColor(Color.TRANSPARENT); setFocusable(true); setClickable(true);
            setContentDescription("Alphabet index. Swipe up or down to jump to a letter in the app list.");
            paint.setColor(ACCENT); paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float labelSize = Math.min(dp(28), getHeight() / 4f);
            paint.setTextSize(labelSize);
            float top = labelSize / 2f + dp(4), bottom = Math.max(top, getHeight() - top - dp(24));
            float baselineOffset = -(paint.ascent() + paint.descent()) / 2f;
            canvas.drawText("A", getWidth() / 2f, top + baselineOffset, paint);
            canvas.drawText("Z", getWidth() / 2f, bottom + baselineOffset, paint);
            for (int i = 1; i <= 5; i++) canvas.drawCircle(getWidth() / 2f,
                top + (bottom - top) * i / 6f, dp(2), paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                selected = DashboardPolicy.letterIndex(event.getY() - alphabetInset(getHeight()), alphabetRange(getHeight())); showAlphabet((char) ('A' + selected)); return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (letterBubble != null) letterBubble.setVisibility(View.GONE); performClick(); return true;
            }
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info); info.setScrollable(true);
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
        @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
            if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                selected = Math.max(0, Math.min(25, selected + (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1)));
                showAlphabet((char) ('A' + selected)); announceForAccessibility(String.valueOf((char) ('A' + selected)));
                TextView bubble = letterBubble; root.postDelayed(() -> bubble.setVisibility(View.GONE), 1200); return true;
            }
            return super.performAccessibilityAction(action, arguments);
        }
    }
}
