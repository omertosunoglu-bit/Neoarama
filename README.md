# NeoArama — Android telefon (arama) uygulaması

Telefonun içinde çalışan bir arama uygulaması. Telefonun kendi rehberini, arama geçmişini, SIM kartlarını
ve arama altyapısını kullanır. **Varsayılan telefon uygulaması** yapıldığında gelen ve giden bütün aramalar
bu uygulamanın ekranında açılır.

## Özellikler

**Arama ekranı**
- Gelen aramada büyük **Cevapla / Reddet** düğmeleri, kilit ekranının üstünde de açılır
- **Mesajla reddet:** "Araç kullanıyorum, sonra ararım" gibi hazır mesajlarla tek dokunuşla reddetme
- Konuşma sırasında: Sessiz, Tuş takımı (menüler için), Hoparlör, Beklet, Arama ekle, **Bluetooth** (araç/kulaklık)
- İkinci arama gelince bekletme ve aramalar arasında geçiş
- Telefon kulağa yaklaşınca ekran kararır (yakınlık sensörü)
- Çift SIM'de "Hangi SIM ile?" seçimi
- Bildirimden cevaplama, reddetme ve kapatma

**Ana ekranlar**
- **Favoriler:** büyük fotoğraflı kartlar. Dokununca arar, basılı tutunca detayı açar. "Sık arananlar" bölümü otomatik oluşur
- **Son aramalar:** güne göre gruplu, cevapsızlar kırmızı, "Cevapsız" filtresi ve okunmamış rozeti
- **Kişiler:** Türkçe alfabetik sıralama, isim ya da numarayla arama
- **Tuş takımı:** yazarken rehberde arar (T9, Türkçe harfler dahil), tuş sesi ve titreşim.
  1–9 tuşlarına basılı tutunca sıradaki favoriyi hızlıca arar, 0'a basılı tutunca "+" yazar
- **Kişi detayı:** Ara, SMS, WhatsApp, numarayı kopyala, rehbere ekle/düzenle, **numara engelleme**, o kişiyle son aramalar
- Açık ve koyu tema (telefonun ayarına göre)

## 1) APK'yı oluşturun

### A) Bilgisayar olmadan (GitHub ile)
1. GitHub'da yeni bir depo oluşturun ve bu klasördeki tüm dosyaları yükleyin (gizli `.github` klasörü dahil).
2. **Actions** sekmesinde "APK oluştur" işi kendiliğinden başlar (5–8 dakika sürer).
3. İş bitince **Artifacts** bölümünden `NeoArama-apk` dosyasını indirin ve zip'i açın.

### B) Android Studio ile
**Open** → bu klasörü seçin → **Build → Build APK(s)**. APK şurada oluşur: `app/build/outputs/apk/debug/app-debug.apk`

## 2) Kurulum
1. APK'yı telefonda açıp kurun. "Bilinmeyen uygulamaları yükle" izni sorulursa verin.
2. NeoArama'yı açın ve istenen izinleri verin (rehber, arama geçmişi, telefon, bildirim).
3. Üstteki **Varsayılan yap** düğmesine dokunun ve NeoArama'yı seçin.

Eski telefon uygulamanıza dönmek isterseniz: **Ayarlar → Uygulamalar → Varsayılan uygulamalar → Telefon uygulaması**.

## Notlar
- Zil sesini ve titreşimi telefonun kendisi çalar. Sizin seçtiğiniz zil sesi aynen kalır.
- Telefon Android Auto ile araca bağlıyken aramalar aracın ekranında Android Auto'nun kendi arayüzüyle görünür; bu normaldir.
- Samsung, Xiaomi gibi bazı telefonlarda kilit ekranında açılması için **Ayarlar → Uygulamalar → NeoArama → Bildirimler**
  bölümünde "Tam ekran bildirimlere izin ver" seçeneğinin açık olması gerekebilir.

## Teknik
Kotlin · Jetpack Compose · Android Telecom `InCallService` · minSdk 26 (Android 8), targetSdk 35
