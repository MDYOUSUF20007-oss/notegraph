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
