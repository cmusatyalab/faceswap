package edu.cmu.cs.cloudletdemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;


import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
            sendOpenFaceResetRequest(Const.CLOUDLET_GABRIEL_IP);
            mSharedPreferences=getSharedPreferences(getString(R.string.shared_preference_file_key),
                    MODE_PRIVATE);
        }

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

            String serverLocation ="";
            if (curModId == R.id.setting_cloudlet_ip){
                serverLocation="Cloudlet";
            } else if (curModId == R.id.setting_cloud_ip){
                serverLocation="Cloud";
            }

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
//                new EditTextAlertDialog(this, this).createDialog("Settings",
//                        "No Gabriel Server Found. \n" +
//                                "Please enter a Gabriel Server IP (" + serverLocation + "): ", "");
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
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == FilePickerActivity.REQUEST_FILE && resultCode==RESULT_OK){
            Bundle extras = data.getExtras();
            String path = (String) extras.get(FilePickerActivity.FILE_EXTRA_DATA_PATH);
            Log.d(TAG, "path: " + path);
            boolean isLoad=(boolean)extras.get(FilePickerActivity.INTENT_EXTRA_ACTION_READ);
//            UIUtils uiHelper=new UIUtils();
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
            Log.d(TAG, "send reset openface server request");
            childFragment.clearTrainedPeople();
        } else {
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE, false, this);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

//        //noinspection SimplifiableIfStatement
//        if (id == R.id.setting_cloudlet_ip) {
//            Intent i = new Intent(this, IPSettingActivity.class);
//            startActivity(i);
//            curModId = R.id.setting_cloudlet_ip;
//            return true;
//        }
//        if (id == R.id.setting_cloud_ip) {
//            curModId = R.id.setting_cloud_ip;
//            return true;
//        }

        if (id==R.id.manage_servers){
            Intent i = new Intent(this, IPSettingActivity.class);
            startActivity(i);
        }

        if (id == R.id.setting_reset_openface_server) {
            //check wifi state
            if(checkOnline(this)){
                sendOpenFaceResetRequest(Const.CLOUDLET_GABRIEL_IP);
                return true;
            }
            return false;
        }

        if (id==R.id.setting_load_state){
            //check online
            if (!checkOnline(this)){
                return false;
            }
            //launch activity result to readin states
            Intent intent=prepareForResultIntentForFilePickerActivity(this,true);
            startActivityForResult(intent, FilePickerActivity.REQUEST_FILE);
        }

        if (id == R.id.setting_save_state) {
            if (!checkOnline(this)){
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
        }
        if (id == R.id.setting_load_state) {
            //check wifi state
            //load state
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



}
