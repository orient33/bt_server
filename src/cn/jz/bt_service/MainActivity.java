package cn.jz.bt_service;

import java.io.IOException;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
	static final int OVER = 11;	//socket关闭,线程结束
	BluetoothAdapter mBluetoothAdapter;
	AcceptThread mTask;
	Button mStop;
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
		mStop = (Button)findViewById(R.id.stop);
		mStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				mTask.stopService();
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
				Log.i("sw2df", "hand msg :"+msg.what);
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
				case OVER:
					mStateView.setText("关闭监听... closed");
					Toast.makeText(MainActivity.this, "Thread over", 1).show();
					break;
				}
			}
			
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		mTask = new AcceptThread();
		mTask.start();
	}

	@Override
	protected void onDestroy() {
		if (mTask != null)
			mTask.stopService();
		super.onDestroy();
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
		protected AcceptThread() {
			// Use a temporary object that is later assigned to serverSocket,
			// because serverSocket is final		
			//mHandler.sendEmptyMessage(0);
			runing = true;
		}

		public void run() {
			
			BluetoothSocket socket = null;
			
			while (runing) {
				
				BluetoothServerSocket serverSocket = null;
				
				try {
					// MY_UUID is the app's UUID string, also used by the client
					serverSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
							"aa", UUID.fromString(MY_UUID));
				} catch (IOException e) {
					display(126,e.toString());
					break;
				}				 
				//serverSocket must is not null.
				try {
					mHandler.sendEmptyMessage(LISTENING);
					socket = serverSocket.accept();// 阻塞于此					
				} catch (IOException e) {
					display(134,e.toString());
					break;
				} finally {
					//It is important .
					try {
					    serverSocket.close();
					} catch (IOException e) {
						display(141,e.toString());
						break;
					}
				}				
				// socket is not null.
				mHandler.sendEmptyMessage(SUCCESS);
				try {
				   socket.close();
				} catch (IOException e) {
				   display(154,e.toString());
				   break;
				}

			}
			mHandler.sendEmptyMessage(OVER);
			loge("thread over================");
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void stopService() {
			runing = false;	
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
