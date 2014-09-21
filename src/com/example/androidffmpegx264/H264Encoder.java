package com.example.androidffmpegx264;

import java.io.File;
import java.io.RandomAccessFile;

import android.hardware.Camera;
import android.util.Log;

public class H264Encoder implements Camera.PreviewCallback {
    private String TAG="H264Encoder";
    private long encoder = 0;
    private RandomAccessFile raf = null;
    private byte[] h264Buff = null;
    
    static {
        System.loadLibrary("ffmpeg-x264");
    }

    private H264Encoder() {};
    
    public H264Encoder(int width, int height) {
        encoder = CompressBegin(width, height);
        h264Buff = new byte[width*height*8];
        
        try {
            File file = new File("/sdcard/camera.h264");
            raf = new RandomAccessFile(file, "rw");
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
    }
    
    //native method
    private native long CompressBegin(int width, int height);
    private native int CompressEnd(long encoder);
    private native int CompressBuffer(long encoder, int type, byte[] in,
            int insize, byte[] out);

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        int result = CompressBuffer(encoder, -1, data, data.length, h264Buff);
        
        try {
            if (result > 0) {
                raf.write(h264Buff, 0, result);
            }
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        CompressEnd(encoder);
        if (raf != null) {
            try {
                raf.close();
            } catch (Exception e) {
                Log.w(TAG, e.toString());
            }
        }
        super.finalize();
    }

}
