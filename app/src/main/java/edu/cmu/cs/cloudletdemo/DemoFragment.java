package edu.cmu.cs.cloudletdemo;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.os.Build;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.GabrielConfigurationAsyncTask;
import edu.cmu.cs.gabriel.R;

/**
 * Created by junjuew on 1/28/16.
 */
public class DemoFragment extends Fragment implements  AdapterView.OnItemSelectedListener{
    private final int LAUNCHCODE_GET_STATE= 1;

    protected TextView titleTextView;
    protected TextView ipTextView;
    protected Button resetIPButton;
    protected EditText ipEditText;
    protected EditText nameEditText;
    protected Button cloudletRunDemoButton;
    protected Button cloudRunDemoButton;
    protected Button addPersonButton;
    protected Button addRuleButton;
    protected Button removeRuleButton;
    protected Button resetButton;
    protected Button getStateButton;
    protected RadioGroup typeRadioGroup;
    protected RadioButton cloudletRadioButton;
    protected RadioButton cloudRadioButton;
    protected Spinner selectServerSpinner;

    protected View view;
    protected List<String> spinnerList;
    protected TableLayout tb;
    protected LinearLayout personLinearLayout;
    protected Spinner fromSpinner, toSpinner;
    public ArrayAdapter<String> spinnerAdapter;
    public TextView trainPeopleTextView;
    public TextView ruleTextView;

    private static final String LOG_TAG = "fragment";

    public List<String> trainedPeople;
    public HashMap<String, String> faceTable;

