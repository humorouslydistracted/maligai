# 🛒 Maligai — Kirana Store Billing App

**Maligai** is an offline-first Android POS for kirana / maligai-kadai (grocery) shops across India. The homepage is a **handwriting canvas**: write an item in your local script (or English), the app recognizes it with on-device ML Kit Digital Ink, suggests matching inventory, and adds it to the running bill.

Built with Kotlin + Jetpack Compose + Hilt + Room. Prints to Bluetooth ESC/POS thermal printers (58 mm / 80 mm).

<!-- Screenshots — add images to docs/screenshots/ and uncomment when ready
<p align="center">
  <img src="docs/screenshots/billing.png" width="240" alt="Billing screen" />
  <img src="docs/screenshots/canvas.png" width="240" alt="Handwriting canvas" />
  <img src="docs/screenshots/receipt.png" width="240" alt="Receipt preview" />
</p>
-->

---

## ✨ Features

- **Handwriting billing** — Write item names in Tamil, Hindi, Telugu, Kannada, Malayalam, Bengali, Marathi, Gujarati, or English
- **Smart line parsing** — `item x2 - 40` for quantity + amount; supports `x250gm`, `x200ml`, `x1kg` units
- **Catalog matching** — Fuzzy suggestions from your shop inventory; per-shop correction learning
- **Unit-aware quantity** — Weight (kg/g), volume (l/ml), or count (piece/packet) with quick picker + keypad
- **Multiple parked bills** — Renameable top tabs for concurrent customers
- **Customer credit (கடன்)** — Outstanding balances, part-payments, loan ledger
- **GST receipts** — Optional CGST + SGST (inclusive), printed breakup
- **Configurable receipts** — Per-field font size, bold, header/footer, live preview
- **Ledger & analysis** — Sales history, bar/pie charts, ranked items
- **CSV backup** — Auto-export to dual folders (internal + Documents)
- **Thermal printing** — Bluetooth Classic SPP / ESC/POS; regional script as bitmap or English text
- **Biometric + PIN** — Device unlock gate, 4-digit admin PIN, security question recovery
- **Dark / light themes** — Material 3 with readable text in setup and billing flows

---

## 🏗️ Architecture

```
MVVM + Repository Pattern
├── UI Layer        → Jetpack Compose (Material 3)
├── ViewModel       → StateFlow, business logic
├── Repository      → Data operations, recognition pipeline
├── Database        → Room (SQLite)
├── Recognition     → ML Kit Digital Ink (regional + English models)
└── Services        → PrinterManager, CsvManager, LineParser
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt 2.51.1 |
| Database | Room 2.6.1 |
| Recognition | ML Kit Digital Ink 18.1.0 |
| Navigation | Navigation Compose 2.7.7 |
| Auth | Biometric 1.1.0 |
| Printing | Bluetooth Classic SPP (ESC/POS) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

### Source Files

```
app/src/main/java/com/maligai/app/
├── Database.kt         — Entities, DAOs, Room DB, type converters
├── Repository.kt       — Repositories, ViewModels, Hilt module
├── MainActivity.kt     — Entry, biometric gate, first-time setup, NavHost
├── BillScreen.kt       — Homepage: tabs, canvas, suggestions, bill preview
├── AdminScreen.kt      — Items, ledger, loans, spending, analysis, CSV, settings
├── ScriptLanguage.kt   — Regional language registry + ML Kit tags + canvas hints
├── LineParser.kt       — Handwritten line parsing (x-quantity, amount)
├── Recognizer.kt       — ML Kit dual-model wrapper (regional + English)
├── PrinterManager.kt   — Bluetooth SPP, ESC/POS, script bitmap raster
├── CsvManager.kt       — CSV export (dual folder, monthly + latest)
├── QuantityPicker.kt   — Unit-aware quantity UI
└── Theme.kt            — Colors, typography, Material 3 theme
```

See [tamil_handwriting_pos_spec.md](tamil_handwriting_pos_spec.md) for the full product specification.

---

## 📥 Quick Install

Download the latest APK from [Releases](https://github.com/humorouslydistracted/maligai/releases/latest) and install on your Android device (SDK 26+ / Android 8.0+).

> **Note:** You may need to enable "Install from unknown sources" in your device settings.

---

## 🚀 Building from Source

### Prerequisites

- **Android Studio** Ladybug (2024.1+) or newer
- **JDK 17**
- **Android device** with SDK 26+ (Bluetooth for printing)
- Internet once at setup to download handwriting models (~15–20 MB each)

### Clone & Build

```bash
git clone https://github.com/humorouslydistracted/maligai.git
cd maligai
```

Open in Android Studio → Sync Gradle → Build.

Or build from terminal (Windows):

```bat
gradlew.bat assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## ✍️ Handwriting Convention

