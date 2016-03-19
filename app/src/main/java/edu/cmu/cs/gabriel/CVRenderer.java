package edu.cmu.cs.gabriel;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.jar.Attributes;

/**
 * Created by junjuew on 3/19/16.
 */
public class CVRenderer implements CameraBridgeViewBase.CvCameraViewListener2{
    private static final String LOG_TAG= "cv_renderer";
    private CVPreviewListener delegate=null;
    private volatile Face[] faces;
    private Mat displayFrame;

    public interface CVPreviewListener {
        void onCVPreviewAvailable(Mat frame);
    }

    public CVRenderer(CVPreviewListener delegate){
        this.delegate=delegate;
        this.faces = new Face[0];
    }

    public void onCameraViewStarted(int width, int height) {
        displayFrame=new Mat(height, width, CvType.CV_8UC3);
        Log.d(LOG_TAG, "on camera view started width: "+width+" height:"+height);
    }

    public void onCameraViewStopped() {
        displayFrame.release();
        Log.d(LOG_TAG, "on camera view stopped: ");
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Imgproc.cvtColor(inputFrame.rgba(), displayFrame, Imgproc.COLOR_BGRA2BGR);
        if (this.delegate!=null){
            //TODO: needed so that bg encoding won't interfere with foreground getting a new image
            Mat duplicate = new Mat();
            displayFrame.copyTo(duplicate);
            delegate.onCVPreviewAvailable(duplicate);
        }

        synchronized (this.faces){
            for (Face face: faces){
                int[] roi = face.realRoi;
                Imgproc.rectangle(displayFrame,
                        new Point(roi[0], roi[1]),
                        new Point(roi[2], roi[3]),
                        new Scalar(255, 0, 0));

            }
            for (Face face:faces){
                if (face.isRenderring){
                    int[] roi = face.realRoi;
                    Log.d("debug", "roi : " + roi[0] + ", " + roi[2] + ", "+ roi[1] + ", " + roi[3]);
                    Log.d("debug", " frame width : " + displayFrame.width()
                        + " frame height: " + displayFrame.height());
                    Mat pRoi=displayFrame.submat(roi[1],roi[1] + face.argbMat.width(),
                            roi[0],roi[0]+face.argbMat.height());
                    face.argbMat.copyTo(pRoi);
                }
//                int[] roi = face.realRoi;
            }
        }

        return displayFrame;
//                Mat pRoi=mRgba.submat(10,10+replaceImg.width(),10,10+replaceImg.height());
//                Log.d("debug", "mat width: " + pRoi.width() + " height: " + pRoi.height());
//                Log.d("debug", "img width: " + replaceImg.width() + " height: " + replaceImg.height());
//                replaceImg.copyTo(pRoi);
    }


    public void drawFaces(Face[] faces) {
        for (Face face: faces){
            MatOfByte byteMat = new MatOfByte();
            byteMat.fromArray(face.displayImg);
            face.argbMat = Imgcodecs.imdecode(byteMat,Imgcodecs.CV_LOAD_IMAGE_COLOR);
        }
        synchronized (this.faces){
            this.faces=faces;
        }

    }
}
