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
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class BackgroundWorkerService extends HeadlessJsTaskService {

    private BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int max = intent.getIntExtra("max", 0);
            int progress = intent.getIntExtra("progress", 0);
            Log.d("setting progress", "max " + max + " progress " + progress);
            BackgroundWorkerService.this.builder.setProgress(max, progress, false);
            BackgroundWorkerService.this.startForeground(123456789, BackgroundWorkerService.this.builder.build());
        }
    };

    @TargetApi(26)
    private Notification.Builder builder;

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {

        String id = intent.getStringExtra("id");

        Log.d("BackgroundWorker", "starting service " + id);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){

            String[] actions = intent.getStringArrayExtra("actions");

            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");

            Notification.Action[] actionsObj = new Notification.Action[actions.length];

            for(int i=0;i<actions.length;i++) {

                Intent someIntent = new Intent(actions[i]);

                someIntent.setClass(ActionReceiver.reactContext, ActionReceiver.class);

                Log.d("creating action " + i + " for worker " + id, actions[i]);

                someIntent.putExtra("action", actions[i]);
                someIntent.putExtra("id", id);

                PendingIntent somePendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1000, someIntent, PendingIntent.FLAG_CANCEL_CURRENT);

                Notification.Action someAction = new Notification.Action.Builder(null, actions[i], somePendingIntent).build();

                actionsObj[i] = someAction;
            }

            Log.d("listening to progress", id);

            LocalBroadcastManager.getInstance(ActionReceiver.reactContext).registerReceiver(this.progressReceiver, new IntentFilter("react-native-background-worker-progress"+id));

            createChannel();
            startForeground(123456789, createNotification(title, message, actionsObj));
        }

        return new HeadlessJsTaskConfig(id, null, TimeUnit.MINUTES.toMillis(10), true);

    }

    @TargetApi(26)
    private Notification createNotification(String title, String message, Notification.Action[] actions) {

        Intent onClick = new Intent("clicked");
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 1000, onClick, 0);

        if(this.builder==null) this.builder = new Notification.Builder(this, "background");

        this.builder
                .setContentTitle(title)
                .setContentText(message)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentIntent);

        for(int i=0;i<actions.length;i++) builder.addAction(actions[i]);

        return builder.build();
    }

    @TargetApi(26)
    private void createChannel() {
        NotificationChannel channel = new NotificationChannel("background", "background services", NotificationManager.IMPORTANCE_MIN);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
    }

}
