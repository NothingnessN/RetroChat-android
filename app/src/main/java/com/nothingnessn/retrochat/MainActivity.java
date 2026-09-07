package com.nothingnessn.retrochat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private TextView tvMyName, tvStatus;
    private ListView listChats;
    private LocalDb db;
    private List<LocalDb.ChatItem> chats = new ArrayList<LocalDb.ChatItem>();
    private ChatListAdapter adapter;
    private boolean syncing = false;

    private final BroadcastReceiver chatsUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (db == null) return;
            reloadChats();

            if (!syncing) syncFromServer();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Prefs.isLoggedIn(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        UiUtil.setupEdgeToEdge(this);
        db = new LocalDb(this);
        try { db.purgeGhostChats(); } catch (Exception ignored) {}

        tvMyName = (TextView) findViewById(R.id.tvMyName);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        listChats = (ListView) findViewById(R.id.listChats);
        Button btnSearch = (Button) findViewById(R.id.btnSearch);
        Button btnNewGroup = (Button) findViewById(R.id.btnNewGroup);

        tvMyName.setText("@" + Prefs.getUsername(this));

        adapter = new ChatListAdapter();
        listChats.setAdapter(adapter);
        reloadChats();

        try {
            View hdr = tvMyName != null ? (View) tvMyName.getParent() : null;
            if (hdr instanceof ViewGroup) {
                Button btnTheme = new Button(this);
                btnTheme.setText("Tema");
                btnTheme.setTextColor(0xFFFFFFFF);
                btnTheme.setTextSize(12);
                btnTheme.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        startActivity(new Intent(MainActivity.this, ThemeActivity.class));
                    }
                });
                ((ViewGroup) hdr).addView(btnTheme);
            }
        } catch (Exception ignored) {}
        applyMainTheme();

        checkServerAndSync();
        PollService.start(this);
        requestNotifyPermission();

        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearchDialog();
            }
        });

        btnNewGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNewGroupDialog();
            }
        });

        listChats.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= chats.size()) return;
                LocalDb.ChatItem item = chats.get(position);
                openChat(item.title != null ? item.title : item.peer, item.peer, item.isGroup);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void applyMainTheme() {
        AppTheme t = AppTheme.get(Prefs.getThemeId(this));
        View root = findViewById(android.R.id.content);
        if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
            BgHelper.apply(this, ((ViewGroup) root).getChildAt(0), t);
        } else if (root != null) {
            BgHelper.apply(this, root, t);
        }

        if (tvMyName != null) {
            View p = (View) tvMyName.getParent();
            if (p != null) {
                if (t.id == AppTheme.ID_AERO || t.id == AppTheme.ID_AURORA || t.id == AppTheme.ID_MIDNIGHT) {
                    android.graphics.drawable.GradientDrawable hd = t.rounded(t.headerBg, 0);
                    p.setBackgroundDrawable(hd);
                } else {
                    p.setBackgroundColor(t.headerBg);
                }
            }
            tvMyName.setTextColor(t.headerText);
        }
        TextView tvTitle = (TextView) findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setTextColor(t.headerText);

        if (tvStatus != null) tvStatus.setTextColor(t.statusText);

        float rBtn = AppTheme.dp(this.getWindow().getDecorView(), t.cornerRadiusDp > 0 ? t.cornerRadiusDp : 14);
        Button btnSearch = (Button) findViewById(R.id.btnSearch);
        Button btnNewGroup = (Button) findViewById(R.id.btnNewGroup);
        styleRoundButton(btnSearch, t.accent, rBtn, t);
        styleRoundButton(btnNewGroup, t.accent2 != 0 ? t.accent2 : t.accent, rBtn, t);

        try {
            View hdr = tvMyName != null ? (View) tvMyName.getParent() : null;
            if (hdr instanceof ViewGroup) {
                ViewGroup hg = (ViewGroup) hdr;
                for (int i = 0; i < hg.getChildCount(); i++) {
                    View ch = hg.getChildAt(i);
                    if (ch instanceof Button) {
                        styleRoundButton((Button) ch, t.accent, rBtn * 0.7f, t);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (listChats != null) {
            try {
                listChats.setDivider(new android.graphics.drawable.ColorDrawable(t.borderSoft));
                listChats.setDividerHeight(AppTheme.dp(listChats, 6));
                listChats.setPadding(
                        AppTheme.dp(listChats, 8), AppTheme.dp(listChats, 4),
                        AppTheme.dp(listChats, 8), AppTheme.dp(listChats, 8));
                listChats.setClipToPadding(false);
            } catch (Exception ignored) {}
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void styleRoundButton(Button b, int color, float radiusPx, AppTheme t) {
        if (b == null) return;
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        if (t.id == AppTheme.ID_AERO) {
            d.setStroke(1, t.borderSoft);
        } else if (t.id == AppTheme.ID_MIDNIGHT) {
            d.setStroke(1, t.borderSoft);
        }
        b.setBackgroundDrawable(d);
        b.setTextColor(0xFFFFFFFF);
        try {
            b.setPadding(AppTheme.dp(b, 12), AppTheme.dp(b, 8),
                    AppTheme.dp(b, 12), AppTheme.dp(b, 8));
        } catch (Exception ignored) {}
    }

    private void requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 91);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            IntentFilter f = new IntentFilter(PollService.ACTION_CHATS_UPDATED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(chatsUpdatedReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(chatsUpdatedReceiver, f);
            }
        } catch (Exception ignored) {}
        if (db != null) {
            applyMainTheme();
            reloadChats();
            if (!syncing) checkServerAndSync();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(chatsUpdatedReceiver); } catch (Exception ignored) {}
    }

    private void checkServerAndSync() {
        tvStatus.setText("Sunucu kontrol ediliyor...");
        Network.ping(new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result != null && result.startsWith("OK")) {
                    tvStatus.setText("Sunucu bagli");
                    syncFromServer();
                } else {
                    tvStatus.setText("Sunucu yanit vermedi");
                    Toast.makeText(MainActivity.this, "Sunucuya baglanilamiyor", Toast.LENGTH_LONG).show();
                    reloadChats();
                }
            }

            @Override
            public void onError(String error) {
                tvStatus.setText("Sunucu kapali / erisilemiyor");
                Toast.makeText(MainActivity.this,
                        "Sunucuya baglanilamiyor",
                        Toast.LENGTH_LONG).show();
                reloadChats();
            }
        });
    }

    private void reloadChats() {
        chats = db.getChats();
        adapter.notifyDataSetChanged();
        if (chats.isEmpty()) {
            tvStatus.setText("Sohbet yok - senkron bekleniyor...");
        } else {
            tvStatus.setText(chats.size() + " sohbet");
        }
    }

    private void syncFromServer() {
        if (syncing) return;
        syncing = true;
        tvStatus.setText("Sunucudan senkron...");

        final String uuid = Prefs.getUUID(this);
        if (Prefs.getUsername(this) == null) {
            syncing = false;
            return;
        }

        Network.fetchChats(uuid, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                applyChatsPayload(result);
                reloadChats();
                syncing = false;
                tvStatus.setText(chats.size() + " sohbet");
            }

            @Override
            public void onError(String error) {
                syncing = false;
                reloadChats();
                tvStatus.setText(chats.size() + " sohbet (yerel)");
            }
        });
    }

    private void applyChatsPayload(String result) {
        if (result == null || result.startsWith("ERROR")) return;
        if (result.equals("EMPTY")) return;
        String[] lines = result.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 4) continue;
            String kind = p[0];
            String id = p[1];
            String title = p[2];
            String preview = p[3];
            long ts = 0;
            try { if (p.length > 4) ts = Long.parseLong(p[4].trim()); } catch (Exception ignored) {}
            boolean group = "group".equals(kind);

            if (!UiUtil.looksLikeRealPeer(id)) continue;
            db.upsertChat(id, title, preview, formatTs(ts), ts > 0 ? ts : System.currentTimeMillis(),
                    group, group ? id : null);
        }
    }

    private String formatTs(long ts) {
        return TimeUtil.formatList(ts);
    }

    private void openSearchDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Kullanici adi");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);

        new AlertDialog.Builder(this)
                .setTitle("Kullanici Ara")
                .setView(input)
                .setPositiveButton("Ara", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String q = input.getText().toString().trim();
                        if (q.length() < 2) {
                            Toast.makeText(MainActivity.this, "En az 2 karakter", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        doSearch(q);
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void doSearch(final String query) {
        tvStatus.setText("Araniyor: " + query);
        Network.searchUser(query, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result != null && result.startsWith("OK")) {
                    String name = query;
                    int idx = result.indexOf(':');
                    if (idx > 0 && idx < result.length() - 1) {
                        name = result.substring(idx + 1).trim();
                    }
                    db.upsertChat(name, name, "", nowTime(), System.currentTimeMillis(), false, null);
                    openChat(name, name, false);
                } else if (result != null && result.contains("NOTFOUND")) {
                    tvStatus.setText("Bu isimde biri yok: " + query);
                    Toast.makeText(MainActivity.this, "Kullanici bulunamadi", Toast.LENGTH_SHORT).show();
                } else {
                    tvStatus.setText("Sonuc: " + result);
                }
            }

            @Override
            public void onError(String error) {
                tvStatus.setText("Hata: " + error);
            }
        });
    }

    private void openNewGroupDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Grup adi veya kod");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);

        new AlertDialog.Builder(this)
                .setTitle("Grup")
                .setView(input)
                .setPositiveButton("Olustur", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() < 2) {
                            Toast.makeText(MainActivity.this, "En az 2 karakter", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (Network.looksLikeGroupId(name)) {
                            doJoinGroup(name);
                        } else {
                            doCreateGroup(name);
                        }
                    }
                })
                .setNeutralButton("Katil", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String code = input.getText().toString().trim();
                        if (code.length() < 2) {
                            Toast.makeText(MainActivity.this, "Grup kodu yaz", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        doJoinGroup(code);
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void doJoinGroup(final String groupId) {
        tvStatus.setText("Gruba katilinıyor...");
        Network.joinGroup(Prefs.getUUID(this), groupId, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result == null || result.startsWith("ERROR")) {
                    tvStatus.setText(result != null ? result : "Katilinamadi");
                    return;
                }
                db.upsertChat(groupId, "Grup", "Grup sohbeti", nowTime(), System.currentTimeMillis(), true, groupId);
                openChat("Grup", groupId, true);
            }
            @Override
            public void onError(String error) {
                tvStatus.setText("Hata: " + error);
            }
        });
    }

    private void doCreateGroup(final String name) {
        tvStatus.setText("Grup olusturuluyor...");
        String uuid = Prefs.getUUID(this);
        Network.createGroup(uuid, name, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (result != null && result.startsWith("OK")) {
                    String groupId = name;
                    int idx = result.indexOf(':');
                    if (idx > 0) groupId = result.substring(idx + 1).trim();
                    final String gid = groupId;
                    db.upsertChat(gid, "Grup: " + name, "Yeni grup", nowTime(), System.currentTimeMillis(), true, gid);
                    promptFirstMember(gid, "Grup: " + name);
                } else {
                    tvStatus.setText("Grup hatasi: " + result);
                }
            }

            @Override
            public void onError(String error) {
                tvStatus.setText("Hata: " + error);
            }
        });
    }

    private void promptFirstMember(final String groupId, final String title) {
        final EditText input = new EditText(this);
        input.setHint("Kullanici adi (bos = atla)");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFAAAAAA);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(24, 16, 24, 16);
        new AlertDialog.Builder(this)
                .setTitle("Ilk uyeyi ekle")
                .setView(input)
                .setPositiveButton("Ekle", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String u = input.getText().toString().trim();
                        if (u.length() >= 2) {
                            Network.groupAdd(Prefs.getUUID(MainActivity.this), groupId, u, new Network.Callback() {
                                @Override public void onSuccess(String result) {
                                    openChat(title, groupId, true);
                                }
                                @Override public void onError(String error) {
                                    openChat(title, groupId, true);
                                }
                            });
                        } else {
                            openChat(title, groupId, true);
                        }
                    }
                })
                .setNegativeButton("Atla", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openChat(title, groupId, true);
                    }
                })
                .show();
    }

    private String nowTime() {
        return TimeUtil.formatClock(System.currentTimeMillis());
    }

    private void openChat(String title, String target, boolean isGroup) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("title", title);
        i.putExtra("target", target);
        i.putExtra("isGroup", isGroup);
        startActivity(i);
    }

    private class ChatListAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return chats.size();
        }

        @Override
        public Object getItem(int position) {
            return chats.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_chat, parent, false);
            }
            LocalDb.ChatItem item = chats.get(position);
            TextView t1 = (TextView) convertView.findViewById(R.id.tvChatRowTitle);
            TextView t2 = (TextView) convertView.findViewById(R.id.tvChatRowPreview);
            TextView tTime = (TextView) convertView.findViewById(R.id.tvChatRowTime);
            String name = item.title != null ? item.title : item.peer;
            t1.setText(item.isGroup ? ("Grup: " + name.replace("Grup: ", "").replace("# ", "")) : ("@ " + name));
            AppTheme th = AppTheme.get(Prefs.getThemeId(MainActivity.this));
            if (item.isGroup) t1.setTextColor(th.accent);
            else t1.setTextColor(th.chatRowTitle);
            t2.setTextColor(th.chatRowPreview);
            if (tTime != null) tTime.setTextColor(th.statusText);

            int rowBg;
            if (th.id == AppTheme.ID_AERO) {
                rowBg = 0x44A8D4F0;
            } else if (th.id == AppTheme.ID_AURORA) {
                rowBg = 0xFF15202B;
            } else if (th.id == AppTheme.ID_MIDNIGHT) {
                rowBg = 0xFF1D162B;
            } else if (th.terminalMode) {
                rowBg = 0xFF0A0A0A;
            } else {
                rowBg = 0xFF1A1A1A;
            }
            float rad = AppTheme.dp(convertView, th.cornerRadiusDp > 0 ? Math.min(th.cornerRadiusDp, 16) : 12);
            android.graphics.drawable.GradientDrawable rd = th.rounded(rowBg, rad);
            if (th.id == AppTheme.ID_AERO || th.id == AppTheme.ID_MIDNIGHT || th.id == AppTheme.ID_AURORA) {
                rd.setStroke(1, th.borderSoft);
            }
            convertView.setBackgroundDrawable(rd);
            int pad = AppTheme.dp(convertView, 12);
            convertView.setPadding(pad, pad, pad, pad);

            String preview = item.lastMsg != null ? item.lastMsg : "";
            if (preview.length() > 42) preview = preview.substring(0, 40) + "...";
            t2.setText(preview);
            if (tTime != null) {
                tTime.setText(TimeUtil.formatList(item.lastTs));
            }
            return convertView;
        }
    }
}
