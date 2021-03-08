package it.sapienza.netlab.airmon.tasks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

import it.sapienza.netlab.airmon.common.Constants;
import it.sapienza.netlab.airmon.listeners.Listeners;
import it.sapienza.netlab.airmon.models.Server;


public class ConnectBLETask {
    private final static String TAG = ConnectBLETask.class.getName();
    private Server server;
    private BluetoothGattCallback mGattCallback;
    private BluetoothGatt mGatt;
    private Context context;
    private String id;
    private boolean temporaryClient;
    private Listeners.OnJobDoneListener onJobDoneListener;
    private boolean jobDone = false;
    private Listeners.OnConnectionLost OnConnectionLostListener;
    private int maxAttempt;
    private byte[] latitude = null, longitude = null, timestamp = null;
    private boolean isConnected = false;

    public ConnectBLETask(Server server, final Context context) {
        // GATT OBJECT TO CONNECT TO A GATT SERVER
        maxAttempt = 0;
        this.context = context;
        this.server = server;
        this.id = null;

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    Log.d(TAG, "Connected to GATT client. Attempting to start service discovery from " + gatt.getDevice().getName());
                    boolean res = gatt.discoverServices();
                    Log.d(TAG, "onConnectionStateChange: discover services :" + res);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    Log.d(TAG, "onConnectionStateChange: disconnected");
                }
                super.onConnectionStateChange(gatt, status, newState);
            }


            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                Log.d(TAG, "GATT: " + gatt.toString());
                Log.d(TAG, "Service discovered");

                super.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    if (characteristic.getUuid().equals(Constants.CharacteristicLatitudeUUID)) {

                        Log.d(TAG, "Latitude characteristic value has been written");
                        BluetoothGattService service = gatt.getService(Constants.LocationServiceUUID);
                        if (service == null) {
                            return;
                        }
                        Log.d(TAG, "Service : " + service.getUuid().toString());
                        BluetoothGattCharacteristic charact = service.getCharacteristic(Constants.CharacteristicLongitudeUUID);
                        if (charact == null) {
                            return;
                        }
                        charact.setValue(longitude);
                        gatt.writeCharacteristic(charact);
                        longitude = null;
                    } else if (characteristic.getUuid().equals(Constants.CharacteristicLongitudeUUID)){
                        Log.d(TAG, "Longitude characteristic value has been written");
                        BluetoothGattService service = gatt.getService(Constants.TimeServiceUUID);
                        if (service == null) {
                            return;
                        }
                        Log.d(TAG, "Service : " + service.getUuid().toString());
                        BluetoothGattCharacteristic charact = service.getCharacteristic(Constants.CharacteristicTimestampUUID);
                        if (charact == null) {
                            return;
                        }
                        Date currentTime = Calendar.getInstance().getTime();
                        DateFormat dateformat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss", Locale.ITALY);
                        timestamp = dateformat.format(currentTime).getBytes();

                        charact.setValue(timestamp);
                        gatt.writeCharacteristic(charact);
                        timestamp = null;
                    } else {
                        Log.d(TAG, "All characteristics values has been written");
                    }
                }
                super.onCharacteristicWrite(gatt, characteristic, status);
            }
        };
    }


    public boolean IsDeviceConnected(){
        return this.isConnected;
    }

    public void sendMessage(byte[] latitude, byte[] longitude, byte[] timestamp) {

        BluetoothGattService service = mGatt.getService(Constants.LocationServiceUUID);
        if (service == null) {
            return;
        }
        Log.d(TAG, "Service : " + service.getUuid().toString());

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(Constants.CharacteristicLatitudeUUID);
        if (characteristic == null) {
            return;
        }
        this.longitude = longitude;
        this.timestamp = timestamp;
        characteristic.setValue(latitude);
        mGatt.writeCharacteristic(characteristic);
    }

    public void sendMessage(String latitude, String longitude, String timestamp) {

        sendMessage(latitude.getBytes(), longitude.getBytes(), timestamp.getBytes());
    }

    public void startClient() {
        this.mGatt = server.getBluetoothDevice().connectGatt(context, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        server.setBluetoothGatt(this.mGatt);
        server.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        server.getBluetoothGatt().connect();

    }

    public void stopClient() {
                    mGatt.close();
                    mGatt = null;
    }

    public void setCallback(BluetoothGattCallback callback) {
        this.temporaryClient = true;
        this.mGattCallback = callback;
    }

    public void setJobDone() {
        jobDone = true;
        stopClient();
        if (this.onJobDoneListener != null) onJobDoneListener.OnJobDone();
    }

    public boolean getJobDone() {
        return jobDone;
    }

    public BluetoothGatt getmGatt() {
        return mGatt;
    }

    public void setOnJobDoneListener(Listeners.OnJobDoneListener l) {
        this.onJobDoneListener = l;
    }

    public void setOnConnectionLostListener(Listeners.OnConnectionLost l) {
        this.OnConnectionLostListener = l;
    }

    public void restartClient() {
        Log.d(TAG, "RestartClient");
        stopClient();
        maxAttempt++;
        if (maxAttempt == Constants.MAX_ATTEMPTS_RETRY) {
            Log.d(TAG, "RestartClient: stop");
            return;
        }
        startClient();
    }

    public int getMaxAttempt() {
        return maxAttempt;
    }
}

