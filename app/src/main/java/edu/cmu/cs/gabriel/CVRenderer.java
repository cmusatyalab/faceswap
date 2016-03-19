package edu.cmu.cs.gabriel;

import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;

/**
 * Created by junjuew on 3/19/16.
 */
public class CVRenderer implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final String LOG_TAG= "cv_renderer";
    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.d(LOG_TAG, "on camera view started width: "+width+" height:"+height);
    }

    @Override
    public void onCameraViewStopped() {
        Log.d(LOG_TAG, "on camera view stopped: ");
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
//                Mat pRoi=mRgba.submat(10,10+replaceImg.width(),10,10+replaceImg.height());
//                Log.d("debug", "mat width: " + pRoi.width() + " height: " + pRoi.height());
//                Log.d("debug", "img width: " + replaceImg.width() + " height: " + replaceImg.height());
//                replaceImg.copyTo(pRoi);
    }
}
