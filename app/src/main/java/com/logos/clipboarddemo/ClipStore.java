package com.logos.clipboarddemo;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ClipStore {
    private static final String DB_NAME = "logos_clipboard.db";
    private static final int DB_VERSION = 1;
    private static final String OLD_PREF = "logos_clip_store";
    private static final String OLD_KEY = "clips";
    private static volatile DbHelper helper;

    private ClipStore() { }

    private static DbHelper db(Context context) {
        if (helper == null) {
            synchronized (ClipStore.class) {
                if (helper == null) helper = new DbHelper(context.getApplicationContext());
            }
        }
        return helper;
    }

    public static synchronized List<ClipItem> list(Context context) {
        return query(context, "", "all", "全部");
    }

    public static synchronized List<ClipItem> search(Context context, String q) {
        return query(context, q, "all", "全部");
    }

    public static synchronized List<ClipItem> query(Context context, String q, String mode, String category) {
        SQLiteDatabase database = db(context).getReadableDatabase();
        ArrayList<ClipItem> out = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        ArrayList<String> args = new ArrayList<>();

        if ("pinned".equals(mode)) where.append(" AND pinned=1");
        else if ("common".equals(mode)) where.append(" AND common=1");
        else if ("favorite".equals(mode)) where.append(" AND favorite=1");
        else if ("sensitive".equals(mode)) where.append(" AND sensitive=1");

        if (category != null && !category.trim().isEmpty() && !"全部".equals(category)) {
            where.append(" AND category=?");
            args.add(category.trim());
        }

        String query = q == null ? "" : q.trim();
        Cursor cursor = null;
        try {
            if (!query.isEmpty()) {
                String match = makeFtsQuery(query);
                String sql = "SELECT c.* FROM clips c JOIN clips_fts f ON c.id=f.id WHERE "
                        + where + " AND clips_fts MATCH ? ORDER BY c.pinned DESC,c.common DESC,c.lastUsedAt DESC";
                args.add(match);
                cursor = database.rawQuery(sql, args.toArray(new String[0]));
            } else {
                cursor = database.query("clips", null, where.toString(), args.toArray(new String[0]),
                        null, null, "pinned DESC, common DESC, lastUsedAt DESC");
            }
        } catch (SQLiteException ftsFailure) {
            if (cursor != null) cursor.close();
            args.clear();
            where = new StringBuilder("1=1");
            if ("pinned".equals(mode)) where.append(" AND pinned=1");
            else if ("common".equals(mode)) where.append(" AND common=1");
            else if ("favorite".equals(mode)) where.append(" AND favorite=1");
            else if ("sensitive".equals(mode)) where.append(" AND sensitive=1");
            if (category != null && !category.trim().isEmpty() && !"全部".equals(category)) {
                where.append(" AND category=?");
                args.add(category.trim());
            }
            if (!query.isEmpty()) {
                where.append(" AND (title LIKE ? OR content LIKE ? OR tags LIKE ? OR source LIKE ?)");
                String like = "%" + query + "%";
                args.add(like); args.add(like); args.add(like); args.add(like);
            }
            cursor = database.query("clips", null, where.toString(), args.toArray(new String[0]),
                    null, null, "pinned DESC, common DESC, lastUsedAt DESC");
        }

        try {
            while (cursor != null && cursor.moveToNext()) out.add(fromCursor(cursor));
        } finally {
            if (cursor != null) cursor.close();
        }
        return out;
    }

    public static synchronized ClipItem get(Context context, String id) {
        Cursor c = db(context).getReadableDatabase().query("clips", null, "id=?",
                new String[]{id}, null, null, null, "1");
        try { return c.moveToFirst() ? fromCursor(c) : null; }
        finally { c.close(); }
    }

    public static synchronized ClipItem add(Context context, String content, String source) {
        return addDetailed(context, null, content, source, "未分类", "", false, false, false, false);
    }

    public static synchronized ClipItem addDetailed(Context context, String title, String content,
                                                    String source, String category, String tags,
                                                    boolean pinned, boolean favorite,
                                                    boolean common, boolean sensitive) {
        if (content == null) return null;
        String normalized = content.trim();
        if (normalized.isEmpty()) return null;
        String id = sha1(normalized);
        long now = System.currentTimeMillis();
        SQLiteDatabase database = db(context).getWritableDatabase();
        ClipItem old = get(context, id);
        if (old != null) {
            ContentValues update = new ContentValues();
            update.put("lastUsedAt", now);
            update.put("usageCount", old.usageCount + 1);
            if (source != null && !source.trim().isEmpty()) update.put("source", source);
            database.update("clips", update, "id=?", new String[]{id});
            syncFts(database, get(context, id));
            return get(context, id);
        }

        ClipItem item = new ClipItem(id,
                title == null || title.trim().isEmpty() ? makeTitle(normalized) : title.trim(),
                normalized,
                source == null || source.trim().isEmpty() ? "manual" : source.trim(),
                category == null || category.trim().isEmpty() ? "未分类" : category.trim(),
                tags == null ? "" : tags.trim(), now, now, 0,
                pinned, favorite, common, sensitive);
        database.insertOrThrow("clips", null, values(item));
        syncFts(database, item);
        return item;
    }

    public static synchronized void update(Context context, ClipItem item) {
        if (item == null || item.id == null) return;
        SQLiteDatabase database = db(context).getWritableDatabase();
        database.update("clips", values(item), "id=?", new String[]{item.id});
        syncFts(database, item);
    }

    public static synchronized void markUsed(Context context, String id) {
        ClipItem item = get(context, id);
        if (item == null) return;
        item.usageCount += 1;
        item.lastUsedAt = System.currentTimeMillis();
        update(context, item);
    }

    public static synchronized void togglePinned(Context context, String id) { toggle(context, id, "pinned"); }
    public static synchronized void toggleFavorite(Context context, String id) { toggle(context, id, "favorite"); }
    public static synchronized void toggleCommon(Context context, String id) { toggle(context, id, "common"); }
    public static synchronized void toggleSensitive(Context context, String id) { toggle(context, id, "sensitive"); }

    private static void toggle(Context context, String id, String column) {
        ClipItem item = get(context, id);
        if (item == null) return;
        int current;
        if ("pinned".equals(column)) current = item.pinned ? 1 : 0;
        else if ("favorite".equals(column)) current = item.favorite ? 1 : 0;
        else if ("common".equals(column)) current = item.common ? 1 : 0;
        else current = item.sensitive ? 1 : 0;
        ContentValues values = new ContentValues();
        values.put(column, current == 0 ? 1 : 0);
        db(context).getWritableDatabase().update("clips", values, "id=?", new String[]{id});
    }

    public static synchronized void delete(Context context, String id) {
        SQLiteDatabase database = db(context).getWritableDatabase();
        database.delete("clips", "id=?", new String[]{id});
        database.delete("clips_fts", "id=?", new String[]{id});
    }

    public static synchronized List<String> categories(Context context) {
        Set<String> set = new LinkedHashSet<>();
        set.add("全部");
        set.add("未分类");
        Cursor c = db(context).getReadableDatabase().rawQuery(
                "SELECT DISTINCT category FROM clips WHERE category IS NOT NULL AND TRIM(category)<>'' ORDER BY category", null);
        try { while (c.moveToNext()) set.add(c.getString(0)); }
        finally { c.close(); }
        return new ArrayList<>(set);
    }

    public static synchronized String exportJson(Context context) throws Exception {
        JSONArray array = new JSONArray();
        for (ClipItem item : query(context, "", "all", "全部")) array.put(toJson(item));
        JSONObject root = new JSONObject();
        root.put("format", "logos-clipboard-backup");
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        root.put("clips", array);
        return root.toString(2);
    }

    public static synchronized int importJson(Context context, String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        JSONArray array = root.getJSONArray("clips");
        int count = 0;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            ClipItem item = fromJson(o);
            addDetailed(context, item.title, item.content, item.source, item.category, item.tags,
                    item.pinned, item.favorite, item.common, item.sensitive);
            count++;
        }
        return count;
    }

    public static synchronized int count(Context context) {
        Cursor c = db(context).getReadableDatabase().rawQuery("SELECT COUNT(*) FROM clips", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; }
        finally { c.close(); }
    }

    private static ContentValues values(ClipItem item) {
        ContentValues v = new ContentValues();
        v.put("id", item.id);
        v.put("title", item.title);
        v.put("content", item.content);
        v.put("source", item.source);
        v.put("category", item.category);
        v.put("tags", item.tags);
        v.put("createdAt", item.createdAt);
        v.put("lastUsedAt", item.lastUsedAt);
        v.put("usageCount", item.usageCount);
        v.put("pinned", item.pinned ? 1 : 0);
        v.put("favorite", item.favorite ? 1 : 0);
        v.put("common", item.common ? 1 : 0);
        v.put("sensitive", item.sensitive ? 1 : 0);
        return v;
    }

    private static ClipItem fromCursor(Cursor c) {
        return new ClipItem(
                c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("content")),
                c.getString(c.getColumnIndexOrThrow("source")),
                c.getString(c.getColumnIndexOrThrow("category")),
                c.getString(c.getColumnIndexOrThrow("tags")),
                c.getLong(c.getColumnIndexOrThrow("createdAt")),
                c.getLong(c.getColumnIndexOrThrow("lastUsedAt")),
                c.getInt(c.getColumnIndexOrThrow("usageCount")),
                c.getInt(c.getColumnIndexOrThrow("pinned")) != 0,
                c.getInt(c.getColumnIndexOrThrow("favorite")) != 0,
                c.getInt(c.getColumnIndexOrThrow("common")) != 0,
                c.getInt(c.getColumnIndexOrThrow("sensitive")) != 0
        );
    }

    private static void syncFts(SQLiteDatabase database, ClipItem item) {
        if (item == null) return;
        database.delete("clips_fts", "id=?", new String[]{item.id});
        ContentValues f = new ContentValues();
        f.put("id", item.id);
        f.put("title", item.title);
        f.put("content", item.content);
        f.put("source", item.source);
        f.put("category", item.category);
        f.put("tags", item.tags);
        database.insert("clips_fts", null, f);
    }

    private static String makeFtsQuery(String query) {
        String[] parts = query.trim().replace('"', ' ').split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String clean = part.replaceAll("[^\\p{L}\\p{N}_-]", "");
            if (clean.isEmpty()) continue;
            if (out.length() > 0) out.append(" AND ");
            out.append(clean).append('*');
        }
        return out.length() == 0 ? query : out.toString();
    }

    private static JSONObject toJson(ClipItem item) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", item.id); o.put("title", item.title); o.put("content", item.content);
        o.put("source", item.source); o.put("category", item.category); o.put("tags", item.tags);
        o.put("createdAt", item.createdAt); o.put("lastUsedAt", item.lastUsedAt);
        o.put("usageCount", item.usageCount); o.put("pinned", item.pinned);
        o.put("favorite", item.favorite); o.put("common", item.common); o.put("sensitive", item.sensitive);
        return o;
    }

    private static ClipItem fromJson(JSONObject o) {
        return new ClipItem(o.optString("id"), o.optString("title"), o.optString("content"),
                o.optString("source", "import"), o.optString("category", "未分类"), o.optString("tags"),
                o.optLong("createdAt", System.currentTimeMillis()), o.optLong("lastUsedAt", System.currentTimeMillis()),
                o.optInt("usageCount"), o.optBoolean("pinned"), o.optBoolean("favorite"),
                o.optBoolean("common"), o.optBoolean("sensitive"));
    }

    private static String makeTitle(String content) {
        String oneLine = content.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 42 ? oneLine.substring(0, 42) + "…" : oneLine;
    }

    private static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(text.hashCode()); }
    }

    private static final class DbHelper extends SQLiteOpenHelper {
        private final Context context;
        DbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); this.context = context; }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE clips (id TEXT PRIMARY KEY,title TEXT NOT NULL,content TEXT NOT NULL,source TEXT,category TEXT,tags TEXT,createdAt INTEGER,lastUsedAt INTEGER,usageCount INTEGER DEFAULT 0,pinned INTEGER DEFAULT 0,favorite INTEGER DEFAULT 0,common INTEGER DEFAULT 0,sensitive INTEGER DEFAULT 0)");
            db.execSQL("CREATE VIRTUAL TABLE clips_fts USING fts4(id,title,content,source,category,tags,tokenize=unicode61)");
            db.execSQL("CREATE INDEX idx_clips_recent ON clips(lastUsedAt DESC)");
            db.execSQL("CREATE INDEX idx_clips_category ON clips(category)");
            migrateLegacy(db);
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

        private void migrateLegacy(SQLiteDatabase db) {
            try {
                SharedPreferences sp = context.getSharedPreferences(OLD_PREF, Context.MODE_PRIVATE);
                JSONArray array = new JSONArray(sp.getString(OLD_KEY, "[]"));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    ClipItem item = new ClipItem(o.optString("id"), o.optString("title"), o.optString("content"),
                            o.optString("source"), "未分类", "", o.optLong("createdAt"), o.optLong("lastUsedAt"),
                            o.optInt("usageCount"), o.optBoolean("pinned"), false, false, false);
                    db.insertWithOnConflict("clips", null, values(item), SQLiteDatabase.CONFLICT_IGNORE);
                    syncFts(db, item);
                }
            } catch (Exception ignored) { }
        }
    }
}
