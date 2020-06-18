package com.dfrobot.angelo.bleTester;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class MainActivity  extends BlunoLibrary {

    private Button buttonScan;
    private Button buttonSerialSend;
    private Button buttonClearLog;
    private TextView serialReceivedText;
    private TextView deviceData;
    private TextView testResultsTextView;
    private EditText triggerEditText;
    private EditText messageEditText;
    private EditText responseEditText;
    private Switch enableTriggerSwitch;

    private boolean scanning = false;               //flag to indicate that there is connection just to choose device, to disable accidental opening
    private boolean connecting = false;             //true if application is attempting to connect to bluno
    private boolean waitingToDisconnect = false;    //it true, the application is in process of disconnecting

    private SavedSettings settings;
    private Context thisContext = this;
    private DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SS");

    private boolean testsRunning = false;

    //storing tests results
    private int no_of_tests  = 0;
    private int test_success = 0;
    private int test_failure = 0;

    //testing results
    public TestResult testresult = TestResult.none;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        onCreateProcess();

        settings = SavedSettings.getInstance(this);

        buttonSerialSend = (Button) findViewById(R.id.buttonSerialSend);          //initial the button for sending the data
        buttonScan = (Button) findViewById(R.id.buttonScan);                      //initial the button for scanning the BLE device
        buttonClearLog = (Button) findViewById(R.id.buttonClearLog);              //initial the button for clearing activity log

        triggerEditText = (EditText) findViewById(R.id.triggerText);
        messageEditText = (EditText) findViewById(R.id.messageText);
        responseEditText = (EditText) findViewById(R.id.responseText);

        serialReceivedText = (TextView) findViewById(R.id.serialReveicedText);    //initial the EditText of the received data
        deviceData = (TextView) findViewById(R.id.deviceData);                    //data of connected device to be displayed here
        testResultsTextView = (TextView) findViewById(R.id.testResultsTextView);

        enableTriggerSwitch = (Switch) findViewById(R.id.switchPassword);

        displayBLEDeviceData();
        no_of_tests  = 0;
        test_success = 0;
        test_failure = 0;
        setTestResultsTextView(test_success, test_failure, no_of_tests);

        /////////////////////////////////////////////button handlers//////////////////////////////////////////////////////////

        buttonSerialSend.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                saveTextFields();

                if(testsRunning){
                    testsRunning = false;
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    buttonSerialSend.setText("START");
                    disconnectInActivity();
                    Toast.makeText(thisContext, "Finishing test, please wait a second", Toast.LENGTH_LONG).show();
                }else{
                    testsRunning = true;
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    buttonSerialSend.setText("STOP");
                    no_of_tests  = 0;
                    test_success = 0;
                    test_failure = 0;
                    setTestResultsTextView(test_success, test_failure, no_of_tests);
                    performTests(); //start tests
                }
            }
        });

        buttonScan.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                waitingToDisconnect = true;         //disable open button
                buttonScanOnClickProcess();         //Alert Dialog for selecting the BLE device
                scanning = true;                    //flag to indicate that scanning for BLE device is on
            }
        });

        buttonClearLog.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                serialReceivedText.setText(null);
            }
        });
    }

    @Override
    protected void onResume(){
        super.onResume();
        onResumeProcess();      //onResume Process by BlunoLibrary
        updateTextFields();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        onActivityResultProcess(requestCode, resultCode, data);		//onActivityResult Process by BlunoLibrary
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        onPauseProcess();
        saveTextFields();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(testsRunning) {
            testsRunning = false;
            no_of_tests  = 0;
            test_success = 0;
            test_failure = 0;
            setTestResultsTextView(test_success, test_failure, no_of_tests);
            buttonSerialSend.setText("START");
            Toast.makeText(thisContext, "Test has been interrupted", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        onStopProcess();	    //onStop Process by BlunoLibrary
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onDestroyProcess();	    //onDestroy Process by BlunoLibrary
    }

    private void performTests(){
        if (!waitingToDisconnect) { //wait until the application disconnects completely
            waitingToDisconnect = true; //disable calling this until the connection is finished
            testresult = TestResult.none;
            onClickInActivity(); //connect if not connected
            scanning = false;
            connecting = true; //app is trying to connect
        }
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {    //Once connection state changes, this function will be called
        switch (theConnectionState) {
            case isConnected:
                buttonScan.setText("Connected");
                displayBLEDeviceData();
                connecting = false; //app has connected to the board successfully
                if (scanning) { // disconnect in case of scanning if the device was found and app connected to it
                    Toast.makeText(thisContext, "Device has been changed", Toast.LENGTH_SHORT).show();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            disconnectInActivity(); //disconnect
                            scanning = false;
                        }
                    },300);
                }
                break;
            case isConnecting:
                buttonScan.setText("Connecting");
                break;
            case isToScan:
                buttonScan.setText("Change Device");
                displayBLEDeviceData();
                if(connecting) { //connecting has failed
                    onSerialReceived("CONNECTING FAILED");
                    testresult = TestResult.connectionFailure;
                }
                waitingToDisconnect = false;
                connecting = false;

                if(testsRunning){
                    processTestResult();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if(testsRunning) {
                                performTests();
                            }
                        }
                    }, WAITING_FOR_NEW_CONNECTION_MS);
                }
                break;
            case isScanning:
                buttonScan.setText("Scanning");
                break;
            case isDisconnecting:
                //buttonScan.setText("isDisconnecting"); //commented to prevent the text flashing due to rapid changes of text
                break;
            default:
                break;
        }
    }

    @Override
    public void onSerialReceived(String theString) {	//Once connection data received, this function will be called
        serialReceivedText.append(dateFormat.format(new Date())+ ": " + theString + "\n");
        ((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    @Override
    public void passTestingResults(TestResult result)
    {
        testresult = result;
    }

    private void processTestResult()
    {
        switch(testresult){
            case success:
                onSerialReceived("RESULT: SUCCESS");
                ++test_success;
                break;
            case dataFailure:
                onSerialReceived("RESULT: DATA FAILURE");
                ++test_failure;
                break;
            case connectionFailure:
                onSerialReceived("RESULT: CONN. FAILURE");
                ++test_failure;
                break;
            case none:
                onSerialReceived("TEST NOT PERFORMED!!!");
                break;
        }
        setTestResultsTextView(test_success, test_failure, ++no_of_tests);
    }

    private void displayBLEDeviceData(){
        String tmp = settings.getDeviceName() + "\n" + settings.getDeviceAddress();
        deviceData.setText(tmp);
    }

    private void updateTextFields(){
        triggerEditText.setText(settings.getTrigger());
        messageEditText.setText(settings.getMessage());
        responseEditText.setText(settings.getResponse());
        enableTriggerSwitch.setChecked(settings.getEnableTrigger());
    }

    private void saveTextFields(){
        settings.setTrigger(triggerEditText.getText().toString());
        settings.setMessage(messageEditText.getText().toString());
        settings.setResponse(responseEditText.getText().toString());
        settings.setEnableTrigger(enableTriggerSwitch.isChecked());
    }

    private void setTestResultsTextView(int success, int failed, int trials){
        String tmp = "TRIALS:      " + trials + "\n" + "SUCCESS: " + success + "\n" + "FAILED:      " + failed;
        testResultsTextView.setText(tmp);
    }
}

