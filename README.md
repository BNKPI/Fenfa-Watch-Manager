# Fenfa Watch Manager

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="Fenfa Watch Manager Icon" />
</p>

Eine eigenständige Android-Smartphone-App, die Wear OS Watch-Apps aus einer selbst gehosteten **Fenfa**-Instanz verwaltet, automatisch neue Builds erkennt, herunterlädt und per **Wireless ADB** geräuschlos direkt auf deiner Smartwatch installiert – ganz ohne externe Tools wie Bugjaeger oder den Wear Installer.

---

## ⌚ Kompatibilität

### Getestet & Verifiziert:
- ✅ **Samsung Galaxy Watch 8** (Wear OS 5 / One UI Watch 6)

### Unterstützte Smartwatches:
Die App funktioniert grundsätzlich mit **jeder Smartwatch, die auf Google Wear OS (Android)** basiert und **Drahtloses Debuggen (Wireless ADB)** in den Entwickleroptionen unterstützt:

* **Samsung Galaxy Watch:**
  * Galaxy Watch 8 *(geprüft)*
  * Galaxy Watch 7 & Watch Ultra
  * Galaxy Watch 6 & Watch 6 Classic
  * Galaxy Watch 5 & Watch 5 Pro
  * Galaxy Watch 4 & Watch 4 Classic
  *(Hinweis: Ältere Samsung-Uhren mit Tizen OS wie Galaxy Watch 3 oder Gear S3 werden nicht unterstützt)*
* **Google:** Pixel Watch, Pixel Watch 2, Pixel Watch 3
* **OnePlus:** OnePlus Watch 2, Watch 2R
* **Xiaomi:** Xiaomi Watch 2, Xiaomi Watch 2 Pro
* **Mobvoi:** TicWatch Pro 5, TicWatch Pro 3, TicWatch E3
* **Fossil / Skagen / Michael Kors:** Gen 6, Gen 5
* **Weitere:** TAG Heuer Connected, Montblanc Summit, Suunto 7 etc.

> [!NOTE]
> **Nicht kompatibel:** Smartwatches mit proprietären Systemen (wie Garmin, Fitbit ohne Wear OS, Amazfit / Zepp OS, Huawei Watch mit HarmonyOS oder Apple Watch mit watchOS), da diese kein Android ADB bereitstellen.

---

## ✨ Features

- **Fenfa OTA-Sync**: Dual-Host-Unterstützung – prüft Updates wahlweise über das lokale Heim-WLAN oder über ein sicheres VPN/Tailscale-Netzwerk.
- **Integrierter Wireless ADB Client**: Direkte Verbindung zur Smartwatch über die native Kotlin-ADB-Bibliothek [`Kadb`](https://github.com/flyfishxu/Kadb) (kein PC oder Kabel erforderlich).
- **Automatisches mDNS Discovery**: Spürt die Galaxy Watch im WLAN bei aktivem Wireless Debugging automatisch auf (`_adb-tls-connect._tcp`) und liest den aktuellen dynamischen Port ein.
- **Persistenter ADB-Schlüssel**: Speichert den generierten RSA-Schlüsselbund (`kadb_key.pem`) dauerhaft im App-Speicher, sodass die Autorisierung auf der Uhr auch nach App-Neustarts und Updates erhalten bleibt.
- **Multi-App-Verwaltung**: Verwaltet beliebig viele Wear OS Apps (Standard: *OpenGym Wear*).
- **Versions-Abgleich**: Liest den auf der Watch installierten `versionCode` via ADB-Shell aus und vergleicht ihn live mit den Releases auf dem Fenfa-Server.
- **1-Klick-Installation**: Lädt das APK im Hintergrund herunter und stößt die Installation per `pm install` auf der Uhr an.
- **Self-Update**: Der Manager erkennt in Fenfa neu bereitgestellte Versionen seiner selbst und bietet eine direkte In-App-Aktualisierung über den Android `PackageInstaller`.

---

## 📱 Schnellstart

### 1. Smartwatch vorbereiten
1. Auf der Smartwatch: **Einstellungen** → **Info zur Uhr** → **Software-Info** → 7-mal auf **Softwareversion** tippen, um die Entwickleroptionen zu aktivieren.
2. Zurück in die **Einstellungen** → **Entwickleroptionen**:
   - **ADB-Debugging** aktivieren.
   - **Drahtloses Debuggen** aktivieren.

### 2. Einmalig koppeln (Pairing)
1. Auf der Uhr in *Drahtloses Debuggen* auf **Neues Gerät koppeln** tippen.
2. In der **Fenfa Watch Manager**-App auf dem Smartphone auf **Koppeln** tippen.
3. Die angezeigte IP, den 5-stelligen Pairing-Port und den 6-stelligen WLAN-Kopplungscode eingeben.
4. Nach erfolgreicher Kopplung ist dein Smartphone auf der Uhr dauerhaft autorisiert.

### 3. Verbinden & Aktualisieren
1. Auf der Uhr einen Schritt zurück auf die Hauptseite von *Drahtloses Debuggen* gehen (dort steht der aktuelle Connect-Port).
2. In der Smartphone-App entweder auf **Suchen** tippen (automatische mDNS-Erkennung) oder auf **IP/Port**, um den Connect-Port einzutragen.
3. Auf **Verbinden** tippen.
4. Bei Apps mit verfügbarem Update einfach auf **Auf Watch installieren** tippen!

---

## 🛠 Tech-Stack & Architektur

- **UI:** Jetpack Compose, Material 3, Dark & Dynamic Color Theme
- **Architektur:** MVVM, Coroutines, StateFlow, Android Architecture Components
- **Netzwerk & ADB:** 
  - [Kadb](https://github.com/flyfishxu/Kadb) (Kotlin ADB implementation mit TLS-Pairing & TLS-Connect)
  - OkHttp 4
  - Android Network Service Discovery (NSD / mDNS mit `MulticastLock`)
- **Persistenz:** Jetpack Preferences DataStore
- **Distribution:** Gradle Fenfa Upload Plugin (`fenfa-upload.gradle`)

---

## 🚀 Entwicklung & Build

### Voraussetzungen
- Android Studio (aktuell)
- Android SDK 35 / compileSdk 37
- JDK 17 / 21 (z. B. Android Studio JBR)

### Konfiguration
Kopiere `fenfa.properties.example` zu `fenfa.properties`:
```bash
cp fenfa.properties.example fenfa.properties
```
Passe die Werte für deine Fenfa-Instanz an:
```properties
fenfa.localUrl=http://192.168.1.100:8100
fenfa.url=https://fenfa.example.com
fenfa.uploadToken=YOUR_UPLOAD_TOKEN
fenfa.variantId=var_YOUR_VARIANT_ID
fenfa.productSlug=watchmanager
```

### Bauen
```bash
# Debug APK bauen
./gradlew assembleDebug

# Release APK bauen
./gradlew assembleRelease

# Release nach Fenfa hochladen
./gradlew fenfaUploadRelease -Pchangelog="Release notes..."
```

---

## 📄 Lizenz

Dieses Projekt steht unter der [GNU General Public License v3.0 (GPLv3)](LICENSE).
