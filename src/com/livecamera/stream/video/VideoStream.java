package com.livecamera.stream.video;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.livecamera.encoder.h264encoder;
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
	
	protected int mImageFormat;
	
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
	    
	    stopPreview();
	    
	    mFlashEnabled = false;
	    
	    if (previewing) {
            startPreview();
        }
	    
	    if (streaming) {
            start();
        }
	}
	
	public int getCamera() {
	    return mCameraId;
	}
	
	
	/**
	 * set surface view
	 */
	public synchronized void setSurfaceView(SurfaceView view) {
	    mSurfaceView = view;
	    
	    if (mSurfaceHolderCallback != null && mSurfaceView != null
	            && mSurfaceView.getHolder() != null) {
	        mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
        }
	    
	    if (mSurfaceView.getHolder() != null) {
            mSurfaceHolderCallback = new SurfaceHolder.Callback() {
                
                @Override
                public void surfaceDestroyed(SurfaceHolder arg0) {
                    mSurfaceReady = false;
                    stopPreview();
                    Log.d(TAG, "Surface destroyed!");
                }
                
                @Override
                public void surfaceCreated(SurfaceHolder arg0) {
                    mSurfaceReady = true;
                }
                
                @Override
                public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
                    Log.d(TAG, "surface changed!");
                }
            };
            
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
            mSurfaceReady = true;
        }
	}
	
	
	public void setPreviewOrientation(int orientation) {
	    mOrientation = orientation;
	}
	
	
	public void setVideoParam(VideoParam videoParam) {
	    mParam = videoParam;
	}
	
	public VideoParam getVideoParam() {
	    return mParam;
	}
	
	/**
	 * start preview
	 */
	public synchronized void startPreview() throws RuntimeException {
	    mPreviewRunning = true;
	    if (!mPreviewStarted) {
            createCamera();
            updateCamera();
            
            try {
                mCamera.startPreview();
                mPreviewStarted = true;
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
        }
	}
	
	/**
	 * stop preview
	 */
	public synchronized void stopPreview() {
	    mPreviewRunning = false;
	    stop();
	}
	
	
	/**
	 * start the stream
	 */
	public synchronized void start() throws IllegalStateException, IOException {
	    if (!mPreviewStarted) {
            mPreviewRunning = false;
        }
	    
	    //start encode
	    if (mMode == MODE_FFMPEGX264_API) {
            encodecWithX264();
        }
	    
	    Log.i(TAG, "encode params: FPS: " + mParam.framerate +
	            " Width: " + mParam.width + " Height: " + mParam.height);
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
            
            if (!mPreviewRunning) {
                destroyCamera();
            } else {
                try {
                    startPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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
                        stop();
                    } else {
                        Log.e(TAG, "unknown error: " + error);
                    }
                }
            });
            
            //set camera parameters
            try {
                Parameters p = mCamera.getParameters();
                p.setRecordingHint(true);
                mCamera.setParameters(p);
                mCamera.setDisplayOrientation(mOrientation);
                
                try {
                    mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            } catch (RuntimeException e) {
                destroyCamera();
                throw e;
            }
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
	 * destroy the camera
	 */
	protected synchronized void destroyCamera() {
	    if (mCamera != null) {
	        if (mStreaming) {
                super.stop();
                mCamera.stopPreview();
                try {
                    mCamera.release();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage() != null ? e.getMessage() : "unknown error");
                }
                
                mCamera = null;
                mCameraLooper.quit();
                mPreviewStarted = false;
            }
            
        }
	}
	
	
	/**
	 * update the camera parameters
	 */
	protected synchronized void updateCamera() throws RuntimeException {
        if (mPreviewStarted) {
            mPreviewStarted = false;
            mCamera.stopPreview();
        }
        
        Parameters p = mCamera.getParameters();
        mParam = VideoParam.validateSupportedResolution(p, mParam);
        int[] max = VideoParam.getMaximumSupportedFramerate(p);
        p.setPreviewFormat(mImageFormat);
        p.setPreviewSize(mParam.width, mParam.height);
        p.setPreviewFpsRange(max[0], max[1]);
        
        try {
            mCamera.setParameters(p);
            mCamera.setDisplayOrientation(mOrientation);
            //mCamera.startPreview();
            //mPreviewStarted = true;
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }
    }
	
	
	/**
	 * encode with ffmpeg-x264
	 */
	protected void encodecWithX264() throws RuntimeException {
        Log.d(TAG, "Video encoded with ffmpeg x264");
        
        //reopen if need
        destroyCamera();
        createCamera();
        
        try {
            mH264Encoder = new h264encoder();
            mH264Encoder.setVideoParam(mParam);
            mH264Encoder.setCamera(mCamera);
            mH264Encoder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        mStreaming = true;
    }
	
	/**
	 * encode with mediacodec
	 */
	
	/**
	 * encode with mediarecorder
	 */
}
