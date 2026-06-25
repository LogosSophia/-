package com.logos.clipboarddemo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FloatingClipboardService extends Service {
    private WindowManager wm;
    private View handle;
    private View panel;
    private LinearLayout listBox;
    private EditText search;
    private boolean showing = false;
    private float downX, downY;
    private int startX, startY;

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
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void showHandle() {
        TextView v = new TextView(this);
        v.setText("▮▮▮▮");
        v.setTextColor(Color.rgb(190, 160, 145));
        v.setTextSize(10);
        v.setLetterSpacing(0.18f);
        v.setGravity(Gravity.CENTER);
        v.setAlpha(0.92f);
        v.setBackground(sidePillBackground(false));

        int w = dp(50), h = dp(38);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = dp(-2);
        lp.y = -dp(170);
        handle = v;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); startX = lp.x; startY = lp.y;
                    handle.setAlpha(1.0f);
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    lp.y = startY + (int)(e.getRawY() - downY);
                    wm.updateViewLayout(handle, lp); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    handle.setAlpha(0.92f);
                    if (Math.abs(e.getRawX() - downX) < 12 && Math.abs(e.getRawY() - downY) < 12) togglePanel();
                    return true;
                }
                return false;
            }
        });
        wm.addView(handle, lp);
    }

    private GradientDrawable sidePillBackground(boolean light) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(light ? Color.argb(235, 245, 245, 245) : Color.argb(230, 18, 18, 18));
        bg.setCornerRadius(dp(20));
        return bg;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(248, 250, 250, 250));
        float r = dp(22);
        bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        return bg;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        return bg;
    }

    private void togglePanel() { if (showing) hidePanel(); else showPanel(); }

    private void showPanel() {
        showing = true;
        FrameLayout root = new FrameLayout(this);
        root.setBackground(panelBackground());
        root.setElevation(dp(8));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("Logos Clipboard");
        title.setTextSize(19);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        search = new EditText(this);
        search.setHint("搜索剪切板 / 常用语");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setBackgroundColor(Color.TRANSPARENT);
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));

        View line = new View(this);
        line.setBackgroundColor(Color.argb(90, 0, 0, 0));
        box.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(10), 0, dp(8));
        scroll.addView(listBox);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(16);
        close.setTextColor(Color.WHITE);
        close.setBackground(sidePillBackground(false));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, dp(44));
        closeLp.setMargins(0, dp(4), 0, 0);
        box.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hidePanel(); }});

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(320), WindowManager.LayoutParams.MATCH_PARENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
        lp.x = 0;
        panel = root;
        wm.addView(panel, lp);
        refresh();
    }

    private void hidePanel() {
        showing = false;
        if (panel != null) { try { wm.removeView(panel); } catch (Exception ignored) {} panel = null; }
    }

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews();
        String q = search == null ? "" : search.getText().toString();
        List<ClipItem> items = ClipStore.search(this, q);
        if (items.isEmpty()) {
            TextView empty = rowText("暂无内容：先回主界面手动添加，或复制后打开主界面保存当前剪切板", 14, Color.GRAY);
            listBox.addView(empty);
            return;
        }
        for (final ClipItem item : items) {
            TextView row = rowText((item.pinned ? "★ " : "") + item.title + "\n" + preview(item.content), 14, Color.BLACK);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardTools.copy(FloatingClipboardService.this, item.content);
                    ClipStore.markUsed(FloatingClipboardService.this, item.id);
                    Toast.makeText(FloatingClipboardService.this, "已复制，请在输入框粘贴", Toast.LENGTH_SHORT).show();
                    hidePanel();
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    ClipStore.togglePinned(FloatingClipboardService.this, item.id);
                    Toast.makeText(FloatingClipboardService.this, "已切换置顶", Toast.LENGTH_SHORT).show();
                    refresh();
                    return true;
                }
            });
            listBox.addView(row);
        }
    }

    private TextView rowText(String s, int size, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1.0f);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackground(cardBackground());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        v.setLayoutParams(lp);
        return v;
    }

    private String preview(String s) {
        String p = s.replace('\n', ' ').trim();
        return p.length() > 86 ? p.substring(0, 86) + "…" : p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onDestroy() {
        hidePanel();
        if (handle != null) { try { wm.removeView(handle); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
