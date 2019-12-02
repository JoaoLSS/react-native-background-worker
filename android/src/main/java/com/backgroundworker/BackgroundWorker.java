package com.backgroundworker;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.RxWorker;
import androidx.work.WorkerParameters;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactInstanceManagerBuilder;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.HashMap;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleObserver;
import io.reactivex.SingleOnSubscribe;

import static android.os.SystemClock.sleep;

public class BackgroundWorker extends RxWorker {

    private Map<String, Object> worker;
    private String id;

    public BackgroundWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        worker = workerParams.getInputData().getKeyValueMap();
        Log.d("BACKROUND-WORKER",worker.toString());
        id = workerParams.getId().toString();
    }

    /**
     * We chose the RxWorker because we can listen to JS during the task,
     * If the module context is destroyed, the app was closed hence we can't perform any task,
     * so just return retry to work manager
     * @return Single that listens to JS finishing the work
     */
    @NonNull
    @Override
    public Single<Result> createWork() {

        if(BackgroundWorkerModule.context==null)
            return Single.just(Result.retry());

        String name = (String) worker.get("name");
        String payload = (String) worker.get("payload");

        if(name==null)
            return Single.just(Result.failure());

        Bundle extras = new Bundle();
        extras.putString("id", id);
        if(payload!=null) extras.putString("payload",payload);

        BackgroundWorkerModule.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(name, Arguments.fromBundle(extras));

        return Single.create(emitter -> {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String value = intent.getStringExtra("value");
                    String result = intent.getStringExtra("result");
                    Data outputData = new Data.Builder()
                            .putString("value", value)
                            .build();
                    switch (result) {
                        case "success":
                            emitter.onSuccess(Result.success(outputData));
                            break;
                        case "retry":
                            emitter.onSuccess(Result.retry());
                            break;
                        default:
                            emitter.onSuccess(Result.failure(outputData));
                    }
                }
            };
            LocalBroadcastManager.getInstance(BackgroundWorkerModule.context).registerReceiver(receiver,new IntentFilter(id+"result"));
        });
    }
}