# DRAFT — Alur REST API untuk Frontend (React)

> Hasil audit alur web **tiket_pesawat** (backend Spring Boot, output JSON murni).
> Frontend direncanakan memakai **React** dan mengonsumsi REST API langsung.
> Thymeleaf sudah dihapus. Seluruh endpoint di bawah sudah ada di backend
> (`src/main/java`). Status: **DRAFT v2** — diperbarui dengan contoh request/
> response, validasi, dan pengaturan lingkungan.

---

## 1. Ringkasan

| Hal | Nilai |
|---|---|
| Base URL (dev) | `http://localhost:8080` |
| Autentikasi | JWT Bearer (`Authorization: Bearer <aksesToken>`) |
| Sesi | Stateless (tanpa cookie/session server) |
| Role | `USER` dan `ADMIN` (disimpan di claim `role` pada JWT) |
| Masa berlaku access token | 1 jam (default `JWT_EXPIRATION_MS`) |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| Format respons | `ResponseApi` (lihat bagian 2) |
| Upload KTP | disimpan server di `uploads/ktp/` |
| Format kode booking | `ASTRA-XXXX` (8 karakter acak, contoh `ASTRA-3F2B9C1A`) |

**Penting untuk FE:** hampir semua endpoint `/user/**` dan `/admin/**` butuh
header `Authorization: Bearer <aksesToken>`. React wajib menyimpan token hasil
login (localStorage/sessionStorage) lalu mengirimnya di setiap request yang
terproteksi. Endpoint `/auth/**` dan `/payment/notification` bersifat publik.

---

## 2. Format Respons Standar

Semua endpoint (kecuali unduhan PDF/Excel) membungkus respons dalam `ResponseApi`:

```json
{
  "sukses": true,
  "pesanNya": "Berhasil!",
  "data": { "...": "..." },
  "statusKode": 200,
  "stempelWaktu": "2026-09-02T10:00:00"
}
```

- `sukses=false` pada error bisnis. Status HTTP tetap sesuai kondisi
  (200, 400, 401, 403, 404, 409, 500, dst).
- `data` bisa berupa object, array, halaman Spring pagination, atau `null`.
  Halaman Spring (`Penerbangan`) berbentuk:

```json
{
  "content": [ { "...": "..." } ],
  "pageable": { "...": "..." },
  "totalElements": 12,
  "totalPages": 2,
  "number": 0,
  "size": 10,
  "numberOfElements": 10,
  "first": true,
  "last": false,
  "empty": false
}
```

- Pengecualian — respons TIDAK terbungkus `ResponseApi` (hanya unduhan file):
  - `GET /user/{tiketId}/download-tiket-PDF` -> binary `application/pdf`
  - `GET /admin/{id}/ekspor-history-update-ke-excell` -> binary `.xlsx`

  (Catatan: `POST /user/pemesanan` dan `GET /user/{kodeBooking}/verifikasi-pembayaran`
  sudah dibungkus `ResponseApi`; `data` berisi object responnya.)

### Error di luar ResponseApi (perlu normalisasi di FE)

Saat token salah/expired, `JwtFilter` mengembalikan JSON bentuk berbeda:

```json
{ "status": 401, "pesan": "Token tidak ditemukan. Silakan login terlebih dahulu.", "timestamp": "2026-09-02T10:00:00.123" }
```

Pesan yang mungkin keluar dari `JwtFilter`:
- `Token tidak ditemukan. Silakan login terlebih dahulu.` (header kosong / bukan `Bearer`)
- `Token sudah expired. Silakan login ulang.`
- `Token tidak valid. Silakan periksa kembali token Anda.`

Saran FE: buat satu fungsi `apiFetch()` yang menormalkan error — jika JSON punya
`sukses`, baca `pesanNya`; jika bentuk `{status, pesan}`, baca `pesan`. Lihat
bagian 8 untuk pola lengkap (termasuk auto-refresh).

---

## 3. Alur Lengkap — Sisi User

### 3.1 Flow utama (booking s.d. e-tiket)

