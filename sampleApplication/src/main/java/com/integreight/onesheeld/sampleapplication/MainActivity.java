package com.integreight.onesheeld.sampleapplication;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.integreight.onesheeld.sdk.OneSheeldBaudRateQueryCallback;
import com.integreight.onesheeld.sdk.OneSheeldConnectionCallback;
import com.integreight.onesheeld.sdk.OneSheeldDataCallback;
import com.integreight.onesheeld.sdk.OneSheeldDevice;
import com.integreight.onesheeld.sdk.OneSheeldError;
import com.integreight.onesheeld.sdk.OneSheeldErrorCallback;
import com.integreight.onesheeld.sdk.OneSheeldFirmwareUpdateCallback;
import com.integreight.onesheeld.sdk.OneSheeldManager;
import com.integreight.onesheeld.sdk.OneSheeldRenamingCallback;
import com.integreight.onesheeld.sdk.OneSheeldScanningCallback;
import com.integreight.onesheeld.sdk.OneSheeldSdk;
import com.integreight.onesheeld.sdk.OneSheeldTestingCallback;
import com.integreight.onesheeld.sdk.ShieldFrame;
import com.integreight.onesheeld.sdk.SupportedBaudRate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private Handler uiThreadHandler = new Handler();
    private Button connectButton;
    private Button disconnectButton;
    private TextView oneSheeldNameTextView;
    private Spinner pinsSpinner;
    private Spinner baudRateSpinner;
    private Spinner firmwareSpinner;
    private ProgressDialog scanningProgressDialog;
    private ProgressDialog connectionProgressDialog;
    private ProgressDialog firmwareUpdateProgressDialog;
    private LinearLayout oneSheeldLinearLayout;
    private ArrayList<String> connectedDevicesNames;
    private ArrayList<String> scannedDevicesNames;
    private ArrayList<OneSheeldDevice> oneSheeldScannedDevices;
    private ArrayList<OneSheeldDevice> oneSheeldConnectedDevices;
    private ArrayAdapter<String> connectedDevicesArrayAdapter;
    private ArrayAdapter<String> scannedDevicesArrayAdapter;
    private OneSheeldDevice selectedConnectedDevice = null;
    private OneSheeldDevice selectedScannedDevice = null;
    private boolean digitalWriteState = false;
    private byte pushButtonShieldId = OneSheeldSdk.getKnownShields().PUSH_BUTTON_SHIELD.getId();
    private byte pushButtonFunctionId = (byte) 0x01;
    private OneSheeldManager oneSheeldManager;
    private char[] nameChars = new char[]{};
    private Random random = new Random();
    private HashMap<String, String> pendingRenames;
    private Dialog bluetoothTestingDialog;
    private EditText bluetoothTestingSendingEditText;
    private EditText bluetoothTestingFramesNumberEditText;
    private EditText bluetoothTestingReceivingEditText;
    private TextView bluetoothSentFramesCounterTextView;
    private TextView bluetoothTestingReceivingFramesCounterTextView;
    private Button bluetoothTestingStartButton;
    private Button bluetoothTestingResetButton;
    private boolean isBaudRateQueried = false;
    private StringBuilder receivedStringBuilder = new StringBuilder();
    private BluetoothTestingSendingThread bluetoothTestingSendingThread;

    private OneSheeldScanningCallback scanningCallback = new OneSheeldScanningCallback() {
        @Override
        public void onDeviceFind(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    oneSheeldScannedDevices.add(device);
                    scannedDevicesNames.add(device.getName());
                    scannedDevicesArrayAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onScanFinish(List<OneSheeldDevice> foundDevices) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    scanningProgressDialog.dismiss();
                }
            });
        }
    };
    private OneSheeldTestingCallback testingCallback = new OneSheeldTestingCallback() {
        @Override
        public void onFirmwareTestResult(final OneSheeldDevice device, final boolean isPassed) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Firmware test result: " + (isPassed ? "Correct" : "Failed"), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onLibraryTestResult(final OneSheeldDevice device, final boolean isPassed) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Library test result: " + (isPassed ? "Correct" : "Failed"), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onFirmwareTestTimeOut(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Error, firmware test timeout!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onLibraryTestTimeOut(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Error, library test timeout!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };
    private OneSheeldRenamingCallback renamingCallback = new OneSheeldRenamingCallback() {
        @Override
        public void onRenamingAttemptTimeOut(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Error, renaming attempt failed, retrying!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onAllRenamingAttemptsTimeOut(final OneSheeldDevice device) {

            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Error, all renaming attempts failed!", Toast.LENGTH_SHORT).show();
                }
            });
            pendingRenames.remove(device.getAddress());
        }

        @Override
        public void onRenamingRequestReceivedSuccessfully(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, device.getName() + ": Renaming request received successfully!", Toast.LENGTH_SHORT).show();
                    if (connectedDevicesNames.contains(pendingRenames.get(device.getAddress()))) {
                        connectedDevicesNames.add(connectedDevicesNames.indexOf(pendingRenames.get(device.getAddress())), device.getName());
                        connectedDevicesNames.remove(pendingRenames.get(device.getAddress()));
                        pendingRenames.remove(device.getAddress());
                        connectedDevicesArrayAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };
    private OneSheeldDataCallback dataCallback = new OneSheeldDataCallback() {
        @Override
        public void onSerialDataReceive(OneSheeldDevice device, int data) {
            receivedStringBuilder.append((char) data);
            if (receivedStringBuilder.length() >= bluetoothTestingReceivingEditText.getText().toString().length()) {
                String compareString = receivedStringBuilder.substring(0, bluetoothTestingReceivingEditText.getText().toString().length());
                if (compareString.equals(bluetoothTestingReceivingEditText.getText().toString())) {
                    receivedStringBuilder.delete(0, bluetoothTestingReceivingEditText.getText().toString().length());
                    uiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            bluetoothTestingReceivingFramesCounterTextView.setText(String.valueOf((Integer.valueOf(bluetoothTestingReceivingFramesCounterTextView.getText().toString()) + 1)));
                        }
                    });
                }
                if (receivedStringBuilder.length() > 0) receivedStringBuilder.deleteCharAt(0);
            }
        }
    };
    private OneSheeldBaudRateQueryCallback baudRateQueryCallback = new OneSheeldBaudRateQueryCallback() {
        @Override
        public void onBaudRateQueryResponse(final OneSheeldDevice device, final SupportedBaudRate supportedBaudRate) {
            if (isBaudRateQueried) {
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, device.getName() + (supportedBaudRate != null ? ": Current baud rate: " + supportedBaudRate.getBaudRate() : ": Device responded with an unsupported baud rate"), Toast.LENGTH_SHORT).show();
                    }
                });
                isBaudRateQueried = false;
            }
        }
    };
    private OneSheeldConnectionCallback connectionCallback = new OneSheeldConnectionCallback() {
        @Override
        public void onConnect(final OneSheeldDevice device) {
            oneSheeldScannedDevices.remove(device);
            oneSheeldConnectedDevices.add(device);
            final String deviceName = device.getName();
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (scannedDevicesNames.indexOf(deviceName) >= 0) {
                        scannedDevicesNames.remove(deviceName);
                        connectedDevicesNames.add(deviceName);
                        connectedDevicesArrayAdapter.notifyDataSetChanged();
                        scannedDevicesArrayAdapter.notifyDataSetChanged();
                    }
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(false);
                    oneSheeldLinearLayout.setVisibility(View.INVISIBLE);
                }
            });
            connectionProgressDialog.dismiss();
            device.addTestingCallback(testingCallback);
            device.addRenamingCallback(renamingCallback);
            device.addDataCallback(dataCallback);
            device.addBaudRateQueryCallback(baudRateQueryCallback);
            device.addFirmwareUpdateCallback(firmwareUpdateCallback);
        }

        @Override
        public void onDisconnect(OneSheeldDevice device) {
            final String deviceName = device.getName();

            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (connectedDevicesNames.indexOf(deviceName) >= 0) {
                        connectedDevicesNames.remove(deviceName);
                        connectedDevicesArrayAdapter.notifyDataSetChanged();
                    }
                    connectButton.setEnabled(false);
                    disconnectButton.setEnabled(false);
                    oneSheeldLinearLayout.setVisibility(View.INVISIBLE);
                }
            });
            oneSheeldConnectedDevices.remove(device);
            if (!scannedDevicesNames.contains(device.getName()) && !oneSheeldScannedDevices.contains(device)) {
                oneSheeldScannedDevices.add(device);
                scannedDevicesNames.add(device.getName());
            }
            bluetoothTestingDialog.dismiss();
        }
    };
    private OneSheeldErrorCallback errorCallback = new OneSheeldErrorCallback() {
        @Override
        public void onError(final OneSheeldDevice device, final OneSheeldError error) {
            if (connectionProgressDialog != null)
                connectionProgressDialog.dismiss();
            if (scanningProgressDialog != null)
                scanningProgressDialog.dismiss();
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Error: " + error.toString() + (device != null ? " in " + device.getName() : ""), Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private OneSheeldFirmwareUpdateCallback firmwareUpdateCallback = new OneSheeldFirmwareUpdateCallback() {
        @Override
        public void onStart(OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    firmwareUpdateProgressDialog.show();
                    firmwareUpdateProgressDialog.setMessage("Starting..");
                }
            });
        }

        @Override
        public void onProgress(OneSheeldDevice device, final int totalBytes, final int sentBytes) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    firmwareUpdateProgressDialog.setMessage(((int) ((float) sentBytes / totalBytes * 100)) + "%");
                }
            });
        }

        @Override
        public void onSuccess(final OneSheeldDevice device) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    firmwareUpdateProgressDialog.dismiss();
                    Toast.makeText(MainActivity.this, device.getName() + ": Firmware update succeeded!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onFailure(final OneSheeldDevice device, final boolean isTimeOut) {
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    firmwareUpdateProgressDialog.dismiss();
                    Toast.makeText(MainActivity.this, device.getName() + ": Firmware update failed!" + (isTimeOut ? "Time-out occurred!" : ""), Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private AdapterView.OnItemClickListener scannedDevicesListViewClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectedScannedDevice = oneSheeldScannedDevices.get(position);
            connectButton.setEnabled(true);
        }
    };
    private AdapterView.OnItemClickListener connectedDevicesListViewClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectedConnectedDevice = oneSheeldConnectedDevices.get(position);
            oneSheeldNameTextView.setText(selectedConnectedDevice.getName());
            oneSheeldLinearLayout.setVisibility(View.VISIBLE);
            disconnectButton.setEnabled(true);
        }
    };

    public void onClickScan(View v) {
        scanningProgressDialog.show();
        oneSheeldManager.setScanningTimeOut(20);
        oneSheeldManager.cancelScanning();
        scannedDevicesNames.clear();
        scannedDevicesArrayAdapter.notifyDataSetChanged();
        oneSheeldScannedDevices.clear();
        oneSheeldManager.scan();
    }

    public void onClickConnect(View v) {
        if (selectedScannedDevice != null) {
            oneSheeldManager.cancelScanning();
            connectionProgressDialog.setMessage("Please wait while connecting to " + selectedScannedDevice.getName());
            connectionProgressDialog.show();
            selectedScannedDevice.connect();
        }
    }

    public void onClickDisconnect(View v) {
        if (selectedConnectedDevice != null) {
            selectedConnectedDevice.disconnect();
            selectedConnectedDevice = null;
            oneSheeldLinearLayout.setVisibility(View.INVISIBLE);
        }
    }

    public void onClickRename(View v) {
        if (selectedConnectedDevice != null) {
            pendingRenames.put(selectedConnectedDevice.getAddress(), selectedConnectedDevice.getName());
            selectedConnectedDevice.rename("1Sheeld #" + (selectedConnectedDevice.isTypePlus() ? getRandomChars(2) : getRandomChars(4)));
        }
    }

    public void onClickRenameAll(View v) {
        for (OneSheeldDevice device : oneSheeldConnectedDevices) {
            pendingRenames.put(device.getAddress(), device.getName());
            device.rename("1Sheeld #" + (device.isTypePlus() ? getRandomChars(2) : getRandomChars(4)));
        }
    }

    public void onClickTestBoard(View v) {
        if (selectedConnectedDevice != null) {
            selectedConnectedDevice.test();
        }
    }

    public void onClickQueryBaudRate(View v) {
        if (selectedConnectedDevice != null) {
            isBaudRateQueried = true;
            selectedConnectedDevice.queryBaudRate();
        }
    }

    public void onClickSetBaudRate(View v) {
        if (selectedConnectedDevice != null && baudRateSpinner != null) {
            if (baudRateSpinner.getSelectedItem().toString().equals("9600")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._9600);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("14400")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._14400);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("19200")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._19200);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("28800")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._28800);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("38400")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._38400);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("57600")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._57600);
            } else if (baudRateSpinner.getSelectedItem().toString().equals("115200")) {
                selectedConnectedDevice.setBaudRate(SupportedBaudRate._115200);
            }
        }
    }

    public void onClickFirmwareUpdate(View v) {
        if (selectedConnectedDevice != null) {
            InputStream is;
            try {
                is = getAssets().open("firmwares/" + firmwareSpinner.getSelectedItem().toString());
                byte[] fileBytes = new byte[is.available()];
                is.read(fileBytes);
                is.close();
                selectedConnectedDevice.update(fileBytes);
            } catch (IOException ignored) {
            }
        }
    }

    private String getRandomChars(int digitNum) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < digitNum; i++)
            builder.append(nameChars[random.nextInt(nameChars.length)]);
        return builder.toString();
    }

    public void onClickDigitalWrite(View v) {
        if (selectedConnectedDevice != null && pinsSpinner != null) {
            digitalWriteState = !digitalWriteState;
            selectedConnectedDevice.digitalWrite(pinsSpinner.getSelectedItemPosition() + 2, digitalWriteState);
            ((Button) v).setText("Digital Write (" + String.valueOf(digitalWriteState) + ")");
        }
    }

    public void onClickDigitalRead(View v) {
        if (selectedConnectedDevice != null && pinsSpinner != null)
            ((Button) v).setText("DigitalRead (" + selectedConnectedDevice.digitalRead(pinsSpinner.getSelectedItemPosition() + 2) + ")");
    }

    public void onClickSendOnFrame(View v) {
        if (selectedConnectedDevice != null) {
            ShieldFrame sf = new ShieldFrame(pushButtonShieldId, pushButtonFunctionId);
            sf.addArgument(true);
            selectedConnectedDevice.sendShieldFrame(sf);
        }
    }

    public void onClickSendOffFrame(View v) {
        if (selectedConnectedDevice != null) {
            ShieldFrame sf = new ShieldFrame(pushButtonShieldId, pushButtonFunctionId);
            sf.addArgument(false);
            selectedConnectedDevice.sendShieldFrame(sf);
        }
    }

    public void onClickBroadcastOn(View v) {
        ShieldFrame sf = new ShieldFrame(pushButtonShieldId, pushButtonFunctionId);
        sf.addArgument(true);
        oneSheeldManager.broadcastShieldFrame(sf);
    }

    public void onClickBroadcastOff(View v) {
        ShieldFrame sf = new ShieldFrame(pushButtonShieldId, pushButtonFunctionId);
        sf.addArgument(false);
        oneSheeldManager.broadcastShieldFrame(sf);
    }

    public void onClickDisconnectAll(View v) {
        oneSheeldManager.disconnectAll();
        oneSheeldLinearLayout.setVisibility(View.INVISIBLE);
        disconnectButton.setEnabled(false);
    }

    public void onClickBluetoothTestingDialog(View v) {
        resetBluetoothTesting();
        bluetoothSentFramesCounterTextView.setText("0");
        bluetoothTestingReceivingFramesCounterTextView.setText("0");
        bluetoothTestingSendingEditText.setText("a0b1c2d3e4f5g6h7i8j9");
        bluetoothTestingFramesNumberEditText.setText("10000");
        bluetoothTestingReceivingEditText.setText("a0b1c2d3e4f5g6h7i8j9");
        bluetoothTestingStartButton.setEnabled(true);
        bluetoothTestingSendingEditText.setEnabled(true);
        bluetoothTestingFramesNumberEditText.setEnabled(true);
        bluetoothTestingDialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        oneSheeldLinearLayout = (LinearLayout) findViewById(R.id.onesheeld_container);
        connectButton = (Button) findViewById(R.id.connect_1sheeld);
        disconnectButton = (Button) findViewById(R.id.disconnect_1sheeld);
        ListView connectedDevicesListView = (ListView) findViewById(R.id.connected_list);
        ListView scannedDevicesListView = (ListView) findViewById(R.id.scanned_list);
        oneSheeldNameTextView = (TextView) findViewById(R.id.selected_1sheeld_name);
        pinsSpinner = (Spinner) findViewById(R.id.pin_number);
        baudRateSpinner = (Spinner) findViewById(R.id.baud_rate);
        firmwareSpinner = (Spinner) findViewById(R.id.firmwaresSpinner);
        connectedDevicesNames = new ArrayList<>();
        scannedDevicesNames = new ArrayList<>();
        connectedDevicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, connectedDevicesNames);
        scannedDevicesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scannedDevicesNames);
        ArrayList<String> pinNumbers = new ArrayList<>();
        for (int pinNum = 2; pinNum <= 13; pinNum++)
            pinNumbers.add(String.valueOf(pinNum));
        ArrayAdapter<String> pinsArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, pinNumbers);
        ArrayList<String> baudRates = new ArrayList<>();
        for (SupportedBaudRate baudRate : SupportedBaudRate.values())
            baudRates.add(String.valueOf(baudRate.getBaudRate()));
        ArrayAdapter<String> baudRatesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baudRates);
        ArrayList<String> firmwares = new ArrayList<>();
        try {
            Collections.addAll(firmwares, getAssets().list("firmwares"));
        } catch (IOException ignored) {
        }
        ArrayAdapter<String> firmwaresAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, firmwares);
        oneSheeldLinearLayout.setVisibility(View.INVISIBLE);
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(false);
        oneSheeldScannedDevices = new ArrayList<>();
        oneSheeldConnectedDevices = new ArrayList<>();
        pendingRenames = new HashMap<>();
        connectedDevicesListView.setAdapter(connectedDevicesArrayAdapter);
        scannedDevicesListView.setAdapter(scannedDevicesArrayAdapter);
        pinsSpinner.setAdapter(pinsArrayAdapter);
        baudRateSpinner.setAdapter(baudRatesAdapter);
        firmwareSpinner.setAdapter(firmwaresAdapter);
        scannedDevicesListView.setOnItemClickListener(scannedDevicesListViewClickListener);
        connectedDevicesListView.setOnItemClickListener(connectedDevicesListViewClickListener);
        initScanningProgressDialog();
        initConnectionProgressDialog();
        initFirmwareUpdateProgressDialog();
        initRandomChars();
        initBluetoothTestingDialog();
        initOneSheeldSdk();
    }

    void initBluetoothTestingDialog() {
        bluetoothTestingDialog = new Dialog(this);
        bluetoothTestingDialog.setContentView(R.layout.testing_dialog);
        bluetoothTestingDialog.setCanceledOnTouchOutside(false);
        bluetoothTestingSendingEditText = (EditText) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingSendingEditText);
        bluetoothTestingFramesNumberEditText = (EditText) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingFramesNumberEditText);
        bluetoothTestingReceivingEditText = (EditText) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingReceivingEditText);
        bluetoothSentFramesCounterTextView = (TextView) bluetoothTestingDialog.findViewById(R.id.bluetoothSentFramesCounterTextView);
        bluetoothTestingReceivingFramesCounterTextView = (TextView) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingReceivingFramesCounterTextView);
        bluetoothTestingStartButton = (Button) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingStartButton);
        bluetoothTestingResetButton = (Button) bluetoothTestingDialog.findViewById(R.id.bluetoothTestingResetButton);
        bluetoothTestingStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBluetoothTesting();
            }
        });
        bluetoothTestingResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetBluetoothTesting();
            }
        });
        resetBluetoothTesting();
        bluetoothTestingDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                resetBluetoothTesting();
            }
        });
        bluetoothTestingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                resetBluetoothTesting();
            }
        });
    }

    private void resetBluetoothTesting() {
        if (bluetoothTestingSendingThread != null)
            bluetoothTestingSendingThread.stopRunning();
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                bluetoothSentFramesCounterTextView.setText("0");
                bluetoothTestingReceivingFramesCounterTextView.setText("0");
                bluetoothTestingSendingEditText.setEnabled(true);
                bluetoothTestingFramesNumberEditText.setEnabled(true);
                bluetoothTestingStartButton.setEnabled(true);
            }
        });
        receivedStringBuilder = new StringBuilder();
    }

    private void startBluetoothTesting() {
        if (selectedConnectedDevice != null) {
            if (bluetoothTestingSendingThread != null)
                bluetoothTestingSendingThread.stopRunning();
            bluetoothTestingSendingThread = new BluetoothTestingSendingThread(selectedConnectedDevice, bluetoothTestingSendingEditText.getText().toString(), Integer.valueOf(bluetoothTestingFramesNumberEditText.getText().toString()));
        }
        uiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                bluetoothTestingStartButton.setEnabled(false);
                bluetoothTestingSendingEditText.setEnabled(false);
                bluetoothTestingFramesNumberEditText.setEnabled(false);

            }
        });
    }

    private void initRandomChars() {
        StringBuilder tmp = new StringBuilder();
        for (char ch = '0'; ch <= '9'; ++ch)
            tmp.append(ch);
        for (char ch = 'A'; ch <= 'Z'; ++ch)
            tmp.append(ch);
        nameChars = tmp.toString().toCharArray();
    }

    private void initOneSheeldSdk() {
        OneSheeldSdk.setDebugging(true);
        OneSheeldSdk.init(this);
        oneSheeldManager = OneSheeldSdk.getManager();
        oneSheeldManager.setConnectionRetryCount(1);
        oneSheeldManager.setAutomaticConnectingRetriesForClassicConnections(true);
        oneSheeldManager.addScanningCallback(scanningCallback);
        oneSheeldManager.addConnectionCallback(connectionCallback);
        oneSheeldManager.addErrorCallback(errorCallback);
    }

    private void initScanningProgressDialog() {
        scanningProgressDialog = new ProgressDialog(this);
        scanningProgressDialog.setMessage("Please wait..");
        scanningProgressDialog.setTitle("Scanning");
        scanningProgressDialog.setCancelable(true);
        scanningProgressDialog.setCanceledOnTouchOutside(true);
        scanningProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                oneSheeldManager.cancelScanning();
            }
        });
    }

    private void initConnectionProgressDialog() {
        connectionProgressDialog = new ProgressDialog(this);
        connectionProgressDialog.setMessage("Please wait while connecting..");
        connectionProgressDialog.setTitle("Connecting");
        connectionProgressDialog.setCancelable(false);
        connectionProgressDialog.setCanceledOnTouchOutside(false);
    }

    private void initFirmwareUpdateProgressDialog() {
        firmwareUpdateProgressDialog = new ProgressDialog(this);
        firmwareUpdateProgressDialog.setTitle("Updating Firmware..");
        firmwareUpdateProgressDialog.setCancelable(false);
        firmwareUpdateProgressDialog.setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onDestroy() {
        oneSheeldManager.cancelScanning();
        oneSheeldManager.disconnectAll();
        bluetoothTestingDialog.dismiss();
        super.onDestroy();
    }

    private class BluetoothTestingSendingThread extends Thread {
        AtomicBoolean stopRequested;
        OneSheeldDevice device;
        String string;
        int count;

        BluetoothTestingSendingThread(OneSheeldDevice device, String string, int count) {
            stopRequested = new AtomicBoolean(false);
            this.device = device;
            this.string = string;
            this.count = count;
            start();
        }

        private void stopRunning() {
            if (this.isAlive())
                this.interrupt();
            stopRequested.set(true);
        }

        @Override
        public void run() {
            for (int i = 1; i <= count && !this.isInterrupted() && !stopRequested.get(); i++) {
                device.sendSerialData(string.getBytes(Charset.forName("US-ASCII")));
                final int counter = i;
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!stopRequested.get())
                            bluetoothSentFramesCounterTextView.setText(String.valueOf(counter));
                    }
                });
            }
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!stopRequested.get()) {
                        bluetoothTestingSendingEditText.setEnabled(true);
                        bluetoothTestingFramesNumberEditText.setEnabled(true);
                        bluetoothTestingStartButton.setEnabled(true);
                    }
                }
            });
        }
    }
}
