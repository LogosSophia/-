package com.logos.clipboarddemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AppPrefs.overlayEnabled(context)) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return;
        Intent service = new Intent(context, FloatingClipboardService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
    }
}
