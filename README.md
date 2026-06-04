# 🛡️ Aethelguard

> Paper tabanlı Minecraft sunucuları için temiz loglu, yüksek özelleştirilebilirlik sunan, captcha + 2FA + recovery + adaptive security destekli modern auth eklentisi.

## 🧠 v0.3-sentinel — Adaptive Security + Recovery Update

`v0.3-sentinel`, Aethelguard’ın **Adaptive Security + Recovery Update** sürümüdür. Bu güncelleme; captcha, 2FA, VPN/proxy kontrolü, güvenlik soruları, backup code, şifre sıfırlama, gelişmiş şifre politikası ve config sync sistemini tek büyük güvenlik paketinde toplar.

Aethelguard, oyuncular sunucuya girdiğinde onları doğrulama tamamlanana kadar kontrollü bir güvenlik ekranında tutar. Oyuncu captcha, login/register, 2FA veya recovery aşamasındayken dünyaya, chate ve riskli komutlara erişemez. Doğrulama tamamlandığında envanteri geri verilir, bossbar temizlenir ve güvenli konumuna döner.

Amaç basit: **auth güçlü olsun, console temiz kalsın, admin her şeyi configten rahatça yönetebilsin.** ✨

---

## 📌 Güncel Sürüm Durumu

Şu anda Aethelguard için önerilen ana sürüm: **`v0.3-sentinel`** ✅

GitHub Releases kısmında **Latest / stable** olarak tutulması gereken sürüm budur. Normal sunucu sahipleri, canlı sunucularında bu sürümü kullanmalıdır. `v0.3-sentinel`; Adaptive Security + Recovery Update’in tamamlanmış, ana yayın olarak paylaşılmış sürümüdür.

`v0.3.1-dev` ile başlayan ve `v0.4-dev` test build’ine kadar devam eden `dev` etiketli sürümler ise **pre-release / test build** olarak düşünülmelidir. 🧪

Bu dev sürümler yeni özellikleri erkenden denemek isteyen adminler içindir. İçlerinde PIN sistemi, PIN GUI polish, diagnostics, reload backup, temporary lockout, gelişmiş status çıktısı ve tab completion polish gibi yeni özellikler bulunabilir; fakat bu sürümler canlı sunucuya koymadan önce mutlaka yedek/test sunucusunda denenmelidir.

Kısa özet:

- ✅ **Canlı sunucu için:** `v0.3-sentinel`
- 🧪 **Yeni özellikleri erken test etmek için:** `v0.3.x-dev` ve `v0.4-dev`
- 🚧 **Final ücretsiz yol haritası:** `v0.4-sentinel`

---

## 🚀 Öne Çıkanlar

- 🔐 `/register`, `/login`, `/changepassword` hesap sistemi
- 🧂 BCrypt ile güvenli şifre hashleme
- 💾 Local YAML veya MySQL depolama
- 🧩 Detaylı `config.yml` ve otomatik config düzenleme sistemi
- 🌐 `messages_<code>.yml` dil sistemi
- 🇹🇷 Türkçe ve İngilizce hazır mesaj dosyaları
- 🖥️ Console için `en`, `tr native`, `tr ascii` log desteği
- 🧼 Vanilla join/quit/connection/command loglarını susturma
- 🧠 Adaptive security ve VPN/proxy kontrolü
- 🧪 Captcha sistemi: `MAP`, `TEXT`, `NUMERIC`, `ALPHANUMERIC`, `MATH`
- 🗺️ Harita üstünde gerçek captcha kodu gösterimi
- 📱 TOTP 2FA: Google Authenticator, Microsoft Authenticator, Authy uyumlu
- 🔁 Session auto-login
- 🧯 Security question ve backup code tabanlı account recovery
- ⏳ Hassas güvenlik komutları için cooldown sistemi
- 🔑 Gelişmiş şifre politikası
- 📊 `/aethelguard status` ve session yönetimi
- 🎒 Auth sırasında envanter, zırh ve offhand gizleme
- 💬 Auth olmamış oyuncuların chat görmesini/konuşmasını engelleme
- 📌 Auth durumuna göre bossbar yönlendirmeleri
- 🧭 İlk register sonrası spawn yönlendirmesi
- 🕳️ Void-zone güvenli bekleme alanı

