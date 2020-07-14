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
import org.opencv.core.Rect;
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
    private Mat transmitFrame;

    public interface CVPreviewListener {
        void onCVPreviewAvailable(Mat frame);
    }

    public CVRenderer(CVPreviewListener delegate){
        this.delegate=delegate;
        this.faces = new Face[0];
    }

    public void onCameraViewStarted(int width, int height) {
        displayFrame=new Mat(width, height, CvType.CV_8UC3);
        transmitFrame=new Mat(width, height, CvType.CV_8UC3);
        Log.d(LOG_TAG, "on camera view started width: "+width+" height:"+height);
    }

    public void onCameraViewStopped() {
        displayFrame.release();
        transmitFrame.release();
        Log.d(LOG_TAG, "on camera view stopped: ");
    }

    int ensureRange(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private void boundryCheck(int[] bx, int imgWidth, int imgHeight){
        bx[0] = ensureRange(bx[0], 0, imgWidth-1);
        bx[1] = ensureRange(bx[1], 0, imgHeight-1);
        bx[2] = ensureRange(bx[2], 0, imgWidth-1);
        bx[3] = ensureRange(bx[3], 0, imgHeight-1);
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Imgproc.cvtColor(inputFrame.rgba(), displayFrame, Imgproc.COLOR_BGRA2BGR);
        if (this.delegate!=null){
            //TODO: needed so that bg encoding won't interfere with foreground getting a new image
            displayFrame.copyTo(transmitFrame);
            delegate.onCVPreviewAvailable(transmitFrame);
        }
        synchronized (this.faces){
            for (Face face:faces){
                int[] roi = face.realRoi;
                boundryCheck(roi, displayFrame.width(), displayFrame.height());
                Point leftTop=new Point(roi[0], roi[1]);
                Point rightBottom=new Point(roi[2], roi[3]);
                if (face.isRenderring && face.argbMat!=null){
                    rightBottom=new Point(roi[0]+face.argbMat.width(),
                            roi[1]+face.argbMat.height());
                    Rect pRoi = new Rect(roi[0],roi[1],
                            face.argbMat.width(), face.argbMat.height());
                    Log.d("debug", "pRoi : " + pRoi.toString());
                    Log.d("debug", "display frame width : " + displayFrame.width()
                            + " display frame height: " + displayFrame.height());
                    Mat pRoiMat=displayFrame.submat(pRoi);
                    Log.d("debug", "display frame width : " + displayFrame.width()
                            + " display frame height: " + displayFrame.height());

                    face.argbMat.copyTo(pRoiMat);
                }
                Imgproc.rectangle(displayFrame,
                        leftTop,
                        rightBottom,
                        new Scalar(255, 0, 0));
                Imgproc.putText(displayFrame,
                        face.getName(),
                        new Point(roi[0], roi[1]),
                        0,
                        0.8,
                        new Scalar(255,255,0));

            }
        }
        Log.d(LOG_TAG, "rendered");
        return displayFrame;
//                Mat pRoi=mRgba.submat(10,10+replaceImg.width(),10,10+replaceImg.height());
//                Log.d("debug", "mat width: " + pRoi.width() + " height: " + pRoi.height());
//                Log.d("debug", "img width: " + replaceImg.width() + " height: " + replaceImg.height());
//                replaceImg.copyTo(pRoi);
    }


    public void drawFaces(Face[] faces) {
        for (Face face: faces){
            if (face.img != null){
                MatOfByte byteMat = new MatOfByte();
                byteMat.fromArray(face.displayImg);
                face.argbMat = Imgcodecs.imdecode(byteMat,Imgcodecs.CV_LOAD_IMAGE_COLOR);
            }
        }
        synchronized (this.faces){
            this.faces=faces;
        }

    }
}
