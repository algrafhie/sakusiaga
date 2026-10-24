package id.sakusiaga.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dipanggil AlarmManager saat jadwal tiba, atau saat tombol notifikasi ditekan. */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        int id = intent.getIntExtra("id", 0);
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");

        if (AlarmScheduler.ACTION_STOP.equals(action)) {
            AlarmNotifier.cancel(ctx, id);
            AlarmScheduler.scheduleAll(ctx); // siapkan jadwal hari berikutnya
            return;
        }

        if (AlarmScheduler.ACTION_SNOOZE.equals(action)) {
            AlarmNotifier.cancel(ctx, id);
            AlarmScheduler.snooze(ctx, id, title, body, 5);
            return;
        }

        // ALARM TIBA
        AlarmNotifier.show(ctx, id,
                title == null ? "Waktunya minum obat" : title,
                body == null ? "" : body);

        boolean snoozeFire = intent.getBooleanExtra("snooze", false);
        if (!snoozeFire) {
            AlarmScheduler.scheduleAll(ctx); // jadwal berulang untuk besok
        }
    }
}
