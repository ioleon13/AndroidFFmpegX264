package com.livecamera.surface;

import java.util.concurrent.Semaphore;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

@SuppressLint("NewApi")
public class GLSurfaceView extends SurfaceView implements Runnable,
        OnFrameAvailableListener, Callback {
    public final static String TAG = "GLSurfaceView";

    private Thread mThread = null;
    private boolean mFrameAvailable = false; 
    private boolean mRunning = true;

    private SurfaceManager mViewSurfaceManager = null;
    private SurfaceManager mCodecSurfaceManager = null;
    private TextureManager mTextureManager = null;
    
    private Semaphore mLock = new Semaphore(0);
    private Object mSyncObject = new Object();

    public GLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }
    
    
    public SurfaceTexture getSurfaceTexture() {
        return mTextureManager.getSurfaceTexture();
    }

    public void addMediaCodecSurface(Surface surface) {
        synchronized (mSyncObject) {
            mCodecSurfaceManager = new SurfaceManager(surface,mViewSurfaceManager);         
        }
    }
    
    public void removeMediaCodecSurface() {
        synchronized (mSyncObject) {
            if (mCodecSurfaceManager != null) {
                mCodecSurfaceManager.release();
                mCodecSurfaceManager = null;
            }
        }
    }
    
    public void startGLThread() {
        Log.d(TAG,"Thread started.");
        if (mTextureManager == null) {
            mTextureManager = new TextureManager();
        }
        if (mTextureManager.getSurfaceTexture() == null) {
            mThread = new Thread(GLSurfaceView.this);
            mRunning = true;
            mThread.start();
            mLock.acquireUninterruptibly();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        if (mThread != null) {
            mThread.interrupt();
        }
        mRunning = false;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture arg0) {
        synchronized (mSyncObject) {
            mFrameAvailable = true;
            mSyncObject.notifyAll();    
        }
    }

    @Override
    public void run() {
        mViewSurfaceManager = new SurfaceManager(getHolder().getSurface());
        mViewSurfaceManager.makeCurrent();
        mTextureManager.createTexture().setOnFrameAvailableListener(this);

        mLock.release();

        try {
            long ts = 0, oldts = 0;
            while (mRunning) {
                synchronized (mSyncObject) {
                    mSyncObject.wait(2500);
                    if (mFrameAvailable) {
                        mFrameAvailable = false;

                        mViewSurfaceManager.makeCurrent();
                        mTextureManager.updateFrame();
                        mTextureManager.drawFrame();
                        mViewSurfaceManager.swapBuffer();
                        
                        if (mCodecSurfaceManager != null) {
                            mCodecSurfaceManager.makeCurrent();
                            mTextureManager.drawFrame();
                            oldts = ts;
                            ts = mTextureManager.getSurfaceTexture().getTimestamp();
                            //Log.d(TAG,"FPS: "+(1000000000/(ts-oldts)));
                            mCodecSurfaceManager.setPresentationTime(ts);
                            mCodecSurfaceManager.swapBuffer();
                        }
                        
                    } else {
                        Log.e(TAG,"No frame received !");
                    }
                }
            }
        } catch (InterruptedException ignore) {
        } finally {
            mViewSurfaceManager.release();
            mTextureManager.release();
        }
    }

}
