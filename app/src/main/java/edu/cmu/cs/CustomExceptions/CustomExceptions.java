package edu.cmu.cs.CustomExceptions;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

/**
 * Created by junjuew on 3/3/16.
 */
public class CustomExceptions {
    public static void notifyError(String msg, final boolean terminate, final Activity activity){
        DialogInterface.OnClickListener error_listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (terminate){
                            activity.finish();
                        }
                    }
                };
        new AlertDialog.Builder(activity)
                .setTitle("Error").setMessage(msg)
                .setNegativeButton("close", error_listener).show();
    }

    public static void notifyError(String msg,
                                   DialogInterface.OnClickListener errorListener,
                                   final Activity activity){
        new AlertDialog.Builder(activity)
                .setTitle("Error").setMessage(msg)
                .setNegativeButton("close", errorListener).show();
    }

}
