

# ✈️ Aplikasi Tiket Pesawat

Sistem ini menyediakan layanan pemesanan tiket pesawat untuk **User** dan manajemen penerbangan untuk **Admin**.

---

## 📋 Fitur

### 1. Authentication (Auth)
- **Daftar** — Registrasi akun baru.
- **Login** — Masuk ke sistem menggunakan akun.
- **Logout** — Keluar dari akun yang sedang aktif.
- **Refresh Token** — Memperpanjang masa aktif token autentikasi.
- **Request Lupa Password** — Permintaan reset password dengan email.
- **Verifikasi OTP** — Memasukkan kode OTP untuk verifikasi reset password.
- **Reset Password** — Mengganti password setelah verifikasi berhasil.

### 2. Admin
- **Tambah Penerbangan** — Menambahkan jadwal dan detail penerbangan baru.
- **Update Data Penerbangan** — Mengubah informasi penerbangan yang sudah ada.
- **Hapus Penerbangan** — Menghapus data penerbangan tertentu.
- **Ambil Semua Data Penerbangan (Pagination)** — Melihat daftar semua penerbangan dengan fitur pagination.
- **Ekspor Histori Update Penerbangan** — Mengunduh histori perubahan data penerbangan dalam bentuk file (misal CSV/Excel).
- **Update Data Penerbangan** — Menyunting jadwal atau informasi lainnya untuk penerbangan yang sudah ada.

### 3. User
- **Melihat Penerbangan Tersedia** — Menampilkan daftar semua penerbangan yang bisa dipesan.
- **Pemesanan** — Memesan tiket penerbangan.
- **Verifikasi Pembayaran** — Mengkonfirmasi pembayaran tiket.
- **Melihat Kursi Tersedia** — Menampilkan daftar kursi kosong pada penerbangan.
- **Memilih Nomor Kursi** — Memilih dan memesan kursi yang diinginkan.
- **Download Tiket PDF** — Mengunduh tiket penerbangan dalam format PDF.

---

## 🚀 Teknologi
- **Backend**: [Spring Boot] / [Express.js] / (isi sesuai projectmu)
- **Database**: (MySQL / PostgreSQL / MongoDB)
- **Authentication**: JWT (JSON Web Token)
- **File Export**: (Apache POI untuk Excel atau pdfkit untuk PDF)

---

## ⚙️ Cara Install dan Jalankan
1. Clone repository:
   ```bash
   git clone https://github.com/namaproject/tiket-pesawat.git
   ```
2. Masuk ke folder project:
   ```bash
   cd tiket-pesawat
   ```
3. Install dependencies:
   ```bash
   npm install
   ```
4. Jalankan server:
   ```bash
   npm start
   ```
   (atau sesuaikan kalau pakai Java/Spring Boot)

---

## 🛡️ Hak Akses
- **Admin**: Akses penuh untuk manajemen penerbangan.
- **User**: Akses untuk melihat penerbangan, melakukan pemesanan, dan mengelola tiket.

---
