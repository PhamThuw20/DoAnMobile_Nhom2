package com.example.melodyverse;

import android.app.Activity;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialInterruptedException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * Nút "Tiếp tục với Google" dùng chung cho màn Đăng nhập và Đăng ký.
 *
 * Luồng: Credential Manager hiện bảng chọn tài khoản Google → nhận Google ID token →
 * {@link AuthManager#signInWithGoogle} đăng nhập Firebase Authentication bằng GoogleAuthProvider
 * và lấy / tạo hồ sơ users/{username} trên Firestore (không tạo trùng hồ sơ).
 *
 * Cần cấu hình trên Firebase Console: bật nhà cung cấp Google, thêm SHA-1 / SHA-256 của khóa ký
 * rồi tải lại google-services.json (sinh ra chuỗi default_web_client_id).
 */
public class GoogleSignInHelper {

    public interface Listener {
        /** Đăng nhập Firebase xong (thành công, bị khóa hoặc thất bại kèm thông báo). */
        void onResult(AuthManager.Result result);
    }

    private final Activity activity;
    private final View button;
    private final TextView label;
    private final ImageView logo;
    private final ProgressBar progress;
    private final Listener listener;
    private boolean busy = false;

    public GoogleSignInHelper(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        button = activity.findViewById(R.id.btn_google_sign_in);
        label = activity.findViewById(R.id.tv_google_sign_in);
        logo = activity.findViewById(R.id.iv_google_logo);
        progress = activity.findViewById(R.id.pb_google_loading);
        if (button != null) button.setOnClickListener(v -> start());
    }

    /** Web client ID do plugin google-services sinh ra (0 nếu google-services.json chưa có OAuth client). */
    static String webClientId(Activity activity) {
        int id = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        return id != 0 ? activity.getString(id) : null;
    }

    private void start() {
        if (busy) return;
        if (!SyncManager.get(activity).isOnline()) {
            toast("Cần kết nối Internet để đăng nhập bằng Google. Vui lòng bật Wi-Fi hoặc dữ liệu di động!");
            return;
        }
        String clientId = webClientId(activity);
        if (clientId == null || clientId.isEmpty()) {
            toast("Google Sign-In chưa được cấu hình cho ứng dụng (thiếu Web client ID trong google-services.json). "
                    + "Hãy bật Google trong Firebase Authentication, thêm SHA-1 rồi tải lại google-services.json.");
            return;
        }
        setBusy(true, "Đang mở tài khoản Google...");

        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(clientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleCredential(response.getCredential());
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        android.util.Log.w("GoogleSignIn", "Credential Manager lỗi: " + e.getClass().getSimpleName() + " / " + e.getType() + " / " + e.getMessage());
                        setBusy(false, null);
                        if (e instanceof GetCredentialCancellationException) {
                            String msg = e.getMessage() != null ? e.getMessage() : "";
                            if (msg.contains("[16]") || msg.toLowerCase().contains("reauth")) {
                                // Google trả về hủy sau khi đã chọn tài khoản: thường do SHA-1 của APK
                                // không khớp OAuth client Android trên Firebase hoặc OAuth consent screen chưa cấu hình.
                                toast("Google từ chối đăng nhập sau khi chọn tài khoản (" + msg + ").\n\n"
                                        + "Thường do SHA-1 của bản cài trên máy không khớp với Firebase, "
                                        + "hoặc OAuth consent screen chưa cấu hình / tài khoản chưa nằm trong Test users.");
                            } else {
                                toast("Bạn đã hủy đăng nhập bằng Google." + (msg.isEmpty() ? "" : "\n(" + msg + ")"));
                            }
                        } else if (e instanceof NoCredentialException) {
                            toast("Không tìm thấy tài khoản Google trên thiết bị. Hãy thêm tài khoản Google trong Cài đặt rồi thử lại.");
                        } else if (e instanceof androidx.credentials.exceptions.GetCredentialProviderConfigurationException) {
                            toast("Thiết bị chưa hỗ trợ đăng nhập Google: Google Play services quá cũ hoặc chưa được cài. "
                                    + "Hãy cập nhật Google Play services (qua Cửa hàng Play) rồi thử lại.");
                        } else if (e instanceof GetCredentialInterruptedException) {
                            toast("Đăng nhập Google bị gián đoạn, vui lòng thử lại!");
                        } else if (!SyncManager.get(activity).isOnline()) {
                            toast("Mất kết nối mạng khi đăng nhập Google. Vui lòng kết nối lại và thử lại!");
                        } else {
                            toast("Không đăng nhập được bằng Google: " + e.getMessage());
                        }
                    }
                });
    }

    private void handleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            setBusy(false, null);
            toast("Phương thức xác thực không được hỗ trợ.");
            return;
        }
        String idToken;
        try {
            idToken = GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData()).getIdToken();
        } catch (Exception e) {
            setBusy(false, null);
            toast("Xác thực Google thất bại (token không hợp lệ). Vui lòng thử lại!");
            return;
        }
        setBusy(true, "Đang xác thực với MelodyVerse...");
        new AuthManager(activity).signInWithGoogle(idToken, result -> {
            if (activity.isFinishing()) return;
            setBusy(false, null);
            listener.onResult(result);
        });
    }

    private void setBusy(boolean value, String text) {
        busy = value;
        if (button != null) {
            button.setEnabled(!value);
            button.setAlpha(value ? 0.7f : 1f);
        }
        if (label != null) label.setText(value && text != null ? text : "Tiếp tục với Google");
        if (logo != null) logo.setVisibility(value ? View.GONE : View.VISIBLE);
        if (progress != null) progress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    /** Hiện lý do bằng hộp thoại (Toast có thể bị hệ thống bỏ qua nên người dùng không thấy). */
    private void toast(String msg) {
        if (activity.isFinishing()) return;
        new androidx.appcompat.app.AlertDialog.Builder(activity, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle("Đăng nhập Google")
                .setMessage(msg)
                .setPositiveButton("Đã hiểu", null)
                .show();
    }
}
