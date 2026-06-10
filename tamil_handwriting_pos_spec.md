# Maligai — Tamil Handwriting POS (Android Native, Kotlin)

## Overview

**Maligai** is a handwriting-first, offline billing app for Tamil kirana / maligai-kadai (grocery) shops.

Input method: the shopkeeper writes **one full line per bill row** on a canvas with a finger or stylus:

```
[ item name ]  [ optional weight ]  -  [ amount in ₹ ]
```

ML Kit recognizes the ink → `LineParser` extracts displayText and lineTotal → matching inventory items are suggested → one tap adds the row to the bill.

The app reuses the proven architecture and shell of the **Makulu** eatery POS, but replaces the table + menu-grid homepage with a handwriting canvas, and swaps the "tables" concept for **parked bills as top tabs**. It adds a kirana-specific **customer credit (loan / கடன்) ledger**.

- No internet required for daily use. No account. No cloud. Fully offline.
- Internet is needed exactly once: to download the ML Kit Tamil handwriting model (~10 MB) on first launch.

App name: **Maligai** · Package: `com.maligai.app`

---

## Locked Decisions (from requirements Q&A)

| Topic | Decision |
|---|---|
| Canvas input script | **Tamil, English, or mixed** (e.g. அரிசி 1கி, aarokya paal, Horlicks) |
| Recognition engine | ML Kit Digital Ink Recognition — Tamil (`ta`, primary, required) + optional English (`en`). On-device after one-time download per language. |
| Line format | `item [weight] - amount` on one stroke. Amount after hyphen = line total. Left side stored **verbatim** — never rewritten from catalog. |
| Verbatim display rule | Whatever is written on the left of `-` is stored as `itemName` and shown as-is on bill, receipt, ledger, CSV. No catalog-name substitution, no unit normalisation. |
| Item matching | Parsed item/weight text fuzzy-scored against inventory (`nameTamil`, `nameLatin`, combined, token overlap). App **learns from corrections** (matchHint → itemId). |
| Item definition | Tamil name (required), optional Latin name for receipt. |
| Unit model (per item) | One of: **WEIGHT** (kg/g), **VOLUME** (l/ml), **COUNT** (piece/packet). Price stored per base unit. Parsed-line rows use `unitLabel=""` (weight is inside displayText). |
| Quantity entry | For parsed lines: quantity=1, weight lives in displayText. Fallback keypad for amount-only corrections. |
| Bills | **Multiple parked bills** shown as renameable **top tabs** (auto "Bill 1/2…"), up to ~10. No "tables". |
| Loans (கடன்) | A completed bill can be **marked as loan**: customer name + optional phone, per-customer outstanding balance, part-payment recording, on a separate **Admin → Loans** page. |
| GST | **Optional toggle**. When on: CGST + SGST, prices GST-inclusive, breakup printed on receipt. Frozen per bill at completion. **No discounts.** |
| Receipt item names | **Configurable**: English / transliteration (default, reliable) OR Tamil rendered as a bitmap image. |
| Security | Reused from Makulu: device biometric gate + 4-digit admin PIN + one security question. |
| Printing | Bluetooth Classic SPP, ESC/POS, supports 58 mm and 80 mm paper. |
| Build workflow | Fresh project, minimal file layout, source edited here and compiled on a separate laptop. |

---

## Handwriting Line Format

### Input pattern
```
பால் - 30              → displayText="பால்"            lineTotal=30
அரிசி 1கி - 120       → displayText="அரிசி 1கி"       lineTotal=120  matchHint="அரிசி"
aarokya paal - 45      → displayText="aarokya paal"     lineTotal=45
sugar 500g - 25        → displayText="sugar 500g"       lineTotal=25   matchHint="sugar"
```

### Parse rules
- Split on **last `-`** (hyphens in item names handled correctly).
- **Right of hyphen** → strip `₹ / Rs / rs`, normalise Tamil digits → `lineTotal`.
- **Left of hyphen** → stored verbatim as `displayText`; no conversion.
- **matchHint** (internal only) → strip trailing weight token (e.g. `1கி`, `500g`, `1kg`) from a copy of displayText for inventory matching. Bill always shows original `displayText`.

