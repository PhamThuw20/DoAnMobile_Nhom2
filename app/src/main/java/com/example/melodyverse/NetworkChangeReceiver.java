package com.example.melodyverse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public
class NetworkChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                
                if (!isConnected) {
                    Toast.makeText(context, "Mất kết nối mạng! Chuyển sang chế độ ngoại tuyến.", Toast.LENGTH_LONG).show();
                    // TODO: Gửi sự kiện (EventBus / LocalBroadcastManager) để cập nhật UI vô hiệu hóa nút Đăng/Like
                } else {
                    Toast.makeText(context, "Đã khôi phục kết nối mạng.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
