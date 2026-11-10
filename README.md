# NoteGraph

This is my app. I've been trying to build a solid note-taking app from scratch, and it's still pretty new. Test it out and give me honest feedback. I've been reading through Reddit posts to see what people actually want and what annoys them in other apps, and I'm trying to add as many of those fixes as I can. You all can contribute.

Everything works offline on your device. No login, no subscription, and no forced cloud.

## Why build another note app?

Most apps get one part right and miss the rest:
- PDF and stylus apps are great for writing, but they rarely include proper note linking or a graph view.
- Markdown graph apps are great for linking notes, but they feel clunky for live teaching, sketches, or scribbling on PDFs.
- Browser whiteboards get sluggish halfway through a long lecture or math derivation.

NoteGraph keeps an **Android app (`app/`)** and a **Windows / macOS board app (`desktop/`)** in one repo, with symmetric cross-OS sync (Shared Cloud Folder, Direct Wi-Fi LAN P2P, and `.zip` Sync Package v2) so notes, folders, sketches, PDF markup, and graph links stay in sync everywhere.

## Downloads

Prebuilt installers and APKs are on the [Releases page](https://github.com/MDYOUSUF20007-oss/notegraph/releases/latest):
- **Android:** `notegraph.apk`
- **Windows (x64):** `NoteGraph-Board-2.0.0-Windows-x64-Setup.exe` or `NoteGraph-Board-2.0.0-Windows-x64-Portable.exe`
- **macOS (Apple Silicon & Intel):** `NoteGraph-Board-2.0.0-macOS-arm64.dmg` / `NoteGraph-Board-2.0.0-macOS-x64.dmg` (plus `.zip` builds — unsigned CI builds, so right-click -> Open on first launch if macOS prompts you)

## What works right now

### Bespoke Luxury UI & Runtime Interface Engine Switcher (Android)
- **Three curated zero-gradient themes:** Switch anytime between **AMOLED Black** (`#000000` / `#121212`, `1px` `#262626` borders, `#8B5CF6` accent), **Kindle Parchment** (`#F4F1EA` warm paper), and **Matte Minimalist** (`#1E2229` charcoal slate).
- **Technical blueprint Knowledge Graph:** Minimalist circle & rounded-rectangle hub nodes, ultra-thin (`1px`, `30%` opacity) curved vector Bezier edges over a drafting grid, monospace tags & `[x,y]` coordinates, and `#8B5CF6` restraint for active selections and clusters.
- **Edge-to-edge canvas & restrained chrome:** Full-bleed ink and PDF markup surfaces, `1.dp` hairline floating pills with zero drop shadows, Serif prose + Monospace chrome hierarchy, and physically anchored 3D spine-hinge page curls in Book Reader.
- **Runtime Interface Engine switcher:** Toggle live between **Bespoke Luxury (Default)** and **Classic UI** (legacy `ObsidianCard` gradients & specs) at the bottom of **Appearance & Vault Settings** with zero restart.

### Desktop & Mac - NoteGraph Board (`desktop/`)
Built for online classes, derivations, and freeform boards:
- Infinite canvas with `Space` + drag (or middle mouse) to pan, scroll zoom, sticky notes, interactive checklist cards, and card connectors
- Pen, highlighter, shape snapping (straight lines, circles, rectangles), smooth directional lasso move/scale/delete, and a bounded-memory O(1) fading rainbow laser pointer
- Switchable grids: Dots, Cornell ruled, XY graph axes, or Blank
- Import PDFs as separate lecture slides or side-by-side canvas pages, paste screenshots with `Ctrl+V`, and export to PNG, multi-page PDF, or Markdown
- `F11` fullscreen Zen mode, class stopwatch / 25-min timer, follow-up reminder dates, `Ctrl+K` quick command palette, and a 2D link graph
- Direct Wi-Fi LAN peer sync, Shared Cloud Folder sync (Google Drive / OneDrive / Dropbox / iCloud / Syncthing), and `.zip` Sync Package v2 with full tombstone deletion propagation

### Android App (`app/`)
- **Cross-OS sync & Field Guide:** Sync every note, folder, checklist, ink stroke, PDF annotation, and deletion across Android, Windows & macOS via Shared Cloud Folder, Wi-Fi LAN peer-to-peer sync, or `.zip` v2; plus an interactive in-app **Tutorial / Field Guide** with 1-tap demo graph generation
- **PDF import, markup, and OCR:** Import PDFs in-app (or via Android "Open with -> NoteGraph"), pinch to zoom, draw or highlight edge-to-edge on pages, toggle Night PDF, enable S-Pen-only mode for palm rejection, keep per-page margin notes, and run offline **PDF OCR**
- **Folders, checklists & sketch studio:** Custom folders with long-press drag-and-drop sorting, interactive checklists, multi-direction Lasso move/scale/delete, and an O(1)-bounded Rainbow Vanishing Laser
- **Links and graph:** Live `[[Wiki-link]]` autocomplete as you type, backlinks, `#tags`, and a 2D Knowledge Graph
- **Book reading mode:** Turn pages like a book with spine-hinged 3D curl, geometric corner-fold physics (`Corner Fold`, `3D Curl`, `Slide Stack`), or swipe up to toss a page forward
- **Voice, privacy, and exports:** Real-time waveform voice visualizer with timestamp jump chips, offline image OCR, PIN lock for sensitive notes, Trash recovery, and `.pptx` (PowerPoint & Keynote), `.pdf`, `.docx`, `.png`, `.md`, and `.zip` export

## Still missing

A few things are still not built yet:
- Hosted proprietary cloud accounts (sync runs serverless over your Shared Cloud Folder, Wi-Fi LAN, or portable `.zip` v2)
- Converting handwritten ink strokes into typed text or solving equations automatically
- Full live Markdown rendering, LaTeX math preview, and YAML frontmatter editing

## Build from source

**Desktop (`desktop/` - Node.js 20+)**
```bash
cd desktop
npm install
npm test
npm start
```

**Android (`app/` - JDK 17+, Gradle 8.7+, Android SDK 26+)**
```bash
gradle assembleDebug
```

## Contributing & License

If you hit a bug, UI glitch, or performance issue while testing, open an issue or pull request on [GitHub](https://github.com/MDYOUSUF20007-oss/notegraph/issues).

Licensed under the [MIT License](LICENSE.txt).
