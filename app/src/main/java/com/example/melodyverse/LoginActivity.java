package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE = "extra_login_message";

    // Trạng thái lưu lại khi xoay màn hình (Activity bị hủy rồi tạo lại)
    private static final String KEY_FORGOT_OPEN = "forgot_open";
    private static final String KEY_FORGOT_IDENTIFIER = "forgot_identifier";
    private static final String KEY_FORGOT_SENT_MSG = "forgot_sent_msg";

    // Lượt đăng nhập đang chờ Firebase + màn Đăng nhập đang hiển thị: xoay màn hình giữa chừng thì
    // kết quả được giao cho Activity mới thay vì Activity cũ đã bị hủy
    private static boolean signingIn = false;
    private static LoginActivity activeInstance;

    private EditText etUsername, etPassword;
    private View btnLogin;
    private CharSequence loginButtonText = "ĐĂNG NHẬP";

    // Hộp thoại Quên mật khẩu đang mở (để lưu / khôi phục khi xoay màn hình)
    private AlertDialog forgotDialog;
    private EditText etForgotIdentifier;
    private String forgotSentMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Áp dụng Dark Mode đã lưu trước khi inflate layout
        boolean darkMode = getSharedPreferences("melodyverse_settings", MODE_PRIVATE)
                .getBoolean("dark_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

        // Còn phiên đăng nhập (kể cả khi đang offline) thì vào thẳng ứng dụng
        if (AuthManager.hasValidSession(this) && getIntent().getStringExtra(EXTRA_MESSAGE) == null) {
            UserModel me = DatabaseHelper.getInstance(this).getUser(SessionManager.getCurrentUsername(this));
            openHome(me.isAdmin());
            return;
        }

        setContentView(R.layout.activity_login);
        activeInstance = this;

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        TextView btnQuickUser = findViewById(R.id.btn_quick_user);
        TextView btnQuickAdmin = findViewById(R.id.btn_quick_admin);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView btnRegister = findViewById(R.id.btn_register);

        // Nút tự điền tài khoản mẫu Thành viên
        if (btnQuickUser != null) {
            btnQuickUser.setOnClickListener(v -> {
                etUsername.setText("minhthu");
                etPassword.setText("12345678");
                Toast.makeText(this, "Đã điền tài khoản Thành viên MelodyVerse!", Toast.LENGTH_SHORT).show();
            });
        }

        // Nút tự điền tài khoản mẫu Quản trị viên (Admin)
        if (btnQuickAdmin != null) {
            btnQuickAdmin.setOnClickListener(v -> {
                etUsername.setText("admin");
                etPassword.setText("admin123");
                Toast.makeText(this, "Đã điền tài khoản Quản trị viên (Admin)!", Toast.LENGTH_SHORT).show();
            });
        }

        // Quên mật khẩu: Firebase Authentication gửi email chứa link đặt lại mật khẩu
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog(null, null));
        }

        // Đăng ký tài khoản - Chuyển sang RegisterActivity chuyên biệt
        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> {
                Intent registerIntent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivityForResult(registerIntent, 1001);
            });
        }

        // Nút Đăng nhập (Firebase Authentication)
        btnLogin = findViewById(R.id.btn_login);
        if (btnLogin instanceof TextView) loginButtonText = ((TextView) btnLogin).getText();
        // Vừa xoay màn hình khi đang đăng nhập: giữ nút ở trạng thái chờ đến khi có kết quả
        if (signingIn) setLoading(true);
        btnLogin.setOnClickListener(v -> {
            String username = etUsername != null ? etUsername.getText().toString().trim() : "";
            String password = etPassword != null ? etPassword.getText().toString().trim() : "";

            if (username.isEmpty()) {
                etUsername.setError("Vui lòng nhập tài khoản hoặc email!");
                etUsername.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError("Vui lòng nhập mật khẩu!");
                etPassword.requestFocus();
                return;
            }

            signingIn = true;
            setLoading(true);
            new AuthManager(this).signIn(username, password, result -> {
                signingIn = false;
                // Giao kết quả cho màn Đăng nhập hiện tại (có thể là Activity mới sau khi xoay màn hình)
                LoginActivity target = activeInstance;
                if (target == null || target.isFinishing() || target.isDestroyed()) return;
                target.onSignInResult(result);
            });
        });

        // Tiếp tục với Google: đăng nhập, hoặc tự tạo hồ sơ mới nếu tài khoản Google chưa từng dùng MelodyVerse
        new GoogleSignInHelper(this, result -> {
            if (result.status == AuthManager.Status.LOCKED) {
                showLockedDialog(result.username);
            } else if (result.status == AuthManager.Status.FAILED) {
                new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                        .setTitle("Không thể đăng nhập bằng Google")
                        .setMessage(result.message)
                        .setPositiveButton("Đã hiểu", null)
                        .show();
            } else {
                Toast.makeText(this, "Chào mừng đến với MelodyVerse!", Toast.LENGTH_SHORT).show();
                openHome(result.isAdmin, result.username, result.needsOnboarding);
            }
        });

        // Thông báo khi bị đưa về màn đăng nhập (ví dụ tài khoản vừa bị khóa)
        // (chỉ hiện lần đầu, không hiện lại mỗi lần xoay màn hình)
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message != null && savedInstanceState == null) {
            new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                    .setTitle("Phiên đăng nhập đã kết thúc")
                    .setMessage(message)
                    .setPositiveButton("Đã hiểu", null)
                    .show();
        }

        // Mở lại hộp thoại Quên mật khẩu nếu nó đang mở trước khi xoay màn hình
        if (savedInstanceState != null && savedInstanceState.getBoolean(KEY_FORGOT_OPEN)) {
            showForgotPasswordDialog(savedInstanceState.getString(KEY_FORGOT_IDENTIFIER),
                    savedInstanceState.getString(KEY_FORGOT_SENT_MSG));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (forgotDialog != null && forgotDialog.isShowing()) {
            outState.putBoolean(KEY_FORGOT_OPEN, true);
            if (etForgotIdentifier != null) {
                outState.putString(KEY_FORGOT_IDENTIFIER, etForgotIdentifier.getText().toString());
            }
            outState.putString(KEY_FORGOT_SENT_MSG, forgotSentMessage);
        }
    }

    @Override
    protected void onDestroy() {
        if (activeInstance == this) activeInstance = null;
        // Đóng hộp thoại để không bị rò rỉ cửa sổ khi Activity bị hủy (sẽ mở lại sau khi tạo lại)
        if (forgotDialog != null) forgotDialog.dismiss();
        super.onDestroy();
    }

    /** Kết quả đăng nhập bằng tài khoản + mật khẩu (Firebase Authentication). */
    private void onSignInResult(AuthManager.Result result) {
        setLoading(false);
        if (result.status == AuthManager.Status.LOCKED) {
            showLockedDialog(result.username);
        } else if (result.status == AuthManager.Status.FAILED) {
            if (AuthManager.FIELD_USERNAME.equals(result.field)) {
                etUsername.setError(result.message);
                etUsername.requestFocus();
            } else if (AuthManager.FIELD_PASSWORD.equals(result.field)) {
                etPassword.setError(result.message);
                etPassword.requestFocus();
            }
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Chào mừng đến với MelodyVerse!", Toast.LENGTH_SHORT).show();
            openHome(result.isAdmin, result.username, result.needsOnboarding);
        }
    }

    private void setLoading(boolean loading) {
        if (btnLogin == null) return;
        btnLogin.setEnabled(!loading);
        btnLogin.setAlpha(loading ? 0.6f : 1f);
        if (btnLogin instanceof TextView) {
            ((TextView) btnLogin).setText(loading ? "Đang đăng nhập..." : loginButtonText);
        }
    }

    /**
     * savedIdentifier: nội dung ô nhập cần khôi phục (null = điền sẵn từ màn đăng nhập);
     * sentMessage: khác null nếu hộp thoại đang ở trạng thái "Đã gửi email" trước khi xoay màn hình.
     */
    private void showForgotPasswordDialog(String savedIdentifier, String sentMessage) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null, false);
        EditText etIdentifier = dialogView.findViewById(R.id.et_forgot_identifier);
        if (savedIdentifier != null) {
            etIdentifier.setText(savedIdentifier);
        } else if (etUsername != null) {
            // Điền sẵn tài khoản đang nhập ở màn đăng nhập (nếu có)
            etIdentifier.setText(etUsername.getText().toString().trim());
        }

        Button btnSend = dialogView.findViewById(R.id.btn_forgot_send);
        Button btnCancel = dialogView.findViewById(R.id.btn_forgot_cancel);

        // Giao diện riêng (cùng kiểu hộp thoại Đăng xuất): nền trong suốt để hiện góc bo của layout
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        forgotDialog = dialog;
        etForgotIdentifier = etIdentifier;
        forgotSentMessage = null;
        dialog.setOnDismissListener(d -> {
            if (forgotDialog == dialog) forgotDialog = null;
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        // Tự xử lý nút Gửi để hộp thoại không đóng khi nhập sai / đang gửi
        btnSend.setOnClickListener(v -> {
            String identifier = etIdentifier.getText().toString().trim();
            if (identifier.isEmpty()) {
                etIdentifier.setError("Vui lòng nhập tên đăng nhập hoặc email!");
                etIdentifier.requestFocus();
                return;
            }
            btnSend.setEnabled(false);
            btnCancel.setEnabled(false);
            btnSend.setText("Đang gửi...");
            dialog.setCancelable(false);
            new AuthManager(this).sendPasswordReset(identifier, result -> {
                if (isFinishing() || isDestroyed()) return;
                btnSend.setEnabled(true);
                btnCancel.setEnabled(true);
                btnSend.setText("Gửi email");
                dialog.setCancelable(true);
                if (result.status == AuthManager.Status.OK) {
                    showForgotPasswordSent(dialogView, dialog, result.message);
                } else if (AuthManager.FIELD_USERNAME.equals(result.field)) {
                    etIdentifier.setError(result.message);
                    etIdentifier.requestFocus();
                } else {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                }
            });
        });
        etIdentifier.setOnEditorActionListener((tv, actionId, event) -> {
            btnSend.performClick();
            return true;
        });
        if (sentMessage != null) showForgotPasswordSent(dialogView, dialog, sentMessage);
        dialog.show();
    }

    /** Gửi thành công: chuyển ngay hộp thoại sang trạng thái "Đã gửi email" thay vì mở thêm hộp thoại mới. */
    private void showForgotPasswordSent(View dialogView, AlertDialog dialog, String message) {
        forgotSentMessage = message;
        ((android.widget.ImageView) dialogView.findViewById(R.id.iv_forgot_icon)).setImageResource(R.drawable.ic_mail);
        ((TextView) dialogView.findViewById(R.id.tv_forgot_title)).setText("Đã gửi email");
        ((TextView) dialogView.findViewById(R.id.tv_forgot_msg)).setText(message);
        dialogView.findViewById(R.id.et_forgot_identifier).setVisibility(View.GONE);
        dialogView.findViewById(R.id.btn_forgot_cancel).setVisibility(View.GONE);
        Button btnSend = dialogView.findViewById(R.id.btn_forgot_send);
        btnSend.setText("Đã hiểu");
        btnSend.setOnClickListener(v -> dialog.dismiss());
    }

    private void showLockedDialog(String accountUsername) {
        new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                .setTitle("Tài Khoản Đã Bị Khóa")
                .setMessage("Tài khoản @" + accountUsername + " đã bị Quản trị viên tạm thời khóa do vi phạm tiêu chuẩn cộng đồng MelodyVerse.\n\nVui lòng liên hệ ban quản trị để được hỗ trợ mở khóa.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    /** Phân quyền điều hướng theo vai trò: Admin -> AdminActivity, User -> MainActivity. */
    private void openHome(boolean isAdmin) {
        openHome(isAdmin, null, false);
    }

    /** needsOnboarding: tài khoản mới → Bảng tin hiện bước gợi ý nghệ sĩ (người dùng tự chọn theo dõi hoặc bỏ qua). */
    private void openHome(boolean isAdmin, String username, boolean needsOnboarding) {
        Intent intent = new Intent(LoginActivity.this, isAdmin ? AdminActivity.class : MainActivity.class);
        if (!isAdmin && username != null && AuthManager.shouldShowOnboarding(this, username, needsOnboarding)) {
            intent.putExtra(MainActivity.EXTRA_SHOW_ARTIST_SUGGESTIONS, true);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String regUser = data.getStringExtra("registered_username");
            String regPass = data.getStringExtra("registered_password");
            if (regUser != null && etUsername != null) {
                etUsername.setText(regUser);
            }
            if (regPass != null && etPassword != null) {
                etPassword.setText(regPass);
            }
            Toast.makeText(this, "Tài khoản @" + regUser + " đã sẵn sàng. Nhấn Đăng nhập để vào app!", Toast.LENGTH_LONG).show();
        }
    }
}
