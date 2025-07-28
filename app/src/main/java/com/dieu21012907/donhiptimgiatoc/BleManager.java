// BleManager.java - Đã fix lỗi connectGatt và tùy chọn BLE
package com.dieu21012907.donhiptimgiatoc;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.M)
public class BleManager {
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean useBLE = false;

    private final List<BluetoothDevice> bleDevices = new ArrayList<>();
    private ScanCallback currentScanCallback;

    public static final UUID SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    public BleManager(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public List<BluetoothDevice> getDeviceList() {
        return bleDevices;
    }

    public boolean isUseBLE() {
        return useBLE;
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BleManager", "Thiếu quyền BLUETOOTH_CONNECT");
            return;
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
    }

    public void sendBLEData(int bpm, float x, float y, float z) {
        if (bluetoothGatt == null) return;

        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        if (service == null) return;

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic == null) return;

        String payload = bpm + "|" + x + "|" + y + "|" + z;
        characteristic.setValue(payload.getBytes());

        bluetoothGatt.writeCharacteristic(characteristic);
    }

    public void startScan(Runnable onDeviceFound) {
        if (bluetoothLeScanner == null) {
            Log.e("BleManager", "Không tìm thấy BLE Scanner");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BleManager", "Thiếu quyền BLUETOOTH_SCAN");
            return;
        }

        bleDevices.clear();

        currentScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (!bleDevices.contains(device)) {
                    bleDevices.add(device);
                    onDeviceFound.run();
                }
            }
        };

        bluetoothLeScanner.startScan(currentScanCallback);
        new Handler(Looper.getMainLooper()).postDelayed(() -> bluetoothLeScanner.stopScan(currentScanCallback), 5000);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                useBLE = true;
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e("BleManager", "Thiếu quyền BLUETOOTH_CONNECT khi gọi discoverServices");
                    return;
                }
                gatt.discoverServices();
            }
        }
    };
}
