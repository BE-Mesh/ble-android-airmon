package it.sapienza.netlab.airmon;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import it.sapienza.netlab.airmon.common.Constants;
import it.sapienza.netlab.airmon.common.Utility;
import it.sapienza.netlab.airmon.listeners.ServerScanCallback;

import static it.sapienza.netlab.airmon.common.Utility.isBLESupported;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    public static final int REQUEST_ENABLE_BT = 322;
    private static final long SCAN_PERIOD = 5000;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 456;
    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private static final String TAG = MainActivity.class.getSimpleName();

    private TextView debugger;
    private Button startScanButton;
    private Button sendMessageButton;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Provides access to the Location Settings API.
     */
    private SettingsClient mSettingsClient;
    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private LocationRequest mLocationRequest;

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private LocationSettingsRequest mLocationSettingsRequest;

    /**
     * Callback for Location events.
     */
    private LocationCallback mLocationCallback;

    /**
     * Represents a geographical location.
     */
    private Location mCurrentLocation;
    private String mTimestamp;


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private boolean isMultipleAdvertisementSupported;
    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean mRequestingLocationUpdates = false;

    private ServerScanCallback serverScanCallback;
    private BluetoothGattCallback mGattCallback;
    BluetoothGatt mGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        debugger = findViewById(R.id.debugger);
        startScanButton = findViewById(R.id.startService);
        sendMessageButton = findViewById(R.id.sendMessage);

        startScanButton.setOnClickListener(v -> startScan());
        sendMessageButton.setOnClickListener(view -> sendMessage());
        cleanDebug();
        askPermissions(savedInstanceState);


        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        // Kick off the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationCallback();
        createLocationRequest();
        buildLocationSettingsRequest();

        // Start the scan
        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        serverScanCallback = new ServerScanCallback(new ServerScanCallback.OnServerFoundMessageListener() {
            @Override
            public void OnServerFound(String message) {
                writeDebug(message);
            }

            @Override
            public void OnErrorScan(String message, int errorCodeCallback) {
                writeErrorDebug(message);
            }
        });

        mGattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true;
                    writeDebug("Connected to GATT client. Attempting to start service discovery from " + gatt.getDevice().getAddress());
                    boolean res = gatt.discoverServices();
                    writeDebug("onConnectionStateChange: discover services: " + res);
                    mGatt = gatt;
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false;
                    writeDebug("onConnectionStateChange: disconnected");
                }
                super.onConnectionStateChange(gatt, status, newState);
            }


            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                writeDebug("I discovered a service");
                super.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    writeDebug("I wrote a characteristic");
                } else {
                    writeErrorDebug("Error in writing in characteristic");
                }
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

        };
        writeDebug("Start update of the location");
        mRequestingLocationUpdates = true;
        startLocationUpdates();
    }

    private void sendMessage() {
        if (!isConnected) {
            // for now only to the first server, can be better in future
            mGatt = serverScanCallback.getResults().get(0).getDevice().connectGatt(this, false, mGattCallback);
        } else {

            writeDebug("Services available");
            for (BluetoothGattService service : mGatt.getServices()) {
                writeDebug(service.getUuid().toString());
            }

            writeDebug("Getting location service");
            BluetoothGattService locationService = mGatt.getService(Constants.LocationServiceUUID);
            if (locationService == null) {
                writeErrorDebug("Location Service is null, try again");
                return;
            }

            writeDebug("Characteristics available on Location service");
            for (BluetoothGattCharacteristic characteristic : locationService.getCharacteristics()) {
                writeDebug(characteristic.getUuid().toString());
            }

            BluetoothGattCharacteristic latitudeCharacteristic = locationService.getCharacteristic(Constants.CharacteristicLatitudeUUID);
            if (latitudeCharacteristic == null) {
                writeErrorDebug("Latitude Characteristic is null, try again");
                return;
            }
            writeDebug("Writing on latitude char");
            latitudeCharacteristic.setValue(String.valueOf(mCurrentLocation.getLatitude()));
            mGatt.writeCharacteristic(latitudeCharacteristic);

            BluetoothGattCharacteristic longitudeCharacteristic = locationService.getCharacteristic(Constants.CharacteristicLongitudeUUID);
            if (longitudeCharacteristic == null) {
                writeErrorDebug("Longitude Characteristic is null, try again");
                return;
            }
            writeDebug("Writing on longitude char");
            longitudeCharacteristic.setValue(String.valueOf(mCurrentLocation.getLongitude()));
            mGatt.writeCharacteristic(longitudeCharacteristic);

            writeDebug("Getting time service");
            BluetoothGattService timeService = mGatt.getService(Constants.TimeServiceUUID);
            if (timeService == null) {
                writeErrorDebug("Time Service is null, try again");
                return;
            }

            writeDebug("Characteristics available on Time service");
            for (BluetoothGattCharacteristic characteristic : timeService.getCharacteristics()) {
                writeDebug(characteristic.getUuid().toString());
            }

            // Ora riprendiamo la caratteristica, come abbiamo fatto prima, ma per il tempo
            BluetoothGattCharacteristic timeCharacteristic = timeService.getCharacteristic(Constants.CharacteristicTimestampUUID);
            if (timeCharacteristic == null) {
                writeErrorDebug("Time Characteristic is null, try again");
                return;
            }
            writeDebug("writing on time char");
            timeCharacteristic.setValue(mTimestamp.getBytes());
            mGatt.writeCharacteristic(timeCharacteristic);

            writeDebug("I wrote all the characteristics");
        }
    }


    private void startScan() {
        writeDebug("Start scan");
        if (isConnected) {
            mGatt.disconnect();
            isConnected = false;
        }
        if (!isScanning) {
            // Stops scanning after a pre-defined scan period.
            new Handler().postDelayed(this::stopScan, SCAN_PERIOD);
            serverScanCallback.clearResults();
            isScanning = true;
            bluetoothLeScanner.startScan(Utility.buildScanFilters(), Utility.buildScanSettings(), serverScanCallback);
            // bluetoothLeScanner.startScan(serverScanCallback);
        } else {
            isScanning = false;
            bluetoothLeScanner.stopScan(serverScanCallback);
        }

    }

    private void stopScan() {
        isScanning = false;
        bluetoothLeScanner.stopScan(serverScanCallback);
        writeDebug("Scan Stop");
        for (ScanResult result : serverScanCallback.getResults()) {
            writeDebug("Address: " + result.getDevice().getAddress() + ", UUIDs Found " + result.getScanRecord().getServiceUuids());
        }
        if (serverScanCallback.getResults().size() == 0) {
            writeDebug("No server founds, check the other device.");
        }
        writeDebug("Scan operations completed.");
    }


    private void askPermissions(Bundle savedInstanceState) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            checkBluetoothAvailability(savedInstanceState);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
        }
    }

    /**
     * Cattura la risposta asincrona di richiesta dei permessi e se è tutto ok passa a controllare il bluetooth
     *
     * @param requestCode  codice richiesta ( per coarse location = PERMISSION_REQUEST_COARSE_LOCATION )
     * @param permissions  permessi richiesti. NB If request is cancelled, the result arrays are empty.
     * @param grantResults int [] rappresentati gli esiti delle richieste
     */

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    checkBluetoothAvailability();
                } else {
                    writeErrorDebug("onRequestPermissionsResult: Permission denied");
                }
                break;
            default:
                writeErrorDebug("Case not found.");
        }
    }

    /**
     * Controlla che il cellulare supporti l'app e il multiple advertisement. Maschera per onActivityResult e onRequestPermissionsResult
     */
    private void checkBluetoothAvailability() {
        checkBluetoothAvailability(null);
    }


    /**
     * Controlla che il cellulare supporti l'app e il multiple advertisement.
     *
     * @param savedInstanceState se l'app era già attiva non devo reinizializzare tutto
     */
    private void checkBluetoothAvailability(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager != null) {
                mBluetoothAdapter = mBluetoothManager.getAdapter();

                // Is Bluetooth turned on?
                if (mBluetoothAdapter.isEnabled() && isBLESupported(this)) {
                    // Are Bluetooth Advertisements supported on this device?
                    if (mBluetoothAdapter.isMultipleAdvertisementSupported()) {
                        writeDebug("Everything is supported and enabled");
                        isMultipleAdvertisementSupported = true;
                    } else {
                        isMultipleAdvertisementSupported = false;
                        writeDebug("Your device does not support multiple advertisement, you can be only client");
                    }
                } else {
                    // Prompt user to turn on Bluetooth (logic continues in onActivityResult()).
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                // Bluetooth is not supported.
                writeErrorDebug("Bluetooth is not supported");
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS) {

            switch (resultCode) {
                case Activity.RESULT_OK:
                    writeDebug("GPS OK");
                    break;
                case Activity.RESULT_CANCELED:
                    writeErrorDebug("GPS request was cancelled.");
                    break;
            }

        } else if (requestCode == REQUEST_ENABLE_BT) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    checkBluetoothAvailability();
                    break;
                case Activity.RESULT_CANCELED:
                    Log.e(TAG, "Bluetooth is not enabled. Please reboot application.");
                    break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        mRequestingLocationUpdates = false;
        if (isConnected) {
            mGatt.disconnect();
            isConnected = false;
        }
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
//                writeDebug("New location found");
                mCurrentLocation = locationResult.getLastLocation();
                DateFormat dateformat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss", Locale.ITALY);
                mTimestamp = dateformat.format(new Date());
            }
        };
    }

    /**
     * Uses a {@link com.google.android.gms.location.LocationSettingsRequest.Builder} to build
     * a {@link com.google.android.gms.location.LocationSettingsRequest} that is used for checking
     * if a device has the needed location settings.
     */
    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                writeErrorDebug("Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    writeErrorDebug("PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                writeErrorDebug(errorMessage);
                                mRequestingLocationUpdates = false;
                        }
                    }
                });
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private void stopLocationUpdates() {
        if (!mRequestingLocationUpdates) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        mRequestingLocationUpdates = false;
                    }
                });
    }

    /**
     * Clean the field debugger
     */
    private void cleanDebug() {
        runOnUiThread(() -> debugger.setText(""));
    }

    /**
     * Write a message debug into log and text debugger. The message will be logged into the debug logger.
     *
     * @param message message to be written
     */
    private void writeDebug(final String message) {
        runOnUiThread(() -> {
            if (debugger.getLineCount() == debugger.getMaxLines())
                debugger.setText(String.format("%s\n", message));
            else
                debugger.setText(String.format("%s%s\n", debugger.getText(), message));
        });
        Log.d(TAG, message);
    }

    /**
     * Write a message debug into log and text debugger. The message will be logged into the error logger.
     *
     * @param message message to be written
     */
    private void writeErrorDebug(final String message) {
        runOnUiThread(() -> {
            if (debugger.getLineCount() == debugger.getMaxLines())
                debugger.setText(String.format("%s\n", message));
            else
                debugger.setText(String.format("%s%s\n", debugger.getText(), message));
        });
        Log.e(TAG, message);
    }

}