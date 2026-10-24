package id.sakusiaga.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Layar alarm layar penuh — tampil di atas layar kunci seperti panggilan masuk.
 * Dipicu lewat full-screen intent dari AlarmNotifier.
 */
public class AlarmRingActivity extends Activity {

    private MediaPlayer player;
    private Vibrator vibrator;
    private int alarmId = 0;
    private String title = "Waktunya minum obat";
    private String body = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        terimaIntent(getIntent());
        siapkanJendela();
        setContentView(bangunUi());
        AlarmNotifier.cancel(this, alarmId); // hindari bunyi dobel (notifikasi + activity)
        mulaiBunyi();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        terimaIntent(intent);
        setContentView(bangunUi());
    }

    private void terimaIntent(android.content.Intent i) {
        if (i == null) return;
        alarmId = i.getIntExtra("id", 0);
        String t = i.getStringExtra("title");
        String b = i.getStringExtra("body");
        if (t != null && !t.isEmpty()) title = t;
        if (b != null) body = b;
    }

    private void siapkanJendela() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private LinearLayout bangunUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#E5281B"));
        root.setPadding(dp(28), dp(48), dp(28), dp(48));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView label = new TextView(this);
        label.setText("WAKTUNYA MINUM OBAT");
        label.setTextColor(Color.parseColor("#FFD9D5"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setLetterSpacing(0.18f);
        label.setGravity(Gravity.CENTER);
        root.addView(label);

        TextView nama = new TextView(this);
        nama.setText(title);
        nama.setTextColor(Color.WHITE);
        nama.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        nama.setGravity(Gravity.CENTER);
        nama.setPadding(0, dp(18), 0, 0);
        root.addView(nama);

        if (body != null && !body.isEmpty()) {
            TextView dosis = new TextView(this);
            dosis.setText(body);
            dosis.setTextColor(Color.WHITE);
            dosis.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            dosis.setGravity(Gravity.CENTER);
            dosis.setPadding(0, dp(8), 0, 0);
            root.addView(dosis);
        }

        TextView info = new TextView(this);
        info.setText("Alarm obat SakuSiaga");
        info.setTextColor(Color.parseColor("#FFD9D5"));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(14), 0, dp(38));
        root.addView(info);

        Button stop = new Button(this);
        stop.setText("Matikan alarm");
        stop.setAllCaps(false);
        stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        stop.setTextColor(Color.parseColor("#B31E14"));
        stop.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        stop.setOnClickListener(v -> matikan());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        root.addView(stop, lp1);

        Button tunda = new Button(this);
        tunda.setText("Tunda 5 menit");
        tunda.setAllCaps(false);
        tunda.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tunda.setTextColor(Color.WHITE);
        GradientDrawable garis = new GradientDrawable();
        garis.setColor(Color.TRANSPARENT);
        garis.setStroke(dp(2), Color.parseColor("#FFB3AC"));
        garis.setCornerRadius(dp(28));
        tunda.setBackground(garis);
        tunda.setOnClickListener(v -> tunda());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp2.topMargin = dp(14);
        root.addView(tunda, lp2);

        return root;
    }

    private void mulaiBunyi() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            player = null;
        }
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pola = new long[]{0, 700, 300, 700, 600};
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pola, 0));
                } else {
                    vibrator.vibrate(pola, 0);
                }
            }
        } catch (Exception ignored) { }
    }

    private void hentikanBunyi() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) { }
        player = null;
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) { }
        vibrator = null;
    }

    private void matikan() {
        hentikanBunyi();
        AlarmNotifier.cancel(this, alarmId);
        AlarmScheduler.scheduleAll(this);
        finishAndRemoveTask();
    }

    private void tunda() {
        hentikanBunyi();
        AlarmNotifier.cancel(this, alarmId);
        AlarmScheduler.snooze(this, alarmId, title, body, 5);
        finishAndRemoveTask();
    }

    @Override
    public void onBackPressed() {
        matikan(); // tombol back tidak boleh menutup tanpa mematikan alarm
    }

    @Override
    protected void onDestroy() {
        hentikanBunyi();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }
}
