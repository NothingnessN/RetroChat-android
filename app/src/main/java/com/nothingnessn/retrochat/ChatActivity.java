package com.nothingnessn.retrochat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class ChatActivity extends Activity {

    private static final int REQ_FILE = 1001;

    private TextView tvTitle, tvStatus, tvReplyPreview;
    private LinearLayout replyBar;
    private ListView listMessages;
    private EditText etMessage;
    private Button btnSend, btnRefresh, btnAttach, btnCancelReply;

    private String title, target;
    private boolean isGroup;
    private List<Message> messages = new ArrayList<Message>();
    private MessageAdapter adapter;
    private LocalDb db;

    private Message replyTarget = null;
    private final Map<String, Bitmap> thumbCache = new HashMap<String, Bitmap>();
    private final Handler handler = new Handler();
    private final BroadcastReceiver chatsUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            softRefreshFromServer();
        }
    };
    private Runnable typingPollRunnable;
    private long lastTypingSent = 0;
    private MediaRecorder recorder = null;
    private File voiceFile = null;
    private boolean recording = false;
    private boolean voicePaused = false;

    private boolean voiceLegacyMode = false;
    private final List<File> voiceSegments = new ArrayList<File>();
    private LinearLayout recordingBar = null;
    private TextView tvRecordTimer = null;
    private Button btnPauseResume = null;
    private Runnable recordTimerRunnable = null;
    private int recordSeconds = 0;
    private MediaPlayer player = null;
    private boolean audioBusy = false;
    private String playingPath = null;
    private boolean playPaused = false;
    private AppTheme theme;

    private final List<Message> recentLocalEchoes = new ArrayList<Message>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        try {
            getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        } catch (Exception ignored) {}
        UiUtil.setupEdgeToEdge(this);
        theme = AppTheme.get(Prefs.getThemeId(this));
        applyChatTheme();

        title = getIntent().getStringExtra("title");
        target = getIntent().getStringExtra("target");
        isGroup = getIntent().getBooleanExtra("isGroup", false);
        if (target != null) {
            target = target.trim();
            if (!isGroup) target = target.toLowerCase();
        }
        db = new LocalDb(this);

        tvTitle = (TextView) findViewById(R.id.tvChatTitle);
        tvStatus = (TextView) findViewById(R.id.tvChatStatus);
        listMessages = (ListView) findViewById(R.id.listMessages);
        etMessage = (EditText) findViewById(R.id.etMessage);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnRefresh = (Button) findViewById(R.id.btnRefresh);
        btnAttach = (Button) findViewById(R.id.btnAttach);
        replyBar = (LinearLayout) findViewById(R.id.replyBar);
        tvReplyPreview = (TextView) findViewById(R.id.tvReplyPreview);
        btnCancelReply = (Button) findViewById(R.id.btnCancelReply);

        String head = title != null ? title : "Sohbet";
        if (isGroup && head.toLowerCase().indexOf("grup") < 0) head = "Grup: " + head;
        tvTitle.setText(head);

        Button btnMembers = (Button) findViewById(R.id.btnMembers);
        if (btnMembers != null) {
            if (isGroup) {
                btnMembers.setVisibility(View.VISIBLE);
                btnMembers.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { openGroupMembers(); }
                });
            } else {
                btnMembers.setVisibility(View.GONE);
            }
        }
        adapter = new MessageAdapter();
        listMessages.setAdapter(adapter);
        listMessages.setSmoothScrollbarEnabled(true);
        listMessages.setScrollingCacheEnabled(false);
        listMessages.setAnimationCacheEnabled(false);

        listMessages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= messages.size()) return;
                Message m = messages.get(position);
                if (m.deleted) return;
                if (m.hasMedia() && isImageMessage(m)) openImageViewer(m);
                else if (m.hasMedia() && isAudioMessage(m)) playAudio(m);
                else if (m.hasMedia()) downloadMedia(m);
            }
        });

        listMessages.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= messages.size()) return true;
                showMessageMenu(messages.get(position));
                return true;
            }
        });

        if (btnCancelReply != null) {
            btnCancelReply.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { clearReply(); }
            });
        }

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (!isGroup && s != null && s.length() > 0) maybeSendTyping();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadLocal();
        if (messages.isEmpty() && isGroup) {
            tvStatus.setText("Grup acik — mesaj yaz veya Uyeler");
        }
        refreshFromServer();
        markPeerRead();
        startTypingPoll();

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendText(); }
        });
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                refreshFromServer();
                markPeerRead();
            }
        });
        btnAttach.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAttachMenu(); }
        });
        tvTitle.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showChatTools();
                return true;
            }
        });
        NotifyHelper.setActiveChat(target);
        NotifyHelper.cancelFor(this, target);
    }

    @Override
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    protected void onResume() {
        super.onResume();
        theme = AppTheme.get(Prefs.getThemeId(this));
        applyChatTheme();
        if (adapter != null) adapter.notifyDataSetChanged();
        NotifyHelper.setActiveChat(target);
        NotifyHelper.cancelFor(this, target);
        try {
            IntentFilter f = new IntentFilter(PollService.ACTION_CHATS_UPDATED);

            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(chatsUpdatedReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(chatsUpdatedReceiver, f);
            }
        } catch (Exception ignored) {}
        softRefreshFromServer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotifyHelper.setActiveChat(null);
        try { unregisterReceiver(chatsUpdatedReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NotifyHelper.setActiveChat(null);
        if (typingPollRunnable != null) handler.removeCallbacks(typingPollRunnable);
        stopPlayer();
        cancelVoice();
    }

    private void applyChatTheme() {
        if (theme == null) theme = AppTheme.defaultTheme();
        View root = findViewById(android.R.id.content);
        if (root instanceof android.view.ViewGroup && ((android.view.ViewGroup) root).getChildCount() > 0) {
            BgHelper.apply(this, ((android.view.ViewGroup) root).getChildAt(0), theme);
        } else if (root != null) {
            BgHelper.apply(this, root, theme);
        }
        if (tvTitle != null) {
            View parent = (View) tvTitle.getParent();
            if (parent != null) {
                if (theme.id == AppTheme.ID_AERO) {
                    parent.setBackgroundDrawable(theme.rounded(theme.headerBg, 0));
                } else {
                    parent.setBackgroundColor(theme.headerBg);
                }
            }
            tvTitle.setTextColor(theme.headerText);
        }
        if (tvStatus != null) tvStatus.setTextColor(theme.statusText);
        if (etMessage != null) {
            if (theme.id == AppTheme.ID_AERO) {
                etMessage.setBackgroundDrawable(theme.glassBubble(false,
                        etMessage.getResources().getDisplayMetrics().density));
            } else {
                etMessage.setBackgroundDrawable(theme.rounded(theme.inputBg, AppTheme.dp(etMessage, 22)));
            }
            etMessage.setTextColor(theme.bubbleText);
            etMessage.setHintTextColor(theme.statusText);
        }
        if (btnSend != null) {
            btnSend.setBackgroundDrawable(theme.rounded(theme.accent, AppTheme.dp(btnSend, 22)));
            btnSend.setTextColor(0xFFFFFFFF);
        }
        if (btnAttach != null) {
            btnAttach.setBackgroundDrawable(theme.rounded(theme.accent2, AppTheme.dp(btnAttach, 22)));
        }
        if (btnRefresh != null) {
            try { btnRefresh.setTextColor(theme.headerText); } catch (Exception ignored) {}
        }
        if (replyBar != null) replyBar.setBackgroundColor(theme.replyBg);
        if (tvReplyPreview != null) tvReplyPreview.setTextColor(theme.replyText);
    }

    private void showMessageMenu(final Message m) {
        if (m.deleted) {
            Toast.makeText(this, "Mesaj silinmis", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = m.isMine
                ? new String[]{"Yanitla", "Ilet", "Sil (herkesten)"}
                : new String[]{"Yanitla", "Ilet", "Sil"};
        new AlertDialog.Builder(this)
                .setTitle("Mesaj")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) setReply(m);
                        else if (which == 1) forwardMessage(m);
                        else if (which == 2) deleteMessage(m, m.isMine);
                    }
                })
                .show();
    }

    private void setReply(Message m) {
        replyTarget = m;
        if (replyBar != null) replyBar.setVisibility(View.VISIBLE);
        if (tvReplyPreview != null) {
            String prev = m.displayLabel();
            if (prev.length() > 80) prev = prev.substring(0, 80) + "...";
            tvReplyPreview.setText("Yanit: " + prev);
        }
    }

    private void clearReply() {
        replyTarget = null;
        if (replyBar != null) replyBar.setVisibility(View.GONE);
    }

    private void forwardMessage(final Message m) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Kullanici adi");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);

        new AlertDialog.Builder(this)
                .setTitle("Ilet - kime?")
                .setView(input)
                .setPositiveButton("Gonder", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String to = input.getText().toString().trim().toLowerCase();
                        if (to.length() < 2) {
                            Toast.makeText(ChatActivity.this, "Gecersiz kullanici", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        doForward(m, to);
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void doForward(final Message m, final String to) {
        if (m.deleted) {
            Toast.makeText(this, "Silinmis mesaj iletilemez", Toast.LENGTH_SHORT).show();
            return;
        }

        if (m.hasMedia() && m.mediaPath != null) {
            tvStatus.setText("Iletiliyor (medya)...");
            Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    HttpURLConnection conn = null;
                    try {
                        File local = new File(getCacheDir(), "fwd_" + m.mediaPath.replace("/", "_"));
                        if (!local.exists() || local.length() == 0) {
                            URL url = new URL(Network.SERVER + "/api/media/" + m.mediaPath);
                            conn = (HttpURLConnection) url.openConnection();
                            conn.setConnectTimeout(15000);
                            conn.setReadTimeout(300000);
                            conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                            if (conn.getResponseCode() != 200) {
                                showToast("Medya alinamadi");
                                return;
                            }
                            InputStream in = conn.getInputStream();
                            FileOutputStream fos = new FileOutputStream(local);
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                            fos.close();
                            in.close();
                        }
                        final File f = local;
                        final String name = m.mediaOriginal != null ? m.mediaOriginal : "file";
                        final String cap = m.text != null ? m.text : "";
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Network.uploadFile(Prefs.getUUID(ChatActivity.this), to, false, f, cap,
                                        new Network.Callback() {
                                    @Override
                                    public void onSuccess(String result) {
                                        if (result != null && result.startsWith("OK")) {
                                            Toast.makeText(ChatActivity.this, "Medya iletildi: @" + to, Toast.LENGTH_SHORT).show();
                                            tvStatus.setText("Iletildi");
                                        } else {
                                            Toast.makeText(ChatActivity.this, "Iletilemedi: " + result, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                    @Override
                                    public void onError(String error) {
                                        Toast.makeText(ChatActivity.this, "Hata: " + error, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        showToast("Iletme hatasi: " + e.getMessage());
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
            });
            return;
        }
        String body = m.text != null ? m.text : "";
        if (body.length() == 0) {
            Toast.makeText(this, "Bos mesaj iletilemez", Toast.LENGTH_SHORT).show();
            return;
        }
        final String fwd = "Iletildi:\n" + body;
        Network.sendPrivate(Prefs.getUUID(this), to, fwd, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result != null && result.startsWith("OK")) {
                    Toast.makeText(ChatActivity.this, "Iletildi: @" + to, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, "Iletilemedi: " + result, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Hata: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteMessage(final Message m, final boolean everyone) {
        if (m.id == null || m.id.length() == 0) {
            Toast.makeText(this, "Bu mesaj henuz senkron degil", Toast.LENGTH_SHORT).show();
            return;
        }
        String uuid = Prefs.getUUID(this);
        Network.deleteMessage(uuid, m.id, everyone, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result != null && result.startsWith("OK")) {
                    m.deleted = true;
                    m.text = "";
                    m.mediaPath = null;
                    adapter.notifyDataSetChanged();
                    Toast.makeText(ChatActivity.this, "Silindi", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, "Silinemedi: " + result, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, "Hata: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void markPeerRead() {
        if (isGroup || target == null) return;
        Network.markRead(Prefs.getUUID(this), target, null);
    }

    private void maybeSendTyping() {
        if (isGroup || target == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTypingSent < 2000) return;
        lastTypingSent = now;
        Network.sendTyping(Prefs.getUUID(this), target, null);
    }

    private void startTypingPoll() {
        typingPollRunnable = new Runnable() {
            @Override
            public void run() {

                softRefreshFromServer();
                if (!isGroup) {
                    Network.getTyping(Prefs.getUUID(ChatActivity.this), new Network.Callback() {
                        @Override
                        public void onSuccess(String result) {
                            boolean typing = false;
                            if (result != null && !result.equals("EMPTY") && !result.startsWith("ERROR")) {
                                String[] lines = result.split("\n");
                                for (int i = 0; i < lines.length; i++) {
                                    if (target != null && target.equalsIgnoreCase(lines[i].trim())) {
                                        typing = true;
                                        break;
                                    }
                                }
                            }
                            if (typing) {
                                tvStatus.setText(target + " yaziyor...");
                            }
                        }
                        @Override
                        public void onError(String error) {}
                    });
                }
                handler.postDelayed(this, 2500);
            }
        };
        handler.postDelayed(typingPollRunnable, 2000);
    }

    private void softRefreshFromServer() {
        refreshFromServer(true);
    }

    private void showAttachMenu() {
        if (recording) {

            Toast.makeText(this, "Ses kaydi devam ediyor", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[]{"Dosya / Resim", "Ses kaydi baslat"};
        new AlertDialog.Builder(this)
                .setTitle("Ekle")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) openFilePicker();
                        else startVoiceRecord();
                    }
                })
                .show();
    }

    private void ensureRecordingBar() {
        if (recordingBar != null) return;
        recordingBar = new LinearLayout(this);
        recordingBar.setOrientation(LinearLayout.HORIZONTAL);
        recordingBar.setGravity(Gravity.CENTER_VERTICAL);
        recordingBar.setBackgroundColor(0xFFB71C1C);
        recordingBar.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        tvRecordTimer = new TextView(this);
        tvRecordTimer.setTextColor(0xFFFFFFFF);
        tvRecordTimer.setTextSize(16);
        tvRecordTimer.setText("REC 0:00");
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        recordingBar.addView(tvRecordTimer, tLp);

        btnPauseResume = new Button(this);
        btnPauseResume.setText("Duraklat");
        btnPauseResume.setTextColor(0xFFFFFFFF);
        btnPauseResume.setBackgroundColor(0xFF424242);
        btnPauseResume.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (voicePaused) resumeVoice(); else pauseVoice();
            }
        });
        recordingBar.addView(btnPauseResume);

        Button btnCancel = new Button(this);
        btnCancel.setText("Iptal");
        btnCancel.setTextColor(0xFFFFFFFF);
        btnCancel.setBackgroundColor(0xFF424242);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancelVoice(); }
        });
        recordingBar.addView(btnCancel);

        Button btnSendRec = new Button(this);
        btnSendRec.setText("Gonder");
        btnSendRec.setTextColor(0xFFFFFFFF);
        btnSendRec.setBackgroundColor(0xFF660099);
        btnSendRec.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopVoiceAndSend(); }
        });
        recordingBar.addView(btnSendRec);

        View et = findViewById(R.id.etMessage);
        if (et != null && et.getParent() instanceof ViewGroup) {
            View inputRow = (View) et.getParent();
            if (inputRow.getParent() instanceof ViewGroup) {
                ViewGroup rootLl = (ViewGroup) inputRow.getParent();
                int idx = rootLl.indexOfChild(inputRow);

                View reply = findViewById(R.id.replyBar);
                if (reply != null && reply.getParent() == rootLl) {
                    int ridx = rootLl.indexOfChild(reply);

                    idx = rootLl.indexOfChild(inputRow);
                }
                if (idx < 0) idx = rootLl.getChildCount();
                rootLl.addView(recordingBar, idx, barLp);
            }
        } else {

            View root = findViewById(android.R.id.content);
            if (root instanceof ViewGroup) {
                ViewGroup content = (ViewGroup) root;

                ViewGroup target = content;
                if (content.getChildCount() > 0 && content.getChildAt(0) instanceof ViewGroup) {
                    ViewGroup inner = (ViewGroup) content.getChildAt(0);
                    if ("aurora_wrap".equals(inner.getTag()) && inner.getChildCount() > 1) {
                        for (int i = 0; i < inner.getChildCount(); i++) {
                            if (!(inner.getChildAt(i) instanceof AuroraBlobView)) {
                                target = (ViewGroup) inner.getChildAt(i);
                                break;
                            }
                        }
                    } else {
                        target = inner;
                    }
                }
                int idx = Math.max(0, target.getChildCount() - 1);
                target.addView(recordingBar, idx, barLp);
            }
        }
        recordingBar.setVisibility(View.GONE);
    }

    private void showRecordingBar(boolean show) {
        ensureRecordingBar();
        if (recordingBar != null) {
            recordingBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            recordSeconds = 0;
            if (tvRecordTimer != null) tvRecordTimer.setText("REC 0:00");
            if (recordTimerRunnable != null) handler.removeCallbacks(recordTimerRunnable);
            recordTimerRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!recording) return;
                    if (!voicePaused) {
                        recordSeconds++;
                    }
                    int m = recordSeconds / 60;
                    int s = recordSeconds % 60;
                    if (tvRecordTimer != null) {
                        String state = voicePaused ? "(duraklatildi)" : "(kayit acik)";
                        tvRecordTimer.setText(String.format(Locale.getDefault(), "REC %d:%02d  %s", m, s, state));
                    }
                    handler.postDelayed(this, 1000);
                }
            };
            handler.postDelayed(recordTimerRunnable, 1000);
        } else {
            if (recordTimerRunnable != null) handler.removeCallbacks(recordTimerRunnable);
        }
    }

    private void showChatTools() {
        java.util.ArrayList<String> opts = new java.util.ArrayList<String>();
        opts.add("Sohbette ara");
        opts.add("Medya galerisi");
        if (isGroup) opts.add("Grup uyeleri");
        final String[] items = opts.toArray(new String[opts.size()]);
        new AlertDialog.Builder(this)
                .setTitle("Araclar")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String s = items[which];
                        if (s.startsWith("Sohbet")) openSearchInChat();
                        else if (s.startsWith("Medya")) openMediaGallery();
                        else if (s.startsWith("Grup")) openGroupMembers();
                    }
                })
                .show();
    }

    private void startVoiceRecord() {
        try {

            voiceLegacyMode = android.os.Build.VERSION.SDK_INT < 24;
            voiceSegments.clear();
            voicePaused = false;
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            if (voiceLegacyMode) {
                // Eski cihazlarda (SDK<24) duraklatma, ayri segmentler kaydedip
                // ham AMR frame'lerini birlestirerek yapiliyor (mergeAmrSegments).
                // Bu yontem sadece AMR/3GP ham veri yapisinda guvenli calisir,
                // AAC/MP4 konteynerinde dosyayi bozar. O yuzden burada dokunmuyoruz.
                voiceFile = new File(getCacheDir(), "voice_" + System.currentTimeMillis() + "_0.amr");
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            } else {
                // Modern cihazlarda pause()/resume() native oldugu icin tek dosya
                // yeterli. AAC/M4A, AMR_NB'ye gore cok daha kaliteli (genis bant,
                // daha yuksek ornekleme + bit hizi) ve Android 4.0 ICS'ten beri
                // (API 10+ AAC encoder, API 14+ hedef minSdk ile) destekleniyor.
                voiceFile = new File(getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                recorder.setAudioEncodingBitRate(96000);
                recorder.setAudioSamplingRate(44100);
            }
            recorder.setOutputFile(voiceFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            showRecordingBar(true);
            updatePauseResumeUi();
            tvStatus.setText("SES KAYDI ACIK");
            Toast.makeText(this, "Kayit basladi - kirmizi panelden Duraklat/Gonder/Iptal", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Mikrofon hatasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            cancelVoice();
        }
    }

    private void pauseVoice() {
        if (!recording || voicePaused) return;
        try {
            if (!voiceLegacyMode) {
                if (android.os.Build.VERSION.SDK_INT >= 24) { recorder.pause(); }
            } else {
                if (recorder != null) {
                    try { recorder.stop(); } catch (Exception ignored) {}
                    try { recorder.release(); } catch (Exception ignored) {}
                }
                recorder = null;
                if (voiceFile != null && voiceFile.exists() && voiceFile.length() > 0) {
                    voiceSegments.add(voiceFile);
                }
                voiceFile = null;
            }
            voicePaused = true;
            updatePauseResumeUi();
            tvStatus.setText("Kayit duraklatildi");
        } catch (Exception e) {
            Toast.makeText(this, "Duraklatma hatasi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void resumeVoice() {
        if (!recording || !voicePaused) return;
        try {
            if (!voiceLegacyMode) {
                if (android.os.Build.VERSION.SDK_INT >= 24) { recorder.resume(); }
            } else {
                voiceFile = new File(getCacheDir(),
                        "voice_" + System.currentTimeMillis() + "_" + voiceSegments.size() + ".amr");
                recorder = new MediaRecorder();
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                recorder.setOutputFile(voiceFile.getAbsolutePath());
                recorder.prepare();
                recorder.start();
            }
            voicePaused = false;
            updatePauseResumeUi();
            tvStatus.setText("SES KAYDI ACIK");
        } catch (Exception e) {
            Toast.makeText(this, "Devam ettirme hatasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
            cancelVoice();
        }
    }

    private void updatePauseResumeUi() {
        if (btnPauseResume != null) {
            btnPauseResume.setText(voicePaused ? "Devam" : "Duraklat");
        }
    }

    private void cancelVoice() {
        try {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) {}
                recorder.release();
            }
        } catch (Exception ignored) {}
        recorder = null;
        recording = false;
        voicePaused = false;
        if (voiceFile != null && voiceFile.exists()) voiceFile.delete();
        voiceFile = null;
        for (int i = 0; i < voiceSegments.size(); i++) {
            File seg = voiceSegments.get(i);
            if (seg != null && seg.exists()) seg.delete();
        }
        voiceSegments.clear();
        showRecordingBar(false);
        tvStatus.setText("Kayit iptal");
    }

    private void stopVoiceAndSend() {
        if (voiceLegacyMode) {
            try {
                if (recorder != null) {
                    recorder.stop();
                    recorder.release();
                }
            } catch (Exception ignored) {

            }
            recorder = null;
            if (voiceFile != null && voiceFile.exists() && voiceFile.length() > 0) {
                voiceSegments.add(voiceFile);
            }
            voiceFile = null;
            recording = false;
            voicePaused = false;
            showRecordingBar(false);
            File merged = mergeAmrSegments(voiceSegments);
            for (int i = 0; i < voiceSegments.size(); i++) {
                File seg = voiceSegments.get(i);
                if (seg != null && seg.exists()) seg.delete();
            }
            voiceSegments.clear();
            if (merged == null) {
                Toast.makeText(this, "Bos kayit", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadFile(merged, "ses_" + System.currentTimeMillis() + ".amr");
            return;
        }

        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Kayit bitirme hatasi", Toast.LENGTH_SHORT).show();
            cancelVoice();
            return;
        }
        recorder = null;
        recording = false;
        voicePaused = false;
        showRecordingBar(false);
        if (voiceFile == null || !voiceFile.exists() || voiceFile.length() < 10) {
            Toast.makeText(this, "Bos kayit", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = voiceFile;
        voiceFile = null;

        uploadFile(f, "ses_" + System.currentTimeMillis() + ".m4a");
    }

    private File mergeAmrSegments(List<File> segments) {
        if (segments == null || segments.isEmpty()) return null;
        File out = new File(getCacheDir(), "voice_" + System.currentTimeMillis() + "_merged.amr");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            final int headerLen = 6;
            for (int i = 0; i < segments.size(); i++) {
                File seg = segments.get(i);
                if (seg == null || !seg.exists() || seg.length() == 0) continue;
                InputStream fis = new java.io.FileInputStream(seg);
                byte[] buf = new byte[4096];
                int toSkip = (i == 0) ? 0 : headerLen;
                int n;
                while ((n = fis.read(buf)) != -1) {
                    int start = 0;
                    if (toSkip > 0) {
                        int skipNow = Math.min(toSkip, n);
                        start = skipNow;
                        toSkip -= skipNow;
                    }
                    if (start < n) fos.write(buf, start, n - start);
                }
                fis.close();
            }
            fos.close();
            fos = null;
            return out.length() > headerLen ? out : null;
        } catch (Exception e) {
            return null;
        } finally {
            if (fos != null) { try { fos.close(); } catch (Exception ignored) {} }
        }
    }

    private void openSearchInChat() {
        final EditText input = new EditText(this);
        input.setHint("Ara...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);
        new AlertDialog.Builder(this)
                .setTitle("Sohbette ara")
                .setView(input)
                .setPositiveButton("Bul", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String q = input.getText().toString().trim().toLowerCase();
                        if (q.length() == 0) return;
                        int found = -1;
                        for (int i = 0; i < messages.size(); i++) {
                            String t = messages.get(i).displayLabel().toLowerCase();
                            if (t.contains(q)) { found = i; break; }
                        }
                        if (found >= 0) {
                            listMessages.setSelection(found);
                            Toast.makeText(ChatActivity.this, "Bulundu", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ChatActivity.this, "Sonuc yok", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void openMediaGallery() {
        java.util.ArrayList<String> labels = new java.util.ArrayList<String>();
        final java.util.ArrayList<Message> mediaMsgs = new java.util.ArrayList<Message>();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m.hasMedia() && !m.deleted) {
                mediaMsgs.add(m);
                String n = m.mediaOriginal != null ? m.mediaOriginal : m.mediaPath;
                labels.add((m.isMine ? "Sen: " : "") + n);
            }
        }
        if (mediaMsgs.isEmpty()) {
            Toast.makeText(this, "Medya yok", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = labels.toArray(new String[labels.size()]);
        new AlertDialog.Builder(this)
                .setTitle("Medya galerisi")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Message m = mediaMsgs.get(which);
                        if (isImageMessage(m)) openImageViewer(m);
                        else downloadMedia(m);
                    }
                })
                .show();
    }

    private void openGroupMembers() {
        if (!isGroup) return;
        Network.groupMembers(target, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result == null || result.startsWith("ERROR")) {
                    Toast.makeText(ChatActivity.this, "Uyeler alinamadi", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] lines = result.split("\n");
                final java.util.ArrayList<String> members = new java.util.ArrayList<String>();
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.length() == 0 || line.startsWith("OWNER:")) continue;
                    members.add(line);
                }
                final String[] items = members.toArray(new String[members.size()]);
                new AlertDialog.Builder(ChatActivity.this)
                        .setTitle("Grup · kod: " + target)
                        .setItems(items, null)
                        .setPositiveButton("Uye ekle", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) { promptAddMember(); }
                        })
                        .setNeutralButton("Uye cikar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) { promptRemoveMember(items); }
                        })
                        .setNegativeButton("Kapat", null)
                        .show();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void promptAddMember() {
        final EditText input = new EditText(this);
        input.setHint("Kullanici adi");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);
        new AlertDialog.Builder(this)
                .setTitle("Uye ekle")
                .setView(input)
                .setPositiveButton("Ekle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String u = input.getText().toString().trim().toLowerCase();
                        if (u.length() < 2) return;
                        Network.groupAdd(Prefs.getUUID(ChatActivity.this), target, u, new Network.Callback() {
                            @Override
                            public void onSuccess(String result) {
                                Toast.makeText(ChatActivity.this,
                                        result != null && result.startsWith("OK") ? "Eklendi" : String.valueOf(result),
                                        Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void promptRemoveMember(final String[] members) {
        if (members == null || members.length == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Cikarilacak uye")
                .setItems(members, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String u = members[which];
                        Network.groupRemove(Prefs.getUUID(ChatActivity.this), target, u, new Network.Callback() {
                            @Override
                            public void onSuccess(String result) {
                                Toast.makeText(ChatActivity.this,
                                        result != null && result.startsWith("OK") ? "Cikarildi" : String.valueOf(result),
                                        Toast.LENGTH_SHORT).show();
                            }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .show();
    }

    private boolean isImageMessage(Message m) {
        if (m.mediaMime != null && m.mediaMime.toLowerCase().startsWith("image/")) return true;
        String name = m.mediaOriginal != null ? m.mediaOriginal : m.mediaPath;
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private boolean isAudioMessage(Message m) {
        if (m.mediaMime != null) {
            String mime = m.mediaMime.toLowerCase();
            if (mime.startsWith("audio/")) return true;
        }
        String name = m.mediaOriginal != null ? m.mediaOriginal : m.mediaPath;
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".3gp") || lower.endsWith(".amr") || lower.endsWith(".mp3")
                || lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg")
                || lower.startsWith("ses_");
    }

    private void playAudio(final Message m) {
        if (m == null || !m.hasMedia() || m.mediaPath == null) return;
        final String stored = m.mediaPath;

        if (player != null && stored.equals(playingPath)) {
            try {
                if (player.isPlaying()) {
                    player.pause();
                    playPaused = true;
                    if (tvStatus != null) tvStatus.setText("Ses duraklatildi — tekrar dokun: devam");
                    if (adapter != null) adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Duraklatildi", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (playPaused) {
                    player.start();
                    playPaused = false;
                    if (tvStatus != null) tvStatus.setText("Oynatiliyor — dokun: duraklat");
                    Toast.makeText(this, "Devam", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Exception e) {
                stopPlayer();
            }
        }

        if (audioBusy) {
            Toast.makeText(this, "Ses henuz hazir degil, bekle...", Toast.LENGTH_SHORT).show();
            return;
        }
        stopPlayer();
        audioBusy = true;
        playPaused = false;
        playingPath = stored;
        tvStatus.setText("Ses indiriliyor...");
        final String disp = (m.mediaOriginal != null && m.mediaOriginal.length() > 0)
                ? m.mediaOriginal : stored;
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                File local = null;
                try {
                    String safe = stored.replace("/", "_").replace("..", "_");
                    local = new File(getCacheDir(), "play_" + safe);

                    if (local.exists() && local.length() >= 32) {
                        final File cached = local;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                audioBusy = false;
                                startLocalPlayer(cached, disp != null ? disp.toLowerCase() : "");
                            }
                        });
                        return;
                    }
                    if (local.exists()) local.delete();

                    URL url = new URL(Network.SERVER + "/api/media/" + stored);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(20000);
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    conn.setRequestProperty("User-Agent", "RetroChat-Android/1.0");
                    conn.setRequestProperty("Connection", "close");
                    int code = conn.getResponseCode();
                    String ctype = conn.getContentType();
                    if (code != 200) {
                        showPlayErr("HTTP " + code + (ctype != null ? (" " + ctype) : ""));
                        return;
                    }

                    if (ctype != null && ctype.toLowerCase().indexOf("text/html") >= 0) {
                        showPlayErr("Sunucu HTML dondurdu (medya yok)");
                        return;
                    }

                    InputStream in = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(local);
                    byte[] buf = new byte[4096];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                        total += n;
                        if (total > 8L * 1024 * 1024) break;
                    }
                    fos.flush();
                    fos.close();
                    in.close();

                    if (total < 32) {
                        if (local.exists()) local.delete();
                        showPlayErr("Dosya cok kucuk (" + total + " bayt)");
                        return;
                    }

                    byte[] head = new byte[12];
                    java.io.FileInputStream fis = new java.io.FileInputStream(local);
                    int hr = fis.read(head);
                    fis.close();
                    boolean ok = false;
                    if (hr >= 6) {

                        if (head[0] == '#' && head[1] == '!' && head[2] == 'A'
                                && head[3] == 'M' && head[4] == 'R') ok = true;

                        if (hr >= 8 && head[4] == 'f' && head[5] == 't'
                                && head[6] == 'y' && head[7] == 'p') ok = true;

                        if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') ok = true;

                        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0) ok = true;

                        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F') ok = true;
                    }
                    if (!ok) {

                        final long sz = total;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ChatActivity.this,
                                        "Uyari: ses imzasi bilinmiyor (" + sz + "b), deneniyor...",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    final File playFile = local;
                    final String low = disp.toLowerCase();
                    final long fsz = total;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvStatus.setText("Ses hazir (" + fsz + "b)");
                            startLocalPlayer(playFile, low);
                        }
                    });
                } catch (Exception e) {
                    showPlayErr(e.getMessage() != null ? e.getMessage() : "indirme");
                } finally {
                    if (conn != null) try { conn.disconnect(); } catch (Exception ignored) {}
                    audioBusy = false;
                }
            }
        });
    }

    private void startLocalPlayer(File file, String lowName) {
        String keepPath = playingPath;
        stopPlayer();
        playingPath = keepPath;
        playPaused = false;
        if (file == null || !file.exists() || file.length() < 32) {
            Toast.makeText(this, "Oynatma hatasi: dosya yok", Toast.LENGTH_SHORT).show();
            playingPath = null;
            return;
        }
        java.io.FileInputStream fis = null;
        try {
            player = new MediaPlayer();

            fis = new java.io.FileInputStream(file);
            player.setDataSource(fis.getFD());
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Toast.makeText(ChatActivity.this,
                            "Oynatma hatasi: " + what + "/" + extra,
                            Toast.LENGTH_LONG).show();
                    stopPlayer();
                    return true;
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (tvStatus != null) tvStatus.setText("Ses bitti");
                    stopPlayer();
                }
            });
            player.prepare();
            player.start();
            if (tvStatus != null) tvStatus.setText("Oynatiliyor — dokun: duraklat");
        } catch (Exception e1) {

            stopPlayer();
            try {
                player = new MediaPlayer();
                player.setDataSource(file.getAbsolutePath());
                player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Toast.makeText(ChatActivity.this,
                                "Oynatma hatasi: " + what + "/" + extra,
                                Toast.LENGTH_LONG).show();
                        stopPlayer();
                        return true;
                    }
                });
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        if (tvStatus != null) tvStatus.setText("Ses bitti");
                        stopPlayer();
                    }
                });
                player.prepare();
                player.start();
                if (tvStatus != null) tvStatus.setText("Oynatiliyor — dokun: duraklat");
            } catch (Exception e2) {
                String msg = e2.getMessage();
                if (msg == null) msg = "prepare failed";
                Toast.makeText(this, "Oynatma hatasi: " + msg, Toast.LENGTH_LONG).show();
                Toast.makeText(this, "Dosya: " + file.length() + " bayt / " + lowName,
                        Toast.LENGTH_LONG).show();
                try { file.delete(); } catch (Exception ignored) {}
                stopPlayer();
            }
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }

    private void showPlayErr(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                audioBusy = false;
                Toast.makeText(ChatActivity.this, "Oynatma hatasi: " + msg, Toast.LENGTH_LONG).show();
                if (tvStatus != null) tvStatus.setText("Ses hatasi");
            }
        });
    }

    private void stopPlayer() {
        try {
            if (player != null) {
                try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
                try { player.reset(); } catch (Exception ignored) {}
                try { player.release(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        player = null;
        playingPath = null;
        playPaused = false;
    }

    private void openImageViewer(final Message m) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER);
        final ImageView iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        TextView info = new TextView(this);
        String name = m.mediaOriginal != null ? m.mediaOriginal : m.mediaPath;
        info.setText(name);
        info.setTextColor(Color.LTGRAY);
        info.setGravity(Gravity.CENTER);
        info.setPadding(16, 8, 16, 8);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(8, 8, 8, 16);
        Button btnDl = new Button(this);
        btnDl.setText("Indir");
        btnDl.setBackgroundColor(0xFF660099);
        btnDl.setTextColor(Color.WHITE);
        btnDl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { downloadMedia(m); }
        });
        Button btnClose = new Button(this);
        btnClose.setText("Kapat");
        btnClose.setBackgroundColor(0xFF333333);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        bar.addView(btnDl);
        bar.addView(btnClose);
        root.addView(iv);
        root.addView(info);
        root.addView(bar);
        dialog.setContentView(root);
        dialog.show();
        loadBitmapAsync(m.mediaPath, 1280, new BitmapCallback() {
            @Override
            public void onBitmap(final Bitmap bmp) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (bmp != null) iv.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private interface BitmapCallback { void onBitmap(Bitmap bmp); }

    private void loadBitmapAsync(final String mediaPath, final int maxSide, final BitmapCallback cb) {
        if (mediaPath == null || mediaPath.length() == 0) { cb.onBitmap(null); return; }
        final String cacheKey = mediaPath + "_" + maxSide;
        if (thumbCache.containsKey(cacheKey)) { cb.onBitmap(thumbCache.get(cacheKey)); return; }
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                HttpURLConnection conn = null;
                try {
                    File local = new File(getCacheDir(), "img_" + mediaPath.replace("/", "_"));
                    if (!(local.exists() && local.length() > 0)) {
                        URL url = new URL(Network.SERVER + "/api/media/" + mediaPath);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                        if (conn.getResponseCode() == 200) {
                            InputStream in = conn.getInputStream();
                            FileOutputStream fos = new FileOutputStream(local);
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
                            fos.close();
                            in.close();
                        }
                    }
                    if (local.exists()) bmp = decodeSampled(local.getAbsolutePath(), maxSide);
                    if (bmp != null && maxSide <= 400) {
                        synchronized (thumbCache) {
                            if (thumbCache.size() > 40) thumbCache.clear();
                            thumbCache.put(cacheKey, bmp);
                        }
                    }
                } catch (Exception e) {
                    bmp = null;
                } finally {
                    if (conn != null) conn.disconnect();
                }
                cb.onBitmap(bmp);
            }
        });
    }

    private Bitmap decodeSampled(String path, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Dosya / Resim sec"), REQ_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "Secici acilamadi", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            String name = queryName(uri);
            long size = querySize(uri);
            if (size > Network.MAX_FILE_BYTES) {
                Toast.makeText(this, "5 GB'den buyuk dosya gonderilemez", Toast.LENGTH_LONG).show();
                return;
            }
            File cache = copyUriToCache(uri, name);
            if (cache == null) {
                Toast.makeText(this, "Dosya kopyalanamadi", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadFile(cache, name);
        } catch (Exception e) {
            Toast.makeText(this, "Hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String queryName(Uri uri) {
        String result = "file";
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && n.length() > 0) result = n;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return result;
    }

    private long querySize(Uri uri) {
        long size = -1;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) size = c.getLong(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return size;
    }

    private File copyUriToCache(Uri uri, String name) {
        try {
            File out = new File(getCacheDir(), "upload_" + System.currentTimeMillis() + "_" + name.replace("/", "_"));
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > Network.MAX_FILE_BYTES) {
                    fos.close();
                    in.close();
                    out.delete();
                    return null;
                }
                fos.write(buf, 0, n);
            }
            fos.close();
            in.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void uploadFile(final File file, final String displayName) {
        btnAttach.setEnabled(false);
        btnSend.setEnabled(false);
        tvStatus.setText("Gonderiliyor: " + displayName);
        final String uuid = Prefs.getUUID(this);
        final String myName = Prefs.getUsername(this);
        final String caption = etMessage.getText().toString().trim();
        final long fileSize = file.length();
        final String mimeGuess = guessMime(displayName);

        Network.uploadFile(uuid, target, isGroup, file, caption, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                btnAttach.setEnabled(true);
                btnSend.setEnabled(true);
                file.delete();
                if (result != null && result.startsWith("OK")) {
                    String stored = "", original = displayName;
                    String[] parts = result.split("\\|");
                    if (parts.length >= 2) stored = parts[1].trim();
                    if (parts.length >= 4 && parts[3].trim().length() > 0) original = parts[3].trim();
                    long ts = System.currentTimeMillis();
                    String timeStr = nowTime();
                    Message m = new Message(uuid, myName, caption, true, timeStr);
                    m.isGroup = isGroup;
                    m.mediaPath = stored;
                    m.mediaOriginal = original;
                    m.mediaSize = fileSize;
                    m.mediaMime = mimeGuess;
                    m.mediaType = 1;
                    if (parts.length >= 1) {
                        String idPart = parts[0].replace("OK:", "").trim();
                        if (idPart.length() > 0) m.id = idPart;
                    }
                    m.ts = ts;
                    messages.add(m);
                    recentLocalEchoes.add(m);
                    db.addMessageFull(target, myName, caption, timeStr, ts, true, isGroup,
                            stored, original, mimeGuess, fileSize);
                    adapter.notifyDataSetChanged();
                    listMessages.setSelection(messages.size() - 1);
                    etMessage.setText("");
                    clearReply();
                    tvStatus.setText("Gonderildi: " + original);
                } else {
                    tvStatus.setText("Hata: " + result);
                }
            }
            @Override
            public void onError(String error) {
                btnAttach.setEnabled(true);
                btnSend.setEnabled(true);
                file.delete();
                Toast.makeText(ChatActivity.this, "Yukleme hatasi: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String guessMime(String name) {
        if (name == null) return "application/octet-stream";
        String l = name.toLowerCase();
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".webp")) return "image/webp";
        if (l.endsWith(".bmp")) return "image/bmp";
        if (l.endsWith(".3gp")) return "audio/3gpp";
        if (l.endsWith(".amr")) return "audio/amr";
        if (l.endsWith(".m4a")) return "audio/mp4";
        return "application/octet-stream";
    }

    private void downloadMedia(final Message m) {
        if (!m.hasMedia()) return;
        final String stored = m.mediaPath;
        final String saveName = (m.mediaOriginal != null && m.mediaOriginal.length() > 0) ? m.mediaOriginal : stored;
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Indiriliyor: " + saveName);
        pd.setCancelable(true);
        pd.show();
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(Network.SERVER + "/api/media/" + stored);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(300000);
                    conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                    if (conn.getResponseCode() != 200) {
                        showToast("HTTP " + conn.getResponseCode());
                        return;
                    }
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) dir = getExternalFilesDir(null);
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    File out = new File(dir, saveName);
                    int n = 1;
                    while (out.exists()) {
                        int dot = saveName.lastIndexOf('.');
                        String base = dot > 0 ? saveName.substring(0, dot) : saveName;
                        String ext = dot > 0 ? saveName.substring(dot) : "";
                        out = new File(dir, base + "_" + n + ext);
                        n++;
                    }
                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
                    fos.close();
                    in.close();
                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try { pd.dismiss(); } catch (Exception ignored) {}
                            Toast.makeText(ChatActivity.this, "Indirildi:\n" + path, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try { pd.dismiss(); } catch (Exception ignored) {}
                            Toast.makeText(ChatActivity.this, "Indirme hatasi: " + err, Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(ChatActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadLocal() {
        messages = db.getMessages(target);
        adapter.notifyDataSetChanged();
        if (messages.size() > 0) listMessages.setSelection(messages.size() - 1);
        tvStatus.setText(messages.size() + " mesaj");
    }

    private void sendText() {
        String text = etMessage.getText().toString().trim();
        if (text.length() == 0) return;
        etMessage.setText("");
        btnSend.setEnabled(false);
        tvStatus.setText("Gonderiliyor...");
        final String uuid = Prefs.getUUID(this);
        final String myName = Prefs.getUsername(this);
        final String finalText = text;
        final long ts = System.currentTimeMillis();
        final String timeStr = nowTime();
        final String replyId = replyTarget != null ? replyTarget.id : null;
        final String replyPrev = replyTarget != null ? replyTarget.displayLabel() : null;
        final Message localReply = replyTarget;

        Network.Callback cb = new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                btnSend.setEnabled(true);
                if (result != null && result.startsWith("OK")) {
                    Message m = new Message(uuid, myName, finalText, true, timeStr);
                    m.isGroup = isGroup;
                    if (result.length() > 3) m.id = result.substring(3).trim();
                    if (localReply != null) {
                        m.replyToId = replyId;
                        m.replyPreview = replyPrev;
                    }
                    m.ts = ts;
                    messages.add(m);
                    recentLocalEchoes.add(m);
                    db.addMessage(target, myName, finalText, timeStr, ts, true, isGroup);
                    adapter.notifyDataSetChanged();
                    listMessages.setSelection(messages.size() - 1);
                    clearReply();
                    tvStatus.setText("Gonderildi");
                } else {
                    tvStatus.setText("Hata: " + result);
                }
            }
            @Override
            public void onError(String error) {
                btnSend.setEnabled(true);
                tvStatus.setText("Sunucuya baglanilamiyor");
                Toast.makeText(ChatActivity.this, "Sunucu kapali veya ag hatasi", Toast.LENGTH_LONG).show();
            }
        };

        if (isGroup) {
            Network.sendGroup(uuid, target, text, cb);
        } else {
            Network.sendPrivateReply(uuid, target, text, replyId, replyPrev, cb);
        }
    }

    private void refreshFromServer() {
        refreshFromServer(false);
    }

    private void refreshFromServer(final boolean silent) {
        if (!silent) tvStatus.setText("Sunucudan yukleniyor...");
        final String uuid = Prefs.getUUID(this);
        final String myName = Prefs.getUsername(this);
        if (myName == null) return;

        Network.Callback cb = new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result == null || result.startsWith("ERROR")) {
                    loadLocal();
                    if (isGroup && result != null && result.contains("forbidden")) {
                        tvStatus.setText("Bu gruba uye degilsin");
                    }
                    return;
                }
                if (result.equals("EMPTY")) {
                    loadLocal();
                    if (messages.isEmpty()) {
                        tvStatus.setText(isGroup ? "Grup acik — mesaj yaz veya Uyeler" : "Mesaj yok");
                    }
                    return;
                }

                List<Message> parsed = new ArrayList<Message>();
                String[] lines = result.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.length() == 0) continue;
                    String[] p = line.split("\\|", -1);
                    if (p.length < 4) continue;

                    String fromName = p[1] != null ? p[1].trim() : "";
                    String text = p[2] != null ? p[2] : "";
                    long ts = 0;
                    try { ts = Long.parseLong(p[3].trim()); } catch (Exception e) {}
                    String fromLower = fromName.toLowerCase();
                    if (fromLower.equals("?") || fromLower.length() == 0) continue;

                    if (!UiUtil.looksLikeRealPeer(fromName)) continue;

                    String media = p.length > 6 && p[6] != null ? p[6].trim() : "";

                    if (media.length() > 0 && !UiUtil.looksLikeRealMedia(media)) {
                        media = "";
                    }
                    String mime = p.length > 7 && p[7] != null ? p[7].trim() : "";
                    long msize = 0;
                    try { if (p.length > 8 && p[8].length() > 0) msize = Long.parseLong(p[8].trim()); } catch (Exception e) {}
                    String original = p.length > 9 && p[9] != null && p[9].trim().length() > 0 ? p[9].trim() : media;
                    String mid = p.length > 10 ? p[10].trim() : "";
                    String replyTo = p.length > 11 ? p[11].trim() : "";
                    String replyPrev = p.length > 12 ? p[12].trim() : "";
                    if ("0".equals(replyTo) || "1".equals(replyTo) || "?".equals(replyTo)) replyTo = "";
                    if ("0".equals(replyPrev) || "1".equals(replyPrev) || "?".equals(replyPrev)) replyPrev = "";
                    if (original != null && ("0".equals(original) || "1".equals(original))) original = media;
                    boolean read = p.length > 13 && "1".equals(p[13].trim());
                    boolean deleted = p.length > 14 && "1".equals(p[14].trim());

                    boolean isMine = fromLower.equals(myName.toLowerCase());
                    Message tmp = new Message(null, isMine ? myName : fromName, text, isMine, formatTs(ts));
                    tmp.id = mid;
                    tmp.ts = ts;
                    tmp.isGroup = isGroup;
                    tmp.mediaPath = media.length() > 0 ? media : null;
                    tmp.mediaOriginal = original;
                    tmp.mediaMime = mime;
                    tmp.mediaSize = msize;
                    tmp.replyToId = replyTo;
                    tmp.replyPreview = replyPrev;
                    tmp.read = read;
                    tmp.deleted = deleted;
                    if (tmp.hasMedia()) tmp.mediaType = 1;

                    boolean emptyBody = (tmp.text == null || tmp.text.length() == 0) && !tmp.hasMedia();
                    if (emptyBody && !tmp.deleted) continue;
                    parsed.add(tmp);
                }
                mergeRecentLocalEchoes(parsed);
                messages = parsed;
                db.replacePeerMessages(target, messages, isGroup);
                adapter.notifyDataSetChanged();
                if (!silent && messages.size() > 0) {
                    listMessages.setSelection(messages.size() - 1);
                }
                if (!silent) tvStatus.setText(messages.size() + " mesaj");

                markPeerRead();
            }
            @Override
            public void onError(String error) {
                loadLocal();
                if (!silent) tvStatus.setText("Sunucuya baglanilamiyor — yerel");
            }
        };

        if (isGroup) {
            Network.fetchGroupMessages(uuid, target, cb);
        } else {
            Network.fetchThread(uuid, target, cb);
        }
    }

    private void mergeRecentLocalEchoes(List<Message> parsed) {
        long now = System.currentTimeMillis();
        for (int i = recentLocalEchoes.size() - 1; i >= 0; i--) {
            Message echo = recentLocalEchoes.get(i);
            boolean found = false;
            for (int j = 0; j < parsed.size(); j++) {
                Message pm = parsed.get(j);
                if (echo.id != null && echo.id.length() > 0) {
                    if (echo.id.equals(pm.id)) { found = true; break; }
                } else if (pm.isMine && Math.abs(pm.ts - echo.ts) < 4000
                        && strEq(pm.text, echo.text) && strEq(pm.mediaPath, echo.mediaPath)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                // Sunucu artik bu mesaji dondurdu, echo cache'inden kaldirilabilir.
                recentLocalEchoes.remove(i);
            } else if (now - echo.ts > 120000) {
                // 2 dakikadir sunucu cevabinda yok. Cache'i temizle (sonsuza kadar
                // biriktirmesin) ama mesaji ekrandan SILME - kullanicinin gordugu
                // "gonderdim ama ekrandan kayboldu" bug'i tam olarak buradaki eski
                // "sessizce at" davranisindan kaynaklaniyordu.
                recentLocalEchoes.remove(i);
                parsed.add(echo);
            } else {
                // Henuz sunucu cevabinda yok ama cok da eski degil: gostermeye
                // devam et, bir sonraki fetch'te tekrar kontrol edilecek.
                parsed.add(echo);
            }
        }
        if (parsed.size() > 1) {
            java.util.Collections.sort(parsed, new java.util.Comparator<Message>() {
                @Override
                public int compare(Message a, Message b) {
                    if (a.ts < b.ts) return -1;
                    if (a.ts > b.ts) return 1;
                    return 0;
                }
            });
        }
    }

    private static boolean strEq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private String nowTime() {
        return TimeUtil.formatClock(System.currentTimeMillis());
    }

    private String formatTs(long ts) {
        return TimeUtil.formatClock(ts);
    }

    private boolean sameCluster(int position) {
        if (position <= 0 || position >= messages.size()) return false;
        Message a = messages.get(position - 1);
        Message b = messages.get(position);
        if (a.deleted || b.deleted) return false;
        if (a.isMine != b.isMine) return false;
        if (!a.isMine) {
            String na = a.fromName != null ? a.fromName : "";
            String nb = b.fromName != null ? b.fromName : "";
            if (!na.equalsIgnoreCase(nb)) return false;
        }

        if (a.ts > 0 && b.ts > 0 && Math.abs(b.ts - a.ts) > 5L * 60 * 1000) return false;
        return true;
    }

    private boolean isClusterEnd(int position) {
        if (position < 0 || position >= messages.size() - 1) return true;
        return !sameCluster(position + 1);
    }

    private class MessageAdapter extends BaseAdapter {
        @Override public int getCount() { return messages.size(); }
        @Override public Object getItem(int position) { return messages.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_message, parent, false);
            }
            final Message m = messages.get(position);

            boolean clustered = sameCluster(position);
            boolean clusterEnd = isClusterEnd(position);
            int topPad = clustered ? AppTheme.dp(convertView, 1) : AppTheme.dp(convertView, 4);
            int botPad = clusterEnd ? AppTheme.dp(convertView, 4) : AppTheme.dp(convertView, 1);
            convertView.setPadding(
                    AppTheme.dp(convertView, 6), topPad,
                    AppTheme.dp(convertView, 6), botPad);

            LinearLayout bubble = (LinearLayout) convertView.findViewById(R.id.bubbleRoot);
            TextView tvFrom = (TextView) convertView.findViewById(R.id.tvFrom);
            TextView tvText = (TextView) convertView.findViewById(R.id.tvText);
            TextView tvTime = (TextView) convertView.findViewById(R.id.tvTime);
            TextView tvDate = (TextView) convertView.findViewById(R.id.tvDateHeader);
            final ImageView imgMedia = (ImageView) convertView.findViewById(R.id.imgMedia);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bubble.getLayoutParams();
            if (lp == null) {
                lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int maxBubble = (int) (screenW * 0.78f);
            if (maxBubble < 120) maxBubble = screenW - AppTheme.dp(convertView, 24);
            int maxText = maxBubble - AppTheme.dp(convertView, 28);
            if (tvText != null) {
                tvText.setMaxWidth(maxText);
                tvText.setSingleLine(false);
                tvText.setHorizontallyScrolling(false);
            }
            TextView tvReplySnippet2 = (TextView) convertView.findViewById(R.id.tvReplySnippet);
            if (tvReplySnippet2 != null) tvReplySnippet2.setMaxWidth(maxText);

            if (theme == null) theme = AppTheme.get(Prefs.getThemeId(ChatActivity.this));
            View replyQuote = convertView.findViewById(R.id.replyQuote);
            TextView tvReplyAuthor = (TextView) convertView.findViewById(R.id.tvReplyAuthor);
            TextView tvReplySnippet = (TextView) convertView.findViewById(R.id.tvReplySnippet);
            View replyStripe = convertView.findViewById(R.id.replyStripe);

            if (theme.terminalMode) {
                bubble.setBackgroundColor(0x00000000);
                bubble.setPadding(0, 0, 0, 0);
                lp.gravity = Gravity.START;
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.leftMargin = 0;
                lp.rightMargin = 0;
                bubble.setLayoutParams(lp);
                theme.applyTerminalFont(tvText);
                theme.applyTerminalFont(tvTime);
                if (tvTime != null) tvTime.setVisibility(View.GONE);
                if (imgMedia != null) imgMedia.setVisibility(View.GONE);
                if (replyQuote != null) replyQuote.setVisibility(View.GONE);
                tvFrom.setVisibility(View.GONE);
            } else {

                android.graphics.drawable.Drawable bubbleBg;
                if (theme.id == AppTheme.ID_AERO) {
                    float dens = convertView.getResources().getDisplayMetrics().density;
                    bubbleBg = theme.glassBubble(m.isMine, dens);
                } else {
                    android.graphics.drawable.GradientDrawable gd = theme.bubbleDrawable(m.isMine);
                    if (clustered || !clusterEnd) {
                        float d = convertView.getResources().getDisplayMetrics().density;
                        float big = theme.cornerRadiusDp * d;
                        float small = Math.max(4f * d, big * 0.35f);
                        if (m.isMine) {
                            gd.setCornerRadii(new float[]{
                                    big, big, big, big,
                                    (clusterEnd ? big : small), (clusterEnd ? big : small),
                                    big, big
                            });
                        } else {
                            gd.setCornerRadii(new float[]{
                                    big, big, big, big,
                                    big, big,
                                    (clusterEnd ? big : small), (clusterEnd ? big : small)
                            });
                        }
                    }
                    bubbleBg = gd;
                }
                bubble.setBackgroundDrawable(bubbleBg);
                lp.gravity = m.isMine ? Gravity.END : Gravity.START;
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;

                bubble.setLayoutParams(lp);
                int padH = AppTheme.dp(bubble, theme.id == AppTheme.ID_AERO ? 14 : 12);
                int padV = AppTheme.dp(bubble, theme.id == AppTheme.ID_AERO ? 10 : 8);
                bubble.setPadding(padH, padV, padH, AppTheme.dp(bubble, 6));
                theme.applyTerminalFont(tvText);
            }

            if (isGroup && !m.isMine && !theme.terminalMode && !clustered) {
                String senderName = (m.fromName != null && m.fromName.length() > 0) ? m.fromName : "?";
                tvFrom.setText(senderName);
                tvFrom.setTextColor(theme.accent2);
                tvFrom.setVisibility(View.VISIBLE);
            } else {
                tvFrom.setVisibility(View.GONE);
            }

            boolean showDate = m.ts > 0;
            if (showDate && position > 0) {
                Message prev = messages.get(position - 1);
                showDate = !TimeUtil.sameDay(prev.ts, m.ts);
            }
            if (showDate && tvDate != null) {
                tvDate.setVisibility(View.VISIBLE);
                tvDate.setText(TimeUtil.formatDateHeader(m.ts));
            } else if (tvDate != null) {
                tvDate.setVisibility(View.GONE);
            }

            String timeLine = (m.time != null && m.time.length() > 0)
                    ? m.time : TimeUtil.formatClock(m.ts);
            if (m.isMine && !m.deleted) {
                timeLine = timeLine + (m.read ? "  ✓✓" : "  ✓");
            }
            tvTime.setText(timeLine);

            if (m.deleted) {
                imgMedia.setVisibility(View.GONE);
                tvText.setVisibility(View.VISIBLE);
                tvText.setText("Bu mesaj silindi");
                tvText.setTextColor(0xFFAAAAAA);
                return convertView;
            }
            tvText.setTextColor(m.isMine ? theme.bubbleText : theme.bubbleTextOther);

            if (theme.terminalMode) {

                String nick = m.isMine ? Prefs.getUsername(ChatActivity.this) : m.fromName;
                if (nick == null || nick.length() == 0) nick = m.isMine ? "sen" : "???";
                String clock = (m.time != null && m.time.length() > 0) ? m.time : TimeUtil.formatClock(m.ts);
                String payload;
                if (m.deleted) payload = "(silindi)";
                else if (m.hasMedia()) payload = m.displayLabel().replace("\n", " ");
                else payload = m.text != null ? m.text : "";
                payload = payload.replace("\n", " ");
                String line = "[" + clock + "] <" + nick + "> " + payload;
                if (m.isMine && !m.deleted) line = line + (m.read ? " ++" : " +");
                tvText.setText(line);
                tvText.setTextColor(m.isMine ? theme.accent : theme.bubbleTextOther);
                tvText.setVisibility(View.VISIBLE);
                convertView.setPadding(AppTheme.dp(convertView, 4), AppTheme.dp(convertView, 2),
                        AppTheme.dp(convertView, 4), AppTheme.dp(convertView, 2));
                return convertView;
            }

            StringBuilder body = new StringBuilder();

            if (replyQuote != null) {
                if (m.replyPreview != null && m.replyPreview.length() > 0
                        && !"0".equals(m.replyPreview) && !"?".equals(m.replyPreview)) {
                    replyQuote.setVisibility(View.VISIBLE);
                    replyQuote.setBackgroundDrawable(theme.rounded(theme.replyBg, AppTheme.dp(replyQuote, 8)));
                    if (replyStripe != null) replyStripe.setBackgroundColor(theme.replyBar);
                    if (tvReplyAuthor != null) {
                        tvReplyAuthor.setText("Yanit");
                        tvReplyAuthor.setTextColor(theme.replyBar);
                    }
                    if (tvReplySnippet != null) {
                        String sn = m.replyPreview;
                        if (sn.length() > 80) sn = sn.substring(0, 80) + "…";
                        tvReplySnippet.setText(sn);
                        tvReplySnippet.setTextColor(theme.replyText);
                    }
                } else {
                    replyQuote.setVisibility(View.GONE);
                }
            }

            if (m.hasMedia() && isImageMessage(m)) {
                imgMedia.setVisibility(View.VISIBLE);
                imgMedia.setImageResource(android.R.drawable.ic_menu_gallery);
                imgMedia.setTag(m.mediaPath);
                loadBitmapAsync(m.mediaPath, 320, new BitmapCallback() {
                    @Override
                    public void onBitmap(final Bitmap bmp) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (bmp != null && m.mediaPath != null && m.mediaPath.equals(imgMedia.getTag())) {
                                    imgMedia.setImageBitmap(bmp);
                                }
                            }
                        });
                    }
                });
                if (m.text != null && m.text.length() > 0) body.append(m.text).append("\n");
                body.append("Dokun: buyut");
                tvText.setText(body.toString());
                tvText.setVisibility(View.VISIBLE);
            } else if (m.hasMedia() && isAudioMessage(m)) {
                imgMedia.setVisibility(View.GONE);
                String tip = "Dokun: oynat";
                if (m.mediaPath != null && m.mediaPath.equals(playingPath) && player != null) {
                    tip = playPaused ? "Dokun: devam" : "Dokun: duraklat";
                }
                body.append("🎤 ").append(m.displayLabel()).append("\n").append(tip);
                tvText.setText(body.toString());
                tvText.setVisibility(View.VISIBLE);
            } else if (m.hasMedia()) {
                imgMedia.setVisibility(View.GONE);
                body.append(m.displayLabel()).append("\nDokun: indir");
                tvText.setText(body.toString());
                tvText.setVisibility(View.VISIBLE);
            } else {
                imgMedia.setVisibility(View.GONE);
                body.append(m.text != null ? m.text : "");
                tvText.setText(body.toString());
                tvText.setVisibility(View.VISIBLE);
            }
            return convertView;
        }
    }
}
