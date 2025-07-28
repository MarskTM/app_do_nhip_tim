package com.dieu21012907.donhiptimgiatoc;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class HeartRate {
    private final ArrayList<Integer> redAvgList = new ArrayList<>();
    private final int SAMPLE_WINDOW = 5000; // 5 giây
    private final int FRAME_INTERVAL = 100;
    private final int MAX_BUFFER = SAMPLE_WINDOW / FRAME_INTERVAL;
    private final Queue<Long> beatTimestamps = new LinkedList<>();

    private boolean isMeasuring = false;
    private int frameCount = 0;

    private static final int RED_THRESHOLD = 180;
    private int lowRedCount = 0;
    private static final int MAX_LOW_RED_FRAMES = 5; // nếu tay đặt sai quá 5 lần liên tiếp → dừng đo

    public interface HeartRateListener {
        void onProgressUpdate(int percent);
        void onBpmResult(int bpm);
        void onNoSignal();
    }

    private HeartRateListener listener;

    public void setListener(HeartRateListener l) {
        this.listener = l;
    }

    public void reset() {
        redAvgList.clear();
        beatTimestamps.clear();
        frameCount = 0;
        lowRedCount = 0;
        isMeasuring = false;
    }

    public void processFrame(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) return;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int startX = Math.max(0, width / 2 - 50);
        int startY = Math.max(0, height / 2 - 50);
        int endX = Math.min(width, startX + 100);
        int endY = Math.min(height, startY + 100);

        int count = 0;
        long sumRed = 0;

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int pixel = bitmap.getPixel(x, y);
                sumRed += Color.red(pixel);
                count++;
            }
        }

        if (count == 0) return; // tránh chia 0
        int redAvg = (int) (sumRed / count);
        Log.d("HeartRate", "Red average: " + redAvg);

        // Nếu red thấp → tăng bộ đếm lowRedCount
        if (redAvg < RED_THRESHOLD) {
            lowRedCount++;
            Log.w("HeartRate", "Low red count: " + lowRedCount);
            if (lowRedCount >= MAX_LOW_RED_FRAMES) {
                Log.w("HeartRate", "Dừng đo do không đủ tín hiệu (tay sai vị trí?)");
                reset(); // dừng và reset toàn bộ
                if (listener != null) listener.onNoSignal();
            }
            return;
        }

        // Nếu tín hiệu tốt → reset bộ đếm lỗi
        lowRedCount = 0;

        redAvgList.add(redAvg);
        frameCount++;

        int percent = (int) (frameCount * 100.0 / MAX_BUFFER);
        if (listener != null) listener.onProgressUpdate(percent);

        if (frameCount >= MAX_BUFFER) {
            int bpm = estimateBPM(redAvgList);
            Log.d("HeartRate", "Detected BPM: " + bpm);
            if (listener != null) listener.onBpmResult(bpm);
            new Handler(Looper.getMainLooper()).postDelayed(this::reset, 1000);
        }
    }

    private int estimateBPM(ArrayList<Integer> data) {
        int peaks = 0;
        for (int i = 1; i < data.size() - 1; i++) {
            int prev = data.get(i - 1);
            int curr = data.get(i);
            int next = data.get(i + 1);
            if (curr > prev && curr > next && curr > 180) { // lọc nhiễu
                peaks++;
            }
        }
        float seconds = (float) data.size() * FRAME_INTERVAL / 1000f;
        Log.d("HeartRate", "Peaks detected: " + peaks + ", Duration (s): " + seconds);
        return Math.round((peaks / seconds) * 60f);
    }
}
