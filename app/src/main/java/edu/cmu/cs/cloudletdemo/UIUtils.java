package edu.cmu.cs.cloudletdemo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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

/**
 * Created by junjuew on 3/2/16.
 */
public class UIUtils {
    private static final String TAG = "UIUtils";
    private EditText dialogInputTextEdit;
    public String inputDialogResult;
    private DialogEditTextResultListener delegate;

    /**
     * Create and return an example alert dialog with an edit text box.
     */
    public Dialog createExampleDialog(Context m, String title, String msg,
                                      String hint,
                                      DialogEditTextResultListener delegate) {
        AlertDialog.Builder builder = new AlertDialog.Builder(m);
        builder.setTitle(title);
        builder.setMessage(msg);

        // Use an EditText view to get user input.
        final EditText input = new EditText(m);
        input.setText(hint);
        dialogInputTextEdit = input;
        inputDialogResult=null;
        builder.setView(input);

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });

        this.delegate=delegate;
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        Button customOkButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        customOkButton.setOnClickListener(new CustomListener(alertDialog));
        return alertDialog;
    }

    class CustomListener implements View.OnClickListener {
        private final Dialog dialog;
        public CustomListener(Dialog dialog) {
            this.dialog = dialog;
        }
        @Override
        public void onClick(View v) {
            inputDialogResult = dialogInputTextEdit.getText().toString();
            Log.d(TAG, "user input: " + inputDialogResult);
            if (null != delegate){
                delegate.onDialogEditTextResult(inputDialogResult);
            }
            this.dialog.dismiss();
            return;
        }
    }

    public interface DialogEditTextResultListener {
        void onDialogEditTextResult(String result);
    }


    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public byte[] loadFromFile(File file){
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

    public boolean saveToFile(File file, byte[] val){
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
