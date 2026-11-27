# Signing Key Vault

**Tiếng Việt** | [English](#english)

Ứng dụng Android để tạo và quản lý khóa ký (keystore) ngay trên điện thoại, chuyển sang base64 để dán vào GitHub Secrets, và lưu thêm bí mật cho web/web app. Có sao lưu mã hóa và nhắc kiểm tra để không mất khóa.

Phiên bản hiện tại: **1.1.0** (giai đoạn cá nhân, chưa kiểm chứng trên nhiều máy).

## Cài đặt
1. Vào trang [Releases](https://github.com/trunghuynhvan0310/signing-key-vault/releases), tải `signing-key-vault-v<phiên bản>.apk`.
2. Mở tệp trên điện thoại và cho phép cài từ nguồn này. Yêu cầu Android 11 trở lên và đã đặt khóa màn hình.

> Nếu repo chưa có 4 secret ký, APK là bản **debug** (gói `...signingkeyvault.debug`, đánh dấu prerelease). Muốn có bản release ký bằng khóa riêng: dùng chính app này tạo keystore, dán 4 secret `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` vào repo, chạy lại workflow, rồi cài bản release (khác khóa nên phải gỡ bản debug; kho của bản debug là kho riêng).

## Cách dùng nhanh
1. Mở app, tạo kho (vân tay hoặc khóa màn hình), rồi bấm **Thiết lập sao lưu**: chọn thư mục, đặt mật khẩu sao lưu (ghi ra giấy).
2. Bấm **Tạo khóa ký**, nhập tên app (và repo `chủ/repo` nếu muốn mở nhanh trang Secrets).
3. Ở màn hình khóa, chép lần lượt 4 giá trị vào GitHub Secrets; mỗi giá trị đã chép được đánh dấu.
4. Mục Bí mật dùng cho token, khóa API, tệp cấu hình của web/web app.

## Bảo mật
- Dữ liệu mã hóa AES-GCM bằng khóa ngẫu nhiên; khóa này được bọc bởi khóa trong Android Keystore, chỉ dùng được trong 30 giây sau khi xác thực sinh trắc học hoặc khóa màn hình.
- Không có quyền Internet. Chặn chụp màn hình, tự khóa khi rời app, clipboard đánh dấu nhạy cảm và tự xóa sau 60 giây, tắt sao lưu đám mây của Android.
- Sao lưu `.vault`: Argon2id + AES-256-GCM, ghi luân phiên hai tệp A/B vào thư mục bạn chọn và đọc lại để kiểm tra. Mất mật khẩu sao lưu thì không khôi phục được.
- Keystore được sinh trên máy bằng Bouncy Castle (RSA 2048/4096, PKCS12, mật khẩu ngẫu nhiên).

## Giới hạn hiện tại
- Chưa đẩy secret lên GitHub trực tiếp (cần quyền Internet; dự kiến bản sau).
- Chưa hỗ trợ khóa SSH/VAPID.

## Build
GitHub Actions (`.github/workflows/build.yml`) chạy test, build APK và đăng lên Releases mỗi lần push `main`. Phiên bản nằm ở `version.properties`. Công nghệ: Kotlin, Jetpack Compose, AGP 9.1.1, Gradle 9.3.1, JDK 17.

## Giấy phép
[MIT](LICENSE)

---

## English

An Android app to create and manage signing keys (keystores) on your phone, convert them to base64 for GitHub Secrets, and keep secrets for websites and web apps. It has encrypted backups and reminders so you never lose a key again.

Current version: **1.1.0** (personal stage, not yet verified on many devices).

### Install
1. Download `signing-key-vault-v<version>.apk` from [Releases](https://github.com/trunghuynhvan0310/signing-key-vault/releases).
2. Open it on your phone and allow installing from this source. Requires Android 11+ and a screen lock.

> Until the repo has the 4 signing secrets, the APK is a **debug** build (package `...signingkeyvault.debug`, marked prerelease). To get a release build signed with your own key: create a keystore with this app, add `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` to the repo, re-run the workflow, and install the release build (different key, so uninstall the debug build; its vault is separate).

### Quick start
1. Open the app, create the vault (fingerprint or screen lock), then tap **Set up backup**: pick a folder and a backup passphrase (write it on paper).
2. Tap **Create signing key** and enter the app name (and `owner/repo` to open its Secrets page quickly).
3. On the key screen, copy the 4 values into GitHub Secrets; each copied value gets a check mark.
4. Use Secrets for tokens, API keys and config files of your web apps.

### Security
- Data is AES-GCM encrypted with a random key wrapped by an Android Keystore key that works only for 30 seconds after biometric/screen-lock authentication.
- No Internet permission. Screenshots blocked, auto-lock, sensitive clipboard flag with 60 s auto-clear, Android cloud backup disabled.
- `.vault` backups: Argon2id + AES-256-GCM, alternating A/B files in a folder you choose, read back to verify. A lost backup passphrase cannot be recovered.
- Keystores are generated on-device with Bouncy Castle (RSA 2048/4096, PKCS12, random password).

### Limitations
- No direct upload of secrets to GitHub yet (needs Internet; planned).
- No SSH/VAPID keys yet.

### Build
GitHub Actions runs tests, builds the APK and publishes it to Releases on every push to `main`. Version lives in `version.properties`.

### License
[MIT](LICENSE)
