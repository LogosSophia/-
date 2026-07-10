package com.logos.clipboarddemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FloatingClipboardService extends Service {
    private static final int NOTIFICATION_ID = 7204;
    private static final String CHANNEL_ID = "logos_clipboard_overlay";
    private static final String POSITION_PREF = "overlay_position";

    private WindowManager wm;
    private EdgeHandleView handle;
    private WindowManager.LayoutParams handleParams;
    private View panel;
    private LinearLayout listBox;
    private EditText search;
    private TextView categoryButton;
    private String mode = "all";
    private String category = "全部";
    private boolean showing;
    private float downX, downY;
    private int startY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fadeHandle = new Runnable() {
        @Override public void run() {
            if (handle != null && !showing) handle.animate().alpha(0.34f).setDuration(260).start();
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        AppPrefs.setOverlayEnabled(this, true);
        showHandle();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (handle == null && wm != null) showHandle();
        return START_STICKY;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showHandle() {
        if (handle != null) return;
        final EdgeHandleView view = new EdgeHandleView(this);
        handleParams = new WindowManager.LayoutParams(dp(30), dp(100), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        handleParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        handleParams.x = 0;
        SharedPreferences sp = getSharedPreferences(POSITION_PREF, MODE_PRIVATE);
        handleParams.y = sp.getInt("y", -dp(150));
        handle = view;
        handle.setAlpha(0.68f);
        handle.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(fadeHandle);
                        downX = event.getRawX(); downY = event.getRawY(); startY = handleParams.y;
                        handle.animate().alpha(1f).setDuration(90).start();
                        handle.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dy) > dp(4)) {
                            handleParams.y = startY + (int) dy;
                            try { wm.updateViewLayout(handle, handleParams); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float dx = event.getRawX() - downX;
                        float totalDy = event.getRawY() - downY;
                        boolean tap = Math.abs(dx) < dp(12) && Math.abs(totalDy) < dp(12);
                        boolean swipe = dx < -dp(24) && Math.abs(totalDy) < dp(70);
                        getSharedPreferences(POSITION_PREF, MODE_PRIVATE).edit().putInt("y", handleParams.y).apply();
                        if (tap || swipe) showPanel(); else scheduleFade();
                        return true;
                    default: return false;
                }
            }
        });
        wm.addView(handle, handleParams);
        scheduleFade();
    }

    private void scheduleFade() {
        handler.removeCallbacks(fadeHandle);
        handler.postDelayed(fadeHandle, 1200L);
    }

    private void showPanel() {
        if (showing) return;
        showing = true;
        handler.removeCallbacks(fadeHandle);
        if (handle != null) handle.animate().alpha(0f).setDuration(120).start();
        saveCurrentClipboardSilently();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        final LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16),dp(12),dp(16),dp(14));
        sheet.setBackground(panelBackground());
        sheet.setElevation(dp(14));
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(-1,-1);
        sheetLp.setMargins(dp(8),dp(56),dp(8),dp(56));
        root.addView(sheet, sheetLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(header, new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0,-1,1f));
        TextView title = text("剪切板", 21, Color.rgb(24,24,26));
        titles.addView(title, new LinearLayout.LayoutParams(-1,0,0.65f));
        TextView status = text(ClipboardAccessibilityService.isRunning() ? "自动收集与直接粘贴已连接" : "开启无障碍后可自动收集和直接粘贴", 10, Color.rgb(130,130,138));
        titles.addView(status, new LinearLayout.LayoutParams(-1,0,0.35f));
        TextView close = text("×", 28, Color.rgb(92,92,98));
        close.setGravity(Gravity.CENTER); close.setBackground(iconBackground());
        header.addView(close, new LinearLayout.LayoutParams(dp(38),dp(38)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hidePanelAnimated(); }
        });

        HorizontalScrollView modeScroll = new HorizontalScrollView(this);
        modeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modeScroll.addView(modes);
        LinearLayout.LayoutParams modesLp = new LinearLayout.LayoutParams(-1,dp(44));
        modesLp.setMargins(0,dp(4),0,dp(4));
        sheet.addView(modeScroll,modesLp);
        addMode(modes,"最近","all"); addMode(modes,"置顶","pinned");
        addMode(modes,"常用","common"); addMode(modes,"收藏","favorite");
        categoryButton = chip("分类：" + category);
        LinearLayout.LayoutParams categoryLp = new LinearLayout.LayoutParams(-2,dp(36));
        categoryLp.setMargins(dp(5),dp(2),0,0);
        modes.addView(categoryButton,categoryLp);
        categoryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleCategory(); }
        });

        search = new EditText(this);
        search.setHint("全文搜索"); search.setHintTextColor(Color.rgb(150,150,156));
        search.setTextColor(Color.rgb(28,28,30)); search.setTextSize(16); search.setSingleLine(true);
        search.setPadding(dp(14),0,dp(14),0); search.setBackground(searchBackground());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1,dp(46));
        searchLp.setMargins(0,dp(4),0,dp(10)); sheet.addView(search,searchLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this); listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0,dp(2),0,dp(8)); scroll.addView(listBox);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        WindowManager.LayoutParams panelParams = new WindowManager.LayoutParams(dp(350),-1,overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.RIGHT | Gravity.TOP;
        panel = root;
        wm.addView(panel,panelParams);
        refresh();
        sheet.setAlpha(0f); sheet.setTranslationX(dp(350));
        sheet.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void addMode(LinearLayout row, String label, final String value) {
        TextView button = chip(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2,dp(36));
        lp.setMargins(0,dp(2),dp(5),0); row.addView(button,lp);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mode = value; refresh(); }
        });
    }

    private void cycleCategory() {
        List<String> categories = ClipStore.categories(this);
        int index = categories.indexOf(category);
        category = categories.get((index + 1) % categories.size());
        categoryButton.setText("分类：" + category);
        refresh();
    }

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews();
        List<ClipItem> items = ClipStore.query(this, search == null ? "" : search.getText().toString(), mode, category);
        if (items.isEmpty()) {
            TextView empty = text("没有找到内容",15,Color.rgb(142,142,147));
            empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(50),0,dp(50));
            listBox.addView(empty,new LinearLayout.LayoutParams(-1,-2)); return;
        }
        for (final ClipItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(12),dp(14),dp(10));
            card.setBackground(cardBackground(item.pinned)); card.setElevation(dp(1));
            TextView title = text(prefix(item) + safe(item.title),15,Color.rgb(28,28,30));
            title.setMaxLines(1); card.addView(title,new LinearLayout.LayoutParams(-1,-2));
            String body = item.sensitive && AppPrefs.maskSensitive(this) ? "••••••  敏感内容已隐藏" : preview(item.content);
            TextView content = text(body,14,Color.rgb(55,55,60));
            content.setLineSpacing(dp(3),1.02f); content.setMaxLines(5);
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1,-2); bodyLp.setMargins(0,dp(6),0,0);
            card.addView(content,bodyLp);
            TextView meta = text(meta(item),10,Color.rgb(148,148,155)); meta.setPadding(0,dp(7),0,0);
            card.addView(meta,new LinearLayout.LayoutParams(-1,-2));
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { useItem(item); }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    ClipStore.togglePinned(FloatingClipboardService.this,item.id);
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); refresh(); return true;
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10));
            listBox.addView(card,lp);
        }
    }

    private void useItem(final ClipItem item) {
        ClipStore.markUsed(this,item.id);
        final String text = item.content;
        hidePanelImmediate();
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                boolean pasted = AppPrefs.directPaste(FloatingClipboardService.this)
                        && ClipboardAccessibilityService.pasteTextGlobal(text);
                if (!pasted) {
                    ClipboardTools.copy(FloatingClipboardService.this,text);
                    Toast.makeText(FloatingClipboardService.this,"已复制",Toast.LENGTH_SHORT).show();
                }
            }
        },220L);
    }

    private void saveCurrentClipboardSilently() {
        if (!AppPrefs.autoCapture(this)) return;
        String text = ClipboardTools.read(this);
        if (text != null && !text.trim().isEmpty() && !ClipboardTools.isRecentOwnWrite(text))
            ClipStore.add(this,text,"system_clipboard");
    }

    private void hidePanelAnimated() {
        if (!showing || panel == null) return;
        final View removing = panel;
        View sheet = ((FrameLayout) removing).getChildAt(0);
        sheet.animate().alpha(0f).translationX(dp(350)).setDuration(170)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                    @Override public void run() {
                        try { wm.removeView(removing); } catch (Exception ignored) { }
                        if (panel == removing) panel = null;
                        showing = false; restoreHandle();
                    }
                }).start();
    }

    private void hidePanelImmediate() {
        if (panel != null) { try { wm.removeView(panel); } catch (Exception ignored) { } panel = null; }
        showing = false; restoreHandle();
    }

    private void restoreHandle() {
        if (handle != null) { handle.animate().alpha(0.68f).setDuration(130).start(); scheduleFade(); }
    }

    private String prefix(ClipItem item) {
        StringBuilder s = new StringBuilder();
        if (item.pinned) s.append("★ "); if (item.common) s.append("常用 · ");
        if (item.favorite) s.append("收藏 · "); if (item.sensitive) s.append("私密 · ");
        return s.toString();
    }

    private String meta(ClipItem item) {
        StringBuilder s = new StringBuilder(safe(item.category));
        if (item.tags != null && !item.tags.trim().isEmpty()) s.append(" · #").append(item.tags.replace(","," #"));
        if (item.usageCount > 0) s.append(" · ").append(item.usageCount).append(" 次");
        return s.toString();
    }

    private String preview(String text) {
        String value = safe(text).trim(); return value.length() > 240 ? value.substring(0,240) + "…" : value;
    }
    private String safe(String value) { return value == null ? "" : value; }

    private TextView chip(String label) {
        TextView v = text(label,12,Color.rgb(68,68,74)); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12),0,dp(12),0); v.setBackground(chipBackground()); return v;
    }
    private TextView text(String value,int size,int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.argb(252,248,248,250));
        bg.setCornerRadius(dp(24)); bg.setStroke(dp(1),Color.argb(25,0,0,0)); return bg;
    }
    private GradientDrawable searchBackground() {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(233,233,237)); bg.setCornerRadius(dp(15)); return bg;
    }
    private GradientDrawable chipBackground() {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(239,239,243)); bg.setCornerRadius(dp(18)); return bg;
    }
    private GradientDrawable iconBackground() {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(238,238,242)); bg.setCornerRadius(dp(19)); return bg;
    }
    private GradientDrawable cardBackground(boolean pinned) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(pinned ? Color.rgb(255,250,240) : Color.WHITE);
        bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),pinned ? Color.rgb(238,215,170) : Color.rgb(229,229,233)); return bg;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,"剪切板侧边栏",NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("保持用户启用的剪切板侧边栏运行"); channel.setShowBadge(false);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this,MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this,CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("剪切板侧边栏已启用")
                .setContentText("点击管理剪切板").setContentIntent(pending).setOngoing(true).build();
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (panel != null) { try { wm.removeView(panel); } catch (Exception ignored) { } }
        if (handle != null) { try { wm.removeView(handle); } catch (Exception ignored) { } }
        panel = null; handle = null; showing = false;
        super.onDestroy();
    }

    private static final class EdgeHandleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        EdgeHandleView(Context context) { super(context); paint.setStrokeCap(Paint.Cap.ROUND); setBackgroundColor(Color.TRANSPARENT); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            paint.setColor(Color.argb(190,116,116,122)); paint.setStrokeWidth(3.6f * density);
            float x = getWidth() - 3.4f * density; float cy = getHeight() / 2f;
            canvas.drawLine(x,cy - 23f*density,x,cy + 23f*density,paint);
        }
    }
}
