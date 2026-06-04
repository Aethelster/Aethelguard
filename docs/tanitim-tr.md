# Aethelguard v0.3-sentinel
## Adaptive Security + Recovery Update

Selamlar! Uzun süredir geliştirdiğim **Aethelguard** adlı Paper tabanlı auth ve güvenlik eklentisini tanıtmak istiyorum.

Aethelguard'ın amacı basit: Oyuncular sunucuya girdiğinde güvenli şekilde doğrulansın, login/register olmadan dünyaya karışamasın, console gereksiz vanilla loglarla dolmasın ve sunucu sahipleri neredeyse her davranışı config üzerinden rahatça yönetebilsin.

Bu sürümün adı:

**Aethelguard v0.3-sentinel - Adaptive Security + Recovery Update**

Bu update, Aethelguard'ı klasik login/register eklentisi çizgisinden çıkarıp captcha, 2FA, recovery, adaptive security, VPN/proxy kontrolü ve gelişmiş config sistemiyle daha kapsamlı bir auth güvenlik paketine dönüştürüyor.

---

## Ana Özellikler

- `/register`, `/login`, `/changepassword` hesap sistemi
- BCrypt ile güvenli şifre hashleme
- Local YAML veya MySQL depolama
- Captcha sistemi: MAP, TEXT, NUMERIC, ALPHANUMERIC, MATH
- Harita üstünde captcha kodu gösterimi
- TOTP tabanlı 2FA desteği
- Güvenlik sorusu ve backup code ile hesap kurtarma
- Adaptive security sistemi
- VPN/proxy/Tor/hosting IP kontrolü
- Session auto-login
- Gelişmiş şifre politikası
- Hassas güvenlik komutları için cooldown sistemi
- Auth sırasında envanter, zırh ve offhand gizleme
- Auth aşamasına göre bossbar yönlendirmeleri
- Login ekranındaki oyuncuların chati görmesini engelleme
- Vanilla join/quit/connection/command loglarını susturma
- Türkçe/İngilizce mesaj dosyaları ve özel dil desteği
- Eksik config ayarlarını commentleriyle doğru yere ekleyen config sync sistemi

---

## Adaptive Security

Aethelguard, oyuncunun giriş riskini değerlendirebilir ve auth akışını buna göre değiştirebilir.

Örneğin:

- Aynı IP'den güvenli giriş geçmişi varsa captcha bypass verilebilir.
- Şüpheli IP tespit edilirse ekstra captcha uygulanabilir.
- VPN/proxy/Tor/hosting sinyalleri kontrol edilebilir.
- Aynı IP'den çok fazla hesap açılmışsa oyuncu riskli sayılabilir.
- Çok fazla yanlış şifre denemesi varsa güvenlik seviyesi artabilir.

Bu sayede her oyuncuya aynı sert güvenlik akışı uygulanmak zorunda kalmaz. Güvenilir oyuncular daha hızlı girerken, şüpheli bağlantılar daha dikkatli kontrol edilir.

---

## VPN / Proxy Detection

Aethelguard v0.3-sentinel ile VPN/proxy kontrol sistemi eklendi.

Desteklenen providerlar:

- IPWHOIS
- IPAPI

Kontrol edilen sinyaller:

- VPN
- Proxy
- Tor
- Hosting/datacenter

Sistem cache ve timeout ayarlarına sahiptir. Bu sayede aynı IP sürekli API'ye gönderilmez ve giriş ekranı gereksiz uzun beklemez. API'ler cevap vermezse ne yapılacağı da config üzerinden belirlenebilir.

---

## Account Recovery

Oyuncular hesabını kaybettiğinde güvenli şekilde geri alabilir.

Desteklenen yöntemler:

- Güvenlik sorusu
- Tek kullanımlık backup code

Komut örnekleri:

```text
/securityquestion setup
/securityquestion answer <cevap>
/backupcodes generate
/recoverymethod question
/recoverymethod backup-code
/recover question <cevap> <yeniŞifre>
/recover code <yedekKod> <yeniŞifre>
```

Adminler isterse güvenlik sorusu veya backup code sistemini ayrı ayrı configten kapatabilir.

