# Download Manager (Android)

Aplikasi download manager sederhana untuk **Android 5.0 (API 21) ke atas**, dibangun dengan Kotlin + Jetpack.

## Fitur

- Tambah download via URL (nama file opsional)
- Progress per file (persentase + ukuran terunduh/total)
- Jeda, lanjutkan (resume dengan HTTP Range), batalkan, hapus
- Notifikasi foreground dengan progress agregat
- Daftar download tersimpan otomatis (bertahan setelah app ditutup)
- Buka file selesai dengan aplikasi lain
- File tersimpan di folder Downloads publik (Android < 10) / MediaStore Downloads (Android 10+)
- Tombol "Bersihkan yang selesai" di menu toolbar

## Persyaratan

- Android 5.0+ (minSdk 21), target SDK 34
- Java 17 dan Android SDK untuk build lokal

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
app/src/main/java/com/tasirin/downloadmanager/
├── MainActivity.kt          # UI utama + dialog tambah URL
├── App.kt                   # Application (inisialisasi engine)
├── data/
│   ├── DownloadItem.kt      # Model + state download
│   └── DownloadRepository.kt# Persistensi daftar (SharedPreferences)
├── download/
│   ├── DownloadEngine.kt    # Logika unduh, resume (Range), jeda, batal
│   └── DownloadService.kt   # Foreground service + notifikasi
├── ui/DownloadAdapter.kt    # RecyclerView adapter
└── util/
    ├── FileSaver.kt         # Simpan file (MediaStore / folder Downloads)
    ├── MimeTypes.kt         # Deteksi MIME
    └── NotificationHelper.kt# Notifikasi progress
```

## Lisensi

MIT — lihat [LICENSE](LICENSE).
