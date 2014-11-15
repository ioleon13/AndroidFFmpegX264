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

import com.livecamera.net.TcpClient;
import com.livecamera.stream.video.VideoParam;
import com.livecamera.surface.GLSurfaceView;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

@SuppressLint("NewApi")
public class MediaCodecEncoder {
	private static final String TAG = "MediaCodecEncoder";
	private static final boolean VERBOSE = true;
	
	private static final String MIME_TYPE = "video/avc";
	private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
	
	private Camera mCamera;
	
	private MediaCodec mMediaCodec;
	private MediaCodecInfo mCodecInfo;
	
	private VideoParam mVideoParam = VideoParam.DEFAULT_VIDEO_PARAM.clone();
	
	private RandomAccessFile mRaf = null;
	
	private byte[] mYUV420 = null;
	private byte[] mEncodedBuf = null;
	private byte[] mPpsSpsInfo = null;
	
	//network
    /*private DatagramSocket mSocket;
    private InetAddress mAddress;*/
    
    private int mColorFormat = 0;
    
    private boolean mSemiPlanar = false;
    
    private GLSurfaceView mSurfaceView = null;
    
    private boolean mEncodeFromSurface = true;
    
    private boolean mStop = false;
    
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;
    private static final int TEST_R0 = 0;                   // RGB equivalent of {0,0,0}
    private static final int TEST_G0 = 136;
    private static final int TEST_B0 = 0;
    private static final int TEST_R1 = 236;                 // RGB equivalent of {120,160,200}
    private static final int TEST_G1 = 50;
    private static final int TEST_B1 = 186;
	
	public MediaCodecEncoder() {
        super();
        
        //network
        /*try {
            mSocket = new DatagramSocket();
            mAddress = InetAddress.getByName("192.168.2.104");
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/
    }

    public void setCamera(Camera camera) {
		mCamera = camera;
	}
	
	public void setVideoParam(VideoParam p) {
		mVideoParam = p;
	}
	
	public void setSurfaceView(GLSurfaceView view) {
        mSurfaceView = view;
    }
    
    public void setEncodeFromSurface(boolean encodeFromSurface) {
        mEncodeFromSurface = encodeFromSurface;
    }
	
	@SuppressLint("NewApi")
	public void start() {
		//computes the average framerate of the camera
		//measureFramerate();
	    
	    //start tcp client
	    //mTcpClient.start();
		
		mYUV420 = new byte[mVideoParam.width*mVideoParam.height*3/2];
		mEncodedBuf = new byte[mVideoParam.width*mVideoParam.height*3/2];
		
		//save file first for testing
        try {
            File file = new File("/sdcard/camera1.h264");
            mRaf = new RandomAccessFile(file, "rw");
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }
		
		mCodecInfo = selectCodec(MIME_TYPE);
        if (mCodecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        
        if(VERBOSE) Log.d(TAG, "found codec: " + mCodecInfo.getName());
        
        if (mEncodeFromSurface) {
            if(VERBOSE) Log.d(TAG, "Encode video from surface");
            encodeFromSurface();
        } else {
            if(VERBOSE) Log.d(TAG, "Encode video from preview callback buffer");
            encodeFromBuffer();
        }
	}
	
	public void stop() {
	    mStop = true;
		if (mCamera != null) {
		    if (mEncodeFromSurface) {
                if (mSurfaceView != null) {
                    mSurfaceView.removeMediaCodecSurface();
                }
            } else {
                //mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.setPreviewCallback(null);
            }
			
        }
		
		if (mMediaCodec != null && mEncodeFromSurface) {
		    if (VERBOSE) Log.d(TAG, "signaling input EOS");
            mMediaCodec.signalEndOfInputStream();
        }
	}
	
	public void release() {
	    Log.e(TAG, "release the encoder");
	    
	    try {
	        if (mMediaCodec != null) {
	            mMediaCodec.stop();
	            mMediaCodec.release();
	            mMediaCodec = null;
            }
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * encode video from onPreviewFrame buffer
	 * @param codecInfo
	 */
	@SuppressLint("NewApi")
	private void encodeFromBuffer() {
		mColorFormat = selectColorFormat(mCodecInfo, MIME_TYPE);
		if(VERBOSE) Log.d(TAG, "found colorFormat: " + mColorFormat);
        
        mSemiPlanar = isSemiPlanarYUV(mColorFormat);
		
        mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());

		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mVideoParam.width,
				mVideoParam.height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoParam.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoParam.framerate);
		
