# SakuSiaga — Paket Android (alarm obat LAYAR PENUH)

Paket ini membungkus aplikasi web SakuSiaga (`dist/index.html`, UI 100% sama) menjadi
aplikasi Android, dan memindahkan alarm jadwal minum obat ke **sistem Android**:
`AlarmManager` + **full-screen intent**.

Hasilnya: saat jadwal tiba, HP menampilkan **layar alarm penuh di atas layar kunci**
— mirip panggilan masuk — walau aplikasi sedang ditutup, layar mati, atau baru
di-restart. Tidak perlu internet.

## Perilaku alarm

| Kondisi | Versi web | Versi Android ini |
|---|---|---|
| App terbuka | overlay di dalam app | layar penuh + suara alarm + getar |
| App ditutup / di-swipe | tidak bunyi | **layar penuh, tetap bunyi** |
| Layar mati / terkunci | tidak bunyi | **layar penuh menutupi layar kunci** |
| Setelah HP restart | hilang efeknya | **jadwal dipulihkan otomatis** |
| Internet | tidak perlu | tidak perlu |

Di dalam app Android, timer web otomatis dimatikan (`isNativeShell()`), jadi bunyi
hanya berasal dari satu sumber: sistem Android.

## Alur sinkronisasi (medSync.ts → native)

```
UI Jadwal Obat
   │ applyMeds()
   ▼
localStorage  (sumber kebenaran tunggal)
   │ syncNativeAlarms()
   ▼
MedAlarmPlugin.sync({alarms})          ← Capacitor bridge
   ├─ SharedPreferences  "sakusiaga_alarms"   (disimpan native, tahan restart)
   └─ AlarmManager.setExactAndAllowWhileIdle  (per jadwal, harian)
            │
            ▼  waktu tiba
      AlarmReceiver  ──► notifikasi full-screen intent ──► AlarmRingActivity
            │                                              (layar penuh + ringtone + getar)
            └── scheduleAll()  → siapkan jadwal besok
```

Sinkronisasi dijalankan otomatis:
1. setiap **tambah obat / tambah jam / hapus jam / toggle / hapus obat**
2. setiap **aplikasi dibuka** (memulihkan setelah update app)
3. setiap **aplikasi kembali aktif** (kalau OS sempat membunuh alarm)
4. **tombol manual** "Sinkronkan jadwal" di UI

Saat HP restart / app di-update / zona waktu berubah, `BootReceiver` mendaftar ulang
semua jadwal langsung dari SharedPreferences — **tanpa perlu membuka aplikasi**.

## Izin yang perlu aktif di HP (sekali saja)
App sudah menampilkan statusnya di tab Alarm dan tombol **"Aktifkan izin alarm"**
yang membuka setelan yang kurang satu per satu:

| Izin | Untuk apa | Cara |
|---|---|---|
| **Notifikasi** (Android 13+) | notifikasi alarm muncul | dialog otomatis saat menambah obat |
| **Alarm & pengingat** (`SCHEDULE_EXACT_ALARM`) | alarm tepat waktu, tembus mode hemat baterai | tombol di app → setelan Android |
| **Layar penuh** (`USE_FULL_SCREEN_INTENT`) | layar alarm menutupi layar kunci (Android 14+) | tombol di app → setelan Android |

Status di app menampilkan rincian: `Notifikasi ✓ · Alarm presisi ✓ · Layar penuh ✓`.

## Cara build

### Opsi A — GitHub Actions (tanpa Android Studio)
1. Buat repo GitHub, unggah **seluruh isi folder ini**.
2. **Actions** → **Build SakuSiaga APK** → **Run workflow**.
3. ±5–10 menit → unduh artifact **sakusiaga-debug-apk** → `app-debug.apk`.
4. Pasang di HP (izinkan "dari sumber ini").

### Opsi B — Lokal (Android Studio)
```bash
npm install
npx cap add android
node apply-native.mjs            # pasang Java + izin + activity layar penuh
npx cap sync android
npx cap open android              # Run ▶
```
Butuh: Node 18+, JDK 17, Android Studio (SDK 34).

## Uji di HP (3 menit)
1. Buka app → tab **Alarm** → **Aktifkan izin alarm** → ikuti 2–3 dialog setelan.
2. Tekan **Uji layar penuh** → layar alarm harus langsung muncul.
3. Tambah obat dengan jam 2 menit dari sekarang → tutup total app (swipe dari recent apps)
   → alarm harus muncul sebagai layar penuh di atas layar kunci.
4. Restart HP → tunggu jam jadwal berikutnya → alarm tetap muncul.

## Isi paket

```
package.json                  Capacitor 6 (tanpa plugin notifikasi eksternal)
capacitor.config.ts           appId id.sakusiaga.app, webDir dist
index.html                    aplikasi SakuSiaga hasil build (offline, font inline)
*.java                        sumber native:
    MedAlarmPlugin.java       jembatan JS ↔ native (sync, izin, uji)
    AlarmScheduler.java       SharedPreferences + AlarmManager
    AlarmReceiver.java        menerima alarm, menyiapkan notifikasi full-screen
    AlarmNotifier.java        channel alarm + full-screen intent + tombol notifikasi
    AlarmRingActivity.java    layar penuh (ringtone + getar + stop/tunda)
    BootReceiver.java         pulihkan jadwal setelah restart
apply-native.mjs             menyalin Java, mendaftarkan plugin, mem-patch manifest
.github/workflows/build-apk.yml  build APK otomatis
```

`apply-native.mjs` membaca `appId` dari `capacitor.config.ts`, lalu menyesuaikan
nama package Java dan action string di manifest. Ganti appId sebelum build bila perlu.

## Untuk naik ke Play Store
1. Buat keystore sendiri, simpan sebagai secret GitHub:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
2. Aktifkan blok **Build AAB release** di `build-apk.yml` (sudah disiapkan).
3. Di Play Console, isi **deklarasi izin**:
   - `SCHEDULE_EXACT_ALARM` → "pengingat minum obat pada jam tertentu".
   - `USE_FULL_SCREEN_INTENT` → hanya untuk alarm waktu-sensitif (obat).
     Pada Android 14+, pengguna tetap bisa mencabutnya; app akan fallback ke
     notifikasi heads-up biasa (tidak error).
4. **Data safety**: tidak ada data dikumpulkan — semua (kontak darurat, kartu medis,
   jadwal obat) hanya tersimpan di perangkat.
5. Target SDK mengikuti template Capacitor 6 (34). Jika Play meminta lebih baru,
   ubah `android/variables.gradle`.

## Batasan yang jujur
- **Layar penuh bisa diblokir OEM.** Beberapa produsen (Xiaomi/Oppo/Vivo/Samsung)
  membatasi full-screen intent. Bila diblokir, alarm tetap muncul sebagai notifikasi
  heads-up dengan suara alarm. Solusi: matikan optimasi baterai + izinkan
  "tampil di atas aplikasi lain" untuk SakuSiaga.
- **Suara** mengikuti nada alarm bawaan HP (bisa diubah di setelan kanal notifikasi).
- Tidak bisa diuji dari sandbox ini: sandbox tidak punya Java/Android SDK, jadi
  APK-nya harus Anda build lewat GitHub Actions atau Android Studio.
