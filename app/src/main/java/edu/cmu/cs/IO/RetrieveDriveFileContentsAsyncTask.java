package edu.cmu.cs.IO;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by junjuew on 3/30/16.
 */
public class RetrieveDriveFileContentsAsyncTask
        extends ApiClientAsyncTask<DriveId, Boolean, String> {
    private static final String TAG = "RetrieveDriveAsyncTask";

    public RetrieveDriveFileContentsAsyncTask(Context context) {
        super(context);
    }

    @Override
    protected String doInBackgroundConnected(DriveId... params) {
        String contents = null;
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
            }
            contents = builder.toString();
        } catch (IOException e) {
            Log.e(TAG, "IOException while reading from the stream", e);
        }

        driveContents.discard(getGoogleApiClient());
        return contents;
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (result == null) {
            Log.d(TAG, "Error while reading from the file");
            return;
        }
        Log.d(TAG, "File contents: " + result);

    }
}

