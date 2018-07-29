package fi.vexu.supercubeapi;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import fi.vexu.supercubeapi.SuperCube.SuperCube;
import fi.vexu.supercubeapi.SuperCube.SuperCubeListener;

public class DetailsActivity extends AppCompatActivity {
    private final static String TAG = DetailsActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView textViewState;

    private SuperCube mSuperCube;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        final Intent intent = getIntent();
        String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        String mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        getSupportActionBar().setTitle(mDeviceName);

        textViewState = findViewById(R.id.textState);

        final Button getMoves = findViewById(R.id.getmoves);
        final Button getBattery = findViewById(R.id.getbattery);
        final Button getMystery1 = findViewById(R.id.getmystery1);
        final Button getMystery2 = findViewById(R.id.getmystery2);
        final Button getMystery3 = findViewById(R.id.getmystery3);
        final Button getMystery4 = findViewById(R.id.getmystery4);
        final Button resetSolved = findViewById(R.id.resetsolved);
        //final Button resetCustom = findViewById(R.id.resetcustom);
        final TextView cubeState = findViewById(R.id.cubestatus);
        final TextView cubeInfo = findViewById(R.id.cubeinfo);


        mSuperCube = new SuperCube(mDeviceName, mDeviceAddress);
        mSuperCube.setSuperCubeListener(new SuperCubeListener() {
            @Override
            public void onStatusReceived() {
                cubeState.setText(SuperCube.bytesToString(mSuperCube.getCubeState()));
            }

            @Override
            public void onBatteryReceived(int battery) {
                cubeInfo.setText("Battery level: " + battery);
            }

            @Override
            public void onMovesReceived(int moves) {
                cubeInfo.setText("Total moves: " + moves);
            }

            @Override
            public void onResetReceived(byte[] response) {
                cubeInfo.setText("Cube reset: " + SuperCube.bytesToString(response));
            }

            @Override
            public void onOtherReceived(byte[] response) {
                cubeInfo.setText("Other received: " + SuperCube.bytesToString(response));
            }

            @Override
            public void onCubeReady() {
                getMoves.setClickable(true);
                getBattery.setClickable(true);
                getMystery1.setClickable(true);
                getMystery2.setClickable(true);
                getMystery3.setClickable(true);
                getMystery4.setClickable(true);
                resetSolved.setClickable(true);
                //resetCustom.setClickable(true); disabled
            }

            @Override
            public void onConnectionStateUpdated(String state) {
                textViewState.setText(state);
            }
        });
        if (!mSuperCube.connect(this)) {
            Log.e(TAG, "SuperCube connection failed");
        }

        getMoves.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestMoveCount();
            }
        });
        getBattery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestBatteryLevel();
            }
        });
        getMystery1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestMystery1();
            }
        });
        getMystery2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestMystery2();
            }
        });
        getMystery3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestMystery3();
            }
        });
        getMystery4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.requestMystery4();
            }
        });
        resetSolved.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSuperCube.resetSolved();
            }
        });
        /*resetCustom.setOnClickListener(new View.OnClickListener() { //disabled
            @Override
            public void onClick(View view) {
                mSuperCube.resetCustom();
            }
        });*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSuperCube.close();
    }
}