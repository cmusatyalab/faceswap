package edu.cmu.cs.gabriel;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
//import android.widget.Toast;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback{
    private static final String LOG_TAG = "CameraPreview";
	/*
	 * Galaxy Nexus
	 * 320x240	: 2 Mbps, 24 FPS
	 * 640x480	: 3 Mbps, 10 FPS
	 * 800x480	: 2.5Mbps, 7.5 FPS 
	 */

	public SurfaceHolder mHolder;
	public Camera mCamera = null;
	public List<int[]> supportingFPS = null;
	public List<Camera.Size> supportingSize = null;
	public Size imageSize;

    private byte [] rgbbuffer = new byte[256 * 256];
    private int [] rgbints = new int[256 * 256];

    private boolean isPreviewRunning = false;


	public void close() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
        this.isPreviewRunning=false;
	}

	/** Check if this device has a camera */
	private boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

    /** A safe way to get an instance of the Camera object. */
    public Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
			Log.e(LOG_TAG,e.toString());
            Log.e(LOG_TAG, "failed to open camera");
            System.exit(1);
        }
        return c; // returns null if camera is unavailable
    }

	public CameraPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		Log.d("krha_debug", "context : " + context);

			// Launching Camera App using voice command need to wait.
			// See more at https://code.google.com/p/google-glass-api/issues/list
//			try {
//				Thread.sleep(1000);
//			} catch (InterruptedException e) {}
//
		if (checkCameraHardware(context)){
			mCamera = getCameraInstance();
		} else {
                //TODO: exit with warning "no camera hardware"
			Log.e(LOG_TAG, "no camera hardware available");
//			Toast.makeText(getContext(), "no camera hardware available",
//					Toast.LENGTH_LONG).show();
		}

		mHolder = getHolder();
		mHolder.addCallback(this);
		//normal is needed for locking canvas
		// http://stackoverflow.com/questions/6478375/how-can-i-manipulate-the-camera-preview
//		mHolder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        //set enable cache to cache the view bitmap
        this.setDrawingCacheEnabled(true);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// TODO Auto-generated method stub
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	public void changeConfiguration(int[] range, Size imageSize) {
		Camera.Parameters parameters = mCamera.getParameters();
		if (range != null){
			Log.d("krha", "frame rate configuration : " + range[0] + "," + range[1]);
			parameters.setPreviewFpsRange(range[0], range[1]);			
		}
		if (imageSize != null){
			Log.d("krha", "image size configuration : " + imageSize.width + "," + imageSize.height);
			parameters.setPreviewSize(imageSize.width, imageSize.height);
			parameters.setPictureFormat(ImageFormat.JPEG);			
		}

		this.imageSize=imageSize;
		mCamera.setParameters(parameters);
	}

    /**
     * start camera preview automatically here
     * @param holder
     */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
        Log.d(LOG_TAG, "surface created!");
		if (mCamera == null) {
//			mCamera = Camera.open();
            Log.e(LOG_TAG, "surface view created but camera has not opened ");
		} else {
			try {
				mCamera.setPreviewDisplay(holder);

				// set fps to capture
				Camera.Parameters parameters = mCamera.getParameters();
				List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();
				if(this.supportingFPS == null)
					this.supportingFPS = supportedFps;
				int index = 0, fpsDiff = Integer.MAX_VALUE;
				for (int i = 0; i < supportedFps.size(); i++){
					int[] frameRate = supportedFps.get(i);
					int diff = Math.abs(Const.MIN_FPS*1000 - frameRate[0]);
					if (diff < fpsDiff){
						fpsDiff = diff;
						index = i;
					}
				}
				int[] targetRange = supportedFps.get(index);
				
				// set resolusion
				List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
				if(this.supportingSize == null)
					this.supportingSize = supportedSizes;
				index = 0;
				int sizeDiff = Integer.MAX_VALUE;
				for (int i = 0; i < supportedSizes.size(); i++){
					Camera.Size size = supportedSizes.get(i);
					int diff = Math.abs(size.width - Const.IMAGE_WIDTH);
					if (diff < sizeDiff){
						sizeDiff = diff;
						index = i;
					}
				}
				Camera.Size target_size = supportedSizes.get(index);

				changeConfiguration(targetRange, target_size);
				mCamera.startPreview();

                this.isPreviewRunning=true;

			} catch (IOException exception) {
				Log.e("Error", "exception:surfaceCreated Camera Open ");
				this.close();
			}
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
        this.close();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        //TODO: move fps and size settings here ?
		/*
		 * Camera.Parameters parameters = mCamera.getParameters();
		 * parameters.setPreviewSize(w, h); mCamera.setParameters(parameters);
		 * mCamera.startPreview();
		 */
	}

	public void setPreviewCallback(PreviewCallback previewCallback) {
		if (this.mCamera != null){
			mCamera.setPreviewCallback(previewCallback);
		}
	}

	public Camera getCamera() {
		return mCamera;
	}

	public void drawOnPreview(){
		Log.d(LOG_TAG, "try to draw on camera preview");
		Canvas c = null;

		if(mHolder == null){
			Log.e(LOG_TAG, "cameraPreview mholder is null");
			return;
		}

		try {
			synchronized (mHolder) {
				c = mHolder.lockCanvas();
				if (c==null){
					Log.e(LOG_TAG, "failed to get surface view canvas");
					return;
				}

				// Do your drawing here
				// So this data value you're getting back is formatted in YUV format and you can't do much
				// with it until you convert it to rgb
				c.drawRect(100, 100, 200, 200, new Paint());
				Log.d("SOMETHING", "Got Bitmap");
				// do this in a finally so that if an exception is thrown
				// during the above, we don't leave the Surface in an
				// inconsistent state
				if (c != null) {
					mHolder.unlockCanvasAndPost(c);
				}
			}
		} finally {
		}
	}


    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(LOG_TAG, "Got a camera frame");
        Canvas c = null;

        if (!isPreviewRunning){
            return;
        }

        if(mHolder == null){
            return;
        }

        try {
            synchronized (mHolder) {
//                c = mHolder.lockCanvas(null);
                c = mHolder.lockCanvas();
                if (c==null){
                    Log.e(LOG_TAG, "failed to get surface view canvas");
                    return;
                }

                // Do your drawing here
                // So this data value you're getting back is formatted in YUV format and you can't do much
                // with it until you convert it to rgb
                int bwCounter=0;
                int yuvsCounter=0;
                for (int y=0;y<160;y++) {
                    System.arraycopy(data, yuvsCounter, rgbbuffer, bwCounter, 240);
                    yuvsCounter=yuvsCounter+240;
                    bwCounter=bwCounter+256;
                }
                for(int i = 0; i < rgbints.length; i++){
                    if (i % 10 !=0){
                        rgbints[i] =0;
                    } else {
                        rgbints[i] = (int)rgbbuffer[i];
                    }

                }
                c.drawBitmap(rgbints, 0, 256, 0, 0, 256, 256, false, new Paint());
                Log.d("SOMETHING", "Got Bitmap");
				// do this in a finally so that if an exception is thrown
				// during the above, we don't leave the Surface in an
				// inconsistent state
				if (c != null) {
					mHolder.unlockCanvasAndPost(c);
				}
            }
        } finally {
        }
    }
}
