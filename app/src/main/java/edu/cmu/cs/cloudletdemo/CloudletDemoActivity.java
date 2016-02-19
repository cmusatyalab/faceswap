package edu.cmu.cs.cloudletdemo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.cmu.cs.gabriel.R;

public class CloudletDemoActivity extends AppCompatActivity {
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    public HashMap<String, String> faceTable;
    public Set<String> trainedPeople;

    private static final String TAG = "cloudletDemoActivity";
    private static final int DLG_EXAMPLE1 = 0;
    private static final int TEXT_ID = 0;

    public boolean reset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloudlet_demo);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);

        faceTable = new HashMap<String, String>();
        trainedPeople = new HashSet<String>();
        reset=false;
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new CloudletFragment(), "Face Swap Demo");
//        adapter.addFragment(new CloudFragment(), "cloud-EC2");
        viewPager.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_cloudlet_demo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.setting_cloudlet_ip) {
            Dialog settingDialog = createExampleDialog();
            settingDialog.show();
            return true;
        }
        if (id == R.id.setting_cloud_ip) {
            Dialog settingDialog = createExampleDialog();
            settingDialog.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
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

    /**
     * If a dialog has already been created,
     * this is called to reset the dialog
     * before showing it a 2nd time. Optional.
     */
    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DLG_EXAMPLE1:
                // Clear the input box.
                EditText text = (EditText) dialog.findViewById(TEXT_ID);
                text.setText("");
                break;
        }
    }


    /**
     * Create and return an example alert dialog with an edit text box.
     */
    private Dialog createExampleDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        builder.setMessage("Enter Cloudlet IP:");

        // Use an EditText view to get user input.
        final EditText input = new EditText(this);
        input.setId(TEXT_ID);
        builder.setView(input);

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                Log.d(TAG, "cloudlet ip: " + value);
                return;
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                return;
            }
        });

        return builder.create();
    }
}
