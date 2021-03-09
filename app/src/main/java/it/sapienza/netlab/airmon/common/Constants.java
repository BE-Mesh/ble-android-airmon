package it.sapienza.netlab.airmon.common;

import android.os.ParcelUuid;

import java.util.UUID;

//Constants for use in the Bluetooth Advertisements sample

public class Constants {

    /**
     * UUID identified with this app - set as Service UUID for BLE Advertisements.
     * <p>
     * Bluetooth requires a certain format for UUIDs associated with Services.
     * The official specification can be found here:
     * {https://www.bluetooth.org/en-us/specification/assigned-numbers/service-discovery}
     */

    public static final UUID CharacteristicLocationUUID = UUID.fromString("00002a67-0000-1000-8000-00805f9b34fb");
    public static final UUID CharacteristicLatitudeUUID = UUID.fromString("00002aae-0000-1000-8000-00805f9b34fb");
    public static final UUID CharacteristicLongitudeUUID = UUID.fromString("00002aaf-0000-1000-8000-00805f9b34fb");
//    public static final UUID CharacteristicTimestampUUID = UUID.fromString("00002a08-0000-1000-8000-00805f9b34fb");         //This is the TimeStampUUID for nRF Connection
    public static final UUID CharacteristicTimestampUUID = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");         //This is the TimeStampUUID for ESP

    public static final UUID NotificationDescriptor = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final UUID LocationServiceUUID = UUID.fromString("00001819-0000-1000-8000-00805f9b34fb");
    public static final UUID TimeServiceUUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid LocationServiceParcelUUID = ParcelUuid.fromString("00001819-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid TimeServiceParcelUUID = ParcelUuid.fromString("00001805-0000-1000-8000-00805f9b34fb");


    public static final int MAX_ATTEMPTS_RETRY = 5;


}