```
[1] Registrasi -> [2] Login (dapat JWT) -> [3] Cari penerbangan
  -> [4] Booking (upload KTP) -> [5] Bayar Midtrans (Snap)
  -> [6] Webhook Midtrans -> [7] Pilih kursi -> [8] Unduh e-tiket PDF
```

| # | Aksi | Method & Path | Auth | Body / Params |
|---|---|---|---|---|
| 1 | Registrasi | `POST /auth/daftar` | - | `{email, nama, password, role: null}` |
| 2 | Login | `POST /auth/login` | - | `{email, password}` |
| 3 | Cari penerbangan | `GET /user/melihat-penerbangan-tersedia` | JWT | query: `dari`, `ke`, `tanggal` (ISO `yyyy-MM-dd`), `maskapai` (semua opsional) |
| 4 | Booking | `POST /user/pemesanan` | JWT | `multipart/form-data`: `penerbanganId`, `nama`, `noHP`, `fileKtp` |
| 5 | Bayar | buka `redirectUrl` (Snap) atau embed `snapToken` | - | lihat bagian 3.4 |
| 6 | Cek status bayar | `GET /user/{kodeBooking}/detail` | JWT | polling hingga `SUDAH_DIBAYAR` |
| 7 | Ambil peta kursi | `GET /user/melihat-peta-kursi?kodeBooking=` | JWT | - |
| 8 | Pilih kursi | `POST /user/pemilihan-nomor-kursi` | JWT | query: `kodeBooking`, `kursiId` |
| 9 | Unduh e-tiket | `GET /user/{tiketId}/download-tiket-PDF` | JWT | - |

Batasan yang wajib diakomodasi FE (diteruskan dari pesan error backend):
- Penerbangan `DEPARTED` / `ARRIVED` -> tidak bisa dibooking.
- Semua kursi terisi -> `TIDAK_TERSEDIA`, tidak bisa dibooking.
- Pembayaran harus selesai maksimal 15 menit (`batasWaktuPembayaran`); lewat batas
  booking dibatalkan otomatis oleh scheduler.
- Pilih kursi hanya bisa setelah status `SUDAH_DIBAYAR`.
- Booking yang sudah dibayar tidak bisa dibatalkan.
- Satu booking hanya boleh untuk 1 kursi (buy 1 ticket per booking).

### 3.2 Contoh request/response endpoint kunci

#### a. Registrasi — `POST /auth/daftar`

Request:
```json
{ "email": "user@mail.com", "nama": "Budi", "password": "Abc12345", "role": null }
```

Response `201 Created`:
```json
{
  "sukses": true,
  "pesanNya": "Sip!, Data anda sudah berhasil Daftar",
  "data": { "email": "user@mail.com", "nama": "Budi", "role": "USER" },
  "statusKode": 201,
  "stempelWaktu": "2026-09-02T10:00:00"
}
```

Error umum: `409 Conflict` jika email sudah terpakai; backend menolak role `ADMIN`.

#### b. Login — `POST /auth/login`

Request:
```json
{ "email": "user@mail.com", "password": "Abc12345" }
```

Response `200 OK` (perhatikan: token juga dikirim ke email oleh backend):
```json
{
  "sukses": true,
  "pesanNya": "Berhasil!",
  "data": {
    "aksesToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "2f3a9c...",
    "role": "USER",
    "email": "user@mail.com"
  },
  "statusKode": 200
}
```

Error umum: `401 Unauthorized` (email/password salah), `Email Tidak Ditemukan atau salah`.

#### c. Cari penerbangan — `GET /user/melihat-penerbangan-tersedia`

Query opsional: `dari`, `ke`, `tanggal` (`2026-09-10`), `maskapai`.

Response `200 OK`:
```json
{
  "sukses": true,
  "pesanNya": "Data Penerbangan Yang Tersedia",
  "data": [
    {
      "id": 1,
      "maskapai": "Garuda Indonesia",
      "kotaKeberangkatan": "Jakarta",
      "kotaTujuan": "Denpasar",
      "waktuKeberangkatan": "2026-09-10T08:30:00",
      "waktuKedatangan": "2026-09-10T11:20:00",
      "hargaTiket": 1250000.00
    }
  ],
  "statusKode": 200
}
```

#### d. Booking — `POST /user/pemesanan`

