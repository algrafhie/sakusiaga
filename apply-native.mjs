/**
 * apply-native.mjs
 *
 * Memasang fitur "alarm obat layar penuh" ke project Android hasil `npx cap add android`:
 *   1. menyalin sumber Java (plugin, receiver, activity layar penuh) ke folder app
 *   2. mendaftarkan plugin di MainActivity
 *   3. menambah izin + activity + receiver ke AndroidManifest.xml
 *
 * Idempoten: aman dijalankan berulang.
 * Jalankan: node scripts/apply-native.mjs
 */
import { readFileSync, writeFileSync, existsSync, mkdirSync, copyFileSync, readdirSync } from 'node:fs';
import { join, dirname } from 'node:path';

const CONFIG = 'capacitor.config.ts';
const MANIFEST = 'android/app/src/main/AndroidManifest.xml';
const JAVA_ROOT = '.'; // berkas .java berada di root repo (mudah diunggah lewat web)

if (!existsSync(CONFIG) || !existsSync(MANIFEST)) {
  console.error('✗ Jalankan "npx cap add android" lebih dulu.');
  process.exit(1);
}

// ---- 1. Tentukan appId → package & folder ----
const cfg = readFileSync(CONFIG, 'utf8');
const m = cfg.match(/appId:\s*['"]([^'"]+)['"]/);
if (!m) {
  console.error('✗ appId tidak ditemukan di capacitor.config.ts');
  process.exit(1);
}
const pkg = m[1];
const pkgPath = pkg.replace(/\./g, '/');
const javaDir = `android/app/src/main/java/${pkgPath}`;
mkdirSync(javaDir, { recursive: true });

// ---- 2. Salin sumber Java (ganti package & action string) ----
let copied = 0;
for (const file of readdirSync(JAVA_ROOT).filter((f) => f.endsWith('.java') && f !== 'MainActivity.java')) {
  let code = readFileSync(join(JAVA_ROOT, file), 'utf8');
  code = code.replace(/^package id\.sakusiaga\.app;/m, `package ${pkg};`);
  code = code.replace(/id\.sakusiaga\.app\.(RING|STOP|SNOOZE)/g, `${pkg}.$1`);
  writeFileSync(join(javaDir, file), code);
  copied++;
}

// ---- 3. MainActivity: daftarkan plugin ----
const actJava = join(javaDir, 'MainActivity.java');
const actKt = join(javaDir, 'MainActivity.kt');
if (existsSync(actKt)) {
  writeFileSync(actKt, `package ${pkg}

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(MedAlarmPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
`);
} else {
  writeFileSync(actJava, `package ${pkg};

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MedAlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
`);
}

// ---- 4. Manifest: izin, activity layar penuh, receiver ----
const PERMISSIONS = [
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.USE_FULL_SCREEN_INTENT',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.VIBRATE',
  'android.permission.WAKE_LOCK',
  'android.permission.DISABLE_KEYGUARD',
];

const BLOCK = `
        <!-- SakuSiaga: layar alarm penuh di atas layar kunci -->
        <activity
            android:name=".AlarmRingActivity"
            android:exported="false"
            android:launchMode="singleInstance"
            android:excludeFromRecents="true"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:configChanges="orientation|keyboardHidden|screenSize|uiMode"
            android:theme="@style/AppTheme.NoActionBarLaunch" />

        <!-- SakuSiaga: menerima alarm dari AlarmManager -->
        <receiver android:name=".AlarmReceiver" android:exported="false">
            <intent-filter>
                <action android:name="${pkg}.RING" />
                <action android:name="${pkg}.STOP" />
                <action android:name="${pkg}.SNOOZE" />
            </intent-filter>
        </receiver>

        <!-- SakuSiaga: pulihkan jadwal setelah restart / update / ubah waktu -->
        <receiver android:name=".BootReceiver" android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>
`;

let xml = readFileSync(MANIFEST, 'utf8');
const missing = PERMISSIONS.filter((p) => !xml.includes(`android:name="${p}"`));
if (missing.length) {
  const block = missing.map((p) => `    <uses-permission android:name="${p}" />`).join('\n');
  xml = xml.replace(/<manifest([^>]*)>/, (tag) => `${tag}\n${block}`);
}
if (!xml.includes('AlarmRingActivity')) {
  xml = xml.replace('</application>', `${BLOCK}    </application>`);
}
writeFileSync(MANIFEST, xml);

console.log(`✓ Package          : ${pkg}`);
console.log(`✓ Java disalin     : ${copied} berkas → ${javaDir}`);
console.log(`✓ MainActivity     : plugin MedAlarm terdaftar`);
console.log(`✓ Manifest         : ${missing.length} izin + activity + 2 receiver`);
console.log('  Lanjutkan: npx cap sync android');
