package edu.cmu.cs.cloudletdemo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by junjuew on 3/3/16.
 */
public class NetworkUtils {
    public static boolean isOnline(Context m) {
        ConnectivityManager connMgr = (ConnectivityManager)
                m.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

}
