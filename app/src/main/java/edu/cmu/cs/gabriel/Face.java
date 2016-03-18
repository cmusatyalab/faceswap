package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.util.Log;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Created by junjuew on 1/21/16.
 */
public class Face {
    private static final String TAG = "Face";
    private int[] roi;
    public int[] screenSizeRoi;
    //image roi for display. is the result of swap
    public int[] imageRoi;
    //original detected roi. before swap
    public int[] realRoi;
    private byte[] img;
    public Bitmap bitmap;
    public Bitmap screenBitmap;
    private String name;
    public boolean renderBitmap=true;

    public Face(int[] roi, byte[] img) {
        this.roi = roi;
        this.imageRoi=new int[roi.length];
        System.arraycopy(this.roi, 0, this.imageRoi, 0, this.roi.length);
        this.img = img;
    }

    public Face(int[] roi, byte[] img, String name) {
        this(roi, img);
        this.realRoi=new int[roi.length];
        System.arraycopy(this.roi, 0, this.realRoi, 0, this.roi.length);
        this.name=name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public int[] getRoi() {
        return roi;
    }

    public void setRoi(int[] roi) {
        this.roi = roi;
    }

    public byte[] getImg() {
        return img;
    }

    public void setImg(byte[] img) {
        this.img = img;
    }

    public int[] byteArray2IntArray(byte[] byteArray){
        ByteBuffer bb=
                ByteBuffer.wrap(byteArray)
                        .order(ByteOrder.BIG_ENDIAN);
        bb.rewind();
        LongBuffer lb = bb.asLongBuffer();
        lb.rewind();
        long[] larray = new long[lb.remaining()];
        lb.get(larray);
        int[] iarray=new int[larray.length/3*4];
        for (int idx=0;idx<larray.length;idx++){
            iarray[idx] = (int)larray[idx];
        }
        return iarray;
    }

    public int[] rgbByteArray2ARGBIntArray(byte[] byteArray){
        int[] argbArray=new int[byteArray.length/3*4];
        int argbIdx=0;
        int rgbIdx=0;
        //assuming a is the last byte
        while (argbIdx < argbArray.length){
            if (argbIdx % 3 ==0){
                argbArray[argbIdx] = 255;
            } else {
                argbArray[argbIdx] = (int)byteArray[rgbIdx];
                rgbIdx++;
            }
            argbIdx++;
        }
        return argbArray;
    }

    public Bitmap createBitmapFromByteArray() {
        if (null != this.img && null == this.bitmap) {
//            this.bitmap = BitmapFactory.decodeByteArray(this.img, 0, this.img.length);

            // case dealing with raw pixels
            int width = this.roi[2] - this.roi[0] + 1;
            int height = this.roi[3] - this.roi[1] + 1;
            //failed rgb attempt
//            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
//            ByteBuffer bb=
//                    ByteBuffer.wrap(this.img)
//                            .order(ByteOrder.BIG_ENDIAN);
//            bb.rewind();
//            Log.d(TAG, "bitmap width: " + width + " height: " + height +
//                    " byte buffer size:" + this.img.length);
//            bitmap.copyPixelsFromBuffer(bb);

            //failed argb attempt
//            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//            int[] argbArray=this.rgbByteArray2ARGBIntArray(this.img);
//            bitmap.setPixels(argbArray, 0, width, 0, 0, width, height);

            Log.d(TAG, "created bitmap from byte array");
            //TODO: not working well... use jpeg. jpeg seems to be even a bit faster!!!
            this.bitmap = BitmapFactory.decodeByteArray(this.img, 0, this.img.length);
//            int[] pixels = byteArray2IntArray(this.img);
//            Log.d(TAG, "width: " + width + " height: " + height + " int array size: " + pixels.length +
//                    " byte array size:" + this.img.length);
//            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        }
        return this.bitmap;
    }

    /**
     * scale the original image size roi to actual screen size
     */
    public void scale(Camera.Size imageSize, int viewWidth, int viewHeight) {
        if (null != imageSize && null ==screenSizeRoi){
            int[] scaledRoi = new int[roi.length];
            int imageWidth = imageSize.width;
            int imageHeight = imageSize.height;
            double width_ratio = (double) viewWidth / imageWidth;
            double height_ratio = (double) viewHeight / imageHeight;

            scaledRoi[0] = (int) (roi[0] * width_ratio);
            scaledRoi[1] = (int) (roi[1] * height_ratio);
            scaledRoi[2] = (int) (roi[2] * width_ratio);
            scaledRoi[3] = (int) (roi[3] * height_ratio);

            this.screenSizeRoi=scaledRoi;
            if (null != img){
                Bitmap renderImg = this.createBitmapFromByteArray();
                Bitmap scaledBitmap =Bitmap.createScaledBitmap(renderImg,
                        scaledRoi[2] - scaledRoi[0] + 1,
                        scaledRoi[3] - scaledRoi[1] + 1 ,false);
                this.bitmap = scaledBitmap;
            }
        }

    }
}
