package edu.cmu.cs.faceswap;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import edu.cmu.cs.IO.RetrieveDriveFileContentsAsyncTask;
import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.GabrielConfigurationAsyncTask;
import edu.cmu.cs.utils.UIUtils;
import filepickerlibrary.FilePickerActivity;

import static edu.cmu.cs.CustomExceptions.CustomExceptions.notifyError;
import static edu.cmu.cs.utils.NetworkUtils.checkOnline;
import static edu.cmu.cs.utils.NetworkUtils.isOnline;
import static edu.cmu.cs.utils.UIUtils.prepareForResultIntentForFilePickerActivity;
import static java.util.concurrent.TimeUnit.*;

public class CloudletDemoActivity extends AppCompatActivity implements
        GabrielConfigurationAsyncTask.AsyncResponse, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {



    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 23;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 24;
    private static final int MY_PERMISSIONS_REQUEST_ALL = 25;
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private static final String TAG = "cloudletDemoActivity";

    private static final int DLG_EXAMPLE1 = 0;
    private static final int TEXT_ID = 999;
    public String inputDialogResult;
    private CloudletFragment childFragment;
    private EditText dialogInputTextEdit;

    public SharedPreferences mSharedPreferences = null;

    private byte[] asyncResponseExtra = null;
    public String currentServerIp = null;
    private Activity mActivity = null;

    //fix the bug for load_state, and onresume race for sending
    public boolean onResumeFromLoadState = false;
    private GoogleApiClient mGoogleApiClient;
    public static final int GDRIVE_RESOLVE_CONNECTION_REQUEST_CODE = 32891;
    private static final int GDRIVE_REQUEST_CODE_OPENER = 23091;
    private static final int GDRIVE_REQUEST_CODE_CREATOR = 20391;

    //closed when request for connection.
    // open when onconnected
    // or no solution for failed connection
    // or solution result comes back
    private int pendingGDriveAction = -1;
    private static final int GDRIVE_ACTION_LOAD = 12;
    private static final int GDRIVE_ACTION_SAVE = 13;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPersmission();


        if (!isOnline(this)) {
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, true, this);
        } else {
            setContentView(R.layout.activity_cloudlet_demo);

            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            childFragment =
                    (CloudletFragment) getSupportFragmentManager().findFragmentById(R.id.demofragment);
            mSharedPreferences = getSharedPreferences(getString(R.string.shared_preference_file_key),
                    MODE_PRIVATE);
        }
        Log.d(TAG, "on create");
