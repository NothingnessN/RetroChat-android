package com.nothingnessn.retrochat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Network {

    public static String SERVER = "http://YOUR_SERVER_IP";

    private static final String TAG = "RetroChatNet";
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024 * 1024;

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(String result);
        void onError(String error);
    }

    public static void register(String uuid, String username, String question, String answer, final Callback cb) {
        String body = "uuid=" + enc(uuid)
                + "&username=" + enc(username)
                + "&question=" + enc(question)
                + "&answer=" + enc(answer);
        post("/api/register", body, cb);
    }

    public static void verify(String uuid, String username, String answer, final Callback cb) {
        String body = "uuid=" + enc(uuid)
                + "&username=" + enc(username)
                + "&answer=" + enc(answer);
        post("/api/verify", body, cb);
    }

    public static void setupSecurity(String uuid, String username, String question, String answer, final Callback cb) {
        String body = "uuid=" + enc(uuid)
                + "&username=" + enc(username)
                + "&question=" + enc(question)
                + "&answer=" + enc(answer);
        post("/api/setup_security", body, cb);
    }

    public static void ping(final Callback cb) {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(SERVER + "/api/ping");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setRequestProperty("User-Agent", "RetroChat-Android/1.0");
                    conn.setRequestProperty("Connection", "close");
                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String result = readStream(is);
                    if (code >= 200 && code < 300) postSuccess(cb, result);
                    else postError(cb, "HTTP " + code);
                } catch (Exception e) {
                    postError(cb, e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    public static void searchUser(String query, final Callback cb) {
        get("/api/search?q=" + enc(query), cb);
    }

    public static void sendPrivate(String fromUuid, String toUsername, String text, final Callback cb) {
        sendPrivateReply(fromUuid, toUsername, text, null, null, cb);
    }

    public static void sendPrivateReply(String fromUuid, String toUsername, String text,
                                       String replyTo, String replyPreview, final Callback cb) {
        String body = "from=" + enc(fromUuid)
                + "&to=" + enc(toUsername)
                + "&text=" + enc(text)
                + "&type=private";
        if (replyTo != null && replyTo.length() > 0) {
            body += "&reply_to=" + enc(replyTo) + "&reply_preview=" + enc(replyPreview != null ? replyPreview : "");
        }
        post("/api/send", body, cb);
    }

    public static void sendGroup(String fromUuid, String groupId, String text, final Callback cb) {
        String body = "from=" + enc(fromUuid)
                + "&group=" + enc(groupId)
                + "&text=" + enc(text)
                + "&type=group";
        post("/api/send", body, cb);
    }

    public static void markRead(String uuid, String peer, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&peer=" + enc(peer);
        post("/api/read", body, cb);
    }

    public static void deleteMessage(String uuid, String msgId, boolean everyone, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&id=" + enc(msgId) + "&scope=" + (everyone ? "everyone" : "me");
        post("/api/delete", body, cb);
    }

    public static void sendTyping(String uuid, String to, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&to=" + enc(to);
        post("/api/typing", body, cb);
    }

    public static void getTyping(String uuid, final Callback cb) {
        get("/api/typing?uuid=" + enc(uuid), cb);
    }

    public static void fetchMessages(String uuid, final Callback cb) {
        get("/api/messages?uuid=" + enc(uuid), cb);
    }

    public static void fetchChats(String uuid, final Callback cb) {
        get("/api/chats?uuid=" + enc(uuid), cb);
    }

    public static void fetchThread(String uuid, String peer, final Callback cb) {
        get("/api/thread?uuid=" + enc(uuid) + "&peer=" + enc(peer) + "&limit=500", cb);
    }

    public static void fetchGroupMessages(String uuid, String groupId, final Callback cb) {
        get("/api/group/messages?uuid=" + enc(uuid) + "&group=" + enc(groupId) + "&limit=500", cb);
    }

    public static void joinGroup(String uuid, String groupId, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&group=" + enc(groupId);
        post("/api/group/join", body, cb);
    }

    public static boolean looksLikeGroupId(String s) {
        if (s == null) return false;
        int n = s.length();
        if (n < 8 || n > 16) return false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    public static void createGroup(String uuid, String name, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&name=" + enc(name);
        post("/api/group/create", body, cb);
    }

    public static void listMyGroups(String uuid, final Callback cb) {
        get("/api/group/list?uuid=" + enc(uuid), cb);
    }

    public static void groupMembers(String groupId, final Callback cb) {
        get("/api/group/members?group=" + enc(groupId), cb);
    }

    public static void groupAdd(String uuid, String groupId, String username, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&group=" + enc(groupId) + "&username=" + enc(username);
        post("/api/group/add", body, cb);
    }

    public static void groupRemove(String uuid, String groupId, String username, final Callback cb) {
        String body = "uuid=" + enc(uuid) + "&group=" + enc(groupId) + "&username=" + enc(username);
        post("/api/group/remove", body, cb);
    }

    public static void uploadFile(final String fromUuid, final String toOrGroup, final boolean isGroup,
                                  final File file, final String caption, final Callback cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (file == null || !file.exists()) {
                    postError(cb, "Dosya yok");
                    return;
                }
                long size = file.length();
                if (size > MAX_FILE_BYTES) {
                    postError(cb, "too_large");
                    return;
                }
                HttpURLConnection conn = null;
                try {
                    String boundary = "----RetroChat" + System.currentTimeMillis();
                    URL url = new URL(SERVER + "/api/upload");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(7200000);
                    conn.setChunkedStreamingMode(8192);
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setRequestProperty("User-Agent", "RetroChat-Android/1.0");

                    DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                    writeFormField(out, boundary, "from", fromUuid);
                    if (isGroup) {
                        writeFormField(out, boundary, "group", toOrGroup);
                        writeFormField(out, boundary, "type", "group");
                    } else {
                        writeFormField(out, boundary, "to", toOrGroup);
                        writeFormField(out, boundary, "type", "private");
                    }
                    if (caption != null && caption.length() > 0) {
                        writeFormField(out, boundary, "text", caption);
                    }

                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
                            + file.getName().replace("\"", "") + "\"\r\n");
                    out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

                    FileInputStream fis = new FileInputStream(file);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    fis.close();
                    out.writeBytes("\r\n");
                    out.writeBytes("--" + boundary + "--\r\n");
                    out.flush();
                    out.close();

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String result = readStream(is);
                    if (code >= 200 && code < 300) postSuccess(cb, result);
                    else postError(cb, "HTTP " + code + " " + result);
                } catch (Exception e) {
                    Log.e(TAG, "upload error", e);
                    postError(cb, e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static void writeFormField(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes("UTF-8"));
        out.writeBytes("\r\n");
    }

    private static void get(final String path, final Callback cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(SERVER + path);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setRequestProperty("User-Agent", "RetroChat-Android/1.0");
                    conn.setRequestProperty("Connection", "close");
                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String result = readStream(is);
                    if (code >= 200 && code < 300) postSuccess(cb, result);
                    else postError(cb, "HTTP " + code + " " + result);
                } catch (Exception e) {
                    Log.e(TAG, "GET error", e);
                    postError(cb, e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static void post(final String path, final String body, final Callback cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(SERVER + path);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setRequestProperty("User-Agent", "RetroChat-Android/1.0");
                    conn.setRequestProperty("Connection", "close");
                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, Charset.forName("UTF-8")));
                    writer.write(body);
                    writer.flush();
                    writer.close();
                    os.close();
                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String result = readStream(is);
                    if (code >= 200 && code < 300) postSuccess(cb, result);
                    else postError(cb, "HTTP " + code + " " + result);
                } catch (Exception e) {
                    Log.e(TAG, "POST error", e);
                    postError(cb, e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        reader.close();
        return sb.toString().trim();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static void postSuccess(final Callback cb, final String result) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() { cb.onSuccess(result); }
        });
    }

    private static void postError(final Callback cb, final String error) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() { cb.onError(error != null ? error : "Unknown error"); }
        });
    }
}
