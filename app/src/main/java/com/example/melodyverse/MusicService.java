package com.example.melodyverse;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import java.util.List;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final String TAG = "MusicService";

    public static final String ACTION_PLAY = "com.example.melodyverse.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.melodyverse.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.example.melodyverse.ACTION_RESUME";
    public static final String ACTION_TOGGLE = "com.example.melodyverse.ACTION_TOGGLE";
    public static final String ACTION_STOP = "com.example.melodyverse.ACTION_STOP";
    public static final String ACTION_NEXT = "com.example.melodyverse.ACTION_NEXT";
    public static final String ACTION_PREVIOUS = "com.example.melodyverse.ACTION_PREVIOUS";
    public static final String ACTION_STOP_UNAVAILABLE = "com.example.melodyverse.ACTION_STOP_UNAVAILABLE";
    public static final String ACTION_SEEK = "com.example.melodyverse.ACTION_SEEK";
    public static final String ACTION_RESET = "com.example.melodyverse.ACTION_RESET";
    public static final String ACTION_MUSIC_STATUS_CHANGED = "com.example.melodyverse.MUSIC_STATUS_CHANGED";
    public static final String EXTRA_IS_PLAYING = "is_playing";
    public static final String EXTRA_SONG_TITLE = "song_title";
    public static final String EXTRA_ARTIST_NAME = "artist_name";
    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_DURATION = "duration";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_SEEK_POSITION = "seek_position";
    public static final String EXTRA_MUSIC_URL = "music_url";
    public static final String EXTRA_RAW_RES = "raw_res";
    public static final String EXTRA_GENRE = "genre";
    public static final String EXTRA_ANNOUNCED = "announced";
    public static final String EXTRA_MESSAGE = "message";

    public static final String OFFLINE_STREAM_MESSAGE =
            "Bạn đang ngoại tuyến. Vui lòng kết nối Internet để nghe bài hát trực tuyến này";

    public static boolean isPlaying = false;
    public static boolean hasTrackLoaded = false;
    public static String currentSongTitle = "50 Cuộc Gọi Nhỡ";
    public static String currentArtistName = "Tinh Hà Say Hi • Tập 8";
    public static String currentPostId = "p1";
    public static String currentGenre = "V-Pop";
    public static int currentRawRes = R.raw.song_sample_1;
    public static String currentMusicUrl = null;
    public static int currentDuration = 0;
    public static int currentPosition = 0;
    public static boolean isBuffering = false;

    public static boolean isShuffleEnabled = false;
    public static boolean isRepeatEnabled = false;

    private static List<PostModel> playQueue = null;
    private static String playQueueName = null;

    public static final int PREVIOUS_RESTART_THRESHOLD_MS = 3000;
    private static final java.util.ArrayDeque<String> playHistory = new java.util.ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    public static boolean shouldRestartOnPrevious(int positionMs) {
        return positionMs > PREVIOUS_RESTART_THRESHOLD_MS;
    }

    static int previousIndex(int currentIndex, int size, boolean wrap) {
        if (size <= 0 || currentIndex < 0) return -1;
        if (currentIndex > 0) return currentIndex - 1;
        return wrap && size > 1 ? size - 1 : -1;
    }

    public static void setShuffleEnabled(boolean enabled) {
        isShuffleEnabled = enabled;
        if (enabled) isRepeatEnabled = false;
    }

    public static void setRepeatEnabled(boolean enabled) {
        isRepeatEnabled = enabled;
        if (enabled) isShuffleEnabled = false;
    }

    public static boolean isPlayingQueue(String name) {
        return playQueue != null && name != null && name.equals(playQueueName);
    }
    private static MediaPlayer activePlayer;
    private static final java.util.Random random = new java.util.Random();

    private MediaPlayer mediaPlayer;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("melodyverse_settings", MODE_PRIVATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

        switch (action) {
            case ACTION_PLAY:
                String title = intent.getStringExtra(EXTRA_SONG_TITLE);
                String artist = intent.getStringExtra(EXTRA_ARTIST_NAME);
                String postId = intent.getStringExtra(EXTRA_POST_ID);
                String genre = intent.getStringExtra(EXTRA_GENRE);
                int rawRes = intent.getIntExtra(EXTRA_RAW_RES, 0);
                String url = intent.getStringExtra(EXTRA_MUSIC_URL);

                // Bấm Play đúng bài đang tạm dừng / đang tải: phát tiếp, không phát lại từ đầu
                if (postId != null && postId.equals(currentPostId) && hasTrackLoaded && mediaPlayer != null
                        && java.util.Objects.equals(url, currentMusicUrl)) {
                    if (!isPlaying) resumeTrack();
                    break;
                }

                if (postId != null && hasTrackLoaded && !postId.equals(currentPostId)) rememberInHistory(currentPostId);
                if (title != null) currentSongTitle = title;
                if (artist != null) currentArtistName = artist;
                if (postId != null) currentPostId = postId;
                if (genre != null) currentGenre = genre;
                if (rawRes != 0) currentRawRes = rawRes;

                playTrack(currentRawRes, url, intent.getBooleanExtra(EXTRA_ANNOUNCED, false));
                break;

            case ACTION_PAUSE:
                pauseTrack();
                break;

            case ACTION_RESUME:
                resumeTrack();
                break;

            case ACTION_TOGGLE:
                if (isPlaying) {
                    pauseTrack();
                } else {
                    if (mediaPlayer != null) {
                        resumeTrack();
                    } else {
                        playTrack(currentRawRes, currentMusicUrl, false);
                    }
                }
                break;

            case ACTION_NEXT:
                playNextTrack(true);
                break;

            case ACTION_PREVIOUS:
                playPreviousTrack();
                break;

            case ACTION_STOP_UNAVAILABLE:
                String goneId = intent.getStringExtra(EXTRA_POST_ID);
                if (goneId != null && goneId.equals(currentPostId)) {
                    stopUnavailable();
                }
                break;

            case ACTION_RESET:
                playHistory.clear();
                stopUnavailable();
                break;

            case ACTION_SEEK:
                int seekPos = intent.getIntExtra(EXTRA_SEEK_POSITION, 0);
                if (mediaPlayer != null) {
                    try {
                        int realDur = mediaPlayer.getDuration();
                        if (realDur > 0) {
                            mediaPlayer.seekTo(Math.max(0, Math.min(seekPos, realDur - 1)));
                        }
                    } catch (Exception ignored) {}
                }
                broadcastStatus();
                break;

            case ACTION_STOP:
                stopTrack();
                stopSelf();
                break;
        }

        return START_STICKY;
    }

    /** Link nhạc thật (http/https, không phải link mẫu example.com) thì phát trực tuyến. */
    static boolean isStreamableUrl(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return (u.startsWith("http://") || u.startsWith("https://")) && !u.contains("example.com");
    }

    public static final String INVALID_AUDIO_LINK_MESSAGE =
            "Đường dẫn bài hát không hợp lệ. Vui lòng nhập link bài hát hợp lệ hoặc bấm Tìm nhạc trên Audius";

    private static final String[] AUDIO_FILE_EXTENSIONS = {
            ".mp3", ".wav", ".ogg", ".oga", ".m4a", ".aac", ".flac", ".opus", ".weba", ".webm"};


    public static boolean isAudiusPageLink(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        if (u.isEmpty() || u.contains(" ")) return false;
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        try {
            java.net.URI uri = new java.net.URI(u);
            String host = uri.getHost();
            if (!"audius.co".equals(host) && !"www.audius.co".equals(host)) return false;
            String path = uri.getPath() != null ? uri.getPath() : "";
            String[] parts = path.replaceAll("^/+|/+$", "").split("/+");
            return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty();
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }
    public static boolean isSupportedAudioLink(String url) {
        if (url == null) return false;
        String raw = url.trim();
        if (raw.isEmpty() || raw.contains(" ")) return false;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("audius.co/") || lower.startsWith("www.audius.co/")) lower = "https://" + lower;
        java.net.URI uri;
        try {
            uri = new java.net.URI(lower);
        } catch (java.net.URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath() != null ? uri.getPath() : "";
        if (scheme == null || host == null || !(scheme.equals("http") || scheme.equals("https"))) return false;
        int dot = host.lastIndexOf('.');
        if (dot <= 0 || dot == host.length() - 1) return false;
        if (host.equals("example.com") || host.endsWith(".example.com")) return false;

        if (host.equals("audius.co") || host.equals("www.audius.co")) {
            return isAudiusPageLink(lower);
        }
        if (host.endsWith("audius.co")) {
            return path.matches("/v1/tracks/[A-Za-z0-9]+/stream/?");
        }
        for (String ext : AUDIO_FILE_EXTENSIONS) {
            if (path.endsWith(ext) && path.length() > ext.length() + 1) return true;
        }
        return false;
    }
    private void playTrack(int rawRes, String url, boolean announced) {
        currentMusicUrl = url;
        if (isStreamableUrl(url)) {
            if (!SyncManager.get(this).isOnline()) {

                streamFailed(announced ? null : OFFLINE_STREAM_MESSAGE);
                return;
            }
            playStream(url.trim());
            return;
        }
        playRaw(rawRes);
    }

    public static boolean isOfflineStream(Context context, PostModel post) {
        return isStreamableUrl(post.getMusicUrl()) && !SyncManager.get(context).isOnline();
    }

    public static String startMessage(Context context, PostModel post) {
        if (isOfflineStream(context, post)) return OFFLINE_STREAM_MESSAGE;
        return nowPlayingMessage(post.getSongTitle());
    }

    static String nowPlayingMessage(String songTitle) {
        return "Đang phát: " + (songTitle != null ? songTitle.trim() : "");
    }

    public static boolean isStreamingCurrent() {
        return isStreamableUrl(currentMusicUrl);
    }

    private void playStream(String url) {
        releaseMediaPlayer();
        isPlaying = false;
        isBuffering = true;
        hasTrackLoaded = true;
        broadcastStatus();
        updateNotification();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(url);
            mp.setOnPreparedListener(p -> {
                if (p != mediaPlayer) return; // đã chuyển sang bài khác trong lúc đang tải
                p.setLooping(false);
                p.setOnCompletionListener(this);
                p.start();
                activePlayer = p;
                isBuffering = false;
                isPlaying = true;
                hasTrackLoaded = true;
                broadcastStatus();
                updateNotification();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.e(TAG, "Không phát được link " + url + " (what=" + what + ", extra=" + extra + ")");
                if (p == mediaPlayer) {
                    streamFailed(SyncManager.get(this).isOnline()
                            ? "Không phát được nhạc trực tuyến (link lỗi hoặc máy chủ không phản hồi)."
                            : "Mất kết nối mạng — đã dừng phát nhạc trực tuyến. Hãy kết nối lại rồi bấm phát.");
                }
                return true;
            });
            mediaPlayer = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Link nhạc không hợp lệ: " + e.getMessage());
            streamFailed("Link nhạc trực tuyến không hợp lệ, không thể phát.");
        }
    }
    private void streamFailed(String message) {
        releaseMediaPlayer();
        isPlaying = false;
        isBuffering = false;
        hasTrackLoaded = true;
        currentPosition = 0;
        currentDuration = 0;
        broadcastStatus(message);
        updateNotification();
    }

    private void playRaw(int rawRes) {
        releaseMediaPlayer();
        isBuffering = false;

        try {
            if (rawRes != 0) {
                mediaPlayer = MediaPlayer.create(this, rawRes);
            } else {
                mediaPlayer = MediaPlayer.create(this, R.raw.song_sample_1);
            }

            if (mediaPlayer != null) {
                mediaPlayer.setLooping(false);
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.start();
                activePlayer = mediaPlayer;
                isPlaying = true;
                hasTrackLoaded = true;

                broadcastStatus();
                updateNotification();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi phát bài hát: " + e.getMessage());
            isPlaying = false;
            broadcastStatus();
        }
    }

    private void pauseTrack() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        isPlaying = false;
        broadcastStatus();
        updateNotification();
    }

    private void resumeTrack() {
        if (mediaPlayer != null && mediaPlayer != activePlayer) {
            return; // link nhạc còn đang tải, onPrepared sẽ tự phát
        }
        if (mediaPlayer != null) {
            mediaPlayer.start();
            isPlaying = true;
            hasTrackLoaded = true;
            broadcastStatus();
            updateNotification();
        } else {
            playTrack(currentRawRes, currentMusicUrl, false);
        }
    }
    private void stopUnavailable() {
        stopTrack();
        hasTrackLoaded = false;
        currentPosition = 0;
        currentDuration = 0;
        playQueue = null;
        playQueueName = null;
        broadcastStatus();
        NotificationHelper.cancelMusicNotification(this);
        stopSelf();
    }

    private void stopTrack() {
        isBuffering = false;
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            releaseMediaPlayer();
        }
        isPlaying = false;
        broadcastStatus();
        NotificationHelper.cancelMusicNotification(this);
    }

    private void playNextTrack(boolean manual) {
        List<PostModel> posts = currentPlayList();
        if (posts.isEmpty()) {
            broadcastStatus();
            updateNotification();
            return;
        }
        int currentIndex = indexOfPost(posts, currentPostId);
        int nextIndex;
        if (isShuffleEnabled && posts.size() > 1) {
            do {
                nextIndex = random.nextInt(posts.size());
            } while (nextIndex == currentIndex);
        } else if (playQueue != null) {
            nextIndex = currentIndex + 1;
            if (nextIndex >= posts.size()) {
                // Đã phát hết danh sách: dừng lại, không nhảy ra bài ngoài danh sách
                finishQueue(manual ? "Đây là bài cuối cùng trong " + playQueueName + "."
                        : "Đã phát hết " + playQueueName + ".");
                return;
            }
        } else {
            nextIndex = (currentIndex + 1) % posts.size();
        }
        playPost(posts.get(nextIndex));
    }

    private List<PostModel> currentPlayList() {
        if (playQueue != null) return playQueue;
        List<PostModel> posts = new java.util.ArrayList<>();
        for (PostModel p : DatabaseHelper.getInstance(this).getAllPosts()) {
            if (!p.isPrivate() || SessionManager.isCurrentUser(this, p.getAuthorUsername())) {
                posts.add(p);
            }
        }
        return posts;
    }

    private void playPreviousTrack() {
        if (mediaPlayer != null && mediaPlayer == activePlayer && shouldRestartOnPrevious(getCurrentPosition())) {
            restartCurrent();
            return;
        }
        List<PostModel> posts = currentPlayList();
        PostModel previous = null;
        if (isShuffleEnabled) {
            while (previous == null && !playHistory.isEmpty()) {
                String id = playHistory.pollLast();
                int idx = indexOfPost(posts, id);
                if (idx >= 0 && !id.equals(currentPostId)) previous = posts.get(idx);
            }
        }
        if (previous == null) {
            int idx = previousIndex(indexOfPost(posts, currentPostId), posts.size(), playQueue == null);
            if (idx >= 0) previous = posts.get(idx);
        }
        if (previous == null) {
            // Đang ở bài đầu tiên của danh sách: phát lại bài hiện tại từ đầu
            restartCurrent();
            return;
        }
        playPost(previous, false);
    }

    private void restartCurrent() {
        if (mediaPlayer != null && mediaPlayer == activePlayer) {
            try {
                mediaPlayer.seekTo(0);
                if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                isPlaying = true;
                hasTrackLoaded = true;
            } catch (Exception ignored) {}
            broadcastStatus();
            updateNotification();
        } else {
            playTrack(currentRawRes, currentMusicUrl, false);
        }
    }

    private static void rememberInHistory(String postId) {
        if (postId == null) return;
        if (!postId.equals(playHistory.peekLast())) playHistory.addLast(postId);
        while (playHistory.size() > MAX_HISTORY) playHistory.pollFirst();
    }

    private static int indexOfPost(List<PostModel> posts, String postId) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() != null && posts.get(i).getId().equals(postId)) return i;
        }
        return -1;
    }
    private void playPost(PostModel next) {
        playPost(next, true);
    }

    private void playPost(PostModel next, boolean forward) {
        if (forward) rememberInHistory(currentPostId);
        currentSongTitle = next.getSongTitle();
        currentArtistName = next.getArtistName();
        currentPostId = next.getId();
        currentGenre = next.getGenre();
        currentRawRes = next.getRawAudioRes() != 0 ? next.getRawAudioRes() : R.raw.song_sample_1;
        playTrack(currentRawRes, next.getMusicUrl(), false);
    }

    private void finishQueue(String message) {
        if (!isPlaying && mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(0);
            } catch (Exception ignored) {}
        }
        broadcastStatus(message);
        updateNotification();
    }

    private void broadcastStatus() {
        broadcastStatus(null);
    }

    private void broadcastStatus(String message) {
        currentDuration = getDuration();
        currentPosition = getCurrentPosition();
        Intent intent = new Intent(ACTION_MUSIC_STATUS_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        intent.putExtra(EXTRA_SONG_TITLE, currentSongTitle);
        intent.putExtra(EXTRA_ARTIST_NAME, currentArtistName);
        intent.putExtra(EXTRA_POST_ID, currentPostId);
        intent.putExtra(EXTRA_DURATION, currentDuration);
        intent.putExtra(EXTRA_POSITION, currentPosition);
        if (message != null) intent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(intent);
    }

    private void updateNotification() {
        boolean allowNotif = prefs.getBoolean("notifications", true);
        if (allowNotif) {
            NotificationHelper.showMusicNotification(this, currentSongTitle, currentArtistName, isPlaying, isBuffering);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (!isPlaying) {
            return;
        }
        if (isStreamingCurrent() && !SyncManager.get(this).isOnline()) {
            int dur = getDuration();
            int pos = getCurrentPosition();
            if (dur <= 0 || pos < dur - 2000) {
                streamFailed("Mất kết nối mạng — đã dừng phát nhạc trực tuyến. Hãy kết nối lại rồi bấm phát.");
                return;
            }
        }
        if (isRepeatEnabled) {
            mp.seekTo(0);
            mp.start();
            isPlaying = true;
            broadcastStatus();
            return;
        }
        boolean autoNext = prefs.getBoolean("auto_next", true);
        if (autoNext || playQueue != null) {
            isPlaying = false;
            playNextTrack(false);
        } else {
            isPlaying = false;
            mp.seekTo(0);
            broadcastStatus();
            updateNotification();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        releaseMediaPlayer();
        isPlaying = false;
        broadcastStatus();
        return true;
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            if (activePlayer == mediaPlayer) activePlayer = null;
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        stopTrack();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public static void play(Context context, PostModel post) {
        playQueue = null;
        playQueueName = null;
        startPlay(context, post);
    }
    public static void playQueue(Context context, String name, List<PostModel> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        int index = Math.max(0, Math.min(startIndex, tracks.size() - 1));
        playQueue = new java.util.ArrayList<>(tracks);
        playQueueName = name;
        startPlay(context, playQueue.get(index));
    }

    private static void startPlay(Context context, PostModel post) {
        boolean offline = isOfflineStream(context, post);
        if (offline) {
            android.widget.Toast.makeText(context, OFFLINE_STREAM_MESSAGE, android.widget.Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra(EXTRA_SONG_TITLE, post.getSongTitle());
        intent.putExtra(EXTRA_ARTIST_NAME, post.getArtistName());
        intent.putExtra(EXTRA_POST_ID, post.getId());
        intent.putExtra(EXTRA_RAW_RES, post.getRawAudioRes());
        intent.putExtra(EXTRA_GENRE, post.getGenre());
        intent.putExtra(EXTRA_MUSIC_URL, post.getMusicUrl());
        intent.putExtra(EXTRA_ANNOUNCED, offline);
        context.startService(intent);
    }

    public static void announce(Context context, PostModel post) {
        if (isOfflineStream(context, post)) return;
        android.widget.Toast.makeText(context, startMessage(context, post), android.widget.Toast.LENGTH_SHORT).show();
    }

    public static void toggle(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_TOGGLE);
        context.startService(intent);
    }

    public static void pause(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PAUSE);
        context.startService(intent);
    }

    public static void previous(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_PREVIOUS);
        context.startService(intent);
    }

    public static void stopIfPlaying(Context context, String postId) {
        if (postId == null || !postId.equals(currentPostId) || (!hasTrackLoaded && !isPlaying)) return;
        isPlaying = false;
        hasTrackLoaded = false;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_STOP_UNAVAILABLE);
        intent.putExtra(EXTRA_POST_ID, postId);
        try {
            context.startService(intent);
        } catch (Exception e) {
            NotificationHelper.cancelMusicNotification(context);
        }
    }

    public static void stopAndReset(Context context) {
        isPlaying = false;
        hasTrackLoaded = false;
        isBuffering = false;
        currentPosition = 0;
        currentDuration = 0;
        currentMusicUrl = null;
        playQueue = null;
        playQueueName = null;
        playHistory.clear();
        BaseActivity.isGlobalPlaying = false;
        BaseActivity.isMiniPlayerDismissed = false;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_RESET);
        try {
            context.startService(intent);
        } catch (Exception e) {
            NotificationHelper.cancelMusicNotification(context);
        }
    }

    public static void next(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_NEXT);
        context.startService(intent);
    }

    public static void seekTo(Context context, int msec) {
        currentPosition = msec;
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(ACTION_SEEK);
        intent.putExtra(EXTRA_SEEK_POSITION, msec);
        context.startService(intent);
    }

    public static int getCurrentPosition() {
        if (activePlayer != null) {
            try {
                currentPosition = activePlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return currentPosition;
    }

    public static int getDuration() {
        if (activePlayer != null) {
            try {
                int dur = activePlayer.getDuration();
                if (dur > 0) currentDuration = dur;
            } catch (Exception ignored) {}
        }
        return currentDuration;
    }
}
