package edu.cmu.cs.faceswap;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.DriveApi.DriveContentsResult;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.OpenFileActivityBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

public class CloudletDemoActivity extends AppCompatActivity implements
        GabrielConfigurationAsyncTask.AsyncResponse, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private static final String TAG = "cloudletDemoActivity";

    private static final int DLG_EXAMPLE1 = 0;
    private static final int TEXT_ID = 999;
    public String inputDialogResult;
    private CloudletFragment childFragment;
    private EditText dialogInputTextEdit;
//    int curModId = -1;

    public SharedPreferences mSharedPreferences= null;

    private byte[] asyncResponseExtra=null;
    public String currentServerIp=null;
    private Activity mActivity=null;

    //fix the bug for load_state, and onresume race for sending
    public boolean onResumeFromLoadState=false;
    private GoogleApiClient mGoogleApiClient;
    private final int RESOLVE_CONNECTION_REQUEST_CODE=32891;
    private static final int REQUEST_CODE_OPENER = 23091;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isOnline(this)){
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, true, this);
        } else {
            setContentView(R.layout.activity_cloudlet_demo);

            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            childFragment=
                    (CloudletFragment) getSupportFragmentManager().findFragmentById(R.id.demofragment);
            mSharedPreferences=getSharedPreferences(getString(R.string.shared_preference_file_key),
                    MODE_PRIVATE);
        }
        Log.d(TAG,"on create");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mActivity=this;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "on start");
        mGoogleApiClient.connect();
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
//            else {
//                if (curModId == R.id.setting_cloudlet_ip){
//                    Const.CLOUDLET_GABRIEL_IP = inputDialogResult;
//                    Log.i(TAG, "cloudlet ip changed to : " + Const.CLOUDLET_GABRIEL_IP);
//                } else if (curModId == R.id.setting_cloud_ip){
//                    Const.CLOUD_GABRIEL_IP = inputDialogResult;
//                    Log.i(TAG, "cloudlet ip changed to : " + Const.CLOUD_GABRIEL_IP);
//                }
//                curModId = -1;
//            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_UPLOAD_STATE)){
            Log.d(TAG, "upload state finished. success? " + success);
            if (success){
                //fetch person's name
                Log.d(TAG, "request trained people list" + success);
                sendOpenFaceGetPersonRequest(currentServerIp);
            }
        } else if (action.equals(Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE)){
            Log.d(TAG, "download state finished. success? " + success);
            if (success){
                asyncResponseExtra=extra;
                Intent intent = prepareForResultIntentForFilePickerActivity(this, false);
                startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
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

    /**
     * file picker activie result
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case FilePickerActivity.REQUEST_FILE:
                if (resultCode==RESULT_OK){
                    Bundle extras = data.getExtras();
                    String path = (String) extras.get(FilePickerActivity.FILE_EXTRA_DATA_PATH);
                    Log.d(TAG, "path: " + path);
                    boolean isLoad=(boolean)extras.get(FilePickerActivity.INTENT_EXTRA_ACTION_READ);
                    File file=new File(path);
                    if (isLoad){
                        byte[] stateData= UIUtils.loadFromFile(file);
                        if (stateData!=null){
                            //send asyncrequest
                            sendOpenFaceLoadStateRequest(stateData);
                        } else {
                            Log.e(TAG, "wrong file format");
                            Toast.makeText(this, "wrong file format", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        if (this.asyncResponseExtra!=null){
                            UIUtils.saveToFile(file, this.asyncResponseExtra);
                        }
                    }
                }
                break;
            case RESOLVE_CONNECTION_REQUEST_CODE:
                Log.i(TAG, "returned from resolution for drive connection");
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "successfully connected drive ");
                    mGoogleApiClient.connect();
                }
                break;
            case REQUEST_CODE_OPENER:
                if (resultCode == RESULT_OK) {
                    DriveId fileId = (DriveId) data.getParcelableExtra(
                            OpenFileActivityBuilder.EXTRA_RESPONSE_DRIVE_ID);
                    Log.i(TAG, "user select drive file id: "+fileId);
                    openDriveFile(fileId);
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

/*
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
*/

    public boolean actionUploadStateFromLocalFile(){
        //check online
        if (!checkOnline(this)) {
            return false;
        }
        //launch activity result to readin states
        Intent intent = prepareForResultIntentForFilePickerActivity(this, true);
        onResumeFromLoadState=true;
        startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
        return true;
    }

    //TODO: move check online to async task?
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
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

            case R.id.setting_load_state:
                actionUploadStateFromLocalFile();
            case R.id.setting_save_state:
                if (!checkOnline(this)) {
                    return false;
                }
                //fire off download state async task
                //return value will be called into onGabrielConfigurationAsyncTaskFinish
                GabrielConfigurationAsyncTask task =
                        new GabrielConfigurationAsyncTask(this,
                                Const.CLOUDLET_GABRIEL_IP,
                                GabrielClientActivity.VIDEO_STREAM_PORT,
                                GabrielClientActivity.RESULT_RECEIVING_PORT,
                                Const.GABRIEL_CONFIGURATION_DOWNLOAD_STATE,
                                this);
                task.execute();
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "on resume");
        super.onResume();
        if (mGoogleApiClient == null) {
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "on pause");
        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }
        super.onPause();
    }

    // connection with google drive
    public void actionReadStateFileFromGoogleDrive(){
        // Let the user pick text file
        // no files selected by the user.
        //http://stackoverflow.com/questions/26331046/android-how-can-i-access-a-spreadsheet-with-the-google-drive-sdk
        // cannot open google doc file
        IntentSender intentSender = Drive.DriveApi
                .newOpenFileActivityBuilder()
                .setMimeType(new String[]{"text/plain", "application/vnd.google-apps.document"})
                .build(mGoogleApiClient);
        try {
            startIntentSenderForResult(intentSender, REQUEST_CODE_OPENER, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.w(TAG, "Unable to send intent", e);
        }
    }

    private void openDriveFile(DriveId mSelectedFileDriveId) {
        // Reset progress dialog back to zero as we're
        // initiating an opening request.
        RetrieveDriveFileContentsAsyncTask task=
                new RetrieveDriveFileContentsAsyncTask(getApplicationContext());
        task.execute(mSelectedFileDriveId);
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "drive API client connected.");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Called whenever the API client fails to connect.
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show();
            Log.i(TAG, "trying to resolve" + result.toString());
            return;
        }
        // The failure has a resolution. Resolve it.
        // Called typically when the app is not yet authorized, and an
        // authorization
        // dialog is displayed to the user.
        try {
            result.startResolutionForResult(this, RESOLVE_CONNECTION_REQUEST_CODE);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

}