### ParseConfidence
| Level | Condition |
|---|---|
| `HIGH` | Hyphen found + valid amount in ₹5–₹10,000 + non-empty item text |
| `MEDIUM` | Item text only (no hyphen/amount) or amount out of range |
| `LOW` | Amount only, empty input, or unparseable |

### Grace fallbacks
| Parsed result | UX |
|---|---|
| Item match + amount (HIGH) | Chip shows verbatim preview; one tap adds immediately |
| Item match, no amount | Chip shows recognized text; ConfirmLineDialog for amount only |
| Amount parsed, no inventory match | Shows verbatim displayText chip; one-tap or confirm |
| Fails entirely | Raw ML candidates as chips + confirm |

---

## Navigation — Makulu-style Sidebar

### Top bar (fixed, never changes)
```
[☰]  Maligai (clickable — taps Home from any screen)  …  [●printer dot]
```

### Drawer structure
```
Maligai
────────────────
New Bill          ← always visible
Today's Bills     ← always visible
Shop Spending     ← always visible, no PIN
────────────────
Admin             ← shown while locked; tap → PIN entry
[ after unlock: ]
  Items
  Ledger
  Loans
  Spending
  Analysis
  CSV Backup
  GST & Receipt
  Printer
────────────────
About
```

### Admin session rules
- Admin PIN unlock valid for **30 seconds of inactivity**.
- Timer resets on every admin navigation action.
- On timeout: admin items hidden from drawer; if on admin screen, user is navigated to Home.
- PIN gate: `AdminPinEntry` composable (gets settings from `AdminViewModel` internally).

---

## Tech Stack

| Layer | Choice | Reason |
|---|---|---|
| Language | Kotlin 2.0 | Native performance, best Android support |
| UI | Jetpack Compose + Material 3 | Modern, less boilerplate (same as Makulu) |
| DI | Hilt | Industry standard (same as Makulu) |
| Handwriting Recognition | ML Kit Digital Ink Recognition (`ta`) | Offline, Tamil supported, on-device |
| Local DB | Room (SQLite) | Inventory, bills, loans, settings |
| Charts | Vico (Compose) | Analysis bar/pie charts |
| Printing | ESC/POS over Bluetooth Classic SPP | Thermal printer support (ported from Makulu) |
| Auth | AndroidX Biometric | Device-lock gate |
| Min SDK | API 26 (Android 8.0) | ML Kit Digital Ink minimum |
| Target SDK | API 35 | Latest |

---

## Architecture (MVVM + Repository, mirrors Makulu)

```
Handwriting Canvas (Tamil)
        │  stroke end → debounce 400–600ms
        ▼
Recognizer.kt (ML Kit ta) ── candidates ──► match vs items + learned corrections
        │
        ▼
Suggestion chips (top 5) ──► Quantity picker (unit-aware) ──► Active bill (one of N parked tabs)
        │                                                          │
        ▼                                                          ▼
   Complete ──► Ledger + CSV                              Print (PrinterManager ESC/POS)
        │
        └─ optionally Mark as Loan ──► Admin Loans page
```

```
UI Layer       → Jetpack Compose (Material 3)
ViewModel      → StateFlow, business logic
Repository     → Data operations
Database       → Room (SQLite)
Services       → Recognizer, PrinterManager, CsvManager
```

---

## Project Structure (`app/src/main/java/com/maligai/app/`)

```
com/maligai/app/
├── Database.kt        — Entities, DAOs, Room DB, TypeConverters
├── Repository.kt      — Repositories, Hilt module, all ViewModels
├── Recognizer.kt      — ML Kit Tamil wrapper + correction-learning + matching
├── Theme.kt           — Colors, Tamil typography (Noto Sans Tamil), Material 3 theme
├── PrinterManager.kt  — BT SPP, ESC/POS, Tamil-to-bitmap raster, receipt/loan printing
├── CsvManager.kt      — CSV export, monthly + latest, dual-folder save, health check
├── BillScreen.kt      — Homepage: parked tabs, canvas, suggestions, quantity, bill, actions
├── AdminScreen.kt     — PIN + admin sections
└── MainActivity.kt    — Entry, biometric gate, first-time setup, NavHost, sidebar
```