`multipart/form-data` (perhatikan: bukan JSON!) dengan field:

| Field | Tipe | Aturan |
|---|---|---|
| `penerbanganId` | number | ID penerbangan dari hasil pencarian |
| `nama` | string | 2-25 karakter, wajib |
| `noHP` | string | diawali `08`, 10-15 digit |
| `fileKtp` | file | gambar JPG/PNG atau PDF, maks 2MB |

Response `200 OK` (data hasil pembayaran ada di dalam `ResponseApi.data`):
```json
{
  "sukses": true,
  "pesanNya": "Pesanan Berhasil Disimpan, Silahkan Melakukan Pembayaran",
  "data": {
    "kodeBooking": "ASTRA-3F2B9C1A",
    "snapToken": "d9a2f1...",
    "redirectUrl": "https://app.sandbox.midtrans.com/snap/v2/vt-...",
    "totalHarga": "1250000"
  },
  "statusKode": 200
}
```

Error umum: `400 Bad Request` (format KTP salah, ukuran > 2MB, kursi habis,
penerbangan sudah berangkat/selesai, KTP wajib di-upload), `401` (token tidak ada).

#### e. Pilih kursi — `POST /user/pemilihan-nomor-kursi`

Query: `kodeBooking=ASTRA-3F2B9C1A&kursiId=12`

Response `200 OK`:
```json
{ "sukses": true, "pesanNya": "Tiket Berhasil Dibuat Dengan Nomor kursi + A2", "data": null, "statusKode": 200 }
```

#### f. Detail booking — `GET /user/{kodeBooking}/detail`

Response `200 OK`:
```json
{
  "sukses": true,
  "pesanNya": "Detail Booking",
  "data": {
    "kodeBooking": "ASTRA-3F2B9C1A",
    "namaPenumpang": "Budi",
    "noHP": "081234567890",
    "maskapai": "Garuda Indonesia",
    "dari": "Jakarta",
    "ke": "Denpasar",
    "waktuKeberangkatan": "2026-09-10T08:30:00",
    "waktuKedatangan": "2026-09-10T11:20:00",
    "totalHarga": 1250000.00,
    "statusPembayaran": "BELUM_DIBAYAR",
    "batasWaktuPembayaran": "2026-09-02T10:15:00",
    "nomorKursi": null
  },
  "statusKode": 200
}
```

#### g. Peta kursi — `GET /user/melihat-peta-kursi?kodeBooking=...`

Data berupa array `{id, nomorKursi, tersedia}` (sudah terurut baris -> kolom):
```json
{
  "data": [
    { "id": 1, "nomorKursi": "A1", "tersedia": true },
    { "id": 2, "nomorKursi": "A2", "tersedia": false }
  ]
}
```

#### h. Riwayat pemesanan — `GET /user/riwayat-pemesanan`

Data berupa array: `kodeBooking`, `maskapai`, `kotaKeberangkatan`, `kotaTujuan`,
`waktuKeberangkatan`, `totalHarga`, `statusPembayaran`, `nomorKursi` (null jika
belum pilih kursi).

#### i. Daftar kota & maskapai (untuk dropdown)

- `GET /user/daftar-kota` -> `data` adalah array string kota (gabungan unik
  keberangkatan + tujuan, terurut):
```json
{ "data": ["Bali", "Banjarmasin", "Jakarta", "Makassar", "Yogyakarta"] }
```
- `GET /user/daftar-maskapai` -> `data` adalah array string maskapai unik terurut:
```json
{ "data": ["Batik Air", "Garuda Indonesia", "Lion Air"] }
```

### 3.3 Daftar endpoint lengkap — User

