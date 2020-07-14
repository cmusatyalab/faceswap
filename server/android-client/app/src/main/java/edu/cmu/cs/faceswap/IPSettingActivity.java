package edu.cmu.cs.faceswap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

import androidx.cardview.widget.CardView;
import edu.cmu.cs.utils.NetworkUtils;

import static edu.cmu.cs.CustomExceptions.CustomExceptions.notifyError;

public class IPSettingActivity extends AppCompatActivity {
    private static final String TAG = "IPSettingActivity";

    private Toolbar toolbar;
    private SharedPreferences mSharedPreferences;
    private Button addIpButton, changeDefaultButton,nextButton;
    private EditText ipNameEditText,ipEditText;
    private Spinner placeSpinner;
    private Activity mActivity;
    private TableLayout[] tbs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ipsetting);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mSharedPreferences=getSharedPreferences(getString(R.string.shared_preference_file_key),
                MODE_PRIVATE);
        addIpButton=(Button)findViewById(R.id.add_ip_button);
nextButton=(Button)findViewById(R.id.add_next_button);
//       // ipNameEditText= (EditText) findViewById(R.id.add_ip_ip_cardview);
     ipNameEditText=(EditText)findViewById(R.id.add_ip_name_edittext);
      ipEditText=(EditText)findViewById(R.id.add_ip_ip_edittext);
        placeSpinner=(Spinner)findViewById(R.id.add_ip_place_spinner);
        TableLayout cloudletTb = (TableLayout)findViewById(R.id.add_ip_cloudlet_table);
        TableLayout cloudTb = (TableLayout)findViewById(R.id.add_ip_cloud_table);
        tbs=new TableLayout[]{cloudletTb, cloudTb};
        initializeTableLayouts();

        //TODO: read off sharedpreferences about current cloudlet ip and cloudip
        mActivity=this;
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(IPSettingActivity.this,
                        CloudletDemoActivity.class);
                //Intent is used to switch from one activity to another.

                startActivity(i);
            }
        });
        addIpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String name = ipNameEditText.getText().toString();
                String ip = ipEditText.getText().toString();
                //assume type 0 is cloudlet
                // type 1 is cloud
                int type=placeSpinner.getSelectedItemPosition();
                String sharedPreferenceIpDictName=
                        getResources().getStringArray(R.array.shared_preference_ip_dict_names)[type];
                // a deep copy is needed
                //http://stackoverflow.com/questions/21396358/sharedpreferences-putstringset-doesnt-work
                Set<String> existingNames =
                        new HashSet<String>(mSharedPreferences.getStringSet(sharedPreferenceIpDictName,
                        new HashSet<String>()));
                if (name.isEmpty()){
                    notifyError("Invalid Name", false, mActivity);
                    return;
                } else if (existingNames.contains(name)){
                    notifyError("Duplicate Name", false, mActivity);
                    return;
                } else if (mSharedPreferences.getAll().containsKey(name)){
                    //TODO: here cloud and cloudlet server cannot share
                    // the same name
                    notifyError("Duplicate Name", false, mActivity);
                    return;
                } else if (!NetworkUtils.isIpAddress(ip)){
                    notifyError("Invalid IP Address", false, mActivity);
                    return;
                }
                else
                {
                    Toast.makeText(getApplicationContext(),"IP SUCCESFULLY ADDED",Toast.LENGTH_LONG).show();
                }
                //add new ip in
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                editor.putString(name,ip);
                existingNames.add(name);
                editor.putStringSet(sharedPreferenceIpDictName, existingNames);
                editor.apply();
                //update UI
                addPersonUIRow(mSharedPreferences, type, name, ip);
                ipEditText.setText("");
                ipNameEditText.setText("");
            }
        });
    }

    /**
     * populate cloudlet and cloud ip tables based on existing configurations
     */
    private void initializeTableLayouts(){
        for (int type=0;type<tbs.length;type++){
            String sharedPreferenceIpDictName=
                    getResources().getStringArray(R.array.shared_preference_ip_dict_names)[type];
            Set<String> existingNames =
                    mSharedPreferences.getStringSet(sharedPreferenceIpDictName,
                            new HashSet<String>());
            for (String name:existingNames){
                String ip = mSharedPreferences.getString(name,null);
                addPersonUIRow(mSharedPreferences, type, name, ip);
            }
        }
    }

    private void addPersonUIRow(final SharedPreferences mSharedPreferences, final int type,
                                final String name, String ip) {
        final TableLayout tb=tbs[type];

        //create a new table row
        final TableRow tr = new TableRow(this);
        TableRow.LayoutParams trTlp = new TableRow.LayoutParams(
                0,
                TableLayout.LayoutParams.WRAP_CONTENT
        );
        tr.setLayoutParams(trTlp);

        //create name view
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(20);
        TableRow.LayoutParams tlp1 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp1.column=0;
        nameView.setLayoutParams(tlp1);

        //create sub view
        TextView subView = new TextView(this);
        subView.setText(ip);
        subView.setTextSize(20);
        TableRow.LayoutParams tlp3 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp3.column=1;
        subView.setLayoutParams(tlp3);

        //create delete button
        ImageView deleteView = new ImageView(this);
        deleteView.setImageResource(R.drawable.ic_delete_black_24dp);
        TableRow.LayoutParams tlp4 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp4.column=2;
        deleteView.setLayoutParams(tlp4);
        deleteView.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View v) {
                //remove name from sharedPreferences
                String sharedPreferenceIpDictName=
                        getResources().getStringArray(R.array.shared_preference_ip_dict_names)[type];
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                Set<String> existingNames =
                        new HashSet<String>(mSharedPreferences.getStringSet(sharedPreferenceIpDictName,
                                new HashSet<String>()));
                editor.remove(name);
                existingNames.remove(name);
                editor.putStringSet(sharedPreferenceIpDictName, existingNames);
                editor.commit();

                //remove current line from UI
                tb.removeView(tr);
            }
        });

        tr.addView(nameView);
        tr.addView(subView);
        tr.addView(deleteView);

        tb.addView(tr,
                new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));
    }
}
