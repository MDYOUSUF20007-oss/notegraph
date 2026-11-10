# NoteGraph

> **One-line pitch:** A cross-platform offline productivity suite combining an ultra-fast Classroom & Freeform Whiteboard for **Windows & macOS (`desktop/`)** with a tri-theme **Android (`app/`)** note-taking app and seamless `.zip` vault sync.

![Status](https://img.shields.io/badge/status-active%20development-purple)
![Platforms](https://img.shields.io/badge/platform-Android%20%7C%20Windows%20%7C%20macOS-blue)

NoteGraph bridges freeform digital ink, live classroom presentation tools, structured to-do checklists, and Obsidian-style `[[Wiki-Links]]` without database bloat.

## Downloads (Releases)

Grab prebuilt installers and packages from the [**GitHub Releases**](https://github.com/MDYOUSUF20007-oss/notegraph/releases/latest) page:
- **Android:** `notegraph.apk`
- **Windows (x64):** Setup Installer (`*-Setup.exe`) & standalone Portable (`*-Portable.exe`)
- **macOS (Apple Silicon `arm64` & Intel `x64`):** `.dmg` & `.zip` bundles

---

## Features

### NoteGraph Board (Windows & macOS Desktop App — `desktop/`)
- **Zero-Lag Classroom Whiteboard:** Dual-layer GPU-friendly HTML5 canvas with `getCoalescedEvents()` high-frequency pointer ink, viewport culling, spatial Note/Checklist cards, Bezier flowchart connectors, geometric shape snapping (lines, circles, rectangles), and **Lasso** stroke selection/nudging.
- **Built for Live & Online Teaching:**
  - **Fullscreen Classroom Zen Mode (`F11`)**, integrated **Class Stopwatch / 25m Pomodoro**, and multi-slide lecture decks (`PageUp` / `PageDown`).
  - **Rainbow Vanishing Laser Pointer** (`1.5s` glowing fade trail), Highlighter, Vector Arrows, and **Dots / Cornell Ruled / XY Coordinate Axes / Blackboard** grids.
  - **Offline PDF Lecture Markup (`pdfjs-dist`):** Import PDFs as sequential lecture slides or canvas cards and draw/highlight directly over pages with zero interference, then export all slides back to sharp **PNG** or **Multi-Page PDF**.
  - **`Ctrl+V` Instant Screenshot Paste** directly onto any board slide.
- **Clutter-Free Notes, Checklists & Graph:** Clean split editor with live interactive checkboxes (`[x]`), `[[Wiki-Link]]` backlinks, `#tags`, `Ctrl+K` Searchable Command/Note Picker, Follow-Up Check-in Reminder dates (`🔔`), drag-and-drop customizable folders (`📚 Study`, `💼 Work`, `🎮 Gaming`, `🛒 Shopping`, `⏰ Reminder`), and a 2D force-directed **Knowledge Graph**.
- **Android ↔ Desktop Sync:** One-click import/export of `NoteGraph_Vault_Backup.zip` (`notes.json`).

### NoteGraph Android App (`app/`)
- **One App, Three Signature Themes:** Toggle between **Deep Purple AMOLED** (`#000000`), **Kindle Parchment** (warm eye-care paper), and **Matte Studio** (anodized slate).
- **Customizable Drag-and-Drop Folders & Interactive Checklists:** Organize notes via drag-and-drop with editable folder names, icons, and colors; write notes with embedded interactive to-do checklists underneath.
- **Handwriting, 3D Page-Curl Reader, Lecture Audio & Exports:** Smooth ink canvas, Rainbow Vanishing Pen, 60 FPS book-curl reader, synchronized audio, ML Kit OCR, and multi-format exports (`.pdf`, `.docx`, `.pptx`, `.png`, `.md`, `.zip`).

---

## Development & Building Locally

### Desktop App (`desktop/`)
Requirements: Node.js 20+
```bash
cd desktop
npm install
npm test
npm start
```
Package installers locally:
```bash
# Windows (runs on Windows)
npm run pack:win

# macOS (runs on macOS)
npm run pack:mac
```

### Android App (`app/`)
Requirements: Android Studio / JDK 17+ / Android SDK 26+
```bash
./gradlew assembleDebug
```
All three platforms (Android, Windows, and macOS) are automatically built and published on every push to `main` via `.github/workflows/main.yml`.

## License

Built by [MDYOUSUF](https://github.com/MDYOUSUF20007-oss).
