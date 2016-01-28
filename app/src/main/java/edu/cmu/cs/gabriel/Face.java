package edu.cmu.cs.gabriel;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by junjuew on 1/21/16.
 */
public class Face {
    private int[] roi;
    private byte[] img;
    private Bitmap bitmap;

    public Face(int[] roi, byte[] img) {
        this.roi = roi;
        this.img = img;
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
}
