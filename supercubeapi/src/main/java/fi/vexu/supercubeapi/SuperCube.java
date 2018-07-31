package fi.vexu.supercubeapi;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

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
public class SuperCube implements Parcelable {
    private final static String TAG = SuperCube.class.getSimpleName();

    private SuperCubeService mSuperCubeService;
    private Context mContext;
    private static final String STATE_STRINGS[] = {"Disconnected", "Connecting", "Connected"};

    private SuperCubeListener mSuperCubeListener;

    private String mCubeName;
    private String mCubeAddress;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mSuperCubeService = ((SuperCubeService.LocalBinder) service).getService();
            mSuperCubeService.setSuperCubeListener(mSuperCubeListener);
            if (!mSuperCubeService.connect(mContext, mCubeAddress)) {
                Log.e(TAG, "Connection failed");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSuperCubeService = null;
        }
    };

    public void connect(Context context) {
        if (mCubeName == null || mCubeName.indexOf("GiC") != 0) {
            Log.e(TAG, "Invalid name");
            return;
        }

        mContext = context;
        mContext.bindService(new Intent(mContext, SuperCubeService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
    }

    public void disconnect() {
        if (mSuperCubeService != null) {
            mSuperCubeService.disconnect();
            mContext.unbindService(mServiceConnection);
        }
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
        return mSuperCubeService.getCubeState();
    }

    public String getCubeName() {
        return mCubeName;
    }

    public String getCubeAddress() {
        return mCubeName;
    }

    public String getConnectionState() {
        return STATE_STRINGS[(mSuperCubeService == null) ? 0 : mSuperCubeService.getConnectionState()];
    }

    public void setCubeNameAddress(String name, String address) {
        if (mContext != null) {
            mSuperCubeService.disconnect();
        }
        mCubeName = name;
        mCubeAddress = address;
    }

    //info requests, return true if request was sent
    public void requestMoveCount() {
        mSuperCubeService.requestMoveCount();
    }

    public void requestBatteryLevel() {
        mSuperCubeService.requestBatteryLevel();
    }

    public void resetSolved() {
        mSuperCubeService.resetSolved();
    }

    public void resetCustom(byte[] state) { //todo
        mSuperCubeService.resetCustom(state);
    }

    public void requestMystery1() {
        mSuperCubeService.requestMystery1();
    }

    public void requestMystery2() {
        mSuperCubeService.requestMystery2();
    }

    public void requestMystery3() {
        mSuperCubeService.requestMystery3();
    }

    public void requestMystery4() {
        mSuperCubeService.requestMystery4();
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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mCubeName);
        out.writeString(mCubeAddress);
        if (mContext != null)
            mContext.unbindService(mServiceConnection);
    }

    protected SuperCube(Parcel in) {
        mCubeName = in.readString();
        mCubeAddress = in.readString();
    }

    public static final Creator<SuperCube> CREATOR = new Creator<SuperCube>() {
        @Override
        public SuperCube createFromParcel(Parcel in) {
            return new SuperCube(in);
        }

        @Override
        public SuperCube[] newArray(int size) {
            return new SuperCube[size];
        }
    };
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