package com.livecamera.stream.video;

import android.hardware.Camera;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.livecamera.stream.MediaStream;

public class VideoStream extends MediaStream {
	protected final static String TAG = "VideoStream";
	
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected SurfaceView mSurfaceView = null;
	protected int mVideoEncoder = 0;
	protected int mCameraId = 0;
	protected int mRequestedOrientation = 0;
	protected int mOrientation = 0;
	protected Camera mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;
	
	protected boolean mPreviewRunning = true;
	protected boolean mPreviewStarted = false;
	
	protected VideoParam mRequestedParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
	protected VideoParam mParam = mRequestedParam.clone();
	protected int mMaxFps = 0;
}