		//if you use COLOR_FormatYUV420SemiPlanar, the input data should not to convert, the camera
		//preview format was set NV21 defaultly.
		//but if you set color format COLOR_FormatYUV420Planar here, you should convert the input data
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();
		
		Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				int result = doEncodeFromBuffer(data, mEncodedBuf);
				
				if (result > 0) {
                    /*DatagramPacket packet = new DatagramPacket(mEncodedBuf, result,
                            mAddress, 5000);
                    mSocket.send(packet);*/
                    //mRaf.write(mEncodedBuf, 0, result);
                }
			}
		};
		
		mCamera.setPreviewCallback(callback);
	}
	
	
	@SuppressLint("NewApi")
	private void encodeFromSurface() {
		mColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
		if(VERBOSE) Log.d(TAG, "found colorFormat: " + mColorFormat);

		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mVideoParam.width,
				mVideoParam.height);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoParam.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoParam.framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
		
		mMediaCodec = MediaCodec.createByCodecName(mCodecInfo.getName());
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		Surface surface = mMediaCodec.createInputSurface();
		if (mSurfaceView != null) {
            mSurfaceView.addMediaCodecSurface(surface);
        }
		mMediaCodec.start();
		
		doEncodeFromSurface();
	}
	
	
	/**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / mVideoParam.framerate;
    }
    
    
    /**
     * Generates a frame of data using GL commands.
     */
    private void generateSurfaceFrame(int frameIndex) {
        frameIndex %= 8;

        int startX, startY;
        if (frameIndex < 4) {
            // (0,0) is bottom-left in GL
            startX = frameIndex * (mVideoParam.width / 4);
            startY = mVideoParam.height / 2;
        } else {
            startX = (7 - frameIndex) * (mVideoParam.width / 4);
            startY = 0;
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(TEST_R0 / 255.0f, TEST_G0 / 255.0f, TEST_B0 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(startX, startY, mVideoParam.width / 4, mVideoParam.height / 2);
        GLES20.glClearColor(TEST_R1 / 255.0f, TEST_G1 / 255.0f, TEST_B1 / 255.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
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
					if(VERBOSE) Log.d(TAG, "The average of framerate is: " + mVideoParam.framerate);
					lock.release();
				}
			}
		};
		
		mCamera.setPreviewCallback(callback);
		
		try {
			lock.tryAcquire(2, TimeUnit.SECONDS);
			if(VERBOSE) Log.d(TAG, "The framerate configurated is: " + mVideoParam.framerate);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		mCamera.setPreviewCallback(null);
	}
	
	
	/**
	 * The format of camera video data was NV21, if the color format of MediaCodec
	 * was COLOR_FormatYUV420Planar should be converted 
	 */
	private void convertColorFormat(byte[] YUV420sp, byte[] YUV420p, int width, int height) {	    
	    if (mSemiPlanar) { //if the color format was semi planar, do not convert, copy directly
	        System.arraycopy(YUV420sp, 0, YUV420p, 0, YUV420sp.length);
        } else {
            final int frameSize = width*height;
            
            //Y
            System.arraycopy(YUV420sp, 0, YUV420p, 0, frameSize);
            
            //U
            int pIndex = width*height;
            for (int i = frameSize+1; i < YUV420p.length; i+=2) {
                YUV420p[pIndex++] = YUV420sp[i];
            }
            
            //V
            for (int i = frameSize; i < YUV420p.length; i+=2) {
                YUV420p[pIndex++] = YUV420sp[i];
            }
        }
		
		
		//System.arraycopy(YUV420sp, frameSize + qFrameSize, YUV420p, frameSize, qFrameSize);
		//System.arraycopy(YUV420sp, frameSize, YUV420p, frameSize + qFrameSize, qFrameSize);
	}
	
	
	/**
	 * encode buff
	 */
	@SuppressLint("NewApi")
	private int doEncodeFromBuffer(byte[] input, byte[] output) {
		int pos = 0;
		
		//the camera preview format was nv21, convert it to yuv420sp or yuv420p
		convertColorFormat(input, mYUV420, mVideoParam.width, mVideoParam.height);
		
		try {
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
			
			int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
			if (inputBufferIndex >= 0) {
				ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
				inputBuffer.clear();
				inputBuffer.put(mYUV420);
				mMediaCodec.queueInputBuffer(inputBufferIndex, 0, mYUV420.length,
				        System.nanoTime()/1000, 0);
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
	
	
	private void doEncodeFromSurface() {
	    final int TIMEOUT_USEC = 10000;
	    int generateIndex = 0;
	    boolean inputDone = false;
	    boolean encoderDone = false;
	    
	    try {
	        ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
	        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	        
	        while (!mStop) {
	            if (!inputDone) {
	                /*mInputSurface.makeCurrent();
	                generateSurfaceFrame(generateIndex);
	                mInputSurface.setPresentationTime(computePresentationTime(generateIndex));
	                if (VERBOSE) Log.d(TAG, "inputSurface swapBuffers");
	                mInputSurface.swapBuffers();*/
	                
	                generateIndex++;
                }
	            
	            while (true) {
	                if (!encoderDone) {
	                    int pos = 0;
	                    
	                    int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
	                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
	                        Log.w(TAG, "no output available yet");
	                        break;
	                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
	                        //getOutputBuffers again
	                        outputBuffers = mMediaCodec.getOutputBuffers();
	                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
	                        
	                    } else if (outputBufferIndex < 0) {
	                        Log.w(TAG, "unexpected result from mMediaCodec.dequeueOutputBuffer: " + outputBufferIndex);
	                    } else {
	                        ByteBuffer encodedData = outputBuffers[outputBufferIndex];
	                        if (encodedData == null) {
	                            throw new RuntimeException("encoderOutputBuffer " + outputBufferIndex +
	                                    " was null");
	                        }
	                        
	                        if (bufferInfo.size != 0) {
	                            // adjust the ByteBuffer values to match BufferInfo (not needed?)
	                            encodedData.position(bufferInfo.offset);
	                            encodedData.limit(bufferInfo.offset + bufferInfo.size);

	                            byte[] outData = new byte[bufferInfo.size];
	                            encodedData.get(outData);
	                            encodedData.position(bufferInfo.offset);
	                            
	                            if (mPpsSpsInfo != null) {
	                                System.arraycopy(outData, 0, mEncodedBuf, pos, outData.length);
	                                pos += outData.length;
	                            } else {// pps sps info, save these in the first key frame
	                                ByteBuffer ppsSpsBuffer = ByteBuffer.wrap(outData);
	                                if (ppsSpsBuffer.getInt() == 0x00000001) {
	                                    mPpsSpsInfo = new byte[outData.length];
	                                    System.arraycopy(outData, 0, mPpsSpsInfo, 0, outData.length);
	                                } else {
	                                    Log.w(TAG, "unexpected pps sps info");
	                                }
	                            }
	                        }

	                        mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);

	                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
	                            encoderDone = true;
	                            break;
	                        }
	                    }
	                    
	                    //keyframe, add pps sps info, 00 00 00 01 65
	                    if (mEncodedBuf[4] == 0x65) {
	                        System.arraycopy(mEncodedBuf, 0, mYUV420, 0, pos);
	                        System.arraycopy(mPpsSpsInfo, 0, mEncodedBuf, 0, mPpsSpsInfo.length);
	                        System.arraycopy(mYUV420, 0, mEncodedBuf, mPpsSpsInfo.length, pos);
	                        pos += mPpsSpsInfo.length;
	                    }
	                    
	                    if (pos > 0) {
	                        Log.e(TAG, "write file");
	                        mRaf.write(mEncodedBuf, 0, pos);
	                    }
	                }
                }
	            
	            
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
	}
	
	
	/**
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    @SuppressLint("NewApi")
    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
    
    /**
     * Returns a color format that is supported by the codec and by this test code.  If no
     * match is found, this throws a test failure -- the set of formats known to the test
     * should be expanded for new platforms.
     */
    @SuppressLint("NewApi")
    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }
    
    /**
     * Returns true if this is a color format that this test code understands (i.e. we know how
     * to read and generate frames in this format).
     */
    private boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Returns true if the specified color format is semi-planar YUV.  Throws an exception
     * if the color format is not recognized (e.g. not YUV).
     */
    private boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }
}
