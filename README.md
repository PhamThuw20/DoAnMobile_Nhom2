<div align="center">

### 🎵 MELODYVERSE 🎵
### Mạng xã hội chia sẻ âm nhạc

MelodyVerse là ứng dụng kết hợp giữa **mạng xã hội** và **trình phát nhạc nội bộ**, cho phép người dùng chia sẻ, khám phá và kết nối với nhau xoay quanh âm nhạc — tất cả trong một không gian duy nhất, không cần rời khỏi ứng dụng để nghe nhạc.

</div>

## GIỚI THIỆU

Trong thời đại âm nhạc trở thành một phần không thể thiếu của cuộc sống, nhu cầu chia sẻ bài hát yêu thích, khám phá những giai điệu mới và kết nối với những người có cùng gu âm nhạc ngày càng phổ biến. Tuy nhiên, những hoạt động này thường diễn ra trên nhiều nền tảng khác nhau, khiến trải nghiệm chia sẻ và tương tác chưa thực sự liền mạch.

**MelodyVerse** ra đời nhằm kết nối những trải nghiệm ấy trong một không gian duy nhất, nơi người dùng có thể chia sẻ bài hát, khám phá nội dung từ cộng đồng, tương tác và kết nối với những người có cùng sở thích âm nhạc.

Điểm nổi bật của ứng dụng là khả năng tích hợp trình phát nhạc trực tiếp, cho phép người dùng thưởng thức âm nhạc ngay trong ứng dụng và duy trì phát nhạc nền khi chuyển đổi giữa các màn hình. Nhờ đó, việc nghe nhạc và tương tác trên mạng xã hội được kết hợp trong một trải nghiệm thống nhất.

**MelodyVerse** là sản phẩm được phát triển trong đồ án môn **Phát triển ứng dụng Mobile**, với mục tiêu xây dựng một nền tảng chia sẻ âm nhạc trực quan, tiện lợi và gắn kết cộng đồng.

## TÍNH NĂNG CHÍNH

### 🔐 Xác thực
- Đăng nhập bằng tài khoản Google (Firebase Auth)
- Tạo hồ sơ lần đầu: username, avatar, bio
- Phân quyền tự động: User / Admin

### 🏠 Bảng tin & Khám phá
- Xem bảng tin từ những người đang theo dõi, sắp xếp theo thời gian mới nhất
- Lọc bài đăng theo 6 thể loại: Pop, Rock, EDM, Rap, Acoustic, Ballad
- Chế độ xem ngoại tuyến (Offline mode) khi mất kết nối mạng

### 🎶 Đăng bài chia sẻ nhạc
- **Cách 1 — Tìm qua iTunes API:** nhập tên bài hát, hệ thống tự động điền tên bài, nghệ sĩ, ảnh bìa và link nhạc
- **Cách 2 — Tự upload:** nhập tay thông tin bài hát, chọn thể loại, tải file MP3 từ máy
- Tùy chọn đính kèm link YouTube để xem MV

### ▶️ Nghe nhạc trực tiếp trong ứng dụng
- Phát nhạc nền bằng Service + MediaPlayer, không bị ngắt khi chuyển màn hình
- Mini Player hiển thị cố định, mở rộng thành Full Player với ảnh bìa, thanh tiến trình, điều khiển Next/Previous
- Tự động chuyển bài (Auto-Next) theo danh sách phát từ bảng tin
- Xem MV bằng Implicit Intent (mở ứng dụng YouTube)

### 💬 Tương tác xã hội
- Thích (Like) và bình luận (Comment) bài đăng, cập nhật theo thời gian thực
- Theo dõi (Follow/Unfollow) người dùng khác, xem hồ sơ và danh sách follower/following
- Tìm kiếm bài hát hoặc người dùng
- Báo cáo bài đăng vi phạm

### 🛡️ Quản trị (Admin)
- Xem và xử lý danh sách bài đăng bị báo cáo (xóa / bỏ qua)
- Quản lý tài khoản người dùng vi phạm (khóa tài khoản)
