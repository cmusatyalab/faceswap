package edu.cmu.cs.IO;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.cmu.cs.utils.UIUtils;

/**
 * Created by junjuew on 3/30/16.
 */
public class RetrieveDriveFileContentsAsyncTask
        extends ApiClientAsyncTask<DriveId, Boolean, String> {
    private static final String TAG = "RetrieveDriveAsyncTask";
    private ProgressDialog dialog;
    private GdriveRetrieveFileContentCallBack delegate;
    private Context mContext;

    public interface GdriveRetrieveFileContentCallBack{
        void onFileRetrieved(byte[] state);
    }

    public RetrieveDriveFileContentsAsyncTask(Context context,
                                              GdriveRetrieveFileContentCallBack delegate) {
        super(context);
        dialog = new ProgressDialog(context);
        this.delegate= delegate;
        this.mContext=context;
    }


    @Override
    protected void onPreExecute() {
        super.onPreExecute();
//        dialog.setMessage("Loading file from Google Drive...");
//        dialog.show();
    }

    @Override
    protected String doInBackgroundConnected(DriveId... params) {
        String state = null;
        DriveFile file = params[0].asDriveFile();
        DriveApi.DriveContentsResult driveContentsResult =
                file.open(getGoogleApiClient(), DriveFile.MODE_READ_ONLY, null).await();
        if (!driveContentsResult.getStatus().isSuccess()) {
            return null;
        }
        DriveContents driveContents = driveContentsResult.getDriveContents();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(driveContents.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }
            state = builder.toString();
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading from the stream", e);
        }
        driveContents.discard(getGoogleApiClient());
        String validState=UIUtils.stripMagicSequence(state);
        return validState;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        dialog.dismiss();
        if (result == null) {
            Log.d(TAG, "Error while reading from the file");
            Toast.makeText(mContext, "Invalid File",Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "File contents: " + result);
        if (null != delegate){
            delegate.onFileRetrieved(result.getBytes());
        }
    }
}