Güvenlik soruları da mesaj sistemi gibi dil dosyalarından okunur:

- `security_questions_tr.yml`
- `security_questions_en.yml`

---

## 2FA / Authenticator

Aethelguard TOTP standardını kullanır.

Uyumlu uygulamalar:

- Google Authenticator
- Microsoft Authenticator
- Authy
- TOTP destekleyen benzer uygulamalar

Komutlar:

```text
/2fa setup
/2fa confirm <kod>
/2fa disable <kod>
/2fa <kod>
```

2FA açık oyuncularda captcha sonrası direkt `/2fa <kod>` akışına geçilebilir. Böylece oyuncu arka arkaya captcha + login + 2FA yapmak zorunda kalmaz.

---

## Captcha Sistemi

Login/register öncesi captcha doğrulaması eklendi.

Desteklenen captcha türleri:

- MAP
- TEXT
- NUMERIC
- ALPHANUMERIC
- MATH

Captcha sistemi; cooldown, deneme hakkı, kick davranışı, map item yönetimi ve başarı sesi gibi ayarlarla özelleştirilebilir.

Captcha deneme hakkı, login/register yanlış şifre denemesinden ayrıdır. Böylece captcha için ayrı, login için ayrı güvenlik kuralları kullanılabilir.

---

## Şifre Politikası

Şifre kuralları config üzerinden detaylıca yönetilebilir.

Configten ayarlanabilenler:

- Minimum şifre uzunluğu
- Maximum şifre uzunluğu
- Harf zorunluluğu
- Sayı zorunluluğu
- Kullanıcı adının şifrede geçmesini engelleme
- Türkçe karakter kullanımını aç/kapat
- Noktalama işareti kullanımını aç/kapat
- Emoji, özel font ve normal alfabe dışı sembolleri aç/kapat
- Yasaklı kelime listesi

Bu kurallar şu akışlarda ortak çalışır:

- `/register`
- `/changepassword`
- `/recover`
- `/aethelguard changepassword`

---

## Config ve Dil Sistemi

Aethelguard config dosyasını açılışta ve reload sırasında kontrol eder.

Sistem şunları yapar:

- Eksik ayarları ekler.
- Yeni ayarları commentleriyle doğru bölüme koyar.
- Yanlış yerdeki bilinen ayarları doğru yere taşır.
- Mevcut değerleri korur.
- Eski config kullanan sunucuların geçişini kolaylaştırır.

Mesajlar şu dosyalardan yönetilebilir:

- `messages_tr.yml`
- `messages_en.yml`
- Özel `messages_<code>.yml`

Console dili de ayrıca ayarlanabilir:

- `console-language: en`
- `console-language: tr`
- `console-text-mode: native`
- `console-text-mode: ascii`

Türkçe karakter desteklemeyen paneller için `ascii` modu kullanılabilir.

---

## Komutlar

Oyuncu komutları:

```text
/register <şifre> <şifre tekrar>
/login <şifre>
/captcha <kod>
/changepassword <eski> <yeni> <yeni tekrar>
/2fa setup
/2fa confirm <kod>
/2fa disable <kod>
/securityquestion setup
/securityquestion answer <cevap>
/backupcodes generate
/recoverymethod question
/recoverymethod backup-code
/recover question <cevap> <yeniŞifre>
/recover code <yedekKod> <yeniŞifre>
```

Admin komutları:

```text
/aethelguard reload
/aethelguard status <oyuncu>
/aethelguard sessions
/aethelguard session <oyuncu>
/aethelguard clearsession <oyuncu>
/aethelguard clearsessions
/aethelguard unregister <oyuncu>
/aethelguard changepassword <oyuncu> <yeniŞifre>
/aethelguard unlogin <oyuncu>
```

---

## 0.2-sentinel'den 0.3-sentinel'e Ne Değişti?

`0.2-sentinel`, temiz login/register akışı ve console log düzeni üzerine kuruluydu.

`0.3-sentinel` ise auth sistemini daha akıllı, daha güvenli ve daha yönetilebilir hale getiriyor.

Kısaca:

