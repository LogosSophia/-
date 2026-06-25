package com.logos.clipboarddemo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
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
        v.setText("≡");
        v.setTextColor(Color.WHITE);
        v.setTextSize(20);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.argb(210, 20, 20, 20));
        int w = dp(32), h = dp(76);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.x = 0; lp.y = 0;
        handle = v;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); startX = lp.x; startY = lp.y; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    lp.y = startY + (int)(e.getRawY() - downY);
                    wm.updateViewLayout(handle, lp); return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.abs(e.getRawX() - downX) < 12 && Math.abs(e.getRawY() - downY) < 12) togglePanel();
                    return true;
                }
                return false;
            }
        });
        wm.addView(handle, lp);
    }

    private void togglePanel() { if (showing) hidePanel(); else showPanel(); }

    private void showPanel() {
        showing = true;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(245, 248, 248, 248));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("Logos Clipboard Demo");
        title.setTextSize(18);
        title.setTextColor(Color.BLACK);
        box.addView(title, new LinearLayout.LayoutParams(-1, dp(36)));

        search = new EditText(this);
        search.setHint("搜索剪切板 / 常用语");
        search.setSingleLine(true);
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setGravity(Gravity.CENTER);
        close.setTextSize(16);
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.rgb(45,45,45));
        box.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hidePanel(); }});

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(330), WindowManager.LayoutParams.MATCH_PARENT, overlayType(), WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
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
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        v.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private String preview(String s) {
        String p = s.replace('\n', ' ').trim();
        return p.length() > 96 ? p.substring(0, 96) + "…" : p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onDestroy() {
        hidePanel();
        if (handle != null) { try { wm.removeView(handle); } catch (Exception ignored) {} }
        super.onDestroy();
    }
}
