# Evrak
[<img width="220" height="66" src="https://github.com/user-attachments/assets/7c2fadad-e134-4566-a5cb-9f4a0f32732a" />](https://play.google.com/store/apps/details?id=com.avalibeyaz.evrak)


**Evrak**, avukatların ve arabulucuların günlük iş akışında en sık karşılaştığı dosya formatlarını **-UDF, TIFF, PDF, DOCX, DOC, HTML, TXT, JPG, GIF, PNG-** tek bir uygulama üzerinden basit, sade ve hızlı bir şekilde görüntülemesini sağlayan ücretsiz ve reklamsız bir Android uygulamasıdır.

UYAP, CELSE, e-arabulucu, e-Adalet vb. uygulamalar üzerinden veya e-posta/Whatsapp vb. mesajlaşma uygulamaları aracılığıyla gelen evrakları açmak için genellikle birden fazla farklı uygulamaya ihtiyaç duyulur. Evrak Android uygulaması, bu ihtiyacı tek bir yerde, gereksiz karmaşıklık olmadan çözer.  

Ayrıca Evrak Android uygulaması; özellikle UDF'den dönüştürülen PDF dosyalarındaki Türkçe karakterlerin görüntülenememe sorununu ve UDF dosyalarının mobil cihazlarda görüntülenirken bozulması sorununu da ortadan kaldırır.

## Özellikler

- **Çoklu format desteği:** UDF, TIFF, PDF, DOCX, DOC, HTML, TXT, JPG, GIF, PNG
- **Basit ve hızlı:** Dosyayı aç, görüntüle - başka hiçbir şeye gerek yok
- **Paylaşma ve kaydetme:** Görüntülenen dosyalar istenilen yere kaydedilebilir veya doğrudan başka uygulamalarla paylaşılabilir
- **UDF, TIFF, DOCX, DOC ve HTML dosyalarını PDF'e çevirme:** Paylaşma ve kaydetme esnasında istenirse bu dosyalar PDF'e çevrilebilir
- **Sorunsuz Türkçe karakterler:** Özellikle UDF'den dönüştürülen PDF dosyalarındaki Türkçe karakterlerin görüntülenememe sorunu yok
- **Yazdırma:** Bütün dosya türleri, Android'in kendi yazdırma özelliği ile doğrudan cihazınızdan yazdırılabilir
- **Geçmiş:** Açılan dosyalar tarih sırasına göre uygulama içinde listelenir
- **Filtreleme:** Geçmişteki dosyaları dosya türüne göre filtreleyebilirsiniz
- **Yeniden adlandırma ve silme:** Geçmişteki dosyalar daha kolay hatırlanabilmeleri için istenildiğinde yeniden adlandırılabilir veya silinebilir
- **Sistem entegrasyonu:** "Birlikte aç" ve "Paylaş" menülerinden herhangi bir uygulama üzerinden gelen desteklenen dosyalar doğrudan Evrak ile açılabilir
- **Diğer uygulamalarla uyum içerisinde çalışma:** "Birlikte aç" seçeneği ile Evrak üzerinden görüntülenen dosyalar, destekleyen diğer uygulamalar ile açılabilir
- **Desteklenmeyen dosya türlerini paylaşabilme ve kaydedebilme:** Bu sayede cihaza kaydedilmeden doğrudan açılan dosyalar, desteklenmese bile paylaşılabilir ve kaydedilebilir

## Neden Evrak?

- ✅ **Tamamen ücretsiz**
- ✅ **Reklamsız**
- ✅ **%100 Türkçe**
- ✅ **Açık kaynak kodlu**
- ✅ **Hiçbir izin istemez** — bildirim izni dahil, uygulamanın çalışması için herhangi bir Android izni gerekmez

## CELSE Android uygulaması entegrasyonu

Evrak uygulamasının, CELSE ile Uyap Doküman Editör'ü uygulaması olmadan doğrudan çalışabilmesi için [şu adımları izleyin.](https://github.com/symbuzzer/UDE_stub)


## Desteklenen dosya formatları

| Format | Uzantı |
| --- | --- |
| UYAP UDF | `.udf` |
| TIFF | `.tif`, `.tiff` |
| PDF | `.pdf` |
| Word | `.docx`, `.doc` |
| HTML | `.html`, `.htm` |
| Metin | `.txt` |
| Görsel | `.jpg`, `.jpeg`, `.png`, `.gif` |

## Kullanılan kütüphaneler ve lisansları

Bu proje aşağıdaki açık kaynak kütüphaneleri kullanmaktadır:

### Dosya görüntüleme ve dönüştürme

| Kütüphane                                                                                                                                                    | Lisans |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| [Coil](https://github.com/coil-kt/coil) (coil-compose, coil-gif) — görsel yükleme (JPG/PNG/GIF)                                                              | Apache License 2.0 |
| [PDF.js](https://mozilla.github.io/pdf.js/) — Mozilla tarafından geliştirilen PDF görüntüleme motoru                                                          | Apache License 2.0 |
| [tiffrenderer](https://github.com/lucf15/TiffRenderer) — TIFF render motoru ve PDF dönüşümü                                                                  | Apache License 2.0 |
| [LibreOffice Core](https://www.libreoffice.org/) (LibreOfficeKit) — DOC/DOCX görüntüleme ve PDF dönüşümü                                                     | Mozilla Public License 2.0 |

### Android / Kotlin

| Kütüphane | Lisans |
| --- | --- |
| [AndroidX Core KTX](https://developer.android.com/jetpack/androidx/releases/core) | Apache License 2.0 |
| [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat) | Apache License 2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | Apache License 2.0 |
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx/releases/activity) | Apache License 2.0 |
| [Jetpack Compose](https://developer.android.com/jetpack/androidx/releases/compose) (BOM, UI, Graphics, Material 3, Material Icons Extended, Tooling) | Apache License 2.0 |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle) (Runtime KTX) | Apache License 2.0 |
| [AndroidX Navigation Compose](https://developer.android.com/jetpack/androidx/releases/navigation) | Apache License 2.0 |
| [AndroidX Room](https://developer.android.com/jetpack/androidx/releases/room) (Runtime, KTX, Compiler/KSP) | Apache License 2.0 |
| [AndroidX DocumentFile](https://developer.android.com/jetpack/androidx/releases/documentfile) | Apache License 2.0 |
| [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit) | Apache License 2.0 |
| [AndroidX Print](https://developer.android.com/jetpack/androidx/releases/print) | Apache License 2.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) (dil ve derleyici eklentileri) | Apache License 2.0 |
| [KSP](https://github.com/google/ksp) (Kotlin Symbol Processing) | Apache License 2.0 |
| [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin) | Apache License 2.0 |

### Test / Debug
| Kütüphane                                                                                                                                                     | Lisans |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| **[JUnit 4](https://github.com/junit-team/junit4)** | EPL 1.0 |
| **[AndroidX Test JUnit](https://developer.android.com/training/testing/junit-runner)** | Apache License 2.0  |
| **[Espresso Core](https://developer.android.com/training/testing/espresso)** | Apache License 2.0  |
| **[Compose UI Test JUnit4](https://developer.android.com/develop/ui/compose/testing)** | Apache License 2.0  |
| **[Compose UI Tooling](https://developer.android.com/develop/ui/compose/tooling)** | Apache License 2.0  |
| **[Compose UI Test Manifest](https://developer.android.com/develop/ui/compose/testing)** | Apache License 2.0  |

## Gizlilik

Evrak, herhangi bir Android izni talep etmez. Açılan dosyalar yalnızca cihazın kendi yerel depolama alanında (uygulamanın kendi önbelleğinde) tutulur; herhangi bir sunucuya veri gönderilmez, hiçbir analitik/takip SDK'sı kullanılmaz. 

Uygulama içindeki tüm dönüştürme ve görüntüleme işlemleri tamamen cihaz üzerinde, çevrimdışı (offline) olarak gerçekleştirilir. İnternet bağlantısı gerektirmez ve verilerinizi hiçbir şekilde internete aktarmaz.

## Sorumluluk reddi

Bu uygulama, belgelerin doğru ve eksiksiz görüntülenmesini garanti etmez. Evrak uygulamasını indirmek ve kullanmak tamamen kullanıcının sorumluluğundadır. Uygulamanın kullanılmasından kaynaklı zararlardan geliştirici hiç bir şekilde sorumlu tutulamaz.

Evrak uygulamasını indirerek ve kullanarak bu şartları kabul etmiş sayılırsınız. Şartları kabul etmiyorsanız uygulamayı kaldırmanız ve kullanmamanız gerekmektedir.

## Katkıda bulunma

Hata bildirimleri, öneriler ve pull request'ler memnuniyetle karşılanır. Yeni bir dosya formatı desteği eklemek veya mevcut görüntüleyicilerden birini iyileştirmek isterseniz, lütfen bir issue açarak talep ve öneride bulunun.

## Lisans

Bu proje açık kaynak kodludur. Lisans bilgisi için depo kök dizinindeki `LICENSE` dosyasına bakınız.
