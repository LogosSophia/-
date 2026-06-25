package com.logos.clipboarddemo;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ClipStore {
    private static final String PREF = "logos_clip_store";
    private static final String KEY = "clips";
    private static final int MAX_DEMO_ITEMS = 300;

    public static synchronized List<ClipItem> list(Context context) {
        ArrayList<ClipItem> result = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new ClipItem(
                        o.optString("id"),
                        o.optString("title"),
                        o.optString("content"),
                        o.optString("source"),
                        o.optLong("createdAt"),
                        o.optLong("lastUsedAt"),
                        o.optInt("usageCount"),
                        o.optBoolean("pinned")
                ));
            }
        } catch (Exception ignored) { }
        sort(result);
        return result;
    }

    public static synchronized List<ClipItem> search(Context context, String q) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return list(context);
        ArrayList<ClipItem> result = new ArrayList<>();
        for (ClipItem item : list(context)) {
            String hay = (item.title + "\n" + item.content + "\n" + item.source).toLowerCase(Locale.ROOT);
            if (hay.contains(query)) result.add(item);
        }
        sort(result);
        return result;
    }

    public static synchronized ClipItem add(Context context, String content, String source) {
        if (content == null) return null;
        String normalized = content.trim();
        if (normalized.isEmpty()) return null;

        ArrayList<ClipItem> items = new ArrayList<>(list(context));
        String id = sha1(normalized);
        long now = System.currentTimeMillis();

        for (ClipItem item : items) {
            if (item.id.equals(id) || item.content.equals(normalized)) {
                item.lastUsedAt = now;
                item.usageCount += 1;
                save(context, items);
                return item;
            }
        }

        ClipItem item = new ClipItem(
                id,
                makeTitle(normalized),
                normalized,
                source == null ? "manual" : source,
                now,
                now,
                0,
                false
        );
        items.add(0, item);
        while (items.size() > MAX_DEMO_ITEMS) items.remove(items.size() - 1);
        save(context, items);
        return item;
    }

    public static synchronized void markUsed(Context context, String id) {
        ArrayList<ClipItem> items = new ArrayList<>(list(context));
        long now = System.currentTimeMillis();
        for (ClipItem item : items) {
            if (item.id.equals(id)) {
                item.usageCount += 1;
                item.lastUsedAt = now;
                break;
            }
        }
        save(context, items);
    }

    public static synchronized void togglePinned(Context context, String id) {
        ArrayList<ClipItem> items = new ArrayList<>(list(context));
        for (ClipItem item : items) {
            if (item.id.equals(id)) {
                item.pinned = !item.pinned;
                break;
            }
        }
        save(context, items);
    }

    public static synchronized void delete(Context context, String id) {
        ArrayList<ClipItem> items = new ArrayList<>(list(context));
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equals(id)) items.remove(i);
        }
        save(context, items);
    }

    private static synchronized void save(Context context, List<ClipItem> items) {
        sort(items);
        try {
            JSONArray arr = new JSONArray();
            for (ClipItem item : items) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("title", item.title);
                o.put("content", item.content);
                o.put("source", item.source);
                o.put("createdAt", item.createdAt);
                o.put("lastUsedAt", item.lastUsedAt);
                o.put("usageCount", item.usageCount);
                o.put("pinned", item.pinned);
                arr.put(o);
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static void sort(List<ClipItem> items) {
        Collections.sort(items, new Comparator<ClipItem>() {
            @Override public int compare(ClipItem a, ClipItem b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                return Long.compare(b.lastUsedAt, a.lastUsedAt);
            }
        });
    }

    private static String makeTitle(String content) {
        String oneLine = content.replace('\n', ' ').replace('\r', ' ').trim();
        if (oneLine.length() > 32) return oneLine.substring(0, 32) + "…";
        return oneLine;
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
