package edu.cmu.cs.gabriel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.jar.Attributes;

/**
 * Created by Jay on 12/30/2015.
 */

public class DisplaySurface extends SurfaceView implements SurfaceHolder.Callback {
    public static final String LOG_TAG = "junjuew";
    private DisplayThread drawingThread;

    public DisplaySurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
//        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
//        canvas.drawColor(Color.BLACK);
//        canvas.drawBitmap(icon, 10, 10, new Paint());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(LOG_TAG, "display surface created!");
        this.drawingThread=new DisplayThread(getHolder(),this);
        this.drawingThread.setRunning(true);
        this.drawingThread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("display suface", "surface destroyed");
        this.drawingThread.setRunning(false);
        this.drawingThread.interrupt();
//        this.drawingThread=null;
        Log.d("display surface", "drawing thread killed");
    }

    public void push(byte[] frame){
        if (this.drawingThread!=null && this.drawingThread.isAlive()){
            this.drawingThread.push(frame);
        }
    }

    public void drawRect(){
        this.drawingThread.drawRect();
    }
}
