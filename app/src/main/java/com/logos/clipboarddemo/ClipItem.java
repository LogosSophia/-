package com.logos.clipboarddemo;

public class ClipItem {
    public String id;
    public String title;
    public String content;
    public String source;
    public long createdAt;
    public long lastUsedAt;
    public int usageCount;
    public boolean pinned;

    public ClipItem(String id, String title, String content, String source, long createdAt, long lastUsedAt, int usageCount, boolean pinned) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.source = source;
        this.createdAt = createdAt;
        this.lastUsedAt = lastUsedAt;
        this.usageCount = usageCount;
        this.pinned = pinned;
    }
}
