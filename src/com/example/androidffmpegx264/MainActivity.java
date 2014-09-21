package com.example.androidffmpegx264;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.TextView;
import android.hardware.Camera;

public class MainActivity extends Activity implements Callback, Camera.PictureCallback{
    private String TAG="MainActivity";
    
    //private TextView mVersion;
    private SurfaceView mCameraView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private Camera mCamera = null;
    private boolean mPreviewRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        
        /*mVersion = (TextView)findViewById(R.id.version_num);
        FFmpegTest ffmpeg = new FFmpegTest();
        mVersion.setText(String.valueOf(ffmpeg.stringFromJNI()));*/
        
        mCameraView = (SurfaceView)findViewById(R.id.camera_view);
        mSurfaceHolder = mCameraView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mCamera != null) {
            if (mPreviewRunning) {
                mCamera.stopPreview();
            }
            
            Camera.Parameters p = mCamera.getParameters();
            //p.setPreviewSize(352, 288);
            mCamera.setPreviewCallback(new H264Encoder(352, 288));
            mCamera.setParameters(p);
            
            try {
                mCamera.setPreviewDisplay(mSurfaceHolder);
                mCamera.setDisplayOrientation(90);
            } catch (Exception e) {
                Log.w(TAG, e.toString());
            }
            
            mCamera.startPreview();
            mPreviewRunning = true;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        mCamera = Camera.open();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mPreviewRunning = false;
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

}
