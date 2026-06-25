package com.logos.clipboarddemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout listBox;
    private EditText input;
    private EditText search;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("Logos Clipboard Demo");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        Button overlay = new Button(this);
        overlay.setText("1. 授权悬浮窗");
        root.addView(overlay);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });

        Button start = new Button(this);
        start.setText("2. 启动右侧剪切板小条");
        root.addView(start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "请先授权悬浮窗", Toast.LENGTH_SHORT).show();
                    openOverlaySettings();
                    return;
                }
                startService(new Intent(MainActivity.this, FloatingClipboardService.class));
                Toast.makeText(MainActivity.this, "已启动，点屏幕右侧小条呼出", Toast.LENGTH_SHORT).show();
            }
        });

        Button stop = new Button(this);
        stop.setText("停止悬浮小条");
        root.addView(stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopService(new Intent(MainActivity.this, FloatingClipboardService.class)); }
        });

        Button saveCurrent = new Button(this);
        saveCurrent.setText("保存当前系统剪切板");
        root.addView(saveCurrent);
        saveCurrent.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String clip = ClipboardTools.read(MainActivity.this);
                if (clip == null || clip.trim().isEmpty()) Toast.makeText(MainActivity.this, "当前剪切板为空或不可读", Toast.LENGTH_SHORT).show();
                else { ClipStore.add(MainActivity.this, clip, "system_clipboard"); Toast.makeText(MainActivity.this, "已保存当前剪切板", Toast.LENGTH_SHORT).show(); refresh(); }
            }
        });

        input = new EditText(this);
        input.setHint("手动添加一条常用语/长文本");
        input.setMinLines(2);
        root.addView(input, new LinearLayout.LayoutParams(-1, dp(90)));

        Button add = new Button(this);
        add.setText("添加到剪切板库");
        root.addView(add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String s = input.getText().toString();
                if (s.trim().isEmpty()) return;
                ClipStore.add(MainActivity.this, s, "manual");
                input.setText("");
                refresh();
            }
        });

        search = new EditText(this);
        search.setHint("主界面搜索");
        search.setSingleLine(true);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(46)));
        search.setOnEditorActionListener((v, actionId, event) -> { refresh(); return false; });

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        seedIfEmpty();
        refresh();
    }

    private void seedIfEmpty() {
        if (!ClipStore.list(this).isEmpty()) return;
        ClipStore.add(this, "这是 Logos Clipboard Demo：右侧小条呼出，点击条目复制，长按置顶。", "demo");
        ClipStore.add(this, "投稿常用语：感谢编辑和审稿人的审阅意见，我会据此进一步修改稿件。", "demo");
        ClipStore.add(this, "Prompt 片段：请先给出结构化判断，再指出最关键的问题。", "demo");
    }

    private void refresh() {
        listBox.removeAllViews();
        List<ClipItem> items = ClipStore.search(this, search == null ? "" : search.getText().toString());
        for (final ClipItem item : items) {
            TextView row = new TextView(this);
            row.setText((item.pinned ? "★ " : "") + item.title + "\n" + preview(item.content));
            row.setTextSize(15);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ClipboardTools.copy(MainActivity.this, item.content);
                    ClipStore.markUsed(MainActivity.this, item.id);
                    Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                    refresh();
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    ClipStore.togglePinned(MainActivity.this, item.id);
                    refresh();
                    return true;
                }
            });
            listBox.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private String preview(String s) {
        String p = s.replace('\n', ' ').trim();
        return p.length() > 110 ? p.substring(0, 110) + "…" : p;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
