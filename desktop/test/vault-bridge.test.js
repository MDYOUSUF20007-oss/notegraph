const test = require('node:test');
const assert = require('node:assert/strict');
const VaultBridge = require('../renderer/vault-bridge');
const { buildMultiPagePdfFromJpegs } = require('../pdf-writer');

test('createStarterVault creates 5 category folders and starter boards', () => {
  const vault = VaultBridge.createStarterVault();
  assert.equal(vault.folders.length, 5);
  assert.deepEqual(
    vault.folders.map((f) => f.name),
    ['Study', 'Work', 'Gaming', 'Shopping', 'Reminder']
  );
  assert.ok(vault.notes.length >= 2);
  assert.equal(vault.activeTheme, 'DEEP_PURPLE_AMOLED');
});

test('Android notes.json export and re-import preserves checklists and ink strokes', () => {
  const vault = VaultBridge.createStarterVault();
  const androidArray = VaultBridge.exportToAndroidNotesArray(vault);

  assert.ok(androidArray.length >= 2);
  const first = androidArray[0];
  assert.equal(typeof first.blocksJson, 'string');
  const blocks = JSON.parse(first.blocksJson);
  assert.ok(blocks.some((b) => b.type === 'checklist'));
  assert.ok(blocks.some((b) => b.type === 'ink'));

  const reimported = VaultBridge.importAndroidNotesArray(androidArray, {
    version: 2,
    activeTheme: 'KINDLE_PARCHMENT',
    activeNoteId: null,
    folders: vault.folders,
    notes: []
  });

  assert.equal(reimported.notes.length, androidArray.length);
  const restored = reimported.notes.find((n) => n.id === first.id);
  assert.ok(restored);
  assert.ok(restored.board.pages[0].cards.some((c) => c.kind === 'checklist'));
  assert.ok(restored.board.pages[0].strokes.length >= 1);
});

test('searchNotes searches across titles, body content, and checklist items', () => {
  const vault = VaultBridge.createStarterVault();
  const res1 = VaultBridge.searchNotes(vault, 'Rainbow Laser');
  assert.equal(res1.length, 1);

  const res2 = VaultBridge.searchNotes(vault, 'Physics');
  assert.ok(res2.length >= 1);

  const resEmpty = VaultBridge.searchNotes(vault, 'xyznonexistenttoken');
  assert.equal(resEmpty.length, 0);
});

test('buildGraphData resolves [[Wiki-Links]] between notes/boards', () => {
  const vault = VaultBridge.createStarterVault();
  const graph = VaultBridge.buildGraphData(vault);
  assert.ok(graph.nodes.length >= 2);
  assert.ok(graph.edges.length >= 1);
});

test('snapStrokeGeometry snaps straight lines and circles accurately', () => {
  const straightRaw = Array.from({ length: 12 }, (_, i) => ({
    x: 10 + i * 20,
    y: 50 + (i % 2 === 0 ? 1 : -1)
  }));
  const snappedLine = VaultBridge.snapStrokeGeometry(straightRaw);
  assert.equal(snappedLine.length, 2);

  const circleRaw = Array.from({ length: 36 }, (_, i) => {
    const a = (Math.PI * 2 * i) / 35;
    return { x: 200 + 80 * Math.cos(a), y: 200 + 80 * Math.sin(a) };
  });
  const snappedCircle = VaultBridge.snapStrokeGeometry(circleRaw);
  assert.equal(snappedCircle.length, 49);
});

test('noteToMarkdown exports checklist markdown and buildMultiPagePdfFromJpegs builds valid PDF 1.4', () => {
  const vault = VaultBridge.createStarterVault();
  const md = VaultBridge.noteToMarkdown(vault.notes[0]);
  assert.ok(md.includes('# Physics & Calculus Class Board'));
  assert.ok(md.includes('- [x]'));
  assert.ok(md.includes('- [ ]'));

  const fakeJpeg = Buffer.from([0xff, 0xd8, 0xff, 0xd9]).toString('base64');
  const pdf = buildMultiPagePdfFromJpegs([
    { widthPt: 842, heightPt: 595, imgWidth: 842, imgHeight: 595, jpegBase64: fakeJpeg },
    { widthPt: 842, heightPt: 595, imgWidth: 842, imgHeight: 595, jpegBase64: fakeJpeg }
  ]);
  const text = pdf.toString('latin1');
  assert.ok(text.startsWith('%PDF-1.4'));
  assert.ok(text.includes('/Count 2'));
  assert.ok(text.trimEnd().endsWith('%%EOF'));
});

test('importAndroidNotesArray converts Android PdfBlock annotations into summary card', () => {
  const androidPdfNote = {
    id: 'pdf_note_1',
    title: 'Thermodynamics Lecture',
    content: '#study #physics',
    blocksJson: JSON.stringify([
      {
        type: 'pdf',
        id: 'blk_pdf_1',
        filePath: '/data/user/0/com.notegraph.app/files/pdfs/sample.pdf',
        fileName: 'Thermo_Ch3.pdf',
        pageCount: 14,
        currentPage: 2,
        annotationsJson: JSON.stringify([
          { pageIndex: 2, strokes: [{ points: [{ x: 0.1, y: 0.2 }, { x: 0.4, y: 0.2 }] }], pageNote: 'Key entropy formula' }
        ])
      }
    ])
  };
  const imported = VaultBridge.importAndroidNotesArray([androidPdfNote], {
    version: 2,
    activeTheme: 'DEEP_PURPLE_AMOLED',
    activeNoteId: null,
    folders: [],
    notes: []
  });
  assert.equal(imported.notes.length, 1);
  const cards = imported.notes[0].board.pages[0].cards;
  const pdfCard = cards.find((c) => c.title.includes('Thermo_Ch3.pdf'));
  assert.ok(pdfCard);
  assert.ok(pdfCard.content.includes('14 pages'));
  assert.equal(pdfCard.content.includes('Page 3: Key entropy formula'), true);
});

test('createStarterVault includes per-theme choosable gradient defaults', () => {
  const vault = VaultBridge.createStarterVault();
  assert.equal(vault.gradientsEnabled, true);
  assert.equal(vault.themeGradients.DEEP_PURPLE_AMOLED, 'obsidian_violet');
  assert.equal(vault.themeGradients.KINDLE_PARCHMENT, 'warm_sepia');
  assert.equal(vault.themeGradients.MATTE_STUDIO, 'studio_cyan');
  assert.equal(vault.themeGradients.BLACKBOARD_GREEN, 'emerald_chalk');
});
