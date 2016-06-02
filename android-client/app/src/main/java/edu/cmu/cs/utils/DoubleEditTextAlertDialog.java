package edu.cmu.cs.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by junjuew on 3/4/16.
 */
public class DoubleEditTextAlertDialog extends GenericEditTextAlertDialog {
    private static final String TAG="DoubleEditTextDialog";
    private EditText keyEditText;
    private EditText valEditText;

    public DoubleEditTextAlertDialog(Context m, DialogEditTextResultListener delegate) {
        super(m, delegate);
    }

    public Dialog createDialog(String title, String keyMsg, String keyHint,
                               String valMsg, String valHint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mContext);
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView keyText = new TextView(mContext);
        keyText.setText(keyMsg);
        layout.addView(keyText);

        final EditText keyBox = new EditText(mContext);
        keyBox.setHint(keyHint);
        layout.addView(keyBox);

        final TextView valText = new TextView(mContext);
        valText.setText(valMsg);
        layout.addView(valText);

        final EditText valBox = new EditText(mContext);
        valBox.setHint(valHint);
        layout.addView(valBox);

        builder.setView(layout);
        keyEditText = keyBox;
        valEditText = valBox;
        builder.setView(layout);

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


        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        Button customOkButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        customOkButton.setOnClickListener(new OKReturnTextListener(alertDialog));
        return alertDialog;
    }


    class OKReturnTextListener implements View.OnClickListener {
        private final Dialog dialog;
        public OKReturnTextListener(Dialog dialog) {
            this.dialog = dialog;
        }
        @Override
        public void onClick(View v) {
            String key = keyEditText.getText().toString();
            String val = valEditText.getText().toString();
            Log.d(TAG, "user input: key: " + key + " val: "+ val);
            if (null != delegate){
                delegate.onDialogEditTextResult(key,val);
            }
            this.dialog.dismiss();
            return;
        }
    }

}
