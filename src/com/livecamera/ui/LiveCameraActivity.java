package com.livecamera.ui;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.example.androidffmpegx264.R;
import com.livecamera.stream.MediaStream;
import com.livecamera.stream.video.VideoParam;
import com.livecamera.stream.video.VideoStream;
import com.livecamera.surface.GLSurfaceView;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;

public class LiveCameraActivity extends Activity implements OnClickListener {
    static final public String TAG = "LiveCameraActivity";
    
    private GLSurfaceView mSurfaceView;
    private ImageButton mStartStopView;
    
    private VideoStream mVideoStream;
    private VideoParam mVideoParam = new VideoParam(320, 240, 15, 1000000);
    
    private boolean mStarted = false;
    private long mClickTime = 0;

    @SuppressLint("InlinedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_preview);
        
        mSurfaceView = (GLSurfaceView)findViewById(R.id.camera_view);
        mStartStopView = (ImageButton)findViewById(R.id.live_start_stop);
        mStartStopView.setOnClickListener(this);
        
        //use the back camera
        mVideoStream = new VideoStream(CameraInfo.CAMERA_FACING_BACK);
        mVideoStream.setEncodingMethod(MediaStream.MODE_MEDIACODEC_API);
        mVideoStream.setSurfaceView(mSurfaceView);
        mVideoStream.setPreviewOrientation(90);
        
        //TODO, load the video param from settings
        mVideoStream.setVideoParam(mVideoParam);
        
        //test
        /*byte[] cmdNum = new byte[2];
        ByteBuffer buff = ByteBuffer.wrap(cmdNum);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putShort((short) 1);
        
        Log.e(TAG, "cmdNum: " + Arrays.toString(cmdNum));
        byte[] len = new byte[4];
        buff = ByteBuffer.wrap(len);
        buff.putInt((int)(10 + 1 + 2));
        Log.e(TAG, "len: " + Arrays.toString(len));*/
        //start preview
        //mVideoStream.startPreview();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onClick(View v) {
        if (v == mStartStopView) {
            
            long currentTime = System.currentTimeMillis();
            if (currentTime - mClickTime < 700) return;
            mClickTime = currentTime;
            
            if (mStarted) {
                Log.e(TAG, "click to stop");
                mVideoStream.stop();
                mStartStopView.setImageResource(R.drawable.ic_control_play);
                mStarted = false;
            } else {
                Log.e(TAG, "click to start");
                try {
                    mVideoStream.start();
                    mStartStopView.setImageResource(R.drawable.ic_control_stop);
                } catch (IllegalStateException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (Throwable e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                mStarted = true;
            }
        }
    }

}
