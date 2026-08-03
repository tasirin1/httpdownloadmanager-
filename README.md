# Download Manager (Android)

Aplikasi download manager sederhana untuk **Android 5.0 (API 21) ke atas**, dibangun dengan Kotlin + Jetpack.

## Fitur

- Tambah download via URL (nama file opsional)
- Progress per file (persentase + ukuran terunduh/total)
- Jeda, lanjutkan (resume dengan HTTP Range), batalkan, hapus
- Notifikasi foreground dengan progress agregat
- Daftar download tersimpan otomatis (bertahan setelah app ditutup)
- Buka file selesai dengan aplikasi lain
- Nama file otomatis menyesuaikan server (header Content-Disposition/Content-Type) jika tidak diisi manual
- Remote control dari browser di perangkat lain via HTTP server bawaan (LAN)
- Pilihan lokasi penyimpanan: Folder Downloads (default) atau folder kustom (internal/SD card via Storage Access Framework)
- File tersimpan di folder Downloads publik (Android < 10) / MediaStore Downloads (Android 10+) jika folder kustom tidak dipilih
- Tombol "Bersihkan yang selesai" dan "Penyimpanan" di menu toolbar

## Persyaratan

- Android 5.0+ (minSdk 21), target SDK 34
- Java 17 dan Android SDK untuk build lokal

## Penyimpanan

Default file disimpan ke **Folder Downloads** (MediaStore di Android 10+, folder Downloads publik di bawahnya).
Untuk memilih lokasi lain (misalnya folder di SD card):

1. Ketuk menu **⋮ → Penyimpanan**
2. Pilih **Pilih folder…** dan tentukan folder tujuan di sistem
3. Pilihan tersimpan otomatis; setiap download berikutnya masuk ke folder itu
4. Untuk kembali ke default, buka **Penyimpanan** lagi lalu tekan **Pakai default**

Izin akses folder bersifat persisten (bertahan setelah aplikasi ditutup/di-restart).

## Remote dari Browser (HTTP Server)

Aplikasi punya HTTP server bawaan untuk kontrol dari perangkat lain dalam satu jaringan Wi-Fi:

1. Pastikan HP dan perangkat lain terhubung ke Wi-Fi yang sama
2. Di aplikasi, buka menu **⋮ → Remote (HTTP)** → **Mulai server**
3. Catat alamat yang ditampilkan (misalnya `http://192.168.1.5:8080/`)
4. Buka alamat tersebut di browser perangkat lain — halaman kontrol muncul

Halaman remote mendukung: tambah URL download, pantau progress, jeda/lanjut/batalkan/hapus.
Server berjalan selama aplikasi aktif dan berhenti manual lewat menu yang sama.

## Build Lokal

```bash
./gradlew assembleDebug
# Hasil: app/build/outputs/apk/debug/app-debug.apk
```

Build release (perlu keystore, lihat bagian signing):

```bash
./gradlew assembleRelease \
  -PstoreFile=keystore.jks \
  -PstorePassword=password \
  -PkeyAlias=alias \
  -PkeyPassword=password
```

## Build Otomatis di GitHub Actions

Workflow `.github/workflows/build.yml` berjalan otomatis setiap push ke `main` (atau PR / manual via **Actions → Build APK → Run workflow**).

1. Buka tab **Actions** di repositori ini
2. Pilih workflow **Build APK**
3. Setelah selesai, buka run tersebut → bagian **Artifacts** → unduh `app-debug.apk` (dan `app-release.apk` jika signing diatur)

### Signing release (opsional)

Agar workflow menghasilkan APK release yang ditandatangani, tambahkan **repository secrets** di `Settings → Secrets and variables → Actions`:

| Secret | Isi |
|---|---|
| `KEYSTORE_BASE64` | `base64` dari file `keystore.jks` |
| `KEYSTORE_PASSWORD` | Password keystore |
| `KEY_ALIAS` | Alias kunci |
| `KEY_PASSWORD` | Password kunci |

Buat keystore lalu encode:

```bash
keytool -genkeypair -v -keystore keystore.jks -alias release \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 keystore.jks > keystore.b64   # isi ke secret KEYSTORE_BASE64
```

Jika secret tidak diisi, workflow tetap menghasilkan `app-debug.apk`.

## Struktur Project

```
app/src/main/java/com/tasirin/httpdownloadmanager/
├── MainActivity.kt          # UI utama + dialog tambah URL
├── App.kt                   # Application (inisialisasi engine)
├── data/
│   ├── DownloadItem.kt      # Model + state download
│   └── DownloadRepository.kt# Persistensi daftar (SharedPreferences)
├── download/
│   ├── DownloadEngine.kt    # Logika unduh, resume (Range), jeda, batal
│   └── DownloadService.kt   # Foreground service + notifikasi
├── remote/HttpControlServer.kt # HTTP server untuk remote via browser
├── ui/DownloadAdapter.kt    # RecyclerView adapter
└── util/
    ├── FileSaver.kt         # Simpan file (MediaStore / folder Downloads)
    ├── MimeTypes.kt         # Deteksi MIME
    └── NotificationHelper.kt# Notifikasi progress
```

## Lisensi

MIT — lihat [LICENSE](LICENSE).
