package id.aashari.code.miband2.Activities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import id.aashari.code.miband2.Helpers.CustomBluetoothProfile;
import id.aashari.code.miband2.R;

import com.ubidots.ApiClient;
import com.ubidots.Variable;

public class MainActivity extends Activity {
    private final static int REQUEST_ENABLE_BT = 1;
    /*    private String KEY= "A1E-0ee54d1181c8a871188d4ddd4fdb2f2d3ef8";
        private String BATTERY_KEY= "5a15afe3c03f977cb0361e49";
        private String HEART_RATE_ID= "5a60eebfc03f971830968752";*/
    private String KEY; //= "A1E-0ee54d1181c8a871188d4ddd4fdb2f2d3ef8";
    private String BATTERY_KEY; //= "5a15afe3c03f977cb0361e49";
    private String HEART_RATE_ID;//= "5a60eebfc03f971830968752";
    private static final int ABSOLUTE_MAX_BPM = 200;
    private static final int ABSOLUTE_MIN_BPM = 40;
    private Integer MaxBpmAlarm = 190;
    private Integer MinBpmAlarm = 40;
    private Integer Min_TIMER = 20;
    private Integer Hour_TIMER = 0;


    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    final static String path = Environment.getExternalStorageDirectory().getAbsolutePath();
    private final static String filesettsname = "settings.txt";
    Context context = this;

    Boolean isListeningHeartRate = false;
    Boolean isListeningBateryLevel = false;
    Boolean vibrate = false;
    Boolean timerHasStarted = false;
    Integer mibandTimeOut = 30000;
    TimerTask timerTask;

    Timer timer = new Timer();
    CountDownTimer miBandTimeOut = new CountDownTimer(mibandTimeOut, 5000) {
        public void onTick(long millisUntilFinished) {
            //there's nothing to do
            Log.v("mibandtimerout", "here I am waiting for miband answer");
        }

        public void onFinish() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtBpm.setText("Failed to read bpm. Please, try again.");
                    Log.v("mibandtimerout", "Failed to read bpm. Please, try again.");
                }
            });

        }
    };


    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;

    Button btnStartConnecting, btnGetBatteryInfo, btnGetHeartRate, btnStartVibrate, btnMiBand_show_cfg;
    EditText txtPhysicalAddress, maxBpmAlarm, minBpmAlarm, readMin, readHour, ubiID, ubiHeartID, ubiBatID;
    TextView txtState, txtStateImg, txtBpm, txtBat;
    View heartLy, FBatLy, ubiLx, timerLx, bpmLx;
    SeekBar setMinBpm, setMaxBpm, setTimerMin, setTimerHour;
    int batlevel, bpm;

    final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.v("test", "onConnectionStateChange");

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                bluetoothGatt.discoverServices();
                ShowConnected();
                Log.v("test", "ConnectionStateChange to ShowConnected");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtState.setText("Connected");
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                bluetoothGatt.disconnect();
                showConnect();
                Log.v("test", "ConnectionStateChange to showConnect");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtState.setText("Disconnected");
                    }
                });
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.v("test", "onServicesDiscovered:");
            Log.v("test", "is Listening to HeartRate: " + isListeningHeartRate);
            Log.v("test", "is Listening to BateryLevel: " + isListeningBateryLevel);
            listenHeartRate();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.v("test", "onCharacteristicRead");
            final byte[] data = characteristic.getValue();
            batlevel = data[1];
            Log.v("test", "listen battery batlevel xubiz : " + batlevel + "%");
            if (batlevel > 0) {
                // Toast.makeText(this, "battery info: " + batlevel, Toast.LENGTH_SHORT).show();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtBat.setText(batlevel + "%");
                    }
                });
                boolean goodKeyAndVar = false;
                try {
                    Log.v("charRead", "check point! trying to  battery batlevel: " + batlevel + "%");
                    String[] keyvarArray = new String[]{KEY, BATTERY_KEY, batlevel + ""};
                    goodKeyAndVar = new ApiUbidots_VerifyVarId().execute(keyvarArray).get();
                    Log.v("charRead", "var return: var good key n var? : " + goodKeyAndVar);
                } catch (InterruptedException e) {
                    Log.v("charRead_IntrptExcept", "the exception is " + e.toString());
                } catch (ExecutionException e) {
                    Log.v("charRead_ExecExcept", "the exception is " + e.toString());
                }
                if (goodKeyAndVar) {
                    new ApiUbidots().execute(new String[]{KEY, BATTERY_KEY, batlevel + ""});
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.v("test", "onCharacteristicWrite");

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.v("charChanged", "onCharacteristicChanged");
            final byte[] data = characteristic.getValue();
            bpm = (int) data[1];
            miBandTimeOut.cancel();
            final String hrbpm = String.valueOf(bpm) + " bpm";
            Log.v("charChanged", "got heartrate bpm: " + hrbpm);
            if (bpm <= 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txtBpm.setText("Adjust the MiBand for better reading");
                    }
                });
            } else {
                boolean goodKeyAndVar = false;
                try {
                    String[] keyvarArray = new String[]{KEY, HEART_RATE_ID};
                    goodKeyAndVar = new ApiUbidots_verifyApiKey().execute(keyvarArray).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                if (goodKeyAndVar) {
                    new ApiUbidots().execute(new String[]{KEY, HEART_RATE_ID, String.valueOf(bpm)});
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            txtBpm.setText(hrbpm);

                        }
                    });
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            getBatteryStatus();
                        }
                    });*/
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.v("test", "onDescriptorRead");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.v("test", "onDescriptorWrite");
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
            Log.v("test", "onReliableWriteCompleted");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.v("test", "onReadRemoteRssi");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.v("test", "onMtuChanged");
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        verifyStoragePermissions(this);
        isExternalStorageWritable();
        isExternalStorageReadable();
        //writeSettings(this, filesettsname);