| Method | Path | Auth | Keterangan |
|---|---|---|---|
| POST | `/auth/daftar` | - | Daftar akun (role selalu dipaksa `USER`) |
| POST | `/auth/login` | - | Login -> `{aksesToken, refreshToken, role, email}` |
| POST | `/auth/refresh-token` | - | Body `{refreshToken}` -> `{aksesToken, refreshToken}` |
| POST | `/auth/logout` | - | Body `{refreshToken}` -> cabut refresh token |
| POST | `/auth/request-lupa-password?email=...` | - | Kirim OTP 6 digit ke email |
| POST | `/auth/verifikasi-otp?email=...&otp=...` | - | Validasi OTP (berlaku 5 menit) |
| POST | `/auth/reset?email=...&passwordBaru=...` | - | Set password baru (OTP wajib sudah DIVERIFIKASI) |
| GET | `/user/melihat-penerbangan-tersedia` | JWT | Cari penerbangan (filter opsional) |
| GET | `/user/daftar-kota` | JWT | Daftar kota unik (berangkat + tujuan) untuk dropdown |
| GET | `/user/daftar-maskapai` | JWT | Daftar maskapai unik untuk dropdown |
| POST | `/user/pemesanan` | JWT | Booking (multipart + KTP) |
| GET | `/user/{kodeBooking}/verifikasi-pembayaran` | JWT | Ambil link verifikasi pembayaran (hanya pemilik booking) |
| POST | `/user/pemilihan-nomor-kursi` | JWT | Pilih kursi |
| GET | `/user/{tiketId}/download-tiket-PDF` | JWT | Unduh boarding pass (hanya pemilik) |
| GET | `/user/melihat-kursi-tersedia?kodeBooking=` | JWT | Kursi yang masih kosong |
| GET | `/user/melihat-peta-kursi?kodeBooking=` | JWT | Peta kursi (pasca-booking) |
| GET | `/user/melihat-peta-kursi-penerbangan?penerbanganId=` | JWT | Peta kursi sebelum checkout |
| GET | `/user/profile` | JWT | `{id, email, nama, role}` |
| PUT | `/user/profile-update` | JWT | Body `{nama}` |
| PUT | `/user/change-password` | JWT | Body `{passwordLama, passwordBaru}` |
| GET | `/user/riwayat-pemesanan` | JWT | Riwayat booking user |
| GET | `/user/{kodeBooking}/detail` | JWT | Detail booking (untuk polling status) |
| POST | `/user/{kodeBooking}/batalkan` | JWT | Batalkan booking (hanya jika belum dibayar) |
| GET | `/user/{kodeBooking}/halaman-verifikasi-pembayaran` | JWT | JSON status pembayaran (hanya pemilik booking) |
| ~~POST~~ | ~~`/user/{kodeBooking}/konfirmasi-pembayaran`~~ | - | **Dihapus.** Celah keamanan (menandai bayar tanpa verifikasi gateway). Gunakan webhook Midtrans. |

### 3.4 Alur pembayaran (Midtrans Snap)

Setelah booking sukses, FE menerima `snapToken` dan `redirectUrl`. Dua cara bayar:

1. **Opsi A — Redirect:** buka `redirectUrl` di tab baru (atau
   `https://app.sandbox.midtrans.com/snap/v2/vt-<snapToken>`).
2. **Opsi B — Embed Snap.js:** muat library Midtrans Snap dari CDN
   (`https://app.sandbox.midtrans.com/snap/snap.js`, dengan `data-client-key`),
   lalu lakukan `snap.pay(snapToken)`.

Hasil pembayaran diproses di sisi server melalui webhook
`POST /payment/notification` (publik). FE cukup **polling**
`GET /user/{kodeBooking}/detail` sampai `statusPembayaran = SUDAH_DIBAYAR`.

Catatan:
- `order_id` Midtrans = `<kodeBooking>-<timestamp>` (backend memetakan ulang ke
  kode booking asli).
- `snapToken` bersifat sekali pakai.

### 3.5 Flow lupa password (OTP)

```
1. POST /auth/request-lupa-password?email=user@mail.com   -> OTP 6 digit terkirim ke email
2. user membaca OTP (berlaku 5 menit)
3. POST /auth/verifikasi-otp?email=user@mail.com&otp=123456
4. POST /auth/reset?email=user@mail.com&passwordBaru=Baru12345
```

- OTP: 6 digit angka, berlaku **5 menit**.
- `reset` hanya berhasil jika OTP sudah berstatus `DIVERIFIKASI`.
- Aturan password backend: minimal 5 karakter, diawali huruf kapital, mengandung
  huruf kecil, diakhiri 3 angka (contoh valid: `Abc12345`).

