package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * Created by Jay on 12/30/2015.
 */
public class DisplayThread extends Thread {
    public static final String LOG_TAG = "junjuew";
    private boolean isRunning;
    private SurfaceHolder holder;
    private DisplaySurface displaySurface;
    private Object frameLock= new Object();
    private Bitmap frameBuffer = null;

    public DisplayThread(SurfaceHolder holder, DisplaySurface displaySurface){
        this.isRunning=false;
        this.holder=holder;
        this.displaySurface=displaySurface;
    }

    public void run(){
        while (this.isRunning){
            Bitmap renderImg=null;
            synchronized(frameLock){
                while (this.frameBuffer == null){
                    try {
                        frameLock.wait();
                    } catch (InterruptedException e) {}
                }
                renderImg = this.frameBuffer;
                this.frameBuffer = null;
            }

            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas(null);
                synchronized (holder) {
                    canvas.drawBitmap(renderImg, 0, 0, null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }

    public void setRunning(boolean running){
        this.isRunning=running;
    }


    public void push(byte[] frame){
        synchronized (frameLock) {
            this.frameBuffer= BitmapFactory.decodeByteArray(frame, 0, frame.length);
            //resize the bitmap so that it is the same as screen size
            this.frameBuffer=Bitmap.createScaledBitmap(this.frameBuffer,
                    this.holder.getSurfaceFrame().width(),
                    this.holder.getSurfaceFrame().height(),false);
//            Log.d(LOG_TAG, "image size: " + frameBuffer.getWidth() + "x" + frameBuffer.getHeight());
            frameLock.notify();
        }
    }


}
