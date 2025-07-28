// MainActivity.java - Sử dụng BleManager để xử lý BLE
package com.dieu21012907.donhiptimgiatoc;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.*;
import android.util.Log;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import okhttp3.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private PreviewView previewView;
    private ProgressBar progressBar;
    private TextView tvProgressPercent, textHeartRate, textAcceleration, textX, textY, textZ;
    private TextView textMeasuringStatus, textServerAddress, textServerStatus;
    private RecyclerView deviceRecyclerView;
    private Button btnRefresh;

    private HeartRate heartRate;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float accelX, accelY, accelZ;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final String SERVER_URL = "http://192.168.2.6/process.php";

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private Camera camera;

    private BleManager bleManager;
    private BLEDeviceAdapter bleDeviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressCircle);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        textHeartRate = findViewById(R.id.textHeartRate);
        textAcceleration = findViewById(R.id.textAcceleration);
        textX = findViewById(R.id.textX);
        textY = findViewById(R.id.textY);
        textZ = findViewById(R.id.textZ);
        textMeasuringStatus = findViewById(R.id.textMeasuringStatus);
        textServerAddress = findViewById(R.id.textServerAddress);
        textServerStatus = findViewById(R.id.textServerStatus);
        deviceRecyclerView = findViewById(R.id.deviceRecyclerView);
        btnRefresh = findViewById(R.id.btnRefresh);

        heartRate = new HeartRate();
        heartRate.setListener(new HeartRate.HeartRateListener() {
            @Override
            public void onProgressUpdate(int percent) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    tvProgressPercent.setText("Đang đo (" + percent + "%)");
                });
            }

            @Override
            public void onBpmResult(int bpm) {
                runOnUiThread(() -> {
                    textHeartRate.setText("Nhịp tim: " + bpm + " bpm");
                    textMeasuringStatus.setText("✅ Đã đo xong");
                });
                if (bleManager != null && bleManager.isUseBLE()) {
                    bleManager.sendBLEData(bpm, accelX, accelY, accelZ);
                } else {
                    sendToServer(bpm, accelX, accelY, accelZ, System.currentTimeMillis());
                }
            }

            @Override
            public void onNoSignal() {
                runOnUiThread(() -> {
                    textMeasuringStatus.setText("⚠️ Không có tín hiệu hoặc tay sai vị trí");
                    tvProgressPercent.setText("Đang chờ tín hiệu...");
                    progressBar.setProgress(0);
                });
            }
        });

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        } else {
            startCamera();
        }

        textServerAddress.setText("Địa chỉ: " + SERVER_URL);
        textServerStatus.setText("Tình trạng: Đang kết nối...");
        textServerStatus.setTextColor(0xFF888888);

        cameraExecutor = Executors.newSingleThreadExecutor();

        bleManager = new BleManager(this);
        bleDeviceAdapter = new BLEDeviceAdapter(bleManager.getDeviceList(), device -> bleManager.connectToDevice(device));
        deviceRecyclerView.setAdapter(bleDeviceAdapter);

        bleManager.startScan(() -> bleDeviceAdapter.notifyDataSetChanged());
        btnRefresh.setOnClickListener(v -> bleManager.startScan(() -> bleDeviceAdapter.notifyDataSetChanged()));

        LinearLayout serverCard = findViewById(R.id.serverCard);
        serverCard.setOnClickListener(v -> {
            Toast.makeText(this, "Chuyển về chế độ gửi qua server", Toast.LENGTH_SHORT).show();
        });
    }

    // Các hàm startCamera, startFrameCapture, sendToServer, sensorChanged giữ nguyên như cũ

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder().setFlashMode(ImageCapture.FLASH_MODE_ON).build();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                camera.getCameraControl().enableTorch(true);
                startFrameCapture();
            } catch (Exception e) {
                Log.e("CameraX", "Không khởi tạo được camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startFrameCapture() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = previewView.getBitmap();
                if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    try {
                        heartRate.processFrame(bitmap);
                    } catch (Exception e) {
                        Log.e("HeartRate", "Lỗi xử lý khung hình", e);
                    }
                }
                handler.postDelayed(this, 100);
            }
        }, 300);
    }

    private void sendToServer(int bpm, float x, float y, float z, long ts) {
        try {
            JSONObject json = new JSONObject();
            json.put("bpm", bpm);
            json.put("x", x);
            json.put("y", y);
            json.put("z", z);
            json.put("ts", ts);

            RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().url(SERVER_URL).post(body).build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        textServerStatus.setText("Tình trạng: Lỗi kết nối");
                        textServerStatus.setTextColor(0xFFD32F2F);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    runOnUiThread(() -> {
                        textServerStatus.setText("Tình trạng: Đã gửi thành công");
                        textServerStatus.setTextColor(0xFF4CAF50);
                    });
                }
            });

        } catch (Exception e) {
            Log.e("HTTP", "Lỗi gửi server", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];
            float acc = (float) Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
            textAcceleration.setText(String.format(Locale.US, "Gia tốc: %.2f m/s²", acc));
            textX.setText(String.format(Locale.US, "x = %.2f", accelX));
            textY.setText(String.format(Locale.US, "y = %.2f", accelY));
            textZ.setText(String.format(Locale.US, "z = %.2f", accelZ));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
