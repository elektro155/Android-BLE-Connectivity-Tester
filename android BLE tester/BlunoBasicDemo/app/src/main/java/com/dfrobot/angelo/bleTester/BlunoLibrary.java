package com.dfrobot.angelo.bleTester;

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.IBinder;
import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

public abstract class BlunoLibrary  extends AppCompatActivity{

	private Context mainContext=this;
	private SavedSettings settings;

	public abstract void onConectionStateChange(connectionStateEnum theconnectionStateEnum);
	public abstract void onSerialReceived(String theString);
	public abstract void passTestingResults(TestResult result);

	///////////////////////////////////testing variables///////////////////////////////////////////
	public enum TestResult{none, success, dataFailure, connectionFailure};

	private String trigger_saved = "";
	private String message_saved = "";
	private String response_saved = "";
	private boolean testingWithTrigger = true;
	private int messageCount = 0;

	private final int DISCONNECT_AFTER_TEST_DELAY_MS = 1500;  //disconnect delay
	private final int NO_TRIGGER_SENDING_DELAY_MS = 900;      //time between establishing of connection and sending data (only fo no trigger mode)
	public  final int WAITING_FOR_NEW_CONNECTION_MS = 2000;   //delay after that the new connection will be established

	///////////////////////////////////////////////////////////////////////////////////////////////