---

## 📦 Kurulum

1. 📁 `aethelguard-0.3-sentinel.jar` dosyasını sunucunun `plugins` klasörüne at.
2. ▶️ Sunucuyu başlat.
3. ⚙️ `plugins/Aethelguard/config.yml` dosyasını düzenle.
4. 🌍 Mesajları değiştirmek istersen `plugins/Aethelguard/messages/` klasörünü kullan.
5. 🧠 Güvenlik sorularını değiştirmek istersen `plugins/Aethelguard/security_questions/` klasörünü düzenle.
6. 🔁 Database veya temel auth ayarlarını değiştirdiysen sunucuyu yeniden başlat.

> 💡 GitHub Releases üzerinden indiriyorsan **Latest** etiketi olan `v0.3-sentinel` sürümünü tercih et. `dev` yazan sürümler pre-release olduğu için daha çok test etmek, geri bildirim vermek veya yeni özellikleri erkenden görmek isteyenler içindir.

Çoğu ayar reload ile yenilenebilir:

```text
/aethelguard reload
```

---

## ⌨️ Oyuncu Komutları

| Komut | Aliaslar | Açıklama |
| --- | --- | --- |
| `/register <şifre> <şifre tekrar>` | `/kayitol`, `/kayıtol`, `/kayit` | Yeni hesap oluşturur. |
| `/login <şifre>` | `/giris`, `/giriş` | Hesaba giriş yapar. |
| `/captcha <kod>` | `/dogrula`, `/doğrula` | Captcha doğrulamasını tamamlar. |
| `/changepassword <eski> <yeni> <yeni tekrar>` | `/sifredegistir`, `/şifredeğiştir`, `/password` | Oyuncunun kendi şifresini değiştirmesini sağlar. |
| `/twofactor <kod>` | `/2fa`, `/authenticator`, `/authy` | Login sırasında 2FA kodunu doğrular. |
| `/twofactor setup` | `/2fa setup` | Authenticator kurulumunu başlatır. |
| `/twofactor confirm <kod>` | `/2fa confirm <kod>` | 2FA kurulumunu tamamlar. |
| `/twofactor disable <kod>` | `/2fa disable <kod>` | 2FA korumasını kapatır. |
| `/securityquestion setup` | `/securityq`, `/guvenliksorusu` | Güvenlik sorusu seçimini başlatır. |
| `/securityquestion answer <cevap>` | `/securityq answer` | Güvenlik sorusu cevabını kaydeder. |
| `/backupcodes generate` | `/backupcode`, `/yedekkodlar` | Tek kullanımlık recovery kodları üretir. |
| `/recoverymethod question` | `/recovery` | Recovery yöntemini güvenlik sorusu yapar. |
| `/recoverymethod backup-code` | `/recovery` | Recovery yöntemini backup code yapar. |
| `/recover question <cevap> <yeniŞifre>` | `/sifresifirla`, `/kurtar` | Güvenlik sorusuyla şifre sıfırlar. |
| `/recover code <kod> <yeniŞifre>` | `/sifresifirla`, `/kurtar` | Backup code ile şifre sıfırlar. |

---

## 👑 Admin Komutları

| Komut | Açıklama |
| --- | --- |
| `/aethelguard reload` | Config, mesajlar ve database bağlantısını yeniler. |
| `/aethelguard status <oyuncu>` | Oyuncunun auth kaydını, IP bilgisini, konumunu ve deneme sayılarını gösterir. |
| `/aethelguard sessions` | Aktif auth session listesini gösterir. |
| `/aethelguard session <oyuncu>` | Oyuncunun aktif session bilgisini gösterir. |
| `/aethelguard clearsession <oyuncu>` | Bir oyuncunun session kaydını temizler. |
| `/aethelguard clearsessions` | Tüm session kayıtlarını temizler. |
| `/aethelguard unregister <oyuncu>` | Oyuncunun auth kaydını siler. |
| `/aethelguard changepassword <oyuncu> <yeniŞifre>` | Bir oyuncunun şifresini admin olarak değiştirir. |
| `/aethelguard unlogin <oyuncu>` | Online oyuncuyu oyundan atmadan tekrar auth ekranına alır. |

