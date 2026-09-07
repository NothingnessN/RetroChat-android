package com.nothingnessn.retrochat;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SetupActivity extends Activity {

    private EditText etUsername, etQuestion, etAnswer, etVerifyAnswer;
    private LinearLayout layoutSecurity, layoutVerify;
    private TextView tvVerifyQuestion, tvStatus;
    private Button btnContinue, btnVerify;
    private String pendingUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.isLoggedIn(this)) {
            goMain();
            return;
        }

        setContentView(R.layout.activity_setup);
        UiUtil.setupEdgeToEdge(this);
        try { applySetupTheme(); } catch (Exception ignored) {}

        etUsername = (EditText) findViewById(R.id.etUsername);
        etQuestion = (EditText) findViewById(R.id.etQuestion);
        etAnswer = (EditText) findViewById(R.id.etAnswer);
        etVerifyAnswer = (EditText) findViewById(R.id.etVerifyAnswer);
        layoutSecurity = (LinearLayout) findViewById(R.id.layoutSecurity);
        layoutVerify = (LinearLayout) findViewById(R.id.layoutVerify);
        tvVerifyQuestion = (TextView) findViewById(R.id.tvVerifyQuestion);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        btnContinue = (Button) findViewById(R.id.btnSave);
        btnVerify = (Button) findViewById(R.id.btnVerify);

        if (layoutSecurity != null) layoutSecurity.setVisibility(View.VISIBLE);
        if (layoutVerify != null) layoutVerify.setVisibility(View.GONE);
        if (btnVerify != null) btnVerify.setVisibility(View.GONE);

        requestNotifyPermission();

        setStatus("Sunucu kontrol ediliyor...", false);
        Network.ping(new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                setStatus("Sunucu hazir", false);
            }
            @Override
            public void onError(String error) {
                setStatus("Sunucu kapali / erisilemiyor", true);
                Toast.makeText(SetupActivity.this, "Sunucuya baglanilamiyor", Toast.LENGTH_LONG).show();
            }
        });

        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegisterOrLogin();
            }
        });

        if (btnVerify != null) {
            btnVerify.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    attemptVerify();
                }
            });
        }
    }

    private void requestNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 91);
            } catch (Exception ignored) {}
        }
    }

    private boolean usernameOk(String name) {
        if (name == null || name.length() < 3 || name.length() > 20) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 32 || c == 127) return false;
        }
        return true;
    }

    private void attemptRegisterOrLogin() {
        String name = etUsername.getText().toString().trim();
        if (!usernameOk(name)) {
            setStatus("3-20 karakter, bosluksuz (Turkce harf olur)", true);
            return;
        }

        String question = etQuestion != null ? etQuestion.getText().toString().trim() : "";
        String answer = etAnswer != null ? etAnswer.getText().toString().trim() : "";

        btnContinue.setEnabled(false);
        setStatus("Kontrol ediliyor...", false);

        final String uuid = Prefs.getUUID(this);
        final String finalName = name.toLowerCase();
        final String q = question;
        final String a = answer;

        Network.register(uuid, finalName, question, answer, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                btnContinue.setEnabled(true);
                if (result != null && result.startsWith("OK")) {
                    loginOk(finalName);
                } else if (result != null && result.startsWith("NEED_VERIFY:")) {
                    pendingUsername = finalName;
                    showVerifyUI(result.substring("NEED_VERIFY:".length()));
                } else if (result != null && result.contains("NEED_SETUP_SECURITY")) {
                    if (q.length() > 0 && a.length() > 0) {
                        finishSetupSecurity(uuid, finalName, q, a);
                    } else {
                        setStatus("Bu eski hesapta soru yok. Soru + cevabi yaz, tekrar Kaydet.", true);
                        if (layoutSecurity != null) layoutSecurity.setVisibility(View.VISIBLE);
                    }
                } else if (result != null && result.contains("need_security")) {
                    setStatus("Yeni hesap icin soru ve cevap gir (bos birakma)", true);
                    if (layoutSecurity != null) layoutSecurity.setVisibility(View.VISIBLE);
                } else {
                    setStatus("Hata: " + result, true);
                }
            }

            @Override
            public void onError(String error) {
                btnContinue.setEnabled(true);
                setStatus("Sunucuya baglanilamiyor: " + error, true);
                Toast.makeText(SetupActivity.this, "Sunucu kapali veya ag hatasi", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishSetupSecurity(String uuid, final String name, String q, String a) {
        setStatus("Soru baglaniyor...", false);
        Network.setupSecurity(uuid, name, q, a, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                btnContinue.setEnabled(true);
                if (result != null && result.startsWith("OK")) {
                    loginOk(name);
                } else {
                    setStatus("Hata: " + result, true);
                }
            }

            @Override
            public void onError(String error) {
                btnContinue.setEnabled(true);
                setStatus("Sunucuya baglanilamiyor: " + error, true);
            }
        });
    }

    private void showVerifyUI(String question) {
        if (layoutSecurity != null) layoutSecurity.setVisibility(View.GONE);
        if (layoutVerify != null) {
            layoutVerify.setVisibility(View.VISIBLE);
            if (tvVerifyQuestion != null) tvVerifyQuestion.setText(question);
        }
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
        if (btnVerify != null) btnVerify.setVisibility(View.VISIBLE);
        setStatus("Bu hesap korumali. Asagidaki sorunun cevabini yaz.", false);
    }

    private void attemptVerify() {
        String answer = "";
        if (etVerifyAnswer != null) answer = etVerifyAnswer.getText().toString().trim();
        if (answer.length() < 1 && etAnswer != null) {
            answer = etAnswer.getText().toString().trim();
        }
        if (answer.length() < 1) {
            setStatus("Cevap gerekli", true);
            return;
        }
        if (pendingUsername == null) {
            setStatus("Once kullanici adi gir", true);
            return;
        }

        if (btnVerify != null) btnVerify.setEnabled(false);
        setStatus("Dogrulaniyor...", false);
        final String uuid = Prefs.getUUID(this);

        Network.verify(uuid, pendingUsername, answer, new Network.Callback() {
            @Override
            public void onSuccess(String result) {
                if (btnVerify != null) btnVerify.setEnabled(true);
                if (result != null && result.startsWith("OK")) {
                    loginOk(pendingUsername);
                } else {
                    setStatus("Yanlis cevap veya hata: " + result, true);
                }
            }

            @Override
            public void onError(String error) {
                if (btnVerify != null) btnVerify.setEnabled(true);
                setStatus("Sunucuya baglanilamiyor: " + error, true);
                Toast.makeText(SetupActivity.this, "Sunucu kapali veya ag hatasi", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loginOk(String name) {
        Prefs.setUsername(this, name);
        Prefs.setLoggedIn(this, true);
        PollService.start(this);
        Toast.makeText(this, "Hos geldin @" + name, Toast.LENGTH_SHORT).show();
        goMain();
    }

    private void setStatus(String msg, boolean error) {
        if (tvStatus == null) return;
        tvStatus.setText(msg);
        tvStatus.setTextColor(error ? 0xFFFF1744 : 0xFFAAAAAA);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void applySetupTheme() {
        AppTheme t = AppTheme.get(Prefs.getThemeId(this));
        View root = findViewById(android.R.id.content);
        if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
            ((ViewGroup) root).getChildAt(0).setBackgroundColor(t.bg);
        }
        float r = AppTheme.dp(this.getWindow().getDecorView(), 16);
        int[] ids = new int[]{ R.id.btnSave, R.id.btnVerify };
        for (int id : ids) {
            View v = findViewById(id);
            if (v instanceof Button) {
                android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
                d.setColor(t.accent);
                d.setCornerRadius(r);
                v.setBackgroundDrawable(d);
                ((Button) v).setTextColor(0xFFFFFFFF);
            }
        }
        if (tvStatus != null) tvStatus.setTextColor(t.statusText);
    }
}
