package cn.jz.bt_service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	static String MY_UUID = "54ceb2f6-856e-4d17-9e5b-ee5afb474de8";
    static String TAG = "sw2df";
	static final int SUCCESS = 1;	//连接成功
	static final int FAILED = 9;	//发生异常
	static final int LISTENING = 10; //监听中
	static final int Reading = 13;
	static final int SHOW_IP_PORT=12;
	static final int OVER = 11;	//socket关闭,线程结束

    private static int TCP_DEBUG_PORT = 49761;
	private static final int Uuid = 1, Chanel = 2, TCP = 3;
	private int useMethod = 3;
    
	private boolean SECURE_CONNECT = false;

    private AlertDialog mDialog;
	BluetoothAdapter mBluetoothAdapter;
	AcceptThread mTask;
	TcpAcceptThread mTcpTask;
	Button mStop,mStart,mMethod;
	TextView mSuccessCount;
	TextView mStateView,mLogView;
	Handler mHandler;
	int mCount;
	
	private Class<?> mSystemProperties;
	private Method mGet;
	boolean mIsGateway;// true if on phone, false if on watch
	String get(String key){
		String value="";
		try {
			if (mSystemProperties == null) {
				mSystemProperties = Class.forName("android.os.SystemProperties");
				mGet = mSystemProperties.getDeclaredMethod("get", String.class);
			}
			value = (String) mGet.invoke(mSystemProperties, key);
		} catch (Exception e) {

		}
		return value;
	}
	
	String getIP(){
		String key="dhcp.bt-pan.ipaddress";
		if(mIsGateway)
			key="dhcp.bt-pan.gateway";
		return get(key);
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if("Ingenic".equalsIgnoreCase(get("ro.product.brand"))
				||"s2122b".equalsIgnoreCase(get("ro.product.device"))){
			mIsGateway = false;
		}else 
			mIsGateway = true;
		setContentView(R.layout.activity_main);
		mSuccessCount = (TextView) findViewById(R.id.success);
		mStateView = (TextView) findViewById(R.id.state);
		mLogView = (TextView)findViewById(R.id.log);
		mMethod=(Button)findViewById(R.id.method);
		mStart=(Button)findViewById(R.id.start);
		mStop = (Button)findViewById(R.id.stop);
		mStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				if (mTask != null){
					mTask.stopService();
					mTask=null;
				}
				if(mTcpTask != null){
					mTcpTask.stopService();
					mTcpTask=null;
				}
				finish();
			}
		});
		mStart.setOnClickListener(new View.OnClickListener(){
			public void onClick(View arg0) {
				if ((mTask != null && mTask.isAlive())
						|| (mTcpTask != null && mTcpTask.isAlive())) {
					Toast.makeText(MainActivity.this, "监听中...", 0).show();
					return;
				}
				if (useMethod == Uuid || useMethod == Chanel) {
					mTask = new AcceptThread();
					mTask.start();
				} else {
					mTcpTask = new TcpAcceptThread();
					mTcpTask.start();
				}
			}});
		mMethod.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				switchMethod();
			}
		});
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			// Device does not support Bluetooth
			Toast.makeText(this, "设备不支持蓝牙", 1).show();
			finish();
		}

		if (!mBluetoothAdapter.isEnabled()) {
			Toast.makeText(this, "请打开蓝牙", 1).show();
			finish();
		}
		mCount = 0;
		mHandler = new Handler(){
			public void handleMessage(Message msg) {
				switch(msg.what){
				case 0:
					mStateView.setText("初始化...Init");
					mSuccessCount.setText(" 0");
					break;
				case SUCCESS:
					mSuccessCount.setText(" " + mCount);
					break;
				case FAILED:
					mLogView.setText(msg.arg1+"}"+msg.obj.toString());
					break;
				case LISTENING:
					mStateView.setText("监听中... listening");
					break;
				case Reading:
					mStateView.setText("accept() OK. read()...");
					break;
				case OVER:
					mStateView.setText("关闭监听... closed");
					Toast.makeText(MainActivity.this, "Thread over", 1).show();
					break;
				case SHOW_IP_PORT:
					if(!mIsGateway)
						showMyDialog(msg.obj.toString(),msg.arg1);
					break;
				}
			}
			
		};
		refreshMethod();
	}

	
	private void switchMethod(){
		if ((mTask != null && mTask.isAlive())
				|| (mTcpTask != null && mTcpTask.isAlive())) {
			Toast.makeText(this, "监听中...无法切换", 0).show();
			return;
		}
		if (useMethod == Uuid)
			useMethod = Chanel;
		else if (useMethod == Chanel)
			useMethod = TCP;
		else
			useMethod = Uuid;
		refreshMethod();
	}
	private void refreshMethod(){
		if (useMethod == Chanel)
			mMethod.setText(" Chanel ");
		else if (useMethod == Uuid)
			mMethod.setText(" UUID ");
		else if (useMethod == TCP)
			mMethod.setText(" TCP ");
	}
	
	void showMyDialog(String ip,int port){
		if(mDialog==null){
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			mDialog=b.create();
		}
		mDialog.setMessage("地址: "+ip/*+"\n"+port*/);
		mDialog.show();
	}
	
	private boolean mExit = false;
	public void onBackPressed() {
		if (((mTask != null && mTask.isAlive()) ||
				(mTcpTask != null && mTcpTask.isAlive())) 
			&& !mExit) {
			Toast.makeText(this, "监听中...，再按一下强制退出", 0).show();
			mExit = true;
		} else{
			super.onBackPressed();
		}
	}
	@Override
	protected void onDestroy() {
		if (mDialog != null) {
			mDialog.dismiss();
		}
		if (mTask != null){
			mTask.stopService();
			mTask=null;
		}
		if(mTcpTask!=null){
			mTcpTask.stopService();
			mTcpTask=null;
		}
		super.onDestroy();
		Process.killProcess(Process.myPid());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private class AcceptThread extends Thread {
		private volatile boolean runing;
		BluetoothServerSocket serverSocket = null;
		protected AcceptThread() {
			//mHandler.sendEmptyMessage(0);
			runing = true;
		}

		public void run() {

			BluetoothSocket socket = null;
			Log.d("sw2df", "{server} SECURE_CONNECT is "+ SECURE_CONNECT);
			try {
				if (useMethod==Chanel) {
					Method m = BluetoothAdapter.class.getMethod(
							"listenUsingRfcommOn", int.class);
					serverSocket = (BluetoothServerSocket) m.invoke(
							mBluetoothAdapter, 13);
				} else if(useMethod == Uuid){
					// MY_UUID is the app's UUID string, also used by the client
					// SDK 10 is Android 2.3.3
					if (!SECURE_CONNECT
							&& android.os.Build.VERSION.SDK_INT >= 10) {
						serverSocket = mBluetoothAdapter
								.listenUsingInsecureRfcommWithServiceRecord(
										"aa", UUID.fromString(MY_UUID));
					} else {
						if (!SECURE_CONNECT
								&& android.os.Build.VERSION.SDK_INT < 10) {
							loge("it is not a secure_connect , but SDK Level < 10, and then run secure connect");
						}
						serverSocket = mBluetoothAdapter
								.listenUsingRfcommWithServiceRecord("aa",
										UUID.fromString(MY_UUID));
					}
				}else
					return;
			} catch (Exception e) {
				loge("listenUsingRfcomm...."+e.toString());
				display(126,e.toString());
			}				 
			while (runing) {
				//serverSocket must is not null.
				try {
					mHandler.sendEmptyMessage(LISTENING);
					socket = serverSocket.accept();// 阻塞于此
					try{
						socket.getOutputStream().write(++mCount);
					}catch(IOException e){
						loge("client socket write error."+e.getMessage());
						display(132,e.getMessage());
					}
					try{
						mHandler.sendEmptyMessage(Reading);
						socket.getInputStream().read();
					}catch(IOException e){
						loge("client socket read error."+e.getMessage());
						display(133,e.getMessage());
					}
				} catch (IOException e) {
					loge("accept()..."+e.toString());
					display(134,e.toString());
					break;
				}
				// socket is not null.
				mHandler.sendEmptyMessage(SUCCESS);
				try {
					socket.close();
				} catch (IOException e) {
					loge("close() " + e.toString());
					display(154, e.toString());
					break;
				} finally {
					socket = null;
				}
			}
			mHandler.sendEmptyMessage(OVER);
			loge("thread over================");
		}

		synchronized private boolean listenSocketClose(){
			if(serverSocket==null)
				return true;
			try {
			    serverSocket.close();
			} catch (IOException e) {
				loge("close() "+e.toString());
				display(141,e.toString());
				return false;
			} finally{
				serverSocket = null;
			}
			return true;
		}
		
		/** Will cancel the listening socket, and cause the thread to finish */
		public void stopService() {
			runing = false;
			Log.d("sw2df","stopService() activly, not an Exception");
			if(serverSocket!=null){
				listenSocketClose();
			}
		}
	}

	public void display(int msgtype, String e) {
		Message msg=mHandler.obtainMessage(FAILED, msgtype, 0, e.toString());
		msg.sendToTarget();
	}
	void loge(String s) {
		Log.e("sw2df", s);
	}
	void logd(String s) {
		Log.d("sw2df", s);
	}

	private class TcpAcceptThread extends Thread {
		//private BluetoothServerSocket serverSocket;
		private volatile boolean runing;
        private ServerSocket mTcpServerSocket = null;
		protected TcpAcceptThread() {
			runing = true;
		}

		public void run() {

			Socket mTcpConnectSocket = null;
			try {
				Log.d(TAG, "Create TCP ServerSocket");
				mTcpServerSocket = new ServerSocket(TCP_DEBUG_PORT, 1);
				int port = mTcpServerSocket.getLocalPort();
				String ip = getIP();
				Message msg = mHandler.obtainMessage(SHOW_IP_PORT, port,0,ip);
				logd("ip="+ip+",port = "+port);
				msg.sendToTarget();
			} catch (IOException e) {
				loge("create socket..e.." + e.toString());
				display(3180, e.toString());
			}
			while (runing) {
								 
				//serverSocket must is not null.
				try {
					mHandler.sendEmptyMessage(LISTENING);
					logd("tcpServerSocket start accept");
                    mTcpConnectSocket = mTcpServerSocket.accept();
                    logd("local Address: "+mTcpConnectSocket.getLocalAddress().toString());
                    mTcpConnectSocket.getOutputStream().write(++mCount);
                    logd("finish accept ok, then write data to socket.");
    				
    				// socket is not null.
    				mHandler.sendEmptyMessage(SUCCESS);
    				try {
                        mTcpConnectSocket.close();
    				} catch (IOException e) {
    					loge("close() " + e.toString());
    					display(3360, e.toString());
    					break;
    				} finally {
                        mTcpConnectSocket = null;
    				}
				} catch (IOException e) {
					loge("accept()..."+e.toString());
					display(3430,e.toString());
					break;
				} finally {
					//It is important .
//					if(!listenSocketClose())
//						break;
				}
			}
			mHandler.sendEmptyMessage(OVER);
			logd("thread over================");
		}

		synchronized private boolean listenSocketClose(){
			if(mTcpServerSocket == null)
				return true;
			try {
			    mTcpServerSocket.close();
			} catch (IOException e) {
				loge("close() "+e.toString());
				display(3610,e.toString());
				return false;
			} finally{
				mTcpServerSocket = null;
			}
			return true;
		}
		
		/** Will cancel the listening socket, and cause the thread to finish */
		public void stopService() {
			runing = false;
			logd("stopService() activly, not an Exception");
		    listenSocketClose();
		}
	}
}
