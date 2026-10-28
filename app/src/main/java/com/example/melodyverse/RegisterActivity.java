package com.example.melodyverse;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Màn hình Đăng ký tài khoản thành viên MelodyVerse (Firebase Authentication + hồ sơ trên Firestore).
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etUsername, etEmail, etPassword, etConfirmPassword;
    private FrameLayout[] avatarViews;
    private int selectedAvatarIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupAvatarSelection();
        setupListeners();
    }

    private void initViews() {
        etFullName = findViewById(R.id.et_reg_fullname);
        etUsername = findViewById(R.id.et_reg_username);
        etEmail = findViewById(R.id.et_reg_email);
        etPassword = findViewById(R.id.et_reg_password);
        etConfirmPassword = findViewById(R.id.et_reg_confirm_password);

        avatarViews = new FrameLayout[]{
                findViewById(R.id.avatar_opt_0),
                findViewById(R.id.avatar_opt_1),
                findViewById(R.id.avatar_opt_2),
                findViewById(R.id.avatar_opt_3),
                findViewById(R.id.avatar_opt_4)
        };
    }

    private void setupAvatarSelection() {
        for (int i = 0; i < avatarViews.length; i++) {
            final int index = i;
            if (avatarViews[i] != null) {
                avatarViews[i].setOnClickListener(v -> selectAvatar(index));
            }
        }
        selectAvatar(0);
    }

    private void selectAvatar(int index) {
        selectedAvatarIndex = index;
        for (int i = 0; i < avatarViews.length; i++) {
            if (avatarViews[i] != null) {
                if (i == index) {
                    avatarViews[i].setBackgroundResource(R.drawable.bg_story_ring);
                    avatarViews[i].animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                } else {
                    avatarViews[i].setBackgroundResource(R.drawable.bg_avatar_circle);
                    avatarViews[i].animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
        }
    }

    private void setupListeners() {
        // Nút quay lại
        View btnBack = findViewById(R.id.iv_register_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Chuyển sang đăng nhập
        View tvToLogin = findViewById(R.id.tv_to_login);
        if (tvToLogin != null) {
            tvToLogin.setOnClickListener(v -> finish());
        }

        // Nút Tạo tài khoản
        findViewById(R.id.btn_register_submit).setOnClickListener(v -> handleRegister());

        // Đăng ký nhanh bằng Google: tạo hồ sơ (hoặc dùng hồ sơ đã có) rồi vào thẳng ứng dụng
        new GoogleSignInHelper(this, result -> {
            if (result.status == AuthManager.Status.OK) {
                Toast.makeText(this, "Chào mừng đến với MelodyVerse!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, result.isAdmin ? AdminActivity.class : MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                if (!result.isAdmin && AuthManager.shouldShowOnboarding(this, result.username, result.needsOnboarding)) {
                    intent.putExtra(MainActivity.EXTRA_SHOW_ARTIST_SUGGESTIONS, true);
                }
                startActivity(intent);
                finish();
            } else {
                String msg = result.status == AuthManager.Status.LOCKED
                        ? "Tài khoản @" + result.username + " đã bị Quản trị viên khóa."
                        : result.message;
                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.ThemeOverlay_MelodyVerse_Dialog)
                        .setTitle("Không thể tiếp tục với Google")
                        .setMessage(msg)
                        .setPositiveButton("Đã hiểu", null)
                        .show();
            }
        });
    }

    private void handleRegister() {
        String fullName = etFullName != null ? etFullName.getText().toString().trim() : "";
        String username = etUsername != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword != null ? etConfirmPassword.getText().toString().trim() : "";

        // Kiểm tra Họ và tên
        if (fullName.isEmpty()) {
            etFullName.setError("Vui lòng nhập họ và tên của bạn!");
            etFullName.requestFocus();
            return;
        }

        // Kiểm tra Tên đăng nhập
        if (username.isEmpty()) {
            etUsername.setError("Vui lòng nhập tên đăng nhập!");
            etUsername.requestFocus();
            return;
        }
        if (username.contains(" ")) {
            etUsername.setError("Tên đăng nhập không được chứa khoảng trắng!");
            etUsername.requestFocus();
            return;
        }
        if (username.length() < 3) {
            etUsername.setError("Tên đăng nhập tối thiểu 3 ký tự!");
            etUsername.requestFocus();
            return;
        }
        if (!username.matches("[A-Za-z0-9._]+")) {
            etUsername.setError("Tên đăng nhập chỉ gồm chữ không dấu, số, dấu chấm hoặc gạch dưới!");
            etUsername.requestFocus();
            return;
        }

        // Kiểm tra Email
        if (email.isEmpty()) {
            etEmail.setError("Vui lòng nhập địa chỉ email!");
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Email không đúng định dạng!");
            etEmail.requestFocus();
            return;
        }

        // Kiểm tra Mật khẩu
        if (password.isEmpty()) {
            etPassword.setError("Vui lòng nhập mật khẩu!");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Mật khẩu phải có ít nhất 6 ký tự!");
            etPassword.requestFocus();
            return;
        }

        // Kiểm tra Xác nhận mật khẩu
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Vui lòng xác nhận lại mật khẩu!");
            etConfirmPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Mật khẩu xác nhận không khớp!");
            etConfirmPassword.requestFocus();
            return;
        }

        // Tạo tài khoản trên Firebase Authentication + hồ sơ Firestore (kiểm tra trùng username / email trên máy chủ)
        View btnSubmit = findViewById(R.id.btn_register_submit);
        btnSubmit.setEnabled(false);
        btnSubmit.setAlpha(0.6f);
        new AuthManager(this).register(username, fullName, email, password, selectedAvatarIndex, result -> {
            if (isFinishing()) return;
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1f);
            if (result.status != AuthManager.Status.OK) {
                if (AuthManager.FIELD_USERNAME.equals(result.field)) {
                    etUsername.setError(result.message);
                    etUsername.requestFocus();
                } else if (AuthManager.FIELD_EMAIL.equals(result.field)) {
                    etEmail.setError(result.message);
                    etEmail.requestFocus();
                } else if (AuthManager.FIELD_PASSWORD.equals(result.field)) {
                    etPassword.setError(result.message);
                    etPassword.requestFocus();
                }
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Đăng ký thành công! Hãy đăng nhập để bắt đầu trải nghiệm", Toast.LENGTH_LONG).show();

            // Trả thông tin về LoginActivity để người dùng bấm Đăng nhập
            Intent resultIntent = new Intent();
            resultIntent.putExtra("registered_username", result.username);
            resultIntent.putExtra("registered_password", password);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}
