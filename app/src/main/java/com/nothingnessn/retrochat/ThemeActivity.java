package com.nothingnessn.retrochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ThemeActivity extends Activity {

    private static final int REQ_IMG = 4401;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme);
        UiUtil.setupEdgeToEdge(this);

        Button back = (Button) findViewById(R.id.btnThemeBack);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { finish(); }
            });
        }

        LinearLayout list = (LinearLayout) findViewById(R.id.themeList);
        if (list == null) return;

        list.addView(buildBgSection());

        int current = Prefs.getThemeId(this);
        AppTheme[] themes = AppTheme.all();
        for (int i = 0; i < themes.length; i++) {
            list.addView(buildCard(themes[i], themes[i].id == current));
        }
    }

    private View buildBgSection() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = AppTheme.dp(card, 12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = AppTheme.dp(card, 12);
        card.setLayoutParams(lp);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A1A1A);
        bg.setCornerRadius(AppTheme.dp(card, 16));
        card.setBackgroundDrawable(bg);

        TextView title = new TextView(this);
        title.setText("Ozel arka plan (yerel)");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        card.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Tema ustune renk veya galeriden resim. Sunucuya gitmez.");
        hint.setTextColor(0xFFAAAAAA);
        hint.setTextSize(12);
        card.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, AppTheme.dp(card, 8), 0, 0);

        row.addView(smallBtn("Tema default", new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.setBgMode(ThemeActivity.this, 0);
                Prefs.setBgImage(ThemeActivity.this, null);
                Toast.makeText(ThemeActivity.this, "Arka plan: tema", Toast.LENGTH_SHORT).show();
            }
        }));
        row.addView(smallBtn("Renk", new View.OnClickListener() {
            @Override public void onClick(View v) { pickColor(); }
        }));
        row.addView(smallBtn("Resim", new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "Arka plan resmi"), REQ_IMG);
                } catch (Exception e) {
                    Toast.makeText(ThemeActivity.this, "Galeri acilamadi", Toast.LENGTH_SHORT).show();
                }
            }
        }));
        card.addView(row);
        return card;
    }

    private Button smallBtn(String text, View.OnClickListener cl) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.rightMargin = AppTheme.dp(b, 4);
        b.setLayoutParams(lp);
        b.setOnClickListener(cl);
        return b;
    }

    private void pickColor() {
        final String[] names = new String[]{
                "Ozel renk (RGB)...",
                "Siyah", "Koyu gri", "Gece moru", "Lacivert", "Koyu yesil", "Bordo",
                "Tema rengine don"
        };
        final int[] colors = new int[]{
                -2,
                0xFF000000, 0xFF121212, 0xFF0A0814, 0xFF0B1E3A, 0xFF0A1F14, 0xFF2A0A0A,
                0
        };
        new AlertDialog.Builder(this)
                .setTitle("Arka plan rengi")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (colors[which] == 0) {
                            Prefs.setBgMode(ThemeActivity.this, 0);
                            Toast.makeText(ThemeActivity.this, "Tema arka plani", Toast.LENGTH_SHORT).show();
                        } else if (colors[which] == -2) {
                            openRgbPicker();
                        } else {
                            Prefs.setBgColor(ThemeActivity.this, colors[which]);
                            Prefs.setBgMode(ThemeActivity.this, 1);
                            Toast.makeText(ThemeActivity.this, "Renk kaydedildi", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void openRgbPicker() {
        int cur = Prefs.getBgColor(this);
        final int[] rgb = new int[]{
                (cur >> 16) & 0xFF,
                (cur >> 8) & 0xFF,
                cur & 0xFF
        };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AppTheme.dp(root, 16);
        root.setPadding(pad, pad, pad, pad);

        final View preview = new View(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AppTheme.dp(root, 56));
        plp.bottomMargin = AppTheme.dp(root, 12);
        preview.setLayoutParams(plp);
        preview.setBackgroundColor(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
        root.addView(preview);

        final TextView tvHex = new TextView(this);
        tvHex.setTextColor(0xFFFFFFFF);
        tvHex.setTextSize(14);
        tvHex.setText(hexLabel(rgb[0], rgb[1], rgb[2]));
        tvHex.setPadding(0, 0, 0, AppTheme.dp(root, 8));
        root.addView(tvHex);

        final android.widget.SeekBar[] bars = new android.widget.SeekBar[3];
        final String[] labels = new String[]{ "Kirmizi (R)", "Yesil (G)", "Mavi (B)" };
        final int[] barColors = new int[]{ 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF };

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView lab = new TextView(this);
            lab.setText(labels[i] + ": " + rgb[i]);
            lab.setTextColor(barColors[i]);
            lab.setTextSize(13);
            root.addView(lab);

            android.widget.SeekBar sb = new android.widget.SeekBar(this);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            bars[i] = sb;
            final TextView labRef = lab;
            sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    rgb[idx] = progress;
                    labRef.setText(labels[idx] + ": " + progress);
                    int col = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                    preview.setBackgroundColor(col);
                    tvHex.setText(hexLabel(rgb[0], rgb[1], rgb[2]));
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            });
            root.addView(sb);
        }

        TextView quick = new TextView(this);
        quick.setText("Hizli sec:");
        quick.setTextColor(0xFFAAAAAA);
        quick.setPadding(0, AppTheme.dp(root, 8), 0, AppTheme.dp(root, 4));
        root.addView(quick);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] quickCols = new int[]{
                0xFF000000, 0xFF1A1A2E, 0xFF0A0814, 0xFF0B1E3A,
                0xFF1B3A1B, 0xFF3A1A1A, 0xFF2A1A3A, 0xFF333333,
                0xFF660099, 0xFFFF08FC, 0xFF1565C0, 0xFF004D40
        };
        for (int i = 0; i < quickCols.length; i++) {
            final int qc = quickCols[i];
            View sw = new View(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    0, AppTheme.dp(row, 28), 1);
            slp.leftMargin = 2;
            slp.rightMargin = 2;
            sw.setLayoutParams(slp);
            sw.setBackgroundColor(qc);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    rgb[0] = (qc >> 16) & 0xFF;
                    rgb[1] = (qc >> 8) & 0xFF;
                    rgb[2] = qc & 0xFF;
                    bars[0].setProgress(rgb[0]);
                    bars[1].setProgress(rgb[1]);
                    bars[2].setProgress(rgb[2]);
                    preview.setBackgroundColor(qc | 0xFF000000);
                    tvHex.setText(hexLabel(rgb[0], rgb[1], rgb[2]));
                }
            });
            row.addView(sw);
        }
        root.addView(row);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(root);

        new AlertDialog.Builder(this)
                .setTitle("Ozel renk (RGB)")
                .setView(scroll)
                .setPositiveButton("Uygula", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int col = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                        Prefs.setBgColor(ThemeActivity.this, col);
                        Prefs.setBgMode(ThemeActivity.this, 1);
                        Toast.makeText(ThemeActivity.this,
                                "Renk: " + hexLabel(rgb[0], rgb[1], rgb[2]),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private static String hexLabel(int r, int g, int b) {
        return String.format("#%02X%02X%02X  (R%d G%d B%d)", r, g, b, r, g, b);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMG || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return;
            File outFile = new File(getFilesDir(), "custom_bg.jpg");
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 4L * 1024 * 1024) break;
                fos.write(buf, 0, n);
            }
            fos.close();
            in.close();
            Prefs.setBgImage(this, outFile.getAbsolutePath());
            Prefs.setBgMode(this, 2);
            Toast.makeText(this, "Resim arka plan ayarlandi", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Resim kaydedilemedi", Toast.LENGTH_SHORT).show();
        }
    }

    private View buildCard(final AppTheme t, boolean selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = AppTheme.dp(card, 12);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = AppTheme.dp(card, 12);
        card.setLayoutParams(lp);

        GradientDrawable bg = t.rounded(t.bg, AppTheme.dp(card, 16));
        if (selected) bg.setStroke(AppTheme.dp(card, 2), t.accent);
        card.setBackgroundDrawable(bg);

        TextView title = new TextView(this);
        title.setText(t.name + (selected ? "  ✓" : ""));
        title.setTextColor(t.headerText);
        title.setTextSize(17);
        title.setPadding(0, 0, 0, AppTheme.dp(card, 8));
        card.addView(title);

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(AppTheme.dp(card, 8), AppTheme.dp(card, 8),
                AppTheme.dp(card, 8), AppTheme.dp(card, 8));
        GradientDrawable pbg = t.rounded(t.bg, AppTheme.dp(card, 12));
        pbg.setStroke(1, t.borderSoft);
        preview.setBackgroundDrawable(pbg);

        if (t.id == AppTheme.ID_AURORA) {
            TextView a = new TextView(this);
            a.setText("✦ hareketli aurora blob'lar");
            a.setTextColor(t.accent);
            a.setTextSize(11);
            preview.addView(a);
        }
        if (t.id == AppTheme.ID_AERO) {
            TextView a = new TextView(this);
            a.setText("◇ yari saydam cam balonlar");
            a.setTextColor(t.accent);
            a.setTextSize(11);
            preview.addView(a);
        }

        preview.addView(miniBubble(t, false, "Merhaba! Bu " + t.name));
        preview.addView(miniBubble(t, true, "Harika, secildi mi?"));
        card.addView(preview);

        Button use = new Button(this);
        use.setText(selected ? "Kullaniliyor" : "Bu temayi kullan");
        use.setTextColor(0xFFFFFFFF);
        use.setBackgroundDrawable(t.rounded(t.accent2 != 0 ? t.accent2 : t.accent, AppTheme.dp(card, 12)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = AppTheme.dp(card, 10);
        use.setLayoutParams(blp);
        use.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.setThemeId(ThemeActivity.this, t.id);
                Toast.makeText(ThemeActivity.this, "Tema: " + t.name, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });
        card.addView(use);
        return card;
    }

    private View miniBubble(AppTheme t, boolean mine, String text) {
        if (t.terminalMode) {
            TextView tv = new TextView(this);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextColor(mine ? t.accent : 0xFFE0E0E0);
            tv.setTextSize(11);
            String who = mine ? "sen" : "dost";
            tv.setText("[12:00] <" + who + "> " + text);
            tv.setPadding(0, AppTheme.dp(tv, 4), 0, AppTheme.dp(tv, 4));
            return tv;
        }
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(mine ? t.bubbleText : t.bubbleTextOther);
        tv.setTextSize(12);
        int p = AppTheme.dp(tv, 8);
        tv.setPadding(p, p, p, p);
        tv.setBackgroundDrawable(t.bubbleDrawable(mine));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.topMargin = AppTheme.dp(tv, 4);
        lp.bottomMargin = AppTheme.dp(tv, 4);
        tv.setLayoutParams(lp);
        return tv;
    }
}
