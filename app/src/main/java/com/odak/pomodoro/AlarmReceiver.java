package com.odak.pomodoro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uygulama arka plandayken odak/mola bittiğinde bildirim gösterir ve seçilen sesi çalar. */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL = "odak_timer_v2";
    private static MediaPlayer player;

    @Override
    public void onReceive(Context c, Intent i) {
        String title = i.getStringExtra("title");
        String text = i.getStringExtra("text");

        boolean canNotify = Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (canNotify) {
            Intent open = new Intent(c, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_timer)
                    .setContentTitle(title != null ? title : "Odak")
                    .setContentText(text != null ? text : "")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(1, n);
        }

        playSound(c, i.getStringExtra("role"), i.getFloatExtra("vol", 0.8f), goAsync());
    }

    private static void playSound(Context c, String role, float vol, PendingResult pending) {
        AtomicBoolean finished = new AtomicBoolean(false);
        Handler h = new Handler(Looper.getMainLooper());
        Runnable done = () -> {
            if (!finished.compareAndSet(false, true)) return;
            try { if (player != null) { player.release(); player = null; } } catch (Exception ignored) { }
            try { pending.finish(); } catch (Exception ignored) { }
        };
        try {
            if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; }
            File f = role != null ? new File(c.getFilesDir(), "snd_" + role) : null;
            if (f != null && f.exists() && f.length() == 0) { done.run(); return; } // "Sessiz" seçili

            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (f != null && f.exists()) mp.setDataSource(f.getAbsolutePath());
            else mp.setDataSource(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            float v = Math.max(0f, Math.min(1f, vol));
            mp.setVolume(v, v);
            mp.setOnCompletionListener(m -> done.run());
            mp.setOnErrorListener((m, what, extra) -> { done.run(); return true; });
            mp.prepare();
            player = mp;
            mp.start();
            h.postDelayed(done, 9000); // en fazla ~9 sn çal
        } catch (Exception e) {
            done.run();
        }
    }
}
