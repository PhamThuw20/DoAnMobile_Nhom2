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

## CÔNG NGHỆ SỬ DỤNG

| Công nghệ | Vai trò trong dự án |
|---|---|
| Java | Ngôn ngữ lập trình chính, áp dụng lập trình hướng đối tượng (OOP) |
| Android Studio | IDE phát triển, debug và chạy thử ứng dụng |
| Firebase Authentication | Đăng nhập bằng Google OAuth, quản lý phiên đăng nhập |
| Firebase Firestore | CSDL NoSQL trên cloud, tổ chức theo collection và document, liên kết dữ liệu bằng ID |
| Firebase Storage | Lưu file nhạc MP3 người dùng tải lên (giới hạn < 5MB) |
| Room (SQLite) | CSDL local cho chế độ ngoại tuyến, cache bài đăng và bình luận |
| Service + MediaPlayer | Phát nhạc nền trong ứng dụng, duy trì khi chuyển màn hình |
| Retrofit & Gson | Gọi iTunes API để tìm kiếm và lấy dữ liệu âm nhạc tự động |
| Drawables (res/drawable) | Ảnh tĩnh nhúng sẵn cho avatar |
| Adaptive layouts (layout-land) | Giao diện responsive khi xoay ngang màn hình |

**Kiến trúc:** MVVM + Repository + Service. Ứng dụng xây dựng hoàn toàn bằng Activity (không dùng Fragment).

## YÊU CẦU CHỨC NĂNG THEO VAI TRÒ

### Xác thực & phân quyền
- Đăng nhập bằng tài khoản Google (Firebase Auth + Google Sign-In)
- Đăng ký hồ sơ lần đầu: nhập username, chọn avatar (từ danh sách ảnh tĩnh), bio
- Tự động phân quyền User (mặc định) hoặc Admin dựa vào field `role` trong Firestore
- Duy trì phiên đăng nhập bằng Firebase Authentication session
- Đăng xuất qua Navigation Drawer

### User (người dùng thường)

**Bảng tin**
- Xem danh sách bài đăng từ những người đang theo dõi, mới nhất trước
- Lọc bài đăng theo thể loại

**Đăng bài chia sẻ nhạc**
- Lựa chọn 1 (iTunes API): nhập tên bài hát, app tự điền tên bài, nghệ sĩ, ảnh bìa, link nhạc 30s; thể loại được ánh xạ về 6 thể loại chuẩn của app
- Lựa chọn 2 (tự upload): nhập tay tên bài, nghệ sĩ, chọn thể loại, chọn file MP3 từ máy (< 5MB)
- Tùy chọn dán link YouTube; viết caption rồi đăng bài

**Nghe nhạc & tương tác**
- Nhấn ▶️ để phát nhạc, Mini Player hiển thị dưới cùng, nhấn vào để mở Full Player (ảnh bìa, SeekBar, Next/Previous)
- Tự động chuyển bài khi hết bài
- Xem MV bằng Implicit Intent nếu bài đăng có link YouTube
- Like/Unlike (cập nhật thời gian thực), xem và thêm bình luận
- Báo cáo bài đăng vi phạm (Spam / Phản cảm / Bản quyền)
- Lưu ý: nhạc chỉ phát khi ứng dụng đang mở, tự dừng khi người dùng thoát hẳn ứng dụng

**Tìm kiếm & theo dõi**
- Tìm bài hát theo tên hoặc tìm người dùng
- Xem trang cá nhân người dùng khác, Follow/Unfollow
- Xem danh sách đang theo dõi (following) và người theo dõi (followers)

**Hồ sơ cá nhân**
- Xem và chỉnh sửa hồ sơ, xem lại các bài đăng của mình

**Chế độ ngoại tuyến**
- Tự phát hiện mất kết nối WiFi/4G
- Xem lại bảng tin và bình luận đã tải (Room cache)
- Hiển thị nhãn "Đang xem ngoại tuyến", tự động vô hiệu hóa Đăng bài / Like / Comment

### Admin (quản trị viên, thao tác trên mobile)
- Kế thừa toàn bộ quyền của User; Navigation Drawer hiện thêm mục quản trị
- Xem danh sách bài đăng bị báo cáo, xem chi tiết lý do và nội dung
- Xử lý báo cáo: "Xóa bài" (chuyển status thành `deleted`) hoặc "Bỏ qua" (giữ nguyên bài)
- Xem danh sách người dùng, "Khóa tài khoản" (`isActive = false`) hoặc "Mở khóa" (`isActive = true`)
- Cơ chế khóa hoạt động qua 2 lớp: (1) client lắng nghe Firestore Snapshot Listener, khi phát hiện `isActive = false` thì tự đăng xuất và quay về màn hình đăng nhập; (2) Firebase Security Rules từ chối mọi thao tác ghi của tài khoản bị khóa

