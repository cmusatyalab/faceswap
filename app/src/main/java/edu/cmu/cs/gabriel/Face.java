package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;

/**
 * Created by junjuew on 1/21/16.
 */
public class Face {
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

    public Bitmap createBitmapFromByteArray() {
        if (null != this.img && null == this.bitmap) {
            this.bitmap = BitmapFactory.decodeByteArray(this.img, 0, this.img.length);
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
