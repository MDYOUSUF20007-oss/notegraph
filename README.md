NoteGraph

> **One-line pitch:** A premium, all-in-one Android note-taking application synthesizing the intuitive handwriting of Samsung Notes with the knowledge-management power of Obsidian.

![Status](https://img.shields.io/badge/status-active%20development-purple)
![Platform](https://img.shields.io/badge/platform-Android-green)
![Phase](https://img.shields.io/badge/current%20phase-3%20%2B%20luxury%20extensions-blue)

NoteGraph is a high-end, distraction-free productivity app designed for students, developers, and thinkers who demand absolute visual comfort and deep connected note-taking. It bridges the gap between freeform digital ink and structured graph databases.

## Features

- **Three Signature Visual Themes:** Instantly toggle between **Deep Purple AMOLED** (true `#000000` pitch black), **Kindle Parchment** (warm glare-free paper with Cornell ruling), and **Matte Studio** (anodized graphite architectural style).
- **Samsung Notes Canvas Engine:** Smooth stylus/touch drawing featuring a **Rainbow Vanishing Pen** (an RGB pointer stroke that automatically fades away after 1.5s for live presentation), **Neon Lasso Selection**, and **Geometric Shape Snapping**.
- **Obsidian Knowledge Management:** Live `[[` wiki-link popups, interactive backlinks panels, and a 2D force-directed knowledge graph to visualize note connections.
- **Google Play Books 3D Page-Curl Mode:** Smooth 60 FPS page-turn physics with paper shadows and dog-ear corner bookmarks for immersive review.
- **Lecture Audio & PDF Markup:** Synchronized audio waveform scrubbing linked directly to handwritten strokes, plus local ML Kit OCR text extraction.
- **Multi-Format Export Suite:** Lossless exports to `.pdf`, `.docx`, `.pptx`, `.png`, and `.md` (Obsidian Vault zip archives).

## Screenshots

*Coming soon.*

## Roadmap & Progress

| Phase | Goal | Status |
|-------|------|--------|
| 1 | Core notes structure & wiki-links | ✅ Done |
| 2 | Ink canvas, audio recording, & OCR | ✅ Done |
| 3 | Advanced canvas tools & export engine | ✅ Done |
| 4 | Luxury UI overhaul & book-mode navigation | 🚧 In progress |

### Phase 3 & 4 checklist

- [x] Deep Purple AMOLED, Kindle Parchment, & Matte Studio theme system
- [x] Samsung Notes RGB vanishing presentation pen & lasso tool
- [x] Google Play Books 3D 60 FPS page-curl reader mode
- [x] White & Deep Purple monogram app icon with rainbow gradient
- [ ] Automated GitHub Actions CI/CD release pipeline verification

## Tech Stack

- **UI & Architecture:** Jetpack Compose, Material 3, Kotlin DSL
- **Persistence & Storage:** Room Database, local file management
- **Graphics & Canvas:** Compose Graphics layers, custom 3D page-curl math
- **CI/CD:** GitHub Actions (automated cloud compilation & verification)

## Getting Started

### Requirements

- Android Studio (latest stable)
- JDK 17+
- Android device or emulator (API 26+)

### Build & Run

```bash
git clone https://github.com/MDYOUSUF20007-oss/notegraph.git
cd notegraph
# Note: Compilation is verified via GitHub Actions cloud runners
./gradlew assembleDebug
```

Or open the project directly in Android Studio and press **Run**.

## Contributing

The project is under active development. Suggestions and discussions are welcome—feel free to open an issue.

## License

This project is proprietary and built for personal productivity.

## Author

Built by [MDYOUSUF](https://github.com/MDYOUSUF20007-oss).
