# 🛡️ Aethelguard

**Aethelguard**, Paper tabanlı Minecraft sunucuları için geliştirilen, temiz loglara ve yüksek özelleştirme gücüne odaklanan modern bir auth ve hesap güvenliği eklentisidir.

Oyuncular sunucuya girdiğinde doğrulama tamamlanana kadar kontrollü bir auth alanında tutulur. Captcha, login/register, PIN, 2FA veya recovery adımı bitmeden oyuncu dünyaya, chate, envanterine ve riskli komutlara erişemez. Auth tamamlandığında envanteri geri gelir, bossbar temizlenir ve oyuncu güvenli konumuna döner.

> Amaç basit: güçlü auth, temiz console, anlaşılır config ve adminin her şeyi rahatça yönetebildiği bir güvenlik sistemi.

## ✅ Güncel Sürüm

**Latest stable:** `v0.4-sentinel`

`v0.4-sentinel`, Aethelguard’ın final ücretsiz sürümüdür. `v0.3-sentinel` üstüne PIN sistemi, PIN GUI, diagnostics, daha sağlam timeout/reload akışı, güvenlik sorusu zorunluluğu ve birçok polish/fix eklenmiştir.

> ⚠️ **Önemli uyarı:** `v0.4-sentinel` öncesindeki sürümler artık önerilmez. Eski `dev` buildlerde ve önceki sentinel sürümlerde auth akışı, timeout, local YAML password kaydı, 2FA QR linkleri veya config/messages tarafında düzeltilmiş hatalar bulunabilir. Yeni kurulumlarda ve canlı sunucularda doğrudan **`v0.4-sentinel`** kullanmanız tavsiye edilir.

## ✨ Öne Çıkan Özellikler

- 🔐 Password auth: `/register`, `/login`, `/changepassword`
- 🔢 PIN auth: `/pin`, `/setpin`, `/changepin`, `/authmode`
- 🧩 Hardcoded PIN GUI keypad ve tema sistemi
- 🛡️ Captcha: `MAP`, `TEXT`, `NUMERIC`, `ALPHANUMERIC`, `MATH`
- 🗺️ Harita captcha, slot kilidi ve auth sırasında envanter koruması
- 📱 TOTP 2FA: Google Authenticator, Microsoft Authenticator, Authy uyumlu
- 🔗 2FA QR kurulumu için tıklanabilir QR bağlantısı
- 🧠 Adaptive security: güvenilir IP captcha bypass, şüpheli IP ekstra captcha
- 🌐 VPN/proxy kontrolü ve manuel şüpheli IP listesi
- ❓ İlk kayıt sonrası güvenlik sorusu zorunluluğu
- 🧾 Security question ve backup code ile account recovery
- 🔁 Session auto-login
- ⏳ Temporary auth lockout ve attempt yönetimi
- 🔑 Şifre politikası: uzunluk, kullanıcı adı engeli, yasak kelime listesi, Türkçe karakter, noktalama ve özel karakter ayarları
- 💾 Local YAML veya MySQL depolama
- 📊 `/aethelguard status`, `ipinfo`, `accounts`, `sessions`
- 🧪 `/aethelguard diagnostics` ve diagnostic dump desteği
- 🧹 Vanilla join/quit/connection/command loglarını susturma
- 🖥️ Console dili: `en`, `tr native`, `tr ascii`
- 🌍 `messages_<code>.yml` dil sistemi
- ⚙️ Eksik config keylerini otomatik ekleyen ve layout düzelten config sync sistemi
- 💬 Auth olmamış oyuncuların chat görmesini/konuşmasını engelleme
- 📌 Auth aşamasına göre bossbar yönlendirmeleri

## 📦 Kurulum

1. GitHub Releases üzerinden `aethelguard-0.4-sentinel.jar` dosyasını indir.
2. JAR dosyasını sunucunun `plugins` klasörüne at.
3. Sunucuyu başlat.
4. `plugins/Aethelguard/config.yml` dosyasını düzenle.
5. Mesajları değiştirmek için `plugins/Aethelguard/messages/` klasörünü kullan.
6. Güvenlik sorularını değiştirmek için `plugins/Aethelguard/security_questions/` klasörünü düzenle.

Çoğu ayar reload ile yenilenebilir:

```text
/aethelguard reload
```

Database ve bazı temel auth ayarlarında sunucu restartı daha sağlıklı olabilir.

## 🎮 Oyuncu Komutları

