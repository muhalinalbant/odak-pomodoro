package com.odak.pomodoro;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Uygulama arka plandayken odak/mola bittiğinde bildirim gösterir. */
public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL = "odak_timer";

    @Override
    public void onReceive(Context c, Intent i) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        String title = i.getStringExtra("title");
        String text = i.getStringExtra("text");

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
}
