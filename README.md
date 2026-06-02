# Aethelguard

> **0.2-sentinel Release** - temiz loglar, güvenli auth akışı ve daha fazla özelleştirme için hazırlanmış tam 0.2 sürümü.

**Aethelguard**, Paper tabanlı Minecraft sunucuları için hazırlanmış, sade ama güçlü bir auth eklentisidir. Oyuncular sunucuya girdiğinde `/login` veya `/register` ile doğrulanana kadar güvenli bir bekleme alanında tutulur; giriş tamamlanınca da temiz ve kontrollü şekilde oyuna devam ederler.

Bu proje özellikle “oyuncu giriş yapmadan dünyaya, chate veya komutlara erişmesin; ama bunu yaparken sunucu logları da tertemiz kalsın” ihtiyacı için geliştirildi. ⚔️

## ✨ Öne Çıkanlar

- 🔐 `/register` ve `/login` tabanlı hesap sistemi
- 🧂 BCrypt ile güvenli şifre hashleme
- 💾 Local YAML veya MySQL depolama seçeneği
- 🌍 Giriş yapmamış oyuncuları void-zone alanında bekletme
- 📍 Login sonrası oyuncuyu son güvenli konumuna döndürme
- 🏠 İlk register sonrası oyuncuyu spawn noktasına gönderme
- 🧼 Login/register sonrası oyuncunun kendi chat ekranını temizleme
- 💬 Login ekranındaki oyuncuların normal chati görmesini engelleme
- 🚫 Auth olmadan hareket, hasar, chat, blok kırma/yerleştirme gibi aksiyonları kısıtlama
- 🔇 Vanilla join/quit, connection ve komut loglarını susturma
- 📝 Sadece başarılı login/register işlemleri için temiz Aethelguard logları basma
- 🎵 Başarılı giriş, kayıt ve hatalı şifre seslerini config üzerinden yönetme
- 🌐 `messages_<dil>.yml` sistemiyle özel dil dosyaları ekleme
- ⚙️ Geniş ve açıklamalı `config.yml` ile neredeyse her davranışı özelleştirme

## 📦 Kurulum

1. `aethelguard-0.2-sentinel.jar` dosyasını sunucunun `plugins` klasörüne at.
2. Sunucuyu başlat ve pluginin dosyalarını oluşturmasını bekle.
3. `plugins/Aethelguard/config.yml` dosyasını kendi sunucuna göre düzenle.
4. Mesajları değiştirmek istersen `plugins/Aethelguard/messages/` klasöründeki dil dosyalarını düzenle.
5. Database, storage veya ana auth davranışlarını değiştirdiysen sunucuyu yeniden başlat.

> Küçük not: Sadece mesaj değişikliklerinde genelde config/dil dosyalarını düzenlemek yeterlidir; database gibi temel ayarlar için restart önerilir.

## 🕹️ Komutlar

| Komut | Aliaslar | Ne işe yarar? |
| --- | --- | --- |
| `/register <şifre> <şifre tekrar>` | `/kayitol`, `/kayıtol` | Oyuncu için yeni bir hesap oluşturur. |
| `/login <şifre>` | `/giris`, `/giriş` | Daha önce kayıt olmuş oyuncuyu giriş yaptırır. |

Oyuncu giriş yapmadan önce yalnızca config içinde izin verilen komutları kullanabilir. Varsayılan olarak login ve register komutları açıktır.

## ⚙️ Config Rehberi

`config.yml`, pluginin neredeyse bütün davranışlarını düzenleyebilmen için kategorilere ayrılmıştır. Dosyanın içinde her ayarın yanında açıklama bulunur, ama genel mantık şöyle:

