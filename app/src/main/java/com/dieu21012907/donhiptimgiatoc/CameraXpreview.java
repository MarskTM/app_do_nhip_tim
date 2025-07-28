package com.dieu21012907.donhiptimgiatoc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraXpreview extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private PreviewView previewView;
    private ProgressBar progressBar;
    private TextView tvProgressPercent, tvHeartRate;

    private HeartRate heartRate;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_xpreview);

        previewView = findViewById(R.id.previewView);
        progressBar = findViewById(R.id.progressCircle);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvHeartRate = findViewById(R.id.tvHeartRate);

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
                runOnUiThread(() -> tvHeartRate.setText("Nhịp tim: " + bpm + " bpm"));
            }

            @Override
            public void onNoSignal() {
                runOnUiThread(() -> tvHeartRate.setText("Tín hiệu yếu, hãy đặt tay sát vào camera"));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(getExecutor(), image -> {
                    Bitmap bitmap = imageToBitmap(image);
                    if (bitmap != null) heartRate.processFrame(bitmap);
                    image.close();
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("CameraX", "Lỗi khởi tạo CameraX", e);
            }
        }, getExecutor());
    }

    private ExecutorService getExecutor() {
        if (cameraExecutor == null) cameraExecutor = Executors.newSingleThreadExecutor();
        return cameraExecutor;
    }

    private Bitmap imageToBitmap(ImageProxy image) {
        if (image.getFormat() != android.graphics.ImageFormat.YUV_420_888) return null;
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        int width = image.getWidth();
        int height = image.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int luminance = bytes[y * width + x] & 0xFF;
                int color = android.graphics.Color.rgb(luminance, 0, 0);
                bitmap.setPixel(x, y, color);
            }
        }
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }
}
