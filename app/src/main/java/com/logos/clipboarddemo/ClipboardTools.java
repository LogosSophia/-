package com.logos.clipboarddemo;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public class ClipboardTools {
    public static void copy(Context context, String text) {
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
}
