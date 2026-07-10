package com.logos.clipboarddemo;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public class ClipboardAccessibilityService extends AccessibilityService {
    private static volatile ClipboardAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ClipboardManager clipboard;
    private String lastSelectedText = "";
    private String lastSourcePackage = "unknown";
    private String lastSaved = "";
    private long lastSavedAt = 0L;

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener =
            new ClipboardManager.OnPrimaryClipChangedListener() {
                @Override public void onPrimaryClipChanged() {
                    scheduleCapture(lastSourcePackage, 120L);
                }
            };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.addPrimaryClipChangedListener(clipListener);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getPackageName() != null) lastSourcePackage = event.getPackageName().toString();
        AccessibilityNodeInfo source = event.getSource();
        if (source != null && source.isPassword()) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            CharSequence selected = selectedFromEvent(event);
            if (selected != null && selected.length() > 0) lastSelectedText = selected.toString();
        }

        String eventText = flattenEventText(event).toLowerCase(Locale.ROOT);
        if (looksLikeCopyAction(eventText)) scheduleCapture(lastSourcePackage, 180L);
    }

    private void scheduleCapture(final String sourcePackage, long delay) {
        if (!AppPrefs.autoCapture(this)) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() { captureNow(sourcePackage); }
        }, delay);
    }

    private void captureNow(String sourcePackage) {
        String text = ClipboardTools.read(this);
        if ((text == null || text.trim().isEmpty()) && lastSelectedText != null) text = lastSelectedText;
        if (text == null) return;
        text = text.trim();
        if (text.isEmpty() || ClipboardTools.isRecentOwnWrite(text)) return;
        long now = System.currentTimeMillis();
        if (text.equals(lastSaved) && now - lastSavedAt < 3000L) return;
        ClipStore.add(this, text, sourcePackage == null ? "unknown" : sourcePackage);
        lastSaved = text;
        lastSavedAt = now;
    }

    private CharSequence selectedFromEvent(AccessibilityEvent event) {
        List<CharSequence> texts = event.getText();
        if (texts == null || texts.isEmpty() || texts.get(0) == null) return null;
        String full = texts.get(0).toString();
        int from = event.getFromIndex();
        int to = event.getToIndex();
        if (from >= 0 && to > from && to <= full.length()) return full.substring(from, to);
        return full;
    }

    private String flattenEventText(AccessibilityEvent event) {
        StringBuilder out = new StringBuilder();
        if (event.getContentDescription() != null) out.append(event.getContentDescription()).append(' ');
        if (event.getClassName() != null) out.append(event.getClassName()).append(' ');
        if (event.getText() != null) {
            for (CharSequence s : event.getText()) if (s != null) out.append(s).append(' ');
        }
        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            if (source.getText() != null) out.append(source.getText()).append(' ');
            if (source.getContentDescription() != null) out.append(source.getContentDescription()).append(' ');
        }
        return out.toString();
    }

    private boolean looksLikeCopyAction(String text) {
        return text.contains("复制") || text.contains("已复制") || text.contains("剪切")
                || text.contains("copy") || text.contains("copied") || text.contains("cut")
                || text.contains("clipboard");
    }

    public static boolean isRunning() { return instance != null; }

    public static boolean pasteTextGlobal(String text) {
        ClipboardAccessibilityService service = instance;
        return service != null && service.pasteText(text);
    }

    private boolean pasteText(String text) {
        if (text == null) return false;
        ClipboardTools.copy(this, text);
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null) target = findEditable(root);
        if (target == null || target.isPassword()) return false;

        if (target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true;

        CharSequence old = target.getText();
        String oldText = old == null ? "" : old.toString();
        int start = target.getTextSelectionStart();
        int end = target.getTextSelectionEnd();
        if (start < 0 || end < start || end > oldText.length()) start = end = oldText.length();
        String merged = oldText.substring(0, start) + text + oldText.substring(end);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(clipListener);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
