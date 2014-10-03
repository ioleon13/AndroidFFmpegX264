package com.livecamera.encoder;

import java.io.File;
import java.io.RandomAccessFile;

import com.livecamera.stream.video.VideoParam;

import android.hardware.Camera;
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
            File file = new File("/sdcard/camera.h264");
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
		
	}
	
	public void release() {
	    CompressEnd(mEncoder);
        if (mRaf != null) {
            try {
                mRaf.close();
            } catch (Exception e) {
                Log.w(TAG, e.toString());
            }
        }
	}
	
	//native method
    private native long CompressBegin(int width, int height);
    private native int CompressEnd(long encoder);
    private native int CompressBuffer(long encoder, int type, byte[] in,
            int insize, byte[] out);
}
