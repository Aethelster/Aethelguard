# Aethelguard Jar Backups

Bu klasor eski surum jarlarini guvenli sekilde ayri tutmak icindir.

Kural:
- Her build sonrasi Codex, backup'a kopyalama yapmadan once sorar.
- Soruda mutlaka jar surumu yazilir.
- Kullanici onay verirse jar bu klasore eklenir.
- Kullanici onay vermezse jar backup'a kopyalanmaz.
- Eski backup jarlar silinmez.
- Kullanici onay verirse o build icin bir txt fark dosyasi da olusturulur.
- Txt dosyasinda sadece onceki surumden bu surume gelen farklar yazilir.
- Ayni surum ismiyle tekrar build alinirsa mevcut txt analiz edilir ve yeni farklar uzerine eklenir.

Onerilen duzen:

```text
jar-backups/
  main-jars/
    aethelguard-0.3-sentinel.jar
    aethelguard-0.3.1-dev.jar
  original-jars/
    original-aethelguard-0.3-sentinel.jar
    original-aethelguard-0.3.1-dev.jar
  aethelguard-0.3.1-dev-changes.txt
```