    public DemoFragment() {
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        this.trainedPeople= new ArrayList<String>();
//        this.trainedPeople= new HashSet<String>();
        this.spinnerList = new ArrayList<String>();
        faceTable = new HashMap<String, String>();
        trainedPeople = new ArrayList<String>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        view=inflater.inflate(R.layout.unified_fragment,container,false);

        titleTextView=(TextView)view.findViewById(R.id.titleView);
        ipTextView=(TextView)view.findViewById(R.id.cloudletIPView);
        resetIPButton=(Button)view.findViewById(R.id.resetIPButton);
        ipEditText=(EditText)view.findViewById(R.id.cloudletIPEditTextView);
        nameEditText=(EditText)view.findViewById(R.id.nameEditText);

        typeRadioGroup=(RadioGroup)view.findViewById(R.id.type_radiogroup);
        cloudletRadioButton=(RadioButton)view.findViewById(R.id.radio_cloudlet);
        cloudRadioButton=(RadioButton)view.findViewById(R.id.radio_cloud);
        typeRadioGroup.check(R.id.radio_cloudlet);

        selectServerSpinner=(Spinner) view.findViewById(R.id.select_server_spinner);

        cloudletRunDemoButton =(Button)view.findViewById(R.id.cloudletRunDemoButton);
//        cloudRunDemoButton =(Button)view.findViewById(R.id.cloudRunDemoButton);

        addPersonButton = (Button)view.findViewById(R.id.addPersonButton);
        addRuleButton=(Button)view.findViewById(R.id.addSwapRuleButton);
        removeRuleButton=(Button)view.findViewById(R.id.removeRuleButton);
        resetButton = (Button)view.findViewById(R.id.resetButton);

//        resetButton.setOnClickListener(new Button.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Log.d(LOG_TAG, "reset called");
//                ((CloudletDemoActivity) getActivity()).reset = true;
//                Toast.makeText(getContext(), "Training States will be reset next time you add a person or run demo",
//                        Toast.LENGTH_LONG).show();
//            }
//        });

//        getStateButton = (Button)view.findViewById(R.id.getStateButton);;
//        getStateButton.setOnClickListener(new Button.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                new GabrielConfigurationAsyncTask(getActivity(),
//                        Const.GABRIEL_IP,
//                        GabrielClientActivity.VIDEO_STREAM_PORT,
//                        GabrielClientActivity.RESULT_RECEIVING_PORT).execute(
//                        Const.GABRIEL_CONFIGURATION_SYNC_STATE);
//            }
//        });

//        Intent intent = new Intent(getContext(), GabrielClientActivity.class);
//        intent.putExtra("getState", true);
//        startActivity(intent);
//        Toast.makeText(getContext(), "getting state", Toast.LENGTH_SHORT).show();


        personLinearLayout = (LinearLayout)view.findViewById(R.id.personLinearLayout);
        tb = (TableLayout)view.findViewById(R.id.trainedTable);
        trainPeopleTextView = (TextView)view.findViewById(R.id.trainPeopleTextView);

        //populate spinners
//        fromSpinner = (Spinner) view.findViewById(R.id.fromSpinner);
//        toSpinner = (Spinner) view.findViewById(R.id.toSpinner);
//
//        spinnerAdapter = new ArrayAdapter<String>(getActivity(),
//                R.layout.support_simple_spinner_dropdown_item, spinnerList);
//        spinnerAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
//        fromSpinner.setAdapter(spinnerAdapter);
//        toSpinner.setAdapter(spinnerAdapter);
//        fromSpinner.setPrompt("Select a Person");
//        toSpinner.setPrompt("Select a Person");
//        fromSpinner.setOnItemSelectedListener(this);
//        toSpinner.setOnItemSelectedListener(this);
//        ruleTextView = (TextView)view.findViewById(R.id.textViewRules);

//        addRuleButton.setOnClickListener(new Button.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String fromPerson = (String) fromSpinner.getSelectedItem();
//                String toPerson = (String) toSpinner.getSelectedItem();
//                if ((null != fromPerson) || (null != toPerson)) {
//                    //add rule
//                    faceTable.put(fromPerson, toPerson);
//                    updateRulesUI();
//                } else {
//                    alert("Invalid Rule");
//                }
//            }
//        });
//        removeRuleButton.setOnClickListener(new Button.OnClickListener(){
//            @Override
//            public void onClick(View v) {
//                String fromPerson = (String)fromSpinner.getSelectedItem();
//                String toPerson = (String)toSpinner.getSelectedItem();
//                if ( (null != fromPerson) && (null!=toPerson) ){
//                    //add rule
//                    HashMap<String, String> mapping = ((CloudletDemoActivity)getActivity()).faceTable;
//                    if (toPerson.equals(mapping.get(fromPerson))){
//                        mapping.remove(fromPerson);
//                        updateRulesUI();
//                    } else {
//                        alert("Rule Not Found");
//                    }
//                } else {
//                    alert("Rule Not Found");
//                }
//            }
//        });

        return view;
    }

    private void alert(String msg){
        new AlertDialog.Builder(getContext())
                .setTitle("Invalid")
                .setMessage(msg)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }



    //TODO: find a way to save programmatically added view. Whenever current activity is paused
    // (screen resolution change, these dynamicallly added views are lost)
//    public void addTrainedPerson(String name){
//        if (checkName(name)){
//            Set<String> trainedPeople=((CloudletDemoActivity) getActivity()).trainedPeople;
//            if (!trainedPeople.contains(name)){
//                trainedPeople.add(name);
//                spinnerList.clear();
//                spinnerList.addAll(trainedPeople);
//                spinnerAdapter.notifyDataSetChanged();
//                Log.d("fragment", "add name :"+name);
//                trainPeopleTextView.setText(String.valueOf(trainedPeople));
//            }
//        }
//
//    }


//    protected void updateRulesUI(){
//        String rules="";
//        HashMap<String, String> mapping = ((CloudletDemoActivity)getActivity()).faceTable;
//        for (Map.Entry<String, String> entry: mapping.entrySet()){
//            String curRule = "replace "+entry.getKey() + "'s face with "
//                    + entry.getValue()+"'s face\n";
//            rules+=curRule;
//        }
//        ruleTextView.setText(rules);
//    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

}