Admin aliasları:

```text
/ag
/aeg
/ayarlar
```

---

## 🧭 Auth Akışı

Oyuncu sunucuya girdiğinde Aethelguard önce güvenli konumunu hatırlar. Ardından oyuncu auth ekranına alınır.

Varsayılan akış:

1. 🕳️ Oyuncu void-zone konumuna taşınır.
2. 🎒 Envanteri, zırhları ve offhand itemi geçici olarak gizlenir.
3. 🧠 IP ve risk durumu kontrol edilir.
4. 🧪 Captcha gerekiyorsa captcha çözmesi istenir.
5. 🔐 Hesabı yoksa `/register`, hesabı varsa `/login` istenir.
6. 📱 Hesapta 2FA açıksa `/2fa <kod>` istenir.
7. ✅ Auth tamamlanınca bossbar kaldırılır, envanter geri verilir ve oyuncu güvenli konuma döner.

Önemli detaylar:

- 🏠 İlk register sonrasında oyuncu spawn noktasına gönderilir.
- 🕳️ Oyuncu auth olmadan çıkarsa void-zone konumu son konum olarak kaydedilmez.
- 🧼 Login/register sonrası sadece oyuncunun kendi chat ekranı temizlenir.
- 💬 Login ekranındaki oyuncular normal oyuncu chatini göremez.
- 🎒 Captcha map itemi gerçek oyuncu envanterine kalıcı olarak karışmaz.

---

## 🧪 Captcha Sistemi

Aethelguard captcha sistemi login/register öncesinde çalışır.

Desteklenen türler:

- 🗺️ `MAP`
- 🔤 `TEXT`
- 🔢 `NUMERIC`
- 🔡 `ALPHANUMERIC`
- ➕ `MATH`

Varsayılan:

```yml
auth-settings:
  captcha:
    enabled: true
    types: ["MAP"]
```

Birden fazla captcha türü kullanmak istersen:

```yml
types: ["MAP", "TEXT", "NUMERIC", "ALPHANUMERIC", "MATH"]
```

Captcha güvenliği ayrı yönetilir:

- ⏱️ `cooldown-seconds`
- 🎯 `max-attempts`
- 👢 `kick-enabled`
- 🗺️ `map.give-item`
- 🔊 `success-sound`

Captcha deneme hakkı, yanlış şifre deneme hakkından ayrıdır. Böylece captcha için 5 hak, login için 3 hak gibi farklı kurallar kullanabilirsin.

---

## 📱 2FA / Authenticator

Aethelguard TOTP standardını kullanır. Şu uygulamalarla uyumludur:

- 📱 Google Authenticator
- 🔐 Microsoft Authenticator
- 🟣 Authy
- 🧩 TOTP destekleyen benzer uygulamalar

Kurulum:

```text
/2fa setup
/2fa confirm <kod>
```

2FA açık oyuncularda captcha sonrası doğrudan `/2fa <kod>` akışına geçilebilir. Böylece oyuncu arka arkaya captcha + login + 2FA yapmak zorunda kalmaz.

---

## 🧯 Account Recovery

0.3-sentinel ile recovery sistemi çok daha güçlü hale geldi.

Desteklenen yöntemler:

- 🧠 Güvenlik sorusu
- 🧾 Tek kullanımlık backup code

Oyuncu recovery yöntemini seçebilir:

```text
/recoverymethod question
/recoverymethod backup-code
```

Şifre sıfırlama:

```text
/recover question <cevap> <yeniŞifre>
/recover code <yedekKod> <yeniŞifre>
```

Admin isterse yöntemleri ayrı ayrı kapatabilir:

```yml
recovery:
  security-questions:
    enabled: true
  backup-codes:
    enabled: true
```

---

## 🧠 Adaptive Security

Aethelguard, oyuncunun IP geçmişine ve risk sinyallerine göre auth davranışını değiştirebilir.

Özellikler:

- ✅ Güvenilir IP captcha bypass
- 🚨 Şüpheli IP için ekstra captcha
- 📌 Manuel suspicious IP listesi
- 🧪 Şüpheli oyuncular için farklı captcha türü
- 🧭 Aynı IP’den çok hesap açılmışsa risk sinyali
- ❌ Yanlış şifre denemesi artarsa risk sinyali

