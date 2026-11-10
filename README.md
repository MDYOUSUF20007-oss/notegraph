# NoteGraph

This is my app. I've been trying to build a solid note-taking app from scratch, and it's still pretty new. Test it out and give me honest feedback. I've been reading through Reddit posts to see what people actually want and what annoys them in other apps, and I'm trying to add as many of those fixes as I can. Anyone is welcome to contribute.

Everything stays local and offline on your device. No login, no subscription, no forced cloud.

## Why build another note app?

Most apps get one part right and leave out the rest:
- PDF and stylus apps are great for drawing, but they rarely give you proper note linking or a knowledge graph.
- Markdown graph apps are great for connecting ideas, but they aren't built for fast handwriting, PDF scribbling, or live teaching.
- Heavy online whiteboards start lagging right in the middle of a class or long derivation.

NoteGraph puts an **Android app (`app/`)** and a **Windows/macOS whiteboard (`desktop/`)** in the same project, with manual `.zip` vault export/import to move notes between them.

## Downloads

Prebuilt APKs and desktop installers are on the [Releases page](https://github.com/MDYOUSUF20007-oss/notegraph/releases/latest):
- **Android:** `notegraph.apk`
- **Windows (x64):** `NoteGraph-Board-*-Windows-x64-Setup.exe` or `*-Portable.exe`
- **macOS (Apple Silicon & Intel):** `.dmg` and `.zip` *(unsigned CI build, so right-click -> Open on first launch if macOS warns you)*

## What works right now

### Desktop - NoteGraph Board (`desktop/`)
Built for online classes, math/physics derivations, and freeform boards:
- Infinite canvas with `Space` + drag (or middle mouse) to pan, scroll zoom, sticky notes, checklist cards, and connector lines
- Pen, highlighter, shape snapping (lines, circles, boxes), lasso selection, and a fading rainbow laser pointer
- Switchable board backgrounds: Dots, Cornell ruled, XY graph axes, or Blackboard green
- Import PDFs onto slides to write directly on top, paste screenshots with `Ctrl+V`, and export as PNG or multi-page PDF
- `F11` fullscreen mode, a stopwatch / 25-min class timer, follow-up reminder dates, `Ctrl+K` quick search, and a 2D link graph

### Android App (`app/`)
- **PDF import and markup:** Open PDFs directly (or use Android "Open with -> NoteGraph"), pinch to zoom, write or highlight on each page, invert colors for Night PDF, turn on S-Pen-only mode for palm rejection, and keep notes per page
- **Folders and checklists:** Custom folders (editable name, icon, and color) with drag-and-drop sorting, plus interactive to-do checklists inside notes and reader mode
- **Links and graph:** Live `[[Wiki-link]]` autocomplete while typing, backlinks, nested `#tags`, and a 2D graph where you can drag nodes around and filter by title
- **Book reading mode:** Corner-fold page turn animation (swipe from any corner or edge, or switch to 3D curl / slide)
- **Themes, privacy, and exports:** 3 themes in one app (AMOLED black, Kindle paper, Matte slate), ink paper templates, voice recording with jump-to-timestamp chips, offline image OCR, PIN lock for private notes, trash recovery, and export to `.pdf`, `.docx`, `.pptx`, `.png`, `.md`, or `.zip`

## Still missing

To keep expectations realistic, a few things aren't finished yet:
- Automatic background cloud sync (moving data between Android and PC uses manual `.zip` export/import right now)
- Handwriting-stroke-to-text conversion or live math equation solving
- Full Markdown live preview, LaTeX/Mermaid rendering, and YAML frontmatter property editing

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

If something breaks, lags, or feels awkward to use, open an issue or pull request on [GitHub](https://github.com/MDYOUSUF20007-oss/notegraph/issues).

Licensed under the [MIT License](LICENSE.txt).
