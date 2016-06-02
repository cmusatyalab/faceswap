package edu.cmu.cs.utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import edu.cmu.cs.gabriel.Const;

import static edu.cmu.cs.CustomExceptions.CustomExceptions.notifyError;

/**
 * Created by junjuew on 3/3/16.
 */
public class NetworkUtils {
    protected static final String
            IPV4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    protected static final String IPV6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";

    public static boolean isOnline(Context m) {
        ConnectivityManager connMgr = (ConnectivityManager)
                m.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    public static boolean isIpAddress(String ipAddress) {
        if (ipAddress.matches(IPV4Pattern) || ipAddress.matches(IPV6Pattern)) {
            return true;
        }
        return false;
    }

    public static boolean checkOnline(Activity a){
        if (isOnline(a)) {
            return true;
        }
        notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, false, a);
        return false;
    }


}
