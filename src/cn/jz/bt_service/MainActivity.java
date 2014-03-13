package cn.jz.bt_service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
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
	static final int SUCCESS = 1;	//连接成功
	static final int FAILED = 9;	//发生异常
	static final int LISTENING = 10; //监听中
	static final int Reading = 13;
	static final int OVER = 11;	//socket关闭,线程结束
	private boolean SECURE_CONNECT = false;
	private boolean useChanel = false;
	BluetoothAdapter mBluetoothAdapter;
	AcceptThread mTask;
	Button mStop,mStart,mMethod;
	TextView mSuccessCount;
	TextView mStateView,mLogView;
	Handler mHandler;
	int mCount;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
				finish();
			}
		});
		mStart.setOnClickListener(new View.OnClickListener(){
			public void onClick(View arg0) {
				if(mTask!=null&&mTask.isAlive()){
					Toast.makeText(MainActivity.this, "监听中...", 0).show();
					return;
				}
				mTask = new AcceptThread();
				mTask.start();
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
					mCount++;
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
				}
			}
			
		};
		refreshMethod();
	}
	
	private void switchMethod(){
		if(mTask!=null && mTask.isAlive()){
			Toast.makeText(this, "监听中...无法切换", 0).show();
			return;
		}
		if(useChanel)
			useChanel=false;
		else
			useChanel=true;
		refreshMethod();
	}
	private void refreshMethod(){
		if(useChanel){
			mMethod.setText(" Chanel ");
		}else{
			mMethod.setText(" UUID ");
		}
	}
	
	private boolean mExit = false;
	public void onBackPressed() {
		if (mTask != null && mTask.isAlive()&&!mExit) {
			Toast.makeText(this, "监听中...，再按一下强制退出", 0).show();
			mExit = true;
		} else{
			super.onBackPressed();
		}
	}
	@Override
	protected void onDestroy() {
		if (mTask != null){
			mTask.stopService();
			mTask=null;
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
		//private BluetoothServerSocket serverSocket;
		private volatile boolean runing;
		BluetoothServerSocket serverSocket = null;
		protected AcceptThread() {
			// Use a temporary object that is later assigned to serverSocket,
			// because serverSocket is final		
			//mHandler.sendEmptyMessage(0);
			runing = true;
		}

		public void run() {

			BluetoothSocket socket = null;
			Log.d("sw2df", "{server} SECURE_CONNECT is "+ SECURE_CONNECT);
			try {
				if (useChanel) {
					Method m = BluetoothAdapter.class.getMethod(
							"listenUsingRfcommOn", int.class);
					serverSocket = (BluetoothServerSocket) m.invoke(
							mBluetoothAdapter, 13);
				} else {
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
				}
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
						socket.getOutputStream().write(mCount);
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
		
		public void display(int msgtype, String e) {
			Message msg=mHandler.obtainMessage(FAILED, msgtype, 0, e.toString());
			msg.sendToTarget();
		}
		
	}

	void loge(String s) {
		Log.e("sw2df", s);
	}
}