## CẤU TRÚC THƯ MỤC DỰ KIẾN

```
app/
├─ BaseApplication.java              // Đếm Activity đang mở để dừng nhạc khi thoát app
├─ data/
│  ├─ local/
│  │  ├─ AppDatabase.java            // Kế thừa RoomDatabase
│  │  ├─ dao/
│  │  │  ├─ PostDao.java
│  │  │  └─ CommentDao.java
│  │  └─ entity/
│  │     ├─ PostEntity.java
│  │     └─ CommentEntity.java
│  └─ repository/
│     ├─ BaseRepository.java         // Kiểm tra kết nối mạng
│     ├─ PostRepository.java         // Chọn nguồn dữ liệu: Firestore hay Room
│     ├─ UserRepository.java
│     ├─ MusicRepository.java        // Gọi iTunes API
│     └─ DataCallback.java           // Interface callback cho các Repository
│
├─ model/
│  ├─ BaseEntity.java                // Abstract, lớp cha chung cho các model
│  ├─ User.java, AdminUser.java      // AdminUser extends User
│  ├─ Post.java, Comment.java
│  ├─ Like.java, Follow.java, Report.java
│  └─ Track.java                     // Model riêng cho trình phát nhạc
│
├─ service/
│  ├─ BaseMusicService.java          // Abstract extends Service
│  ├─ MusicService.java              // prepareAsync, play, pause, stop, release
│  └─ MusicQueueManager.java         // Singleton giữ playlist và vị trí bài hiện tại
│
├─ ui/
│  ├─ auth/
│  │  └─ LoginActivity.java          // Đăng nhập Google
│  ├─ feed/
│  │  └─ FeedActivity.java           // Navigation Drawer + Mini Player
│  ├─ post/
│  │  ├─ PostDetailActivity.java     // Chi tiết bài đăng + bình luận
│  │  ├─ CreatePostActivity.java     // Form đăng bài (2 lựa chọn)
│  │  └─ FullPlayerActivity.java     // Trình phát toàn màn hình
│  ├─ profile/
│  │  ├─ ProfileActivity.java        // Hồ sơ + nút Follow
│  │  └─ EditProfileActivity.java    // Tạo hồ sơ lần đầu và sửa hồ sơ
│  ├─ search/
│  │  └─ SearchActivity.java         // Tìm bài hát / người dùng
│  └─ admin/
│     ├─ AdminDashboardActivity.java // Danh sách báo cáo
│     └─ ManageUsersActivity.java    // Khóa / mở khóa tài khoản
│
├─ api/
│  ├─ ITunesApi.java                 // Retrofit interface
│  └─ RetrofitClient.java            // Cấu hình Retrofit & Gson
│
├─ viewmodel/
│  ├─ FeedViewModel.java
│  ├─ PostDetailViewModel.java
│  └─ CreatePostViewModel.java
│
├─ adapter/
│  ├─ PostAdapter.java
│  └─ CommentAdapter.java
│
└─ util/
   ├─ NetworkUtils.java
   ├─ GenreMapper.java               // Ánh xạ thể loại iTunes về 6 thể loại chuẩn
   └─ Constants.java

res/
├─ layout/                           // Layout dọc + Mini Player
├─ layout-land/                      // Layout ngang (GridLayoutManager)
├─ drawable/                         // Avatar, ảnh bìa mặc định
└─ menu/                             // drawer_menu.xml
```
## THÀNH VIÊN NHÓM

| Thành viên | Họ và tên | Github |
|:---:|---|---|
| Thành viên 1 | Trịnh Minh Đạt | [mdattrnh](https://github.com/mdattrnh) |
| Thành viên 2 | Trần Thị Ngọc Lan | [LanTranIT0001](https://github.com/LanTranIT0001) |
| Thành viên 3 | Phạm Minh Thư | [PhamThuw20](https://github.com/PhamThuw20) |
| Thành viên 4 | Nguyễn Ngọc Bích Trân | [Btran1201](https://github.com/Btran1201) |
| Thành viên 5 | Nguyễn Thị Bảo Trân | [Baotran265](https://github.com/Baotran265) |