- `prefix`: Oyunculara giden Aethelguard mesajlarının başlığıdır.
- `default-language`: Oyuncu mesajlarının hangi dosyadan okunacağını belirler. Örneğin `DE` yazarsan plugin `messages_de.yml` dosyasını arar.
- `console-language`: Console ve plugin log metinlerinin dilidir. Şu anda sadece `en` ve `tr` desteklenir; özel dil dosyaları buraya uygulanmaz.
- `database`: MySQL bağlantısı, tablo adı ve HikariCP pool ayarlarını içerir.
- `local-logging`: Başarılı login/register kayıtlarını pluginin kendi log dosyasına yazmayı kontrol eder.
- `console-logging`: Vanilla connection loglarını ve Aethelguard auth loglarını açıp kapatmanı sağlar.
- `storage`: MySQL kapalıyken local kullanıcı dosyalarının hangi klasörde tutulacağını belirler.
- `auth-settings`: Void-zone, efektler, prompt mesajları, timeout, kısıtlamalar, izinli komutlar, hatalı şifre davranışı ve ses ayarlarını içerir.

Bu yapı sayesinde plugin sadece “kur ve kullan” şeklinde değil, farklı sunucu tiplerine göre rahatça uyarlanabilecek şekilde tasarlandı.

## 🌐 Dil Dosyaları

Aethelguard varsayılan olarak şu dil dosyalarını oluşturur:

- `messages_tr.yml`
- `messages_en.yml`

Ekstra dil eklemek istersen yeni bir dosya oluşturabilirsin. Örneğin Almanca için:

1. `plugins/Aethelguard/messages/messages_de.yml` dosyasını oluştur.
2. İçine `messages_en.yml` veya `messages_tr.yml` içindeki tüm keyleri ekle.
3. Mesajları istediğin dile çevir.
4. `config.yml` içinde `default-language: "DE"` yap.
5. Sunucuyu yeniden başlat.

`default-language` oyuncuya giden mesajlar içindir ve özel dil dosyalarını okuyabilir. `console-language` ise yalnızca console/plugin logları içindir; bu alan özel `messages_*.yml` dosyalarını kullanmaz.

## 💾 Local ve MySQL Depolama

Database kullanmak istemiyorsan `database.enabled: false` bırakabilirsin. Bu durumda oyuncu hesapları local YAML dosyalarında saklanır:

```text
plugins/Aethelguard/users/
```

MySQL kullanmak istersen `database.enabled: true` yapıp host, port, database, username ve password bilgilerini doldurman yeterlidir. Tablo adı `database.table-name` ile değiştirilebilir.

Tablo adı güvenlik nedeniyle yalnızca harf, sayı ve alt çizgi içermelidir.

## 🧭 Auth Akışı

Oyuncu sunucuya girdiğinde Aethelguard önce oyuncunun güvenli konumunu hatırlar. Eğer void-zone sistemi açıksa oyuncu geçici olarak auth alanına alınır ve login/register tamamlanana kadar kısıtlı modda kalır.

Başarılı login sonrasında:

- Oyuncunun auth efektleri temizlenir.
- Chat ekranı temizlenir.
- Başarılı giriş mesajı gönderilir.
- Oyuncu son güvenli konumuna döndürülür.

İlk register sonrasında:

- Oyuncu otomatik olarak giriş yapmış kabul edilir.
- Chat ekranı temizlenir.
- Başarılı kayıt mesajı gönderilir.
- Oyuncu spawn noktasına yönlendirilir.

Oyuncu login/register yapmadan çıkarsa veya timeout yerse plugin void-zone konumunu son konum olarak kaydetmez. Böylece oyuncu sonraki girişinde yanlışlıkla auth alanından düşmez.

## 🔊 Ses Sistemi

Sesler config üzerinden hem toplu hem de tek tek yönetilebilir. İstersen bütün sesleri tek ayarla kapatabilir, istersen sadece belirli eventlerin sesini değiştirebilirsin.

Desteklenen temel ses olayları:

- Başarılı login
- Başarılı register
- Hatalı şifre

Her ses için `enabled`, `sound`, `volume`, `pitch`, `repeat-times` ve `repeat-interval-ticks` gibi ayarlar bulunur. Bu sayede kısa tek bir efekt de yapabilirsin, birkaç kez tekrar eden daha belirgin bir efekt de.

