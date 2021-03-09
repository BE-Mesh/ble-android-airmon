#  BLE Android Air monitoring

## Project description

This project involves the connection between ESP32 and 1 or more Android phones that send the following data to ESP32:
- Latitude
- Longitude
- Time

## Getting Started

These instructions will get you a copy of the project up and running on your device for development and testing purposes.

### Prerequisites

- Android Studio
- Android phone

### Installing using Android Studio

- `git clone https://github.com/BE-Mesh/ble-android-airmon.git`
- Open Android Studio. After that you Click on `Open an existing Android Studio project`
- After that select the file location where you clone the repository. Then select `ble-android-airmon` project and then click OK
- From the target device drop-down menu, select the device that you want to run your app on
- Click `Run`

### Installing using Android apk

- Download the apk file from [here](https://github.com/BE-Mesh/ble-android-airmon/blob/master/airmon.apk)
- Navigate to your phone settings menu then to the security settings. Enable the Install from Unknown Sources option
- Use a file browser and navigate to your download folder. Tap the APK to begin the installation process
- The app should safely install

## How it works

- Once the app has been installed it will ask for permission to turn on the GPS and Bluetooth and at the first start you will also be asked to be able to access the latter;
- If everything has been done correctly, `Everything is supported and enabled` will be shown on the screen;
- Pressing the `Start Scan` button starts the scan to search for the server, the search phase has been filtered in such a way that it searches for the server with those specific ServiceUUIDs;
- Once the server is found, the GATT will be shown;
- Pressing the `Connect` button will attempt to connect to the server;
- If the connection is successful, the services (with the selected UUIDs) will be shown and we connect;
- Pressing the "Send Message" button we will write on the three characteristics (Latitude, Longitude and Time).

N.B. If the search is restarted the `onStateChange` will change from `Connected` to `Disconnected`. 

## Presentation
You can find the presentation of the project [here](https://github.com/BE-Mesh/ble_esp_airmon/blob/main/Presentation/PresentationProjectBLE.ppt)

## Developers
- [Alessio Amatucci](https://github.com/Alexius22)
- [Federica Di Giacomo](https://github.com/Federicadgc)
- [Alessandro Paniccià](https://github.com/Hoken-rgb)
