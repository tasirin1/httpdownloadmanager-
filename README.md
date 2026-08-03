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
- Remote control dari browser di perangkat lain via HTTP server bawaan (LAN), lengkap dengan **QR code** alamat server (menu Remote)
- File yang sudah selesai bisa **di-streaming atau di-download** langsung dari browser remote (dukungan HTTP Range untuk video/audio)
- **Splash screen** modern + **ikon baru** (adaptive icon, Android 8+; PNG fallback untuk Android 5–7)
- Berjalan di latar belakang (foreground service) + resume otomatis download yang terputus setelah restart/boot
- Unduhan paralel dengan antrean (maks. bersamaan bisa diatur)
- Kecepatan (KB/s/MB/s) + ETA per file, dan batas kecepatan global
- **Batas kecepatan & prioritas per-download** (dialog tambah download / long-press item yang antre/dijeda)
- Multi-koneksi (segmented download) untuk file besar yang mendukung Range
- Share URL langsung dari aplikasi lain (Share → Download Manager)
- Retry otomatis saat gagal (bisa diatur)
- Auth HTTP Basic + custom header (Referer, Cookie, dll.)
- Tempel banyak URL sekaligus + riwayat URL terakhir
- Ubah nama / pindahkan file selesai (long-press item)
- Verifikasi ukuran file vs Content-Length
- Tema gelap otomatis + bahasa Indonesia/Inggris (ikuti sistem)
- Widget homescreen status download
- Pilihan lokasi penyimpanan: Folder Downloads (default) atau folder kustom (internal/SD card via Storage Access Framework)
- File tersimpan di folder Downloads publik (Android < 10) / MediaStore Downloads (Android 10+) jika folder kustom tidak dipilih
- Tombol "Bersihkan yang selesai" dan "Penyimpanan" di menu toolbar

## Persyaratan

- Android 5.0+ (minSdk 21), target SDK 34
- Java 17 dan Android SDK untuk build lokal

## Penyimpanan

Default file disimpan ke **Folder Downloads** (MediaStore di Android 10+, folder Downloads publik di Android 5–9 — izin penyimpanan diminta otomatis saat membuka dialog Penyimpanan di Android 6+). Pemilih folder defaultnya langsung terbuka di folder Downloads (Android 8+), dan aman dipakai di Android 5/6 (tanpa force close saat memilih folder).
Untuk memilih lokasi lain (misalnya folder di SD card):

1. Ketuk menu **⋮ → Penyimpanan**
2. Pilih **Pilih folder…** dan tentukan folder tujuan di sistem
3. Pilihan tersimpan otomatis; setiap download berikutnya masuk ke folder itu
4. Untuk kembali ke default, buka **Penyimpanan** lagi lalu tekan **Pakai default**

Izin akses folder bersifat persisten (bertahan setelah aplikasi ditutup/di-restart).

## Widget & Pintasan

- **Widget homescreen**: tambahkan widget "Download Manager" (3 baris) untuk melihat download aktif + progress; ketuk widget untuk membuka aplikasi
- **Share dari aplikasi lain**: pilih "Share" pada link di browser/file manager lalu pilih **Download Manager** — URL langsung terbuka di dialog tambah

## Pengaturan Lengkap

Menu **⋮ → Pengaturan**:

- Lanjutkan download yang terputus otomatis (latar belakang)
- Mulai otomatis saat perangkat boot (bisa dimatikan/dinyalakan dari menu ⋮ → Auto Start saat boot)
- Unduhan bersamaan (1–5) — sisanya antre
- Batas kecepatan (Tanpa batas / 128 KB/s … 5 MB/s)
- Percobaan ulang saat gagal

## Remote (HTTP) & Streaming

Server remote bisa berjalan **penuh di latar belakang**: otomatis menyala saat boot dan tetap hidup (via foreground service) meski aplikasi ditutup. Atur lewat **⋮ → Pengaturan** → "Jalankan server remote di latar belakang" dan "Auto start server saat boot" (keduanya aktif secara default).

1. Buka **⋮ → Remote (HTTP)** lalu **Mulai server** (jika belum otomatis menyala)
2. Scan **QR code** di dialog tersebut dengan kamera ponsel lain, atau ketik alamat `http://<ip-ponsel>:8080/` di browser perangkat lain (harus satu jaringan Wi-Fi)
3. Dari halaman remote Anda bisa: menambah download (termasuk batas kecepatan & prioritas), menjeda/melanjutkan/membatalkan, serta **Stream** (putar/pratinjau) atau **Download** file yang sudah selesai
4. File besar didukung **HTTP Range** sehingga video/audio bisa di-seek saat streaming
- Percobaan ulang saat gagal (0–5)

## Latar Belakang & Auto Start

- Download berjalan di **foreground service**, tetap berjalan saat aplikasi ditutup / layar terkunci
- Jika proses dihentikan sistem, download yang sedang berjalan otomatis dilanjutkan saat aplikasi/perangkat aktif kembali
- **Mulai otomatis saat boot**: setelah perangkat dinyalakan, aplikasi langsung melanjutkan download yang tertunda
- Pengaturan di menu **⋮ → Pengaturan**: toggle *"Lanjutkan download yang terputus otomatis"* dan *"Mulai otomatis saat perangkat boot"*
- Download yang Anda jeda manual tidak akan dilanjutkan otomatis
- Catatan: beberapa vendor (MIUI, dll.) punya pembatasan baterai ketat — aktifkan *auto-start* di pengaturan sistem agar service tidak dimatikan

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
├── receiver/BootReceiver.kt  # Mulai otomatis saat boot
├── remote/HttpControlServer.kt # HTTP server untuk remote via browser
├── ui/DownloadAdapter.kt    # RecyclerView adapter
├── widget/DownloadWidgetProvider.kt # Widget homescreen
└── util/
    ├── FileSaver.kt         # Simpan file (MediaStore / folder Downloads)
    ├── MimeTypes.kt         # Deteksi MIME
    └── NotificationHelper.kt# Notifikasi progress
```

## Lisensi

MIT — lihat [LICENSE](LICENSE).