//        mGoogleApiClient = new GoogleApiClient.Builder(this)
//                .addApi(Drive.API)
//                .addScope(Drive.SCOPE_FILE)
//                .addConnectionCallbacks(this)
//                .addOnConnectionFailedListener(this)
//                .build();
        mActivity = this;
    }

    private void requestPersmission() {
        Log.d(TAG, "request permission");
        String permissions[] = {Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            ActivityCompat.requestPermissions(this,
                    permissions,
                    MY_PERMISSIONS_REQUEST_ALL);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "camera permission approved");
                } else {
                    Log.d(TAG, "camera permission denied");
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "write external storage permission approved");
                } else {
                    Log.d(TAG, "write external storage permission denied");
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_ALL: {
                Map<String, Integer> perms = new HashMap<>();
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0){
                    for (int i = 0; i < permissions.length; i++)
                        perms.put(permissions[i], grantResults[i]);
                    if (perms.get(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            && perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            ) {
                        Log.d(TAG, "all needed permission granted");
                    } else {
                        notifyError("Please give FaceSwap all permission it needs for proper running", true, this);
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "on start");
//        mGoogleApiClient.connect();
    }

    //activity menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cloudlet_demo, menu);
        return true;
    }

//    @Override
//    public void onDialogEditTextResult(String result) {
//        inputDialogResult=result;
//    }

    /**
     * callback when gabriel configuration async task finished
     * @param action
     * @param success
     * @param extra
     */
    @Override
    public void onGabrielConfigurationAsyncTaskFinish(String action,
                                                      boolean success,
                                                      byte[] extra) {

        if (action.equals(Const.GABRIEL_CONFIGURATION_RESET_STATE)){
            if (!success){
                String errorMsg=
                        "No Gabriel Server Found. \n" +
                                "Please define a valid Gabriel Server IP ";

                notifyError(errorMsg, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                },this);
            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_UPLOAD_STATE)){
            Log.d(TAG, "upload state finished. success? " + success);
            if (success){
                //fetch person's name
                Log.d(TAG, "request trained people list" + success);
                sendOpenFaceGetPersonRequest(currentServerIp);
            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE)) {
            Log.d(TAG, "download state finished. success? " + success);
            if (success) {
                asyncResponseExtra = extra;
                Intent intent = prepareForResultIntentForFilePickerActivity(this, false);
                startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE_TO_GDRIVE)){
            Log.d(TAG, "download state to google drive finished. success? " + success);
            if (success) {
                asyncResponseExtra = extra;
                //actionSaveStateFileToGoogleDrive();
            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_GET_PERSON)){
            Log.d(TAG, "download person finished. success? " + success);
            if (success){
                asyncResponseExtra=extra;
                String peopleString=new String(asyncResponseExtra);
                Log.i(TAG, "people : " + new String(asyncResponseExtra));
                //remove bracket
                String peopleStringNoBracket=peopleString.substring(1,peopleString.length()-1);
                childFragment.clearPersonTable();
                if (!peopleStringNoBracket.isEmpty()){
                    String[] people=peopleStringNoBracket.split(",");
                    childFragment.populatePersonTable(people);
                }
            } else {
                //clear UI
                childFragment.clearPersonTable();
            }
        }
}

    public void actionUploadStateByteArray(byte[] stateData){
        if (stateData!=null){
            onResumeFromLoadState=true;
            sendOpenFaceLoadStateRequest(stateData);
        } else {
            Log.e(TAG, "wrong file format");
            Toast.makeText(this, "wrong file format", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * file picker activie result
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FilePickerActivity.REQUEST_FILE:
                if (resultCode == RESULT_OK) {
                    Bundle extras = data.getExtras();
                    String path = (String) extras.get(FilePickerActivity.FILE_EXTRA_DATA_PATH);
                    Log.d(TAG, "path: " + path);
                    boolean isLoad = (boolean) extras.get(FilePickerActivity.INTENT_EXTRA_ACTION_READ);
                    File file = new File(path);
                    if (isLoad) {
                        byte[] stateData = UIUtils.loadFromFile(file);
                        if (stateData != null) {
                            actionUploadStateByteArray(stateData);
                        } else {
                            Toast.makeText(this, "Invalid File", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        if (this.asyncResponseExtra != null) {
                            UIUtils.saveToFile(file, this.asyncResponseExtra);
                        }
                    }
                }
                break;
            case GDRIVE_RESOLVE_CONNECTION_REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "drive client problem resolved. Trying to connect");
                    mGoogleApiClient.connect();
                } else {
                    //if not okay, then give up
                    pendingGDriveAction = -1;
                    Log.i(TAG, "drive connection resolution failed");
                    Toast.makeText(this, "Failed to Connect Google Drive", Toast.LENGTH_LONG).show();
                }
                break;
            case GDRIVE_REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    DriveId fileId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    Log.i(TAG, "user select drive file id: " + fileId);
                    openDriveFile(fileId);
                }
                break;
            case GDRIVE_REQUEST_CODE_CREATOR:
                // Called after a file is saved to Drive.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Image successfully saved.");
                    Toast.makeText(this,
                            "succesfully saved to google drive", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * send load state control request to OpenFaceServer
     * @param stateData
     */
    private boolean sendOpenFaceLoadStateRequest(byte[] stateData) {
        if (!checkOnline(this)){
            return false;
        }
        //fire off upload state async task
        //return value will be called into onGabrielConfigurationAsyncTaskFinish
        GabrielConfigurationAsyncTask task =
                new GabrielConfigurationAsyncTask(this,
                        currentServerIp,
                        GabrielClientActivity.VIDEO_STREAM_PORT,
                        GabrielClientActivity.RESULT_RECEIVING_PORT,
                        Const.GABRIEL_CONFIGURATION_UPLOAD_STATE,
                        this);
        task.execute(stateData);
        return true;
    }

    public void sendOpenFaceResetRequest(String remoteIP) {
        boolean online = isOnline(this);
        if (online){
            GabrielConfigurationAsyncTask task =
                    new GabrielConfigurationAsyncTask(this,
                            remoteIP,
                            GabrielClientActivity.VIDEO_STREAM_PORT,
                            GabrielClientActivity.RESULT_RECEIVING_PORT,
                            Const.GABRIEL_CONFIGURATION_RESET_STATE,
                            this);
            task.execute();
            Log.d(TAG, "send reset openface server request to " + currentServerIp);
            childFragment.clearPersonTable();
        } else {
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, false, this);
        }
    }

    //TODO: need to check if there is a gabriel server or not
    public void sendOpenFaceGetPersonRequest(String remoteIP) {
        boolean online = isOnline(this);
        if (online){
            GabrielConfigurationAsyncTask task =
                    new GabrielConfigurationAsyncTask(this,
                            remoteIP,
                            GabrielClientActivity.VIDEO_STREAM_PORT,
                            GabrielClientActivity.RESULT_RECEIVING_PORT,
                            Const.GABRIEL_CONFIGURATION_GET_PERSON,
                            this);
            task.execute();
            Log.d(TAG, "send get person openface server request to "+ currentServerIp);
        } else {
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, false, this);
        }
    }



    /**
     * get all avaiable servers names
     * @return
     */
    private CharSequence[] getAllServerNames(){
        String[] dictNames=getResources().getStringArray(R.array.shared_preference_ip_dict_names);
        List<String> allNames=new ArrayList<String>();
        for (int idx=0; idx<dictNames.length;idx++){
            String sharedPreferenceIpDictName=dictNames[idx];
            Set<String> existingNames =
                    mSharedPreferences.getStringSet(sharedPreferenceIpDictName,
                            new HashSet<String>());
            String prefix=getResources().
                    getStringArray(R.array.add_ip_places_spinner_array)[idx]+
                    SelectServerAlertDialog.IP_NAME_PREFIX_DELIMITER;
            for (String name:existingNames){
                allNames.add(prefix+name);
            }
        }
        CharSequence[] result = allNames.toArray(new CharSequence[allNames.size()]);
        return result;
    }


    DialogInterface.OnClickListener launchCopyStateAsyncTaskAction=
            new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            String ipName= SelectServerAlertDialog
                    .getItemArrayWithoutPrefix()[which]
                    .toString();
            Log.d(TAG, "selected ip name:" + ipName);
            String copyFromIp=mSharedPreferences.getString(ipName, Const.CLOUDLET_GABRIEL_IP);
            //send current ip and copy from ip to async task

            //fire off copy from ip async task
            //return value will be called into onGabrielConfigurationAsyncTaskFinish
            GabrielConfigurationAsyncTask task =
                    new GabrielConfigurationAsyncTask(mActivity,
                            currentServerIp,
                            GabrielClientActivity.VIDEO_STREAM_PORT,
                            GabrielClientActivity.RESULT_RECEIVING_PORT,
                            Const.GABRIEL_CONFIGURATION_SYNC_STATE,
                            (CloudletDemoActivity)mActivity);
            task.execute(copyFromIp);
            return;
        }
    };

