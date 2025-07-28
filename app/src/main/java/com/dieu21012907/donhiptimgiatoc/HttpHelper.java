package com.dieu21012907.donhiptimgiatoc;

import android.util.Log;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpHelper {

    private final OkHttpClient client = new OkHttpClient();
    private final String serverUrl;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public HttpHelper(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void sendData(int bpm, float x, float y, float z, long ts, Callback callback) {
        // Tạo chuỗi JSON
        String json = "{"
                + "\"bpm\":" + bpm + ","
                + "\"x\":" + x + ","
                + "\"y\":" + y + ","
                + "\"z\":" + z + ","
                + "\"ts\":" + ts
                + "}";

        // Gửi request POST với JSON
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(body)
                .build();

        client.newCall(request).enqueue(callback);
    }
}