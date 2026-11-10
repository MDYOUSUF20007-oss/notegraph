# NoteGraph

This is my app. I've been trying to build a solid note-taking app from scratch, and it's still pretty new. Test it out and give me honest feedback. I've been reading through Reddit posts to see what people actually want and what annoys them in other apps, and I'm trying to add as many of those fixes as I can. You all can contribute.

Everything works offline on your device. No login, no subscription, and no forced cloud.

## Why build another note app?

Most apps get one part right and miss the rest:
- PDF and stylus apps are great for writing, but they rarely include proper note linking or a graph view.
- Markdown graph apps are great for linking notes, but they feel clunky for live teaching, sketches, or scribbling on PDFs.
- Browser whiteboards get sluggish halfway through a long lecture or math derivation.

NoteGraph keeps an **Android app (`app/`)** and a **Windows / macOS board app (`desktop/`)** in one repo, with manual `.zip` vault export/import to move your notes and boards between them.

## Downloads

Prebuilt installers and APKs are on the [Releases page](https://github.com/MDYOUSUF20007-oss/notegraph/releases/latest):
- **Android:** `notegraph.apk`
- **Windows (x64):** `NoteGraph-Board-2.0.0-Windows-x64-Setup.exe` or `NoteGraph-Board-2.0.0-Windows-x64-Portable.exe`
- **macOS (Apple Silicon & Intel):** `NoteGraph-Board-2.0.0-macOS-arm64.dmg` / `NoteGraph-Board-2.0.0-macOS-x64.dmg` (plus `.zip` builds — unsigned CI builds, so right-click -> Open on first launch if macOS prompts you)

## What works right now

### Clean UI + Themes & Choosable Gradients
- **One app, multiple themes:** Switch anytime between **AMOLED** (true black), **Kindle** (warm e-ink paper), **Matte / Mac** (graphite slate), and **Board** (desktop blackboard).
- **Frosted two-tier obsidian cards (Android):** Note list cards, the graph node inspector, search/tag cards, and vault metadata panels now use two-tier frosted obsidian cards (`#1A1A1E -> #0C0C0E`) with a specular top accent edge and an untruncated dark footer bar for timestamps, local Room DB status, and attachment badges, synced live to whichever theme accent you pick (including Obsidian Violet `#8B5CF6` and Obsidian Gold).
- **Choosable gradients per theme:** Each theme lets you pick its own accent and background gradient (for example Obsidian Violet, Obsidian Gold, Aurora Cyan, Emerald Flux, Warm Sepia, Sage Folio, Studio Cyan, Nord Indigo), or turn gradients off for flat solid colors.
- **Quieter interface:** Cleaned up toolbar clutter, borderless note writing on Android, translucent toolbars on PC/Mac, and a quick theme switcher on Home and the top bar.

### Desktop & Mac - NoteGraph Board (`desktop/`)
Built for online classes, derivations, and freeform boards:
- Infinite canvas with `Space` + drag (or middle mouse) to pan, scroll zoom, sticky notes, interactive checklist cards, and card connectors
- Pen, highlighter, shape snapping (straight lines, circles, rectangles), lasso move/delete, and a fading rainbow laser pointer
- Switchable grids: Dots, Cornell ruled, XY graph axes, or Blank
- Import PDFs as separate lecture slides or side-by-side canvas pages, paste screenshots with `Ctrl+V`, and export to PNG, multi-page PDF, or Markdown
- `F11` fullscreen Zen mode, class stopwatch / 25-min timer, follow-up reminder dates, `Ctrl+K` quick command palette, and a 2D link graph

### Android App (`app/`)
- **PDF import, markup, and OCR:** Import PDFs in-app (or via Android "Open with -> NoteGraph"), pinch to zoom, draw or highlight right on the pages, toggle Night PDF, enable S-Pen-only mode for palm rejection, keep per-page margin notes, and run offline **PDF OCR** in a dedicated extraction window where you can pick individual blocks, trim text, choose a page range, or extract the whole PDF
- **Folders and checklists:** Custom folders (editable name, icon, and color) with long-press drag-and-drop sorting, plus checkable to-do lists inside notes and reader mode
- **Links and graph:** Live `[[Wiki-link]]` autocomplete as you type, backlinks, `#tags`, and a 2D knowledge graph with node dragging and search filtering
- **Book reading mode:** Turn pages like a book with geometric corner-fold physics (`Corner Fold`, `3D Curl`, `Slide Stack`) or swipe up to toss a page away forward
- **Voice, privacy, and exports:** Real-time theme-matched voice visualizer during live recording & playback scrubbing with timestamp jumps, offline image OCR, PIN lock for sensitive notes, Trash recovery, and rebuilt `.pptx` export compatible with PowerPoint & Keynote (plus `.pdf`, `.docx`, `.png`, `.md`, and `.zip`)

## Still missing

A few things are still not built yet:
- Automatic background cloud sync (moving vaults between Android and PC currently uses `.zip` export/import)
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