//
//    public boolean actionUploadStateFromLocalFile(){
//        //check online
//        if (!checkOnline(this)) {
//            return false;
//        }
//        //launch activity result to readin states
//        Intent intent = prepareForResultIntentForFilePickerActivity(this, true);
//
//        startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
//        return true;
//    }

@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

       int id =item.getItemId();
        switch (id) {
            case R.id.manage_servers:
                Intent i = new Intent(this, IPSettingActivity.class);
                startActivity(i);
                return true;
            case R.id.setting_reset_openface_server:
                //check wifi state
                if (checkOnline(this)) {
                    sendOpenFaceResetRequest(currentServerIp);
                    return true;
                }
                return false;
            case R.id.setting_save_state:
                if (!checkOnline(this)) {
                    return false;
                }
                //fire off download state async task
                //return value will be called into onGabrielConfigurationAsyncTaskFinish
                new GabrielConfigurationAsyncTask(this,
                        currentServerIp,
                        GabrielClientActivity.VIDEO_STREAM_PORT,
                        GabrielClientActivity.RESULT_RECEIVING_PORT,
                        Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE,
                        this).execute();
                return true;
            case R.id.setting_save_state_to_gdrive:
                if (!checkOnline(this)) {
                    return false;
                }
                new GabrielConfigurationAsyncTask(this,
                        currentServerIp,
                        GabrielClientActivity.VIDEO_STREAM_PORT,
                        GabrielClientActivity.RESULT_RECEIVING_PORT,
                        Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE_TO_GDRIVE,
                        this).execute();
                return true;
            default:
                return false;
        }
    }

