package edu.cmu.cs.gabriel;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Created by junjuew on 1/21/16.
 */
public class CameraOverlay extends View {

    private static final String DEBUG_TAG = "cameraOverlay";
    private Face[] faces;
    private Paint myPaint;
    private Paint textPaint;

    // image size of transmitted imgs
    // used for scaling
    private Camera.Size imageSize;
    private long timeStamp;
    private Bitmap frame;

    public CameraOverlay(Context context) {
        super(context);
        init();
    }

    public CameraOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        myPaint = new Paint();
        myPaint.setColor(Color.RED);
        myPaint.setStrokeWidth(10);
        myPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(50);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CameraOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas c){
//        Log.d(DEBUG_TAG, "onDraw called! " + (System.currentTimeMillis()-timeStamp) +"ms");
        super.onDraw(c);
        if (null != faces){
            for (Face face: faces){
                int[] roi = face.screenSizeRoi;
                c.drawRect(roi[0],roi[1],roi[2],roi[3],myPaint);
                if (null == face.screenBitmap){
//                    Bitmap renderImg = face.createBitmapFromByteArray();
//                    if (null != renderImg){
//                        c.drawBitmap(renderImg, roi[0], roi[1], null);
//                        renderImg.recycle();
//                    }
                } else {
                    if (face.renderBitmap){
                        c.drawBitmap(face.screenBitmap, face.screenSizeRoi[0], face.screenSizeRoi[1],
                                null);
                    }
                }
                String name = face.getName();
                if (null != name) {
                    c.drawText(name, roi[0]-4, roi[1]-4, textPaint);
                }
            }
        }
        long time = System.currentTimeMillis();
        long elapse = time - timeStamp;
//        Log.d(DEBUG_TAG, "cameraOverlay updated! " + elapse + " ms");
    }


    public void drawFaces(Face[] faces, Camera.Size imageSize, Bitmap curFrame){
        if (null!=this.faces){
            for (Face face:this.faces){
                if (null!=face.bitmap){
                    face.bitmap.recycle();
                }
                if (null != face.screenBitmap){
                    face.screenBitmap.recycle();
                }
            }
        }

        this.faces = faces;
        if (null == this.imageSize){
            this.imageSize = imageSize;
        }
        this.frame=curFrame;
        if (Const.FACE_DEMO_BOUNDING_BOX_ONLY){

        } else if (Const.FACE_DEMO_DISPLAY_RECEIVED_FACES){
            //set screen bitmap to be created from face.img byte array has been done
            // by scale operation
            for (Face face: faces){
                face.screenBitmap=face.bitmap;
            }
            Log.d(DEBUG_TAG, "display bitmap is the same as bitmap");
        } else {
            //get faces by
            if (this.frame!=null){
                for (Face face: faces){
                    int[] roi = face.imageRoi;
                    face.bitmap = Bitmap.createBitmap(this.frame,
                            roi[0],
                            roi[1],
                            Math.min((roi[2]-roi[0]+1), this.frame.getWidth() - roi[0]),
                            Math.min((roi[3]-roi[1]+1),this.frame.getHeight() - roi[1]));
                    face.screenBitmap=Bitmap.createScaledBitmap(
                            face.bitmap,
                            (face.screenSizeRoi[2] - face.screenSizeRoi[0] + 1),
                            (face.screenSizeRoi[3] - face.screenSizeRoi[1] + 1),
                            false
                    );
                }
            }
        }
//        Log.d(DEBUG_TAG, "end creating face bitmap");
        this.invalidate();
        timeStamp = System.currentTimeMillis();
//        Log.d("cameraOverlay", "cameraOverlay refresh requested");
    }


    public void setImageSize(Camera.Size imageSize) {
        this.imageSize = imageSize;
    }
}
