package com.livecamera.stream.video;

import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;

public class VideoParam {
	private final static String TAG = "VideoParam";
	
	public int width = 0;
	public int height = 0;
	public int framerate = 0;
	public int bitrate = 0;
	
	//default video params
	public final static VideoParam DEFAULT_VIDEO_PARAM = new VideoParam(176, 144, 20, 500000);
	
	public VideoParam() {}
	
	public VideoParam(int width, int height) {
		this.width = width;
		this.height = height;
	}
	
	public VideoParam(int width, int height, int framerate, int bitrate) {
		this.width = width;
		this.height = height;
		this.framerate = framerate;
		this.bitrate = bitrate;
	}
	
	public boolean equals(VideoParam param) {
		if (param == null) {
			return false;
		}
		
		return (this.width == param.width
				&& this.height == param.height
				&& this.framerate == param.framerate
				&& this.bitrate == param.bitrate);
	}
	
	public VideoParam clone() {
		return new VideoParam(width, height, framerate, bitrate);
	}
	
	public static VideoParam parseParam(String str) {
		VideoParam param = DEFAULT_VIDEO_PARAM.clone();
		if (null != str) {
			String[] settings = str.split("-");
			
			try {
				param.width = Integer.parseInt(settings[0]);
				param.height = Integer.parseInt(settings[1]);
				param.framerate = Integer.parseInt(settings[2]);
				param.bitrate = Integer.parseInt(settings[3]) * 1000;
			} catch (IndexOutOfBoundsException e) {
				// TODO: handle exception
			}
		}
		
		return param;
	}
	
	/**
	 * Validate the resolution parameters, if not supported, modified these parameters to a closest one
	 */
	public static VideoParam validateSupportedResolution(Camera.Parameters params, VideoParam videoparams) {
		VideoParam v = videoparams.clone();
		int minValue = Integer.MAX_VALUE;
		String strSupportedResolution = "Supported resolutions: ";
		List<Size> supportedSizes = params.getSupportedPreviewSizes();
		for (Iterator<Size> it = supportedSizes.iterator(); it.hasNext();) {
			Size size = it.next();
			strSupportedResolution += size.width + "x" + size.height +
					(it.hasNext() ? ", " : "");
			int dist = Math.abs(videoparams.width - size.width);
			if (dist < minValue) {
				minValue = dist;
				v.width = size.width;
				v.height = size.height;
			}
		}
		Log.v(TAG, strSupportedResolution);
		
		if (videoparams.width != v.width || videoparams.height != v.height) {
			Log.i(TAG, "Resolution was modified: " + videoparams.width + "x" + videoparams.height +
					"->" + v.width + "x" + v.height);
		}
		
		return v;
	}
	
	/**
	 * Get the maximum supported frame rate
	 */
	@SuppressLint("NewApi")
	public static int[] getMaximumSupportedFramerate(Camera.Parameters params) {
		int[] maxFps = new int[]{0, 0};
		
		String strSupportedFpsRange = "Supported frame rates: ";
		List<int[]> supportedFpsRanges = params.getSupportedPreviewFpsRange();
		for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext();) {
			int[] cur = it.next();
			strSupportedFpsRange += cur[0]/1000 + "-" + cur[1]/1000 + "fps" + 
			(it.hasNext() ? ", " : "");
			
			if (cur[1] > maxFps[1]
					|| (cur[0] > maxFps[0] && cur[1] == maxFps[1])) {
				maxFps = cur;
			}
		}
		Log.v(TAG, strSupportedFpsRange);
		
		return maxFps;
	}
}
