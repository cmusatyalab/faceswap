package edu.cmu.cs.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.cloudletdemo.R;
import filepickerlibrary.FilePickerActivity;
import filepickerlibrary.enums.Request;
import filepickerlibrary.enums.Scope;

/**
 * Created by junjuew on 3/2/16.
 */
public class UIUtils {
    private static final String TAG = "UIUtils";

    /**
     * create an intent to start file picker activity
     * @param m
     * @param isRead
     * @return
     */
    public static Intent prepareForResultIntentForFilePickerActivity(Context m, boolean isRead) {
        Log.d(TAG, "starting file picker activity...");
        Intent filePickerActivity = new Intent(m, FilePickerActivity.class);
        filePickerActivity.putExtra(FilePickerActivity.SCOPE, Scope.ALL);
        filePickerActivity.putExtra(FilePickerActivity.REQUEST, Request.FILE);
        filePickerActivity.putExtra(FilePickerActivity.INTENT_EXTRA_FAB_COLOR_ID,
                R.color.colorAccent);
        filePickerActivity.putExtra(FilePickerActivity.INTENT_EXTRA_COLOR_ID,
                R.color.colorPrimary);
        filePickerActivity.putExtra(FilePickerActivity.INTENT_EXTRA_ACTION_READ,
                isRead);
        return filePickerActivity;
    }




    /* Checks if external storage is available for read and write */
    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    private static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static byte[] loadFromFile(File file){
        Log.d(TAG, "loading openface state string to file...");
        if (isExternalStorageReadable()) {
            if (file.exists() && file.isFile() && file.canRead()) {
                try {
                    // open input stream test.txt for reading purpose.
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    String magicSequence = br.readLine();
                    String openFaceMagicSequence=Const.OPENFACE_STATE_FILE_MAGIC_SEQUENCE.replace("\n",
                            "");
                    if (magicSequence.equals(openFaceMagicSequence)) {
                        StringBuilder text = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            text.append(line);
                            text.append('\n');
                        }
                        br.close();
                        return text.toString().getBytes();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static boolean saveToFile(File file, byte[] val){
        try{
            Log.d(TAG, "saving openface state string to file...");
            if (isExternalStorageWritable()){
                // Get the directory for the user's public pictures directory.
                File root = new File(Environment.getExternalStorageDirectory(),
                        Const.FILE_ROOT_PATH);
                root.mkdirs();
                FileOutputStream f = new FileOutputStream(file);
                BufferedOutputStream bos = new BufferedOutputStream(f);
                bos.write(Const.OPENFACE_STATE_FILE_MAGIC_SEQUENCE.getBytes());
                bos.write(val);
                bos.flush();
                bos.close();
                f.close();
            } else {
                Log.d(TAG,"No permission to write external storage");
                return false;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
