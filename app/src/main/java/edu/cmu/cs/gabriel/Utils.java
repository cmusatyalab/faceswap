package edu.cmu.cs.gabriel;

/**
 * Created by junjuew on 1/21/16.
 */
public class Utils {
    public static int[] stringArrayToIntArray(String[] stringArray) {
        int[] results = new int[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            try {
                results[i] = Integer.parseInt(stringArray[i]);
            } catch (NumberFormatException nfe) {

            };
        }
        return results;
    }

}
