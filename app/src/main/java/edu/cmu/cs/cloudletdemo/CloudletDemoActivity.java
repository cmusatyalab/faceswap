package edu.cmu.cs.cloudletdemo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;


import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.GabrielConfigurationAsyncTask;
import edu.cmu.cs.gabriel.R;
import edu.cmu.cs.utils.EditTextAlertDialog;
import edu.cmu.cs.utils.UIUtils;
import filepickerlibrary.FilePickerActivity;

import static edu.cmu.cs.CustomExceptions.CustomExceptions.notifyError;
import static edu.cmu.cs.utils.NetworkUtils.checkOnline;
import static edu.cmu.cs.utils.NetworkUtils.isOnline;
import static edu.cmu.cs.utils.UIUtils.prepareForResultIntentForFilePickerActivity;

public class CloudletDemoActivity extends AppCompatActivity implements
        GabrielConfigurationAsyncTask.AsyncResponse,
        EditTextAlertDialog.DialogEditTextResultListener {
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private static final String TAG = "cloudletDemoActivity";

    private static final int DLG_EXAMPLE1 = 0;
    private static final int TEXT_ID = 999;
    public String inputDialogResult;
    private CloudletFragment childFragment;
    private EditText dialogInputTextEdit;
    int curModId = -1;

    public SharedPreferences mSharedPreferences= null;

    private byte[] asyncResponseExtra=null;
    public String currentServerIp=null;
    private Activity mActivity=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isOnline(this)){
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, true, this);
        } else {
            setContentView(R.layout.activity_cloudlet_demo);

            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            viewPager = (ViewPager) findViewById(R.id.viewpager);
            setupViewPager(viewPager);

            tabLayout = (TabLayout) findViewById(R.id.tabs);
            tabLayout.setupWithViewPager(viewPager);
            curModId=R.id.setting_cloudlet_ip;
            inputDialogResult = Const.CLOUDLET_GABRIEL_IP;
//            sendOpenFaceResetRequest(currentServerIp);
            mSharedPreferences=getSharedPreferences(getString(R.string.shared_preference_file_key),
                    MODE_PRIVATE);
        }
        Log.d(TAG,"on create");
        mActivity=this;
    }


    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        childFragment = new CloudletFragment();
        adapter.addFragment(childFragment, "Face Swap Demo");
//        adapter.addFragment(new CloudFragment(), "cloud-EC2");
        viewPager.setAdapter(adapter);
    }



    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFragment(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return mFragmentTitleList.get(position);
        }
    }


    //activity menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cloudlet_demo, menu);
        return true;
    }

    @Override
    public void onDialogEditTextResult(String result) {
        inputDialogResult=result;
    }

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
            } else {
                if (curModId == R.id.setting_cloudlet_ip){
                    Const.CLOUDLET_GABRIEL_IP = inputDialogResult;
                    Log.i(TAG, "cloudlet ip changed to : " + Const.CLOUDLET_GABRIEL_IP);
                } else if (curModId == R.id.setting_cloud_ip){
                    Const.CLOUD_GABRIEL_IP = inputDialogResult;
                    Log.i(TAG, "cloudlet ip changed to : " + Const.CLOUD_GABRIEL_IP);
                }
                curModId = -1;
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
        if(requestCode == FilePickerActivity.REQUEST_FILE && resultCode==RESULT_OK){
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
                        Const.CLOUDLET_GABRIEL_IP,
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
            case R.id.setting_copy_server_state:
                //TODO: alertdialog let user select which server to copy from
                AlertDialog dg =SelectServerAlertDialog.createDialog(
                        mActivity,
                        "Pick a Server",
                        getAllServerNames(),
                        launchCopyStateAsyncTaskAction,
                        SelectServerAlertDialog.cancelAction,
                        true);
                dg.show();
                return true;

            case R.id.setting_load_state:
                //check online
                if (!checkOnline(this)) {
                    return false;
                }
                //launch activity result to readin states
                Intent intent = prepareForResultIntentForFilePickerActivity(this, true);
                startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
                return true;
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

}



//    /**
//     * Create and return an example alert dialog with an edit text box.
//     */
//    public Dialog createExampleDialog(String title, String msg) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setTitle(title);
//        builder.setMessage(msg);
//
//        // Use an EditText view to get user input.
//        final EditText input = new EditText(this);
//        input.setText("");
//        dialogInputTextEdit = input;
//        inputDialogResult=null;
//        builder.setView(input);
//
//        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int whichButton) {
//            }
//        });
//
//        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                return;
//            }
//        });
//
//        AlertDialog alertDialog = builder.create();
//        alertDialog.show();
//        Button customOkButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
//        customOkButton.setOnClickListener(new CustomListener(alertDialog));
//        return alertDialog;
//    }
//
//    class CustomListener implements View.OnClickListener {
//        private final Dialog dialog;
//        public CustomListener(Dialog dialog) {
//            this.dialog = dialog;
//        }
//        @Override
//        public void onClick(View v) {
//            String value = dialogInputTextEdit.getText().toString();
//            Log.d(TAG, "user input: " + value);
//            if (!isIpAddress(value)) {
//                Toast.makeText(getApplicationContext(),
//                        "Invalid IP address", Toast.LENGTH_SHORT).show();
//            } else {
//                sendOpenFaceResetRequest(value);
//                inputDialogResult = value;
//                dialog.dismiss();
//            }
//            return;
//        }
//    }
