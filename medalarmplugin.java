package id.sakusiaga.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Jembatan JS <-> native untuk jadwal obat:
 *   sync()               → simpan + daftarkan ke AlarmManager
 *   checkPermissions()   → notifikasi / alarm presisi / layar penuh
 *   requestNotifications → dialog izin notifikasi (Android 13+)
 *   openSettings()       → buka setelan izin yang masih kurang
 *   ringNow()            → uji layar penuh sekarang
 */
@CapacitorPlugin(name = "MedAlarm")
public class MedAlarmPlugin extends Plugin {

    private static final int REQ_NOTIF = 7421;

    private boolean izinNotifikasi() {
        Context ctx = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    private boolean izinLayarPenuh() {
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            return nm != null && nm.canUseFullScreenIntent();
        }
        return true;
    }

    private JSObject statusIzin() {
        JSObject o = new JSObject();
        o.put("notifications", izinNotifikasi());
        o.put("exactAlarm", AlarmScheduler.canExact(getContext()));
        o.put("fullScreen", izinLayarPenuh());
        return o;
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(statusIzin());
    }

    @PluginMethod
    public void requestNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !izinNotifikasi()
                && getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        call.resolve(statusIzin());
    }

    @PluginMethod
    public void sync(PluginCall call) {
        AlarmNotifier.ensureChannel(getContext());
        JSArray arr = call.getArray("alarms");
        String json = arr == null ? "[]" : arr.toString();
        int jumlah = AlarmScheduler.sync(getContext(), json);
        JSObject ret = new JSObject();
        ret.put("scheduled", jumlah);
        ret.put("exact", AlarmScheduler.canExact(getContext()));
        call.resolve(ret);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        String target = call.getString("target", "notifications");
        Intent i;
        try {
            if ("exactAlarm".equals(target) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getContext().getPackageName()));
            } else if ("fullScreen".equals(target) && Build.VERSION.SDK_INT >= 34) {
                i = new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT",
                        Uri.parse("package:" + getContext().getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getContext().getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(fallback);
            } catch (Exception ignored) { }
        }
        call.resolve();
    }

    @PluginMethod
    public void ringNow(PluginCall call) {
        String title = call.getString("title", "Amlodipine");
        String body = call.getString("body", "");
        Intent i = new Intent(getContext(), AlarmRingActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("id", 999999);
        i.putExtra("title", title);
        i.putExtra("body", body);
        getContext().startActivity(i);
        call.resolve();
    }
}
