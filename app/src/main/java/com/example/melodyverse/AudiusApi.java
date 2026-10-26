package com.example.melodyverse;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gọi REST API của nền tảng nhạc Audius (https://api.audius.co) để tìm và lấy link phát nhạc.
 * Không cần API key, chỉ cần tham số app_name. Gọi mạng trên luồng nền, trả kết quả về luồng UI.
 */
public class AudiusApi {

    private static final String BASE = "https://api.audius.co/v1";
    private static final String APP_NAME = "MelodyVerse";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Một bài hát lấy về từ Audius. */
    public static class Track {
        public final String id;
        public final String title;
        public final String artist;
        public final String genre;
        public final int durationSec;

        Track(String id, String title, String artist, String genre, int durationSec) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.genre = genre;
            this.durationSec = durationSec;
        }

        /** Link phát ổn định (API tự chuyển hướng tới máy chủ chứa file nhạc). */
        public String getStreamUrl() {
            return streamUrl(id);
        }

        public String getDurationText() {
            return String.format(java.util.Locale.US, "%d:%02d", durationSec / 60, durationSec % 60);
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public static String streamUrl(String trackId) {
        return BASE + "/tracks/" + trackId + "/stream?app_name=" + APP_NAME;
    }

    /**
     * Link bài hát trên trang audius.co (người dùng copy từ web/app Audius), có hoặc không có "www." và http(s)://.
     * Cùng quy tắc với {@link MusicService#isSupportedAudioLink}: đúng 2 phần {nghệ sĩ}/{bài hát}.
     */
    public static boolean isAudiusPageLink(String url) {
        return MusicService.isAudiusPageLink(url);
    }

    /** GET /v1/tracks/search?query=... — tìm bài hát theo tên. */
    public static void search(String query, Callback<List<Track>> callback) {
        String url = BASE + "/tracks/search?query=" + Uri.encode(query) + "&app_name=" + APP_NAME;
        executor.execute(() -> {
            try {
                JSONArray data = new JSONObject(get(url)).getJSONArray("data");
                List<Track> tracks = new ArrayList<>();
                for (int i = 0; i < data.length() && tracks.size() < 20; i++) {
                    JSONObject t = data.getJSONObject(i);
                    // Bỏ các bài nghệ sĩ đã khóa / không cho phát
                    if (!t.optBoolean("is_streamable", true)) continue;
                    tracks.add(parseTrack(t));
                }
                mainHandler.post(() -> callback.onSuccess(tracks));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** GET /v1/resolve?url=... — đổi link trang audius.co thành thông tin bài hát. */
    public static void resolve(String pageUrl, Callback<Track> callback) {
        String full = pageUrl.trim();
        if (!full.startsWith("http")) full = "https://" + full;
        // API resolve nhận link dạng audius.co/{nghệ sĩ}/{bài hát}: bỏ "www." nếu người dùng copy kèm
        full = full.replaceFirst("(?i)^(https?://)www\\.audius\\.co/", "$1audius.co/");
        String url = BASE + "/resolve?url=" + Uri.encode(full) + "&app_name=" + APP_NAME;
        executor.execute(() -> {
            try {
                JSONObject t = new JSONObject(get(url)).getJSONObject("data");
                if (!t.has("title")) throw new Exception("Link không phải là một bài hát");
                if (!t.optBoolean("is_streamable", true)) throw new Exception("Bài hát này không cho phép phát");
                Track track = parseTrack(t);
                mainHandler.post(() -> callback.onSuccess(track));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static Track parseTrack(JSONObject t) {
        JSONObject user = t.optJSONObject("user");
        String artist = user != null ? user.optString("name", user.optString("handle", "Audius")) : "Audius";
        return new Track(
                t.optString("id"),
                t.optString("title"),
                artist,
                t.optString("genre", ""),
                t.optInt("duration", 0));
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            if (code == 404) throw new Exception("Không tìm thấy bài hát trên Audius");
            if (code != 200) throw new Exception("Audius trả về lỗi HTTP " + code);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