//            case R.id.setting_copy_server_state:
//                //TODO: alertdialog let user select which server to copy from
//                AlertDialog dg =SelectServerAlertDialog.createDialog(
//                        mActivity,
//                        "Pick a Server",
//                        getAllServerNames(),
//                        launchCopyStateAsyncTaskAction,
//                        SelectServerAlertDialog.cancelAction,
//                        true);
//                dg.show();
//                return true;
//
//            case R.id.setting_load_state:
//                return actionUploadStateFromLocalFile();


//    private boolean connectGoogleApiClient(){
//        if (mGoogleApiClient == null || (!mGoogleApiClient.isConnected())) {
//            // Create the API client and bind it to an instance variable.
//            // We use this instance as the callback for connection and connection
//            // failures.
//            // Since no account name is passed, the user is prompted to choose.
//            Log.d(TAG, "creating a new google api client");
//            mGoogleApiClient = new GoogleApiClient.Builder(this)
//                    .addApi(Drive.API)
//                    .addScope(Drive.SCOPE_FILE)
//                    .addConnectionCallbacks(this)
//                    .addOnConnectionFailedListener(this)
//                    .build();
//            mGoogleApiClient.connect();
//        }
//        return true;
//        ConnectionResult result=mGoogleApiClient.blockingConnect(2000, MILLISECONDS);
//        if (result.isSuccess()){
//            return true;
//        } else {
//            if (!result.hasResolution()) {
//                // show the localized error dialog.
//                GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
//                Log.i(TAG, "trying to resolve" + result.toString());
//            } else {
//                // The failure has a resolution. Resolve it.
//                // Called typically when the app is not yet authorized, and an
//                // authorization
//                // dialog is displayed to the user.
//                try {
//                    result.startResolutionForResult(this, GDRIVE_RESOLVE_CONNECTION_REQUEST_CODE);
//                } catch (IntentSender.SendIntentException e) {
//                    Log.e(TAG, "Exception while starting resolution activity", e);
//                }
//            }
//            return false;
//        }
//    }

    @Override
    protected void onResume() {
        Log.i(TAG, "on resume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "on pause");
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    private RetrieveDriveFileContentsAsyncTask.GdriveRetrieveFileContentCallBack gdriveCallBack=
            new RetrieveDriveFileContentsAsyncTask.GdriveRetrieveFileContentCallBack() {
                @Override
                public void onFileRetrieved(byte[] content) {
                    Log.i(TAG, "uploading byte array to server...");
                    actionUploadStateByteArray(content);
                }
            };

    private void readFileFromGoogleDrive(){
        if (mGoogleApiClient !=null && mGoogleApiClient.isConnected()){
            // Let the user pick text file
            // no files selected by the user.
            //http://stackoverflow.com/questions/26331046/android-how-can-i-access-a-spreadsheet-with-the-google-drive-sdk
            // cannot open google doc file
            IntentSender intentSender = Drive.DriveApi
                    .newOpenFileActivityBuilder()
                    .setMimeType(new String[]{"text/plain", "application/vnd.google-apps.document"})
                    .build(mGoogleApiClient);
            try {
                startIntentSenderForResult(intentSender, GDRIVE_REQUEST_CODE_OPENER, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.w(TAG, "Unable to send intent", e);
            }
        }
    }

    // connection with google drive
//    public void actionReadStateFileFromGoogleDrive() {
//        pendingGDriveAction=GDRIVE_ACTION_LOAD;
//        connectGoogleApiClient();
//    }
//
//    // connection with google drive
//    public void actionSaveStateFileToGoogleDrive() {
//        pendingGDriveAction=GDRIVE_ACTION_SAVE;
//        connectGoogleApiClient();
//    }


    private void openDriveFile(DriveId mSelectedFileDriveId) {
        // Reset progress dialog back to zero as we're
        // initiating an opening request.
        RetrieveDriveFileContentsAsyncTask task=
                new RetrieveDriveFileContentsAsyncTask(getApplicationContext(),
                        gdriveCallBack);
        task.execute(mSelectedFileDriveId);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "drive API client connected.");
        if (pendingGDriveAction == GDRIVE_ACTION_LOAD){
            readFileFromGoogleDrive();
        } else if (pendingGDriveAction == GDRIVE_ACTION_SAVE){
            saveFileToDrive(asyncResponseExtra);
        }
        pendingGDriveAction=-1;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        Toast.makeText(this, "Failed to Connect to Google Drive. Trying to resolve...",
                Toast.LENGTH_SHORT).show();
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            Log.i(TAG, "trying to resolve" + result.toString());
            pendingGDriveAction=-1;
            return;
        }

        // The failure has a resolution. Resolve it.
        // Called typically when the app is not yet authorized, and an
        // authorization
        // dialog is displayed to the user.
        try {
            result.startResolutionForResult(this, GDRIVE_RESOLVE_CONNECTION_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    /**
     * Create a new file and save it to Drive.
     */
    private void saveFileToDrive(final byte[] state) {
        // Start by creating a new contents, and setting a callback.
        if (mGoogleApiClient!=null && mGoogleApiClient.isConnected()){
            Log.i(TAG, "saving file to drive.");
            Drive.DriveApi.newDriveContents(mGoogleApiClient)
                    .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                        @Override
                        public void onResult(DriveApi.DriveContentsResult result) {
                            // If the operation was not successful, we cannot do anything
                            // and must
                            // fail.
                            if (!result.getStatus().isSuccess()) {
                                Log.i(TAG, "Failed to create new contents.");
                                return;
                            }
                            // Otherwise, we can write our data to the new contents.
                            Log.i(TAG, "New contents created.");
                            // Get an output stream for the contents.
                            OutputStream outputStream = result.getDriveContents().getOutputStream();
                            // Write the bitmap data from it.
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            try {
                                bos.write(Const.OPENFACE_STATE_FILE_MAGIC_SEQUENCE.getBytes());
                                bos.write(state);
                                outputStream.write(bos.toByteArray());
                                bos.close();
                                outputStream.close();
                            } catch (IOException e1) {
                                Log.i(TAG, "Unable to write file contents.");
                            }
                            // Create the initial metadata - MIME type and title.
                            // Note that the user will be able to change the title later.
                            SimpleDateFormat dateFormat = new SimpleDateFormat("MM_dd_hh_mm_ss");
                            GregorianCalendar cal = new GregorianCalendar();
                            dateFormat.setTimeZone(cal.getTimeZone());
                            String hint = "openface_"+ dateFormat.format(cal.getTime()) +".txt";
                            MetadataChangeSet metadataChangeSet = new MetadataChangeSet.Builder()
                                    .setMimeType("text/plain").setTitle(hint).build();
                            // Create an intent for the file chooser, and start it.
                            IntentSender intentSender = Drive.DriveApi
                                    .newCreateFileActivityBuilder()
                                    .setInitialMetadata(metadataChangeSet)
                                    .setInitialDriveContents(result.getDriveContents())
                                    .build(mGoogleApiClient);
                            try {
                                startIntentSenderForResult(
                                        intentSender, GDRIVE_REQUEST_CODE_CREATOR, null, 0, 0, 0);
                            } catch (IntentSender.SendIntentException e) {
                                Log.i(TAG, "Failed to launch file chooser.");
                            }
                        }
                    });
        } else {
            Toast.makeText(this, "failed to connect to google drive", Toast.LENGTH_LONG).show();
        }
    }
}


