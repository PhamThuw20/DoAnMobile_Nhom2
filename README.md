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
- Đăng ký / đăng nhập bằng username hoặc email, hoặc bằng tài khoản Google (Firebase Auth); quên mật khẩu qua email
- Tạo hồ sơ khi đăng ký: username, họ tên, avatar; bio chỉnh sửa trong trang cá nhân
- Phân quyền tự động: User / Admin

### 🏠 Bảng tin & Khám phá
- Xem bảng tin các bài chia sẻ công khai, sắp xếp theo thời gian mới nhất
- Lọc bài đăng theo thể loại (V-Pop, Rap / Hip-Hop, Indie & Chill) và tìm kiếm tiếng Việt không dấu theo tên bài, nghệ sĩ, hashtag
- Chế độ ngoại tuyến (Offline mode) khi mất kết nối mạng: xem dữ liệu đã lưu, thao tác được lưu chờ và tự đồng bộ khi có mạng
- Khám phá: bảng xếp hạng Top 3 theo lượt tim và vòng xoay đĩa than

### 🎶 Đăng bài chia sẻ nhạc
- **Cách 1 — Bài hát mẫu:** chọn bài hát có sẵn trong ứng dụng (nghe được cả khi ngoại tuyến)
- **Cách 2 — Dán link nhạc:** dán link nhạc trực tuyến (hỗ trợ link Audius), nhập tên bài, nghệ sĩ, chọn thể loại, hashtag
- Chế độ Công khai / Riêng tư; sửa / xóa bài của mình

### ▶️ Nghe nhạc trực tiếp trong ứng dụng
- Phát nhạc nền bằng Service + MediaPlayer, không bị ngắt khi chuyển màn hình
- Mini Player hiển thị cố định, mở rộng thành màn Đang phát (đĩa than xoay, sóng âm, tua, Next/Previous, Lặp lại / Ngẫu nhiên)
- Tự động chuyển bài (Auto-Next) theo danh sách phát từ bảng tin
- Lưu bản nhạc yêu t

### 💬 Tương tác xã hội
- Thích (Like) và bình luận (Comment) bài đăng, cập nhật theo thời gian thực
- Theo dõi (Follow/Unfollow) người dùng khác, xem hồ sơ và danh sách follower/following
- Tìm kiếm bài hát, nghệ sĩ hoặc hashtag
- Nhận thông báo khi có người thích, bình luận hoặc theo dõi mình
- Báo cáo bài đăng vi phạm

### 🛡️ Quản trị (Admin)
- Xem thống kê, ẩn / hiện lại hoặc xóa bài đăng
- Xem và xử lý danh sách bài đăng bị báo cáo (ẩn / xóa / bỏ qua)
- Quản lý tài khoản người dùng vi phạm (khóa / mở khóa tài khoản)

## CÔNG NGHỆ SỬ DỤNG

| Công nghệ | Vai trò trong dự án |
|---|---|
| Java | Ngôn ngữ lập trình chính, áp dụng lập trình hướng đối tượng (OOP) |
| Android Studio | IDE phát triển, debug và chạy thử ứng dụng |
| Firebase Authentication | Đăng nhập bằng email / mật khẩu và Google (Credential Manager), quản lý phiên đăng nhập, gửi email đặt lại mật khẩu |
| Firebase Firestore | CSDL NoSQL trên cloud, tổ chức theo collection và document, liên kết dữ liệu bằng ID |
| Room (SQLite) | CSDL local cho chế độ ngoại tuyến, cache bài đăng, bình luận và hàng chờ thao tác chờ đồng bộ |
| Service + MediaPlayer | Phát nhạc nền trong ứng dụng, duy trì khi chuyển màn hình |
| Audius API (HttpURLConnection) | Lấy thông tin bài hát và link phát từ link nhạc Audius |
| Drawables (res/drawable) | Ảnh tĩnh nhúng sẵn cho avatar |

