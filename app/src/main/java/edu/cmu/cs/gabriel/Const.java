package edu.cmu.cs.gabriel;

import java.io.File;

import android.os.Environment;

public class Const {
	/* 
	 * Experiement variable
	 */
	
	public static final boolean IS_EXPERIMENT = false;
	
	// Transfer from the file list
	// If TEST_IMAGE_DIR is not none, transmit from the image
	public static File ROOT_DIR = new File(Environment.getExternalStorageDirectory() + File.separator + "Gabriel" + File.separator);
	public static File TEST_IMAGE_DIR = new File (ROOT_DIR.getAbsolutePath() + File.separator + "images" + File.separator);	
	
	// control VM
	public static String GABRIEL_IP = "128.2.213.107";	// Cloudlet by default
//	public static String GABRIEL_IP = "54.201.173.207";	// Amazon West
    public static String CLOUDLET_GABRIEL_IP = "128.2.213.107";	// Cloudlet
//    public static String CLOUDLET_GABRIEL_IP = "128.2.13.107";	// Cloudlet
//	public static String CLOUD_GABRIEL_IP = "54.200.101.84";	// Amazon West
	public static String CLOUD_GABRIEL_IP = "54.189.165.75";	// Amazon West
	
	// Token
	public static int MAX_TOKEN_SIZE = 1;
	
	// image size and frame rate
	public static int MIN_FPS = 10;
	public static int IMAGE_WIDTH = 640;
	//640x480
	//800x600
	//1024x960

	// Result File
	public static String LATENCY_FILE_NAME = "latency-" + GABRIEL_IP + "-" + MAX_TOKEN_SIZE + ".txt";
	public static File LATENCY_DIR = new File(ROOT_DIR.getAbsolutePath() + File.separator + "exp");
	public static File LATENCY_FILE = new File (LATENCY_DIR.getAbsolutePath() + File.separator + LATENCY_FILE_NAME);

	//result type: one-hot
	public static boolean RESPONSE_ENCODED_IMG=false;
	public static boolean RESPONSE_ROI_FACE_SNIPPET=false;
	public static boolean RESPONSE_JSON=true;

	//display preview or img processed from gabriel server
	public static boolean DISPLAY_PREVIEW_ONLY=false;

	public static String GABRIEL_CONFIGURATION_SYNC_STATE="syncState";
	public static String GABRIEL_CONFIGURATION_RESET_STATE="reset";
	public static String GABRIEL_CONFIGURATION_REMOVE_PERSON="remove_person";

	public static boolean FACE_DEMO_BOUNDING_BOX_ONLY=false;

    public static String CONNECTIVITY_NOT_AVAILABLE = "Not Connected to the Internet. Please enable " +
            "network connections first.";

}