---

## 4. Alur Lengkap — Sisi Admin (role ADMIN)

| # | Aksi | Method & Path | Auth | Body / Params |
|---|---|---|---|---|
| 1 | Login | `POST /auth/login` | - | `{email, password}` akun admin (lihat bagian 7) |
| 2 | Dashboard | `GET /admin/dashboard/stats` | JWT | - |
| 3 | List penerbangan | `GET /admin/Mengambil-semua-data-penerbangan` | JWT | query: `page`, `size`, `urutanBerdasarkan`, `arah` |
| 4 | Detail penerbangan | `GET /admin/penerbangan/{id}` | JWT | - |
| 5 | Tambah | `POST /admin/tambah-penerbangan` | JWT | lihat body di bawah |
| 6 | Update | `PUT /admin/update-data-penerbangan/{id}` | JWT | field parsial |
| 7 | Hapus | `DELETE /admin/{penerbanganId}/hapus-penerbangan` | JWT | - |
| 8 | Histori pemesanan | `GET /admin/histori-pemesanan` | JWT | filter: `maskapai`, `status` (BELUM_DIBAYAR/SUDAH_DIBAYAR/CANCEL), `tanggal` |
| 9 | Penumpang per penerbangan | `GET /admin/penumpang-per-penerbangan/{penerbanganId}` | JWT | daftar penumpang + nomor kursi |
| 10 | Ekspor history update | `GET /admin/{penerbanganId}/ekspor-history-update-ke-excell` | JWT | unduh `.xlsx` |
| 11 | Cancel booking | `POST /admin/booking/{kodeBooking}/cancel` | JWT | batalkan booking dari sisi admin |

Contoh body tambah penerbangan:
```json
{
  "maskapai": "Garuda Indonesia",
  "kotaKeberangkatan": "Jakarta",
  "kotaTujuan": "Denpasar",
  "waktuKeberangkatan": "2026-09-10T08:30:00",
  "waktuKedatangan": "2026-09-10T11:20:00",
  "hargaTiket": 1250000,
  "kursi": 30
}
```

Response dashboard (`GET /admin/dashboard/stats`):
```json
{
  "sukses": true,
  "pesanNya": "Statistik Dashboard",
  "data": {
    "totalPenerbangan": 10,
    "penerbanganTersedia": 8,
    "penerbanganBerangkat": 1,
    "totalBooking": 24,
    "bookingDibayar": 15,
    "bookingDibatalkan": 4,
    "totalTiketTerjual": 15
  },
  "statusKode": 200
}
```

### Format waktu & enum (penting untuk FE)

- Seluruh `LocalDateTime` dikirim **ISO-8601** (contoh `2026-09-02T13:30:00`).
  FE perlu memformat ulang agar ramah pengguna.
- `StatusPembayaran`: `BELUM_DIBAYAR`, `SUDAH_DIBAYAR`, `CANCEL`
- `KetersediaanPenerbangan`: `TERSEDIA`, `TIDAK_TERSEDIA`
- `StatusPenerbangan`: `ON_TIME`, `DEPARTED`, `ARRIVED`
- `StatusOtp`: `AKTIF`, `DIVERIFIKASI`, `EXPIRED`

---

## 5. Permasalahan / Gap yang Ditemukan saat Audit

1. ~~CORS belum dikonfigurasi~~ **SUDAH DIPERBAIKI.** `SecurityConfig` kini
   menyediakan `CorsConfigurationSource` (origin dari `APP_CORS_ORIGINS`,
   default `http://localhost:3000`), method GET/POST/PUT/DELETE/OPTIONS, header
   `Authorization`/`Content-Type`, dan preflight `OPTIONS` dilewati `JwtFilter`.
2. ~~Base URL hardcoded~~ **SUDAH DIPERBAIKI.** Link verifikasi kini memakai
   `app.base-url` (dari env `APP_BASE_URL`, default `http://localhost:8080`)
   di `UserController`.
3. **Format error sudah dinormalkan.** `JwtFilter` dan handler akses ditolak kini
   mengembalikan bentuk `ResponseApi` yang sama (`sukses`, `pesanNya`,
   `statusKode`, `stempelWaktu`) seperti error bisnis.