**Kiến trúc:** Activity (kế thừa BaseActivity) + DatabaseHelper (SQLite) + SyncManager (đồng bộ với Firestore) + Service. Ứng dụng xây dựng hoàn toàn bằng Activity (không dùng Fragment).

## YÊU CẦU CHỨC NĂNG THEO VAI TRÒ

### Xác thực & phân quyền
- Đăng ký / đăng nhập bằng username hoặc email, hoặc bằng tài khoản Google (Firebase Auth + Google Sign-In); quên mật khẩu qua email
- Đăng ký hồ sơ: nhập username, họ tên, email, mật khẩu, chọn avatar (từ danh sách ảnh tĩnh); tài khoản Google được tự tạo hồ sơ ở lần đăng nhập đầu tiên
- Tự động phân quyền User (mặc định) hoặc Admin (tài khoản quản trị riêng, có document admins/{uid} trên Firestore)
- Duy trì phiên đăng nhập bằng Firebase Authentication session
- Đăng xuất ở trang Hồ sơ

### User (người dùng thường)

**Bảng tin**
- Xem danh sách bài đăng từ những người đang theo dõi, mới nhất trước
- Lọc bài đăng theo thể loại

**Đăng bài chia sẻ nhạc**
- Lựa chọn 1 (bài hát mẫu): chọn bài hát có sẵn trong ứng dụng
- Lựa chọn 2 (dán link nhạc): dán link nhạc trực tuyến (hỗ trợ link Audius, app tự lấy tên bài, nghệ sĩ và link phát), nhập tên bài, nghệ sĩ, chọn thể loại (V-Pop, Rap / Hip-Hop, R&B, Ballad, Indie / Acoustic, EDM), thêm hashtag
- Viết caption, chọn chế độ Công khai / Riêng tư rồi đăng bài; có thể sửa / xóa bài của mình
**Nghe nhạc & tương tác**
- Nhấn ▶️ để phát nhạc, Mini Player hiển thị dưới cùng, nhấn vào để mở Full Player (ảnh bìa, SeekBar, Next/Previous)
- Tự động chuyển bài khi hết bài
- Xem MV bằng Implicit Intent nếu bài đăng có link YouTube
- Like/Unlike (cập nhật thời gian thực), xem và thêm bình luận
- Báo cáo bài đăng vi phạm (Spam / Phản cảm / Bản quyền)
- Lưu ý: nhạc chỉ phát khi ứng dụng đang mở, tự dừng khi người dùng thoát hẳn ứng dụng

**Tìm kiếm & theo dõi**
- Tìm bài hát theo tên, nghệ sĩ hoặc hashtag (không phân biệt dấu); xem Bảng xếp hạng và Vòng xoay đĩa than ở tab Khám phá
- Xem trang cá nhân người dùng khác, Follow/Unfollow
- Xem danh sách đang theo dõi (following) và người theo dõi (followers)

**Hồ sơ cá nhân**
- Xem và chỉnh sửa hồ sơ, xem lại các bài đã chia sẻ, bài đã thích và nhạc đã lưu
- Nhận thông báo (thích, bình luận, theo dõi) ở tab Thông báo

**Chế độ ngoại tuyến**
- Tự phát hiện mất kết nối WiFi/4G
- Xem lại bảng tin và bình luận đã tải (SQLite cache)
- Thích, bình luận, đăng bài vẫn thực hiện được: thao tác được lưu kèm nhãn chờ và tự gửi lên khi có mạng; nghe nhạc trực tuyến khi mất mạng sẽ hiện thông báo ngoại tuyến

