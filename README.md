# NCMC Reader

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**NCMC Reader** is an open-source Android application built with Jetpack Compose that allows users to read, inspect, and analyze **National Common Mobility Card (NCMC)** smart cards using NFC (Near Field Communication).

---

## Features

- **NFC Card Scanning**: Tap any NCMC-compliant transit card on your NFC-enabled Android device to instantly read card details.
- **APDU & TLV Parsing**: Communicates with the smart card using ISO-DEP APDU commands and robust TLV (Tag-Length-Value) parsing to extract data structures.
- **Comprehensive Card Details**:
  - Card PAN (Primary Account Number) & Tokenized ID
  - Cardholder Name & Expiry Date
  - Wallet / Purse Balance & Currency
  - Recent Transaction History (Transit logs, tolls, retail)
- **Operator Identification**: Automatically maps card Issuer Identification Numbers (IIN) and operator codes using an embedded/synced SQLite database (`ncmc.db`).
- **Card History & Management**: View, inspect, and manage previously scanned cards locally.
- **Modern UI / UX**: Built entirely with **Jetpack Compose** and **Material 3**, featuring edge-to-edge support, smooth animations, and haptic feedback.

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

### 3. Build Release APK
```bash
./gradlew assembleRelease
```

### 4. Other Useful Gradle Tasks
- **Clean build artifacts**:
  ```bash
    ./gradlew clean
  ```
- **Download / Refresh Operator Database (`ncmc.db`)**:
  ```bash
    ./gradlew downloadNcmcDb
  ```
- **Run Unit Tests**:
  ```bash
    ./gradlew test
  ```
- **Run Instrumented UI/Integration Tests**:
  ```bash
    ./gradlew connectedAndroidTest
  ```

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
