package id.sakusiaga.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Memulihkan semua jadwal setelah HP restart / app di-update / zona waktu berubah. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        AlarmScheduler.cancelAll(ctx);
        AlarmScheduler.scheduleAll(ctx);
    }
}
