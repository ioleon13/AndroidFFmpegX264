package com.livecamera.stream.packetizer;

import java.io.InputStream;

import com.livecamera.net.TcpClient;

public abstract class AbstractPacketizer {
    private static final String TAG = "AbstractPacketizer";
    
    protected TcpClient mClient = null;
    protected InputStream mInputStream = null;
    
    protected long mTimeStamp;
    
    
    public AbstractPacketizer() {
        mClient = new TcpClient();
    }

    public void setDestination(String ipAdress, int port) {
        if (mClient != null) {
            mClient.setDestination(ipAdress, port);
        }
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
