package fi.vexu.supercubeapi;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class Main extends AppCompatActivity {
    private static final String TAG = Main.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private LocationManager mLocationManager;

    private boolean mScanning;

    private static final int REQUEST_BLUETOOTH= 1;
    private static final int REQUEST_LOCATION = 2;

    Button scanButton;
    ListView cubeListView;

    List<BluetoothDevice> cubes;
    CubeAdapter cubeAdapter;

    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "Bluetooth le is not supported");
            finish();
        }

        getBluetoothAdapterAndLeScanner();

        if (mBluetoothAdapter == null) {
            Log.e(TAG,"bluetoothManager is null");
            finish();
        }

        scanButton = findViewById(R.id.cubescan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice();
            }
        });
        cubeListView = findViewById(R.id.cubelist);

        if (savedInstanceState == null)
            cubes = new ArrayList<>();
        else
            cubes = savedInstanceState.getParcelableArrayList("cubes");
        cubeAdapter = new CubeAdapter(this, android.R.layout.simple_list_item_1, cubes);
        cubeListView.setAdapter(cubeAdapter);
        cubeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long idl) {
                final BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(position);
                final Intent intent = new Intent(Main.this, DetailsActivity.class);
                intent.putExtra(DetailsActivity.EXTRAS_DEVICE_NAME,  device.getName());
                intent.putExtra(DetailsActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());

                if (mScanning) {
                    mBluetoothLeScanner.stopScan(scanCallback);
                    mScanning = false;
                    scanButton.setEnabled(true);
                }
                startActivity(intent);
            }
        });

        mHandler = new Handler();
        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        final Intent intent = new Intent(Main.this, DetailsActivity.class);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){
        if(requestCode == REQUEST_LOCATION)
        {
            int grantResult = grantResults[0];

            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this)
                        .setMessage("Location permission is needed for bluetooth scan")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
                            }
                        }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        finish();
                    }
                }).create().show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || !mBluetoothAdapter.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setMessage("Bluetooth and location services need to be enabled to scan for devices")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (!mBluetoothAdapter.isEnabled()) {
                                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                                startActivityForResult(enableBluetooth, REQUEST_BLUETOOTH);
                            }


                            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                Intent enableLocation = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivityForResult(enableLocation, REQUEST_LOCATION);
                            }
                        }
                    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            finish();
                        }
                    }).create().show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_BLUETOOTH && resultCode == Activity.RESULT_CANCELED) || !mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            finish();
            return;
        }

        getBluetoothAdapterAndLeScanner();

        if (mBluetoothAdapter == null) {
            Log.e(TAG,"bluetoothManager is null");
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelableArrayList("cubes", (ArrayList<? extends Parcelable>) cubes);
    }

    private void getBluetoothAdapterAndLeScanner(){
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mScanning = false;
    }

    private void scanLeDevice() {
        cubes.clear();
        cubeListView.invalidateViews();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothLeScanner.stopScan(scanCallback);
                cubeListView.invalidateViews();

                Toast.makeText(Main.this, "Scan timeout", Toast.LENGTH_LONG).show();

                mScanning = false;
                scanButton.setEnabled(true);
            }
        }, SCAN_PERIOD);

        mBluetoothLeScanner.startScan(scanCallback);
        mScanning = true;
        scanButton.setEnabled(false);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addBluetoothDevice(result.getDevice());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult result : results){
                addBluetoothDevice(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Toast.makeText(Main.this, "onScanFailed: " + String.valueOf(errorCode),
                    Toast.LENGTH_LONG).show();
        }

        private void addBluetoothDevice(BluetoothDevice cube){
            if (cube.getName() != null && cube.getName().indexOf("GiC") == 0 && !cubes.contains(cube)){
                cubes.add(cube);
                cubeListView.invalidateViews();
            }
        }
    };
}

class CubeAdapter extends ArrayAdapter<BluetoothDevice> {
    CubeAdapter(Context context, int resource, List<BluetoothDevice> items) {
        super(context, resource, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        if (v == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(getContext());
            v = vi.inflate(android.R.layout.simple_list_item_1, null);
        }
        final BluetoothDevice cube = getItem(position);
        TextView tt1 = v.findViewById(android.R.id.text1);
        tt1.setText(cube.getName());

        return v;
    }

}