Config files: `settings.gradle.kts`, root + app `build.gradle.kts`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `res/values/themes.xml`, `app/proguard-rules.pro`.

---

## Data Model (Room)

### MenuItem
```kotlin
@Entity(tableName = "items")
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameTamil: String,        // "அரிசி" — shown on canvas suggestions & UI
    val nameLatin: String = "",   // optional, used on English receipt
    val unitType: String,         // WEIGHT | VOLUME | COUNT
    val unitLabel: String,        // "kg", "litre", "packet", "piece"...
    val pricePerUnit: Double,     // price per 1 base unit (per kg / per litre / per piece)
    val available: Boolean = true,
    val sortOrder: Int = 0
)
```

### Bill / BillItem
```kotlin
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,             // "Bill 1" or renamed (e.g. customer name)
    val status: String,           // DRAFT | COMPLETE
    val subtotal: Double = 0.0,
    val cgst: Double = 0.0,       // frozen at completion
    val sgst: Double = 0.0,
    val total: Double = 0.0,
    val isLoan: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "bill_items")
data class BillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val itemName: String,         // Tamil name snapshot
    val itemNameLatin: String = "",
    val unitType: String,
    val unitLabel: String,
    val quantity: Double,         // in base units (kg / litre / pieces)
    val unitPrice: Double,
    val lineTotal: Double
)
```

### Customer / Loan / LoanPayment
```kotlin
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = ""
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val customerId: Long,
    val amount: Double,           // original loan amount (bill total)
    val outstanding: Double,      // remaining after payments
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "loan_payments")
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amount: Double,
    val paidAt: Long = System.currentTimeMillis()
)
```

### ShopSpend / AppSettings / ReceiptField / RecognitionCorrection
```kotlin
@Entity(tableName = "shop_spends")
data class ShopSpend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val spentAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val adminPinHash: String = "",
    val securityQuestion: String = "",
    val securityAnswerHash: String = "",
    val gstEnabled: Boolean = false,
    val cgstPercent: Double = 0.0,
    val sgstPercent: Double = 0.0,
    val receiptNameMode: String = "ENGLISH", // ENGLISH | TAMIL_IMAGE
    val footerText: String = "Nandri, varuga!",
    val paperWidthMm: Int = 80,
    val rupeeFix: Boolean = false,
    val printerMac: String = "",
    val printerName: String = ""
)

@Entity(tableName = "receipt_fields")
data class ReceiptField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,            // "Shop Name", "Address", "Phone", "GSTIN", custom...
    val value: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

// The accuracy moat: learned recognized-text → item mapping
@Entity(tableName = "recognition_corrections", primaryKeys = ["recognizedText"])
data class RecognitionCorrection(
    val recognizedText: String,
    val itemId: Long,
    val hitCount: Int = 1
)
```

---

## Homepage / Billing Flow (`BillScreen.kt`)

