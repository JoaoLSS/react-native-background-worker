package com.backgroundworker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

public class ActionReceiver extends BroadcastReceiver {

    static ReactApplicationContext reactContext;

    @Override
    public void onReceive(Context context, Intent intent) {
//        Log.d("action received", intent.getStringExtra("id") + " " + intent.getStringExtra("action"));
        if(reactContext != null) {
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("react-native-background-worker-action"+intent.getStringExtra("id"), intent.getStringExtra("action"));
        }
    }

}