## 📝 Log Sistemi

Aethelguard 0.2-sentinel ile log tarafı daha temiz hale getirildi. Giriş yapmamış oyuncuların `/login` veya `/register` komutları console'a şifreli komut olarak düşmez.

Plugin ayrıca istersen vanilla connection loglarını susturabilir:

- `joined the game`
- `left the game`
- `logged in with entity id`
- `lost connection`
- `UUID of player`
- `issued server command`

Bunun yerine başarılı login/register işlemlerini Aethelguard formatında görebilirsin. Böylece console daha az kalabalık olur ve gerçekten önemli auth olayları daha rahat okunur.

## 🚀 0.1'den 0.2-sentinel'e Neler Değişti?

0.2-sentinel sürümünün ana hedefi pluginin daha güvenli, daha temiz ve daha fazla özelleştirilebilir hale gelmesiydi.

### Yeni Gelenler

- Config dosyası baştan düzenlendi ve açıklamalı kategorilere ayrıldı.
- Çok daha fazla ayar config üzerinden aç/kapa yapılabilir hale geldi.
- Login/register komutlarının console'a şifreyle düşmesi engellendi.
- Vanilla join/quit/connection loglarını susturma sistemi eklendi.
- Başarılı login/register için temiz Aethelguard logları eklendi.
- Login ekranındaki oyuncuların normal chati görmesi engellendi.
- Login/register sonrası oyuncunun kendi chat ekranını temizleme eklendi.
- Ses sistemi genişletildi ve her ses için ayrı ayarlar eklendi.
- Local users klasörü ve internal log klasörü config üzerinden ayarlanabilir hale geldi.
- MySQL tablo adı ve connection pool ayarları config'e taşındı.
- Custom language dosyaları için destek ve açıklamalar netleştirildi.

### Düzeltilenler

- Auth olmadan çıkan veya timeout yiyen oyuncuların konumunun void-zone olarak kaydedilmesi engellendi.
- Oyuncuların tekrar girişte auth alanından düşme sorunu çözüldü.
- İlk register sonrası oyuncunun olduğu yerde kalması yerine spawn'a yönlendirilmesi sağlandı.
- Logların gereksiz kalabalıklaşmasına sebep olan vanilla mesajlar filtrelendi.
- Seslerin çok kısa veya fark edilmesi zor olabildiği durumlar için tekrar ayarları eklendi.

### 0.1'e Göre Fark

0.1 daha çok temel auth sistemi gibi çalışıyordu. 0.2-sentinel ise sunucu sahiplerinin plugin davranışını kendi ihtiyaçlarına göre ayarlayabileceği daha olgun bir sürüm oldu. Artık loglar daha temiz, config daha anlaşılır, dil sistemi daha esnek ve auth akışı daha güvenli.

## 🤝 Katkı ve Geri Bildirim

Bir hata bulursan, yeni özellik fikrin varsa veya config tarafında daha fazla özelleştirme gerektiğini düşünüyorsan projeye katkı verebilirsin. Aethelguard'ın amacı küçük sunuculardan daha düzenli yapılara kadar herkesin rahatça kullanabileceği temiz bir auth deneyimi sunmak.

## 📜 License

Aethelguard, **Aethelguard Public Use License** ile paylaşılır.

Kısaca:

- Plugin kişisel, public veya ticari Minecraft sunucularında ücretsiz kullanılabilir.
- Orijinal ve değiştirilmemiş hali paylaşılabilir.
- Kaynak kod inceleme, öğrenme ve güvenlik kontrolü için okunabilir.
- Değiştirilmiş sürüm yayınlamak, fork dağıtmak veya kodu başka projede kullanmak yasaktır.
- Aethelguard'ın sahipliği ve telif hakları Aethelster'a aittir.

Detaylar için `LICENSE` dosyasını okuyabilirsin.