Write one item per line on the canvas:

| Pattern | Meaning |
|---------|---------|
| `maggi - 40` | Item **maggi**, line total **₹40** |
| `maggi x2 - 40` | **2** units of maggi, total **₹40** |
| `rice x250gm - 30` | **250 g** of rice, total **₹30** |
| `oil x200ml - 50` | **200 ml** of oil, total **₹50** |

- **`-`** separates item from rupee amount (amount is always after `-`)
- **`x`** prefix marks quantity or measure (`x2`, `x250gm`, `x1kg`, `x200ml`)
- Canvas hints update to match your chosen shop language (Admin → Settings)

---

## 🖨️ Printer Setup

Maligai supports **Bluetooth thermal printers** using ESC/POS over Bluetooth Classic SPP.

### Supported Paper Sizes

- **58 mm / 2 inch** — portable POS printers
- **80 mm / 3 inch** — standard retail printers

Set paper width in **Printer → Print Settings**.

### Pairing

1. Pair the printer in Android Bluetooth Settings
2. Open Maligai → Sidebar → Printer
3. Select your printer from discovered devices
4. Print a test page to verify

Regional script on receipts is rendered as a bitmap when the printer cannot print Unicode directly.

---

## 🔐 First Launch

1. **Device unlock** — Biometric or screen-lock gate
2. **Create PIN** — 4-digit admin PIN + security question
3. **Choose language** — Tamil, Hindi, Telugu, etc. (+ English model, always required)
4. **Download models** — English + regional handwriting packs (internet once)
5. **Pair printer** — Optional; can skip and configure later
6. **Start billing**

---

## 📂 CSV Backup

Data is exported automatically and can be triggered manually from Admin:

```
/storage/emulated/0/Documents/MaligaiBackup/
├── bills.csv
├── bill_items.csv
├── menu_items.csv
├── loans.csv
└── spending.csv
```

A copy is also kept in app-internal storage.

---

## 🛠️ Development Notes

### Key Dependencies (`gradle/libs.versions.toml`)

- Compose BOM `2024.06.00`
- Room `2.6.1`
- Hilt `2.51.1`
- ML Kit Digital Ink `18.1.0`
- Kotlin `2.0.0` + KSP `2.0.0-1.0.22`

### Unit Tests

```bash
./gradlew testDebugUnitTest
```

Tests live under `app/src/test/java/com/maligai/app/` (e.g. `LineParserTest.kt`).

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -m "Add my feature"`
4. Push: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 📄 License

This project is private and proprietary.

---

## 📝 Changelog

### v1.0.1 — June 2026

- x-quantity parsing (`x2`, `x250gm`, `x200ml`) on handwriting lines
- Bill preview dialog with print + done actions
- Canvas undo, compact billing layout, dark-mode text fixes
- Catalog price sync when handwritten total differs from catalog × qty
- Receipt footer default: "Thank you! Visit Again."

### v1.0.0 — Initial release

- Handwriting POS for 8 regional scripts + English
- Catalog, GST, loans, ledger, analysis, CSV backup
- Bluetooth ESC/POS printing

---

## 🙏 Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ML Kit Digital Ink](https://developers.google.com/ml-kit/vision/digital-ink-recognition)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Hilt](https://dagger.dev/hilt/)

---

**Made with ❤️ for kirana shops across India** · *Last updated: June 10, 2026*
