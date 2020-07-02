package edu.cmu.cs.faceswap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;


import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;



import static edu.cmu.cs.utils.NetworkUtils.isOnline;
import static edu.cmu.cs.utils.NetworkUtils.checkOnline;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.GabrielConfigurationAsyncTask;

public class MainActivity2 extends AppCompatActivity  {

    View view;
    private Object Bundle;
    private Object ViewGroup;
   protected Button setting_save_state_local, reset, setting_save_state_googleDrive;

    private  MainActivity2 getActivity() {
        MainActivity2 a = (MainActivity2) getActivity();
        return a;
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);


        Button setting_save_state_local = findViewById(R.id.setting_save_state);
        Button reset = (Button)findViewById(R.id.setting_reset_openface_server);
        Button setting_save_state_googleDrive = (Button)findViewById(R.id.setting_save_state_to_gdrive);
        boolean clicked=false;
      clicked= reset.isPressed();

        //then on another method or where you want
        if(clicked==true)
        {
            onOptionsItemSelected((MenuItem) findViewById(R.id.setting_reset_openface_server));
        }


    }
    }





