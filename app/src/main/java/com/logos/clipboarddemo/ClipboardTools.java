package com.logos.clipboarddemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public final class ClipboardTools {
    private static volatile String lastOwnText = "";
    private static volatile long lastOwnWriteAt = 0L;

    private ClipboardTools() { }

    public static void copy(Context context, String text) {
        if (text == null) text = "";
        lastOwnText = text;
        lastOwnWriteAt = System.currentTimeMillis();
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Logos Clipboard", text));
    }

    public static String read(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip() == null) return null;
            ClipData data = cm.getPrimaryClip();
            if (data.getItemCount() == 0) return null;
            CharSequence text = data.getItemAt(0).coerceToText(context);
            return text == null ? null : text.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isRecentOwnWrite(String text) {
        return text != null && text.equals(lastOwnText) && System.currentTimeMillis() - lastOwnWriteAt < 2500L;
    }
}
