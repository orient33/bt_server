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
	static final int REQUEST_ENABLE_BT = 1;
	BluetoothAdapter mBluetoothAdapter;
	AcceptTask mTask;
	Button mStop;
	TextView mSuccessCount;
	TextView mState;
	Handler mHandler;
	int mCount;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mSuccessCount = (TextView) findViewById(R.id.success);
		mState = (TextView) findViewById(R.id.state);
		mStop = (Button)findViewById(R.id.stop);
		mStop.setOnClickListener(new View.OnClickListener() {
			public void onClick(View arg0) {
				mTask.cancelListen();
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
					mState.setText("初始化...Init");
					mSuccessCount.setText(" 0");
					break;
				case 1:
					mCount++;
					mSuccessCount.setText(" " + mCount);
					break;
				case 10:
					mState.setText("监听中... listening");
					break;
				case 11:
					mState.setText("关闭监听... closed");
					break;
				}
			}
			
		};
	}

	@Override
	protected void onResume() {
		super.onResume();
		mTask = new AcceptTask();
		mTask.start();
	}

	@Override
	protected void onDestroy() {
		if (mTask != null)
			mTask.cancelListen();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	class AcceptTask extends Thread {
		private BluetoothServerSocket mmServerSocket;
		boolean runing;
		protected AcceptTask() {
			// Use a temporary object that is later assigned to mmServerSocket,
			// because mmServerSocket is final
			BluetoothServerSocket tmp = null;
			mHandler.sendEmptyMessage(0);
			try {
				// MY_UUID is the app's UUID string, also used by the client
				tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
						"aa", UUID.fromString(MY_UUID));
			} catch (IOException e) {
				loge("81]" + e.toString());
			}
			mmServerSocket = tmp;
			runing = true;
		}

		public void run() {
			BluetoothSocket socket = null;
			while (runing) {
				try {
					mHandler.sendEmptyMessage(10);
					socket = mmServerSocket.accept();// 阻塞于此
				} catch (IOException e) {
					loge("97]" + e.toString());
					break;
				}
				// If a connection was accepted.
				if (socket != null) {// 连接成功一次
					mHandler.sendEmptyMessage(1);
					try {
						socket.close();
					} catch (IOException e) {
						loge("111]" + e.toString());
					}
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancelListen() {
			runing = false;
			try {
				mHandler.sendEmptyMessage(11);
				mmServerSocket.close();
			} catch (IOException e) {
				loge("135]" + e.toString());
			}
		}
	}

	void loge(String s) {
		Log.e("sw2df", s);
	}
}
