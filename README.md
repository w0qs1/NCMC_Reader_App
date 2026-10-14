# NCMC Reader

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**NCMC Reader** is an open-source Android application built with Jetpack Compose that allows users to read **National Common Mobility Card (NCMC)** smart cards using NFC (Near Field Communication).

---

## Features

- **NFC Card Scanning**: Tap any NCMC-compliant transit card on your NFC-enabled Android device to instantly read card details.
- **Comprehensive Card Details**:
  - Card PAN (Primary Account Number)
  - Expiry Date
  - Wallet / Purse Balance
  - Recent Transaction History (Transit logs)
- **Operator/Line/Station Identification**: Automatically decodes Terminal ID using an embedded/synced SQLite database and database is synced online from [NCMC_IDs](https://github.com/w0qs1/NCMC_IDs).
- **Database Contribution**: Please contribute to [github.com/w0qs1/NCMC_IDs](https://github.com/w0qs1/NCMC_IDs/issues) to add support for more operators, stations.
- **Card History & Management**: View, and manage previously scanned cards locally.
- **Backup & Restore**: Export and import card history using JSON files.
- **Automatic History merge**: Scan the same card to collect and merge new transit history.
- **Modern UI / UX**: Built entirely with **Jetpack Compose** and **Material 3**, featuring edge-to-edge support, smooth animations, and haptic feedback.

---

## Disclaimer & No Affiliation

- This app can **only read balance and transit history**. It **cannot perform balance update or balance modification**.
- The contributors of this app are **not responsible for any warranty or damages to cards since this is based on a [reverse-engineered specification](https://nkmason.dev/posts/reversing-ncmc-spec/).**
- **Built using LLM Tools. Use at your own risk!**
- This project and its contributors are **not affiliated with, endorsed by, sponsored by, or officially associated with:**
  - RuPay
  - National Payments Corporation of India (NPCI)
  - National Common Mobility Card (NCMC) program
  - NCMC-issuing banks
  - Metro rail corporations and other transit operators
  - Automatic Fare Collection (AFC) system providers

---

## Prerequisites

- **Hardware**: An Android device with NFC capabilities (API Level 29 / Android 10 or higher).
- **Software**: 
  - Android Studio (Narwhal / Koala or newer recommended)
  - JDK 11 or higher
  - Android SDK Build-Tools (API 37)

---

## Build Steps

The project uses Gradle build system. You can build, test, and package the app using the command line (`gradlew`) or Android Studio.

### 1. Clone the Repository
```bash
git clone https://github.com/w0qs1/NCMC_Reader_App.git
cd NCMCReader
```

### 2. Build Debug APK
To compile and assemble the debug build of the application:
```bash
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/ncmc_reader-debug.apk`

---

## Usage

1. **Enable NFC**: Ensure that NFC is turned on in your Android device settings.
2. **Launch App**: Open **NCMC Reader** on your phone.
3. **Tap Card**: When prompted on the main screen, place your NCMC transit card against the back of your phone.
4. **Inspect Data**: Once detected and successfully read, the app will vibrate and display card balance, personal details, and transaction logs.
5. **History**: Navigate to the scanned cards history tab to review previously read cards offline.

---

## Project Structure

```text
com.w0qs1.ncmcreader/
├── data/               # Database helpers, NCMC data models, and operator mapping
├── nfc/                # NFC reader callbacks, APDU utils, and TLV parsers
├── ui/                 # Jetpack Compose screens, views, and Material 3 themes
├── utils/              # Haptic feedback and utility extensions
└── viewmodel/          # MainViewModel managing UI state and NFC event flow
```

---

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request or open an issue for bug reports, feature requests, or suggestions.

## License

This project is licensed under the terms of the MIT License. See the [LICENSE](LICENSE) file for details.