//        readSettings(this, filesettsname);

        setContentView(R.layout.activity_main);

        initializeBluetoothDevice();
        initializeUIComponents();

        // writeSettings(this, filesettsname);
        initializeEvents();
        //   writeSettings(this, filesettsname);
        readSettings(this, filesettsname);
        getBoundedDevice();
        startConnecting();
        ShowConnected();

        timerTask=new TimerTask()
        {
            public void run()
            {
                startScanHeartRate();
                //getBatteryStatus();
            }
        };
        timer.scheduleAtFixedRate( timerTask,15000, Min_TIMER * 60000 + Hour_TIMER * 3600000);
      //  timerLaucher();

        //    btnGetBatteryInfo.performClick();
    }



    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            Log.v("media", "is writable");

            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            Log.v("media", "is readable");
            return true;
        }
        return false;
    }

    /**
     * Checks if the app has permission to write to device storage
     * <p>
     * If the app does not has permission then the user will be prompted to grant permissions
     *
     * @param activity
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
            Log.v("media", "has permitions");
        }
    }

    void start() {
        btnStartConnecting.setVisibility(View.GONE);
        initializeBluetoothDevice();
        getBoundedDevice();
        startConnecting();
        ShowConnected();
    }

    void getBoundedDevice() {
        String address = "";
        Set<BluetoothDevice> boundedDevice;
        do {
            boundedDevice = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice bd : boundedDevice) {
                if (bd.getName().contains("MI Band 2")) {
                    address = bd.getAddress();
                    txtPhysicalAddress.setText(address);
                } else {
                    Toast.makeText(this, "Waiting for MiBand2 Connection, be sure it's paired", Toast.LENGTH_SHORT).show();
                    Log.v("test", "I'm here waiting for connection");
                }
            }
        } while (address == "");
    }

    void initializeBluetoothDevice() {
        // Ask for location permission if not already allowed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this, "Device does not support Bluetooth!", Toast.LENGTH_SHORT).show();
            onPause();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Toast.makeText(this, "Getting the  Bluetooth Device...", Toast.LENGTH_SHORT).show();
        }
    }

    void initializeUIComponents() {
        btnStartConnecting = (Button) findViewById(R.id.btnStartConnecting);
        heartLy = findViewById(R.id.heartLy);
        FBatLy = findViewById(R.id.FBatLy);
        ubiLx = findViewById(R.id.ubiLx);
        timerLx = findViewById(R.id.timerLx);
        bpmLx = findViewById(R.id.bpmLx);
        btnGetBatteryInfo = (Button) findViewById(R.id.btnGetBatteryInfo);
        btnStartVibrate = (Button) findViewById(R.id.btnStartVibrate);
        btnGetHeartRate = (Button) findViewById(R.id.btnGetHeartRate);
        btnMiBand_show_cfg = (Button) findViewById(R.id.miBand_show_addr);
        txtPhysicalAddress = (EditText) findViewById(R.id.txtPhysicalAddress);
        txtState = (TextView) findViewById(R.id.txtState);
        txtStateImg = (TextView) findViewById(R.id.txtState2);
        txtBpm = (TextView) findViewById(R.id.txtBpm);
        txtBat = (TextView) findViewById(R.id.textBat);
        maxBpmAlarm = (EditText) findViewById(R.id.maxBpmAlarm);
        minBpmAlarm = (EditText) findViewById(R.id.minBpmAlarm);
        readMin = (EditText) findViewById(R.id.readMin);
        readHour = (EditText) findViewById(R.id.readHour);
        ubiID = (EditText) findViewById(R.id.ubiID);
        ubiHeartID = (EditText) findViewById(R.id.ubiHeartKey);
        ubiBatID = (EditText) findViewById(R.id.ubiBatKey);
        setMaxBpm = (SeekBar) findViewById(R.id.setMaxbpmAlarm);
        setMinBpm = (SeekBar) findViewById(R.id.setMinbpmAlarm);
        setTimerMin = (SeekBar) findViewById(R.id.setTimeerMin);
        setTimerHour = (SeekBar) findViewById(R.id.setTimerHour);

    }

    void initializeEvents() {

        btnStartConnecting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start();
            }
        });
        btnGetBatteryInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getBatteryStatus(txtBat);
            }
        });
        btnStartVibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVibrate();
            }
        });
        btnGetHeartRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScanHeartRate();
            }
        });
        btnMiBand_show_cfg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleCfg();
            }
        });
        ubiID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                KEY = ubiID.getText().toString();
                boolean isValid = false;
                try {
                    isValid = new ApiUbidots_verifyApiKey().execute(KEY).get();
                    Log.v("ubikey", "the key is " + isValid);
                } catch (InterruptedException e) {
                    Log.v("ubikey_InterruptedExc", "the exception is " + e.toString());
                } catch (ExecutionException e) {
                    Log.v("ubikey_ExecutionExcept", "the exception is " + e.toString());
                }
            }
        });

        ubiHeartID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean x;
                HEART_RATE_ID = ubiHeartID.getText().toString();
                String[] keyvarArray = new String[]{KEY, HEART_RATE_ID};
                try {
                    boolean isValidVarID = new ApiUbidots_VerifyVarId().execute(keyvarArray).get();
                    if (isValidVarID) {
                        Log.v("ubiVar", " Variable id valid ");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ubiHeartID.setHighlightColor(Color.WHITE);
                                ubiHeartID.setTextColor(Color.BLUE);
                            }
                        });
                    } else {
                        Log.v("ubiVar", " Variable id <<INVALID>>: ");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ubiHeartID.setHighlightColor(Color.GRAY);
                                ubiHeartID.setTextColor(Color.RED);
                                cfgOn();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Log.v("ubiHeart_InterruptedExc", "the exception is " + e.toString());
                } catch (ExecutionException e) {
                    Log.v("ubiheart_ExecutionExc", "the exception is " + e.toString());
                }
            }
        });

        ubiBatID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BATTERY_KEY = ubiBatID.getText().toString();
                String[] keyvarArray = new String[]{KEY, BATTERY_KEY};
                try {
                    boolean isValidVarID = new ApiUbidots_VerifyVarId().execute(keyvarArray).get();
                    if (isValidVarID) {
                        Log.v("ubiVar", " Variable id valid ");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ubiBatID.setHighlightColor(Color.WHITE);
                                ubiBatID.setTextColor(Color.BLUE);
                            }
                        });
                    } else {
                        Log.v("ubiVar", " Variable id <<INVALID>>: ");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ubiBatID.setHighlightColor(Color.GRAY);
                                ubiBatID.setTextColor(Color.RED);
                                cfgOn();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Log.v("ubibat_InterruptedExc", "the exception is " + e.toString());
                } catch (ExecutionException e) {
                    Log.v("ubibat_ExecutionExcept", "the exception is " + e.toString());
                }
            }
        });
//todo caixas de texto mudam seekBars
        setMinBpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int value = MinBpmAlarm;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                //  Toast.makeText(getApplicationContext(), "Changing  max:" + setMinBpm.getMax()+ " value:" + value, Toast.LENGTH_SHORT).show();
                value = progress + ABSOLUTE_MIN_BPM;
                minBpmAlarm.setText(value + "");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                setMinBpm.setMax(Integer.valueOf(maxBpmAlarm.getText().toString()) - ABSOLUTE_MIN_BPM);
                //top = Integer.valueOf(maxBpmAlarm.getText().toString());
                // Toast.makeText(getApplicationContext(), "Started tracking seekbar max:" + setMinBpm.getMax(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Toast.makeText(getApplicationContext(), "Stopped  max:" + setMinBpm.getMax() + " value:" + value, Toast.LENGTH_SHORT).show();
                minBpmAlarm.setText("" + value);
                MinBpmAlarm = value;
            }
        });

        setMaxBpm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int value = MaxBpmAlarm, min;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = progress + min;
                maxBpmAlarm.setText(value + "");
                //   Toast.makeText(getApplicationContext(), "Changing seekbar's progress", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                min = Integer.valueOf(minBpmAlarm.getText().toString());
                setMaxBpm.setMax(ABSOLUTE_MAX_BPM - min);
                //    Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                maxBpmAlarm.setText("" + value);
                //   Toast.makeText(getApplicationContext(), "Stopped tracking seekbar", Toast.LENGTH_SHORT).show();
                MaxBpmAlarm = value;
                //setMinBpm.setMax(value);
            }
        });

        setTimerMin.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int value = Min_TIMER;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = progress;
                readMin.setText(value + "");
                // Toast.makeText(getApplicationContext(), "Changing seekbar's progress", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                readMin.setText("" + value);
                Min_TIMER = value;
                timer.cancel();
                timerTask=new TimerTask()
                {
                    public void run()
                    {
                        startScanHeartRate();
                    }
                };
                timer=new Timer();
                timer.scheduleAtFixedRate( timerTask,15000, Min_TIMER * 60000 + Hour_TIMER * 3600000);

                Toast.makeText(getApplicationContext(), "Stopped tracking seekbar", Toast.LENGTH_SHORT).show();

            }
        });

        setTimerHour.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int value = Hour_TIMER;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = progress;
                readHour.setText(value + "");
                //  Toast.makeText(getApplicationContext(), "Changing seekbar's progress", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //  Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                readHour.setText("" + value);
                Hour_TIMER = value;
                timer.cancel();
                timerTask=new TimerTask()
                {
                    public void run()
                    {
                        startScanHeartRate();
                    }
                };
                timer=new Timer();
                timer.scheduleAtFixedRate( timerTask,15000, Min_TIMER * 60000 + Hour_TIMER * 3600000);
                //  Toast.makeText(getApplicationContext(), "Stopped tracking seekbar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleCfg() {
        if (txtPhysicalAddress.getVisibility() == View.VISIBLE) {
            cfgOff(true);

        } else {
            cfgOn();
        }
    }

    private void cfgOff(boolean save) {
        txtPhysicalAddress.setVisibility(View.INVISIBLE);
        btnStartConnecting.setVisibility(View.GONE);
        bpmLx.setVisibility(View.GONE);
        timerLx.setVisibility(View.GONE);
        ubiLx.setVisibility(View.GONE);
        heartLy.setVisibility(View.VISIBLE);
        FBatLy.setVisibility(View.VISIBLE);
        if (save) writeSettings(context, filesettsname);
    }

    private void cfgOn() {
        txtPhysicalAddress.setVisibility(View.VISIBLE);
        btnStartConnecting.setVisibility(View.GONE);
        bpmLx.setVisibility(View.VISIBLE);
        timerLx.setVisibility(View.VISIBLE);
        ubiLx.setVisibility(View.VISIBLE);
        heartLy.setVisibility(View.GONE);
        FBatLy.setVisibility(View.GONE);
    }

    void showConnect() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnStartConnecting.setVisibility(View.VISIBLE);
                txtBpm.setVisibility(View.GONE);
                txtPhysicalAddress.setVisibility(View.INVISIBLE);
                bpmLx.setVisibility(View.GONE);
                timerLx.setVisibility(View.GONE);
                ubiLx.setVisibility(View.GONE);
                heartLy.setVisibility(View.GONE);
                FBatLy.setVisibility(View.GONE);
                txtState.setText("Disconnected");
                // txtState.setVisibility(View.INVISIBLE);
                txtStateImg.setBackground(getResources().getDrawable(R.drawable.bluetooth_off));

            }
        });
    }

    void ShowConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                cfgOff(false);
                txtState.setText("Connected");
                txtStateImg.setBackground(getResources().getDrawable(R.drawable.bluetooth));

            }
        });
    }

    void startConnecting() {
        String address = txtPhysicalAddress.getText().toString();

        bluetoothDevice = bluetoothAdapter.getRemoteDevice(address);
        Log.v("test", "Connecting to " + address);
        Log.v("test", "Device name " + bluetoothDevice.getName());
        bluetoothGatt = bluetoothDevice.connectGatt(this, true, bluetoothGattCallback);
    }

    void startScanHeartRate() {
        if (bluetoothGatt != null) {
            Log.v("test", "gatt is " + bluetoothGatt.toString());
         //   Toast.makeText(this, "gatt is is " + bluetoothGatt.toString(), Toast.LENGTH_SHORT).show();
            if (bluetoothGatt.getService(CustomBluetoothProfile.Basic.service) == null) {
                Log.v("test", "...waiting for miBand2 bpm answer...");
          //      Toast.makeText(this, "...waiting for miBand2 bpm answer...", Toast.LENGTH_SHORT).show();
                isListeningHeartRate = true;
                return;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtBpm.setText("Started Reading HeartRate");
                }
            });

            BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                    .getCharacteristic(CustomBluetoothProfile.HeartRate.controlCharacteristic);
            bchar.setValue(new byte[]{21, 2, 1});
            bluetoothGatt.writeCharacteristic(bchar);
            Log.v("test", "Started Reading HeartRate");
            isListeningHeartRate = true;
            miBandTimeOut.start();
        } else {
            Log.v("test", "gatt is null, no bpm reading");
            Toast.makeText(this, "Bluetooth gatt is nuked, no bpm reading", Toast.LENGTH_SHORT).show();

        }
        return;
    }

    void listenHeartRate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.HeartRate.service)
                .getCharacteristic(CustomBluetoothProfile.HeartRate.measurementCharacteristic);
        bluetoothGatt.setCharacteristicNotification(bchar, true);
        BluetoothGattDescriptor descriptor = bchar.getDescriptor(CustomBluetoothProfile.HeartRate.descriptor);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
        isListeningHeartRate = true;
        Log.v("test", "I'm here listening");
    }

    void getBatteryStatus(TextView txtBat) {

        BluetoothGattService serviceTemp;
        BluetoothGattCharacteristic bchar;
        byte[] z;
        if (bluetoothGatt != null) {
            Log.v("test", "gatt is " + bluetoothGatt.toString());
            Toast.makeText(this, "gatt is is " + bluetoothGatt.toString(), Toast.LENGTH_SHORT).show();
             serviceTemp = bluetoothGatt.getService(CustomBluetoothProfile.Basic.service);
            if (serviceTemp == null) {
                Log.v("test", "...waiting for miBand2 battery level answer...");
                Toast.makeText(this, "...waiting for miBand2 battery level answer...", Toast.LENGTH_SHORT).show();
                isListeningBateryLevel = true;
                return;
            }
           Log.v("test", "gatt service is " + serviceTemp.toString());
            Toast.makeText(this, "gatt service is is " + serviceTemp.toString(), Toast.LENGTH_SHORT).show();

             bchar = serviceTemp.getCharacteristic(CustomBluetoothProfile.Basic.batteryCharacteristic);
            z = bchar.getValue();

            if (!bluetoothGatt.readCharacteristic(bchar)) {
                Toast.makeText(this, "Failed get battery info", Toast.LENGTH_SHORT).show();
            } else {
                if (z != null) {
                    boolean goodKeyAndVar = false;
                    try {
                        String[] keyvarArray = new String[]{KEY, BATTERY_KEY};
                        goodKeyAndVar = new ApiUbidots_VerifyVarId().execute(keyvarArray).get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    if (goodKeyAndVar) {
                        new ApiUbidots().execute(new String[]{KEY, BATTERY_KEY, z.toString()});
                        batlevel = (int) z[1];
                        String bat = String.valueOf(batlevel) + "%";
                        txtBat.setText(bat);
                        Toast.makeText(this, "battery info: " + batlevel, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } else {
            Log.v("test", "gatt is null, no battery level reading");
            Toast.makeText(this, "Bluetooth gatt is nuked, no battery level reading", Toast.LENGTH_SHORT).show();

        }
    }

    void startVibrate() {
        if (!vibrate) {
            BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.AlertNotification.service)
                    .getCharacteristic(CustomBluetoothProfile.AlertNotification.alertCharacteristic);
            bchar.setValue(new byte[]{2});
            if (!bluetoothGatt.writeCharacteristic(bchar)) {
                Toast.makeText(this, "Failed start vibrate", Toast.LENGTH_SHORT).show();
            } else {
                vibrate = true;
                btnStartVibrate.setText("Found/Stop Vibrate");
            }
        } else {
            stopVibrate();
        }
    }

    void stopVibrate() {
        BluetoothGattCharacteristic bchar = bluetoothGatt.getService(CustomBluetoothProfile.AlertNotification.service)
                .getCharacteristic(CustomBluetoothProfile.AlertNotification.alertCharacteristic);
        bchar.setValue(new byte[]{0});
        if (!bluetoothGatt.writeCharacteristic(bchar)) {
            Toast.makeText(this, "Failed stop vibrate", Toast.LENGTH_SHORT).show();
        } else {
            vibrate = false;
            btnStartVibrate.setText("Find/Start Vibrate");
        }
    }

    public class ApiUbidots extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            ApiClient apiClient = new ApiClient(params[0]);
            Variable variable = apiClient.getVariable(params[1]);
            Log.v("test", "sending Ubidots  bit value: " + params[2] + " to " + params[1] + " Variable");
            if (variable != null) {
                variable.saveValue(Integer.valueOf(params[2]));
            } else {
                Log.v("UBI_SEND_FAIL", "FAIL to send Ubidots  bit value: " + params[2] + " to " + params[1] + " Variable");
            }
            return null;
        }
    }

    public class ApiUbidots_VerifyVarId extends AsyncTask<String, Void, Boolean> {
        boolean keyIsValid = false;

        @Override
        protected Boolean doInBackground(String... params) {
            ApiClient apiClient = new ApiClient(params[0]);
            if (apiClient != null) {
                keyIsValid = true;
                Log.v("ApiubiVerifyVar", "key is valid");
                if (keyIsValid) {
                    Variable variable = apiClient.getVariable(params[1].toString());
                    if (variable != null) {
                        Log.v("ApiubiVerifyVar", "_return_true");
                        return true;
                    }
                }
            }
            Log.v("ApiubiVerifyVar", "_return false");
            return false;
        }

        @Override
        protected void onPostExecute(Boolean isValidVarID) {
            super.onPostExecute(isValidVarID);
            if (!keyIsValid) {
                Log.v("ubiKey", " <<INVALID>> ApiClient ID");
                cfgOn();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiID.setHighlightColor(Color.GRAY);
                        ubiID.setTextColor(Color.RED);
                        cfgOn();
                    }
                });
            } /*else {
                Log.v("ubiKey", " ApiClient ID is valid ");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiID.setHighlightColor(Color.WHITE);
                        ubiID.setTextColor(Color.BLUE);
                    }
                });
            }*/
        }
    }

    public class ApiUbidots_verifyApiKey extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            ApiClient apiClient = new ApiClient(params[0]);
            //  ArrayList<Variable> v = apiClient.getVariables();
            if (apiClient != null) {
                Log.v("ApiubiVerifyKey", "_return_true");
                return true;
            }

            Log.v("ApiubiVerifyKey", "_return_false");
            return false;
        }

        @Override
        protected void onPostExecute(Boolean isValid) {
            super.onPostExecute(isValid);

            if (!isValid) {
                Log.v("ubiBat", " ApiClient ID <<INVALID>>: ");
                cfgOn();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiID.setHighlightColor(Color.GRAY);
                        ubiID.setTextColor(Color.RED);
                    }
                });
            } /*else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiID.setHighlightColor(Color.WHITE);
                        ubiID.setTextColor(Color.BLUE);
                    }
                });
            }*/
        }
    }

    public void readSettings(Context context, String filename) {
        int index;
        try {
            FileInputStream fs = context.openFileInput(filename);
            InputStreamReader isr = new InputStreamReader(fs, "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            String line = br.readLine();
            if (line != null) {
                //  index = line.indexOf("=");
                KEY = line.substring(line.indexOf("=") + 2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiID.setText(KEY);
                    }
                });
            }
            line = br.readLine();
            if (line != null) {
                BATTERY_KEY = line.substring(line.indexOf("=") + 2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiBatID.setText(BATTERY_KEY);
                    }
                });
            }
            line = br.readLine();
            if (line != null) {
                HEART_RATE_ID = line.substring(line.indexOf("=") + 2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ubiHeartID.setText(HEART_RATE_ID);
                    }
                });
            }
            line = br.readLine();
            if (line != null) {
                index = line.indexOf("=");
                MaxBpmAlarm = Integer.parseInt(line.substring(index + 2));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        maxBpmAlarm.setText("" + MaxBpmAlarm);
                        setMaxBpm.setProgress(MaxBpmAlarm);
                    }
                });

            }
            line = br.readLine();
            if (line != null) {
                index = line.indexOf("=");
                MinBpmAlarm = Integer.parseInt(line.substring(index + 2));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        minBpmAlarm.setText("" + MinBpmAlarm);
                        setMinBpm.setProgress(MinBpmAlarm);
                    }
                });
            }
            line = br.readLine();
            if (line != null) {
                index = line.indexOf("=");
                Min_TIMER = Integer.parseInt(line.substring(index + 2));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readMin.setText("" + Min_TIMER);
                        setTimerMin.setProgress(Min_TIMER);
                    }
                });
            }
            line = br.readLine();
            if (line != null) {
                index = line.indexOf("=");
                Hour_TIMER = Integer.parseInt(line.substring(index + 2));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        readHour.setText("" + Hour_TIMER);
                        setTimerHour.setProgress(Hour_TIMER);
                    }
                });
            }
            fs.close();
            Toast.makeText(getBaseContext(),
                    "Done reading SD 'mysdfile.txt'",
                    Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.v("write file not found", e.getMessage());
        } catch (UnsupportedEncodingException e) {
            Log.v("UnsupportedEncoding", e.getMessage());
        } catch (IOException e) {
            Log.v("IO exception", e.getMessage());
        }

    }

    public void writeSettings(Context context, String fileName) {
        try {
            File file = new File(path, fileName);
            if (!file.exists()) {
                file.createNewFile();
            } else {
                Log.v("writing file", "Done file exists in " + path);
            }

            FileOutputStream fs = context.openFileOutput(fileName, Context.MODE_PRIVATE);

            OutputStreamWriter osw = new OutputStreamWriter(fs, "UTF-8");
            String s = "KEY = " + KEY + "\n"
                    + "BATTERY_KEY = " + BATTERY_KEY + "\n"
                    + "HEART_RATE_ID = " + HEART_RATE_ID + "\n"
                    + "MaxBpmAlarm = " + MaxBpmAlarm + "\n"
                    + "MinBpmAlarm = " + MinBpmAlarm + "\n"
                    + "Min_TIMER = " + Min_TIMER + "\n"
                    + "Hour_TIMER = " + Hour_TIMER + "\n";

       /*     osw.append("KEY = " + KEY + "\n"
                    + "BATTERY_KEY = " + BATTERY_KEY + "\n"
                    + "HEART_RATE_ID = " + HEART_RATE_ID + "\n"
                    + "MaxBpmAlarm = " + MaxBpmAlarm + "\n"
                    + "MinBpmAlarm = " + MinBpmAlarm + "\n"
                    + "Min_TIMER = " + Min_TIMER + "\n"
                    + "Hour_TIMER = " + Hour_TIMER + "\n");
            osw.flush();
            osw.close();*/

            fs.write(s.getBytes());
            fs.close();
            // Tell the media scanner about the new file so that it is
            // immediately available to the user.
            MediaScannerConnection.scanFile(this,
                    new String[]{file.toString()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });

            Toast.makeText(getBaseContext(),
                    "Done writing SD 'mysdfile.txt'",
                    Toast.LENGTH_SHORT).show();
            Log.v("writing file", "Done writing to path " + path);
        } catch (FileNotFoundException e) {
            Log.v("write file not found", e.getMessage());
        } catch (UnsupportedEncodingException e) {
            Log.v("UnsupportedEncoding", e.getMessage());
        } catch (IOException e) {
            Log.v("IO exception", e.getMessage());
        }

    }
}
