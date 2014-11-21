package com.livecamera.stream.packetizer;

import java.io.InputStream;

import com.livecamera.net.TcpClient;
import com.livecamera.stream.video.VideoParam;

public abstract class AbstractPacketizer {
    private static final String TAG = "AbstractPacketizer";
    
    protected TcpClient mClient = null;
    protected InputStream mInputStream = null;
    protected VideoParam mVideoParam = null;
    
    protected long mTimeStamp;
    
    protected byte[] mOutput = null;
    
    
    public AbstractPacketizer() {
        mClient = new TcpClient();
    }

    public void setDestination(String ipAdress, int port) {
        if (mClient != null) {
            mClient.setDestination(ipAdress, port);
        }
    }
    
    public void setVideoParam(VideoParam param) {
        mVideoParam = param;
        mOutput = new byte[mVideoParam.height*mVideoParam.width*3/2];
    }
    
    public TcpClient getClient() {
        return mClient;
    }
    
    public void setInputStream(InputStream stream) {
        mInputStream = stream;
    }
    
    
    /** Starts the packetizer. */
    public abstract void start();

    /** Stops the packetizer. */
    public abstract void stop();
    
    protected void send(byte[] data, int timestamp) {
        if (mClient != null && mClient.streamingAllowed()) {
            mClient.sendStreamData(data, timestamp);
        }
    }
}