4. ~~Typo field `resfresh`~~ **SUDAH DIPERBAIKI.** `RefreshTokenResponse` kini
   memakai `refreshToken` pada `POST /auth/refresh-token`.
5. ~~`data` pada beberapa endpoint tidak terbungkus `ResponseApi`~~ **SUDAH DIPERBAIKI.**
   `GET /user/{kodeBooking}/verifikasi-pembayaran` kini dibungkus `ResponseApi`;
   `POST /user/pemesanan` memang sudah sejak awal. Kini hanya unduhan file
   (PDF/Excel) yang tidak terbungkus.
6. ~~Login mengirim token ke email~~ **SUDAH DIPERBAIKI.** `kirimToken` tidak lagi
   dipanggil pada `/auth/login` maupun `/auth/refresh-token` (FE sudah menerima
   token langsung di respons, jadi email tidak perlu diisi token).
7. ~~Tidak ada endpoint daftar kota/maskapai~~ **SUDAH DIPERBAIKI.** Tersedia
   `GET /user/daftar-kota` dan `GET /user/daftar-maskapai` untuk dropdown pencarian.
8. ~~Peta kursi butuh `kodeBooking`~~ **SUDAH DIPERBAIKI.** Ada endpoint baru
   `GET /user/melihat-peta-kursi-penerbangan?penerbanganId={id}` (tanpa booking),
   sehingga FE bisa menampilkan kursi sebelum checkout. `GET /user/melihat-peta-kursi`
   (via `kodeBooking`) tetap ada untuk pemilihan kursi pasca-pembayaran.
9. ~~Celah pembayaran palsu~~ **SUDAH DIPERBAIKI.** `POST /user/{kodeBooking}/konfirmasi-pembayaran`
   (yang menandai `SUDAH_DIBAYAR` tanpa verifikasi gateway & `permitAll`) **dihapus**.
   Satu-satunya jalur sah untuk menandai lunas adalah webhook
   `POST /payment/notification` (memverifikasi signature SHA-512).
10. ~~JWT bocor ke log~~ **SUDAH DIPERBAIKI.** `System.out.println("Authorization Header: ...")`
    di `JwtFilter` dihapus (akses token tidak lagi tercetak ke konsol).
11. ~~Refresh-token Runtime/500~~ **SUDAH DIPERBAIKI.** Error refresh-token kini
    melempar `TokenTidakDitemukan` → respons `ResponseApi` 401, bukan 500 generic.
12. ~~Halaman verifikasi publik~~ **SUDAH DIPERBAIKI.** `GET /user/{kodeBooking}/halaman-verifikasi-pembayaran`
    kini butuh login & hanya untuk pemilik booking (bukan lagi `permitAll`).
13. ~~Header `Content-Disposition` Excel rusak~~ **SUDAH DIPERBAIKI.** Kini memakai
    `ContentDisposition.builder("attachment").filename(...)`.
14. ~~Hapus penerbangan tanpa cek dependensi~~ **SUDAH DIPERBAIKI.** Jika ada booking
    terhubung, hapus ditolak (409). Respons hapus/tambah tak lagi mengembalikan entity
    utuh, hanya field penting.
15. ~~App-password email hardcode~~ **SUDAH DIPERBAIKI.** Default `MAIL_PASSWORD`
    dikosongkan; nilai hanya dari `.env`. File `hs_err_pid*.log`/`replay_pid*.log`
    (sisa crash JVM) dibersihkan dari root.

---

## 6. Rekomendasi Eksekusi — FE React (pola yang disarankan)

- Simpan `aksesToken` + `refreshToken` + `role` setelah login
  (localStorage/sessionStorage).
- CORS di backend **sudah aktif** (default origin `http://localhost:3000`);
  sesuaikan `APP_CORS_ORIGINS` di `.env` bila origin FE berbeda.
- Satu fungsi `apiFetch()` yang menangani header `Authorization`, pengambilan
  `data` dari `ResponseApi`, dan normalisasi error. Contoh (JavaScript):

