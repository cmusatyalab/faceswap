package edu.cmu.cs.utils;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Created by junjuew on 3/4/16.
 */
public class EditTextAlertDialog {
    private static final String TAG = "editTextAlertDialog";
    private EditText dialogInputTextEdit;
    public String inputDialogResult;
    protected DialogEditTextResultListener delegate;
    protected Context mContext;

    public interface DialogEditTextResultListener {
        void onDialogEditTextResult(String result);
    }

    public EditTextAlertDialog(Context m, DialogEditTextResultListener delegate){
        this.mContext=m;
        this.delegate=delegate;
    }

    /**
     * Create and return an example alert dialog with an edit text box.
     */
    public Dialog createDialog(String title, String msg,
                                      String hint){
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mContext);
        builder.setTitle(title);
        builder.setMessage(msg);

        // Use an EditText view to get user input.
        final EditText input = new EditText(this.mContext);
        input.setText(hint);
        dialogInputTextEdit = input;
        inputDialogResult = null;
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
            inputDialogResult = dialogInputTextEdit.getText().toString();
            Log.d(TAG, "user input: " + inputDialogResult);
            if (null != delegate){
                delegate.onDialogEditTextResult(inputDialogResult);
            }
            this.dialog.dismiss();
            return;
        }
    }
}
