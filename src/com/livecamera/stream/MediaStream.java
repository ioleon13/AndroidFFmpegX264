package com.livecamera.stream;

import com.livecamera.encoder.h264encoder;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.util.Log;

public class MediaStream {
	private static final String TAG = "MediaStream";
	
	//encoded method
	public static final byte MODE_FFMPEGX264_API = 0x01;
	public static final byte MODE_MEDIARECORDER_API = 0x02;
	public static final byte MODE_MEDIACODEC_API = 0x03;
	
	//suggested encoded method
	protected static byte mSuggestedMode = MODE_FFMPEGX264_API;
	protected byte mMode;
	
	protected boolean mStreaming = false;
	
	protected h264encoder mH264Encoder;
	protected MediaRecorder mMediaRecorder;
	protected MediaCodec mMediaCodec;
	
	static {
		try {
			System.loadLibrary("ffmpeg-x264");
			mSuggestedMode = MODE_FFMPEGX264_API;
			Log.i(TAG, "The soft encoder x264 was supported");
		} catch (Exception e) {
			try {
				Class.forName("android.media.MediaCodec");
				mSuggestedMode = MODE_MEDIACODEC_API;
				Log.i(TAG, "Using MediaCodec API");
			} catch (ClassNotFoundException e2) {
				mSuggestedMode = MODE_MEDIARECORDER_API;
				Log.i(TAG, "Using MediaRecorder API");
			}
		}
	}
	
	public MediaStream()
	{
		mMode = mSuggestedMode;
	}
	
	public void setEncodingMethod(byte method)
	{
		mMode = method;
	}
	
	@SuppressLint("NewApi")
    public synchronized void stop() {
	    if (mStreaming) {
            try {
                mStreaming = false;
                if (mMode == MODE_MEDIARECORDER_API) {
                    mMediaRecorder.stop();
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                } else if (mMode == MODE_MEDIACODEC_API) {
                    mMediaCodec.stop();
                    mMediaCodec.release();
                    mMediaCodec = null;
                } else {
                    mH264Encoder.stop();
                    mH264Encoder.release();
                    mH264Encoder = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
}
