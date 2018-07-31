package fi.vexu.supercubeapi;

import android.app.Service;
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
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created 31.7.2018.
 *
 * Copyright 2018 Vexu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class SuperCubeService extends Service {
    private final static String TAG = SuperCubeService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

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

    private SuperCubeListener mSuperCubeListener;

    //explained in state.txt
    private byte[] mCurrentState;


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


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                updateConnectionState(STATE_CONNECTED);
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateConnectionState(STATE_DISCONNECTED);
                Log.i(TAG, "Disconnected from GATT server.");
                mNotificationsEnabled = 0;
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


            if (!mBluetoothGatt.setCharacteristicNotification(mStatusResponse, true)) {
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


            if (!mBluetoothGatt.setCharacteristicNotification(mInfoResponse, true)) {
                Log.e(TAG, "Could not enable notifications");
                disconnect();
                return;
            }


            BluetoothGattDescriptor info = mInfoResponse.getDescriptor(CLIENT_CHARACTERISTIC_UUID);
            info.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(info);

            if (mSuperCubeListener != null) {
                mSuperCubeListener.onCubeReady();
            }

            mNotificationsEnabled++;
        }
    }

    boolean connect(Context context, String address) {
        //make sure device is not already connected
        if (mConnectionState != STATE_DISCONNECTED) {
            return true;
        }

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        if (address == null) {
            Log.e(TAG, "Unspecified address.");
            return false;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.e(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        mBluetoothGatt = device.connectGatt(context, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        updateConnectionState(STATE_CONNECTING);
        return true;
    }

    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mNotificationsEnabled = 0;
        updateConnectionState(STATE_DISCONNECTED);
    }

    class LocalBinder extends Binder {
        SuperCubeService getService() {
            return SuperCubeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new LocalBinder();


    void setSuperCubeListener(SuperCubeListener listener) {
        mSuperCubeListener = listener;
    }

    byte[] getCubeState() {
        return mCurrentState;
    }

    int getConnectionState() {
        return mConnectionState;
    }

    void requestMoveCount() {
        writeCommand(WRITE_MOVE_COUNT);
    }

    void requestBatteryLevel() {
        writeCommand(WRITE_BATTERY);
    }

    void resetSolved() {
        writeCommand(WRITE_RESET_SOLVED);
    }

    void resetCustom(byte[] state) {
        writeCommand(WRITE_RESET_CUSTOM, state);
    }

    void requestMystery1() {
        writeCommand(WRITE_MYSTERY_1);
    }

    void requestMystery2() {
        writeCommand(WRITE_MYSTERY_2);
    }

    void requestMystery3() {
        writeCommand(WRITE_MYSTERY_3);
    }

    void requestMystery4() {
        writeCommand(WRITE_MYSTERY_4);
    }

    //write command to CUBE_INFO_REQUEST and return response from CUBE_INFO_RESPONSE
    private void writeCommand(int command) {
        writeCommand(new byte[] {(byte) command});
    }

    private void writeCommand(int command, byte[] data) {
        if (data.length != 16) {
            Log.e(TAG, "Cube status info is not long enough");
            return;
        }
        byte com[] = new byte[17];
        com[0] = (byte) command;
        System.arraycopy(data, 0, com, 1, 16);

        writeCommand(com);
    }

    private void writeCommand(byte command[]) {
        if (mConnectionState != STATE_CONNECTED) {
            Log.e(TAG, "Cube info request before connection");
            return;
        }
        if (mInfoRequest == null) {
            Log.e(TAG, "mInfoRequest is null");
            return;
        }

        mInfoRequest.setValue(command);
        if(!mBluetoothGatt.writeCharacteristic(mInfoRequest)) {
            Log.e(TAG, "Cube info characteristic write failed");
        }
    }

    private void updateConnectionState(int state) {
        mConnectionState = state;
        if (mSuperCubeListener !=  null)
            mSuperCubeListener.onConnectionStateUpdated();
    }
}