	public void serialSend(String theString){
		if (mConnectionState == connectionStateEnum.isConnected) {
			mCommandCharacteristic.setValue(theString);
			mBluetoothLeService.writeCharacteristic(mCommandCharacteristic);
			onSerialReceived("SENT:"+theString);
		}
	}

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
	}

	private static BluetoothGattCharacteristic mSCharacteristic, mSerialPortCharacteristic, mCommandCharacteristic;
	BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private LeDeviceListAdapter mLeDeviceListAdapter=null;
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning =false;
	AlertDialog mScanDeviceDialog;
	private String mDeviceName;
	private String mDeviceAddress;
	public enum connectionStateEnum{isNull, isScanning, isToScan, isConnecting , isConnected, isDisconnecting}
	public connectionStateEnum mConnectionState = connectionStateEnum.isNull;
	private static final int REQUEST_ENABLE_BT = 1;

	protected String deviceAdressToSave=null; //string for saving address of chosen device

	private Handler mHandler= new Handler();

	public boolean mConnected = false;
	private boolean isScanningForDevice = false;

	private final static String TAG = BlunoLibrary.class.getSimpleName();

	private Runnable mConnectingOverTimeRunnable=new Runnable(){

		@Override
		public void run() {
			if(mConnectionState==connectionStateEnum.isConnecting)
				mConnectionState=connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}};

	private Runnable mDisonnectingOverTimeRunnable=new Runnable(){

		@Override
		public void run() {
			if(mConnectionState==connectionStateEnum.isDisconnecting)
				mConnectionState=connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}};

	public static final String SerialPortUUID="6e400003-b5a3-f393-e0a9-e50e24dcca9e";  //read
	public static final String CommandUUID="6e400002-b5a3-f393-e0a9-e50e24dcca9e"; //write

	////////////////////////////////////////scanning without list//////////////////////////////////////////////

	public void onClickInActivity(){
		//update strings used to compare with received data
		trigger_saved = settings.getTrigger();
		message_saved = settings.getMessage();
		response_saved = settings.getResponse();
		testingWithTrigger = settings.getEnableTrigger();

		isScanningForDevice = false;
		scanLeDevice(true); //it must be here to provide proper connection
		if(deviceAdressToSave==null&&settings.getDeviceAddress().length()>0) { //get address if there was something saved and app started
			deviceAdressToSave = settings.getDeviceAddress(); //it is so that the connection could be established to the saved device
		}
		if(deviceAdressToSave!=null&&(mConnectionState==connectionStateEnum.isToScan||mConnectionState==connectionStateEnum.isNull)) {
			/*start scanning if application is disconnected*/
			scanLeDevice(false); //it must be here to provide proper connection

			if (mBluetoothLeService.connect(settings.getDeviceAddress())) {
				Log.d(TAG, "Connect request success");
				mConnectionState = connectionStateEnum.isConnecting;
				onConectionStateChange(mConnectionState);
				mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
			} else {
				Log.d(TAG, "Connect request fail");
				mConnectionState = connectionStateEnum.isToScan;
				onConectionStateChange(mConnectionState);
			}
		}
		scanLeDevice(false); //it must be here to provide proper connection
		if(deviceAdressToSave==null){
			Toast.makeText(mainContext, "Choose the device", Toast.LENGTH_SHORT).show();
		}
	}

	public void disconnectInActivity(){
		if(mConnectionState==connectionStateEnum.isConnected) {
			mBluetoothLeService.disconnect();
			mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			mConnectionState = connectionStateEnum.isDisconnecting;
			onConectionStateChange(mConnectionState);
		}
	}

	public void disconnectInTest(int delay){
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				disconnectInActivity();
			}
		}, delay);
	}

	public void onCreateProcess()
	{
		settings = SavedSettings.getInstance(this);

		if(!initiate())
		{
			Toast.makeText(mainContext, R.string.error_bluetooth_not_supported,
					Toast.LENGTH_SHORT).show();
			(( AppCompatActivity) mainContext).finish();
		}

		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

		// Initializes list view adapter.
		mLeDeviceListAdapter = new LeDeviceListAdapter();
		// Initializes and show the scan Device Dialog
		mScanDeviceDialog = new AlertDialog.Builder(mainContext)
				.setTitle("BLE Device Scan...").setAdapter(mLeDeviceListAdapter, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						final BluetoothDevice device = mLeDeviceListAdapter.getDevice(which);
						if (device == null)
							return;
						scanLeDevice(false);

						if(device.getName()==null || device.getAddress()==null)
						{
							mConnectionState=connectionStateEnum.isToScan;
							onConectionStateChange(mConnectionState);
						}
						else{ //if device is ok
							System.out.println("onListItemClick " + device.getName().toString());
							System.out.println("Device Name:"+device.getName() + "   " + "Device Name:" + device.getAddress());

							mDeviceName=device.getName().toString();
							mDeviceAddress=device.getAddress().toString();

							//save device name and address to saved settings
							settings.setDeviceName(mDeviceName);  //saving name to shared preferences
							settings.setDeviceAddress(mDeviceAddress); //saving address

							if (mBluetoothLeService.connect(mDeviceAddress)) {
								Log.d(TAG, "Connect request success");
								mConnectionState=connectionStateEnum.isConnecting;
								onConectionStateChange(mConnectionState);
								mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
							}
							else {
								Log.d(TAG, "Connect request fail");
								mConnectionState=connectionStateEnum.isToScan;
								onConectionStateChange(mConnectionState);
							}
						}
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {

					@Override
					public void onCancel(DialogInterface arg0) {
						System.out.println("mBluetoothAdapter.stopLeScan");

						mConnectionState = connectionStateEnum.isToScan;
						onConectionStateChange(mConnectionState);
						mScanDeviceDialog.dismiss();

						scanLeDevice(false);
					}
				}).create();

	}

	public void onResumeProcess() {
		System.out.println("BlUNOActivity onResume");
		if (!mBluetoothAdapter.isEnabled()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				((AppCompatActivity) mainContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}
		mainContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}


	public void onPauseProcess() {
		System.out.println("BLUNOActivity onPause");
		scanLeDevice(false);
		mainContext.unregisterReceiver(mGattUpdateReceiver);
		mLeDeviceListAdapter.clear();
		mConnectionState=connectionStateEnum.isToScan;
		onConectionStateChange(mConnectionState);
		mScanDeviceDialog.dismiss();
		if(mBluetoothLeService!=null)
		{
			mBluetoothLeService.disconnect();
			mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
		}
		mSCharacteristic=null;
	}


	public void onStopProcess() {
		System.out.println("MiUnoActivity onStop");
		if(mBluetoothLeService!=null)
		{
			mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
			mBluetoothLeService.close();
		}
		mSCharacteristic=null;
	}

	public void onDestroyProcess() {
		mainContext.unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	public void onActivityResultProcess(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode ==  AppCompatActivity.RESULT_CANCELED) {
			(( AppCompatActivity) mainContext).finish();
			return;
		}
	}

	boolean initiate()
	{
		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!mainContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			return false;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mainContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			return false;
		}
		return true;
	}

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			System.out.println("mGattUpdateReceiver->onReceive->action="+action);
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				onSerialReceived("CONNECTED");
				mHandler.removeCallbacks(mConnectingOverTimeRunnable);
				messageCount = 0; //clear flag here before counting messages

				//set here failure in case no data will be received
				//if data will be received, the failure will be overwritten
				passTestingResults(TestResult.connectionFailure);

				//if no trigger needed for the test, wait 900 ms before sending message
				//else send message in response to trigger in BluetoothLeService.ACTION_DATA_AVAILABLE
				if(!testingWithTrigger)
				{
					new Handler().postDelayed(new Runnable() {
						@Override
						public void run() {
							serialSend(message_saved);
						}
					}, NO_TRIGGER_SENDING_DELAY_MS); //handler for waiting some time (900 ms)
				}

			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				mConnected = false;
				onSerialReceived("DISCONNECTED");
				mConnectionState = connectionStateEnum.isToScan;
				onConectionStateChange(mConnectionState);
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				mBluetoothLeService.close();

			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
					System.out.println("ACTION_GATT_SERVICES_DISCOVERED  "+
							gattService.getUuid().toString());
				}
				getGattServices(mBluetoothLeService.getSupportedGattServices());

			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				//message from UART characteristic
				if (mSCharacteristic==mSerialPortCharacteristic && isScanningForDevice == false)
				{
					++messageCount;
					if(testingWithTrigger && messageCount == 1) {
						//get first message
						String trigger = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
						onSerialReceived("READ:" + trigger);

						//compare trigger with pattern
						if (trigger.equals(trigger_saved)) {
							//ok trigger, send message
							onSerialReceived("OK: trigger match");
							serialSend(message_saved);
						} else {
							//wrong trigger
							onSerialReceived("ERROR: trigger mismatch");
							passTestingResults(TestResult.dataFailure);
							disconnectInTest(DISCONNECT_AFTER_TEST_DELAY_MS);
						}
					}
					else if(
							(!testingWithTrigger && messageCount == 1)  ||  //response for no trigger
							(testingWithTrigger && messageCount == 2)  //response for message with trigger
					)
					{
						String response = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
						onSerialReceived("READ:" + response);

						//compare response here
						if(response.equals(response_saved))
						{
							//ok
							onSerialReceived("OK: response match");
							passTestingResults(TestResult.success);
						}
						else
						{
							//not ok
							onSerialReceived("ERROR: response mismatch");
							passTestingResults(TestResult.dataFailure);
						}
						disconnectInTest(DISCONNECT_AFTER_TEST_DELAY_MS);
					}
					else
					{
						//unexpected message
						String unexpected = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
						onSerialReceived("READ:" + unexpected);
						onSerialReceived("ERROR: unexpected message");
						passTestingResults(TestResult.dataFailure);
						disconnectInTest(DISCONNECT_AFTER_TEST_DELAY_MS);
					}
				}
				System.out.println("displayData "+intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
			}
		}
	};

	void buttonScanOnClickProcess()
	{
		isScanningForDevice = true;
		switch (mConnectionState) {
			case isNull:
				mConnectionState=connectionStateEnum.isScanning;
				onConectionStateChange(mConnectionState);
				scanLeDevice(true);
				mScanDeviceDialog.show();
				break;

			case isToScan:
				mConnectionState=connectionStateEnum.isScanning;
				onConectionStateChange(mConnectionState);
				scanLeDevice(true);
				mScanDeviceDialog.show();
				break;

			case isScanning:
				break;

			case isConnecting:
				mBluetoothLeService.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
				mConnectionState=connectionStateEnum.isDisconnecting;
				onConectionStateChange(mConnectionState);
				break;

			case isConnected:
				mBluetoothLeService.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
				mConnectionState=connectionStateEnum.isDisconnecting;
				onConectionStateChange(mConnectionState);
				break;

			case isDisconnecting:
				break;

			default:
				break;
		}
	}

	void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			System.out.println("mBluetoothAdapter.startLeScan");

			if(mLeDeviceListAdapter != null)
			{
				mLeDeviceListAdapter.clear();
				mLeDeviceListAdapter.notifyDataSetChanged();
			}
			if(!mScanning)
			{
				mScanning = true;
				mBluetoothAdapter.startLeScan(mLeScanCallback);
			}
		} else {
			if(mScanning)
			{
				mScanning = false;
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
			}
		}
	}

	// Code to manage Service lifecycle.
	ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			System.out.println("mServiceConnection onServiceConnected");
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				(( AppCompatActivity) mainContext).finish();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			System.out.println("mServiceConnection onServiceDisconnected");
			mBluetoothLeService = null;
		}
	};

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
							 byte[] scanRecord) {
			(( AppCompatActivity) mainContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					System.out.println("mLeScanCallback onLeScan run ");
					mLeDeviceListAdapter.addDevice(device);
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	private void getGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null) return;
		String uuid = null;
		mSerialPortCharacteristic=null;
		mCommandCharacteristic=null;
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			System.out.println("displayGattServices + uuid="+uuid);

			List<BluetoothGattCharacteristic> gattCharacteristics =
					gattService.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas =
					new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				charas.add(gattCharacteristic);
				uuid = gattCharacteristic.getUuid().toString();
				if(uuid.equals(SerialPortUUID)){
					mSerialPortCharacteristic = gattCharacteristic;
					System.out.println("mSerialPortCharacteristic  "+mSerialPortCharacteristic.getUuid().toString());
				}
				else if(uuid.equals(CommandUUID)){
					mCommandCharacteristic = gattCharacteristic;
					System.out.println("mCommandCharacteristic  "+mCommandCharacteristic.getUuid().toString());
				}
			}
			mGattCharacteristics.add(charas);
		}

		if (mSerialPortCharacteristic==null || mCommandCharacteristic==null) {
			Toast.makeText(mainContext, "Please select nRF51 devices",Toast.LENGTH_SHORT).show();
			//disconnect
			mBluetoothLeService.disconnect();
			mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			mConnectionState=connectionStateEnum.isDisconnecting;
			onConectionStateChange(mConnectionState);
		}
		else { //there was chosen appropriate device
			//set characteristic notification
			mSCharacteristic=mSerialPortCharacteristic;
			mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
			if(mConnected){ //change state if device is connected and
				mConnectionState = connectionStateEnum.isConnected;
				onConectionStateChange(mConnectionState);
			}else{ //disconnect if something went wrong, should never happen
				mBluetoothLeService.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
				mConnectionState=connectionStateEnum.isDisconnecting;
				onConectionStateChange(mConnectionState);
			}
		}
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<BluetoothDevice> mLeDevices;
		private LayoutInflater mInflator;

		public LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<BluetoothDevice>();
			mInflator =  (( AppCompatActivity) mainContext).getLayoutInflater();
		}

		public void addDevice(BluetoothDevice device) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device);
			}
		}

		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position);
		}

		public void clear() {
			mLeDevices.clear();
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = (TextView) view
						.findViewById(R.id.device_address);
				viewHolder.deviceName = (TextView) view
						.findViewById(R.id.device_name);
				System.out.println("mInflator.inflate  getView");
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = mLeDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());

			return view;

		}

	}

}