### Admin (quản trị viên, thao tác trên mobile)
- Tài khoản quản trị riêng: đăng nhập là vào thẳng trang Quản trị, không dùng giao diện thành viên
- Xem danh sách bài đăng bị báo cáo, xem chi tiết lý do và nội dung
- Xử lý báo cáo: "Ẩn bài", "Xóa bài" hoặc "Bỏ qua" (giữ nguyên bài)
- Xem danh sách người dùng, "Khóa tài khoản" (isLocked = true) hoặc "Mở khóa" (isLocked = false)
- Khi tài khoản bị khóa: client lắng nghe Firestore Snapshot Listener, phát hiện isLocked = true thì tự đăng xuất và quay về màn hình đăng nhập; tài khoản bị khóa không đăng nhập lại được cho tới khi được mở khóa

## CẤU TRÚC THƯ MỤC DỰ KIẾN

```
firestore.rules                        // Luật bảo mật Firestore
app/src/main/
├─ java/com/example/melodyverse/
│  ├─ SplashActivity.java               // Màn mở app
│  ├─ LoginActivity.java, RegisterActivity.java   // Đăng nhập, đăng ký
│  ├─ BaseActivity.java                 // Lớp cha: thanh điều hướng, Mini Player, màn Đang phát
│  ├─ MainActivity.java                 // Bảng tin + tìm kiếm + lọc thể loại
│  ├─ PostDetailActivity.java           // Chi tiết bài đăng + bình luận
│  ├─ CreatePostActivity.java           // Đăng / sửa bài
│  ├─ DiscoverActivity.java             // Khám phá: bảng xếp hạng, vòng xoay đĩa than
│  ├─ NotificationsActivity.java        // Thông báo
│  ├─ ProfileActivity.java              // Hồ sơ, theo dõi, nhạc đã lưu
│  ├─ SettingsActivity.java             // Cài đặt
│  ├─ AdminActivity.java                // Trang Quản trị
│  │
│  ├─ AuthManager.java, GoogleSignInHelper.java, SessionManager.java   // Xác thực, phiên đăng nhập
│  ├─ DatabaseHelper.java               // SQLite (cache + hàng chờ thao tác)
│  ├─ SyncManager.java, SyncStatus.java // Đồng bộ SQLite <-> Firestore, realtime
│  ├─ MusicService.java                 // Phát nhạc nền (Service + MediaPlayer)
│  ├─ NotificationHelper.java           // Thông báo điều khiển nhạc
│  ├─ AudiusApi.java                    // Lấy bài hát từ link Audius
│  ├─ MusicChart.java                   // Bảng xếp hạng, tìm kiếm không dấu
│  ├─ NetworkMonitor.java               // Phát hiện mất / có kết nối mạng
│  ├─ PostAdapter, CommentAdapter, TrackListAdapter, FollowUserAdapter, SimilarPostAdapter
│  ├─ DiscWheelView, WaveformSeekBar    // View tùy chỉnh
│  └─ PostModel, UserModel, CommentModel, NotificationModel   // Model
│
└─ res/
   ├─ layout/                           // Giao diện các màn hình, dialog, item
   ├─ drawable/                         // Icon, nền, avatar, ảnh tĩnh
   ├─ menu/                             // bottom_nav_menu.xml
   ├─ raw/                              // Nhạc mẫu (nghe được khi ngoại tuyến)
   ├─ anim/                             // Hiệu ứng chuyển
   └─ values/, values-night/            // Màu, theme (có chế độ tối)
```
## THÀNH VIÊN NHÓM

| Thành viên | Họ và tên | Github |
|:---:|---|---|
| Thành viên 1 | Trịnh Minh Đạt | [mdattrnh](https://github.com/mdattrnh) |
| Thành viên 2 | Trần Thị Ngọc Lan | [LanTranIT0001](https://github.com/LanTranIT0001) |
| Thành viên 3 | Phạm Minh Thư | [PhamThuw20](https://github.com/PhamThuw20) |
| Thành viên 4 | Nguyễn Ngọc Bích Trân | [Btran1201](https://github.com/Btran1201) |
| Thành viên 5 | Nguyễn Thị Bảo Trân | [Baotran265](https://github.com/Baotran265) |
