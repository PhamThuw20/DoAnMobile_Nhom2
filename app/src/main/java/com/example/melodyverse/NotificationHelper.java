package com.example.melodyverse;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

/**
 * Trợ thủ quản lý thông báo đẩy (Bài 7 - 08.1 Notifications).
 */
public class NotificationHelper {
    public static final String CHANNEL_ID = "melodyverse_channel";
    public static final String CHANNEL_NAME = "MelodyVerse Notifications";
    public static final int MUSIC_NOTIFICATION_ID = 1001;

    public static void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Để phát nhạc không kêu 'ting' khó chịu
        );
        channel.setDescription("Kênh thông báo phát nhạc và tin tức MelodyVerse");
        
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static void showNotification(Context context, String title, String content) {
        createNotificationChannel(context);
        
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    public static void showMusicNotification(Context context, String songTitle, String artist, boolean isPlaying) {
        showMusicNotification(context, songTitle, artist, isPlaying, false);
    }

    /** buffering = đang tải nhạc trực tuyến: hiện "Đang tải…" thay vì "Đang phát". */
    public static void showMusicNotification(Context context, String songTitle, String artist, boolean isPlaying, boolean buffering) {
        createNotificationChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Nút Toggle Play/Pause từ thông báo
        Intent toggleIntent = new Intent(context, MusicService.class);
        toggleIntent.setAction(MusicService.ACTION_TOGGLE);
        PendingIntent togglePendingIntent = PendingIntent.getService(
                context, 1, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(songTitle)
                .setContentText(artist + (buffering ? " • Đang tải…" : isPlaying ? " • Đang phát" : " • Đã tạm dừng"))
                .setContentIntent(openAppPendingIntent)
                .setOngoing(isPlaying)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play,
                        isPlaying ? "Tạm dừng" : "Tiếp tục", togglePendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(MUSIC_NOTIFICATION_ID, builder.build());
        }
    }

    public static void cancelMusicNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(MUSIC_NOTIFICATION_ID);
        }
    }
}
