# Download Manager (Android)

Aplikasi download manager + remote control untuk **Android 5.0 (API 21) ke atas**, dibangun dengan Kotlin + Jetpack.

## Fitur

- Tambah download via URL (nama file opsional, checksum MD5/SHA1/SHA256 opsional)
- **Info file otomatis sebelum download mulai**: HEAD ke server → nama asli, ukuran & tipe file ditampilkan di dialog tambah; peringatan merah bila ukuran file melebihi penyimpanan tersisa
- Progress per file (persentase, ukuran, kecepatan KB/s–MB/s, ETA) + grafik kecepatan real-time
- **Kecepatan & ETA stabil** (rata-rata bergerak — tidak melompat-lompat saat kecepatan sesaat naik/turun)
- Jeda, lanjutkan (resume dengan HTTP Range), batalkan, hapus, ulangi gagal
- Unduhan paralel dengan antrean (jumlah maks. bisa diatur), **multi-segmen** untuk file besar yang mendukung Range
- **Antrean pintar**: unduh file kecil lebih dulu (opsional, di pengaturan) selain prioritas manual
- **Batas kecepatan & prioritas per-download** (dialog tambah / long-press item)
- Daftar download tersimpan otomatis (bertahan setelah app ditutup), filter & urutkan daftar
- Foreground service: tetap berjalan saat app ditutup; retry otomatis (jeda bertahap); **auto-start saat boot**
- **Lanjut otomatis saat koneksi pulih**: download yang terputus karena jaringan hilang dijeda otomatis, lalu dilanjutkan sendiri begitu internet kembali (Android 5–6 via broadcast, Android 7+ via NetworkCallback)
- Notifikasi foreground progress + **widget homescreen**
- **Share URL** langsung dari aplikasi lain (Share → Download Manager)
- Auth HTTP Basic + custom header (Referer, Cookie, dll.), tempel banyak URL sekaligus, riwayat URL
- Buka file selesai dengan aplikasi lain, buka folder, ubah nama / pindahkan file (long-press)
- Nama file otomatis mengikuti server (Content-Disposition/Content-Type) atau pola "unduhan_tanggal_waktu"
- **Nama duplikat di folder tujuan otomatis diganti** saat download selesai: `nama (1).ext`, `nama (2).ext`, dst. (berlaku untuk semua lokasi simpan: Downloads, folder kustom, MediaStore, penyimpanan internal)
- Pindahkan otomatis ke subfolder Video/Foto setelah selesai (pengaturan)
- Splash screen modern + adaptive icon (fallback PNG untuk Android 5–7)
- **Tema ikuti sistem** (otomatis / terang / gelap), bahasa Indonesia/Inggris (ikut sistem)

## Remote (HTTP) & Halaman Web

Server remote bawaan untuk mengontrol dari browser di perangkat lain dalam satu jaringan Wi-Fi, berjalan **penuh di latar belakang** (foreground service) dan bisa **auto-start saat boot** — diatur di menu **⋮ → Pengaturan**.

1. Buka **⋮ → Remote (HTTP)** lalu **Mulai server** (atau biarkan menyala otomatis)
2. Scan **QR code** di dialog tersebut, atau ketik alamat `http://<ip-ponsel>:<port>/` di browser perangkat lain
3. Masukkan **PIN** jika sudah diatur di halaman login

Fitur halaman remote:

- **Tampilan mobile**: tombol FAB "+" untuk tambah download, menu aksi item ala bottom-sheet (anti salah pencet), topbar lebih ringkas
- Manajemen download: tambah URL, pantau progress, jeda/lanjut/batalkan/hapus, filter & urutkan
- **Item aktif otomatis di urutan atas** + **total kecepatan live di bar atas** (jumlah download aktif)
- Di layar HP, tombol aksi item dipadatkan ke menu **"⋯"**; hapus item & bersihkan daftar kini memakai dialog konfirmasi
- **Filter Semua/Aktif/Selesai/Gagal menempel (sticky)** saat scroll, **skeleton loading** saat memuat data, dan **empty state** yang lebih informatif di daftar, galeri, dan file manager
- **Indikator koneksi**: "diperbarui X dtk lalu" + tombol refresh di status card; titik status berkedip merah saat koneksi ke server putus
- **Animasi halus** saat pindah tab & item baru muncul, **favicon + theme-color** di browser
- **Upload file ke device** dari browser: banyak file sekaligus, dipecah per potongan 2 MB dengan retry otomatis (koneksi putus tidak mulai dari nol), upload folder utuh + drag & drop, nama duplikat otomatis (`nama (1).ext`), konfirmasi sebelum halaman ditutup saat upload berjalan
- **File manager remote**: jelajah folder, buat folder, rename, pindah, hapus (massal), download folder sebagai ZIP
  - Folder menampilkan info isinya: jumlah item + total ukuran
  - **Breadcrumb tap-able** untuk naik level cepat, **menu aksi "⋯"** (bottom-sheet di HP), dialog custom untuk rename/pindah/hapus, **long-press** untuk pilih massal, dan **ketuk file media langsung pratinjau**