Örnek:

```yml
adaptive-security:
  trusted-ip-captcha-bypass:
    enabled: true
    window-minutes: 30
    required-successful-logins: 3
```

---

## 🕵️ VPN / Proxy Kontrolü

0.3-sentinel ile VPN/proxy kontrolü eklendi.

Desteklenen providerlar:

- 🌐 `IPWHOIS`
- 🌐 `IPAPI`

Kontrol edilen sinyaller:

- VPN
- Proxy
- Tor
- Hosting/datacenter

Örnek config:

```yml
adaptive-security:
  suspicious-ip-extra-captcha:
    vpn-check:
      enabled: true
      providers: ["IPWHOIS", "IPAPI"]
      min-detections: 1
      timeout-ms: 2500
      cache-minutes: 360
      fail-open: true
```

Notlar:

- 🧠 Sonuçlar cache’lenir.
- ⏱️ Timeout düşük tutulur ki login ekranı uzun beklemesin.
- 🧪 VPN/proxy şüpheli bulunursa ekstra captcha uygulanır.
- 🏠 Local/private IP’ler default olarak kontrol edilmez.

---

## 🔑 Şifre Politikası

0.3-sentinel ile şifre kuralları çok daha detaylı hale geldi.

Yönetilebilenler:

- Minimum uzunluk
- Maximum uzunluk
- Harf zorunluluğu
- Sayı zorunluluğu
- Kullanıcı adını şifrede engelleme
- Türkçe karakter izni
- Noktalama işareti izni
- Emoji/özel font/alışılmadık sembol izni
- Yasaklı kelime listesi

Örnek:

```yml
auth-settings:
  password-policy:
    enabled: true
    min-length: 4
    max-length: 64
    require-letter: false
    require-number: false
    block-username: true
    allow-turkish-characters: true
    allow-punctuation: true
    allow-non-alphabet-symbols: false
    blocked-words:
      - "admin"
      - "password"
      - "sifre"
      - "şifre"
```

Bu kurallar şu akışlarda ortaktır:

- `/register`
- `/changepassword`
- `/recover`
- `/aethelguard changepassword`

---

## ⏳ Security Cooldowns

Hassas hesap güvenliği komutları için cooldown sistemi vardır.

Varsayılan örnekler:

- Şifre değiştirme
- 2FA kurma
- 2FA kapatma
- Güvenlik sorusu değiştirme
- Recovery yöntemi değiştirme
- Backup code üretme
- Recover ile şifre sıfırlama

Adminler ayrıca kendi komutlarını listeye ekleyebilir:

```yml
security-cooldowns:
  custom:
    enabled: true
    hours: 24
    commands: []
```

---

## 🔄 Session Auto-Login

Oyuncu başarılı login/register sonrası kısa süreli session alabilir.

```yml
auth-settings:
  sessions:
    enabled: true
    duration-minutes: 10
    match-ip: true
```

`match-ip: true` önerilir. Böylece session sadece aynı IP’den geçerli olur.

---

## 🧩 Config Sync Sistemi

Aethelguard config dosyasını açılışta ve reload sırasında kontrol eder.

Yaptıkları:

- 🧩 Eksik config keylerini ekler.
- 📝 Yeni keyleri commentleriyle ekler.
- 📌 Yeni ayarları dosyanın en altına atmaz.
- 🗂️ Ayarları doğru kategoriye taşır.
- 🔵 Düzenleme yaptığında console’a mavi bilgi logu basar.
- 🛡️ Mevcut admin değerlerini korur.
- 🧼 Eski `local-logging` bloğunu temizler.

Bu sistem özellikle eski sürümden yeni sürüme geçen sunucular için tasarlandı.

---

## 🌍 Dil Sistemi

Oyuncu mesajları `messages_<code>.yml` dosyalarından okunur.

Hazır diller:

- 🇹🇷 `messages_tr.yml`
- 🇬🇧 `messages_en.yml`

Özel dil eklemek:

1. `plugins/Aethelguard/messages/messages_de.yml` oluştur.
2. Mevcut mesaj keylerini ekle.
3. Çevirileri düzenle.
4. Configte dili değiştir:

```yml
default-language: "DE"
```

