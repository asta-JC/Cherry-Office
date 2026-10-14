# Cherry Office

A fast, offline document viewer for Android with a futuristic dark glossy cherry-red neon design. Built with Kotlin and Jetpack Compose (Material 3).

## Features

- **PDF viewer** — renders with Android's `PdfRenderer` as a continuous vertical page list, pinch-to-zoom around your fingers (1x–5x), one-finger pan when zoomed, double-tap to zoom, night mode, auto page indicator (`3 / 12`).
- **Word (.docx) viewer** — parses Office Open XML offline (no new dependencies) into styled HTML and shows it as white A4-like sheets on a dark backdrop, like Word's print layout. Preserves fonts, sizes, bold/italic/underline/strikethrough, colors, highlights, headings, alignment, spacing, indents, nested bullet/numbered lists, tables (borders, merges, shading, widths), images, hyperlinks (tappable), headers/footers, page numbers, page breaks and footnotes. Native WebView pinch-to-zoom + page indicator.
- **Text viewer** — monospace typography, adjustable size, search, night mode.
- **Excel / PowerPoint** — formatted tabbed previews.
- **Open from anywhere** — `ACTION_VIEW` / `ACTION_SEND` intent filters, Storage Access Framework picker, recent-files history (Room), share / open-with-another-app.

## Tech stack

Kotlin · Jetpack Compose Material 3 · Navigation 3 · MVVM + StateFlow · Hilt · Room · DataStore · PdfRenderer · WebView · Coroutines (`Dispatchers.IO` for parsing/rendering) · JUnit tests.

## Project structure

```
app/src/main/java/com/asta/cherryoffice/
├── MainActivity.kt                 # intent handling (VIEW/SEND) + nav graph wiring
├── ui/viewer/
│   ├── PdfViewer.kt                # shared-zoom-state PDF list (scale + pan + double-tap)
│   ├── WordViewer.kt               # WebView host: zoom, links, page indicator, errors
│   ├── RichDocumentViewer.kt       # Excel / PowerPoint previews
│   ├── TextViewer.kt / ViewerScreen.kt / ErrorScreen.kt / ViewerViewModel.kt
├── docx/
│   ├── DocxToHtml.kt               # pure-JVM .docx → HTML (unit-testable, zero Android deps)
│   └── DocxFileReader.kt           # Android glue: Uri I/O, image downsampling, friendly errors
├── ui/home/ ui/settings/ ui/theme/ ui/components/ ui/navigation/
├── data/ (Room: DocumentEntity/Dao/Database, repositories, models)
└── utils/DocumentUtils.kt
```

## Build & test

Requirements: Android Studio (recent), JDK 17, Android SDK 37 (minSdk 24).

```bash
./gradlew :app:assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # unit tests (19 tests, incl. 7 DOCX converter tests)
```

## Testing the DOCX viewer

A ready-made sample lives at `app/src/test/resources/sample.docx` — headings, styled runs, nested bullets, numbered steps, a merged/shaded table, an image, a hyperlink, a page break, page numbers, a footnote, header/footer:

```bash
adb push app/src/test/resources/sample.docx /sdcard/Download/sample.docx
```

Then open it from Cherry Office and check: paper-like sheets, pinch-zoom, `Page X / N` pill while scrolling, tappable link. Regenerate it with `python3 make_sample_docx.py sample.docx` (script kept outside the repo temp files; uses only the Python standard library).

## Notes

- DOCX→HTML approach was chosen over offline DOCX→PDF conversion (no reliable lightweight on-device converter) and full native Compose layout (browser engine handles tables/wrapping best). See `DocxToHtml.kt` header for details.
- Large photos embedded in .docx are downsampled (≤1600px) into app cache before display.
- Password-protected or corrupted files show a friendly message, never a crash.
