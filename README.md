# Evrak
![Github All Releases](https://img.shields.io/github/downloads/symbuzzer/Evrak/total.svg)

**Evrak**, avukatların günlük iş akışında en sık karşılaştığı dosya formatlarını —**UDF, TIFF, PDF, DOCX, DOC, HTML, JPG, GIF ve PNG**— tek bir uygulama üzerinden basit, sade ve hızlı bir şekilde görüntülemesini sağlayan bir Android uygulamasıdır.

UYAP ve Celse uygulaması üzerinden veya e-posta/Whatsapp vb. mesajlaşma uygulamaları aracılığıyla gelen evrakları açmak için genellikle birden fazla farklı uygulamaya ihtiyaç duyulur. Evrak, bu ihtiyacı tek bir yerde, gereksiz karmaşıklık olmadan çözer.

<img width="270" height="585" src="https://github.com/user-attachments/assets/11a02fad-be30-4320-ac76-c1ddf80f5ebd" />
<img width="270" height="585" src="https://github.com/user-attachments/assets/819d6633-1c71-4459-9fbc-b47a9d4933ae" />
<img width="270" height="585" src="https://github.com/user-attachments/assets/315d1016-36f3-457b-bf1b-d04c10106007" />
<img width="270" height="585" src="https://github.com/user-attachments/assets/a4fa0f9a-d922-4025-912c-725a5d07f521" />
<img width="270" height="585" src="https://github.com/user-attachments/assets/16ad8d58-a749-4f2e-bb2b-50b573f4356e" />
<img width="270" height="585" src="https://github.com/user-attachments/assets/4e43874c-9d33-4354-a4b6-568b0968006f" />


## Özellikler

- **Çoklu format desteği:** UDF, TIFF, PDF, DOCX, DOC, HTML, JPG, GIF, PNG
- **Basit ve hızlı:** Dosyayı aç, görüntüle - başka hiçbir şeye gerek yok
- **Paylaşma ve kaydetme:** Görüntülenen dosyaları istediğiniz yere kaydedebilir veya doğrudan başka uygulamalarla paylaşabilirsiniz
- **UDF ve TIFF dosyalarını PDF'e çevirme:** Paylaşma ve kaydetme esnasında isterseniz bu dosyaları PDF'e çevirebilirsiniz
- **Geçmiş:** Açılan dosyalar tarih sırasına göre uygulama içinde listelenir
- **Filtreleme:** Geçmişteki dosyaları dosya türüne göre filtreleyebilirsiniz
- **Yeniden adlandırma ve silme:** Geçmişteki dosyalar daha kolay hatırlanabilmeler için istenildiğinde yeniden adlandırılabilir veya silinebilir
- **Sistem entegrasyonu:** "Birlikte aç" ve "Paylaş" menüsülerinden herhangi bir uygulama üzerinden gelen desteklenen dosyalar doğrudan Evrak ile açılabilir
- **Diğer uygulamalarla uyum içerisinde çalışma:** "Birlikte aç" seçeneği ile Evrak üzerinden görüntülediğiniz dosyaları, destekleyen diğer uygulamalar ile açabilirsiniz

## Neden Evrak?

- ✅ **Tamamen ücretsiz**
- ✅ **Reklamsız**
- ✅ **%100 Türkçe**
- ✅ **Açık kaynak kodlu**
- ✅ **Hiçbir izin istemez** — bildirim izni dahil, uygulamanın çalışması için herhangi bir Android izni gerekmez

## Desteklenen Dosya Formatları

| Format | Uzantı | Görüntüleme Şekli |
| --- | --- | --- |
| UYAP UDF | `.udf` | Zengin metin (paragraf, tablo, resim, liste) HTML olarak render edilir |
| TIFF | `.tif`, `.tiff` | Çok sayfalı görüntü, sayfa sayfa kaydırma ve yakınlaştırma |
| PDF | `.pdf` | Sayfa sayfa görüntüleme ve yakınlaştırma |
| Word | `.docx`, `.doc` | Biçimlendirmesi korunarak HTML'e dönüştürülüp görüntülenir |
| HTML | `.html`, `.htm` | Yakınlaştırma/kaydırma destekli görüntüleyici |
| Görsel | `.jpg`, `.jpeg`, `.png`, `.gif` | Yakınlaştırma/kaydırma destekli görüntüleyici |

## Kullanılan Kütüphaneler ve Lisansları

Bu proje aşağıdaki açık kaynak kütüphaneleri kullanmaktadır:

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
| [Kotlin](https://github.com/JetBrains/kotlin) (dil ve derleyici eklentileri) | Apache License 2.0 |
| [KSP](https://github.com/google/ksp) (Kotlin Symbol Processing) | Apache License 2.0 |
| [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin) | Apache License 2.0 |

### Dosya Görüntüleme / Dönüştürme

| Kütüphane                                                                                                                                                     | Lisans |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| [Apache POI](https://poi.apache.org/) (poi, poi-ooxml, poi-scratchpad) — DOC/DOCX ayrıştırma                                                                  | Apache License 2.0 |
| [Coil](https://github.com/coil-kt/coil) (coil-compose, coil-gif) — görsel yükleme (JPG/PNG/GIF)                                                               | Apache License 2.0 |
| [tiffrenderer](https://github.com/lucf15/TiffRenderer) — TIFF render motoru                                                                                   | Apache License 2.0 |
| [docx-preview](https://github.com/volodymyrbaydalka/docxjs) v0.4.0 — DOCX içeriğinin WebView üzerinde render edilmesi (APK içine `assets/js/` altında gömülü) | Apache License 2.0 |
| [JSZip](https://github.com/Stuk/jszip) — docx-preview'un ZIP (DOCX) ayrıştırma bağımlılığı                                                                    | MIT License (veya GPLv3 - çift lisanslı) |
| [TiffBitmapFactory](https://github.com/Beyka/Android-TiffBitmapFactory) v0.9.9 — TIFF dosyalarını PDF'e çevirme                                               | MIT License |

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

Evrak, herhangi bir Android izni talep etmez. Açılan dosyalar yalnızca cihazın kendi yerel depolama alanında (uygulamanın kendi önbelleğinde) tutulur; herhangi bir sunucuya veri gönderilmez, hiçbir analitik/takip SDK'sı kullanılmaz. İnternete ve cihazın dosya sistemine hiç bir şekilde erişmez.

## Katkıda Bulunma

Hata bildirimleri, öneriler ve pull request'ler memnuniyetle karşılanır. Yeni bir dosya formatı desteği eklemek veya mevcut görüntüleyicilerden birini iyileştirmek isterseniz, lütfen bir issue açarak talep ve öneride bulunun.

## Lisans

Bu proje açık kaynak kodludur. Lisans bilgisi için depo kök dizinindeki `LICENSE` dosyasına bakınız.