`default-language` özel dil dosyalarını destekler.  
`console-language` yalnızca console logları içindir.

---

## 🖥️ Console Dili

Console logları ayrı yönetilir:

```yml
console-language: "tr"
console-text-mode: "ascii"
```

Modlar:

- 📝 `native`: Türkçe karakterleri olduğu gibi yazar. Örnek: `başarıyla giriş yaptı`
- 🖥️ `ascii`: Türkçe karakterleri console-safe hale getirir. Örnek: `basariyla giris yapti`

Hosting panelin Türkçe karakterleri bozuyorsa `ascii` kullanman önerilir.

---

## 🧼 Log Sistemi

Aethelguard gereksiz vanilla log kalabalığını azaltabilir.

Filtrelenebilenler:

- `joined the game`
- `left the game`
- `logged in with entity id`
- `lost connection`
- `UUID of player`
- `issued server command`

Şifre içeren `/login` ve `/register` komutları console’a düz şekilde düşmez.

Aethelguard kendi önemli olaylarını temiz loglar:

- Login success
- Register success
- Auto-login success
- Auth olmadan çıkış
- Auth olmuş oyuncu çıkışı
- Admin unregister
- Admin unlogin
- Password change
- VPN kontrol sonucu

---

## 💾 Depolama

Local mod:

```text
plugins/Aethelguard/users/<uuid>.yml
plugins/Aethelguard/users/user-index.txt
```

MySQL mod:

```yml
database:
  enabled: true
  host: "localhost"
  port: 3306
  database: "aethelguard_db"
  username: "root"
  password: "password123"
```

Local YAML sistemi kaldırılmadı. MySQL kapalıysa plugin local user dosyalarıyla çalışır.

---

## 🧾 0.2-sentinel → 0.3-sentinel Farkları

### 🧠 Update Adı

**Aethelguard v0.3-sentinel — Adaptive Security + Recovery Update**

Bu isim, 0.3-sentinel’in ana odağını özetler: Oyuncunun giriş riskini daha akıllı değerlendirmek ve hesabını kaybettiğinde güvenli şekilde geri alabilmesini sağlamak.

0.2-sentinel, temiz auth akışı ve düzenli console logları üzerine kurulu ilk tam sürümdü. 0.3-sentinel ise Aethelguard’ı daha ciddi bir güvenlik paketine dönüştürüyor.

### 🔐 Auth ve Güvenlik

- 0.2’de temel login/register vardı.
- 0.3’te captcha, 2FA, recovery ve adaptive security birlikte çalışıyor.
- 2FA açık oyuncular captcha sonrası direkt `/2fa` akışına gidebiliyor.
- Şüpheli IP’ler ekstra captcha alabiliyor.
- Güvenilir IP’ler belirli şartlarda captcha bypass alabiliyor.

### 🧪 Captcha

- 0.2’de captcha yoktu.
- 0.3’te map captcha ve alternatif captcha türleri geldi.
- Captcha deneme hakkı yanlış şifre denemesinden ayrıldı.
- Captcha cooldown ve kick davranışı ayrı ayarlanabiliyor.
- Map item auth sonrası oyuncunun elinden temizleniyor.

### 📱 2FA

- 0.2’de 2FA yoktu.
- 0.3’te TOTP tabanlı authenticator desteği geldi.
- Google Authenticator, Microsoft Authenticator, Authy ve benzeri uygulamalar destekleniyor.

### 🧯 Recovery

- 0.2’de şifre sıfırlama/recovery sistemi yoktu.
- 0.3’te güvenlik sorusu ve backup code sistemi geldi.
- Oyuncu kendi recovery yöntemini seçebiliyor.
- Admin isterse security question veya backup code sistemini ayrı ayrı kapatabiliyor.

### 🧠 Adaptive Security

- 0.2’de IP bazlı gelişmiş risk kontrolü yoktu.
- 0.3’te trusted IP captcha bypass, suspicious IP extra captcha ve VPN/proxy detection geldi.

### 🔑 Şifre Politikası

- 0.2’de şifre kuralları sınırlıydı.
- 0.3’te min/max uzunluk, harf/sayı zorunluluğu, username engeli, yasak kelime listesi, Türkçe karakter, noktalama ve emoji/sembol kontrolü geldi.