| Komut | Açıklama |
| --- | --- |
| `/register <şifre> <şifre tekrar>` | Password ile hesap oluşturur. |
| `/login <şifre>` | Password ile giriş yapar. |
| `/captcha <kod>` | Captcha doğrulamasını tamamlar. |
| `/pin <pin>` | PIN ile giriş yapar. |
| `/setpin <pin> <pin>` | İlk PIN kaydını oluşturur. |
| `/changepin <eskiPin> <yeniPin> <yeniPinTekrar>` | PIN değiştirir. |
| `/authmode <password|pin>` | Hesabın giriş yöntemini değiştirir. |
| `/changepassword <eskiŞifre> <yeniŞifre> <yeniŞifreTekrar>` | Oyuncunun kendi şifresini değiştirir. |
| `/twofactor <kod>` | Login sırasında 2FA kodunu doğrular. |
| `/twofactor setup` | Authenticator kurulumunu başlatır. |
| `/twofactor confirm <kod>` | 2FA kurulumunu tamamlar. |
| `/twofactor disable <kod>` | 2FA korumasını kapatır. |
| `/securityquestion setup` | Güvenlik sorusu oluşturur. |
| `/securityquestion answer <cevap>` | Güvenlik sorusu cevabını kaydeder. |
| `/backupcodes generate` | Recovery için tek kullanımlık backup code üretir. |
| `/recoverymethod question` | Recovery yöntemini güvenlik sorusu yapar. |
| `/recoverymethod backup-code` | Recovery yöntemini backup code yapar. |
| `/recover question <cevap> <yeniŞifre>` | Güvenlik sorusuyla şifre sıfırlar. |
| `/recover code <kod> <yeniŞifre>` | Backup code ile şifre sıfırlar. |

## 👑 Admin Komutları

| Komut | Açıklama |
| --- | --- |
| `/aethelguard reload` | Config, mesajlar ve bağlantıları yeniler. |
| `/aethelguard status <oyuncu>` | Oyuncunun auth durumunu ve kayıt detaylarını gösterir. |
| `/aethelguard ipinfo <oyuncu|ip>` | IP ile ilişkili hesapları ve limit bilgisini gösterir. |
| `/aethelguard accounts <oyuncu|ip>` | Aynı IP’ye bağlı hesapları listeler. |
| `/aethelguard sessions` | Aktif auth session listesini gösterir. |
| `/aethelguard session <oyuncu>` | Bir oyuncunun session bilgisini gösterir. |
| `/aethelguard clearsession <oyuncu>` | Oyuncunun session kaydını temizler. |
| `/aethelguard clearsessions` | Tüm session kayıtlarını temizler. |
| `/aethelguard diagnostics` | Genel sistem teşhisi gösterir. |
| `/aethelguard diagnostics player <oyuncu>` | Oyuncu özelinde teşhis çıktısı verir. |
| `/aethelguard diagnostics config` | Config risklerini kontrol eder. |
| `/aethelguard diagnostics dump` | Diagnostic rapor dosyası oluşturur. |
| `/aethelguard pingui preview [tema]` | PIN GUI temasını önizler. |
| `/aethelguard pingui themes` | Mevcut PIN GUI temalarını listeler. |
| `/aethelguard unregister <oyuncu>` | Oyuncunun auth kaydını siler. |
| `/aethelguard changepassword <oyuncu> <yeniŞifre>` | Admin olarak oyuncu şifresi değiştirir. |
| `/aethelguard unlogin <oyuncu>` | Oyuncuyu oyundan atmadan tekrar auth ekranına alır. |

Admin aliasları:

```text
/ag
/aeg
/ayarlar
```

## ⚙️ Config ve Dil Sistemi

Aethelguard’ın ayarları `config.yml` üzerinden yönetilir. Dosyada kategorili açıklamalar bulunur ve yeni sürümlerde eksik keyler otomatik eklenir.

Öne çıkan config alanları:

- `default-language`: Oyuncu mesaj dili. `messages_<code>.yml` mantığıyla yeni dil dosyası eklenebilir.
- `console-language`: Console dili. Sadece `en` ve `tr` desteklenir.
- `console-text-mode`: Türkçe console için `native` veya `ascii`.
- `database`: MySQL veya local YAML depolama.
- `auth-settings`: Captcha, PIN, 2FA, timeout, session, bossbar, sounds ve restriction ayarları.
- `adaptive-security`: IP güveni, şüpheli IP, VPN/proxy kontrolü ve ekstra captcha.
- `recovery`: Security question, backup code ve recovery davranışı.
- `security-cooldowns`: Hassas komutlar için cooldown ayarları.
- `status`: Local user snapshot ve admin status sistemi.
- `diagnostics`: Admin diagnostic ve dump ayarları.
- `console-logging`: Vanilla connection loglarını susturma ve auth state logları.

## 🔐 Auth Akışı

Oyuncu sunucuya girdiğinde:

1. Gerekirse captcha doğrulaması ister.
2. Hesabın moduna göre password veya PIN ister.
3. 2FA aktifse authenticator kodu ister.
4. İlk register sonrası güvenlik sorusu zorunluysa cevap ister.
5. Başarılı olursa session oluşturur, inventory geri verir ve oyuncuyu güvenli konuma taşır.

2FA hesabı için default akış:

```text
30s captcha + 60s 2FA code + 30s security question
```

Normal auth akışı default olarak toplam 90 saniyelik timeout kullanır.

## 🧩 PIN GUI

`v0.4-sentinel` ile PIN sistemi artık sadece komut tabanlı değil, GUI desteklidir.

