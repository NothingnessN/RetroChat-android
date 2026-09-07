<div align="center">

# 📱 RetroChat — Android Client

**Self-hosted, cross-platform chat app**

[![Türkçe](https://img.shields.io/badge/🇹🇷-T%C3%BCrk%C3%A7e-red?style=for-the-badge)](#-türkçe)
[![English](https://img.shields.io/badge/🇬🇧-English-blue?style=for-the-badge)](#-english)

</div>

---

## 🇹🇷 Türkçe

### RetroChat nedir?

RetroChat, **kendi sunucunuzda** çalıştırdığınız, WhatsApp benzeri basit bir mesajlaşma uygulamasıdır. Bu depo, RetroChat'in **Android** istemcisinin (uygulamasının) kaynak kodlarını içerir.

Yani hiçbir üçüncü taraf şirkete veri gitmez; mesajlarınız, sizin kurduğunuz sunucuda kalır.

> ℹ️ Bu depoda **sadece telefon tarafındaki (istemci) kod** vardır. Mesajların gidip geldiği sunucu (backend) bu depoya dahil değildir — çalıştırmak için kendi sunucunuzu kurmanız gerekir.

### Android tarafında cross-platform (Nokia ile birlikte kullanım)

RetroChat'in bir de eski Nokia telefonlar (Series 40 / J2ME) için yazılmış bir istemcisi var: **[NothingnessN/RetroChat-symbian](https://github.com/NothingnessN/RetroChat-symbian)** deposu. İki taraf da aynı sunucu ile konuştuğu için, `Network.java` içindeki `SERVER` adresini her iki uygulamada da **aynı sunucunun IP'sine** ayarlarsanız, Android kullanan biriyle eski bir Nokia telefonu kullanan biri **birbiriyle** mesajlaşabilir. Yani RetroChat, tek bir sunucu üzerinden hem modern Android telefonlar hem de eski Nokia telefonları arasında köprü kurar.

### Bu projeyi nasıl kullanırım? (Hiç bilmeyenler için adım adım)

1. **Bir sunucu kurun.** RetroChat'in beklediği API uçlarını (register, send, thread, upload vb.) karşılayan kendi backend'inizi bir sunucuda (örneğin ucuz bir VPS veya bulut sunucusu) çalıştırın ve bu sunucunun bir **IP adresi** olsun (örnek: `93.184.216.34`).
2. **`app/src/main/java/com/nothingnessn/retrochat/Network.java`** dosyasını açın ve en üstlerde şu satırı bulun:
   ```java
   public static String SERVER = "http://YOUR_SERVER_IP";
   ```
   `YOUR_SERVER_IP` yazan yeri kendi sunucunuzun gerçek IP adresiyle (veya alan adıyla) değiştirin. Örneğin:
   ```java
   public static String SERVER = "http://93.184.216.34";
   ```
3. **Android Studio'yu** indirip kurun (ücretsizdir): https://developer.android.com/studio
4. Android Studio'yu açın, **"Open"** diyerek bu projenin klasörünü (bu depoyu indirdiğiniz/klonladığınız klasörü) seçin.
5. Android Studio ilk açılışta gerekli Gradle/SDK bileşenlerini kendisi indirecektir (internet bağlantısı gerekir), biraz bekleyin.
6. Üstteki yeşil **"Run" (▶)** tuşuna basarak projeyi ya bağlı bir Android telefona ya da bir emülatöre (Android Studio içinden sanal telefon oluşturabilirsiniz) kurup çalıştırabilirsiniz. Ya da **Build → Build Bundle(s) / APK(s) → Build APK(s)** diyerek bir `.apk` dosyası üretip bunu istediğiniz telefona elle kurabilirsiniz.
7. Uygulamayı açın, bir kullanıcı adı oluşturun (kayıt sırasında bir güvenlik sorusu + cevabı da belirlersiniz) ve mesajlaşmaya başlayın!

Kısacası: **kendi sunucunuzu açtıktan sonra, yukarıdaki `SERVER` satırına o sunucunun IP adresini yazmanız ve Android Studio ile derlemeniz yeterli.**

### Bunun için ne gerekiyor?

- **Android Studio** (ücretsiz, resmi Google IDE'si) — [indirme linki](https://developer.android.com/studio)
- İnternet bağlantısı (ilk açılışta gerekli bileşenleri indirmek için)
- Test etmek için gerçek bir Android telefon (USB hata ayıklama / "USB debugging" açık) **veya** Android Studio'nun kendi emülatörü
- Uygulamayı kurduğunuz cihazda internet bağlantısı (sunucunuza ulaşabilmesi için)
- Kendi kurduğunuz bir sunucu (bu depoya dahil değil)

Kodu elle derlemek/paket dosyası oluşturmakla uğraşmanıza gerek yok; Android Studio "Run" tuşuyla her şeyi sizin için hallediyor.

### Bir sorun mu var?

Bir hata bulursanız, bir şey çalışmazsa veya bir sorunuz olursa, bu depodaki **"Issues"** (Sorunlar) sekmesinden yeni bir konu açabilirsiniz. Elimden geldiğince yardımcı olmaya çalışırım.

### Lisans hakkında

Bu projeye resmi bir lisans dosyası eklenmedi, ama bu projeyi istediğiniz gibi kullanabilir, değiştirebilir ve fork'layabilirsiniz. Kod herkese açık paylaşılıyor; ne yaparsanız yapın sorun değil.

### Teknik notlar

- Hedef platform: Android, minimum SDK 15 (Android 4.0.4 ICS) — maksimum SDK 36 (Android 16). Yani hem çok eski hem de en yeni Android sürümlerinde çalışacak şekilde yazıldı.
- `com.nothingnessn.retrochat` paketi altında: ekranlar (Activity sınıfları), ağ (HTTP) katmanı, yerel mesaj veritabanı (SQLite/LocalDb), tema sistemi, arka planda mesaj kontrolü için bir servis (PollService) ve bildirim yardımcıları bulunur.
- Java ile yazıldı, herhangi bir üçüncü parti mesajlaşma/backend SDK'sı kullanılmaz — kendi basit HTTP tabanlı protokolü ile kendi sunucunuzla konuşur.
- Proje standart bir **Gradle** yapısındadır (`build.gradle.kts`, `settings.gradle.kts`); Android Studio'nun "Open" diyerek doğrudan tanıyabileceği bir klasördür.

---

## 🇬🇧 English

### What is RetroChat?

RetroChat is a simple, WhatsApp-like chat app that you run on **your own server**. This repository contains the source code for RetroChat's **Android** client (app).

No data goes to any third-party company — your messages stay on the server you set up.

> ℹ️ This repository only contains the **phone-side (client) code**. The backend server that messages are sent to/from is **not** included here — you need to run your own server for this to work.

### Cross-platform on Android (using it together with Nokia)

RetroChat also has a client written for old Nokia phones (Series 40 / J2ME): the **[NothingnessN/RetroChat-symbian](https://github.com/NothingnessN/RetroChat-symbian)** repository. Since both sides talk to the same server, if you set the `SERVER` address in `Network.java` to **the same server's IP** in both apps, someone using Android and someone using an old Nokia phone can chat with **each other**. In other words, RetroChat bridges modern Android phones and old Nokia phones through a single server.

### How do I use this? (Step by step, no prior knowledge needed)

1. **Set up a server.** Run your own backend that implements the API endpoints RetroChat expects (register, send, thread, upload, etc.) on any machine with a public **IP address** (e.g. a cheap VPS or cloud server).
2. Open **`app/src/main/java/com/nothingnessn/retrochat/Network.java`** and find this line near the top:
   ```java
   public static String SERVER = "http://YOUR_SERVER_IP";
   ```
   Replace `YOUR_SERVER_IP` with your server's actual IP address (or domain name). For example:
   ```java
   public static String SERVER = "http://93.184.216.34";
   ```
3. Download and install **Android Studio** (it's free): https://developer.android.com/studio
4. Open Android Studio, choose **"Open"**, and select this project's folder (the folder you downloaded/cloned this repo into).
5. On first launch, Android Studio will download the required Gradle/SDK components on its own (an internet connection is needed) — this can take a few minutes.
6. Press the green **"Run" (▶)** button at the top to build and install the app on a connected Android phone or an emulator (you can create a virtual phone right inside Android Studio). Alternatively, use **Build → Build Bundle(s) / APK(s) → Build APK(s)** to produce a `.apk` file you can install manually on any phone.
7. Open the app, pick a username (you'll also set a security question + answer during registration), and start chatting!

In short: **once your own server is up, just put its IP address into the `SERVER` line above and build with Android Studio.**

### What do I need for this?

- **Android Studio** (free, official Google IDE) — [download link](https://developer.android.com/studio)
- An internet connection (for downloading required components on first launch)
- A real Android phone to test on (with "USB debugging" enabled) **or** Android Studio's built-in emulator
- An internet connection on the device running the app (so it can reach your server)
- Your own server set up and running (not included in this repository)

You don't need to manually compile anything or build package files by hand — Android Studio's "Run" button takes care of everything for you.

### Found a problem?

If something breaks, doesn't work, or you have a question, please open a new topic in this repository's **"Issues"** tab. I'll try to help out when I can.

### License

No formal license file has been added, but you're free to use, modify, and fork this project however you like. It's shared publicly — do whatever you want with it.

### Technical notes

- Target platform: Android, minimum SDK 15 (Android 4.0.4 ICS) up to SDK 36 (Android 16) — written to run on both very old and the newest Android versions.
- Under the `com.nothingnessn.retrochat` package: screens (Activity classes), the HTTP networking layer, a local message database (SQLite/LocalDb), a theming system, a background polling service (PollService) for checking new messages, and notification helpers.
- Written in Java, with no third-party messaging/backend SDK — it talks to your own server over a simple custom HTTP-based protocol.
- The project uses a standard **Gradle** layout (`build.gradle.kts`, `settings.gradle.kts`), so Android Studio can open it directly via "Open".

