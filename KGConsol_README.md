# KG Consol — Android App

> Warehouse barcode scanning & Zebra label printing application.

**Current version:** `1.5.3` (`versionCode 15`)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| Database | Room 2.6 |
| Settings | DataStore Preferences |
| DI | Hilt |
| Camera / barcode / OCR | CameraX + ML Kit Barcode Scanning + Text Recognition |
| Printing | TCP Socket → ZPL (port 9100) |
| Navigation | Navigation Compose |

---

## Project Structure

```
app/src/main/java/com/kgconsol/
│
├── data/
│   ├── local/
│   │   ├── entity/     Entities.kt        — BatchEntity, BoxEntity, OrderEntity
│   │   ├── dao/        Daos.kt            — BatchDao, BoxDao, OrderDao
│   │   └──             AppDatabase.kt     — Room database
│   ├── preferences/    AppPreferences.kt  — DataStore (printer, language, scanner, keep-on)
│   └── repository/     KGRepository.kt    — Single source of truth
│
├── domain/
│   └── model/          Models.kt          — Batch, Box, Order, BoxNumberHelper, OrderValidator
│
├── di/                 AppModule.kt       — Hilt providers
│
├── util/               ZebraPrinter.kt    — TCP/ZPL printer
│
├── presentation/
│   ├── theme/          Theme.kt           — Material3 color scheme
│   ├── Navigation.kt                      — NavHost + route definitions
│   ├── batch/          BatchListScreen.kt — Home: list of batches
│   ├── box/            BoxScreen.kt       — Box detail: ID display + orders
│   ├── scan/           ScanScreen.kt      — Barcode/OCR scanner + manual input
│   ├── settings/       SettingsScreen.kt  — Printer, scanner, language, screen-on
│   └── reports/        ReportsScreen.kt   — Batch/box report with share/copy
│
├── MainActivity.kt
└── KGConsolApp.kt      (Hilt @HiltAndroidApp)
```

---

## Business Rules

### Партия (Batch)
- Format: `KG` + number → `KG123`, `KG124` …
- Auto-suggest: `MAX(number) + 1`
- The start screen shows the live total order count and box count for every batch.

### Коробка (Box)
| Mode | Values | Display |
|---|---|---|
| Auto | random unique 4-digit ID | `ID 6376` |
| Manual | any 4 digits | `ID 1234` |

Printed tracking number: `NBX + MMdd + HHmm + four-digit box ID`, for example `NBX102114266376`.

### Заказ (Order)
- A decoded barcode is stored exactly as encoded, without extracting or reformatting its contents.
- OCR and manual input use the legacy format `01-2345-6789` with an optional `/N` suffix.
- Duplicate orders are rejected across the entire batch; Room also enforces uniqueness per box.
- Supported by ML Kit: common 1D and 2D barcode formats, with OCR fallback for printed numbers.

### Complete Box flow
```
User taps "Complete Box & Print"
  → mark box completed in DB
  → print 2x ZPL labels (150×100mm) via TCP 9100
  → show ✓ animation (1.5s)
  → auto-create and open the next box
```

When the final report is opened, every box with zero orders is deleted and excluded from the exported report.

---

## ZPL Label Layout (100mm × 150mm portrait)

```
┌──────────────────────────────────┐
│  KG Consol                       │
│  [NBX... Code 128 barcode]       │
│  6376                            │
│  Packed in Malaga: date/time     │
│  KG123                           │
│  Orders total: 42                │
│  [QR]  KG Consol / version / dev │
└──────────────────────────────────┘
```

---

## Setup

### Requirements
- Android Studio Hedgehog+ 
- Android SDK 26+
- Kotlin 2.0+
- A Zebra printer on local WiFi (default IP: 192.168.1.168)

### Permissions (AndroidManifest)
```xml
INTERNET          — TCP printing
CAMERA            — barcode scanning
FLASHLIGHT        — torch button
WAKE_LOCK         — keep screen on
```

### Build

The repository currently has no Gradle wrapper scripts. Build from Android Studio, or use a locally installed compatible Gradle version:

```bash
gradle :app:assembleDebug
```

### First run
1. Open app → "New Batch" → enter number (KG auto-suggests next)
2. Choose box mode: automatic random 4-digit ID or manual 4 digits
3. Scan any supported barcode, recognize a printed legacy number, or type it manually
4. Tap "Complete Box & Print" → 2 labels are printed and the next box opens automatically
5. Open the final report to remove the last empty box and export the completed batch
6. Settings → enter printer IP, enable 1D barcode scanning, and run Test Print

---

## Screens

| # | Screen | Key feature |
|---|---|---|
| 1 | **Batch List** | Live order/box totals and next batch suggestion |
| 2 | **Box Screen** | Large four-digit box ID, Complete button |
| 3 | **Scan Screen** | Barcode decoding + OCR fallback + torch + manual input |
| 4 | **Settings** | Printer, scanner mode, Test Print, Keep Screen, Language |
| 5 | **Reports** | Per-batch/box expandable list, Copy + Share |

---

## Printer Configuration

| Setting | Default |
|---|---|
| IP | 192.168.1.168 |
| Port | 9100 |
| Label size | 150 × 100 mm |
| Copies per completion | 2 |
| Protocol | ZPL via raw TCP |

Error handling: all socket exceptions caught, shown in AlertDialog with human-readable message.

---

## App Icon

The launcher icon combines a warehouse box, barcode, scan frame, and `KG` monogram. Android resources include legacy, round, and adaptive variants for all supported screen densities. The high-resolution source is stored at `app/src/main/art/kg-consol-icon-source.png`.

---

## Localization

| Language | File |
|---|---|
| English (default) | `res/values/strings.xml` |
| Русский | `res/values-ru/strings.xml` |
| Español | `res/values-es/strings.xml` |

---

## Note on iOS

The original spec uses **Android-only** technologies (Kotlin, Jetpack Compose, Room, ML Kit, CameraX). An iOS version would require a full rewrite in **Swift + SwiftUI**, replacing:

| Android | iOS equivalent |
|---|---|
| Room | Core Data / GRDB |
| ML Kit Text Recognition | Vision framework |
| CameraX | AVFoundation |
| DataStore | UserDefaults |
| Hilt | — (manual DI or Needle) |
| TCP Socket | Network.framework NWConnection |
| ZPL | Same ZPL strings, different TCP client |
