package com.livecamera.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.R.string;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class TcpClient {
    private static final String TAG = "TcpClient";
    
    private Socket mSocket = null;
    private OutputStream mOut;
    private InputStream mIn;
    private String mIPAdress = "127.0.0.1";
    private int mServerPort1 = 8252;
    private int mServerPort2;
    private int mTimeoutConn = 10000;
    private String mUrl;
    
    private byte[] mRecvBuff;
    
    private Handler mClientHandler;
    
    private UrlClient mURLClient;
    private StreamingClient mStreamClient;
    
    private boolean mStreamingAllowed = false;
    
    enum RECEIVESTATE {
        WAITING,
        RECEIVING,
        ENDING
    }
    
    private RECEIVESTATE mRecvState;

    public TcpClient(String ipAdress, String url) {
        super();
        mRecvBuff = new byte[1024];
        this.mIPAdress = ipAdress;
        this.mUrl = url;
        this.mRecvState = RECEIVESTATE.WAITING;
        createHander();
    }
    
    public void start() {
        mURLClient = new UrlClient();
        mURLClient.setHandler(mClientHandler);
        
        mStreamClient = new StreamingClient();
        mStreamClient.setHandler(mClientHandler);
        
        boolean isConnected = mURLClient.connect();
        if (isConnected) {
            mURLClient.sendCmd1();
        }
    }
    
    
    public boolean streamingAllowed() {
        return mStreamingAllowed;
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
    
    
    @SuppressLint("HandlerLeak")
    private void createHander() {
        mClientHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MessageTypes.PORT2_RECEIVED:
                    
                    break;
                    
                case MessageTypes.STREAMING_ALLOWED:
                    mStreamingAllowed = true;
                    break;
                    
                case MessageTypes.HEARTBEAT_RECEIVED:
                    break;

                default:
                    break;
                }
                super.handleMessage(msg);
            }
            
        };
    }
    
    
    private void shutdown() {
        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        mSocket = null;
    }
    
    
    public class UrlClient implements Runnable {
        private boolean mIsStop = false;
        private Handler mHandler = null;
        
        public void setHandler(Handler handler) {
            this.mHandler = handler;
        }
        
        
        public boolean connect() {
            return TcpClient.this.connect(mServerPort1);
        }
        
        
        /**
         * Cmd1: 
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=1(2Byte) |  Len(4Byte)   |  ClientType=0(1Byte)  | URL(string) |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        public void sendCmd1() {
            byte[] prefix = {(byte) 0x82};
            byte[] cmdNum = new byte[2];
            ByteBuffer buff = ByteBuffer.wrap(cmdNum);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putShort((short) 1);
            byte[] len = new byte[4];
            buff = ByteBuffer.wrap(len);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt((int)(mUrl.length() + 1 + 2));
            byte[] clientType = {(byte)0x00};
            byte[] url = mUrl.getBytes();
            
            //send
            try {
                mOut.write(prefix, 0, prefix.length);
                mOut.write(cmdNum, 0, cmdNum.length);
                mOut.write(len, 0, len.length);
                mOut.write(clientType, 0, clientType.length);
                mOut.write(url, 0, url.length);
                mOut.flush();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send cmd1, IOException caught");
                e.printStackTrace();
            }
            
            Log.i(TAG, "Successed to send Cmd1");
        }
        
        
        /**
         * read the feedback from server
         */
        private void read() {
            try {
                int bytes = mIn.available();
                if (bytes > 0) {
                    mIn.read(mRecvBuff, 0, 1024);
                    //String data = new String(mRecvBuff, 0, 1024);
                    handleRecvData(mRecvBuff);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to receive Cmd2 from server");
                e.printStackTrace();
            }
        }
        
        
        /**
         * handle the received data from server, such as: Cmd2
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=2(2Byte) |  Len(4Byte)   |  URL(string)  | ServerPort2(4Byte)  |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * @param data
         */
        private int handleRecvData(byte[] data) {
            int size = data.length;
            if (size == 0) {
                Log.e(TAG, "Receiving data size is 0");
                return 2;
            }
            ByteBuffer buff = ByteBuffer.wrap(data);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            if (data[0] != 0x82) {
                return 2;
            }
            
            short cmdNum = buff.getShort(1);
            if (cmdNum != 2) {
                return 2;
            }
            
            int cmdLen = buff.getInt(3);
            int urlLen = buff.getInt(7);
            
            if (urlLen == 0 || (11+urlLen+4) != size) {
                return 2;
            }
            
            byte[] url = new byte[urlLen];
            buff.get(url, 11, urlLen);
            String strUrl = new String(url, 0, urlLen);
            mServerPort2 = buff.getInt(11 + urlLen);
            
            return 0;
        }

        @Override
        public void run() {
            while (!mIsStop) {
                read();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            shutdown();
        }
        
        public synchronized void stop() {
            mIsStop = true;
        }
    }
    
    
    public class StreamingClient implements Runnable {
        private boolean mIsStop = false;
        private Handler mHandler = null;
        
        public void setHandler(Handler handler) {
            this.mHandler = handler;
        }
        
        public boolean connect() {
            return TcpClient.this.connect(mServerPort2);
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            
        }
        
    }
}
