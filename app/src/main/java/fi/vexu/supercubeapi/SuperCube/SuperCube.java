package fi.vexu.supercubeapi.SuperCube;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created 28.7.2018.
 *
 * Copyright 2018 Vexu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class SuperCube {
    private final static String TAG = SuperCube.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static String STATE_STRINGS[] = {"Disconnected", "Connecting", "Connected"};

    private static final UUID CUBE_STATE_SERVICE = UUID.fromString("0000aadb-0000-1000-8000-00805f9b34fb");
    private static final UUID CUBE_STATE_RESPONSE = UUID.fromString("0000aadc-0000-1000-8000-00805f9b34fb");

    private static final UUID CUBE_INFO_SERVICE = UUID.fromString("0000aaaa-0000-1000-8000-00805f9b34fb");
    private static final UUID CUBE_INFO_RESPONSE = UUID.fromString("0000aaab-0000-1000-8000-00805f9b34fb");
    private static final UUID CUBE_INFO_REQUEST = UUID.fromString("0000aaac-0000-1000-8000-00805f9b34fb");

    private static final UUID CLIENT_CHARACTERISTIC_UUID= UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private int mNotificationsEnabled = 0;

    private BluetoothGattCharacteristic mInfoRequest;
    private BluetoothGattCharacteristic mInfoResponse;
    private BluetoothGattCharacteristic mStatusResponse;


    //commands for cube info service
    private static final int WRITE_MOVE_COUNT = 0xCC;
    private static final int WRITE_RESET_SOLVED = 0xA1;
    private static final int WRITE_RESET_CUSTOM = 0xA4;
    private static final int WRITE_BATTERY =  0xB5;

    //not sure what these do
    private static final int WRITE_MYSTERY_1 = 0xB6; //no response
    private static final int WRITE_MYSTERY_2 = 0xB7; //might be firmware version
    private static final int WRITE_MYSTERY_3 = 0xB8; //no response
    private static final int WRITE_MYSTERY_4 = 0xBA; //no request sometimes

    private SuperCubeListener mSuperCubeListener;

    private String mCubeName;
    private String mCubeAddress;

    //explained here https://old.reddit.com/r/Cubers/comments/910iya/xiaomi_giiker_bluetooth_cube_improved_app/e2xpdh5/?context=3
    private byte[] mCurrentState;


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
            }

            if (mSuperCubeListener != null) {
                mSuperCubeListener.onConnectionStateUpdated(STATE_STRINGS[newState]);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                enableNotification(gatt);
            } else {
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite (BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            enableNotification(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte data[] = characteristic.getValue();
            Log.d(TAG, Arrays.toString(data));
            if (mSuperCubeListener == null) {
                return;
            }

            if (characteristic.equals(mStatusResponse)) {
                mCurrentState = data;
                mSuperCubeListener.onStatusReceived();
            } else  if (characteristic.equals(mInfoResponse)) {
                switch (data[0]) {
                    case (byte) WRITE_BATTERY:
                        mSuperCubeListener.onBatteryReceived(data[1]);
                        break;
                    case (byte) WRITE_MOVE_COUNT:
                        byte b[] = characteristic.getValue();
                        mSuperCubeListener.onMovesReceived(
                                b[4] & 0xFF | (b[3] & 0xFF) << 8 |
                                        (b[2] & 0xFF) << 16 | (b[1] & 0xFF) << 24);
                        break;
                    case (byte) WRITE_RESET_CUSTOM:
                        mSuperCubeListener.onResetReceived(data);
                        break;
                    case (byte) WRITE_RESET_SOLVED:
                        mSuperCubeListener.onResetReceived(data);
                        break;
                    default:
                        mSuperCubeListener.onOtherReceived(data);
                }
            }
        }
    };

    private void enableNotification(BluetoothGatt gatt) {
        if (mNotificationsEnabled == 0) {
            BluetoothGattService cubeStateService = mBluetoothGatt.getService(CUBE_STATE_SERVICE);
            if (cubeStateService == null) {
                Log.e(TAG, "cube status service not found");
                disconnect();
                return;
            }
            mStatusResponse = cubeStateService.getCharacteristic(CUBE_STATE_RESPONSE);
            if (mStatusResponse == null) {
                Log.e(TAG, "cube status characteristic not found");
                disconnect();
                return;
            }


            if (!enableNotifications(mStatusResponse)) {
                Log.e(TAG, "Could not enable notifications");
                disconnect();
                return;
            }

            BluetoothGattDescriptor state = mStatusResponse.getDescriptor(CLIENT_CHARACTERISTIC_UUID);
            state.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(state);

            mNotificationsEnabled++;
        } else if (mNotificationsEnabled == 1) {
            BluetoothGattService cubeInfoService = mBluetoothGatt.getService(CUBE_INFO_SERVICE);
            if (cubeInfoService == null) {
                Log.e(TAG, "cube info service not found");
                disconnect();
                return;
            }
            mInfoResponse = cubeInfoService.getCharacteristic(CUBE_INFO_RESPONSE);
            mInfoRequest = cubeInfoService.getCharacteristic(CUBE_INFO_REQUEST);
            if (mInfoResponse == null || mInfoRequest == null) {
                Log.e(TAG, "cube info characteristics not found");
                disconnect();
                return;
            }


            if (!enableNotifications(mStatusResponse)) {
                Log.e(TAG, "Could not enable notifications");
                disconnect();
                return;
            }

            enableNotifications(mInfoResponse);

            BluetoothGattDescriptor info = mInfoResponse.getDescriptor(CLIENT_CHARACTERISTIC_UUID);
            info.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(info);

            if (mSuperCubeListener != null) {
                mSuperCubeListener.onCubeReady();
            }

            mNotificationsEnabled++;
        }
    }


    private boolean enableNotifications(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        return mBluetoothGatt.setCharacteristicNotification(characteristic, true);
    }

    private boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    public boolean connect(Context context) {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (mCubeName == null || mCubeName.indexOf("GiC") != 0) {
            Log.e(TAG, "Invalid name");
            return false;
        }

        if (mCubeAddress == null) {
            Log.e(TAG, "Unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mCubeAddress);
        if (device == null) {
            Log.e(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
        mNotificationsEnabled = 0;
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
        mNotificationsEnabled = 0;
    }


    public SuperCube(String name, String address) {
        mCubeName = name;
        mCubeAddress = address;
    }

    public void setSuperCubeListener(SuperCubeListener listener) {
        mSuperCubeListener = listener;
    }

    //todo improve cube STATE_STRINGS
    public byte[] getCubeState() {
        return mCurrentState;
    }

    public String getCubeName() {
        return mCubeName;
    }

    public String getCubeAddress() {
        return mCubeName;
    }

    public void setCubeNameAddress(String name, String address) {
        if (mConnectionState != STATE_DISCONNECTED) {
            disconnect();
        }
        mCubeName = name;
        mCubeAddress = address;
    }

    //info requests, return true if request was sent
    public boolean requestMoveCount() {
        return writeCommand(WRITE_MOVE_COUNT);
    }

    public boolean requestBatteryLevel() {
        return writeCommand(WRITE_BATTERY);
    }

    public boolean resetSolved() {
        return writeCommand(WRITE_RESET_SOLVED);
    }

    public boolean resetCustom(byte[] state) { //todo
        return writeCommand(WRITE_RESET_CUSTOM, state);
    }

    public boolean requestMystery1() {
        return writeCommand(WRITE_MYSTERY_1);
    }

    public boolean requestMystery2() {
        return writeCommand(WRITE_MYSTERY_2);
    }

    public boolean requestMystery3() {
        return writeCommand(WRITE_MYSTERY_3);
    }

    public boolean requestMystery4() {
        return writeCommand(WRITE_MYSTERY_4);
    }


    //write command to CUBE_INFO_REQUEST and return response from CUBE_INFO_RESPONSE
    private boolean writeCommand(int command) {
        return writeCommand(new byte[] {(byte) command});
    }

    private boolean writeCommand(int command, byte[] data) {
        if (data.length != 16) {
            Log.e(TAG, "Cube status info is not long enough");
            return false;
        }
        byte com[] = new byte[17];
        com[0] = (byte) command;
        System.arraycopy(data, 0, com, 1, 16);

        return writeCommand(com);
    }

    private boolean writeCommand(byte command[]) {
        if (mConnectionState != STATE_CONNECTED) {
            Log.e(TAG, "Cube info request before connection");
            return false;
        }
        if (mInfoRequest == null) {
            Log.e(TAG, "mInfoRequest is null");
            return false;
        }

        mInfoRequest.setValue(command);
        if(!writeCharacteristic(mInfoRequest)) {
            Log.e(TAG, "Cube info characteristic write failed");
            return false;
        }
        return true;
    }


    public static String bytesToString(byte[] arr) {
        if (arr != null) {
            StringBuilder string = new StringBuilder();
            for(byte byteChar : arr) {
                string.append(String.format("%02X ", byteChar));
            }
            return string.toString();
        }
        return "";
    }
}


// SuperCube's bluetooth services
// "00001800-0000-1000-8000-00805f9b34fb"
//      GiC98658 - 4769433938363538 // name
//      0000
//      2000300005005802
// "00001801-0000-1000-8000-00805f9b34fb"
// "0000180a-0000-1000-8000-00805f9b34fb"
//      GiCube.Co.Ltd47 - 69437562652E436F2E4C7464 //manufacturer
// "0000180f-0000-1000-8000-00805f9b34fb" //battery
//      7 - 37 //always 37 for some reason
// "0000aadb-0000-1000-8000-00805f9b34fb"
//      // current state 20 bytes
// "0000aaaa-0000-1000-8000-00805f9b34fb"
//      aaab info response
//      aaac info request
// "00001530-1212-efde-1523-785feabcd123"
//      ?
//      ?
//       - 0100

// ["?", "B", "D", "L", "U", "R", "F"][face]