- **Galeri remote ala YouTube**: thumbnail 16:9, badge **durasi video asli** (di-cache di device), load bertahap saat scroll
- **Player video ala YouTube**: seekbar merah + buffered, waktu, mute, fullscreen otomatis landscape, tap untuk pause/resume, double-tap ±10 detik, gesture geser untuk kecerahan/volume, lanjut dari posisi terakhir, dan **saran video** lain di bawah player
- **Streaming** (putar/pratinjau) atau **download** file yang sudah selesai — dukungan HTTP Range untuk video/audio
- Status baterai & penyimpanan device, pilihan port server, server background + auto-start
- **Auto-lock**: halaman remote meminta PIN lagi setelah 10 menit tanpa aktivitas

## Penyimpanan

Default file disimpan ke **Folder Downloads**. Izin penyimpanan mengikuti pola aplikasi Vaultwarden Host: `WRITE_EXTERNAL_STORAGE` penuh + `requestLegacyExternalStorage` + target SDK 28, jadi folder Downloads publik bisa diakses langsung di Android 5–11. Di Android 12+ tetap memakai MediaStore/SAF otomatis.

Selain picker sistem (SAF), tersedia **folder teks**: ketik path mentah seperti `/storage/emulated/0/<folder>` (pola Vaultwarden Host). Folder otomatis dibuat kalau belum ada, jadi path folder buatan Total Commander dll. bisa langsung dipakai di Android 5–11.

Untuk memilih lokasi lain (misalnya folder di SD card):

1. Ketuk menu **⋮ → Penyimpanan**
2. Pilih **Pilih folder…** dan tentukan folder tujuan di sistem (atau isi folder teks)
3. Pilihan tersimpan otomatis; setiap download berikutnya masuk ke folder itu
4. Untuk kembali ke default, buka **Penyimpanan** lagi lalu tekan **Pakai default**

Izin akses folder bersifat persisten (bertahan setelah aplikasi ditutup/di-restart).

## Pengaturan

Menu **⋮ → Pengaturan**:

- Lanjutkan download yang terputus otomatis (latar belakang)
- Mulai otomatis saat perangkat boot
- Unduhan bersamaan (1–5) — sisanya antre; jumlah segmen multi-download
- Batas kecepatan (Tanpa batas / 128 KB/s … 5 MB/s)
- Percobaan ulang saat gagal (0–5)
- Unduh file kecil lebih dulu (antrean pintar)
- Jalankan server remote di latar belakang + auto start server saat boot

Catatan: beberapa vendor (MIUI, dll.) punya pembatasan baterai ketat — aktifkan *auto-start* di pengaturan sistem agar service tidak dimatikan. Download yang Anda jeda manual tidak dilanjutkan otomatis.

## Widget & Pintasan

- **Widget homescreen**: tambahkan widget "Download Manager" untuk melihat download aktif + progress; ketuk untuk membuka aplikasi
- **Share dari aplikasi lain**: pilih "Share" pada link lalu pilih **Download Manager** — URL langsung terbuka di dialog tambah

## Unduh APK

APK terbaru selalu tersedia di **GitHub Releases** (tidak perlu buka menu Actions):

- https://github.com/tasirin1/httpdownloadmanager-/releases/latest

Setiap push ke `main` langsung di-build otomatis dan rilis diperbarui. Pasang APK di HP (Android 5.0+), beri izin Penyimpanan saat diminta.

## Persyaratan

- Android 5.0+ (minSdk 21), target SDK 28
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
app/src/main/java/com/tasirin/httpdownloadmanager/
├── MainActivity.kt          # UI utama + dialog tambah URL + dialog About
├── GalleryActivity.kt       # Galeri perangkat (foto/video lokal)
├── App.kt                   # Application (inisialisasi engine)
├── data/
│   ├── DownloadItem.kt      # Model + state download
│   └── DownloadRepository.kt# Persistensi daftar (SharedPreferences)
├── download/
│   ├── DownloadEngine.kt    # Logika unduh, resume (Range), multi-segmen, jeda, batal
│   └── DownloadService.kt   # Foreground service + notifikasi
├── receiver/BootReceiver.kt # Mulai otomatis saat boot (download & server)
├── remote/HttpControlServer.kt # HTTP server remote (download, file manager, galeri, durasi video)
├── ui/DownloadAdapter.kt    # RecyclerView adapter
├── widget/DownloadWidgetProvider.kt # Widget homescreen
└── util/
    ├── FileSaver.kt         # Simpan file (MediaStore / folder Downloads)
    ├── MediaLibrary.kt      # Scan media device + thumbnail
    ├── MimeTypes.kt         # Deteksi MIME
    ├── StoragePrefs.kt      # Preferensi penyimpanan
    └── NotificationHelper.kt# Notifikasi progress
```

## Lisensi

MIT — lihat [LICENSE](LICENSE).
