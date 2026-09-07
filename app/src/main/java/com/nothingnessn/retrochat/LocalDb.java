package com.nothingnessn.retrochat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class LocalDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "retrochat_local.db";
    private static final int DB_VER = 5;

    public LocalDb(Context context) {
        super(context, DB_NAME, null, DB_VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                "peer TEXT PRIMARY KEY," +
                "title TEXT," +
                "last_msg TEXT," +
                "last_time TEXT," +
                "last_ts INTEGER," +
                "is_group INTEGER DEFAULT 0," +
                "group_id TEXT" +
                ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "peer TEXT," +
                "server_id TEXT," +
                "from_name TEXT," +
                "text TEXT," +
                "time TEXT," +
                "ts INTEGER," +
                "is_mine INTEGER," +
                "is_group INTEGER DEFAULT 0," +
                "media_path TEXT," +
                "media_original TEXT," +
                "media_mime TEXT," +
                "media_size INTEGER DEFAULT 0," +
                "reply_to TEXT," +
                "reply_preview TEXT," +
                "deleted INTEGER DEFAULT 0," +
                "read_flag INTEGER DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_peer ON messages(peer, ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        onCreate(db);
        String[] alters = new String[]{
                "ALTER TABLE messages ADD COLUMN server_id TEXT",
                "ALTER TABLE messages ADD COLUMN reply_to TEXT",
                "ALTER TABLE messages ADD COLUMN reply_preview TEXT",
                "ALTER TABLE messages ADD COLUMN deleted INTEGER DEFAULT 0",
                "ALTER TABLE messages ADD COLUMN read_flag INTEGER DEFAULT 0",
                "ALTER TABLE messages ADD COLUMN media_path TEXT",
                "ALTER TABLE messages ADD COLUMN media_original TEXT",
                "ALTER TABLE messages ADD COLUMN media_mime TEXT",
                "ALTER TABLE messages ADD COLUMN media_size INTEGER DEFAULT 0"
        };
        for (int i = 0; i < alters.length; i++) {
            try { db.execSQL(alters[i]); } catch (Exception ignored) {}
        }
    }

    public static String previewOf(String text, String mediaOriginal, String mediaPath) {
        String original = mediaOriginal != null && mediaOriginal.length() > 0 ? mediaOriginal : mediaPath;
        if (original != null && original.length() > 0) {
            String low = original.toLowerCase();
            if (low.endsWith(".amr") || low.endsWith(".3gp") || low.startsWith("ses_")) return "[SES] " + original;
            if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png")
                    || low.endsWith(".gif") || low.endsWith(".webp") || low.endsWith(".bmp")) {
                return "[FOTO] " + original;
            }
            if (low.endsWith(".mp4") || low.endsWith(".avi")) return "[VIDEO] " + original;
            return "[dosya] " + original;
        }
        return text != null ? text : "";
    }

    public void upsertChat(String peer, String title, String lastMsg, String lastTime, long ts,
                           boolean isGroup, String groupId) {
        if (peer == null || peer.length() == 0 || "?".equals(peer)) return;

        if (!UiUtil.looksLikeRealPeer(peer)) return;
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT last_ts, title FROM chats WHERE peer=?", new String[]{peer});
        long oldTs = -1;
        String oldTitle = null;
        if (c.moveToFirst()) {
            oldTs = c.getLong(0);
            oldTitle = c.getString(1);
        }
        c.close();
        if (oldTs >= 0 && ts > 0 && ts < oldTs) {
            return;
        }
        ContentValues cv = new ContentValues();
        cv.put("peer", peer);
        String t = title != null && title.length() > 0 && !"?".equals(title) ? title : peer;
        if (oldTitle != null && oldTitle.length() > 0 && (title == null || title.length() == 0)) {
            t = oldTitle;
        }
        cv.put("title", t);
        cv.put("last_msg", lastMsg != null ? lastMsg : "");
        cv.put("last_time", lastTime != null ? lastTime : "");
        cv.put("last_ts", ts);
        cv.put("is_group", isGroup ? 1 : 0);
        cv.put("group_id", groupId);
        db.insertWithOnConflict("chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int purgeGhostChats() {
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        try {
            Cursor c = db.rawQuery("SELECT peer FROM chats", null);
            java.util.ArrayList<String> bad = new java.util.ArrayList<String>();
            while (c.moveToNext()) {
                String peer = c.getString(0);
                if (peer == null || !UiUtil.looksLikeRealPeer(peer)) bad.add(peer == null ? "" : peer);
            }
            c.close();
            for (int i = 0; i < bad.size(); i++) {
                String peer = bad.get(i);
                n += db.delete("chats", "peer=?", new String[]{peer});
                db.delete("messages", "peer=?", new String[]{peer});
            }

            db.execSQL("UPDATE messages SET media_path=NULL, media_original=NULL, media_mime=NULL, media_size=0 "
                    + "WHERE media_path IN ('0','1','?') OR media_original IN ('0','1','?')");
        } catch (Exception ignored) {}
        return n;
    }

    public List<ChatItem> getChats() {
        List<ChatItem> list = new ArrayList<ChatItem>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT peer, title, last_msg, last_time, last_ts, is_group, group_id " +
                        "FROM chats WHERE peer IS NOT NULL AND peer != '?' ORDER BY is_group DESC, last_ts DESC",
                null);
        while (c.moveToNext()) {
            ChatItem item = new ChatItem();
            item.peer = c.getString(0);
            item.title = c.getString(1);
            item.lastMsg = c.getString(2);
            item.lastTime = c.getString(3);
            item.lastTs = c.getLong(4);
            item.isGroup = c.getInt(5) == 1;
            item.groupId = c.getString(6);
            if (item.peer != null && UiUtil.looksLikeRealPeer(item.peer)) list.add(item);
        }
        c.close();
        return list;
    }

    public void addMessage(String peer, String fromName, String text, String time, long ts,
                           boolean isMine, boolean isGroup) {
        addMessageFull(peer, fromName, text, time, ts, isMine, isGroup,
                null, null, null, 0, null, null, null, false, false);
    }

    public void addMessageFull(String peer, String fromName, String text, String time, long ts,
                               boolean isMine, boolean isGroup,
                               String mediaPath, String mediaOriginal, String mediaMime, long mediaSize) {
        addMessageFull(peer, fromName, text, time, ts, isMine, isGroup,
                mediaPath, mediaOriginal, mediaMime, mediaSize, null, null, null, false, false);
    }

    public void addMessageFull(String peer, String fromName, String text, String time, long ts,
                               boolean isMine, boolean isGroup,
                               String mediaPath, String mediaOriginal, String mediaMime, long mediaSize,
                               String serverId, String replyTo, String replyPreview,
                               boolean deleted, boolean read) {
        if (peer == null || peer.length() == 0 || "?".equals(peer)) return;
        if (!UiUtil.looksLikeRealPeer(peer)) return;

        if (mediaPath != null && mediaPath.length() > 0 && !UiUtil.looksLikeRealMedia(mediaPath)) {
            mediaPath = null;
            mediaOriginal = null;
            mediaMime = null;
            mediaSize = 0;
        }
        SQLiteDatabase db = getWritableDatabase();
        if (serverId != null && serverId.length() > 0) {
            Cursor c = db.rawQuery("SELECT id FROM messages WHERE server_id=?", new String[]{serverId});
            boolean exists = c.moveToFirst();
            c.close();
            if (exists) {
                String preview = previewOf(text, mediaOriginal, mediaPath);
                if (deleted) preview = "(silindi)";
                upsertChat(peer, isGroup ? null : (isMine ? peer : fromName),
                        preview, time, ts, isGroup, isGroup ? peer : null);
                return;
            }
        }
        ContentValues cv = new ContentValues();
        cv.put("peer", peer);
        cv.put("server_id", serverId);
        cv.put("from_name", fromName);
        cv.put("text", text);
        cv.put("time", time);
        cv.put("ts", ts);
        cv.put("is_mine", isMine ? 1 : 0);
        cv.put("is_group", isGroup ? 1 : 0);
        cv.put("media_path", mediaPath);
        cv.put("media_original", mediaOriginal);
        cv.put("media_mime", mediaMime);
        cv.put("media_size", mediaSize);
        cv.put("reply_to", replyTo);
        cv.put("reply_preview", replyPreview);
        cv.put("deleted", deleted ? 1 : 0);
        cv.put("read_flag", read ? 1 : 0);
        db.insert("messages", null, cv);

        String preview = previewOf(text, mediaOriginal, mediaPath);
        if (deleted) preview = "(silindi)";
        String title = peer;
        if (!isGroup && !isMine && fromName != null && fromName.length() > 0 && !"?".equals(fromName)) {
            title = fromName;
        }
        upsertChat(peer, title, preview, time, ts, isGroup, isGroup ? peer : null);
    }

    public void replacePeerMessages(String peer, List<Message> list, boolean isGroup) {
        if (peer == null) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("messages", "peer=?", new String[]{peer});
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    Message m = list.get(i);
                    ContentValues cv = new ContentValues();
                    cv.put("peer", peer);
                    cv.put("server_id", m.id);
                    cv.put("from_name", m.fromName);
                    cv.put("text", m.text);
                    cv.put("time", m.time);
                    cv.put("ts", m.ts);
                    cv.put("is_mine", m.isMine ? 1 : 0);
                    cv.put("is_group", isGroup ? 1 : 0);
                    cv.put("media_path", m.mediaPath);
                    cv.put("media_original", m.mediaOriginal);
                    cv.put("media_mime", m.mediaMime);
                    cv.put("media_size", m.mediaSize);
                    cv.put("reply_to", m.replyToId);
                    cv.put("reply_preview", m.replyPreview);
                    cv.put("deleted", m.deleted ? 1 : 0);
                    cv.put("read_flag", m.read ? 1 : 0);
                    db.insert("messages", null, cv);
                }
                if (list.size() > 0) {
                    Message last = list.get(list.size() - 1);
                    String preview = last.deleted ? "(silindi)"
                            : previewOf(last.text, last.mediaOriginal, last.mediaPath);
                    upsertChat(peer, null, preview, last.time, last.ts > 0 ? last.ts : System.currentTimeMillis(),
                            isGroup, isGroup ? peer : null);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Message> getMessages(String peer) {
        List<Message> list = new ArrayList<Message>();
        if (peer == null) return list;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT from_name, text, time, is_mine, is_group, " +
                        "media_path, media_original, media_mime, media_size, " +
                        "server_id, reply_to, reply_preview, deleted, read_flag, ts " +
                        "FROM messages WHERE peer=? ORDER BY ts ASC, id ASC",
                new String[]{peer});
        while (c.moveToNext()) {
            Message m = new Message(null, c.getString(0), c.getString(1), c.getInt(3) == 1, c.getString(2));
            m.isGroup = c.getInt(4) == 1;
            m.mediaPath = c.getString(5);
            m.mediaOriginal = c.getString(6);
            m.mediaMime = c.getString(7);
            m.mediaSize = c.getLong(8);
            m.id = c.getString(9);
            m.replyToId = c.getString(10);
            m.replyPreview = c.getString(11);
            m.deleted = c.getInt(12) == 1;
            m.read = c.getInt(13) == 1;
            m.ts = c.getLong(14);
            if (m.mediaPath != null && m.mediaPath.length() > 0) m.mediaType = 1;
            list.add(m);
        }
        c.close();
        return list;
    }

    public static class ChatItem {
        public String peer;
        public String title;
        public String lastMsg;
        public String lastTime;
        public long lastTs;
        public boolean isGroup;
        public String groupId;
    }
}
