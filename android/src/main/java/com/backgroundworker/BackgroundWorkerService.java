package com.backgroundworker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class BackgroundWorkerService extends HeadlessJsTaskService {

    static String TAG = "BG_SERVICE::";

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {

        String worker = intent.getStringExtra("worker");
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(worker, worker, NotificationManager.IMPORTANCE_MIN);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, worker)
                    .setWhen(System.currentTimeMillis())
                    .setContentText(text)
                    .setContentTitle(title)
                    .setSmallIcon(R.drawable.ic_notification)
                    .build();

            startForeground(123456789, notification);

        }

        Bundle extras = intent.getExtras();

        assert extras != null;
        return new HeadlessJsTaskConfig(worker, Arguments.fromBundle(extras), TimeUnit.MINUTES.toMillis(10), true);

    }

}
