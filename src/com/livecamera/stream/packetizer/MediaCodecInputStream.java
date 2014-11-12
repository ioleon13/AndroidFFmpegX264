package com.livecamera.stream.packetizer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;

public class MediaCodecInputStream extends InputStream {
    public final String TAG = "MediaCodecInputStream";
    
    private MediaCodec mMediaCodec = null;
    private BufferInfo mBufferInfo = new BufferInfo();
    private ByteBuffer[] mOutputBuffers = null;
    private ByteBuffer mOutputBuffer = null;
    private int mIndex = -1;
    private boolean mClosed = false;
    
    private MediaFormat mMediaFormat;

    public MediaCodecInputStream(MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;
        mOutputBuffers = mMediaCodec.getOutputBuffers();
    }

    @Override
    public void close() throws IOException {
        mClosed = true;
    }

    @Override
    public int read(byte[] buffer, int byteOffset, int byteCount)
            throws IOException {
        int readSize = 0;
        try {
            if (mOutputBuffer == null) {
                while (!Thread.interrupted() && !mClosed) {
                    mIndex = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 500000);
                    if (mIndex >= 0) {
                        mOutputBuffer = mOutputBuffers[mIndex];
                        mOutputBuffer.position(0);
                        break;
                    } else if (mIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        mOutputBuffers = mMediaCodec.getOutputBuffers();
                    } else if (mIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        mMediaFormat = mMediaCodec.getOutputFormat();
                        Log.i(TAG, mMediaFormat.toString());
                    } else if (mIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.e(TAG, "No buffer available...");
                    } else {
                        Log.e(TAG, "Unknown output buffer index: " + mIndex);
                    }
                }
            }
            
            if (mClosed) {
                throw new IOException("This input stream was closed.");
            }
            
            int bufferSize = mBufferInfo.size - mOutputBuffer.position();
            readSize = byteCount < bufferSize ? byteCount : bufferSize;
            mOutputBuffer.get(buffer, byteOffset, readSize);
            if (mOutputBuffer.position() >= mBufferInfo.size) {
                mMediaCodec.releaseOutputBuffer(mIndex, false);
                mOutputBuffer = null;
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return readSize;
    }

    @Override
    public int read() throws IOException {
        return 0;
    }
    
    
    public int available() {
        if (mOutputBuffer != null) {
            return mBufferInfo.size - mOutputBuffer.position();
        } else {
            return 0;
        }
    }
    
    public BufferInfo getLastBufferinfo() {
        return mBufferInfo;
    }

}
