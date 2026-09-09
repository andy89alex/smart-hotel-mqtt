# Smart Hotel Room Controller — MQTT Portfolio Project

**Tanggal:** 2026-09-09
**Status:** Design disetujui, siap masuk implementation plan
**Tujuan:** Proyek portfolio/CV untuk melamar posisi yang JD-nya menyebut **Java, Spring, dan MQTT**. Harus bisa jalan **tanpa hardware** dan didemokan di laptop mana pun.

---

## 1. Ringkasan

Simulasi sistem kontrol kamar hotel berbasis MQTT. Sekumpulan "kamar" virtual mem-*publish* status (suhu, lampu, AC, Do-Not-Disturb, availability) ke broker MQTT, dan sebuah dashboard front-desk menampilkan status seluruh kamar secara real-time serta mengirim perintah kembali ke kamar.

Proyek ini dirancang untuk **menunjukkan penguasaan konsep MQTT yang lazim ditanyakan interviewer**, bukan untuk kelengkapan produk. Setiap fitur MQTT dipetakan ke skenario nyata sehingga bisa diceritakan saat wawancara.

**Non-tujuan:** produk siap pakai, keamanan tingkat produksi, persistensi historis.

---

## 2. Sasaran & Kriteria Sukses

- `mvn test` hijau tanpa Docker (broker embedded) → seluruh perilaku MQTT terverifikasi otomatis. Untuk demo live, `docker compose up` (atau Mosquitto lokal + `mvn spring-boot:run`) menghidupkan seluruh sistem.
- Dashboard yang baru connect **langsung** menampilkan status terakhir semua kamar (via retained message), tanpa menunggu telemetri berikutnya.
- Mematikan satu proses/koneksi kamar → dashboard menandai kamar itu `offline` dalam hitungan detik (via LWT).
- Operator menekan toggle di dashboard → kamar terkait merespons dan status ter-update di semua klien.
- Ada integration test yang menjalankan broker MQTT asli (Moquette embedded, in-process, tanpa Docker) dan memverifikasi pub/sub, retained, serta LWT.
- README menjelaskan pemetaan tiap konsep MQTT ke bagian kode (bahan cerita interview).

---

## 3. Arsitektur

Multi-module project (Maven, disarankan; boleh Gradle), tiga bagian, di-orkestrasi lewat Docker Compose:

```
┌─────────────────┐         ┌──────────────┐         ┌──────────────────┐
│ room-simulator  │  MQTT   │  Mosquitto   │  MQTT   │    dashboard     │
│ (Spring Boot)   │────────▶│   (broker)   │◀────────│  (Spring Boot)   │
│  N virtual room │ pub/sub  │   Docker     │ pub/sub │  + WebSocket→web │
└─────────────────┘         └──────────────┘         └──────────────────┘
                                                              │
                                                       browser (front desk)
```

### Komponen

1. **Broker — Mosquitto (Docker)**
   Broker MQTT standar. Konfigurasi minimal via file `mosquitto.conf`. Anonymous access untuk versi dasar; auth adalah stretch goal (lihat §8).

2. **room-simulator (Spring Boot)**
   - Membuat **N kamar virtual** (jumlah & penamaan lewat konfigurasi `application.yml`).
   - **Tiap kamar memiliki koneksi MQTT sendiri** — syarat agar LWT bersifat per-kamar.
   - Tiap kamar:
     - Publish telemetri suhu berkala (mis. tiap 5 detik), QoS 1.
     - Publish state lampu/AC/DND sebagai **retained**, QoS 1, setiap kali berubah.
     - Publish `availability = online` (retained) saat connect; set **LWT** `availability = offline` (retained) agar broker mengumumkannya saat koneksi putus.
     - Subscribe ke topic perintah (`cmd/*`), lalu mengubah state internal & mem-publish state baru.

3. **dashboard (Spring Boot)**
   - Subscribe ke `hotel/#`, memelihara **state kamar di memori**.
   - Mendorong perubahan ke browser via **WebSocket (STOMP)**.
   - Menyajikan **satu halaman** front-desk: satu kartu per kamar (suhu, lampu, AC, DND, online/offline) + kontrol toggle.
   - Saat operator menekan toggle → dashboard mem-*publish* ke `cmd/*` kamar terkait.

---

## 4. Struktur Topic MQTT

```
hotel/{floor}/{room}/telemetry/temperature   retained=false QoS1   (simulator → )
hotel/{floor}/{room}/state/light             retained=true  QoS1   (simulator → )
hotel/{floor}/{room}/state/ac                retained=true  QoS1   (simulator → )
hotel/{floor}/{room}/state/dnd               retained=true  QoS1   (simulator → )
hotel/{floor}/{room}/availability            retained=true  LWT    (online/offline)
hotel/{floor}/{room}/cmd/light               QoS1                  ( → simulator)
hotel/{floor}/{room}/cmd/ac                  QoS1                  ( → simulator)
hotel/{floor}/{room}/cmd/dnd                 QoS1                  ( → simulator)
```

