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
import android.view.SurfaceView;
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
        textPaint.setTextSize(30);
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
                int[] roi = face.getRoi();
                c.drawRect(roi[0],roi[1],roi[2],roi[3],myPaint);
                Bitmap renderImg = face.getBitmap();
                if (null != renderImg){
                    c.drawBitmap(renderImg, roi[0], roi[1], null);
                    renderImg.recycle();
                }
                String name = face.getName();
                if (null != name) {
                    c.drawText(name, roi[0]-4, roi[1]-4, textPaint);
                }
            }
        }
        long time = System.currentTimeMillis();
        long elapse = time - timeStamp;
        Log.d(DEBUG_TAG, "cameraOverlay updated! " + elapse + " ms");
    }


    public void drawFaces(Face[] faces, Camera.Size imageSize){
        this.faces = faces;
        if (null == this.imageSize){
            this.imageSize = imageSize;
        }
        this.invalidate();
        timeStamp = System.currentTimeMillis();
//        Log.d("cameraOverlay", "cameraOverlay refresh requested");
    }


    public void setImageSize(Camera.Size imageSize) {
        this.imageSize = imageSize;
    }
}
