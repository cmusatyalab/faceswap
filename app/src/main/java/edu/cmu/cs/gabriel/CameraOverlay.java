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

    private Face[] faces;
    private Paint myPaint;

    // image size of transmitted imgs
    // used for scaling
    private Camera.Size imageSize;

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

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CameraOverlay(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas c){
        Log.d("cameraOverlay", "view width: " +this.getWidth());
        super.onDraw(c);
        if (null != faces){
            for (Face face: faces){
                int[] roi = face.getRoi();
                roi = scaleToScreen(roi);
                c.drawRect(roi[0],roi[1],roi[2],roi[3],myPaint);
                Bitmap renderImg = face.getBitmap();
                Bitmap scaledBitmap =Bitmap.createScaledBitmap(renderImg,
                        roi[2] - roi[0] + 1,
                        roi[3] - roi[1] + 1 ,false);
                c.drawBitmap(scaledBitmap, roi[0], roi[1], null);
            }
        }

    }

    /**
     * scale the original image size roi to actual screen size
     * @param roi
     */
    private int[] scaleToScreen(int[] roi){
        if (null == this.imageSize){
            return  roi;
        }
        int[] scaledRoi = new int[roi.length];
        int imageWidth = this.imageSize.width;
        int imageHeight = this.imageSize.height;
        double width_ratio = (double) this.getWidth() / imageWidth;
        double height_ratio = (double) this.getHeight() / imageHeight;

        scaledRoi[0] = (int) (roi[0] * width_ratio);
        scaledRoi[1] = (int) (roi[1] * height_ratio);
        scaledRoi[2] = (int) (roi[2] * width_ratio);
        scaledRoi[3] = (int) (roi[3] * height_ratio);
        return scaledRoi;
    }

    public void drawFaces(Face[] faces, Camera.Size imageSize){
        this.faces = faces;
        if (null == this.imageSize){
            this.imageSize = imageSize;
        }
        this.invalidate();
    }


    public Camera.Size getImageSize() {
        return imageSize;
    }

    public void setImageSize(Camera.Size imageSize) {
        this.imageSize = imageSize;
    }
}
