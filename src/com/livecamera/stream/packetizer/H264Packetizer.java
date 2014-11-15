package com.livecamera.stream.packetizer;

import java.io.IOException;

public class H264Packetizer extends AbstractPacketizer implements Runnable{
    public final static String TAG = "H264Packetizer";
    
    private Thread mThread = null;
    private int mNALULen = 0;
    private long mDelay = 0, mOldTime = 0;
    private byte[] mSPS = null, mPPS = null;
    private byte[] mHeader = new byte[5];
    private int mStreamType = 1;
    private int mCount = 0;

    public H264Packetizer() {
        super();
    }

    @Override
    public void start() {
        if (mThread == null) {
            mThread = new Thread(this);
            mThread.start();
        }
    }

    @Override
    public void stop() {
        if (mThread != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            mThread.interrupt();
            try {
                mThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mThread = null;
        }
    }

    @Override
    public void run() {
        mCount = 0;
        
        if (mInputStream instanceof MediaCodecInputStream) {
            mStreamType = 1;
        } else {
            mStreamType = 0;
        }
        
        try {
            while (!Thread.interrupted()) {
                
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

}
