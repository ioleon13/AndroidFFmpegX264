package com.livecamera.encoder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.livecamera.stream.video.VideoParam;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

public class MediaCodecEncoder {
	private String TAG = "MediaCodecEncoder";
	
	private Camera mCamera;
	
	private MediaCodec mMediaCodec;
	
	private VideoParam mVideoParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
	
	private RandomAccessFile mRaf = null;
	
	private byte[] mYUV420 = null;
	private byte[] mEncodedBuf = null;
	private byte[] mPpsSpsInfo = null;
	
	//network
    private DatagramSocket mSocket;
    private InetAddress mAddress;
	
	public MediaCodecEncoder() {
        super();
        
        //network
        try {
            mSocket = new DatagramSocket();
            mAddress = InetAddress.getByName("192.168.2.104");
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void setCamera(Camera camera) {
		mCamera = camera;
	}
	
	public void setVideoParam(VideoParam p) {
		mVideoParam = p;
	}
	
	@SuppressLint("NewApi")
	public void start() {
		//computes the average framerate of the camera
		measureFramerate();
		
		mYUV420 = new byte[mVideoParam.width*mVideoParam.height*3/2];
		mEncodedBuf = new byte[mVideoParam.width*mVideoParam.height*3/2];
		
		//save file first for testing
        /*try {
            File file = new File("/sdcard/camera1.h264");
            mRaf = new RandomAccessFile(file, "rw");
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }*/
		
		mMediaCodec = MediaCodec.createEncoderByType("video/avc");
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mVideoParam.width,
				mVideoParam.height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoParam.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoParam.framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
				MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();
		
		Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				int result = encodeBuffer(data, mEncodedBuf);
				
				try {
                    if (result > 0 && mSocket != null && mAddress != null) {
                        DatagramPacket packet = new DatagramPacket(mEncodedBuf, result,
                                mAddress, 5000);
                        mSocket.send(packet);
                    }
                } catch (IOException e) {
                    Log.w(TAG, e.toString());
                }
			}
		};
		
		mCamera.setPreviewCallback(callback);
	}
	
	public void stop() {
		if (mCamera != null) {
			//mCamera.setPreviewCallbackWithBuffer(null);
		    mCamera.setPreviewCallback(null);
        }
	}
	
	public void release() {
	    Log.e(TAG, "release the encoder");
	    
	    try {
			mMediaCodec.stop();
			mMediaCodec.release();
			mMediaCodec = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * computes the average framerate 
	 */
	private void measureFramerate() {
		final Semaphore lock = new Semaphore(0);
		
		final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			int i = 0, t = 0;
			long now, oldnow, count = 0;
			@Override
			public void onPreviewFrame(byte[] arg0, Camera arg1) {
				i++;
				now = System.nanoTime()/1000;
				if (i > 3) {
					t += now - oldnow;
					count++;
				}
				
				if (i > 20) {
					mVideoParam.framerate = (int) (1000000/(t/count) + 1);
					Log.d(TAG, "The average of framerate is: " + mVideoParam.framerate);
					lock.release();
				}
			}
		};
		
		mCamera.setPreviewCallback(callback);
		
		try {
			lock.tryAcquire(2, TimeUnit.SECONDS);
			Log.d(TAG, "The framerate configurated is: " + mVideoParam.framerate);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		mCamera.setPreviewCallback(null);
	}
	
	
	/**
	 * The format of camera video data was yuv420sp, should convert it to yuv420p 
	 */
	private void convertYUV420sptoYUV420p(byte[] YUV420sp, byte[] YUV420p, int width, int height) {
		System.arraycopy(YUV420sp, 0, YUV420p, 0, width*height);
		System.arraycopy(YUV420sp, width*height + width*height/4, YUV420p, width*height, width*height/4);
		System.arraycopy(YUV420sp, width*height, YUV420p, width*height + width*height/4, width*height/4);
	}
	
	
	/**
	 * encode buff
	 */
	@SuppressLint("NewApi")
	private int encodeBuffer(byte[] input, byte[] output) {
		int pos = 0;
		
		//convert yuv420sp to yuv420p
		convertYUV420sptoYUV420p(input, mYUV420, mVideoParam.width, mVideoParam.height);
		
		try {
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
			
			int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();
				inputBuffer.put(mYUV420);
				mMediaCodec.queueInputBuffer(inputBufferIndex, 0, mYUV420.length, 0, 0);
			}
			
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
			int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
			while (outputBufferIndex >= 0) {
				ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
				byte[] outData = new byte[bufferInfo.size];
				outputBuffer.get(outData);
				
				if (mPpsSpsInfo != null) {
					System.arraycopy(outData, 0, output, pos, outData.length);
					pos += outData.length;
				} else {// pps sps info, save these in the first key frame
					ByteBuffer ppsSpsBuffer = ByteBuffer.wrap(outData);
					if (ppsSpsBuffer.getInt() == 0x00000001) {
						mPpsSpsInfo = new byte[outData.length];
						System.arraycopy(outData, 0, mPpsSpsInfo, 0, outData.length);
					} else {
						return -1;
					}
				}
				
				mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
				outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
			}
			
			//keyframe, add pps sps info, 00 00 00 01 65
			if (output[4] == 0x65) {
				System.arraycopy(output, 0, mYUV420, 0, pos);
				System.arraycopy(mPpsSpsInfo, 0, output, 0, mPpsSpsInfo.length);
				System.arraycopy(mYUV420, 0, output, mPpsSpsInfo.length, pos);
				pos += mPpsSpsInfo.length;
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		
		return pos;
	}
}