- Captcha yoktu, captcha sistemi geldi.
- 2FA yoktu, authenticator desteği geldi.
- Recovery yoktu, security question + backup code geldi.
- VPN kontrolü yoktu, VPN/proxy detection geldi.
- Adaptive security yoktu, risk bazlı captcha davranışı geldi.
- Basit şifre kontrolü vardı, gelişmiş password policy geldi.
- Hassas güvenlik cooldownları eklendi.
- Config sistemi commentli ve sıralı auto-sync yapısına taşındı.
- Console Türkçe modu `native/ascii` ayrımıyla iyileştirildi.
---

## Gelecek Planları

Aethelguard için uzun vadede daha büyük bir güvenlik ve yönetim ekosistemi hedefliyorum.

Planlanan fikirlerden bazıları:

### PIN ile Giriş Sistemi

Login/register akışına alternatif olarak PIN ile giriş sistemi planlanıyor. Sunucu adminleri default giriş yöntemini seçebilecek, oyuncular da kendi hesapları için PIN veya klasik şifre sistemi arasında tercih yapabilecek.

### Oyuncu Ayarları GUI'si

Oyuncuların hesap güvenliği ayarlarını kolayca yönetebilmesi için oyun içi GUI planlanıyor.

Örnekler:

- Şifre değiştirme
- 2FA açma/kapatma
- Auto-login açma/kapatma
- PIN ayarları
- Login/register veya PIN tercihi
- Recovery yöntemi yönetimi

### Yetkili Komutları ve Moderasyon GUI'si

`/ban`, `/kick`, `/mute`, `/tempban` gibi yetkili komutları gelecek güncellemelerde oyuna eklendikten sonra daha kolay kullanmak için GUI tabanlı bir sistem planlanıyor.

Örneğin bir oyuncuya özel GUI açılıp tek tıkla ban, kick, mute veya süreli ceza uygulanabilecek. Süreli cezalar chatten veya GUI içinden seçilebilecek.

### Discord Ticket Entegrasyonu

Yetkililer hakkında şikayetleri olan oyuncular için Discord ile entegre, configten açılıp kapatılabilir bir ticket sistemi planlanıyor.

### Resource Pack Plugin Entegrasyonları

ItemsAdder, Nexo, Oraxen gibi custom resource-pack pluginleriyle daha kolay uyum hedefleniyor.

Planlanan fikirler:

- GUI itemlerini custom model data ile değiştirme
- Custom GUI itemleri
- Resource-pack destekli daha şık auth/recovery ekranları
- 2FA için QR gösterimi üzerine denemeler

### Permission Plugin Entegrasyonları

LuckPerms gibi permission yönetici pluginleriyle daha kolay çalışma hedefleniyor.

Planlanan fikir:

- Yetkili permissionlarını belirli parent gruplara kolayca ekleyen komut/dosya sistemi
- Adminlerin yetki kurulumunu daha hızlı yapabilmesi

### Web Panel

En büyük uzun vadeli fikirlerden biri özel web panel.

Planlanan panel özellikleri:

- Oyuncu profilleri
- Son giriş/çıkış bilgileri
- Timeout, mute, ban geçmişi
- Hangi yetkili tarafından ceza verildiği
- Oyuncunun aktif olduğu günler ve saatler
- Ortalama oynama saatleri
- Son 1 ayda yaşanan olayların özeti
- Yetkili profilleri
- Yetkililerin yaptığı işlemler
- Yetkili giriş/çıkış ve aktiflik raporları
- Yetkililer için gelen şikayetler
- Staff performans raporları
- "Ayın Yetkilisi" tarzı motivasyon sıralaması

Bu sistemin amacı sadece ceza kaydı tutmak değil; sunucu sahiplerinin yetkili performansını, oyuncu güvenliğini ve topluluk yönetimini daha net görebilmesini sağlamak.

---

## Kısa Özet

Aethelguard, klasik login/register eklentisi olmaktan ziyade captcha, 2FA, recovery, VPN kontrolü, adaptive security ve gelişmiş config sistemiyle daha kapsamlı bir auth güvenlik paketi olmayı hedefliyor.

GitHub / Download:

```text
https://github.com/Aethelster/Aethelguard
```

Her türlü fikir, hata bildirimi ve öneriye açığım.
