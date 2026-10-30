package com.example.melodyverse;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.HashSet;
import java.util.Set;

/**
 * Theo dõi kết nối mạng toàn ứng dụng (Bài 6 - Broadcast / Network Callback).
 * Đăng ký một lần bằng Application Context nên hoạt động ở mọi màn hình.
 *
 * - Chỉ coi là "có mạng" khi mạng đã được hệ thống xác minh truy cập được Internet (NET_CAPABILITY_VALIDATED),
 *   không chỉ dựa vào việc Wi-Fi đã bật / đã kết nối.
 * - Có mạng trở lại → {@link SyncManager#onReconnected()} đẩy các thao tác chờ lên Firebase rồi làm mới màn hình.
 * - Cảnh báo mất mạng (Toast) chỉ hiện khi người dùng bật trong Cài đặt. Nhạc trực tuyến sẽ dừng (không phát được
 *   khi mất mạng); file nhạc mẫu đóng gói trong APK vẫn tiếp tục phát.
 */
public class NetworkMonitor {

    private static ConnectivityManager.NetworkCallback callback;
    private static boolean wasOffline = false;
    // Các mạng ĐÃ XÁC MINH có Internet (Wi-Fi, 4G...) — rỗng nghĩa là không truy cập được Internet
    private static final Set<Network> validatedNetworks = new HashSet<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean isWarningEnabled(Context context) {
        return context.getSharedPreferences("melodyverse_settings", Context.MODE_PRIVATE)
                .getBoolean("offline_warning", true);
    }

    public static synchronized void start(Context context) {
        if (callback != null) return;
        Context appContext = context.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        wasOffline = !SyncManager.get(appContext).isOnline();
        validatedNetworks.clear();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                mainHandler.post(() -> {
                    if (validated) {
                        validatedNetworks.add(network);
                        onMaybeReconnected(appContext);
                    } else {
                        validatedNetworks.remove(network);
                        scheduleOfflineCheck(appContext);
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                mainHandler.post(() -> {
                    validatedNetworks.remove(network);
                    scheduleOfflineCheck(appContext);
                });
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, callback);
        } catch (Exception e) {
            callback = null;
        }
    }

    /** Chỉ coi là mất mạng khi không còn mạng nào truy cập được Internet (tránh báo nhầm lúc chuyển Wi-Fi ↔ 4G). */
    private static void scheduleOfflineCheck(Context appContext) {
        mainHandler.postDelayed(() -> {
            if (!validatedNetworks.isEmpty() || wasOffline || SyncManager.get(appContext).isOnline()) return;
            wasOffline = true;
            if (isWarningEnabled(appContext)) {
                Toast.makeText(appContext,
                        "Mất kết nối Internet! Thao tác của bạn sẽ được lưu trên máy và tự đồng bộ khi có mạng.",
                        Toast.LENGTH_LONG).show();
            }
            // Nhạc trực tuyến không thể phát tiếp khi mất mạng; nhạc mẫu trong APK vẫn phát bình thường
            if (MusicService.isPlaying && MusicService.isStreamingCurrent()) {
                MusicService.pause(appContext);
                Toast.makeText(appContext, "Đã tạm dừng nhạc trực tuyến do mất kết nối mạng.", Toast.LENGTH_SHORT).show();
            }
        }, 800);
    }

    private static void onMaybeReconnected(Context appContext) {
        if (!wasOffline) return;
        wasOffline = false;
        if (isWarningEnabled(appContext)) {
            Toast.makeText(appContext, "Đã kết nối lại Internet — đang đồng bộ dữ liệu...", Toast.LENGTH_SHORT).show();
        }
        // Tự động đẩy dữ liệu chờ lên Firebase rồi làm mới màn hình đang mở
        SyncManager.get(appContext).onReconnected();
    }
}
