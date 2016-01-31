package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.util.Size;

/**
 * Created by junjuew on 1/21/16.
 */
public class Face {
    private int[] roi;
    private byte[] img;
    private Bitmap bitmap;
    private String name;

    public Face(int[] roi, byte[] img) {
        this.roi = roi;
        this.img = img;
    }

    public Face(int[] roi, byte[] img, String name) {
        this(roi, img);
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

    public Bitmap getBitmap() {
        if (null == this.bitmap) {
            this.bitmap = BitmapFactory.decodeByteArray(this.img, 0, this.img.length);
        }
        return this.bitmap;
    }

    /**
     * scale the original image size roi to actual screen size
     */
    public void scale(Camera.Size imageSize, int viewWidth, int viewHeight) {
        if (null != imageSize){
            int[] scaledRoi = new int[roi.length];
            int imageWidth = imageSize.width;
            int imageHeight = imageSize.height;
            double width_ratio = (double) viewWidth / imageWidth;
            double height_ratio = (double) viewHeight / imageHeight;

            scaledRoi[0] = (int) (roi[0] * width_ratio);
            scaledRoi[1] = (int) (roi[1] * height_ratio);
            scaledRoi[2] = (int) (roi[2] * width_ratio);
            scaledRoi[3] = (int) (roi[3] * height_ratio);

            this.roi = scaledRoi;
            Bitmap renderImg = this.getBitmap();
            Bitmap scaledBitmap =Bitmap.createScaledBitmap(renderImg,
                    roi[2] - roi[0] + 1,
                    roi[3] - roi[1] + 1 ,false);
            this.bitmap = scaledBitmap;
        }

    }
}
