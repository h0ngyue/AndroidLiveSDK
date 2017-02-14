package com.yolo.livesdk.rx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

/**
 * Created by shuailongcheng on 9/20/16.
 */
public class NetDetectorReceiver extends BroadcastReceiver {
    public static final int NET_ERROR = 0;

    public static final int NET_UNKNOWN = 1;

    public static final int NET_2G3G = 2;

    public static final int NET_WIFI = 3;

    public static int detectNetType(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mobileInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        NetworkInfo wifiInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo activeInfo = manager.getActiveNetworkInfo();
        if (activeInfo == null || !activeInfo.isAvailable()) {
            return NET_ERROR;
        } else {
            if (mobileInfo == null) {
                return NET_UNKNOWN;
            }
            if (mobileInfo.isConnected()) {
                return NET_2G3G;
            } else if (wifiInfo.isConnected()) {
                return NET_WIFI;
            } else {
                return NET_UNKNOWN;
            }
        }
    }

    public static void ConnectionAutoDetect(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mobileInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        NetworkInfo wifiInfo = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo activeInfo = manager.getActiveNetworkInfo();
        if (activeInfo == null || !activeInfo.isAvailable()) {
            Toast.makeText(context, "没有网络", Toast.LENGTH_SHORT).show();
        } else {
            if (mobileInfo != null) {
                if (mobileInfo.isConnected()) {
                    Toast.makeText(context, "2G/3G", Toast.LENGTH_SHORT).show();
                } else if (wifiInfo.isConnected()) {
                    Toast.makeText(context, "Wifi", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "没有网络", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void onReceive(Context context, Intent intent) {
        ConnectionAutoDetect(context);
    }
}
