package com.livecamera.stream.packetizer;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class H264Packetizer extends AbstractPacketizer implements Runnable{
    public final static String TAG = "H264Packetizer";
    
    private Thread mThread = null;
    private int mNALULen = 0;
    private long mDelay = 0, mOldTime = 0;
    private byte[] mSpsPpsInfo = null;
    private byte[] mHeader = new byte[5];
    private int mStreamType = 1;
    private int mCount = 0;

    public H264Packetizer() {
        super();
    }

    @Override
    public void start() {
        if (mClient != null) {
            mClient.start();
        }
        
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
                send();
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    
    @SuppressLint("NewApi")
    private void send() throws IOException {
        if (mStreamType == 0) {
            read(mHeader, 0, 5);
            mNALULen = mHeader[3]&0xFF | (mHeader[2]&0xFF)<<8 |
                    (mHeader[1]&0xFF)<<16 | (mHeader[0]&0xFF)<<24;
        } else if(mStreamType == 1) {
            read(mHeader, 0, 5);
            mTimeStamp = ((MediaCodecInputStream)mInputStream)
                    .getLastBufferinfo().presentationTimeUs*1000L;
            mNALULen = mInputStream.available() + 1;
            if(!(mHeader[0] == 0 && mHeader[1] == 0 && mHeader[2] == 0)) {
                Log.e(TAG, "NAL units are not 0x00000001");
                mStreamType = 2;
                return;
            }
        } else {
            read(mHeader, 0, 1);
            mHeader[4] = mHeader[0];
            mTimeStamp = ((MediaCodecInputStream)mInputStream)
                    .getLastBufferinfo().presentationTimeUs*1000L;
            mNALULen = mInputStream.available() + 1;
        }
        
        byte[] outData = new byte[mNALULen];
        read(outData, 0, mNALULen-1);
        
        if (mSpsPpsInfo != null) {
            if (mOutput != null) {
                System.arraycopy(outData, 0, mOutput, 0, outData.length);
            }
        } else {
            ByteBuffer ppsSpsBuffer = ByteBuffer.wrap(outData);
            if (ppsSpsBuffer.getInt() == 0x00000001) {
                mSpsPpsInfo = new byte[outData.length];
                System.arraycopy(outData, 0, mSpsPpsInfo, 0, outData.length);
            } else {
                return;
            }
        }
        
        //keyframe, add pps sps info, 00 00 00 01 65
        if (mOutput[4] == 0x65) {
            //send pps sps
            super.send(mSpsPpsInfo, (int)mTimeStamp);
        }
        
        //send output
        super.send(mOutput, (int)mTimeStamp);
    }
    
    private int read(byte[] buffer, int offset, int length) throws IOException {
        int ret = 0, len = 0;
        while (ret < length) {
            len = mInputStream.read(buffer, offset+ret, length-ret);
            if (len < 0) {
                throw new IOException("end of stream");
            } else {
                ret += len;
            }
        }
        return ret;
    }
}
