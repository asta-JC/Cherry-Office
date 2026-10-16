<div align="center">

# 🍒 Cherry Office

<p align="center">
  <img src="app/src/main/res/drawable/icon.png" width="108" alt="Cherry Office Logo" />
</p>

> A fast, offline document viewer for Android featuring high-fidelity PDF, Word (.docx), Excel, PowerPoint, and text rendering with a futuristic cherry dark theme. 📄✨

</div>

---

## 📱 Overview

**Cherry Office** is a high-performance offline document viewing suite for Android built with Jetpack Compose and Material 3. Designed with a dark neon cherry aesthetic, it opens PDFs, Word documents (.docx), spreadsheets, presentations, and plain text files with zero external cloud dependencies.

---

## ✨ Key Features

- 📕 **High-Fidelity PDF Viewer**: Native page rendering via Android's `PdfRenderer`, continuous vertical scrolling, pinch-to-zoom (1x–5x), one-finger pan, and night mode.
- 📝 **Zero-Dependency Word (.docx) Viewer**: Parses Office Open XML directly offline into styled HTML and displays it in white A4 print layout sheets with full table, image, bullet, styling, and footnote support.
- 📊 **Excel & PowerPoint Previews**: Formatted tabbed preview cards for spreadsheets (`.xls`, `.xlsx`) and slide decks (`.ppt`, `.pptx`).
- 📜 **Monospace Text Viewer**: Clean typography for `.txt` and source files with adjustable font sizes, search, and dark mode.
- 🌐 **Open from Anywhere**: Handles `ACTION_VIEW` and `ACTION_SEND` intents, Storage Access Framework (SAF) file picker, and recent files history.

---

## 📄 Supported Formats

| Document Format | Engine / Technology | Key Highlights |
| :--- | :--- | :--- |
| **PDF** (`.pdf`) | Android `PdfRenderer` | Continuous scroll, pinch-zoom (1x–5x), page pill indicator |
| **Word** (`.docx`) | Custom OOXML → HTML Parser | Offline styling, tables, images, hyperlinks, A4 sheet layout |
| **Excel** (`.xlsx`, `.xls`) | Formatted tabular engine | Multi-sheet previews, row/column alignment |
| **PowerPoint** (`.pptx`, `.ppt`) | Presentation preview engine | Slide-by-slide card previews |
| **Text** (`.txt`, `.csv`) | Native Compose Typography | Monospace font, search, night mode |

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
   - Connect an Android device or start an emulator.
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
