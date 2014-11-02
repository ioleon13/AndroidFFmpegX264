package com.livecamera.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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
    private int mServerPort1 = 8282;
    private int mServerPort2;
    private int mTimeoutConn = 10000;
    private int mTimeoutRecv = 5000;
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
        
        new Thread(runnable).start();
    }
    
    
    Runnable runnable = new Runnable() {
        
        @Override
        public void run() {
            boolean isConnected = mURLClient.connect();
            if (isConnected) {
                mURLClient.start();
                mURLClient.sendCmd1();
            }
        }
    };
    
    
    /**
     * send encoded data to server
     * @param data
     */
    public void sendStreamData(byte[] data, int timestamp) {
        //TODO: it should be inserted into a buffer
        //but now, send it immediately
    	if (mStreamClient != null) {
			mStreamClient.sendCmd5(data, timestamp);
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
            Log.d(TAG, "connect to <" + mIPAdress + ":" + port + ">");
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
                	mServerPort2 = msg.arg1;
                	mUrl = (String)msg.obj;
                	
                    //stop UrlClient and start StreamClient to send Cmd3
                	startStreamClient();
                    break;
                    
                case MessageTypes.STREAMING_ALLOWED:
                    int access = msg.arg1;
                    if (access == 0) {
                        mStreamingAllowed = true;
                    } else {
                        mStreamingAllowed = false;
                    }
                    break;
                    
                case MessageTypes.HEARTBEAT_RECEIVED:
                    String HeartBeatStr = (String)msg.obj;
                    //handle heartbeat
                    break;

                default:
                    break;
                }
                super.handleMessage(msg);
            }
            
        };
    }
    
    
    private void startStreamClient() {
    	//first: stop UrlClient
    	if (mURLClient != null) {
			mURLClient.stop();
		}
    	
    	//second: StreamClient connect
    	if (mStreamClient != null) {
    		boolean isConnected = mStreamClient.connect();
    		
    		//third: start listen and send Cmd3
    		if (isConnected) {
				mStreamClient.start();
				mStreamClient.sendCmd3();
			}
		}
    	
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
        private long mCmd1SendTime = System.currentTimeMillis();
        
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
            //mUrl = "";
            int urlLen = mUrl.length();
            byte[] toSend = new byte[1 + 2 + 4 + 1 + 2 + urlLen];
            int offset = 0;
            
            byte[] prefix = {(byte) 0x82};
            System.arraycopy(prefix, 0, toSend, offset, prefix.length);
            offset += prefix.length;
            
            byte[] cmdNum = new byte[2];
            ByteBuffer buff = ByteBuffer.wrap(cmdNum);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putShort((short) 1);
            System.arraycopy(cmdNum, 0, toSend, offset, cmdNum.length);
            offset += cmdNum.length;
            
            byte[] len = new byte[4];
            buff = ByteBuffer.wrap(len);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt((int)(urlLen + 1 + 2));
            System.arraycopy(len, 0, toSend, offset, len.length);
            offset += len.length;
            
            byte[] clientType = {(byte)0x00};
            System.arraycopy(clientType, 0, toSend, offset, clientType.length);
            offset += clientType.length;
            
            byte[] strLen = new byte[2];
            buff = ByteBuffer.wrap(strLen);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putShort((short) urlLen);
            System.arraycopy(strLen, 0, toSend, offset, strLen.length);
            offset += strLen.length; 
            
            byte[] url = mUrl.getBytes();
            System.arraycopy(url, 0, toSend, offset, url.length);
            
            //send
            try {
                /*mOut.write(prefix, 0, prefix.length);
                mOut.write(cmdNum, 0, cmdNum.length);
                mOut.write(len, 0, len.length);
                mOut.write(clientType, 0, clientType.length);
                mOut.write(url, 0, url.length);*/
                Log.d(TAG, "Cmd1: " + Arrays.toString(toSend));
                mOut.write(toSend, 0, toSend.length);
                mOut.flush();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send cmd1, IOException caught");
                e.printStackTrace();
            }
            
            Log.i(TAG, "Successed to send Cmd1");
            mCmd1SendTime = System.currentTimeMillis();
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
                    int ret = handleRecvData(mRecvBuff);
                    
                    if (ret != 0) {
						Log.e(TAG, "Failed to receive Cmd2");
						mIsStop = true;
					}
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to receive Cmd2 from server");
                e.printStackTrace();
                mIsStop = true;
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
            Log.d(TAG, "Receive Cmd2: " + Arrays.toString(data));
            int size = data.length;
            if (size == 0) {
                Log.e(TAG, "Receiving data size is 0");
                return 1;
            }
            ByteBuffer buff = ByteBuffer.wrap(data);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            if (data[0] != 0x82) {
                Log.e(TAG, "The prefix was: " + data[0] + ", was not 0x82");
                return 1;
            }
            
            short cmdNum = buff.getShort(1);
            if (cmdNum != 2) {
                Log.e(TAG, "Cmd num was " + cmdNum + ", " + "was not 2");
                return 1;
            }
            
            int cmdLen = buff.getInt(3);
            int urlLen = buff.getInt(7);
            
            if (urlLen == 0 || (11+urlLen+4) != size) {
                Log.e(TAG, "The url of feedback was empty");
                return 1;
            }
            
            byte[] url = new byte[urlLen];
            buff.get(url, 11, urlLen);
            String parsedUrl = new String(url, 0, urlLen);
            int port = buff.getInt(11 + urlLen);
            Log.i(TAG, "Success to receive Cmd2 <" + parsedUrl + " : " + port + ">");
            
            //send message
            handleRecvMessage(cmdNum, parsedUrl, port);
            
            return 0;
        }
        
        
        private void handleRecvMessage(int cmdNum, String url, int port) {
        	Message message = new Message();
        	switch (cmdNum) {
			case 2:
				Log.i(TAG, "HandleMessage: Cmd2 received, send to TcpClient to handle");
				message.what = MessageTypes.PORT2_RECEIVED;
				message.obj = url;
				message.arg1 = port;
				if (mHandler != null) {
					mHandler.sendMessage(message);
				}
				break;

			default:
				break;
			}
        }

        @Override
        public void run() {
            while (!mIsStop) {
            	if (System.currentTimeMillis() - mCmd1SendTime > mTimeoutRecv) {
            		Log.e(TAG, "Failed to Receive Cmd2: Timeout");
					break;
				}
            	
                read();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            shutdown();
        }
        
        public synchronized void start() {
        	Thread listenThread = new Thread(this);
        	listenThread.start();
        }
        
        public synchronized void stop() {
            mIsStop = true;
        }
    }
    
    
    public class StreamingClient implements Runnable {
        private boolean mIsStop = false;
        private Handler mHandler = null;
        private long mCmd3SendTime = System.currentTimeMillis();
        
        public void setHandler(Handler handler) {
            this.mHandler = handler;
        }
        
        public boolean connect() {
            return TcpClient.this.connect(mServerPort2);
        }

        @Override
        public void run() {
        	while (!mIsStop) {
            	if (System.currentTimeMillis() - mCmd3SendTime > mTimeoutRecv) {
            		Log.e(TAG, "Failed to Receive Cmd4: Timeout");
					break;
				}
            	
                read();
                
                //TODO:if allowed to send stream, send it from buffer
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            shutdown();
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
                    int ret = handleRecvData(mRecvBuff);
                    
                    if (ret != 0) {
						Log.e(TAG, "Failed to receive Cmd");
						shutdown();
					}
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to receive Cmd from server");
                e.printStackTrace();
            }
        }
        
        
        private int handleRecvData(byte[] data) {
        	int ret = 0;
        	
        	int size = data.length;
            if (size == 0) {
                Log.e(TAG, "Receiving data size is 0");
                return 1;
            }
            ByteBuffer buff = ByteBuffer.wrap(data);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            if (data[0] != 0x82) {
                return 1;
            }
            
            short cmdNum = buff.getShort(1);
            
            switch (cmdNum) {
			case 4:
				ret = handleRecvCmd4(buff, size);
				break;
				
			case 6:
				ret = handleRecvCmd6(buff, size);
				break;

			default:
				Log.e(TAG, "Unknown Cmd type");
				ret = 1;
				break;
			}
            
        	return ret;
        }
        
        
        /**
         * Is allowed to send stream data
         * @param buff
         * Cmd4:
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=4(2Byte) |  Len(4Byte)   |  Accessable=0(1Byte)  |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * @return 0-success 1-received an error data or unknown cmd
         */
        private int handleRecvCmd4(ByteBuffer buff, int size) {
        	int cmdLen = buff.getInt(3);
        	if (cmdLen != size) {
                return 1;
            }
        	
        	byte access = buff.get(7);
        	
        	//send message
        	Message message = new Message();
        	message.what = MessageTypes.STREAMING_ALLOWED;
        	message.arg1 = access;
        	
        	if (mHandler != null) {
        	    mHandler.sendMessage(message);
            }
        	
        	return 0;
        }
        
        
        /**
         * Heartbeat info
         * @param buff
         * Cmd6:
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=6(2Byte) |  Len(4Byte)   |    string   |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * @return 0-success 1-received an error data or unknown cmd
         */
        private int handleRecvCmd6(ByteBuffer buff, int size) {
        	int cmdLen = buff.getInt(3);
        	if (cmdLen != size) {
                return 1;
            }
        	
        	int strLen = buff.getInt(7);
            
            if (strLen == 0) {
                return 1;
            }
            
            byte[] strInfo = new byte[strLen];
            buff.get(strInfo, 11, strLen);
            String parsedStr = new String(strInfo, 0, strLen);
        	
        	//send message
        	Message message = new Message();
        	message.what = MessageTypes.HEARTBEAT_RECEIVED;
        	message.obj = parsedStr;
        	if (mHandler != null) {
                mHandler.sendMessage(message);
            }
        	return 0;
        }
        
        
        /**
         * Cmd3:
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=3(2Byte) |  Len(4Byte)   |  ClientType=0(1Byte)  |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        public void sendCmd3() {
        	byte[] prefix = {(byte) 0x82};
            byte[] cmdNum = new byte[2];
            ByteBuffer buff = ByteBuffer.wrap(cmdNum);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putShort((short) 3);
            byte[] len = new byte[4];
            buff = ByteBuffer.wrap(len);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt((int) 1);
            byte[] clientType = {(byte)0x00};
            
            //send
            try {
                mOut.write(prefix, 0, prefix.length);
                mOut.write(cmdNum, 0, cmdNum.length);
                mOut.write(len, 0, len.length);
                mOut.write(clientType, 0, clientType.length);
                mOut.flush();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send cmd3, IOException caught");
                e.printStackTrace();
            }
            
            Log.i(TAG, "Successed to send Cmd3");
            mCmd3SendTime = System.currentTimeMillis();
        }
        
        
        /**
         * send H.264 payload
         * @param data
         * Cmd5:
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         * |Prefix=0x82(1Byte) |  Num=5(2Byte) |  Len(4Byte)   |  Timestamp(4Byte) | PayloadLen(4Byte) |  H.264 payload  |
         * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */
        public void sendCmd5(byte[] data, int timestamp) {
            byte[] prefix = {(byte) 0x82};
            
            byte[] cmdNum = new byte[2];
            ByteBuffer buff = ByteBuffer.wrap(cmdNum);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putShort((short) 5);
            
            byte[] len = new byte[4];
            buff = ByteBuffer.wrap(len);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt((int) (4 + 4 + data.length));
            
            byte[] timeStamp = new byte[4];
            buff = ByteBuffer.wrap(timeStamp);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt(timestamp);
            
            byte[] payloadLen = new byte[4];
            buff = ByteBuffer.wrap(payloadLen);
            buff.order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt(data.length);
            
            
            //send
            try {
                mOut.write(prefix, 0, prefix.length);
                mOut.write(cmdNum, 0, cmdNum.length);
                mOut.write(len, 0, len.length);
                mOut.write(timeStamp, 0, timeStamp.length);
                mOut.write(payloadLen, 0, payloadLen.length);
                mOut.write(data, 0, data.length);
                mOut.flush();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send cmd3, IOException caught");
                e.printStackTrace();
            }
            
            Log.i(TAG, "Successed to send Cmd3");
        }
        
        public synchronized void start() {
        	Thread listenThread = new Thread(this);
        	listenThread.start();
        }
        
        public synchronized void stop() {
            mIsStop = true;
        }
    }
}
