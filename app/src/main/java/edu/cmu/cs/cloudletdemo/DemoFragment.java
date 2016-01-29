package edu.cmu.cs.cloudletdemo;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

import edu.cmu.cs.gabriel.Const;
import edu.cmu.cs.gabriel.GabrielClientActivity;
import edu.cmu.cs.gabriel.R;

/**
 * Created by junjuew on 1/28/16.
 */
public class DemoFragment extends Fragment {
    protected TextView titleTextView;
    protected TextView ipTextView;
    protected Button resetIPButton;
    protected EditText ipEditText;
    protected EditText nameEditText;
    protected Button runDemoButton;
    protected Button addPersonButton;
    protected static final String
            IPV4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
    protected static final String IPV6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";
    protected View view;
    protected List<String> trainedPeople;
    protected TableLayout tb;


    public DemoFragment() {
        this.trainedPeople= new ArrayList<String>();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        view=inflater.inflate(R.layout.cloudlet_fragment,container,false);

        titleTextView=(TextView)view.findViewById(R.id.titleView);
        ipTextView=(TextView)view.findViewById(R.id.cloudletIPView);
        resetIPButton=(Button)view.findViewById(R.id.resetIPButton);
        ipEditText=(EditText)view.findViewById(R.id.cloudletIPEditTextView);
        nameEditText=(EditText)view.findViewById(R.id.nameEditText);

        runDemoButton=(Button)view.findViewById(R.id.cloudletRunDemoButton);
        addPersonButton = (Button)view.findViewById(R.id.addPersonButton);
        tb = (TableLayout)view.findViewById(R.id.trainedTable);
        // Inflate the layout for this fragment
        return view;
    }

    public boolean isIpAddress(String ipAddress) {
        if (ipAddress.matches(IPV4Pattern) || ipAddress.matches(IPV6Pattern)) {
            return true;
        }
        return false;
    }


    //TODO: find a way to save programmatically added view. Whenever current activity is paused
    // (screen resolution change, these dynamicallly added views are lost)
    public void addTrainedPerson(String name){
        // 2 column
        TableRow tbr;
        TableRow.LayoutParams tbp = new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT
        );
        if (this.trainedPeople.size() %2 ==0){
            tbr = new TableRow(getContext());
            tbp.column = 0;
        } else {
            tbr = (TableRow) tb.getChildAt(tb.getChildCount()-1);
            tbp.column = 1;
            tb.removeView(tbr);
        }
        TextView tv = new TextView(getContext());
        tv.setText(name);
        tv.setLayoutParams(tbp);
        tbr.addView(tv);
        tb.addView(tbr, new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.MATCH_PARENT));

        this.trainedPeople.add(name);
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
        return true;
    }
}
