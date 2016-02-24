package edu.cmu.cs.cloudletdemo;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.GabrielConfigurationAsyncTask;
import edu.cmu.cs.gabriel.R;

public class CloudletFragment extends DemoFragment implements CompoundButton.OnCheckedChangeListener {
    private final int LAUNCHCODE = 0;
    private static final int DLG_EXAMPLE1 = 0;
    private static final int TEXT_ID = 1000;
    public String inputDialogResult=null;
    private static final String TAG = "FaceSwapFragment";

    private List<PersonUIRow> personUIList = new ArrayList<PersonUIRow>();

    public CloudletFragment() {

    }

    private CloudletDemoActivity getMyAcitivty(){
        CloudletDemoActivity a = (CloudletDemoActivity) getActivity();
        return a;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = super.onCreateView(inflater, container, savedInstanceState);
//        titleTextView.setText("cloudlet ip");
//        ipTextView.setText(Const.CLOUDLET_GABRIEL_IP);
//        resetIPButton.setOnClickListener(new Button.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String input = String.valueOf(ipEditText.getText());
//                if (isIpAddress(input)) {
//                    ipTextView.setText(input);
//                    Const.CLOUDLET_GABRIEL_IP = input;
//                } else {
//                    new AlertDialog.Builder(getContext())
//                            .setTitle("Invalid")
//                            .setMessage("Please Enter a Valid IP Address")
//                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog, int which) {
//                                    // do nothing
//                                }
//                            })
//                            .setIcon(android.R.drawable.ic_dialog_alert)
//                            .show();
//                }
//            }
//        });

        cloudletRunDemoButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Const.GABRIEL_IP=Const.CLOUDLET_GABRIEL_IP;
                Intent intent = new Intent(getContext(), GabrielClientActivity.class);
                intent.putExtra("faceTable", faceTable);
                startActivity(intent);
                Toast.makeText(getContext(), "initializing demo", Toast.LENGTH_SHORT).show();
            }
        });

        cloudRunDemoButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                GabrielConfigurationAsyncTask task =
                        new GabrielConfigurationAsyncTask(getActivity(),
                        Const.CLOUDLET_GABRIEL_IP,
                        GabrielClientActivity.VIDEO_STREAM_PORT,
                        GabrielClientActivity.RESULT_RECEIVING_PORT,
                                Const.GABRIEL_CONFIGURATION_SYNC_STATE);
                task.execute(Const.GABRIEL_CONFIGURATION_SYNC_STATE);

                try {
                    task.get();
                    Const.GABRIEL_IP=Const.CLOUD_GABRIEL_IP;
                    Intent intent = new Intent(getContext(), GabrielClientActivity.class);
                    intent.putExtra("faceTable", faceTable);
                    startActivity(intent);
                    Toast.makeText(getContext(), "initializing demo", Toast.LENGTH_SHORT).show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        });

        addPersonButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Dialog dg = createAddPersonDialog("Train", "Enter Person's name:");
                dg.show();
            }
        });
        // Inflate the layout for this fragment
        return view;
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        String name = data.getStringExtra("name");
//        addTrainedPerson(name);
    }

    protected boolean checkName(String name){
        if (null == name || name.isEmpty()){
            new AlertDialog.Builder(getContext())
                    .setTitle("Invalid")
                    .setMessage("Please Enter a Valid Name")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return false;
        }

        if (trainedPeople.contains(name)){
            new AlertDialog.Builder(getContext())
                    .setTitle("Duplicate")
                    .setMessage("Duplicate Name Entered")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return false;
        }

        return true;
    }

    public Dialog createAddPersonDialog(String title, String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getMyAcitivty());
        builder.setTitle(title);
        builder.setMessage(msg);

        // Use an EditText view to get user input.
        final EditText input = new EditText(getMyAcitivty());
        input.setText("");
        builder.setView(input);

        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString();
                Log.d(TAG, "user input: " + value);
                inputDialogResult = value;
                String name = inputDialogResult;
                if (checkName(name)) {
                    Log.d(TAG, name);
                    trainedPeople.add(name);
                    addPersonUIRow(name);
                    Log.d(TAG, "add name :" + name);
                    startGabrielActivity(name);
                }
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

    private void addPersonUIRow(String name) {
        //create a new table row
        TableRow tr = new TableRow(getContext());
        TableRow.LayoutParams trTlp = new TableRow.LayoutParams(
                0,
                TableLayout.LayoutParams.WRAP_CONTENT
        );
        tr.setLayoutParams(trTlp);

        //create name view
        TextView nameView = new TextView(getContext());
        nameView.setText(name);
        nameView.setTextSize(20);
        TableRow.LayoutParams tlp1 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp1.column=0;
        nameView.setLayoutParams(tlp1);

        //TODO: switch add on toggle listener
        Switch sw = new Switch(getContext());
        TableRow.LayoutParams tlp2 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp2.column=1;
        tlp2.gravity=Gravity.CENTER_HORIZONTAL;
        sw.setTextOff("OFF");
        sw.setTextOn("ON");
        sw.setHeight(20);
        sw.setOnCheckedChangeListener(this);
        sw.setLayoutParams(tlp2);

        //create sub view
        TextView subView = new TextView(getContext());
        subView.setText("");
        subView.setTextSize(20);
        TableRow.LayoutParams tlp3 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp3.column=2;
        subView.setLayoutParams(tlp3);
        subView.setVisibility(View.INVISIBLE);

        //create delete button
        ImageView deleteView = new ImageView(getContext());
        deleteView.setImageResource(R.drawable.ic_delete_black_24dp);
        TableRow.LayoutParams tlp4 = new TableRow.LayoutParams(
                TableLayout.LayoutParams.WRAP_CONTENT,
                TableLayout.LayoutParams.MATCH_PARENT
        );
        tlp4.column=3;
        deleteView.setLayoutParams(tlp4);
        deleteView.setOnClickListener(new ImageView.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toBeRemovedName=null;
                PersonUIRow toBeRemovedRow=null;
                //find the name of the person to be removed
                for (PersonUIRow uiRow: personUIList){
                    if (uiRow.deleteView == v){
                        toBeRemovedName = (String) uiRow.nameView.getText();
                        toBeRemovedRow = uiRow;
                        break;
                    }
                }
                if (null != toBeRemovedName){
                    //TODO: send async request to server to remove
                    GabrielConfigurationAsyncTask task =
                            new GabrielConfigurationAsyncTask(getActivity(),
                                    Const.CLOUDLET_GABRIEL_IP,
                                    GabrielClientActivity.VIDEO_STREAM_PORT,
                                    GabrielClientActivity.RESULT_RECEIVING_PORT,
                                    Const.GABRIEL_CONFIGURATION_REMOVE_PERSON);
                    task.execute(toBeRemovedName);
                    trainedPeople.remove(toBeRemovedName);
                }

                //remove current line
                if (null != toBeRemovedRow){
                    personUIList.remove(toBeRemovedRow);
                    tb.removeView(toBeRemovedRow.tr);
                } else {
                    Log.e(TAG, "delete icon clicked, but didn't find any row to remove");
                }

            }
        });

        tr.addView(nameView);
        tr.addView(sw);
        tr.addView(subView);
        tr.addView(deleteView);

        tb.addView(tr,
                new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT,
                        TableLayout.LayoutParams.WRAP_CONTENT));

        PersonUIRow uiRow = new PersonUIRow(tr,nameView, sw, subView, deleteView);
        personUIList.add(uiRow);
    }

    private void startGabrielActivity(String name) {
        //TODO: how to handle sync faces between cloud and cloudlet?
        Const.GABRIEL_IP = Const.CLOUDLET_GABRIEL_IP;
        Intent intent = new Intent(getContext(), GabrielClientActivity.class);
        intent.putExtra("name", name);
        startActivityForResult(intent, LAUNCHCODE);
        Toast.makeText(getContext(), "training", Toast.LENGTH_SHORT).show();
    }


    private String chosen = null;

    @Override
    public void onCheckedChanged(final CompoundButton buttonView, boolean isChecked) {
        PersonUIRow searchUIRow=null;
        for (PersonUIRow uiRow : personUIList) {
            if (uiRow.switchView == buttonView) {
                searchUIRow=uiRow;
                break;
            }
        }
        final PersonUIRow curUIRow = searchUIRow;

        if (isChecked){
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Make your selection");
//            ArrayList<String> copyTrainedPeople = new ArrayList<String>(trainedPeople);
//            copyTrainedPeople.remove(curUIRow.nameView.getText());
            final String[] itemArray= new String[trainedPeople.size()];
            trainedPeople.toArray(itemArray);

            builder.setItems(itemArray, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
//                    PersonUIRow curUIRow = null;
//                    for (PersonUIRow uiRow : personUIList) {
//                        if (uiRow.switchView == buttonView) {
//                            curUIRow = uiRow;
//                            break;
//                        }
//                    }

                    // Do something with the selection
                    chosen = itemArray[item];
                    if (chosen.equals(curUIRow.nameView.getText())) {
                        curUIRow.switchView.setChecked(false);
                        return;
                    }
                    curUIRow.subView.setText(chosen);
                    curUIRow.subView.setVisibility(View.VISIBLE);
                    faceTable.put((String) curUIRow.nameView.getText(), chosen);
                    Log.d(TAG, "chose to be substitute: " + chosen);
                    return;
                }
            });
            AlertDialog alert = builder.create();
            alert.setCanceledOnTouchOutside(false);
            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    curUIRow.switchView.setChecked(false);
                }
            });
            alert.show();
        } else {
            curUIRow.subView.setText("");
            curUIRow.subView.setVisibility(View.INVISIBLE);
            faceTable.remove(curUIRow.nameView.getText());
        }
    }

    public void clearTrainedPeople(){
        if (null != trainedPeople){
            trainedPeople.clear();
            for (PersonUIRow uiRow: personUIList){
                tb.removeView(uiRow.tr);
            }
        }
    }
}
