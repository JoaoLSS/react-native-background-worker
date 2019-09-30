package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.BackoffPolicy;
import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.impl.model.WorkSpec;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class BackgroundWorkerModule extends ReactContextBaseJavaModule {

    static final String TAG = "BackgroundModule";

    final ReactApplicationContext context;

    public BackgroundWorkerModule(ReactApplicationContext reactContext) {
        super(reactContext);

        this.context = reactContext;


        final Configuration config = new Configuration.Builder()
                .setWorkerFactory(new BackgroundWorkerFactory(reactContext))
                .build();

        WorkManager.initialize(reactContext, config);

        WorkManager.getInstance(reactContext).cancelAllWork();
        ActionReceiver.reactContext = reactContext;

    }

    @Override
    public String getName() {
        return "BackgroundWorker";
    }

    @ReactMethod
    public void oneTimeWorkRequest(ReadableMap data, ReadableMap config, ReadableMap _constraints, Promise sendId) {

        Constraints constraints = Parser.getConstraints(_constraints);

        int keepResultsForAtLeast = config.getInt("keepResultsForAtLeast");
        String backoffPolicy = config.getString("backoffPolicy");
        int backoffDuration = config.getInt("backoffDuration");
        int initialDelay = config.getInt("initialDelay");

        Data inputData = new Data.Builder().putAll(data.toHashMap()).build();

        WorkRequest request = new OneTimeWorkRequest.Builder(BackgroundWorker.class)
                .keepResultsForAtLeast(keepResultsForAtLeast, TimeUnit.MINUTES)
                .setBackoffCriteria(backoffPolicy == "exponential" ? BackoffPolicy.EXPONENTIAL : BackoffPolicy.LINEAR, backoffDuration, TimeUnit.MINUTES)
                .setInitialDelay(initialDelay, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        sendId.resolve(request.getId().toString());

        WorkManager.getInstance(this.context).enqueue(request);

    }

    @ReactMethod
    public void getWorkInfoById(String id) {
        LiveData<WorkInfo> data = WorkManager.getInstance(this.context).getWorkInfoByIdLiveData(UUID.fromString(id));
        data.observe((LifecycleOwner)this.context, new Observer<WorkInfo>() {
            @Override
            public void onChanged(WorkInfo workInfo) {
                String id = workInfo.getId().toString();
                String outputData = workInfo.getOutputData().toString();
                String state = workInfo.getState().toString();
                int attemptCount = workInfo.getRunAttemptCount();

                Data data = new Data.Builder()
                        .putString("state", state)
                        .putString("outputData", outputData)
                        .putInt("attemptCount", attemptCount)
                        .build();

                BackgroundWorkerModule.this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(id, data);
            }
        });
    }

    @ReactMethod
    public void worker(ReadableMap _constraints, ReadableMap config, Callback sendId) {

        Constraints constraints = Parser.getConstraints(_constraints);

        ReadableArray unparsedActions = config.getArray("actions");

        String[] actions = new String[unparsedActions.size()];

        for (int i=0; i<unparsedActions.size();i++) {
            actions[i] = unparsedActions.getString(i);
            Log.d("action" + i, actions[i]);
        }

        Data data = new Data.Builder()
                .putStringArray("actions", actions)
                .putString("title", config.getString("title"))
                .putString("message", config.getString("message"))
                .build();

        WorkRequest request = new PeriodicWorkRequest.Builder( BackgroundWorker.class, 15, TimeUnit.MINUTES )
                .setConstraints(constraints)
                .setInputData(data)
                .build();

        sendId.invoke(request.getId().toString());

        WorkManager.getInstance(this.context).enqueue(request);

    }

    @ReactMethod
    public void cancelWorker(String id) {
        WorkManager.getInstance(this.context).cancelWorkById(UUID.fromString(id));
    }

    private void set(String id, String result) {
        Intent intent = new Intent(id);
        intent.putExtra("result", result);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    @ReactMethod
    public void success(String id) { this.set(id, "success"); }

    @ReactMethod
    public void failure(String id) { this.set(id, "failure"); }

    @ReactMethod
    public void setProgress(String id, int max, int progress) {
        Log.d("setting progress", "id " + id + " max " + max + " progress " + progress);
        Intent intent = new Intent("react-native-background-worker-progress"+id);
        intent.putExtra("max", max);
        intent.putExtra("progress", progress);
        LocalBroadcastManager.getInstance(this.context).sendBroadcast(intent);
    }

    @ReactMethod
    public void startService(String id, ReadableMap notification) {

        ReadableArray unparsedActions = notification.getArray("actions");

        String[] actions = new String[unparsedActions.size()];

        for (int i=0; i<unparsedActions.size();i++) {
            actions[i] = unparsedActions.getString(i);
            Log.d("action" + i, actions[i]);
        }

        Intent service = new Intent(this.context, BackgroundWorkerService.class);
        service.putExtra("id", id);
        service.putExtra("actions", actions);
        service.putExtra("title", notification.getString("title"));
        service.putExtra("message", notification.getString("message"));

        Log.d("BackgroundWorker", "starting service " + id);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) this.context.startForegroundService(service);
        else this.context.startService(service);
    }
}
