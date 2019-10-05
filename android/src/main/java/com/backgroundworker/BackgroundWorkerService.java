package com.backgroundworker;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class BackgroundWorkerService extends HeadlessJsTaskService {

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {

        HeadlessJsTaskConfig config = null;

        String worker = intent.getStringExtra("worker");
        String payload = intent.getStringExtra("payload");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            assert BackgroundWorkerModule.workers.get(worker) != null;
            ReadableMap _notification = BackgroundWorkerModule.workers.get(worker).getMap("notification");

            NotificationChannel channel = new NotificationChannel(worker, worker, NotificationManager.IMPORTANCE_MIN);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            assert _notification != null;
            String title = _notification.getString("title");
            String text = _notification.getString("text");

            Notification notification = new Notification.Builder(this, worker)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_notification)
                    .build();

            startForeground(123456789, notification);

        }

        if(worker == null || payload == null) return null;

        return new HeadlessJsTaskConfig(worker, Arguments.fromBundle(intent.getExtras()), TimeUnit.MINUTES.toMillis(10), true);

    }

}
