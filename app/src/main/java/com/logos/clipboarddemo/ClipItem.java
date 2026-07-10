package com.logos.clipboarddemo;

public class ClipItem {
    public String id;
    public String title;
    public String content;
    public String source;
    public String category;
    public String tags;
    public long createdAt;
    public long lastUsedAt;
    public int usageCount;
    public boolean pinned;
    public boolean favorite;
    public boolean common;
    public boolean sensitive;

    public ClipItem(String id, String title, String content, String source,
                    String category, String tags, long createdAt, long lastUsedAt,
                    int usageCount, boolean pinned, boolean favorite,
                    boolean common, boolean sensitive) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.source = source;
        this.category = category;
        this.tags = tags;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.usageCount = usageCount;
        this.pinned = pinned;
        this.favorite = favorite;
        this.common = common;
        this.sensitive = sensitive;
    }
}
