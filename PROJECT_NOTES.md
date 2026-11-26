Giai đoạn: 1

# Signing Key Vault – ghi chú dự án

## Mục tiêu
App Android cá nhân để tạo và quản lý khóa ký (keystore) ngay trên điện thoại, chuyển sang base64 để dán vào GitHub Secrets, lưu thêm bí mật cho web/web app, có sao lưu mạnh.

## Kế hoạch đã chốt (v1)
- Phạm vi: keystore Android (PKCS12) + bí mật dạng văn bản/tệp. SSH/VAPID để sau.
- Không có quyền INTERNET. Đẩy secret lên GitHub qua API để v2 (cần libsodium).
- Mở khóa: sinh trắc học hoặc khóa màn hình (BiometricPrompt). Sao lưu bằng mật khẩu riêng.
- Giao diện: Jetpack Compose, tiếng Việt và tiếng Anh (theo ngôn ngữ hệ thống).

## Quyết định kỹ thuật
- minSdk 30, targetSdk 36, compileSdk 37; AGP 9.1.1 (Kotlin tích hợp), Gradle 9.3.1, JDK 17.
- applicationId `io.github.trunghuynhvan0310.signingkeyvault` (bản debug thêm `.debug`).
- Kho: khóa DEK ngẫu nhiên 256-bit, bọc bằng khóa AES trong Android Keystore (cần xác thực, hiệu lực 30 giây); vault mã hóa AES-GCM bằng DEK.
- Sao lưu `.vault`: Argon2id + AES-256-GCM (Bouncy Castle), ghi luân phiên 2 tệp A/B vào thư mục SAF, đọc lại để kiểm tra sau mỗi lần ghi.
- Keystore sinh bằng Bouncy Castle (RSA 2048/4096, PKCS12); mật khẩu store = mật khẩu khóa (PKCS12 không hỗ trợ khác nhau).
- Phiên bản: `version.properties`. APK: `signing-key-vault-v<ver>.apk`.

## Việc còn dở
- Chưa tạo khóa ký release cho chính app này (bản đầu dùng khóa debug; tạo khóa bằng app rồi thêm 4 secret, xem README).
- Chưa kiểm chứng trên máy thật: người dùng test.

## Đã thử rồi bỏ
(chưa có)
