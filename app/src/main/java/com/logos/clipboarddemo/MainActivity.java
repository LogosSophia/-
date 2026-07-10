package com.logos.clipboarddemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 4101;
    private static final int REQ_IMPORT = 4102;

    private LinearLayout listBox;
    private EditText search;
    private TextView countText;
    private Button categoryButton;
    private String mode = "all";
    private String category = "全部";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        buildUi();
        seedIfEmpty();
        refresh();
        handleShareIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(247, 247, 249));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView title = text("剪切板", 25, Color.rgb(24,24,26));
        titles.addView(title, new LinearLayout.LayoutParams(-1, 0, 0.64f));
        countText = text("", 11, Color.rgb(125,125,132));
        titles.addView(countText, new LinearLayout.LayoutParams(-1, 0, 0.36f));

        Button add = compactButton("＋ 新建");
        header.addView(add, new LinearLayout.LayoutParams(dp(92), dp(40)));
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showEditor(null, null); }
        });

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actionsScroll.addView(actions);
        root.addView(actionsScroll, new LinearLayout.LayoutParams(-1, dp(48)));

        addAction(actions, "悬浮窗授权", new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
        addAction(actions, "启动侧边栏", new View.OnClickListener() {
            @Override public void onClick(View v) { startOverlay(); }
        });
        addAction(actions, "停止侧边栏", new View.OnClickListener() {
            @Override public void onClick(View v) {
                AppPrefs.setOverlayEnabled(MainActivity.this, false);
                stopService(new Intent(MainActivity.this, FloatingClipboardService.class));
            }
        });
        addAction(actions, "无障碍授权", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        });
        addAction(actions, "保存当前剪切板", new View.OnClickListener() {
            @Override public void onClick(View v) { saveCurrentClipboard(); }
        });
        addAction(actions, "导出备份", new View.OnClickListener() {
            @Override public void onClick(View v) { exportBackup(); }
        });
        addAction(actions, "导入备份", new View.OnClickListener() {
            @Override public void onClick(View v) { importBackup(); }
        });

        HorizontalScrollView settingsScroll = new HorizontalScrollView(this);
        settingsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        settings.setGravity(Gravity.CENTER_VERTICAL);
        settingsScroll.addView(settings);
        root.addView(settingsScroll, new LinearLayout.LayoutParams(-1, dp(46)));

        final Switch autoCapture = new Switch(this);
        autoCapture.setText("自动收集");
        autoCapture.setChecked(AppPrefs.autoCapture(this));
        settings.addView(autoCapture);
        autoCapture.setOnCheckedChangeListener((buttonView, isChecked) -> AppPrefs.setAutoCapture(MainActivity.this, isChecked));

        final Switch directPaste = new Switch(this);
        directPaste.setText("直接粘贴");
        directPaste.setChecked(AppPrefs.directPaste(this));
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(-2, -2);
        switchLp.setMargins(dp(14),0,0,0);
        settings.addView(directPaste, switchLp);
        directPaste.setOnCheckedChangeListener((buttonView, isChecked) -> AppPrefs.setDirectPaste(MainActivity.this, isChecked));

        final Switch maskSensitive = new Switch(this);
        maskSensitive.setText("隐藏敏感内容");
        maskSensitive.setChecked(AppPrefs.maskSensitive(this));
        settings.addView(maskSensitive, switchLp);
        maskSensitive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setMaskSensitive(MainActivity.this, isChecked);
            refresh();
        });

        search = new EditText(this);
        search.setHint("全文检索：内容、标题、标签、来源");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(searchBackground());
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        HorizontalScrollView filtersScroll = new HorizontalScrollView(this);
        filtersScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filtersScroll.addView(filters);
        LinearLayout.LayoutParams filterRowLp = new LinearLayout.LayoutParams(-1, dp(50));
        filterRowLp.setMargins(0,dp(6),0,0);
        root.addView(filtersScroll, filterRowLp);

        addMode(filters, "最近", "all");
        addMode(filters, "置顶", "pinned");
        addMode(filters, "常用语", "common");
        addMode(filters, "收藏", "favorite");
        addMode(filters, "敏感", "sensitive");
        categoryButton = compactButton("分类：全部");
        LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(-2, dp(38));
        catLp.setMargins(dp(8),0,0,0);
        filters.addView(categoryButton, catLp);
        categoryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { chooseCategory(); }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0,dp(4),0,dp(30));
        scroll.addView(listBox);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void addAction(LinearLayout row, String label, View.OnClickListener listener) {
        Button button = compactButton(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0,dp(4),dp(8),0);
        row.addView(button, lp);
        button.setOnClickListener(listener);
    }

    private void addMode(LinearLayout row, String label, final String value) {
        Button button = compactButton(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0,0,dp(7),0);
        row.addView(button, lp);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { mode = value; refresh(); }
        });
    }

    private void refresh() {
        if (listBox == null) return;
        listBox.removeAllViews();
        List<ClipItem> items = ClipStore.query(this, search == null ? "" : search.getText().toString(), mode, category);
        countText.setText(ClipStore.count(this) + " 条 · " + statusText());
        categoryButton.setText("分类：" + category);
        if (items.isEmpty()) {
            TextView empty = text("没有找到内容", 15, Color.rgb(142,142,147));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0,dp(60),0,dp(60));
            listBox.addView(empty, new LinearLayout.LayoutParams(-1,-2));
            return;
        }
        for (final ClipItem item : items) addCard(item);
    }

    private String statusText() {
        if (!ClipboardAccessibilityService.isRunning()) return "无障碍未连接";
        return AppPrefs.autoCapture(this) ? "自动收集中" : "自动收集已关闭";
    }

    private void addCard(final ClipItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(11));
        card.setBackground(cardBackground(item.pinned));
        card.setElevation(dp(1));

        TextView title = text(iconText(item) + safe(item.title), 15, Color.rgb(28,28,30));
        title.setMaxLines(1);
        card.addView(title, new LinearLayout.LayoutParams(-1,-2));

        String body = item.sensitive && AppPrefs.maskSensitive(this) ? "••••••  敏感内容已隐藏" : preview(item.content, 280);
        TextView content = text(body, 14, item.sensitive ? Color.rgb(100,86,82) : Color.rgb(55,55,60));
        content.setLineSpacing(dp(3),1.02f);
        content.setMaxLines(5);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1,-2);
        bodyLp.setMargins(0,dp(6),0,0);
        card.addView(content, bodyLp);

        TextView meta = text(metaText(item), 10, Color.rgb(145,145,152));
        meta.setPadding(0,dp(8),0,0);
        card.addView(meta, new LinearLayout.LayoutParams(-1,-2));

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardTools.copy(MainActivity.this, item.content);
                ClipStore.markUsed(MainActivity.this, item.id);
                Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showItemMenu(item); return true; }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(10));
        listBox.addView(card, lp);
    }

    private String iconText(ClipItem item) {
        StringBuilder s = new StringBuilder();
        if (item.pinned) s.append("★ ");
        if (item.common) s.append("常用 · ");
        if (item.favorite) s.append("收藏 · ");
        if (item.sensitive) s.append("私密 · ");
        return s.toString();
    }

    private String metaText(ClipItem item) {
        StringBuilder s = new StringBuilder();
        s.append(safe(item.category));
        if (item.tags != null && !item.tags.trim().isEmpty()) s.append("  ·  #").append(item.tags.replace(",", " #"));
        s.append("  ·  ").append(sourceLabel(item.source));
        if (item.usageCount > 0) s.append("  ·  使用 ").append(item.usageCount).append(" 次");
        return s.toString();
    }

    private String sourceLabel(String source) {
        if (source == null || source.trim().isEmpty()) return "未知来源";
        if (source.contains("manual")) return "手动添加";
        if (source.contains("system_clipboard")) return "系统剪切板";
        if (source.contains("import")) return "导入";
        try {
            CharSequence label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(source, 0));
            return label == null ? source : label.toString();
        } catch (Exception ignored) { return source; }
    }

    private void showItemMenu(final ClipItem item) {
        String[] options = new String[]{
                "编辑",
                item.pinned ? "取消置顶" : "置顶",
                item.common ? "取消常用语" : "设为常用语",
                item.favorite ? "取消收藏" : "收藏",
                item.sensitive ? "取消敏感标记" : "标记为敏感",
                "删除"
        };
        new AlertDialog.Builder(this).setTitle(item.title).setItems(options, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) showEditor(item, null);
                else if (which == 1) ClipStore.togglePinned(MainActivity.this, item.id);
                else if (which == 2) ClipStore.toggleCommon(MainActivity.this, item.id);
                else if (which == 3) ClipStore.toggleFavorite(MainActivity.this, item.id);
                else if (which == 4) ClipStore.toggleSensitive(MainActivity.this, item.id);
                else if (which == 5) confirmDelete(item);
                if (which > 0 && which < 5) refresh();
            }
        }).show();
    }

    private void confirmDelete(final ClipItem item) {
        new AlertDialog.Builder(this).setTitle("删除这条内容？")
                .setMessage(preview(item.content, 120))
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ClipStore.delete(MainActivity.this, item.id); refresh();
                    }
                }).show();
    }

    private void showEditor(final ClipItem existing, String initialText) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(8),dp(20),dp(8));
        scroll.addView(box);

        final EditText title = field("标题（可自动生成）", existing == null ? "" : existing.title, 1);
        final EditText content = field("正文", existing == null ? safe(initialText) : existing.content, 7);
        final EditText categoryField = field("分类", existing == null ? "未分类" : existing.category, 1);
        final EditText tags = field("标签，用逗号分隔", existing == null ? "" : existing.tags, 1);
        final CheckBox pinned = check("置顶", existing != null && existing.pinned);
        final CheckBox common = check("常用语", existing != null && existing.common);
        final CheckBox favorite = check("收藏", existing != null && existing.favorite);
        final CheckBox sensitive = check("敏感内容（默认隐藏预览）", existing != null && existing.sensitive);
        box.addView(title); box.addView(content); box.addView(categoryField); box.addView(tags);
        box.addView(pinned); box.addView(common); box.addView(favorite); box.addView(sensitive);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "新建内容" : "编辑内容")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String body = content.getText().toString().trim();
                        if (body.isEmpty()) { Toast.makeText(MainActivity.this, "正文不能为空", Toast.LENGTH_SHORT).show(); return; }
                        if (existing == null) {
                            ClipStore.addDetailed(MainActivity.this, title.getText().toString(), body, "manual",
                                    categoryField.getText().toString(), tags.getText().toString(),
                                    pinned.isChecked(), favorite.isChecked(), common.isChecked(), sensitive.isChecked());
                        } else {
                            existing.title = title.getText().toString().trim().isEmpty() ? existing.title : title.getText().toString().trim();
                            existing.content = body;
                            existing.category = categoryField.getText().toString().trim().isEmpty() ? "未分类" : categoryField.getText().toString().trim();
                            existing.tags = tags.getText().toString().trim();
                            existing.pinned = pinned.isChecked(); existing.common = common.isChecked();
                            existing.favorite = favorite.isChecked(); existing.sensitive = sensitive.isChecked();
                            ClipStore.update(MainActivity.this, existing);
                        }
                        refresh();
                    }
                }).show();
    }

    private EditText field(String hint, String value, int lines) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value == null ? "" : value); e.setMinLines(lines); e.setGravity(Gravity.TOP);
        e.setTextSize(15); e.setPadding(dp(10),dp(8),dp(10),dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(5)); e.setLayoutParams(lp); return e;
    }

    private CheckBox check(String text, boolean checked) {
        CheckBox c = new CheckBox(this); c.setText(text); c.setChecked(checked); return c;
    }

    private void chooseCategory() {
        final List<String> categories = ClipStore.categories(this);
        new AlertDialog.Builder(this).setTitle("选择分类")
                .setItems(categories.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        category = categories.get(which); refresh();
                    }
                }).show();
    }

    private void startOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { openOverlaySettings(); return; }
        AppPrefs.setOverlayEnabled(this, true);
        Intent service = new Intent(this, FloatingClipboardService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        Toast.makeText(this, "侧边栏已启动", Toast.LENGTH_SHORT).show();
    }

    private void saveCurrentClipboard() {
        String text = ClipboardTools.read(this);
        if (text == null || text.trim().isEmpty()) Toast.makeText(this, "当前剪切板为空或不可读", Toast.LENGTH_SHORT).show();
        else { ClipStore.add(this, text, "system_clipboard"); refresh(); }
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "logos-clipboard-backup.json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                out.write(ClipStore.exportJson(this).getBytes(StandardCharsets.UTF_8));
                out.close(); Toast.makeText(this, "备份已导出", Toast.LENGTH_SHORT).show();
            } else if (requestCode == REQ_IMPORT) {
                InputStream in = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] bytes = new byte[8192]; int read;
                while ((read = in.read(bytes)) != -1) buffer.write(bytes,0,read);
                in.close();
                int count = ClipStore.importJson(this, buffer.toString("UTF-8"));
                Toast.makeText(this, "已导入 " + count + " 条", Toast.LENGTH_SHORT).show(); refresh();
            }
        } catch (Exception e) { Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction()) || !"text/plain".equals(intent.getType())) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.trim().isEmpty()) showEditor(null, shared);
    }

    private void seedIfEmpty() {
        if (ClipStore.count(this) > 0) return;
        ClipStore.addDetailed(this, "投稿回复", "感谢编辑和审稿人的审阅意见，我会据此进一步修改稿件。", "demo", "投稿", "邮件,投稿", true, true, true, false);
        ClipStore.addDetailed(this, "结构化分析", "请先给出结构化判断，再指出最关键的问题。", "demo", "Prompt", "AI,分析", false, false, true, false);
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= 23) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private Button compactButton(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false); b.setPadding(dp(10),0,dp(10),0); return b;
    }

    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }

    private GradientDrawable searchBackground() {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(233,233,237)); bg.setCornerRadius(dp(15)); return bg;
    }

    private GradientDrawable cardBackground(boolean pinned) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pinned ? Color.rgb(255,250,240) : Color.WHITE); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), pinned ? Color.rgb(238,215,170) : Color.rgb(230,230,234)); return bg;
    }

    private String preview(String value, int max) {
        String text = safe(value).trim(); return text.length() > max ? text.substring(0,max) + "…" : text;
    }
    private String safe(String value) { return value == null ? "" : value; }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
}
