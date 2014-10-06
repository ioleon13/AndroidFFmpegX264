package com.livecamera.encoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;

import com.livecamera.stream.video.VideoParam;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

public class h264encoder {
    private String TAG = "h264encoder";
    private Camera mCamera;
    private VideoParam mVideoParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
    private long mEncoder = 0;
    private byte[] mH264Buff = null;
    private RandomAccessFile mRaf = null;
    
    public void setCamera(Camera c) {
        mCamera = c;
    }
    
    public void setVideoParam(VideoParam p) {
        mVideoParam = p;
    }
    
    public void start() {
        mEncoder = CompressBegin(mVideoParam.width, mVideoParam.height);
        mH264Buff = new byte[mVideoParam.width * mVideoParam.height * 8];
        
        //save file first for testing
        try {
            File file = new File("/sdcard/camera1.h264");
            mRaf = new RandomAccessFile(file, "rw");
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
        
        if (mCamera != null) {
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    int result = CompressBuffer(mEncoder, 0, data, data.length, mH264Buff);
                    
                    try {
                        if (result > 0) {
                            mRaf.write(mH264Buff, 0, result);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, e.toString());
                    }
                }
            });
        }
    }
    
	public void stop() {
		if (mCamera != null) {
            mCamera.setPreviewCallback(null);
        }
	}
	
	public void release() {
	    Log.e(TAG, "release the encoder");
	    CompressEnd(mEncoder);
	    
        if (mRaf != null) {
            try {
                Log.e(TAG, "close the file");
                mRaf.close();
            } catch (Exception e) {
                Log.w(TAG, e.toString());
            }
        }
	}
	
	private byte[] rotateYUV420Degree90(byte[] data, int width, int height) {
	    byte[] yuv = new byte[width*height*3/2];
	    
	    //rotate y
	    int i = 0;
	    for (int x = 0; x < width; x++) {
            for (int y = height-1; y >= 0; y--) {
                yuv[i] = data[y*width + x];
                i++;
            }
        }
	    
	    //rotate the u and v
	    i = width*height*3/2 - 1;
	    for (int x = width-1; x > 0; x=x-2) {
            for (int y = 0; y < height/2; y++) {
                yuv[i] = data[(width*height) + (y*width) + x];
                i--;
                yuv[i] = data[(width*height) + (y*width) + (x-1)];
                i--;
            }
        }
	    return yuv;
	}
	
	private byte[] rotateDataDegree90(byte[] data, int width, int height) {
	    byte[] rotatedData = new byte[data.length];
	    for (int y = 0; y < height; y++) {
	        for (int x = 0; x < width; x++)
	            rotatedData[x * height + height - y - 1] = data[x + y * width];
	    }
	    
	    return rotatedData;
	}
	
	private byte[] rotateDataByBitmap(byte[] data, int width, int height, int rotation) {
	    Size previewSize = mCamera.getParameters().getPreviewSize();
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    byte[] rawImage = null;

	    // Decode image from the retrieved buffer to JPEG
	    YuvImage yuv = new YuvImage(data, ImageFormat.NV21, previewSize.width,
	            previewSize.height, null);
	    yuv.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 80, baos);
	    rawImage = baos.toByteArray();

	    // This is the same image as the preview but in JPEG and not rotated
	    Bitmap bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length);
	    ByteArrayOutputStream rotatedStream = new ByteArrayOutputStream();

	    // Rotate the Bitmap
	    Matrix matrix = new Matrix();
	    matrix.postRotate(rotation);

	    // We rotate the same Bitmap
	    bitmap = Bitmap.createBitmap(bitmap, 0, 0, previewSize.width, previewSize.height,
	            matrix, false);

	    // We dump the rotated Bitmap to the stream 
	    bitmap.compress(CompressFormat.JPEG, 80, rotatedStream);

	    rawImage = rotatedStream.toByteArray();
	    
	    return rawImage;
    }
	
	//native method
    private native long CompressBegin(int width, int height);
    private native int CompressEnd(long encoder);
    private native int CompressBuffer(long encoder, int type, byte[] in,
            int insize, byte[] out);
}
