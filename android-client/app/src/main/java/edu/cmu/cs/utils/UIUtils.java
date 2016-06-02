package edu.cmu.cs.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.faceswap.R;
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

    public static String stripMagicSequence(String stateInput){
        if (stateInput!=null){
            String openFaceMagicSequence=Const.OPENFACE_STATE_FILE_MAGIC_SEQUENCE.replace("\n",
                    "");
            String[] lines=stateInput.split("\n");
            if (lines.length>=2){
                String firstLine = lines[0];
                String secondLine = lines[1].toLowerCase();
                Log.d(TAG, "file check: 1st line: " + lines[0] + "\n2nd line: "+secondLine);
                String validState=null;
                if (firstLine.equals(openFaceMagicSequence) && (!secondLine.equals("null"))){
                    StringBuilder text=new StringBuilder();
                    for (int idx=1;idx<lines.length;idx++){
                        text.append(lines[idx]);
                        text.append("\n");
                    }
                    validState=text.toString();
                }
                return validState;
            }
        }
        return null;
    }

    public static byte[] loadFromFile(File file) {
        Log.d(TAG, "loading openface state string to file...");
        if (isExternalStorageReadable()) {
            if (file.exists() && file.isFile() && file.canRead()) {
                try {
                    // open input stream test.txt for reading purpose.
                    BufferedReader br = new BufferedReader(new FileReader(file));
                    StringBuilder builder = new StringBuilder();
                    String line;
                    String state;
                    while ((line = br.readLine()) != null) {
                        builder.append(line);
                        builder.append("\n");
                    }
                    br.close();
                    state = builder.toString();
                    String validState=stripMagicSequence(state);
                    if (validState!=null){
                        return validState.getBytes();
                    }

//                    String magicSequence = br.readLine();
//                    String openFaceMagicSequence=Const.OPENFACE_STATE_FILE_MAGIC_SEQUENCE.replace("\n",
//                            "");
//                    if (magicSequence.equals(openFaceMagicSequence)) {
//                        StringBuilder text = new StringBuilder();
//                        String line;
//                        while ((line = br.readLine()) != null) {
//                            text.append(line);
//                            text.append('\n');
//                        }
//                        br.close();
//                        return text.toString().getBytes();
//                    }
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
