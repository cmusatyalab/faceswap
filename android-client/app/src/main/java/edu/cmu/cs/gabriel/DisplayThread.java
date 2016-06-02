package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * Created by Jay on 12/30/2015.
 */
public class DisplayThread extends HandlerThread {
    public static final String LOG_TAG = "junjuew";
    private boolean isRunning;
    private SurfaceHolder holder;
    private DisplaySurface displaySurface;
    private Object frameLock= new Object();
    private Bitmap frameBuffer = null;

    public Handler getHandler() {
        return mHandler;
    }

    private Handler mHandler;

    public DisplayThread(SurfaceHolder holder, DisplaySurface displaySurface){
        super("displayThread");
        this.isRunning=false;
        this.holder=holder;
        this.displaySurface=displaySurface;

    }
    @Override
    protected void onLooperPrepared(){
        this.mHandler = new Handler(getLooper());
    }


    public void run(){
        while (this.isRunning){
            Log.d(LOG_TAG, "running");
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


    public void drawRect() {
        this.mHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "drawRect!!!!");
                        Canvas canvas = null;
                        try {
                            canvas = holder.lockCanvas(null);
                            synchronized (holder) {
                                canvas.drawRect(100, 100, 200, 200, new Paint(Color.RED));
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
        );
    }
}
