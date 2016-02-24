package edu.cmu.cs.gabriel;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import edu.cmu.cs.gabriel.network.NetworkProtocol;
import edu.cmu.cs.gabriel.network.ResultReceivingThread;
import edu.cmu.cs.gabriel.network.VideoStreamingThread;
import edu.cmu.cs.gabriel.token.TokenController;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.Camera.PreviewCallback;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

public class GabrielClientActivity extends Activity {

	private static final String LOG_TAG = "GabrielClientActivity";
	private static final String DEBUG_TAG = "krha_debug";

	private static final int SETTINGS_ID = Menu.FIRST;
	private static final int EXIT_ID = SETTINGS_ID + 1;
	private static final int CHANGE_SETTING_CODE = 2;

	public static final int VIDEO_STREAM_PORT = 9098;
	public static final int RESULT_RECEIVING_PORT = 9101;

	VideoStreamingThread videoStreamingThread;
	ResultReceivingThread resultThread;
	TokenController tokenController = null;

	private SharedPreferences sharedPref;
	private boolean hasStarted;
	private CameraPreview mPreview;

	private DisplaySurface mDisplay;
	private CameraOverlay cameraOverlay;
	private RelativeLayout rly;
	private TextView cnt_view;

	private String name = null;
	private HashMap<String, String> faceTable;
	private boolean reset = false;
	private Bitmap curFrame = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(DEBUG_TAG, "on onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		rly = (RelativeLayout) findViewById(R.id.camera_relative_layout);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
				WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON +
				WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		Intent intent = getIntent();
		reset = intent.getExtras().getBoolean("reset");
		if (intent.hasExtra("name")) {
			this.name = String.valueOf(intent.getStringExtra("name"));
			//add training overlay for count
			cnt_view = new TextView(getApplicationContext());
			cnt_view.setText("0");
			cnt_view.setTextColor(Color.RED);
			cnt_view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
			RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT,
					RelativeLayout.LayoutParams.WRAP_CONTENT
			);
			lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
			cnt_view.setLayoutParams(lp);
			rly.addView(cnt_view);

			Intent reply = new Intent();
			reply.putExtra("name", name);
			setResult(RESULT_OK, reply);
		} else if (intent.hasExtra("faceTable")) {
			faceTable = (HashMap<String, String>) intent.getSerializableExtra("faceTable");
		}

        //check wifi state
        boolean online = isOnline();
        if (!online){
            Log.d(LOG_TAG, "no internet connectivity");
            notifyError(Const.CONNECTIVITY_NOT_AVAILABLE);
        } else {
            init_once();
            init_experiement();
        }
	}

    public boolean isOnline() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

	private void init_once() {
		Log.d(DEBUG_TAG, "on init once");

		cameraOverlay = (CameraOverlay) findViewById(R.id.display_surface);
		cameraOverlay.bringToFront();

		mPreview = (CameraPreview) findViewById(R.id.camera_preview);
		if (Const.DISPLAY_PREVIEW_ONLY) {
			RelativeLayout.LayoutParams invisibleLayout = new RelativeLayout.LayoutParams(0, 0);
			mDisplay.setLayoutParams(invisibleLayout);
			mDisplay.setVisibility(View.INVISIBLE);
			mDisplay.setZOrderMediaOverlay(false);
		}

		mPreview.setPreviewCallback(previewCallback);
		cameraOverlay.setImageSize(mPreview.imageSize);

		Const.ROOT_DIR.mkdirs();
		Const.LATENCY_DIR.mkdirs();

		hasStarted = true;
	}

	private void init_experiement() {
		Log.d(DEBUG_TAG, "on init experiment");
		if (tokenController != null) {
			tokenController.close();
		}
		if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
			videoStreamingThread.stopStreaming();
			videoStreamingThread = null;
		}

		if ((resultThread != null) && (resultThread.isAlive())) {
			resultThread.close();
			resultThread = null;
		}

		try {
			Thread.sleep(3 * 1000);
		} catch (InterruptedException e) {
		}

		tokenController = new TokenController(Const.LATENCY_FILE);
		resultThread = new ResultReceivingThread(Const.GABRIEL_IP,
				RESULT_RECEIVING_PORT, returnMsgHandler, tokenController);
		resultThread.start();