### 🧩 Config Sistemi

- 0.2’de config daha düzenli hale getirilmeye başlanmıştı.
- 0.3’te config sync sistemi çok daha olgunlaştı.
- Eksik ayarlar commentleriyle doğru yere ekleniyor.
- Yanlış yerdeki bilinen ayarlar doğru kategoriye taşınıyor.
- Eski `local-logging` bloğu temizleniyor.

### 🖥️ Console ve Dil

- 0.2’de console dili TR/EN ayrımı vardı.
- 0.3’te Türkçe console için `native` ve `ascii` text mode daha sağlam hale geldi.
- Messages EN/TR keyleri eşit tutuldu.
- Yeni tüm oyuncu mesajları messages dosyalarına bağlandı.

---

## 🧪 Pre-release Dev Sürümleri

`v0.3-sentinel`, şu anki ana ve önerilen sürümdür. Bunun üstüne çıkan `v0.3.x-dev` sürümler, Aethelguard’ın bir sonraki büyük ücretsiz sürümü olan `v0.4-sentinel` için hazırlanan test yapılarıdır.

Bu sürümlerin amacı, yeni özellikleri küçük parçalar halinde denemek ve geri bildirim toplamaktır. Yani her dev build bir şeyleri ileri taşır; fakat “her sunucuya direkt kur, unut” mantığında değildir. Özellikle canlı sunucularda kullanmadan önce yedek almak ve test ortamında denemek önerilir. 🔐

Dev sürümlerde eklenen/eklenecek başlıklar:

- 🔢 `v0.3.1-dev` / `v0.3.2-dev`: PIN login temelleri ve komut akışı
- 🎛️ `v0.3.3-dev`: PIN GUI polish, tema sistemi, gizli PIN gösterimi, random numpad ve ses geri bildirimleri
- 🩺 `v0.3.4-dev`: Admin diagnostics, config kontrol raporları, player/session inceleme ve dump dosyaları
- 💾 `v0.3.5-dev`: Reload öncesi config/messages/security question backup sistemi
- 🔒 `v0.3.6-dev`: Geçici auth lockout sistemi
- 📊 `v0.3.7-dev`: Activity/status polish
- 🎨 `v0.3.8-dev`: Theme preview ve tab completion polish
- 🧹 `v0.3.9-dev`: Config, messages ve README final cleanup
- 🧪 `v0.4-dev`: Final test build
- ✅ `v0.4-sentinel`: Final ücretsiz sürüm

Kısacası: **`v0.3-sentinel` güvenli ana sürüm, `v0.3.x-dev` ise 0.4’e giden test laboratuvarı.** ✨

---

## 🛣️ Gelecek Planları

Planlanan büyük fikirler:

- 🔢 PIN ile giriş sistemi
- 🧰 Oyuncu ayarları için GUI
- 👑 Yetkili komutları için GUI
- 🎫 Discord entegre ticket sistemi
- 🎨 ItemsAdder, Nexo, Oraxen gibi resource-pack pluginleriyle uyum
- 🧬 LuckPerms entegrasyonu
- 🌐 Web panel
- 📊 Oyuncu ve yetkili aktivite raporları
- 🏆 Yetkili performans sıralaması

---

## 🔑 İzinler

| Permission | Açıklama |
| --- | --- |
| `aethelguard.admin` | Admin komutlarını kullanma izni. Varsayılan: OP |
| `aethelguard.bypass.iplimit` | IP kayıt limitini bypass eder. Varsayılan: OP |

---

## 📜 Lisans

Aethelguard, **Aethelguard Public Use License** ile paylaşılır.

Kısaca:

- ✅ Plugin kişisel, public veya ticari Minecraft sunucularında ücretsiz kullanılabilir.
- 📦 Orijinal ve değiştirilmemiş hali paylaşılabilir.
- 🔍 Kaynak kod inceleme, öğrenme ve güvenlik kontrolü için okunabilir.
- ⛔ Değiştirilmiş sürüm yayınlamak, fork dağıtmak veya kodu başka projede kullanmak yasaktır.
- 👑 Aethelguard’ın sahipliği ve telif hakları Aethelster’a aittir.

Detaylar için `LICENSE` dosyasını okuyabilirsin.
