<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Tasirin Download Manager" width="96"><br>
  <b>Tasirin Download Manager — Android</b><br>
  Download manager + remote control web lengkap, nyaman dipakai di TV box & HP.
</p>

# Tasirin Download Manager (Android)

**Satu aplikasi untuk semua kebutuhan file di perangkat Android:** unduh cepat, kelola dari browser lewat jaringan Wi-Fi, jelajah file, mainkan galeri ala YouTube, dan pantau semuanya secara realtime — cocok dipakai di HP maupun TV box (Android 5.0+ / API 21+).

Dibangun dengan **Kotlin + Jetpack**, tanpa iklan, tanpa akun. Kode terbuka di GitHub dan setiap pembaruan otomatis di-build menjadi APK siap pasang.

## ✨ Kenapa aplikasi ini?

| | |
|---|---|
| 🚀 **Manajer unduhan lengkap** | multi-segmen, resume dengan Range, antrean pintar, batas kecepatan, auto-retry |
| 📡 **Remote web realtime (SSE)** | kontrol dari browser perangkat lain, update langsung tanpa refresh manual |
| 🖥️ **Player video ala YouTube** | seekbar merah, double-tap ±10 detik, gesture volume/kecerahan, auto-next |
| 🗂️ **File manager remote** | jelajah, upload, hapus massal, ZIP folder, pratinjau media langsung |
| 🖼️ **Galeri media device** | thumbnail 16:9, durasi asli, filter foto/video, folder foto & video terpisah |
| 📶 **Siap untuk jaringan pelan** | timeout connect/read bisa diatur, mirror otomatis, polling adaptif |
| 🔋 **Siap untuk TV box** | dukungan D-pad/remote, server jalan di background, auto-start saat boot |

## 🚀 Unduh

APK terbaru selalu tersedia di **GitHub Releases** — setiap push ke `main` langsung di-build dan rilis diperbarui otomatis:

