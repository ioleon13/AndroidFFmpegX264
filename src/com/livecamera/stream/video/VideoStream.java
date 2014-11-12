package com.livecamera.stream.video;

import java.io.IOException;
import java.util.concurrent.Semaphore;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.livecamera.encoder.MediaCodecEncoder;
import com.livecamera.encoder.h264encoder;
import com.livecamera.surface.GLSurfaceView;
import com.livecamera.stream.MediaStream;

@SuppressLint("NewApi")
public class VideoStream extends MediaStream {
	protected final static String TAG = "VideoStream";
	
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected GLSurfaceView mSurfaceView = null;
	protected int mVideoEncoder = 0;
	protected int mCameraId = 0;
	protected int mRequestedOrientation = 0;
	protected int mOrientation = 0;
	protected Camera mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;
	
	protected boolean mPreviewRunning = false;
	protected boolean mPreviewStarted = false;
	
	protected boolean mFlashEnabled = false;
	protected boolean mSurfaceReady = false;
	
	protected boolean mMediaCodecFromSurface = true;
	
	protected VideoParam mRequestedParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
	protected VideoParam mParam = mRequestedParam.clone();
	protected int mMaxFps = 0;
	
	protected int mImageFormat = ImageFormat.NV21;
	
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
	 * @throws Throwable 
	 */
	public void switchCamera() throws Throwable {
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
	public synchronized void setSurfaceView(GLSurfaceView view) {
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
                    stop();
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
                    startPreview();
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
	    if (mPreviewRunning) {
	        mCamera.stopPreview();
	        mCamera.release();
	        mCamera = null;
            mCameraLooper.quit();
	        mPreviewRunning = false;
        }
	    
	    //stop();
	}
	
	
	/**
	 * start the stream
	 * @throws Throwable 
	 */
	public synchronized void start() throws Throwable {
	    if (!mPreviewStarted) {
            mPreviewRunning = false;
        }
	    
	    //start encode
	    if (mMode == MODE_FFMPEGX264_API) {
            encodeWithX264();
        } else if (mMode == MODE_MEDIACODEC_API) {
            encodeWithMediaCodec();
        }
	    
	    Log.i(TAG, "encode params: FPS: " + mParam.framerate +
	            " Width: " + mParam.width + " Height: " + mParam.height);
	}
	
	/**
     * stop the stream
     */
    public synchronized void stop() {
        if (mCamera != null) {
            /*if (mMode == MODE_MEDIACODEC_API) {
                mCamera.setPreviewCallbackWithBuffer(null);
            }*/
            super.stop();
            
            /*if (!mPreviewRunning) {
                destroyCamera();
            } else {
                try {
                    startPreview();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }*/
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
                    if (mMediaCodecFromSurface) {
                        mSurfaceView.startGLThread();
                        mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
                    } else {
                        mCamera.setPreviewDisplay(mSurfaceView.getHolder());
                    }
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
        
        Log.e(TAG, "preview size: " + mParam.width + "x" + mParam.height);
        
        try {
            mCamera.setParameters(p);
            mCamera.setDisplayOrientation(mOrientation);
            Log.e(TAG, "start preview");
            mCamera.startPreview();
            mPreviewStarted = true;
        } catch (RuntimeException e) {
            destroyCamera();
            throw e;
        }
    }
	
	
	/**
	 * encode with ffmpeg-x264
	 */
	protected void encodeWithX264() throws RuntimeException {
        Log.d(TAG, "Video encoded with ffmpeg x264");
        
        //reopen if need
        destroyCamera();
        createCamera();
        
        Log.d(TAG, mPreviewRunning ? "camera was previewing" : "camera was not previewing");
        if(!mPreviewRunning) {
            startPreview();
        }
        
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
	 * @throws Throwable 
	 */
	protected void encodeWithMediaCodec() throws Throwable {
	    Log.d(TAG, "Video encoded with MediaCodec API");
	    
	    //reopen if need
        destroyCamera();
        createCamera();
        
        Log.d(TAG, mPreviewRunning ? "camera was previewing" : "camera was not previewing");
        if(!mPreviewRunning) {
            startPreview();
        }
        
        try {
            mMediaCodecEncoder = new MediaCodecEncoder();
            mMediaCodecEncoder.setSurfaceView(mSurfaceView);
            mMediaCodecEncoder.setEncodeFromSurface(mMediaCodecFromSurface);
            mMediaCodecEncoder.setVideoParam(mParam);
            mMediaCodecEncoder.setCamera(mCamera);
            mMediaCodecEncoder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	
	/**
	 * encode with mediarecorder
	 */
}