//		FileDescriptor fd = cameraRecorder.getOutputFileDescriptor();
		FileDescriptor fd = null;
		if (null != name) {
			videoStreamingThread = new VideoStreamingThread(fd,
					Const.GABRIEL_IP, VIDEO_STREAM_PORT, returnMsgHandler, tokenController, reset, name);
		} else if (null != faceTable) {
			videoStreamingThread = new VideoStreamingThread(fd,
					Const.GABRIEL_IP, VIDEO_STREAM_PORT, returnMsgHandler, tokenController, reset);
		} else {
			videoStreamingThread = new VideoStreamingThread(fd,
					Const.GABRIEL_IP, VIDEO_STREAM_PORT, returnMsgHandler, tokenController, reset);
		}
		videoStreamingThread.start();
	}

	// Implements TextToSpeech.OnInitListener
	public void onInit(int status) {
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(DEBUG_TAG, "on resume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(DEBUG_TAG, "on pause");
		this.terminate();
		finish();
		Log.d(DEBUG_TAG, "out pause");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		this.terminate();
		finish();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;

		switch (item.getItemId()) {
			case SETTINGS_ID:
				intent = new Intent().setClass(this, SettingsActivity.class);
				startActivityForResult(intent, CHANGE_SETTING_CODE);
				break;
		}

		return super.onOptionsItemSelected(item);
	}

	/***
	 * call back once a preview is available
	 */
	private PreviewCallback previewCallback = new PreviewCallback() {
		public void onPreviewFrame(byte[] frame, Camera mCamera) {
///			Log.d(LOG_TAG, "onpreviewframe called. data transmitting");
			if (hasStarted) {
				Camera.Parameters parameters = mCamera.getParameters();
				if (videoStreamingThread != null) {
					videoStreamingThread.pushAsync(frame, parameters);
				}
			}
		}
	};

	/**
	 * format of face object json representation
	 * 'roi_x1':roi_x1,
	 * 'roi_y1':roi_y1,
	 * 'roi_x2':roi_x2,
	 * 'roi_y2':roi_y2,
	 * 'name':self.name,
	 * 'data':np_array_to_jpeg_string(self.data)
	 *
	 * @param response
	 * @return
	 */
	private Face parseFace(String response) {
		try {
			JSONObject obj = new JSONObject(response);
			int roi_x1 = obj.getInt(
					NetworkProtocol.CUSTOM_DATA_MESSAGE_ROI_X1);
			int roi_y1 = obj.getInt(
					NetworkProtocol.CUSTOM_DATA_MESSAGE_ROI_Y1);
			int roi_x2 = obj.getInt(
					NetworkProtocol.CUSTOM_DATA_MESSAGE_ROI_X2);
			int roi_y2 = obj.getInt(
					NetworkProtocol.CUSTOM_DATA_MESSAGE_ROI_Y2);
			String name = obj.getString(
					NetworkProtocol.CUSTOM_DATA_MESSAGE_NAME
			);

			byte[] img = null;
			if (obj.has(NetworkProtocol.CUSTOM_DATA_MESSAGE_IMG)) {
				String img_string = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_IMG);
				img = Base64.decode(img_string, Base64.DEFAULT);
			}

			int[] roi = new int[]{roi_x1, roi_y1, roi_x2, roi_y2};
			Face face = new Face(roi, img, name);
			return face;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Face[] parseFaceSnippets(String response) {
		try {
			JSONObject obj;
			obj = new JSONObject(response);
			int roiFacePairNum = obj.getInt(NetworkProtocol.CUSTOM_DATA_MESSAGE_NUM);
			Face[] faces = new Face[roiFacePairNum];
			for (int idx = 0; idx < roiFacePairNum; idx++) {
				String faceString = obj.getString(String.valueOf(idx));
				Face face = this.parseFace(faceString);
				faces[idx] = face;
			}
//            Log.d(LOG_TAG, "parsed # faces " + faces.length);
			return faces;
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void drawFaceSnippets(Face[] faces, Bitmap curFrame) {
		// if not destroyed
		if (cameraOverlay != null && mPreview != null) {
			for (Face face : faces) {
				face.scale(mPreview.imageSize,
						cameraOverlay.getWidth(), cameraOverlay.getHeight());
			}
			cameraOverlay.drawFaces(faces, mPreview.imageSize, curFrame);
		}
	}

    private void notifyError(String msg){
        DialogInterface.OnClickListener error_listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        terminate();
                        finish();
                    }
                };

        new AlertDialog.Builder(this)
                .setTitle("Error").setMessage(msg)
                .setNegativeButton("close", error_listener).show();
    }

	private Handler returnMsgHandler = new Handler() {
		public void handleMessage(Message msg) {
			if (msg.what == NetworkProtocol.NETWORK_RET_FAILED) {
				Bundle data = msg.getData();
				String message = data.getString("message");
				stopStreaming();
                notifyError(message);
			}

			//handled by resultReceivingThread!!!
			if (msg.what == NetworkProtocol.NETWORK_RET_RESULT) {
				String response = (String) msg.obj;
//                Log.d(LOG_TAG, "received response");
//                Log.d(LOG_TAG, response);

				if (Const.RESPONSE_JSON) {
					try {
						JSONObject obj;
						obj = new JSONObject(response);
						String type = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE);
						if (type.equals(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE_ADD_PERSON)) {
							String name = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_VALUE);
							Log.d(LOG_TAG, "gabriel server added person: " + name);
						}

						if (type.equals(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE_LOAD_STATE)) {
							Log.d(LOG_TAG, "load state finished");
							Toast.makeText(getApplicationContext(), "load state finished!", Toast.LENGTH_SHORT).show();
						}

						if (type.equals(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE_TRAIN)) {
							String value = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_VALUE);
							Face[] faces = parseFaceSnippets(value);
							drawFaceSnippets(faces, null);
							JSONObject cnt_json = new JSONObject(value);
							String cnt = cnt_json.getString("cnt");
//                            Log.d(LOG_TAG, "gabriel server training cnt: " + cnt);
							cnt_view.setText(String.valueOf(cnt));
						}

						if (type.equals(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE_DETECT)) {
							long time = System.currentTimeMillis();
							String value = obj.getString(NetworkProtocol.CUSTOM_DATA_MESSAGE_VALUE);
							Face[] faces = parseFaceSnippets(value);
							swapFaces(faces);
							if (null != videoStreamingThread) {
								drawFaceSnippets(faces, videoStreamingThread.curFrame);
							}
							Log.w(LOG_TAG, "detect image processing time: " +
									(System.currentTimeMillis() - time));
						}

						if (type.equals(NetworkProtocol.CUSTOM_DATA_MESSAGE_TYPE_IMG)) {
							Log.w(LOG_TAG, "received encoded img. Do not support anymore ");
						}

					} catch (JSONException e) {
						e.printStackTrace();
					}
				}

				if (Const.RESPONSE_ROI_FACE_SNIPPET) {
					Face[] faces = parseFaceSnippets(response);
					// if not destroyed
					if (cameraOverlay != null && mPreview != null) {
						cameraOverlay.drawFaces(faces, mPreview.imageSize, curFrame);
					}

				}


				if (Const.RESPONSE_ENCODED_IMG) {
					byte[] img = Base64.decode(response, Base64.DEFAULT);
					if (mDisplay != null) {
						mDisplay.push(img);
					}
				}


//				if (mTTS != null){
//					String ttsMessage = (String) msg.obj;
//
//					// Select a random hello.
//					Log.d(LOG_TAG, "tts string origin: " + ttsMessage);
//					mTTS.setSpeechRate(1f);
//					mTTS.speak(ttsMessage, TextToSpeech.QUEUE_FLUSH, null);
//				}

			}
		}
	};

	private void swapFaces(Face[] faces) {
		Face[] originalFaces = new Face[faces.length];
		System.arraycopy(faces, 0, originalFaces, 0, faces.length);
		for (Face face : faces) {
			if (faceTable.containsKey(face.getName())) {
				String toPerson = faceTable.get(face.getName());
				for (Face face2 : originalFaces) {
					if (face2.getName().equals(toPerson)) {
						face.imageRoi = face2.realRoi;
						break;
					}
				}
			}
		}
		//remove the bitmap from toPerson
		for (Face face : faces) {
			if (faceTable.containsKey(face.getName())) {
				String toPerson = faceTable.get(face.getName());
				for (Face face2 : faces) {
					if (face2.getName().equals(toPerson)) {
						face2.renderBitmap = false;
						break;
					}
				}

			}
		}
	}


	protected int selectedRangeIndex = 0;

	public void selectFrameRate(View view) throws IOException {
		selectedRangeIndex = 0;
		final List<int[]> rangeList = this.mPreview.supportingFPS;
		String[] rangeListString = new String[rangeList.size()];
		for (int i = 0; i < rangeListString.length; i++) {
			int[] targetRange = rangeList.get(i);
			rangeListString[i] = new String(targetRange[0] + " ~" + targetRange[1]);
		}

		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setTitle("FPS Range List");
		ab.setIcon(R.drawable.ic_launcher);
		ab.setSingleChoiceItems(rangeListString, 0, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				selectedRangeIndex = position;
			}
		}).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				if (position >= 0) {
					selectedRangeIndex = position;
				}
				int[] targetRange = rangeList.get(selectedRangeIndex);
				mPreview.changeConfiguration(targetRange, null);
			}
		}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				return;
			}
		});
		ab.show();
	}

	protected int selectedSizeIndex = 0;

	public void selectImageSize(View view) throws IOException {
		selectedSizeIndex = 0;
		final List<Camera.Size> imageSize = this.mPreview.supportingSize;
		String[] sizeListString = new String[imageSize.size()];
		for (int i = 0; i < sizeListString.length; i++) {
			Camera.Size targetRange = imageSize.get(i);
			sizeListString[i] = new String(targetRange.width + " ~" + targetRange.height);
		}

		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setTitle("Image Size List");
		ab.setIcon(R.drawable.ic_launcher);
		ab.setSingleChoiceItems(sizeListString, 0, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				selectedRangeIndex = position;
			}
		}).setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				if (position >= 0) {
					selectedRangeIndex = position;
				}
				Camera.Size targetSize = imageSize.get(selectedRangeIndex);
				mPreview.changeConfiguration(null, targetSize);
			}
		}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int position) {
				return;
			}
		});
		ab.show();
	}

	public void stopStreaming() {
		hasStarted = false;
		if (mPreview != null)
			mPreview.setPreviewCallback(null);
		if (videoStreamingThread != null && videoStreamingThread.isAlive()) {
			videoStreamingThread.stopStreaming();
		}
		if (resultThread != null && resultThread.isAlive()) {
			resultThread.close();
		}
	}

	public void setDefaultPreferences() {
		// setDefaultValues will only be invoked if it has not been invoked
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		sharedPref.edit().putBoolean(SettingsActivity.KEY_PROXY_ENABLED, true);
		sharedPref.edit().putString(SettingsActivity.KEY_PROTOCOL_LIST, "UDP");
		sharedPref.edit().putString(SettingsActivity.KEY_PROXY_IP, "128.2.213.25");
		sharedPref.edit().putInt(SettingsActivity.KEY_PROXY_PORT, 8080);
		sharedPref.edit().commit();

	}

	public void getPreferences() {
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String sProtocol = sharedPref.getString(SettingsActivity.KEY_PROTOCOL_LIST, "UDP");
		String[] sProtocolList = getResources().getStringArray(R.array.protocol_list);
	}

	/*
	 * Recording battery info by sending an intent Current and voltage at the
	 * time Sample every 100ms
	 */
	Intent batteryRecordingService = null;

	private void terminate() {
		Log.d(DEBUG_TAG, "on terminate");

//		if (cameraRecorder != null) {
//			cameraRecorder.close();
//			cameraRecorder = null;
//		}
		if ((resultThread != null) && (resultThread.isAlive())) {
			resultThread.close();
			resultThread = null;
		}
		if ((videoStreamingThread != null) && (videoStreamingThread.isAlive())) {
			videoStreamingThread.stopStreaming();
			videoStreamingThread = null;
		}
		if (tokenController != null) {
			tokenController.close();
			tokenController = null;
		}

		if (mPreview != null) {
			mPreview.setPreviewCallback(null);
			mPreview.close();
			mPreview = null;
		}

		if (mDisplay != null) {
			mDisplay = null;
		}

		if (cameraOverlay != null) {
			cameraOverlay.destroyDrawingCache();
			cameraOverlay = null;
		}
	}
}
