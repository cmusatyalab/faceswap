package edu.cmu.cs.cloudletdemo;

import android.widget.Switch;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * Created by junjuew on 2/18/16.
 */
public class PersonUIRow {
    public TextView nameView;
    public Switch switchView;
    public TextView subView;
    public TableRow tr;

    public PersonUIRow(TableRow tr, TextView nameView, Switch switchView, TextView subView){
        this.nameView =nameView;
        this.switchView = switchView;
        this.subView = subView;
        this.tr = tr;
    }

}