- 4 haneli sabit PIN
- Tema desteği: quartz, forest-green, pumpkin, netherite, monitor-green, monitor-red
- Gizli veya görünür digit gösterimi
- Random number layout seçeneği
- Confirm, backspace, clear ve exit tuşları
- GUI kapatma spam koruması
- Özel bossbar ve ses efektleri

PIN GUI ücretsiz sürümde bilinçli olarak sade ve hardcoded tutulmuştur.

## 🧠 Adaptive Security

Adaptive security sistemi oyuncunun giriş davranışını değerlendirir.

- Aynı IP’den güvenli tekrar girişlerde captcha bypass uygulanabilir.
- Şüpheli IP veya VPN/proxy durumunda ekstra captcha istenebilir.
- Admin manuel şüpheli IP listesi kullanabilir.
- Ekstra captcha normal captcha tipinden farklı seçilebilir.

Bu sistem, oyuncuyu gereksiz doğrulamayla yormadan riskli girişleri daha sıkı kontrol etmek için tasarlanmıştır.

## 🧾 Recovery Sistemi

Oyuncular hesaplarını şu yollarla kurtarabilir:

- Güvenlik sorusu
- Backup code
- Admin şifre değiştirme

İlk register sonrası güvenlik sorusu zorunluysa oyuncu soruyu cevaplamadan sunucuya alınmaz. Oyuncu bu aşamada timeout yerse, sonraki başarılı login/PIN/2FA sonrası aynı güvenlik sorusu prosedürü tekrar başlar.

## 🧹 Temiz Console

Aethelguard, Paper/vanilla tarafındaki şu logları isteğe bağlı susturabilir:

- joined the game
- left the game
- logged in with entity id
- lost connection
- issued server command

Bunun yerine auth durumlarına özel daha anlamlı loglar üretir:

- login success
- register success
- auto-login success
- 2FA success
- timeout kick
- unauthenticated quit
- authenticated quit

Console dili İngilizce veya Türkçe olabilir. Türkçe console için `native` ve `ascii` modu bulunur.

## 🆕 v0.3-sentinel Sonrası Neler Eklendi?

`v0.4-sentinel`, `v0.3-sentinel` üzerine final ücretsiz paket olarak geldi.

Yeni ana özellikler:

- PIN login sistemi
- PIN kayıt sistemi
- PIN değiştirme
- Password/PIN auth mode değiştirme
- PIN GUI keypad
- PIN GUI tema sistemi
- PIN GUI preview komutları
- Admin diagnostics sistemi
- Diagnostic dump
- `/ipinfo` ve `/accounts`
- Daha detaylı `/status`
- Reload sırasında config/messages backup altyapısı
- Temporary auth lockout
- İlk register sonrası zorunlu güvenlik sorusu
- Chatten captcha ve güvenlik sorusu cevabı girebilme
- 2FA QR link polish
- 2FA özel başarı sesi
- Auth timeout polish
- PIN GUI close spam kick
- Captcha inventory/slot/offhand koruması
- MAP captcha cleanup iyileştirmeleri
- Config layout sync düzeltmeleri
- Messages ve MiniMessage cleanup
- 0.4 final release README cleanup

Önemli fixler:

- Local YAML password hash artık `password.hash` altında saklanır.
- `password.usable` artık hash değerini ezmez.
- Eski local password hash formatı için migration desteği eklendi.
- Captcha sonrası `/login` etkisiz kalma problemi düzeltildi.
- Timeout görevlerinin eski deadline ile yanlış kick/log atması engellendi.
- Kick ekranlarında MiniMessage taglerinin ham görünmesi düzeltildi.
- URL içindeki `&` karakterlerinin renk kodu gibi bozulması engellendi.

## 🧱 Depolama

İki depolama modu vardır:

- **Local YAML:** Küçük/orta sunucular ve hızlı kurulum için.
- **MySQL:** Daha kalıcı ve merkezi depolama isteyen sunucular için.

Local modda kullanıcı dosyaları UUID bazlı tutulur. Adminlerin isim/UUID eşleştirmesini kolay görmesi için user index sistemi bulunur.

## 📌 Desteklenen Platform

Aethelguard Paper API ile geliştirilmiştir.

Önerilen:

- Paper
- Purpur
- Paper tabanlı forklar

Hedef API:

```text
Minecraft / Paper API 1.21.x
Java 21
```

Spigot/Bukkit tarafında bazı Paper API davranışları eksik olabileceği için önerilen platform Paper veya Purpur’dur.

## 📄 Lisans

Bu proje `LICENSE` dosyasındaki özel lisansla paylaşılır.

Kısaca:

- Kullanılabilir.
- Paylaşılabilir.
- Kaynak koddan türev/geliştirilmiş sürüm üretilemez.
- Lisans metni korunmalıdır.

## 💬 Kapanış

Aethelguard, ücretsiz sürümde temiz ve güçlü bir auth deneyimi sunmayı hedefler. `v0.4-sentinel`, bu ücretsiz çizginin final sürümüdür: password, PIN, captcha, 2FA, recovery, diagnostics ve adaptive security tek pakette toplanmıştır.

İyi kullanımlar. ✨
