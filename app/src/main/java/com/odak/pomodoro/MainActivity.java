package com.odak.pomodoro;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int PICK_FILE = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                AlarmReceiver.CHANNEL, "Zamanlayıcı", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Odak ve mola bitiş bildirimleri");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient() {
            // "Kendi sesini ekle" için telefonun dosya seçicisini açar.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("window.__nativePause&&window.__nativePause()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.__nativeResume&&window.__nativeResume()", null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Uygulamayı kapatmak yerine arka plana al; zamanlayıcı devam etsin.
        moveTaskToBack(true);
    }

    private PendingIntent alarmIntent(int id, String title, String text) {
        Intent i = new Intent(this, AlarmReceiver.class);
        if (title != null) i.putExtra("title", title);
        if (text != null) i.putExtra("text", text);
        return PendingIntent.getBroadcast(this, id, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** JavaScript'ten çağrılan köprü: window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public void schedule(int id, double atMillis, String title, String text) {
            AlarmManager am = getSystemService(AlarmManager.class);
            if (am == null) return;
            long at = (long) atMillis;
            PendingIntent pi = alarmIntent(id, title, text);
            try {
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }

        @JavascriptInterface
        public void cancel(int id) {
            AlarmManager am = getSystemService(AlarmManager.class);
            if (am != null) am.cancel(alarmIntent(id, null, null));
        }

        /** Uygulama içindeki tema seçimine göre durum/gezinme çubuğu renklerini ayarlar. */
        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void setBars(boolean dark) {
            runOnUiThread(() -> {
                Window w = getWindow();
                int bg = dark ? 0xFF141312 : 0xFFF6F3EE;
                int nav = dark ? 0xFF1D1B19 : 0xFFFFFFFF;
                w.setStatusBarColor(bg);
                w.setNavigationBarColor(nav);
                if (web != null) web.setBackgroundColor(bg);
                View d = w.getDecorView();
                int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                int f = d.getSystemUiVisibility();
                d.setSystemUiVisibility(dark ? (f & ~lightFlags) : (f | lightFlags));
            });
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }
}
