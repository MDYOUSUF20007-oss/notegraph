const test = require('node:test');
const assert = require('node:assert/strict');
const VaultBridge = require('../renderer/vault-bridge');
const { BoardLassoUtils, BoardLaserUtils } = require('../renderer/board-engine');
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

test('BoardLassoUtils translates selected strokes freely in any direction without affecting others', () => {
  const strokes = [
    { id: 's1', points: [{ x: 100, y: 120 }, { x: 160, y: 180 }] },
    { id: 's2', points: [{ x: 400, y: 400 }, { x: 450, y: 460 }] }
  ];
  const selected = new Set(['s1']);
  const moved = BoardLassoUtils.translateSelectedStrokes(strokes, selected, -40, -60);

  assert.deepEqual(moved[0].points, [{ x: 60, y: 60 }, { x: 120, y: 120 }]);
  assert.deepEqual(moved[1].points, [{ x: 400, y: 400 }, { x: 450, y: 460 }]);
});

test('BoardLassoUtils computes combined selection bounding box across multiple strokes', () => {
  const strokes = [
    { id: 's1', points: [{ x: 40, y: 80 }, { x: 90, y: 30 }] },
    { id: 's2', points: [{ x: 120, y: 140 }, { x: 20, y: 200 }] },
    { id: 's3', points: [{ x: 999, y: 999 }, { x: 1050, y: 1050 }] }
  ];
  const bounds = BoardLassoUtils.computeSelectedStrokesBounds(strokes, new Set(['s1', 's2']));
  assert.deepEqual(bounds, { minX: 20, minY: 30, maxX: 120, maxY: 200 });
});

test('BoardLassoUtils detects 2-point straight lines crossed by lasso polygon even if endpoints sit outside', () => {
  const longLine = { id: 'line1', points: [{ x: 0, y: 100 }, { x: 300, y: 100 }] };
  const centerLasso = [
    { x: 120, y: 70 },
    { x: 180, y: 70 },
    { x: 180, y: 130 },
    { x: 120, y: 130 }
  ];
  assert.equal(BoardLassoUtils.strokeIntersectsPolygon(longLine, centerLasso), true);

  const farLasso = [
    { x: 120, y: 200 },
    { x: 180, y: 200 },
    { x: 180, y: 250 },
    { x: 120, y: 250 }
  ];
  assert.equal(BoardLassoUtils.strokeIntersectsPolygon(longLine, farLasso), false);
});

test('BoardLaserUtils enforces spatial decimation, rolling active TTL, and 140pt cap', () => {
  const pts = [{ x: 100, y: 100, t: 0 }];
  // Sub-threshold jitter (< 6px) must be rejected
  assert.equal(BoardLaserUtils.appendLaserPoint(pts, 102, 103, 16), false);
  assert.equal(pts.length, 1);

  // >= 6px accepted
  assert.equal(BoardLaserUtils.appendLaserPoint(pts, 120, 100, 300), true);
  assert.equal(pts.length, 2);

  // At t=1300 (ttlMs=1250), cutoff is 50 so t=0 expires WHILE still drawing
  assert.equal(BoardLaserUtils.appendLaserPoint(pts, 150, 100, 1300), true);
  assert.equal(pts.length, 2);
  assert.equal(pts[0].t, 300);

  // Hard cap at maxPoints (e.g. 140) prevents unbounded RAM growth
  const longPts = [{ x: 0, y: 0, t: 10_000 }];
  for (let i = 1; i <= 200; i++) {
    BoardLaserUtils.appendLaserPoint(longPts, i * 10, 0, 10_000 + i);
  }
  assert.equal(longPts.length, BoardLaserUtils.LASER_MAX_ACTIVE_POINTS);
});