### Layout (top → bottom)
1. **Top bar** — Maligai heading (home), hamburger (sidebar), printer status dot (🟢/🔴).
2. **Parked-bill tabs row** — `Bill 1`, `Bill 2`, … + `[+]` to open a new bill. Tap to switch.
3. **Bill name (optional)** — single text field bound to `Bill.name` (shown on receipt and Today's Bills). Saves on focus loss.
4. **Running bill list** — each line: item name · quantity + unit · line total. Grand total always visible. Edit / delete per line.
5. **Canvas (≈ bottom 55–60%)** — white surface; finger/stylus writing.
6. **Action row** — `Complete`, `Print Receipt`, `Mark as Loan`, `Clear canvas`.

### Recognition behaviour
- On stroke end, start a **400–600 ms debounce** (tunable 300–700 ms) to allow multi-stroke Tamil letters to finish.
- After the pause, recognize the full ink → up to 5 candidates.
- Each candidate is matched against inventory (item name `contains` / prefix / learned-correction map) → up to **5 suggestion chips** (large, ≥ 56 dp touch targets).
- Selecting a chip whose recognized text differs from the item name **teaches** the mapping (`RecognitionCorrection`).
- After an item is added, the canvas auto-clears, ready for the next word.

### Quantity picker (unit-aware)
- **WEIGHT quick**: 100 g · 200 g · 250 g · 500 g · 750 g · 1 kg — scrollable 1.25 kg → 50 kg — custom input (`250g`, `1.5kg`).
- **VOLUME quick**: 100 ml · 200 ml · 250 ml · 500 ml · 1 L — scrollable 1.25 L → 20 L — custom input (`500ml`, `1L`).
- **COUNT quick**: ¼ · ½ · ¾ · 1 · 2 · 3 · 4 · 5 — scrollable 6 → 100 — custom input (`1/4`, `0.5`, `10`).
- Catalog pick and inline edit both use the same picker; line total = quantity × pricePerUnit (₹ override optional).

### Completed bill edit (Makulu-style)
- **Today's Bills** and **Admin → Ledger**: tap a completed bill → detail dialog (items, GST, total).
- **Update** reopens the bill as a draft on the homepage for editing; **Print** reprints without reopening.
- Re-completing preserves kadan flag and syncs linked loan outstanding to the new total.

### Kadan customer fields
- Name: letters (Latin + Tamil), spaces, `.` `-` only; 2–60 chars; text keyboard.
- Phone (optional): digits only, exactly 10 when provided; phone keyboard.
- If bill name is still default (`Bill N`), kadan save copies customer name to `Bill.name`.

### Actions
- **Complete** → freezes GST (if enabled), writes bill + items to ledger, triggers CSV export, frees the tab.
- **Print Receipt** → ESC/POS print (English or Tamil-image names per setting).
- **Mark as Loan** → dialog: pick/create customer (name + optional phone) → completes bill, creates `Loan` with outstanding = total, listed under Admin → Loans.
- **Clear** → clears the active bill (with confirm if it has items).

---

## Admin Panel (`AdminScreen.kt`) — 4-digit PIN

Sections:
1. **Items** — add/edit/delete; Tamil name, optional Latin name, unit type, unit label, price per unit, availability, drag-reorder. No categories (handwriting-first input).
2. **Ledger** — 4 tabs (History / Today / This Week / This Month). Summary (count + revenue) + list. Tap a bill → detail with Update / Print. Delete removes linked kadan loan.
3. **Loans** — list customers with outstanding balance; open customer → their loan bills + payment history + **Record Payment**; settling reduces outstanding to zero.
4. **Shop Spending** — add (name + amount + optional date), inline edit, delete; same 4-tab view.
5. **Analysis** — period filter (Today / Week / Month); Revenue vs Spending vs Net + outstanding loans; Items sold as Bar / Pie / Ranked list (top 20 + Show All).
6. **CSV Backup** — manual export, status/health of both folders.
7. **GST & Receipt Settings** — GST toggle + CGST/SGST %; header fields (add/remove/reorder, live preview); `receiptNameMode` (English vs Tamil-image); footer text.
8. **Printer Settings** — paired device, paper width 58/80 mm, reconnect/forget, ₹ symbol fix.

### Sidebar (hamburger)
```
📝 Today's Bills      ← no PIN; tap bill → detail, Update, Print
💰 Shop Spending      ← no PIN (quick add / edit / delete last entries)
🔒 Admin              ← 4-digit PIN
    └ Items · Ledger · Loans · Analysis · CSV · GST & Receipt · Printer
ℹ️ About / Version
```

---

## Printing (`PrinterManager.kt`)

- Bluetooth Classic SPP, UUID `00001101-0000-1000-8000-00805F9B34FB`, ESC/POS.
- Auto-reconnect on app open if a printer was previously paired.
- Paper width 58 mm / 80 mm → line formatting auto-scales.
- **Receipt name mode**:
  - `ENGLISH` → item `nameLatin` (fallback to a transliteration / the Tamil bytes) printed as text. Reliable on all printers.
  - `TAMIL_IMAGE` → each Tamil name (or whole receipt body) rendered to a monochrome bitmap and sent as an ESC/POS raster image, so Tamil prints correctly even on ASCII-only printers.
- Receipt content: configurable header fields → order no / date-time → items (name, qty, price) → subtotal → CGST/SGST (if enabled) → grand total → footer → cut.
- ₹ symbol fix: optional codepage command (`ESC t 66` / WPC1252).

### Key ESC/POS commands
| Function | Command |
|---|---|
| Initialize | `ESC @` (0x1B 0x40) |
| Center / Left align | `ESC a 1` / `ESC a 0` |
| Bold on / off | `ESC E 1` / `ESC E 0` |
| Char size | `GS ! n` |
| Raster bitmap | `GS v 0` |
| Feed + cut | `LF` … `GS V 66 0` |

---

## CSV Backup (`CsvManager.kt`)

- Auto-export on every bill completion and spending change.
- Files (dated archive + always-updated `_latest`), saved to **two locations** (app-internal + `Documents/MaligaiBackup/`):
  - `maligai_bills_YYYY-MM.csv` / `maligai_bills_latest.csv`
  - `maligai_bill_items_YYYY-MM.csv`
  - `maligai_spending_YYYY-MM.csv`
  - `maligai_loans_YYYY-MM.csv`
  - `maligai_items_YYYY-MM.csv`
  - `maligai_consolidated_YYYY-MM.csv`
- Room DB is the source of truth; the app works fully even if CSVs are deleted.
- Folder health check warns if either location is not writable.

---

## Security & First-Launch (`MainActivity.kt`)

1. App opens → **device biometric / screen-lock** gate.
2. First-time setup (fresh install):
   - Set 4-digit admin PIN (+ confirm).
   - Pick 1 of 5 security questions → answer (lowercase alphabets only).
   - **Download Tamil handwriting pack** screen (needs internet once; shows clear progress).
   - Optional printer pairing (or Skip).
3. Lands on the Homepage (canvas).
- Admin PIN re-prompts after 30 s of inactivity.

---

## Permissions (`AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.INTERNET" /> <!-- first model download only -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

---

## UX Rules — Non-Negotiable

| Rule | Why |
|---|---|
| Canvas clears after each item added | Clean state, ready for next word |
| Suggestions appear within ~800 ms of stroke end | Must feel instant |
| Max 5 suggestions, large touch targets (≥ 56 dp) | Fat-finger friendly |
| No text keyboard ever on the billing flow | Keyboard defeats the purpose |
| Bill + grand total visible at all times | Seller needs the running total |
| Tamil font: Noto Sans Tamil | Readability on cheap screens |
| Values ₹5 – ₹10,000, 1 – 100 items per bill | Must stay fast and lag-free |

---

## Known Risks & Mitigations

| Risk | Mitigation |
|---|---|
| ML Kit Tamil accuracy ~75–85% | Learned-correction map + top-5 suggestions → near-perfect for that shop over time |
| Multi-stroke Tamil letters need timing | Tune debounce 300–700 ms on the lowest-end target device early |
| Tamil on ASCII-only thermal printers | `TAMIL_IMAGE` raster mode; English/transliteration is the safe default |
| First launch needs internet (model download) | Clear "Downloading Tamil language pack" screen |
| Cheap-phone canvas lag | Test on lowest-end target device early |

---

## Build Order (Milestones)

1. Project skeleton: Gradle, `libs.versions.toml` (+ ML Kit digital-ink), manifest, theme, Room DB.
2. Recognizer + canvas + suggestion matching end-to-end (write → suggest → add to bill).
3. Quantity picker + parked-bill tabs + Complete → ledger.
4. Admin: Items, Ledger → then Loans, Spending, Analysis, GST/Receipt, CSV.
5. PrinterManager: English first, then Tamil-image raster.
6. First-launch setup + biometric/PIN.

Rule: do not polish admin until the core billing flow (write → suggest → add → complete) works perfectly end-to-end.