- Dashboard subscribe: `hotel/#`.
- Telemetri suhu **tidak** retained (nilai sesaat); state lampu/AC/DND **retained** (kondisi persisten); availability **retained + LWT**.
- Contoh floor/room: `hotel/floor2/room201/...`.

### Format payload

JSON ringkas, mis.:
- `telemetry/temperature`: `{"value": 22.4, "unit": "C", "ts": "2026-09-09T10:00:00Z"}`
- `state/light`: `{"on": true, "ts": "..."}`
- `availability`: `{"status": "online", "ts": "..."}` (LWT: `{"status":"offline"}`)
- `cmd/light`: `{"on": false}`

---

## 5. Pemetaan Konsep MQTT (jualan utama ke JD)

| Konsep | Diimplementasikan di |
|---|---|
| Topic hierarchy + wildcard `#` | struktur topic §4; subscribe dashboard |
| QoS 1 | telemetri penting & seluruh perintah |
| Retained message | state kamar & availability → dashboard baru langsung lihat kondisi terakhir |
| Last Will & Testament | kamar crash/disconnect → auto `offline` |
| Spring Integration MQTT | inbound/outbound channel adapter (bukan raw Paho langsung) → sinkron dengan "Spring" di JD |

---

## 6. Teknologi

- **Java 21** (di-compile dengan `release` 21; mesin dev menjalankan JDK 25), **Spring Boot 3.5.6** (dipilih untuk kompatibilitas JDK baru)
- **Spring Integration MQTT** (`spring-integration-mqtt`, di atas Eclipse Paho) untuk inbound/outbound
- **Spring WebSocket + STOMP** untuk push real-time ke browser
- **Frontend**: satu halaman sederhana (HTML + JS via STOMP/SockJS). Fokus fungsi & real-time, bukan UI cantik. Boleh Thymeleaf untuk serve halaman.
- **Broker (live demo)**: Eclipse Mosquitto — via Docker Compose (opsional) atau Mosquitto lokal (`brew install mosquitto`)
- **Testing**: JUnit 5 + **broker Moquette embedded** (in-process, `io.moquette:moquette-broker`) — integration test **tidak butuh Docker**
- **Build**: Maven multi-module; Docker Compose disediakan sebagai jalur demo opsional (bukan syarat test)

---

## 7. Strategi Testing

- **Unit test**: mapping payload JSON ↔ domain object; logika transisi state kamar (mis. perintah `light on` → state berubah & ter-publish).
- **Integration test (broker Moquette embedded, in-process, tanpa Docker)**:
  - Jalankan broker MQTT asli di dalam proses test pada port acak.
  - Verifikasi: publish retained → subscriber yang baru connect menerima nilai terakhir.
  - Verifikasi: perintah `cmd/*` → simulator mengubah & mem-publish state.
  - Verifikasi: koneksi kamar diputus → pesan LWT `offline` diterima subscriber.

---

## 8. Di Luar Scope (YAGNI)

- **Database / histori grafik** — tidak ada. Retained message MQTT menjadi sumber kebenaran status terakhir; state runtime cukup di memori.
- **Login/auth user pada dashboard** — tidak ada.
- **UI mewah** — tidak ada.

### Stretch goal opsional (tidak wajib untuk dianggap selesai)

- **TLS + username/password pada broker** — konfigurasi Mosquitto dengan kredensial & sertifikat, klien Spring menyesuaikan. Nilai tambah keamanan untuk cerita interview.

---

## 9. Struktur Repo (rencana)

```
/
├─ docker-compose.yml
├─ broker/
│  └─ mosquitto.conf
├─ room-simulator/        (Spring Boot module)
├─ dashboard/             (Spring Boot module)
├─ pom.xml                (parent, multi-module)
└─ README.md              (pemetaan konsep MQTT + cara menjalankan)
```

---

## 10. Risiko / Catatan

- **LWT per kamar** mengharuskan tiap kamar punya koneksi MQTT terpisah — pastikan jumlah kamar default kecil (mis. 4–6) agar ringan; jumlah dibuat konfigurasi.
- **Spring Integration MQTT** punya kurva belajar dibanding raw Paho; jika menghambat, boleh fallback ke Paho langsung, tetapi Spring Integration lebih selaras dengan JD.
- **Retained vs non-retained** harus konsisten dengan §4; kesalahan di sini adalah bug paling mungkin dan justru materi cerita interview yang bagus.