test('BoardLaserUtils prunes expired trails, enforces 6-trail cap, preallocates LUT, and clones strokes safely', () => {
  assert.equal(BoardLaserUtils.LASER_COLOR_LUT.length, 24);
  assert.equal(BoardLaserUtils.LASER_COLOR_LUT[0].length, 5);
  assert.match(BoardLaserUtils.LASER_COLOR_LUT[0][0], /^hsla\(/);

  const trails = Array.from({ length: 8 }, (_, idx) => ({
    points: [
      { x: idx * 20, y: 0, t: 1000 + idx * 50 },
      { x: idx * 20 + 20, y: 20, t: 1010 + idx * 50 }
    ]
  }));

  const aliveAt1500 = BoardLaserUtils.pruneCompletedLaserTrails(trails, 1500);
  assert.equal(aliveAt1500.length, BoardLaserUtils.LASER_MAX_COMPLETED_TRAILS);

  const aliveAt3000 = BoardLaserUtils.pruneCompletedLaserTrails(aliveAt1500, 3000);
  assert.equal(aliveAt3000.length, 0);

  const original = [{ id: 's1', colorHex: '#FFF', points: [{ x: 1, y: 2 }] }];
  const cloned = BoardLaserUtils.cloneStrokesShallow(original);
  cloned[0].points[0].x = 999;
  assert.equal(original[0].points[0].x, 1);
});

test('VaultBridge buildSyncPackage & mergeSyncPackage sync notes, folders, tombstones, and graph edges symmetrically', () => {
  const localVault = VaultBridge.createStarterVault();
  const baseTs = 5_000;
  localVault.notes.forEach((n) => {
    n.createdAt = 1000;
    n.updatedAt = baseTs;
  });

  const pkg = VaultBridge.buildSyncPackage(localVault, {
    deviceId: 'dev_win',
    deviceName: 'Windows Workstation',
    platform: 'win32'
  });

  // Exact schema parity with Android SyncPackageV2
  assert.equal(pkg.schemaVersion, 2);
  assert.equal(pkg.platform, 'win32');
  assert.ok(Array.isArray(pkg.folders));
  assert.ok(Array.isArray(pkg.notes));
  assert.ok(Array.isArray(pkg.links));
  assert.ok(Array.isArray(pkg.tombstones));
  assert.equal(typeof pkg.notes[0].blocksJson, 'string');

  // Remote Android payload: edits 1 note, adds 1 new note, deletes starter folder & second note via tombstone
  const targetDeleteNoteId = localVault.notes[1].id;
  const targetDeleteFolderId = 'folder_gaming';
  const androidRemote = {
    schemaVersion: 2,
    exportedAt: 9_000,
    deviceId: 'dev_pixel',
    deviceName: 'Pixel 9',
    platform: 'android',
    folders: [
      ...localVault.folders,
      { id: 'folder_android', name: 'Android Lab', parentId: null, icon: '🤖', colorHex: '#10B981', createdAt: 7000, updatedAt: 7000 }
    ],
    notes: [
      {
        id: localVault.notes[0].id,
        title: localVault.notes[0].title,
        content: 'Updated from Android linking [[Mobile Field Note]]',
        folderId: targetDeleteFolderId, // note inside deleted folder must be unfiled to null
        isPinned: true,
        isFavorite: false,
        isLocked: false,
        isTrash: false,
        createdAt: 1000,
        updatedAt: 8500,
        templateType: 'BLANK',
        blocksJson: JSON.stringify([{ type: 'text', id: 'blk_a', text: 'Fresh mobile edit' }])
      },
      {
        id: 'android_new_1',
        title: 'Mobile Field Note',
        content: 'Captured offline on Android',
        folderId: 'folder_android',
        isPinned: false,
        isFavorite: true,
        isLocked: false,
        isTrash: false,
        createdAt: 8000,
        updatedAt: 8600,
        templateType: 'BLANK',
        blocksJson: JSON.stringify([
          {
            type: 'checklist',
            id: 'chk_mob',
            title: 'Field Tasks',
            items: [{ id: 'it1', text: 'Sync back to Mac', isChecked: true }]
          }
        ])
      }
    ],
    links: [],
    tombstones: [
      { id: targetDeleteNoteId, kind: 'note', deletedAt: 7500 },
      { id: targetDeleteFolderId, kind: 'folder', deletedAt: 7500 }
    ]
  };

  const merged = VaultBridge.mergeSyncPackage(localVault, androidRemote, 10_000);
  assert.equal(merged.summary.newNotesAdded, 1);
  assert.equal(merged.summary.deletionsApplied, 2);
  assert.ok(merged.summary.linksRebuilt >= 1);

  assert.equal(merged.vault.folders.some((f) => f.id === targetDeleteFolderId), false);
  assert.equal(merged.vault.folders.some((f) => f.id === 'folder_android'), true);
  assert.equal(merged.vault.notes.some((n) => n.id === targetDeleteNoteId), false);

  const updatedFirst = merged.vault.notes.find((n) => n.id === localVault.notes[0].id);
  assert.equal(updatedFirst.folderId, null);
  assert.ok(updatedFirst.content.includes('Updated from Android'));

  const mobileNote = merged.vault.notes.find((n) => n.id === 'android_new_1');
  assert.ok(mobileNote);
  assert.ok(mobileNote.board.pages[0].cards.some((c) => c.kind === 'checklist'));
});

test('VaultBridge recordTombstone & revival work cleanly and parseSyncPackage handles legacy arrays', () => {
  const vault = VaultBridge.createStarterVault();
  const targetId = vault.notes[0].id;
  vault.notes[0].createdAt = 1000;
  vault.notes[0].updatedAt = 2000;
  VaultBridge.recordTombstone(vault, targetId, 'note', 3000);

  // Older note is suppressed by tombstone at t=3000
  const afterDelete = VaultBridge.mergeSyncPackage(vault, { schemaVersion: 2, notes: [], folders: [], tombstones: [] }, 4000);
  assert.equal(afterDelete.vault.notes.some((n) => n.id === targetId), false);

  // Note recreated at t=5000 (> tombstone deletedAt=3000) revives and clears tombstone
  const revived = VaultBridge.mergeSyncPackage(
    afterDelete.vault,
    {
      schemaVersion: 2,
      notes: [{ id: targetId, title: 'Reborn Node', content: 'New life', createdAt: 5000, updatedAt: 5000, blocksJson: '[]' }],
      folders: [],
      tombstones: []
    },
    6000
  );
  assert.equal(revived.vault.notes.some((n) => n.id === targetId && n.title === 'Reborn Node'), true);
  assert.equal(revived.vault.tombstones.some((t) => t.id === targetId), false);

  // Legacy array parsing
  const legacyParsed = VaultBridge.parseSyncPackage(JSON.stringify([{ id: 'leg1', title: 'Old JSON Array' }]));
  assert.equal(legacyParsed.schemaVersion, 1);
  assert.equal(legacyParsed.notes.length, 1);
});
