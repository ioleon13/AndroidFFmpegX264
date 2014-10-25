package com.livecamera.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.util.Log;

public class TcpClient implements Runnable {
    private static final String TAG = "TcpClient";
    
    private Socket mSocket = null;
    private OutputStream mOut;
    private InputStream mIn;
    private String mIPAdress = "127.0.0.1";
    private int mServerPort1 = 8252;
    private int mServerPort2;
    private int mTimeoutConn = 10000;
    
    private byte[] mRecvBuff;
    
    enum RECEIVESTATE {
        WAITING,
        RECEIVING,
        ENDING
    }
    
    private RECEIVESTATE mRecvState;

    public TcpClient(String ipAdress) {
        super();
        mRecvBuff = new byte[1024];
        this.mIPAdress = ipAdress;
        this.mRecvState = RECEIVESTATE.WAITING;
    }
    
    /**
     * connect to server, after send Cmd1, receive Cmd2, use the port2 reconnect to the server
     * @param port
     * @return
     */
    public boolean connect(int port) {
        if (mSocket != null) {
            shutdown();
        }
        
        try {
            mSocket = new Socket();
            mSocket.connect(new InetSocketAddress(mIPAdress, port),
                    mTimeoutConn);
            mOut = mSocket.getOutputStream();
            mIn = mSocket.getInputStream();
        } catch (UnknownHostException e) {
            Log.e(TAG, "Failed to connect <" + mIPAdress + ":" + port + ">"
                    + ", error: unknown host");
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect <" + mIPAdress + ":" + port + ">"
                    + ", error: io exception");
            e.printStackTrace();
            return false;
        }
        
        Log.i(TAG, "Success to connect <" + mIPAdress + ":" + port + ">");
        return true;
    }
    
    /**
     * Cmd1: 
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |Prefix=0x82(1Byte) |  Num=1(2Byte)  |  Len(4Byte)   | ClientType=0(1Byte)  | URL(string) |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    public void sendCmd1() {
        
    }
    
    
    private void shutdown() {
        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        mSocket = null;
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        
    }

}
