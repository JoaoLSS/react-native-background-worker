package com.backgroundworker;

import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;

import com.facebook.react.bridge.ReadableMap;

public class Parser {

    static private NetworkType getNetworkType(String networkType) {

        switch(networkType) {
            case "connected" :  return NetworkType.CONNECTED;
            case "metered":     return NetworkType.METERED;
            case "notRoaming":  return NetworkType.NOT_ROAMING;
            case "unmetered":   return NetworkType.UNMETERED;
            default:            return NetworkType.NOT_REQUIRED;
        }

    }

    static public Constraints getConstraints(ReadableMap constraints) {

        final NetworkType networkType = getNetworkType(constraints.hasKey("network") ? constraints.getString("network") : "notRequired");

        final boolean requiresCharging      = constraints.hasKey("battery") && constraints.getString("battery").equals("charging");
        final boolean requiresDeviceIdle    = constraints.hasKey("idle"   ) && constraints.getBoolean("idle");
        final boolean requiresStorageNotLow = constraints.hasKey("storage") && constraints.getBoolean("storage");
        final boolean requiresBatteryNotLow = constraints.hasKey("battery") && constraints.getString("battery").equals("notLow");

        Log.d("networkType"             , networkType.toString());
        Log.d("requiresCharging"        , requiresCharging      ? "true" : "false");
        Log.d("requiresDeviceIdle"      , requiresDeviceIdle    ? "true" : "false");
        Log.d("requiresStorageNotLow"   , requiresStorageNotLow ? "true" : "false");
        Log.d("requiresBatteryNotLow"   , requiresBatteryNotLow ? "true" : "false");

        return new Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(requiresCharging)
                .setRequiresDeviceIdle(requiresDeviceIdle)
                .setRequiresStorageNotLow(requiresStorageNotLow)
                .setRequiresBatteryNotLow(requiresBatteryNotLow)
                .build();

    }

    static public Data getWorkerData(ReadableMap data) {

        Data.Builder dataBuilder  = new Data.Builder();

        dataBuilder.putInt("timeout", data.getInt("timeout"));

        return dataBuilder.build();

    }

}
