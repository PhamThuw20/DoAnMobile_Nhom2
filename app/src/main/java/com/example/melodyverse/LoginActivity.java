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

    private EditText etUsername, etPassword;
    private View btnLogin;
    private CharSequence loginButtonText = "ĐĂNG NHẬP";

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
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
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

            setLoading(true);
            new AuthManager(this).signIn(username, password, result -> {
                if (isFinishing()) return;
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
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message != null) {
            new AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                    .setTitle("Phiên đăng nhập đã kết thúc")
                    .setMessage(message)
                    .setPositiveButton("Đã hiểu", null)
                    .show();
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

    private void showForgotPasswordDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null, false);
        EditText etIdentifier = dialogView.findViewById(R.id.et_forgot_identifier);
        // Điền sẵn tài khoản đang nhập ở màn đăng nhập (nếu có)
        if (etUsername != null) etIdentifier.setText(etUsername.getText().toString().trim());

        Button btnSend = dialogView.findViewById(R.id.btn_forgot_send);
        Button btnCancel = dialogView.findViewById(R.id.btn_forgot_cancel);

        // Giao diện riêng (cùng kiểu hộp thoại Đăng xuất): nền trong suốt để hiện góc bo của layout
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

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
                if (isFinishing()) return;
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
        dialog.show();
    }

    /** Gửi thành công: chuyển ngay hộp thoại sang trạng thái "Đã gửi email" thay vì mở thêm hộp thoại mới. */
    private void showForgotPasswordSent(View dialogView, AlertDialog dialog, String message) {
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
