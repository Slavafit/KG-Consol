# KG Consol — Android App

> Warehouse barcode scanning & Zebra label printing application.

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
| Camera / OCR | CameraX + ML Kit Text Recognition |
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
│   ├── preferences/    AppPreferences.kt  — DataStore (IP, port, language, keep-on)
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
│   ├── scan/           ScanScreen.kt      — CameraX scanner + manual input
│   ├── settings/       SettingsScreen.kt  — Printer IP, language, screen-on
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

### Коробка (Box)
| Mode | Values | Display |
|---|---|---|
| Auto | 11111, 22222 … 99999 (repeating digits) | `ID 11111` |
| Manual | any 5 digits | `ID 12345` |

### Заказ (Order)
- Format: `01-2345-6789` (regex `^\d{2}-\d{4}-\d{4}$`)
- Unique constraint per box (Room UNIQUE index)

### Complete Box flow
```
User taps "Complete Box & Print"
  → mark box completed in DB
  → print 2x ZPL labels (150×100mm) via TCP 9100
  → show ✓ animation (1.5s)
  → auto-create next box with next auto-number
  → navigate to new box screen
```

---

## ZPL Label Layout (150mm × 100mm)

```
┌──────────────────────────────────┐
│  KG123                           │
│                                  │
│  ID 11111       [QR code]        │
│                                  │
│  ───────────────                 │
│  Orders: 42                      │
│  28.05.2026 14:33                │
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
```bash
./gradlew assembleRelease
```

### First run
1. Open app → "New Batch" → enter number (KG auto-suggests next)
2. Choose box mode: Auto (11111…) or Manual (5 digits)
3. Scan orders with camera or type manually
4. Tap "Complete Box & Print" → 2 labels printed → next box opens automatically
5. Settings → enter printer IP → Test Print

---

## Screens

| # | Screen | Key feature |
|---|---|---|
| 1 | **Batch List** | Auto-suggest next batch number |
| 2 | **Box Screen** | Giant `ID XXXXX` display, Complete button |
| 3 | **Scan Screen** | CameraX + ML Kit + torch button + manual input |
| 4 | **Settings** | Printer IP/port, Test Print, Keep Screen, Language |
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
