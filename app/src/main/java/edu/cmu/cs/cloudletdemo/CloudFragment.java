package edu.cmu.cs.cloudletdemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.R;

public class CloudFragment extends DemoFragment {
    private final int LAUNCHCODE = 1;

    public CloudFragment() {

    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        titleTextView.setText("cloud ip");
        ipTextView.setText(Const.CLOUD_GABRIEL_IP);
        resetIPButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String input = String.valueOf(ipEditText.getText());
                if (isIpAddress(input)) {
                    ipTextView.setText(input);
                    Const.CLOUD_GABRIEL_IP = input;
                } else {
                    new AlertDialog.Builder(getContext())
                            .setTitle("Invalid")
                            .setMessage("Please Enter a Valid IP Address")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            }
        });

        runDemoButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                Const.GABRIEL_IP = Const.CLOUD_GABRIEL_IP;
                Intent intent = new Intent(getContext(), GabrielClientActivity.class);
                startActivity(intent);
                Toast.makeText(getContext(), "initializing demo", Toast.LENGTH_SHORT).show();
            }
        });

        addPersonButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = String.valueOf(nameEditText.getText());
                if (checkName(name)){
                    Const.GABRIEL_IP = Const.CLOUD_GABRIEL_IP;
                    Intent intent = new Intent(getContext(), GabrielClientActivity.class);
                    intent.putExtra("name", name);
                    startActivityForResult(intent, LAUNCHCODE);
                    Toast.makeText(getContext(), "training", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String name = data.getStringExtra("name");
        addTrainedPerson(name);
    }

}