**[⬇️ Unduh APK terbaru](https://github.com/tasirin1/tasirin-download-manager/releases/latest)**

APK release sudah **ditandatangani dengan kunci rilis resmi** (bukan debug), jadi lebih dipercaya Android/Play Protect. Pasang di HP / TV box (Android 5.0+), beri izin Penyimpanan saat diminta, selesai.

---

## 📥 Fitur Download

- Tambah via URL dengan **info file otomatis sebelum mulai**: nama asli, ukuran & tipe dari server; peringatan merah bila ukuran melebihi penyimpanan tersisa
- **Progress realtime**: persentase, ukuran, kecepatan KB/s–MB/s, **ETA stabil** (rata-rata bergerak, tidak melompat-lompat) + grafik kecepatan
- **Multi-segmen** untuk file besar yang mendukung Range, unduhan paralel dengan antrean (jumlah maks. bisa diatur)
- **Antrean pintar**: file kecil didahulukan (opsional) + prioritas manual per download
- Jeda, lanjutkan (resume HTTP Range), batalkan, hapus, ulangi gagal — semua tersimpan otomatis
- **Foreground service**: download lanjut walau app ditutup, retry otomatis dengan jeda bertahap, **auto-start saat boot**
- **Lanjut otomatis saat koneksi pulih**: download yang terputus karena jaringan hilang dilanjutkan sendiri (Android 5–6 via broadcast, 7+ via NetworkCallback)
- **Batas kecepatan & prioritas per-download**, auth HTTP Basic + header custom (Referer, Cookie, dll.)
- Tempel banyak URL sekaligus, riwayat URL, **Share dari aplikasi lain** langsung ke dialog tambah
- Checksum opsional (MD5/SHA1/SHA256), nama duplikat otomatis `nama (1).ext`
- **HLS / m3u8**: deteksi otomatis dan pilih kualitas (variant) sebelum unduh
- **Pantau pembaruan**: item download bisa dipantau berkala — periksa versi baru di URL yang sama dan unduh otomatis
- **Auto-sort lengkap** setelah selesai: `Videos/`, `Photos/`, `Music/`, `Documents/`, `APK/` (pengaturan)
- **Fallback cerdas saat Range ditolak**: server yang tidak mendukung Range otomatis diunduh sekali jalan (single-stream), tanpa gagal total
- **Mirror otomatis** untuk server yang lambat/gagal, URL gagal di-blacklist agar tidak dicoba ulang tanpa henti
- Tema ikuti sistem (otomatis / terang / gelap), bahasa Indonesia/Inggris (ikut sistem)

## 📡 Remote Web Realtime

Server remote bawaan berjalan **penuh di latar belakang** dan bisa **auto-start saat boot** — semua diatur di **⋮ → Pengaturan**.

1. Di Pengaturan, mulai server (atau biarkan menyala otomatis saat boot)
2. Scan **QR code**, atau buka `http://<ip-device>:<port>/` di browser perangkat lain
3. Masukkan **PIN** jika diatur

Fitur halaman remote:

- **Realtime via SSE**: progress & status datang langsung dari device tanpa refresh manual; fallback polling otomatis bila jaringan memblokir streaming
- **Polling adaptif**: 2 detik saat ada aktivitas, 10 detik saat idle — hemat baterai
- **Item aktif otomatis di urutan atas** + total kecepatan live di bar atas
- **Tampilan mobile**: FAB "+", menu aksi ala bottom-sheet, filter sticky, skeleton loading, empty state informatif
- **Indikator koneksi**: "diperbarui X dtk lalu", titik status berkedip merah saat koneksi putus, refresh otomatis saat tab kembali fokus
- **Upload file & folder** dari browser: chunk 2 MB dengan retry (putus tidak mulai dari nol), drag & drop, nama duplikat otomatis, konfirmasi sebelum tab ditutup
- **File manager remote**: jelajah, buat folder, rename, pindah, hapus massal, **download folder sebagai ZIP**, breadcrumb, pratinjau media langsung
- **Galeri remote ala YouTube**: thumbnail 16:9, badge durasi asli (cache di device), load bertahap, **filter Semua/Foto/Video**
- **Player video ala YouTube**: seekbar merah + buffered, double-tap ±10 detik, gesture kecerahan/volume, kecepatan putar 0.5×–2×, lanjut dari posisi terakhir, **saran video** di bawah player, **AUTO (auto-next)** menyala otomatis
- **Bagikan file via tautan sementara** (berlaku 24 jam, tanpa PIN) + QR code
- **Streaming** file selesai (HTTP Range untuk video/audio) atau download langsung
- Status baterai & penyimpanan, pilihan port, server background + auto-start
- **Auto-lock**: halaman remote minta PIN lagi setelah 10 menit tanpa aktivitas

## 🗂️ Penyimpanan

Default file disimpan ke **Folder Downloads**. `minSdk 21` dipertahankan (Android 5+ tetap didukung), `targetSdk 34`. Android 5–10 memakai `WRITE_EXTERNAL_STORAGE` + legacy storage (akses penuh); Android 11+ memakai `MANAGE_EXTERNAL_STORAGE` ("Akses semua file").

- **Input path teks**: ketik path mentah seperti `/storage/emulated/0/Download` — folder otomatis dibuat kalau belum ada
- **Folder tambahan (mount)**: ketuk **+** untuk menambah path lain (mis. `/sdcard/Movies`) agar ikut tampil di file manager — cocok untuk folder buatan Total Commander, folder SD card, dll.
- Pilihan tersimpan otomatis dan persisten (bertahan setelah restart)

## 🖼️ Galeri

- **Folder foto & video diatur terpisah** di Pengaturan: tentukan folder galeri foto dan folder galeri video masing-masing (mis. video saja di `/sdcard/Movies/Files`, foto dibiarkan scan semua)
- Kosongkan untuk scan seluruh storage; path `/sdcard/...` otomatis dikenali sebagai `/storage/emulated/0/...`
- Tampilkan durasi video, thumbnail cepat, putar langsung, hapus file

## ⚙️ Pengaturan

Menu **⋮ → Pengaturan**:

- **Unduhan**: resume otomatis, auto-start saat boot, unduhan bersamaan (1–5), jumlah segmen, batas kecepatan, percobaan ulang (0–5), unduh file kecil dulu, **timeout connect (5–60 dtk) & read (10–120 dtk)** untuk jaringan lambat/WISP
- **Server**: port, PIN, QR code, background + auto-start saat boot
- **Penyimpanan**: folder tujuan + folder tambahan
- **Galeri**: folder foto & video terpisah
- **Pembersihan**: hapus log/download yang sudah selesai
- **Log Server realtime**: log aktivitas seluruh sistem (request HTTP, download, galeri, kesalahan) bisa di-cari, di-sorot, dan **diekspor ke file TXT** — mudah untuk lapor bug

Catatan: beberapa vendor (MIUI, dll.) punya pembatasan baterai ketat — aktifkan *auto-start* di pengaturan sistem agar service tidak dimatikan.

## 🛠️ Build

### Lokal

```bash
./gradlew assembleDebug
# Hasil: app/build/outputs/apk/debug/app-debug.apk
```

### Otomatis (GitHub Actions)

Workflow `.github/workflows/build.yml` berjalan otomatis setiap push ke `main` (atau manual via **Actions → Build APK → Run workflow**). Hasilnya otomatis jadi release baru. Untuk release yang ditandatangani, isi secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` di pengaturan repo — lalu APK release (signed) yang otomatis diunggah ke release, bukan APK debug.

> **⚠️ Backup keystore & password-nya selamanya.** Kunci release menandatangani semua rilis — kalau hilang, perangkat tidak bisa update APK lama tanpa uninstall, dan ganti kunci baru bikin Play Protect curiga. Simpan aman (password manager), jangan pernah commit ke repo.

**Persyaratan**: Android 5.0+ (minSdk 21), Java 17 + Android SDK.

## 📁 Struktur Project

```
app/src/main/java/com/tasirin/httpdownloadmanager/
├── MainActivity.kt            # UI utama + dialog tambah URL + dialog About
├── GalleryActivity.kt         # Galeri perangkat (foto/video lokal)
├── SettingsActivity.kt        # Pengaturan lengkap (server, penyimpanan, galeri, log)
├── LogActivity.kt             # Log server realtime + ekspor TXT
├── App.kt                     # Application (inisialisasi engine)
├── data/
│   ├── DownloadItem.kt        # Model + state download
│   └── DownloadRepository.kt  # Persistensi daftar (SharedPreferences)
├── download/
│   ├── DownloadEngine.kt      # Unduh, resume (Range), multi-segmen, HLS probe, monitor, jeda/batal
│   └── DownloadService.kt     # Foreground service + notifikasi
├── receiver/BootReceiver.kt   # Auto-start saat boot (download & server)
├── remote/HttpControlServer.kt# Server HTTP remote (download, file manager, galeri, SSE)
├── ui/DownloadAdapter.kt      # RecyclerView adapter
├── widget/SpeedChartView.kt   # Grafik kecepatan realtime
└── util/
    ├── FileSaver.kt           # Simpan file (MediaStore / folder, auto-sort)
    ├── MediaLibrary.kt        # Scan media device + thumbnail
    ├── MimeTypes.kt           # Deteksi MIME
    ├── StoragePrefs.kt        # Preferensi penyimpanan + folder tambahan
    ├── NotificationHelper.kt  # Notifikasi progress
    ├── Crypto.kt / Formats.kt / FileNames.kt / TlsCompat.kt  # Pendukung
```

## 📄 Lisensi

MIT — lihat [LICENSE](LICENSE).
