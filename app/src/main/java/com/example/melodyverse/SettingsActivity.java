package com.example.melodyverse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Màn hình Cài đặt hệ thống — Bài 8 (SharedPreferences) & Bài 6 (BroadcastReceiver) & Bài 2 (Implicit Intent).
 *
 * Các tính năng thực tế 100% bám sát giáo trình UEH:
 *  1. Chế độ tối Galaxy (Dark Mode) → Áp dụng theme vũ trụ xanh tối (#0B0F19), chữ trắng nổi bật (#FFFFFF).
 *  2. Tự động chuyển bài → MusicService đọc key "auto_next", tự động lấy bài kế tiếp từ CSDL SQLite khi hết bài.
 *  3. Thông báo điều khiển nhạc → Bật/tắt thanh Notification điều khiển nhạc trên hệ thống (Notification Channel - Bài 7).
 *  4. Cảnh báo mất mạng → NetworkMonitor theo dõi mạng toàn app; công tắc chỉ bật / tắt cảnh báo và tạm dừng nhạc khi offline (Bài 6).
 *  5. Gửi phản hồi → Kích hoạt Implicit Intent ACTION_SENDTO mở ứng dụng Email gửi góp ý (Bài 2).
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREF_NAME = "melodyverse_settings";
    private SharedPreferences prefs;

    private SwitchCompat switchDarkMode;
    private SwitchCompat switchAutoNext;
    private SwitchCompat switchNotifications;
    private SwitchCompat switchOfflineWarning;
    private ImageView btnBack;
    private LinearLayout layoutFeedback;
    private AppCompatButton btnResetSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        btnBack = findViewById(R.id.btn_settings_back);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchAutoNext = findViewById(R.id.switch_auto_next);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchOfflineWarning = findViewById(R.id.switch_offline_warning);
        layoutFeedback = findViewById(R.id.layout_feedback);
        btnResetSettings = findViewById(R.id.btn_reset_settings);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadSavedSettings();
        setupListeners();
    }

    private void loadSavedSettings() {
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(prefs.getBoolean("dark_mode", false));
        }
        if (switchAutoNext != null) {
            switchAutoNext.setChecked(prefs.getBoolean("auto_next", true));
        }
        if (switchNotifications != null) {
            switchNotifications.setChecked(prefs.getBoolean("notifications", true));
        }
        if (switchOfflineWarning != null) {
            switchOfflineWarning.setChecked(prefs.getBoolean("offline_warning", true));
        }
    }

    private void setupListeners() {
        // 1. Chế độ tối Galaxy (Dark Mode)
        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
                boolean current = prefs.getBoolean("dark_mode", false);
                if (current == isChecked) return;

                prefs.edit().putBoolean("dark_mode", isChecked).apply();
                int nightMode = isChecked
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO;
                AppCompatDelegate.setDefaultNightMode(nightMode);

                recreate();

                Toast.makeText(this,
                        isChecked ? "Đã kích hoạt Chế độ tối Galaxy (Nền tối, chữ trắng nổi bật)"
                                : "Đã chuyển về Chế độ sáng ngọc trai (Light Mode)",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // 2. Tự động chuyển bài (Auto Next)
        if (switchAutoNext != null) {
            switchAutoNext.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("auto_next", isChecked).apply();
                Toast.makeText(this,
                        isChecked ? "Tự động phát ca khúc tiếp theo trong CSDL SQLite khi hết bài"
                                : "Đã tắt tự động chuyển bài — sẽ tạm dừng khi phát hết bài",
                        Toast.LENGTH_SHORT).show();
            });
        }

        // 3. Thông báo điều khiển nhạc (Notification Channel - Bài 7)
        if (switchNotifications != null) {
            switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("notifications", isChecked).apply();
                if (isChecked) {
                    if (MusicService.isPlaying) {
                        NotificationHelper.showMusicNotification(this,
                                MusicService.currentSongTitle,
                                MusicService.currentArtistName,
                                true);
                    }
                    Toast.makeText(this, "Đã bật thanh thông báo phát nhạc", Toast.LENGTH_SHORT).show();
                } else {
                    NotificationHelper.cancelMusicNotification(this);
                    Toast.makeText(this, "Đã tắt thanh thông báo điều khiển nhạc", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 4. Cảnh báo mất kết nối mạng (Bài 6 - NetworkMonitor theo dõi mạng toàn ứng dụng)
        if (switchOfflineWarning != null) {
            switchOfflineWarning.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("offline_warning", isChecked).apply();
                // NetworkMonitor luôn chạy để tự đồng bộ dữ liệu; công tắc này chỉ bật / tắt phần cảnh báo
                if (isChecked) {
                    Toast.makeText(this,
                            "Đã bật cảnh báo khi mất kết nối mạng trên toàn ứng dụng",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            "Đã tắt cảnh báo mất mạng (dữ liệu vẫn tự đồng bộ khi có mạng)",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 5. Gửi phản hồi qua Email (Bài 2 - Implicit Intent)
        if (layoutFeedback != null) {
            layoutFeedback.setOnClickListener(v -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:support@melodyverse.vn"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "[MelodyVerse App] Góp ý & Báo lỗi từ người dùng");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "Chào đội ngũ phát triển MelodyVerse,\n\nTôi muốn đóng góp ý kiến về tính năng:\n");
                try {
                    startActivity(Intent.createChooser(emailIntent, "Chọn ứng dụng gửi email phản hồi:"));
                } catch (Exception e) {
                    Toast.makeText(this, "Không tìm thấy ứng dụng email tương thích trên thiết bị", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 6. Khôi phục cài đặt mặc định
        if (btnResetSettings != null) {
            btnResetSettings.setOnClickListener(v -> {
                prefs.edit()
                        .putBoolean("dark_mode", false)
                        .putBoolean("auto_next", true)
                        .putBoolean("notifications", true)
                        .putBoolean("offline_warning", true)
                        .apply();

                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                recreate();
                Toast.makeText(this, "Đã khôi phục toàn bộ cài đặt về mặc định ban đầu!", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
