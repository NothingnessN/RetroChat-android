package com.nothingnessn.retrochat;

public class Message {
    public String id;
    public String fromUUID;
    public String fromName;
    public String text;
    public boolean isMine;
    public String time;
    public boolean isGroup;
    public String groupId;
    public String mediaPath;
    public String mediaOriginal;
    public String mediaMime;
    public long mediaSize;
    public int mediaType;
    public String replyToId;
    public String replyPreview;
    public boolean read;
    public boolean deleted;

    public long ts;

    public Message() {}

    public Message(String fromUUID, String fromName, String text, boolean isMine, String time) {
        this.fromUUID = fromUUID;
        this.fromName = fromName;
        this.text = text;
        this.isMine = isMine;
        this.time = time;
        this.isGroup = false;
        this.mediaType = 0;
        this.read = false;
        this.deleted = false;
    }

    public boolean hasMedia() {
        return !deleted && mediaPath != null && mediaPath.length() > 0;
    }

    public String displayLabel() {
        if (deleted) return "Bu mesaj silindi";
        if (hasMedia()) {
            String name = (mediaOriginal != null && mediaOriginal.length() > 0) ? mediaOriginal : mediaPath;
            String sizeStr = mediaSize > 0 ? " (" + formatSize(mediaSize) + ")" : "";
            String cap = (text != null && text.length() > 0) ? "\n" + text : "";
            return "[dosya] " + name + sizeStr + cap;
        }
        return text != null ? text : "";
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        return (bytes / (1024L * 1024 * 1024)) + " GB";
    }
}