```js
const API = "http://localhost:8080";

async function apiFetch(path, { method = "GET", body, isForm = false } = {}) {
  const token = localStorage.getItem("aksesToken");
  const headers = {};
  if (!isForm) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: isForm ? body : body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    throw new Error("Sesi berakhir, silakan login ulang.");
  }

  let payload = {};
  try { payload = await res.json(); } catch { return null; }

  if (!res.ok) {
    throw new Error(payload.pesanNya ?? payload.pesan ?? "Terjadi kesalahan");
  }
  return payload.data ?? payload;
}
```

- Auto-logout saat response 401; auto-refresh lewat `POST /auth/refresh-token`
  bila 401 terjadi pada request ber-auth (interceptor axios / wrapper fetch).
- Halaman yang dibutuhkan: Landing/Cari, Login, Daftar, Lupa password (3 langkah
  OTP), Booking, Konfirmasi Pembayaran, Pilih Kursi, E-Tiket, Riwayat, Profil
  (user); Dashboard, Manajemen Penerbangan, Histori, Penumpang (admin).
- Polling status pembayaran memakai `GET /user/{kodeBooking}/detail` tiap 3-5
  detik hingga `SUDAH_DIBAYAR`, lalu arahkan ke pilih kursi.
- Guard route USER vs ADMIN berdasarkan `role`.
- Format `LocalDateTime` ISO ke tampilan lokal (mis. date-fns).
- Unduh e-tiket: `GET /user/{tiketId}/download-tiket-PDF` dengan `responseType:
  'blob'`, simpan sebagai `.pdf`.

---

## 7. Konfigurasi Lingkungan (.env)

Backend membaca `.env` lewat `springboot3-dotenv`. Nilai yang relevan:

| Variabel | Contoh | Fungsi |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tiket` | koneksi database |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `password123` | kredensial DB |
| `JWT_SECRET` | rahasia panjang | kunci tanda tangan JWT |
| `JWT_EXPIRATION_MS` | `3600000` | umur access token (1 jam) |
| `MAIL_HOST` / `MAIL_PORT` | `smtp.gmail.com` / `587` | server SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | email gmail / app password | pengirim email (OTP, token, tiket) |
| `ADMIN_EMAIL` | `fajar.rafsan01@gmail.com` | akun admin default (dibuat otomatis) |
| `ADMIN_PASSWORD` | `Admin12345` | password admin default |
| `MIDTRANS_SERVER_KEY` / `MIDTRANS_CLIENT_KEY` | `Mid-server-...` | kunci payment gateway |
| `MIDTRANS_IS_PRODUCTION` | `false` | mode sandbox Midtrans |
| `APP_BASE_URL` | `http://localhost:8080` | base URL link yang dikirim backend (verifikasi pembayaran) |
| `APP_CORS_ORIGINS` | `http://localhost:3000` | daftar origin FE yang diizinkan (pisah koma) |

Menjalankan aplikasi: butuh Java 21 + PostgreSQL aktif. `mvnw spring-boot:run`.
Swagger: `http://localhost:8080/swagger-ui/index.html`.

---

## 8. Checklist Konsumsi Endpoint (FE)

- [ ] CORS backend aktif: pastikan origin FE ada di `APP_CORS_ORIGINS` (default `http://localhost:3000`).
- [ ] Token disimpan aman; header `Authorization: Bearer <token>` selalu dikirim.
- [ ] Normalisasi `ResponseApi` vs error `JwtFilter`.
- [ ] Validasi form mengikuti aturan backend (nama 2-25 karakter, noHP `08...`,
      password kapital + kecil + 3 angka, KTP jpg/png/pdf maks 2MB).
- [ ] Polling status pembayaran (interval 3-5 detik).
- [ ] Guard route USER vs ADMIN berdasarkan `role`.
- [ ] Download e-tiket via blob (`responseType: 'blob'`).
- [ ] Tangani halaman Spring pagination di tabel admin.

---

*Draft ini bersumber dari kode aktual (controller, DTO, service, security) dan
memetakan 1:1 dengan endpoint yang berjalan di `src/main/java`.
Status: DRAFT v3 — CORS, base-url dinamis, normalisasi error, dan typo `resfresh`
telah diperbaiki di backend.*