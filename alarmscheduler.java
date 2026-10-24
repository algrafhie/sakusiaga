package id.sakusiaga.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Menyimpan jadwal obat di SharedPreferences dan mendaftarkannya ke AlarmManager.
 * Daftar disimpan native supaya BootReceiver bisa memulihkan jadwal tanpa app dibuka.
 */
public class AlarmScheduler {

    static final String PREFS = "sakusiaga_alarms";
    static final String KEY = "list";

    public static final String ACTION_RING = "id.sakusiaga.app.RING";
    public static final String ACTION_STOP = "id.sakusiaga.app.STOP";
    public static final String ACTION_SNOOZE = "id.sakusiaga.app.SNOOZE";

    public static class Alarm {
        public int id;
        public int hour;
        public int minute;
        public String title = "Waktunya minum obat";
        public String body = "";
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String loadRaw(Context c) {
        return prefs(c).getString(KEY, "[]");
    }

    /** Ganti seluruh daftar: batalkan yang lama, simpan yang baru, lalu jadwalkan. */
    public static int sync(Context c, String json) {
        cancelAll(c);
        prefs(c).edit().putString(KEY, json == null ? "[]" : json).apply();
        return scheduleAll(c);
    }

    public static List<Alarm> load(Context c) {
        List<Alarm> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(loadRaw(c));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Alarm a = new Alarm();
                a.id = o.optInt("id", 4200 + i);
                a.hour = o.optInt("hour", -1);
                a.minute = o.optInt("minute", -1);
                a.title = o.optString("title", "Waktunya minum obat");
                a.body = o.optString("body", "");
                if (a.hour >= 0 && a.hour <= 23 && a.minute >= 0 && a.minute <= 59) out.add(a);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static PendingIntent ringIntent(Context c, Alarm a) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_RING);
        i.putExtra("id", a.id);
        i.putExtra("hour", a.hour);
        i.putExtra("minute", a.minute);
        i.putExtra("title", a.title);
        i.putExtra("body", a.body);
        return PendingIntent.getBroadcast(c, a.id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static long nextTrigger(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    public static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static int scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return 0;
        List<Alarm> list = load(c);
        boolean exact = canExact(c);
        for (Alarm a : list) {
            PendingIntent pi = ringIntent(c, a);
            long when = nextTrigger(a.hour, a.minute);
            pasang(am, when, pi, exact);
        }
        return list.size();
    }

    private static void pasang(AlarmManager am, long when, PendingIntent pi, boolean exact) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
                }
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, when, pi);
            }
        }
    }

    public static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (Alarm a : load(c)) {
            PendingIntent pi = ringIntent(c, a);
            am.cancel(pi);
            pi.cancel();
        }
    }

    /** Tunda 5 menit: alarm sekali jalan dengan request code berbeda. */
    public static void snooze(Context c, int id, String title, String body, int minutes) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_RING);
        i.putExtra("id", id);
        i.putExtra("title", title == null ? "Waktunya minum obat" : title);
        i.putExtra("body", body == null ? "" : body);
        i.putExtra("snooze", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, id + 50000, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + minutes * 60_000L;
        pasang(am, when, pi, canExact(c));
    }
}
