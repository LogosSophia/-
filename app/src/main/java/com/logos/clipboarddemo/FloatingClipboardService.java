package com.logos.clipboarddemo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FloatingClipboardService extends Service {
    private WindowManager wm;
    private EdgeHandleView handle;
    private View panel;
    private LinearLayout listBox;
    private EditText search;
    private boolean showing = false;
    private float downX, downY;
    private int startY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fadeHandle = new Runnable() {
        @Override public void run() {
            if (handle != null && !showing) handle.animate().alpha(0.38f).setDuration(260).start();
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        showHandle();
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showHandle() {
        final EdgeHandleView v = new EdgeHandleView(this);
        int w = dp(30);
        int h = dp(96);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w,
                h,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = 0;
        lp.y = -dp(150);
        handle = v;
        v.setAlpha(0.68f);
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        handler.removeCallbacks(fadeHandle);
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startY = lp.y;
                        handle.animate().alpha(1f).setDuration(90).start();
                        handle.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dy) > dp(4)) {
                            lp.y = startY + (int) dy;
                            try { wm.updateViewLayout(handle, lp); } catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float dx = event.getRawX() - downX;
                        float totalDy = event.getRawY() - downY;
                        boolean tap = Math.abs(dx) < dp(12) && Math.abs(totalDy) < dp(12);
                        boolean swipeOpen = dx < -dp(24) && Math.abs(totalDy) < dp(70);
                        if (tap || swipeOpen) showPanel();
                        else scheduleHandleFade();
                        return true;
                    default:
                        return false;
                }
            }
        });
        wm.addView(handle, lp);
        scheduleHandleFade();
    }

    private void scheduleHandleFade() {
        handler.removeCallbacks(fadeHandle);
        handler.postDelayed(fadeHandle, 1200);
    }

    private void showPanel() {
        if (showing) return;
        showing = true;
        handler.removeCallbacks(fadeHandle);
        if (handle != null) handle.animate().alpha(0f).setDuration(140).start();

        FrameLayout windowRoot = new FrameLayout(this);
        windowRoot.setBackgroundColor(Color.TRANSPARENT);

        final LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(12), dp(16), dp(14));
        sheet.setBackground(panelBackground());
        sheet.setElevation(dp(12));

        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        sheetParams.setMargins(dp(8), dp(64), dp(8), dp(64));
        windowRoot.addView(sheet, sheetParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(0, -1, 1f);
        header.addView(titles, titlesLp);

        TextView title = new TextView(this);
        title.setText("剪切板");
        title.setTextSize(21);
        title.setTextColor(Color.rgb(24, 24, 26));
        title.setGravity(Gravity.BOTTOM);
        titles.addView(title, new LinearLayout.LayoutParams(-1, 0, 0.62f));

        TextView subtitle = new TextView(this);
        subtitle.setText("点击复制 · 长按置顶");
        subtitle.setTextSize(11);
        subtitle.setTextColor(Color.rgb(130, 130, 136));
        subtitle.setGravity(Gravity.TOP);
        titles.addView(subtitle, new LinearLayout.LayoutParams(-1, 0, 0.38f));

        TextView close = new TextView(this);
        close.setText("×");
        close.setTextSize(28);
        close.setTextColor(Color.rgb(92, 92, 98));
        close.setGravity(Gravity.CENTER);
        close.setBackground(iconButtonBackground());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeLp.setMargins(dp(8), 0, 0, 0);
        header.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hidePanelAnimated(); }
        });

        search = new EditText(this);
        search.setHint("搜索内容");
        search.setHintTextColor(Color.rgb(150, 150, 156));
        search.setTextColor(Color.rgb(28, 28, 30));
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(searchBackground());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(46));
        searchLp.setMargins(0, dp(8), 0, dp(10));
        sheet.addView(search, searchLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(2), 0, dp(8));
        scroll.addView(listBox, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(344),
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
        lp.x = 0;
        panel = windowRoot;
        wm.addView(panel, lp);

        refresh();
        sheet.setAlpha(0f);
        sheet.setTranslationX(dp(344));
        sheet.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hidePanelAnimated() {
        if (!showing || panel == null) return;
        final View removing = panel;
        View sheet = ((FrameLayout) removing).getChildAt(0);
        sheet.animate()
                .alpha(0f)
                .translationX(dp(344))
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        try { wm.removeView(removing); } catch (Exception ignored) { }
                        if (panel == removing) panel = null;
                        showing = false;
                        restoreHandle();
                    }
                })
                .start();
    }

    private void hidePanelImmediate() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
            panel = null;
        }
        showing = false;
        restoreHandle();
    }

    private void restoreHandle() {
        if (handle != null) {
            handle.animate().alpha(0.72f).setDuration(140).start();
            scheduleHandleFade();
        }
    }

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String query = search == null ? "" : search.getText().toString();
        List<ClipItem> items = ClipStore.search(this, query);

        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("没有找到内容");
            empty.setTextSize(15);
            empty.setTextColor(Color.rgb(142, 142, 147));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(46), dp(12), dp(46));
            listBox.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }

        for (final ClipItem item : items) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(10));
            card.setBackground(cardBackground(item.pinned));
            card.setElevation(dp(1));

            TextView content = new TextView(this);
            content.setText((item.pinned ? "★  " : "") + preview(item.content));
            content.setTextSize(15);
            content.setTextColor(Color.rgb(28, 28, 30));
            content.setLineSpacing(dp(3), 1.02f);
            content.setMaxLines(4);
            card.addView(content, new LinearLayout.LayoutParams(-1, -2));

            TextView meta = new TextView(this);
            meta.setText(metaText(item));
            meta.setTextSize(10);
            meta.setTextColor(Color.rgb(150, 150, 156));
            meta.setPadding(0, dp(7), 0, 0);
            card.addView(meta, new LinearLayout.LayoutParams(-1, -2));

            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardTools.copy(FloatingClipboardService.this, item.content);
                    ClipStore.markUsed(FloatingClipboardService.this, item.id);
                    Toast.makeText(FloatingClipboardService.this, "已复制", Toast.LENGTH_SHORT).show();
                    hidePanelAnimated();
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    ClipStore.togglePinned(FloatingClipboardService.this, item.id);
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    refresh();
                    return true;
                }
            });

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
            cardLp.setMargins(0, 0, 0, dp(10));
            listBox.addView(card, cardLp);
        }
    }

    private String metaText(ClipItem item) {
        String source = item.source == null || item.source.trim().isEmpty() ? "剪切板" : item.source;
        if (source.contains("system_clipboard")) source = "系统剪切板";
        else if (source.contains("manual")) source = "手动添加";
        else if (source.contains("demo")) source = "示例";
        return source + (item.usageCount > 0 ? "  ·  使用 " + item.usageCount + " 次" : "");
    }

    private String preview(String text) {
        String p = text == null ? "" : text.trim();
        return p.length() > 220 ? p.substring(0, 220) + "…" : p;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(250, 247, 247, 249));
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Color.argb(28, 0, 0, 0));
        return bg;
    }

    private GradientDrawable searchBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(234, 234, 238));
        bg.setCornerRadius(dp(14));
        return bg;
    }

    private GradientDrawable iconButtonBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(234, 234, 238));
        bg.setShape(GradientDrawable.OVAL);
        return bg;
    }

    private GradientDrawable cardBackground(boolean pinned) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pinned ? Color.rgb(255, 251, 241) : Color.WHITE);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), pinned
                ? Color.argb(55, 205, 158, 65)
                : Color.argb(22, 0, 0, 0));
        return bg;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (panel != null) hidePanelImmediate();
        if (handle != null) {
            try { wm.removeView(handle); } catch (Exception ignored) { }
            handle = null;
        }
        super.onDestroy();
    }

    private static class EdgeHandleView extends View {
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF shadowRect = new RectF();
        private final RectF barRect = new RectF();

        EdgeHandleView(Context context) {
            super(context);
            setBackgroundColor(Color.TRANSPARENT);
            shadowPaint.setColor(Color.argb(22, 0, 0, 0));
            barPaint.setColor(Color.rgb(118, 118, 124));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float barWidth = 4f * density;
            float barHeight = 48f * density;
            float right = getWidth() - 2f * density;
            float left = right - barWidth;
            float top = (getHeight() - barHeight) / 2f;
            float bottom = top + barHeight;

            shadowRect.set(left - density, top + density, right + density, bottom + density);
            barRect.set(left, top, right, bottom);
            float radius = barWidth / 2f;
            canvas.drawRoundRect(shadowRect, radius + density, radius + density, shadowPaint);
            canvas.drawRoundRect(barRect, radius, radius, barPaint);
        }
    }
}
