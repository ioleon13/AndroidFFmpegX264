package com.livecamera.stream.video;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.livecamera.stream.MediaStream;

@SuppressLint("NewApi")
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
	
	protected boolean mFlashEnabled = false;
	protected boolean mSurfaceReady = false;
	
	protected VideoParam mRequestedParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
	protected VideoParam mParam = mRequestedParam.clone();
	protected int mMaxFps = 0;
	
	/**
	 * set the camera: facing back or facing front
	 */
	public VideoStream(int camera) {
	    super();
	    setCamera(camera);
	}
	
	/**
	 * set the capturing video camera
	 */
    public void setCamera(int camera) {
	    CameraInfo cameraInfo = new CameraInfo();
	    int nCameras = Camera.getNumberOfCameras();
	    for (int i = 0; i < nCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera) {
                mCameraId = i;
                break;
            }
        }
	}
	
	/**
	 * switch between the front and back facing camera
	 */
	public void switchCamera() throws RuntimeException, IOException {
	    if (Camera.getNumberOfCameras() == 1) {
            throw new IllegalStateException("There was only one camera!");
        }
	    
	    boolean streaming = mStreaming;
	    boolean previewing = mCamera != null && mPreviewRunning;
	    
	    mCameraId = (mCameraId == CameraInfo.CAMERA_FACING_BACK) ?
	            CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK;
	    setCamera(mCameraId);
	    
	    //stoppreview
	    
	    mFlashEnabled = false;
	    
	    if (previewing) {
            //startpreview
        }
	    
	    if (streaming) {
            //start
        }
	}
	
	public int getCamera() {
	    return mCameraId;
	}
	
	/**
	 * start preview
	 */
	public synchronized void startPreview() throws RuntimeException {
	    mPreviewRunning = true;
	    if (!mPreviewStarted) {
            
        }
	}
	
	/**
	 * create camera
	 */
	protected synchronized void createCamera() throws RuntimeException {
	    if (mSurfaceView == null) {
            throw new RuntimeException("invalid surface");
        }
	    
	    if (mSurfaceView.getHolder() == null || !mSurfaceReady) {
            throw new RuntimeException("invalid surface");
        }
	    
	    if (mCamera == null) {
            openCamera();
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                
                @Override
                public void onError(int error, Camera camera) {
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        Log.e(TAG, "Media server died!");
                        mPreviewRunning = false;
                    }
                }
            });
        }
	}
	
	
	/**
	 * open the camera in a looper
	 */
	private void openCamera() throws RuntimeException {
	    final Semaphore lock = new Semaphore(0);
	    final RuntimeException[] exception = new RuntimeException[1];
	    mCameraThread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                Looper.prepare();
                mCameraLooper = Looper.myLooper();
                try {
                    mCamera = Camera.open(mCameraId);
                } catch (RuntimeException e) {
                    exception[0] = e;
                } finally {
                    lock.release();
                    Looper.loop();
                }
            }
        });
	    
	    mCameraThread.start();
	    lock.acquireUninterruptibly();
	    if (exception[0] != null) {
            throw new RuntimeException(exception[0].getMessage());
        }
	}
	
	/**
	 * stop the stream
	 */
	public synchronized void stop() {
	    if (mCamera != null) {
            if (mMode == MODE_MEDIACODEC_API) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }
            super.stop();
        }
	}
}
