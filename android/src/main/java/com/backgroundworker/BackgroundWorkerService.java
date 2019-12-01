package com.backgroundworker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class BackgroundWorkerService extends HeadlessJsTaskService {

    static String TAG = "BG_SERVICE::";

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {

        Bundle extras = intent.getExtras();
        assert extras!=null;

        String name = extras.getString("name");
        assert name!=null;

        String title = extras.getString("title");
        assert title!=null;

        String text = extras.getString("text");
        assert text!=null;

        int timeout = extras.getInt("timeout");

        String id = extras.getString("id");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(name, name, NotificationManager.IMPORTANCE_MIN);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, name)
                    .setWhen(System.currentTimeMillis())
                    .setContentText(text)
                    .setContentTitle(title)
                    .setSmallIcon(getResources().getIdentifier(name,"drawable",getApplicationContext().getPackageName()))
                    .build();

            startForeground(id==null? 123456789 : id.hashCode(), notification);

        }

        return new HeadlessJsTaskConfig(name, Arguments.fromBundle(extras), TimeUnit.MINUTES.toMillis(timeout), true);

    }

}
