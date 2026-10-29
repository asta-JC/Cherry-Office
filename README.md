<div align="center">

# 🍒 Cherry Office

<p align="center">
  <img src="app/src/main/res/drawable/icon.png" width="108" alt="Cherry Office Logo" />
</p>

> A fast, offline document viewer for Android featuring PDF, Word (.docx), Excel (.xls / .xlsx), PowerPoint slide previews, images, and text viewing with a quiet cherry-accented interface. 📄✨

</div>

---

## 📱 Overview

**Cherry Office** is a high-performance offline document viewing suite for Android built with Jetpack Compose and Material 3. Designed with a restrained cherry-accented design, it opens PDFs, Word documents (.docx), spreadsheets, presentations, and plain text files with zero external cloud dependencies.

---

## Version 1.1.0

- Visual PowerPoint previews with vertical scrolling, pictures, shapes, lines, tables, and basic charts.
- Automatic text fitting prevents slide text and final lines from being clipped.
- Improved PDF text selection, document reading, and spreadsheet compatibility.
- Subtle cherry accents, with the original app icon preserved.

## ✨ Key Features

- 📕 **PDF Reading & Copy**: Native page rendering via Android's `PdfRenderer`, continuous vertical scrolling, focal-point pinch-to-zoom (1x–5x), one-finger pan, and adjustable text selection.
- 📝 **Zero-Dependency Word (.docx) Viewer**: Parses Office Open XML directly offline into styled HTML and displays it in white A4 print layout sheets with full table, image, bullet, styling, and footnote support.
- 📊 **Spreadsheet Reading**: Multi-sheet binary `.xls`, `.xlsx`, `.xlsm`, CSV and TSV previews with number/date formatting, cell styles, saved formula results, search, and cell copying.
- 📽️ **PowerPoint Slide Preview**: Vertically scrolling `.pptx` slides with positioned text, embedded PNG/JPEG/GIF/WebP pictures, common shapes, lines, tables, and basic saved-value bar/line/pie charts. Pinch zoom and expandable slide text. Custom shapes and advanced chart styling may differ; animations are static and unsupported diagrams show a preview note.
- 🖼️ **Image Viewing**: Device-supported images with pinch zoom and pan.
- 📜 **Monospace Text Viewer**: Clean typography for `.txt` and source files with adjustable font sizes, search, and theme-aware controls.
- 🌐 **Open from Anywhere**: Handles `ACTION_VIEW` and `ACTION_SEND` intents, Storage Access Framework (SAF) file picker, and recent files history.

---

## 📄 Supported Formats

| Document Format | Engine / Technology | Key Highlights |
| :--- | :--- | :--- |
| **PDF** (`.pdf`) | Android `PdfRenderer` | Continuous scroll, pinch-zoom (1x–5x), page navigation and adjustable text selection |
| **Word** (`.docx`) | Custom OOXML → HTML Parser | Offline styling, tables, images, hyperlinks, A4 sheet layout |
| **Excel** (`.xls`, `.xlsx`, `.xlsm`, `.xltx`) | HSSF / OOXML spreadsheet readers | Multi-sheet grid, saved values, number/date formats, cell styles |
| **PowerPoint** (`.pptx`, `.pptm`, `.potx`) | OOXML → SVG/HTML | Vertical slides, pictures, shapes, lines, tables, basic charts |
| **CSV / TSV** (`.csv`, `.tsv`) | Delimited-text reader | Quoted multiline fields, BOM handling, sheet grid |
| **Text** (`.txt`, `.md`, `.log`, `.json`, `.xml`, `.yaml`) | Native Compose Typography | Selectable text, search highlights, adjustable size, night mode |
| **Images** | Device image decoder | Fit-to-screen, pinch zoom and pan |
| **Legacy / other Office** (`.doc`, `.ppt`, `.odt`, `.ods`, `.odp`, `.rtf`) | External app handoff | Clear compatibility message and open-with chooser |

Spreadsheet previews display up to 10,000 populated rows and 256 columns per sheet. Preview notes disclose limits, merged-cell handling, and formula behavior. Formulas use saved results and are not recalculated. Advanced Office layouts can differ from the authoring app; export as PDF for consistent print layout.

The app offers System, Light, Dark and AMOLED appearances. Document colors and pictures stay original in every appearance. PDF copying uses embedded text with long-press selection and adjustable handles; scanned pages require OCR, which is not included. Keep Screen On applies only during reading, and the saved default PDF zoom is applied when opening files.

Binary XLS compatibility uses a reproducible `office-compat` module around Apache POI 5.5.1. PDF text extraction uses PDFBox Android 2.0.27.0; page rendering remains native. Upstream license notices are bundled in assets.

---

## 🛠️ Tech Stack & Architecture

- **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) & [Material 3](https://m3.material.io/)
- **Navigation**: Jetpack Navigation 3 (`androidx.navigation3`)
- **Document Engines**: Android `PdfRenderer`, pure-JVM OOXML parser, Android `WebView`
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/) (Dagger)
- **Persistence**: Room Database (recent files history) & DataStore Preferences
- **Concurrency**: Kotlin Coroutines (`Dispatchers.IO`) & `StateFlow`

---

## 📂 Project Structure

```tree
com.asta.cherryoffice/
├── data/                   # Document entities, Room database, repositories
├── di/                     # Hilt DI modules
├── docx/                   # Pure-JVM DOCX to HTML parser & document file reader
├── ui/                     # UI components, home screen, settings, theme
├── viewers/                # Dedicated viewers: PdfViewer, WordViewer, RichDocumentViewer, TextViewer
├── utils/                  # Document URI helpers, format detection, MIME resolvers
├── CherryOfficeApplication.kt # Application class
└── MainActivity.kt         # Entry point activity & intent handler
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17 or higher
- Android SDK 24+ (Target SDK 34+)

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone https://github.com/asta-JC/Cherry-Office.git
   cd CherryOffice
   ```

2. **Open in Android Studio**:
   - Open Android Studio and select **Open**.
   - Navigate to the cloned `CherryOffice` directory and let Gradle sync.

3. **Build & Run**:
   - Connect a physical Android device.
   - Run the app via Android Studio or execute via terminal:
     ```bash
     ./gradlew :app:assembleDebug
     ```

4. **Run Unit Tests**:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

---

## 📄 License

This project is developed as part of Asta's project series. Feel free to use and adapt for personal and educational use!

---

<p align="center">
  Made with ❤️ & 🍒 by Asta
</p>
