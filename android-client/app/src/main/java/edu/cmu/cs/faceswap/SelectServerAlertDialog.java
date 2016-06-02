package edu.cmu.cs.faceswap;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by junjuew on 3/9/16.
 */
public class SelectServerAlertDialog {
    //deliminiator used for alertdialog when generating prefix from addPersonButton
    public static String IP_NAME_PREFIX_DELIMITER=":";
    public static CharSequence[] itemArray;
    //whether or not current alertdialog has prefix added to its items
    public static boolean hasPrefix=false;

    public static DialogInterface.OnClickListener cancelAction =new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            return;
        }
    };

    public static CharSequence[] getItemArrayWithoutPrefix(){
        CharSequence[] noPrefix = new CharSequence[itemArray.length];
        for (int idx=0; idx<noPrefix.length;idx++){
            String item=itemArray[idx].toString();
            int deliminiterIdx=item.lastIndexOf(IP_NAME_PREFIX_DELIMITER);
            noPrefix[idx]=item.substring(deliminiterIdx+1,item.length());
        }
        return noPrefix;
    }

    public static AlertDialog createDialog(Context m, String title, List<String> items,
                                           DialogInterface.OnClickListener pos,
                                           DialogInterface.OnClickListener neg,
                                           boolean addedPrefix) {
        CharSequence[] chooseItems = items.toArray(new CharSequence[items.size()]);
        return createDialog(m, title, chooseItems,pos,neg, addedPrefix);
    }

    public static AlertDialog createDialog(Context m, String title, CharSequence[] items,
                                           DialogInterface.OnClickListener pos,
                                           DialogInterface.OnClickListener neg,
                                           boolean addedPrefix) {
        AlertDialog.Builder builder = new AlertDialog.Builder(m);
        builder.setTitle(title)
                .setItems(items, pos);
//        builder.setPositiveButton("Ok", pos);
        builder.setNegativeButton("Cancel", neg);
        itemArray=items;
        hasPrefix=addedPrefix;
        return builder.create();
    }

    public static AlertDialog createDialog(Context m, String title, String prefix, Set<String> items,
                                           DialogInterface.OnClickListener pos,
                                           DialogInterface.OnClickListener neg,
                                           boolean addedPrefix) {
        CharSequence[] chooseItems = new CharSequence[items.size()];
        int idx=0;
        for (String item:items){
            chooseItems[idx] = prefix+item;
            idx++;
        }
        List<String> convert=new ArrayList<String>();
        convert.addAll(items);
        return createDialog(m, title, convert, pos, neg,addedPrefix);
    }

}
