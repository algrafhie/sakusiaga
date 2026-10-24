package id.sakusiaga.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** Membuat notifikasi alarm dengan full-screen intent (tampil seperti panggilan masuk). */
public class AlarmNotifier {

    public static final String CHANNEL_ID = "obat_fullscreen";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Alarm Minum Obat", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Alarm jadwal minum obat, tampil di layar penuh");
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 700, 300, 700, 600});
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (sound != null) {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, aa);
        }
        nm.createNotificationChannel(ch);
    }

    static PendingIntent ringActivity(Context c, int id, String title, String body) {
        Intent i = new Intent(c, AlarmRingActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getActivity(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void show(Context c, int id, String title, String body) {
        ensureChannel(c);

        PendingIntent full = ringActivity(c, id, title, body);

        Intent stop = new Intent(c, AlarmReceiver.class).setAction(AlarmScheduler.ACTION_STOP).putExtra("id", id);
        PendingIntent stopPi = PendingIntent.getBroadcast(c, id + 100, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snz = new Intent(c, AlarmReceiver.class).setAction(AlarmScheduler.ACTION_SNOOZE)
                .putExtra("id", id).putExtra("title", title).putExtra("body", body);
        PendingIntent snoozePi = PendingIntent.getBroadcast(c, id + 200, snz,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(c.getApplicationInfo().icon)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(full, true)
                .setContentIntent(full)
                .addAction(0, "Matikan", stopPi)
                .addAction(0, "Tunda 5 menit", snoozePi);

        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
            // izin notifikasi belum diberikan
        }
    }

    public static void cancel(Context c, int id) {
        try {
            NotificationManagerCompat.from(c).cancel(id);
        } catch (Exception ignored) { }
    }
}
