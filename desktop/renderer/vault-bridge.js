(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.VaultBridge = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const SYNC_SCHEMA_VERSION = 2;
  const TOMBSTONE_TTL_MS = 90 * 24 * 60 * 60 * 1000;
  const MAX_TOMBSTONES = 500;

  function makeId(prefix = 'id') {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 9)}`;
  }

  const STARTER_FOLDERS = [
    { id: 'folder_study', name: 'Study', icon: '📚', colorHex: '#6366F1' },
    { id: 'folder_work', name: 'Work', icon: '💼', colorHex: '#38BDF8' },
    { id: 'folder_gaming', name: 'Gaming', icon: '🎮', colorHex: '#A855F7' },
    { id: 'folder_shopping', name: 'Shopping', icon: '🛒', colorHex: '#10B981' },
    { id: 'folder_reminder', name: 'Reminder', icon: '⏰', colorHex: '#F59E0B' }
  ];

  function createEmptyBoardPage(name = 'Slide 1') {
    return {
      id: makeId('page'),
      name,
      strokes: [],
      cards: [],
      connectors: [],
      viewport: { x: 0, y: 0, zoom: 1 }
    };
  }

  function createNoteRecord(overrides = {}) {
    const now = Date.now();
    const id = overrides.id || makeId('note');
    const firstPage = createEmptyBoardPage('Slide 1');
    return {
      id,
      title: overrides.title !== undefined ? overrides.title : 'Untitled Board',
      content: overrides.content !== undefined ? overrides.content : '',
      folderId: overrides.folderId !== undefined ? overrides.folderId : null,
      isPinned: Boolean(overrides.isPinned),
      isFavorite: Boolean(overrides.isFavorite),
      isLocked: Boolean(overrides.isLocked),
      isTrash: Boolean(overrides.isTrash),
      trashTimestamp: overrides.trashTimestamp || null,
      followUpAt: overrides.followUpAt || null,
      createdAt: overrides.createdAt || now,
      updatedAt: overrides.updatedAt || now,
      templateType: overrides.templateType || 'BOARD',
      board: overrides.board || {
        gridStyle: 'DOTS',
        activePageIndex: 0,
        pages: [firstPage]
      }
    };
  }

  function createStarterVault() {
    const now = Date.now();
    const welcomeBoardId = 'board_welcome_class';
    const crmNoteId = 'note_daily_starter';

    const welcomeCard1 = {
      id: makeId('card'),
      kind: 'note',
      x: 100,
      y: 90,
      w: 360,
      h: 220,
      title: 'Welcome to NoteGraph Board',
      content:
        'Built for zero-lag classes, derivations & Freeform spatial notes.\n\n' +
        '• Space + Drag or Middle Mouse: Pan infinite canvas\n' +
        '• Ctrl / Cmd + Wheel: Smooth cursor zoom\n' +
        '• Ctrl + V: Paste screenshot / image directly onto board\n' +
        '• F11: Fullscreen Classroom Smartboard Mode\n' +
        '• Import PDF button: Drop lecture slides & draw directly on them!',
      colorHex: '#A855F7',
      locked: false
    };

    const welcomeChecklistCard = {
      id: makeId('card'),
      kind: 'checklist',
      x: 520,
      y: 90,
      w: 320,
      h: 220,
      title: 'Lecture / Study Checklist',
      items: [
        { id: makeId('chk'), text: 'Import lecture PDF or paste question (Ctrl+V)', isChecked: true },
        { id: makeId('chk'), text: 'Try Rainbow Laser Pointer for live explaining', isChecked: false },
        { id: makeId('chk'), text: 'Toggle Grid: Dots / Cornell / XY Graph Axis', isChecked: false },
        { id: makeId('chk'), text: 'Sync .zip Vault with NoteGraph Android', isChecked: false }
      ],
      colorHex: '#38BDF8',
      locked: false
    };

    const welcomeBoard = createNoteRecord({
      id: welcomeBoardId,
      title: 'Physics & Calculus Class Board',
      content: 'Use [[Daily Journal]] or #study/physics to connect boards into your knowledge graph.',
      folderId: 'folder_study',
      isPinned: true,
      createdAt: now,
      updatedAt: now,
      board: {
        gridStyle: 'GRAPH',
        activePageIndex: 0,
        pages: [
          {
            id: makeId('page'),
            name: 'Lecture Slide 1',
            strokes: [
              // Clean hand-drawn physics parabola curve demo
              {
                id: makeId('stk'),
                points: Array.from({ length: 28 }, (_, i) => {
                  const t = (i - 14) / 14;
                  return { x: 290 + i * 16, y: 500 - (1 - t * t) * 110 };
                }),
                colorHex: '#38BDF8',
                strokeWidth: 4,
                isHighlighter: false,
                isArrow: false
              },
              {
                id: makeId('stk'),
                points: [
                  { x: 250, y: 510 },
                  { x: 780, y: 510 }
                ],
                colorHex: '#A855F7',
                strokeWidth: 3,
                isHighlighter: false,
                isArrow: true
              },
              {
                id: makeId('stk'),
                points: [
                  { x: 280, y: 530 },
                  { x: 280, y: 350 }
                ],
                colorHex: '#A855F7',
                strokeWidth: 3,
                isHighlighter: false,
                isArrow: true
              }
            ],
            cards: [welcomeCard1, welcomeChecklistCard],
            connectors: [
              {
                id: makeId('conn'),
                fromCardId: welcomeCard1.id,
                toCardId: welcomeChecklistCard.id,
                label: 'Next Step',
                colorHex: '#A855F7'
              }
            ],
            viewport: { x: 0, y: 0, zoom: 1 }
          },
          createEmptyBoardPage('Practice Derivations')
        ]
      }
    });

    const todayStr = new Date().toISOString().slice(0, 10);
    const dailyNote = createNoteRecord({
      id: crmNoteId,
      title: todayStr,
      content: '#daily #study\n\nLinked board: [[Physics & Calculus Class Board]]\n\nQuick notes for today go here.',
      folderId: 'folder_reminder',
      followUpAt: todayStr,
      createdAt: now - 1000,
      updatedAt: now - 1000,
      board: {
        gridStyle: 'DOTS',
        activePageIndex: 0,
        pages: [
          {
            id: makeId('page'),
            name: 'Daily Board',
            strokes: [],
            cards: [
              {
                id: makeId('card'),
                kind: 'checklist',
                x: 100,
                y: 90,
                w: 340,
                h: 210,
                title: "Today's Tasks",
                items: [
                  { id: makeId('chk'), text: 'Review class board derivations', isChecked: false },
                  { id: makeId('chk'), text: 'Check follow-up reminders', isChecked: false }
                ],
                colorHex: '#F59E0B',
                locked: false
              }
            ],
            connectors: [],
            viewport: { x: 0, y: 0, zoom: 1 }
          }
        ]
      }
    });

    return {
      version: 2,
      activeTheme: 'DEEP_PURPLE_AMOLED',
      gradientsEnabled: true,
      themeGradients: {
        DEEP_PURPLE_AMOLED: 'obsidian_violet',
        KINDLE_PARCHMENT: 'warm_sepia',
        MATTE_STUDIO: 'studio_cyan',
        BLACKBOARD_GREEN: 'emerald_chalk'
      },
      activeNoteId: welcomeBoardId,
      folders: STARTER_FOLDERS.map((f, idx) => ({ ...f, createdAt: 1 + idx, updatedAt: 1 + idx })),
      notes: [welcomeBoard, dailyNote],
      tombstones: []
    };
  }

  function decodeBlocksSafe(blocksJson) {
    if (Array.isArray(blocksJson)) return blocksJson;
    if (typeof blocksJson !== 'string' || !blocksJson.trim() || blocksJson.trim() === '[]') {
      return [];
    }
    try {
      const parsed = JSON.parse(blocksJson);
      return Array.isArray(parsed) ? parsed : [];
    } catch (e) {
      return [];
    }
  }

  function blocksJsonToBoard(raw, fallbackNote = null) {
    const parsedBlocks = decodeBlocksSafe(raw && raw.blocksJson);
    const cards = [];
    const strokes = [];
    const extraBlocks = [];
    let nextY = 90;

    if (raw && raw.content && String(raw.content).trim().length > 0) {
      cards.push({
        id: `main_${raw.id}`,
        kind: 'note',
        x: 90,
        y: nextY,
        w: 380,
        h: 210,
        title: raw.title || 'Note Content',
        content: String(raw.content),
        colorHex: '#A855F7',
        locked: false
      });
      nextY += 240;
    }

    for (const blk of parsedBlocks) {
      if (!blk || !blk.type) continue;
      if (blk.type === 'text' && blk.text) {
        cards.push({
          id: blk.id || makeId('card'),
          kind: 'note',
          x: 90,
          y: nextY,
          w: 380,
          h: 170,
          title: 'Text Block',
          content: String(blk.text),
          colorHex: '#6366F1',
          locked: false
        });
        nextY += 195;
      } else if (blk.type === 'checklist') {
        cards.push({
          id: blk.id || makeId('card'),
          kind: 'checklist',
          x: 510,
          y: 90 + (cards.filter((c) => c.kind === 'checklist').length * 240),
          w: 330,
          h: 220,
          title: blk.title || 'To-Do Checklist',
          items: Array.isArray(blk.items)
            ? blk.items.map((it) => ({
                id: it.id || makeId('chk'),
                text: String(it.text || ''),
                isChecked: Boolean(it.isChecked)
              }))
            : [],
          colorHex: '#10B981',
          locked: false
        });
      } else if (blk.type === 'ink') {
        try {
          const decodedStrokes = typeof blk.strokesJson === 'string'
            ? JSON.parse(blk.strokesJson)
            : Array.isArray(blk.strokesJson) ? blk.strokesJson : [];
          for (const s of decodedStrokes) {
            if (s && Array.isArray(s.points) && s.points.length >= 2) {
              strokes.push({
                id: makeId('stk'),
                points: s.points.map((pt) => ({ x: Number(pt.x) + 120, y: Number(pt.y) + 340 })),
                colorHex: s.colorHex || '#FFFFFF',
                strokeWidth: Number(s.strokeWidth) || 4,
                isHighlighter: Boolean(s.isHighlighter),
                isArrow: false
              });
            }
          }
        } catch (e) {
          // Skip malformed stroke JSON safely
        }
      } else if (blk.type === 'pdf') {
        extraBlocks.push(blk);
        let anns = [];
        try {
          anns = typeof blk.annotationsJson === 'string'
            ? JSON.parse(blk.annotationsJson)
            : Array.isArray(blk.annotationsJson) ? blk.annotationsJson : [];
        } catch (e) {
          anns = [];
        }
        const annotatedPages = anns.filter((a) => (a && ((a.strokes && a.strokes.length) || (a.pageNote && a.pageNote.trim()))));
        const notesLines = annotatedPages
          .filter((a) => a.pageNote && a.pageNote.trim())
          .map((a) => `Page ${Number(a.pageIndex || 0) + 1}: ${a.pageNote.trim()}`);
        const summaryParts = [
          `File: ${blk.fileName || 'Document.pdf'} (${Number(blk.pageCount) || 0} pages)`,
          `Annotated Pages: ${annotatedPages.length}`,
          notesLines.length ? '\nMargin Notes:\n' + notesLines.join('\n') : '\nUse Desktop "Import PDF Slides" to render & ink slides on this board.'
        ];
        cards.push({
          id: blk.id || makeId('card'),
          kind: 'note',
          x: 90,
          y: nextY,
          w: 400,
          h: 200,
          title: `📄 ${blk.fileName || 'PDF Document'}`,
          content: summaryParts.join('\n'),
          colorHex: '#38BDF8',
          locked: false
        });
        nextY += 225;
      } else if (blk.type === 'image' || blk.type === 'audio') {
        extraBlocks.push(blk);
        const ocr = blk.extractedOcrText ? `\nOCR Text:\n${blk.extractedOcrText}` : '';
        cards.push({
          id: blk.id || makeId('card'),
          kind: 'note',
          x: 90,
          y: nextY,
          w: 380,
          h: 170,
          title: blk.type === 'audio' ? `🎙 Audio (${Math.round((Number(blk.durationMs) || 0) / 1000)}s)` : '🖼 Embedded Image',
          content: (blk.uriString || blk.filePath || 'Synced attachment') + ocr,
          colorHex: '#A855F7',
          locked: false
        });
        nextY += 195;
      }
    }

    const prevBoard = fallbackNote && fallbackNote.board;
    const gridStyle = (prevBoard && prevBoard.gridStyle) || 'DOTS';

    return {
      extraBlocks,
      board: {
        gridStyle,
        activePageIndex: 0,
        pages: [
          {
            id: (prevBoard && prevBoard.pages && prevBoard.pages[0] && prevBoard.pages[0].id) || makeId('page'),
            name: 'Slide 1',
            strokes,
            cards,
            connectors: [],
            viewport: (prevBoard && prevBoard.pages && prevBoard.pages[0] && prevBoard.pages[0].viewport) || { x: 0, y: 0, zoom: 1 }
          }
        ]
      }
    };
  }

  /**
   * Parses Android NoteEntity list (from notes.json inside NoteGraph_Vault_Backup.zip)
   * into desktop board+note records while merging safely with existing vault.
   */
  function importAndroidNotesArray(androidNotes, existingVault) {
    if (!Array.isArray(androidNotes)) return existingVault;
    const base = existingVault || createStarterVault();
    const existingMap = new Map((base.notes || []).map((n) => [n.id, n]));

    for (const raw of androidNotes) {
      if (!raw || !raw.id) continue;
      const prev = existingMap.get(raw.id);
      const { board: synthesizedBoard, extraBlocks } = blocksJsonToBoard(raw, prev);
      const mergedBoard = prev && prev.board && (!raw.updatedAt || prev.updatedAt > raw.updatedAt)
        ? prev.board
        : synthesizedBoard;

      existingMap.set(raw.id, {
        id: raw.id,
        title: raw.title || 'Untitled Note',
        content: raw.content || '',
        folderId: raw.folderId || null,
        isPinned: Boolean(raw.isPinned),
        isFavorite: Boolean(raw.isFavorite),
        isLocked: Boolean(raw.isLocked),
        isTrash: Boolean(raw.isTrash),
        trashTimestamp: raw.trashTimestamp || null,
        followUpAt: prev ? prev.followUpAt || null : null,
        createdAt: Number(raw.createdAt) || Date.now(),
        updatedAt: Number(raw.updatedAt) || Date.now(),
        templateType: raw.templateType || 'BLANK',
        extraBlocks: extraBlocks.length ? extraBlocks : (prev ? prev.extraBlocks : undefined),
        board: mergedBoard
      });
    }

    return {
      ...base,
      notes: Array.from(existingMap.values()).sort((a, b) => {
        if (Boolean(a.isPinned) !== Boolean(b.isPinned)) return a.isPinned ? -1 : 1;
        return (b.updatedAt || 0) - (a.updatedAt || 0);
      })
    };
  }

  function noteBoardToBlocksJson(note) {
    const blocks = [];
    const pages = (note.board && Array.isArray(note.board.pages)) ? note.board.pages : [];
    const allCards = pages.flatMap((p) => Array.isArray(p.cards) ? p.cards : []);
    const allStrokes = pages.flatMap((p) => Array.isArray(p.strokes) ? p.strokes : []);
    const extraIds = new Set(Array.isArray(note.extraBlocks) ? note.extraBlocks.map((b) => b.id).filter(Boolean) : []);

    for (const card of allCards) {
      if (extraIds.has(card.id)) continue;
      if (card.kind === 'checklist') {
        blocks.push({
          type: 'checklist',
          id: card.id || makeId('blk'),
          title: card.title || 'To-Do Checklist',
          items: Array.isArray(card.items)
            ? card.items.map((it) => ({
                id: it.id || makeId('chk'),
                text: String(it.text || ''),
                isChecked: Boolean(it.isChecked)
              }))
            : []
        });
      } else if (card.kind === 'note' && card.content && card.id !== `main_${note.id}`) {
        const labelPrefix = card.title && card.title !== 'Text Block' ? `${card.title}\n` : '';
        blocks.push({
          type: 'text',
          id: card.id || makeId('blk'),
          text: `${labelPrefix}${card.content}`.trim(),
          audioTimestampMs: null
        });
      }
    }

    if (allStrokes.length > 0) {
      const androidStrokes = allStrokes
        .filter((s) => Array.isArray(s.points) && s.points.length >= 2)
        .map((s) => ({
          points: s.points.map((pt) => ({ x: Number(pt.x), y: Number(pt.y) })),
          colorHex: s.colorHex || '#FFFFFF',
          strokeWidth: Number(s.strokeWidth) || 4,
          isHighlighter: Boolean(s.isHighlighter),
          isEraser: false
        }));
      if (androidStrokes.length > 0) {
        blocks.push({
          type: 'ink',
          id: makeId('ink'),
          strokesJson: JSON.stringify(androidStrokes),
          heightDp: 320.0
        });
      }
    }

    if (Array.isArray(note.extraBlocks) && note.extraBlocks.length > 0) {
      for (const eb of note.extraBlocks) {
        blocks.push(eb);
      }
    }

    return JSON.stringify(blocks);
  }

  /**
   * Exports desktop vault notes to exact NoteGraph Android NoteEntity[] JSON schema.
   */
  function exportToAndroidNotesArray(vault) {
    if (!vault || !Array.isArray(vault.notes)) return [];
    return vault.notes
      .filter((n) => !n.isTrash)
      .map((note) => ({
        id: note.id,
        title: note.title || 'Untitled Note',
        content: note.content || '',
        folderId: note.folderId || null,
        isPinned: Boolean(note.isPinned),
        isFavorite: Boolean(note.isFavorite),
        isLocked: Boolean(note.isLocked),
        isTrash: false,
        trashTimestamp: null,
        createdAt: Number(note.createdAt) || Date.now(),
        updatedAt: Number(note.updatedAt) || Date.now(),
        templateType: note.templateType || 'BLANK',
        blocksJson: noteBoardToBlocksJson(note)
      }));
  }

  function recordTombstone(vault, id, kind = 'note', deletedAt = Date.now()) {
    if (!vault || !id) return vault;
    if (!Array.isArray(vault.tombstones)) vault.tombstones = [];
    const normKind = kind === 'folder' ? 'folder' : 'note';
    vault.tombstones = vault.tombstones.filter((t) => !(t && t.id === id && t.kind === normKind));
    vault.tombstones.push({ id, kind: normKind, deletedAt: Number(deletedAt) || Date.now() });
    if (vault.tombstones.length > MAX_TOMBSTONES) {
      vault.tombstones = vault.tombstones.slice(-MAX_TOMBSTONES);
    }
    return vault;
  }

  function buildSyncPackage(vault, meta = {}) {
    const safeVault = vault || createStarterVault();
    const folders = (safeVault.folders || []).map((f) => ({
      id: f.id,
      name: f.name || 'Folder',
      parentId: f.parentId || null,
      icon: f.icon || '📁',
      colorHex: f.colorHex || '#A855F7',
      createdAt: Number(f.createdAt) || 0,
      updatedAt: Number(f.updatedAt) || Number(f.createdAt) || 0
    }));

    const notes = (safeVault.notes || []).map((n) => ({
      id: n.id,
      title: n.title || 'Untitled Note',
      content: n.content || '',
      folderId: n.folderId || null,
      isPinned: Boolean(n.isPinned),
      isFavorite: Boolean(n.isFavorite),
      isLocked: Boolean(n.isLocked),
      isTrash: Boolean(n.isTrash),
      trashTimestamp: n.trashTimestamp || null,
      followUpAt: n.followUpAt || null,
      createdAt: Number(n.createdAt) || 0,
      updatedAt: Number(n.updatedAt) || 0,
      templateType: n.templateType || 'BOARD',
      blocksJson: noteBoardToBlocksJson(n),
      board: n.board || null
    }));

    const graph = buildGraphData(safeVault);
    const titleById = new Map((safeVault.notes || []).map((n) => [n.id, n.title || 'Untitled']));
    const links = graph.edges.map((e) => ({
      sourceId: e.sourceId,
      targetId: e.targetId,
      targetTitle: titleById.get(e.targetId) || '',
      isResolved: true
    }));

    const tombstones = Array.isArray(safeVault.tombstones)
      ? safeVault.tombstones.map((t) => ({
          id: t.id,
          kind: t.kind === 'folder' ? 'folder' : 'note',
          deletedAt: Number(t.deletedAt) || 0
        }))
      : [];

    return {
      schemaVersion: SYNC_SCHEMA_VERSION,
      exportedAt: Date.now(),
      deviceId: meta.deviceId || '',
      deviceName: meta.deviceName || 'Desktop',
      platform: meta.platform || 'desktop',
      folders,
      notes,
      links,
      tombstones
    };
  }

  function parseSyncPackage(rawOrObj) {
    if (!rawOrObj) return { schemaVersion: SYNC_SCHEMA_VERSION, folders: [], notes: [], links: [], tombstones: [] };
    const parsed = typeof rawOrObj === 'string' ? JSON.parse(rawOrObj) : rawOrObj;
    if (Array.isArray(parsed)) {
      return {
        schemaVersion: 1,
        platform: 'legacy',
        deviceName: 'Legacy Android ZIP',
        folders: [],
        notes: parsed,
        links: [],
        tombstones: []
      };
    }
    return {
      schemaVersion: Number(parsed.schemaVersion) || SYNC_SCHEMA_VERSION,
      exportedAt: Number(parsed.exportedAt) || Date.now(),
      deviceId: parsed.deviceId || '',
      deviceName: parsed.deviceName || parsed.platform || 'Remote Device',
      platform: parsed.platform || 'unknown',
      folders: Array.isArray(parsed.folders) ? parsed.folders : [],
      notes: Array.isArray(parsed.notes) ? parsed.notes : [],
      links: Array.isArray(parsed.links) ? parsed.links : [],
      tombstones: Array.isArray(parsed.tombstones) ? parsed.tombstones : []
    };
  }

  function pickFolderWinner(localF, remoteF) {
    if (!localF) return remoteF;
    if (!remoteF) return localF;
    const lTs = Math.max(Number(localF.updatedAt) || 0, Number(localF.createdAt) || 0);
    const rTs = Math.max(Number(remoteF.updatedAt) || 0, Number(remoteF.createdAt) || 0);
    if (rTs > lTs) return remoteF;
    if (lTs > rTs) return localF;
    const lSig = `${localF.name || ''}|${localF.icon || ''}|${localF.colorHex || ''}|${localF.parentId || ''}`;
    const rSig = `${remoteF.name || ''}|${remoteF.icon || ''}|${remoteF.colorHex || ''}|${remoteF.parentId || ''}`;
    return rSig > lSig ? remoteF : localF;
  }

  function pickNoteWinner(localN, remoteN) {
    if (!localN) return { winner: remoteN, remoteWon: true };
    if (!remoteN) return { winner: localN, remoteWon: false };
    const lTs = Math.max(Number(localN.updatedAt) || 0, Number(localN.createdAt) || 0);
    const rTs = Math.max(Number(remoteN.updatedAt) || 0, Number(remoteN.createdAt) || 0);
    if (rTs > lTs) return { winner: remoteN, remoteWon: true };
    if (lTs > rTs) return { winner: localN, remoteWon: false };
    const lBlocks = typeof localN.blocksJson === 'string' ? localN.blocksJson : noteBoardToBlocksJson(localN);
    const rBlocks = typeof remoteN.blocksJson === 'string' ? remoteN.blocksJson : '';
    const lSig = `${localN.title || ''}|${localN.content || ''}|${lBlocks}|${Boolean(localN.isTrash)}|${Boolean(localN.isPinned)}`;
    const rSig = `${remoteN.title || ''}|${remoteN.content || ''}|${rBlocks}|${Boolean(remoteN.isTrash)}|${Boolean(remoteN.isPinned)}`;
    return rSig > lSig ? { winner: remoteN, remoteWon: true } : { winner: localN, remoteWon: false };
  }

  function mergeSyncPackage(localVault, incomingRaw, nowMs = Date.now()) {
    const base = localVault || createStarterVault();
    const remote = parseSyncPackage(incomingRaw);

    // 1. Merge tombstones (max deletedAt per kind::id)
    const tombMap = new Map();
    const putTomb = (t) => {
      if (!t || !t.id) return;
      const kind = t.kind === 'folder' ? 'folder' : 'note';
      const key = `${kind}::${t.id}`;
      const existing = tombMap.get(key);
      const delAt = Number(t.deletedAt) || 0;
      if (!existing || delAt > existing.deletedAt) {
        tombMap.set(key, { id: t.id, kind, deletedAt: delAt });
      }
    };
    (base.tombstones || []).forEach(putTomb);
    remote.tombstones.forEach(putTomb);

    // 2. Merge folders
    const localFolderMap = new Map((base.folders || []).map((f) => [f.id, f]));
    const remoteFolderMap = new Map((remote.folders || []).map((f) => [f.id, f]));
    const allFolderIds = new Set([...localFolderMap.keys(), ...remoteFolderMap.keys()]);
    const mergedFolders = [];
    let deletionsApplied = 0;

    for (const fid of allFolderIds) {
      const lf = localFolderMap.get(fid);
      const rf = remoteFolderMap.get(fid);
      const winner = pickFolderWinner(lf, rf);
      if (!winner) continue;
      const winTs = Math.max(Number(winner.updatedAt) || 0, Number(winner.createdAt) || 0);
      const tomb = tombMap.get(`folder::${fid}`);
      if (tomb && tomb.deletedAt >= winTs) {
        if (lf) deletionsApplied++;
        continue;
      }
      if (tomb && winTs > tomb.deletedAt) {
        tombMap.delete(`folder::${fid}`);
      }
      mergedFolders.push({
        id: winner.id,
        name: (winner.name || 'Folder').trim() || 'Folder',
        parentId: winner.parentId || null,
        icon: winner.icon || '📁',
        colorHex: winner.colorHex || '#A855F7',
        createdAt: Number(winner.createdAt) || nowMs,
        updatedAt: Number(winner.updatedAt) || Number(winner.createdAt) || nowMs
      });
    }

    const validFolderIds = new Set(mergedFolders.map((f) => f.id));
    for (const f of mergedFolders) {
      if (f.parentId && !validFolderIds.has(f.parentId)) f.parentId = null;
    }
    mergedFolders.sort((a, b) => (a.createdAt - b.createdAt) || a.name.localeCompare(b.name));

    // 3. Merge notes
    const localNoteMap = new Map((base.notes || []).map((n) => [n.id, n]));
    const remoteNoteMap = new Map((remote.notes || []).map((n) => [n.id, n]));
    const allNoteIds = new Set([...localNoteMap.keys(), ...remoteNoteMap.keys()]);
    const mergedNotes = [];
    let newNotesAdded = 0;
    let notesMerged = 0;

    for (const nid of allNoteIds) {
      const ln = localNoteMap.get(nid);
      const rn = remoteNoteMap.get(nid);
      const { winner, remoteWon } = pickNoteWinner(ln, rn);
      if (!winner) continue;

      const winTs = Math.max(Number(winner.updatedAt) || 0, Number(winner.createdAt) || 0);
      const tomb = tombMap.get(`note::${nid}`);
      if (tomb && tomb.deletedAt >= winTs) {
        if (ln) deletionsApplied++;
        continue;
      }
      if (tomb && winTs > tomb.deletedAt) {
        tombMap.delete(`note::${nid}`);
      }

      if (!ln && rn) newNotesAdded++;
      else if (ln && rn) notesMerged++;

      const other = remoteWon ? ln : rn;
      const synthesized = (winner.blocksJson && !winner.board)
        ? blocksJsonToBoard(winner, ln)
        : null;

      let chosenBoard = null;
      if (winner.board && Array.isArray(winner.board.pages) && winner.board.pages.length > 0) {
        chosenBoard = winner.board;
      } else if (synthesized) {
        chosenBoard = synthesized.board;
      } else if (other && other.board && winner.updatedAt <= Math.max(Number(other.updatedAt) || 0, Number(other.createdAt) || 0)) {
        chosenBoard = other.board;
      } else {
        chosenBoard = { gridStyle: 'DOTS', activePageIndex: 0, pages: [createEmptyBoardPage('Slide 1')] };
      }

      const extraBlocks = (synthesized && synthesized.extraBlocks && synthesized.extraBlocks.length)
        ? synthesized.extraBlocks
        : (winner.extraBlocks || (other && other.extraBlocks) || undefined);

      mergedNotes.push({
        id: winner.id,
        title: winner.title || 'Untitled Note',
        content: winner.content || '',
        folderId: winner.folderId && validFolderIds.has(winner.folderId) ? winner.folderId : null,
        isPinned: Boolean(winner.isPinned),
        isFavorite: Boolean(winner.isFavorite),
        isLocked: Boolean(winner.isLocked),
        isTrash: Boolean(winner.isTrash),
        trashTimestamp: winner.trashTimestamp || null,
        followUpAt: winner.followUpAt || (other && other.followUpAt) || null,
        createdAt: Number(winner.createdAt) || nowMs,
        updatedAt: Number(winner.updatedAt) || nowMs,
        templateType: winner.templateType || 'BOARD',
        ...(extraBlocks && extraBlocks.length ? { extraBlocks } : {}),
        board: chosenBoard
      });
    }

    mergedNotes.sort((a, b) => {
      if (Boolean(a.isPinned) !== Boolean(b.isPinned)) return a.isPinned ? -1 : 1;
      return (b.updatedAt || 0) - (a.updatedAt || 0);
    });

    const cutoff = nowMs - TOMBSTONE_TTL_MS;
    const boundedTombstones = Array.from(tombMap.values())
      .filter((t) => t.deletedAt >= cutoff)
      .sort((a, b) => b.deletedAt - a.deletedAt)
      .slice(0, MAX_TOMBSTONES);

    const activeStillValid = mergedNotes.some((n) => n.id === base.activeNoteId && !n.isTrash);
    const fallbackActive = mergedNotes.find((n) => !n.isTrash);

    const mergedVault = {
      ...base,
      activeNoteId: activeStillValid ? base.activeNoteId : (fallbackActive ? fallbackActive.id : null),
      folders: mergedFolders,
      notes: mergedNotes,
      tombstones: boundedTombstones
    };

    const graph = buildGraphData(mergedVault);
    return {
      vault: mergedVault,
      summary: {
        foldersSynced: mergedFolders.length,
        notesMerged,
        newNotesAdded,
        linksRebuilt: graph.edges.length,
        deletionsApplied,
        remoteDevice: remote.deviceName || remote.platform || 'Remote Device'
      }
    };
  }

  /**
   * Searches notes across titles, text body, board cards, checklists, and #tags.
   */
  function searchNotes(vault, query) {
    const all = (vault && Array.isArray(vault.notes)) ? vault.notes.filter((n) => !n.isTrash) : [];
    const q = String(query || '').trim().toLowerCase();
    if (!q) return all;

    return all.filter((note) => {
      if ((note.title || '').toLowerCase().includes(q)) return true;
      if ((note.content || '').toLowerCase().includes(q)) return true;
      const pages = (note.board && note.board.pages) || [];
      for (const page of pages) {
        for (const card of (page.cards || [])) {
          if ((card.title || '').toLowerCase().includes(q)) return true;
          if ((card.content || '').toLowerCase().includes(q)) return true;
          if (Array.isArray(card.items)) {
            for (const item of card.items) {
              if ((item.text || '').toLowerCase().includes(q)) return true;
            }
          }
        }
      }
      return false;
    });
  }

  /**
   * Builds 2D force-graph nodes & wiki-link edges across all notes and board cards.
   */
  function buildGraphData(vault) {
    const notes = ((vault && vault.notes) || []).filter((n) => !n.isTrash);
    const titleToId = new Map();
    for (const n of notes) {
      const clean = (n.title || '').trim().toLowerCase();
      if (clean) titleToId.set(clean, n.id);
    }

    const edgeSet = new Set();
    const edges = [];
    const linkRegex = /\[\[([^\]]+)\]\]/g;

    for (const note of notes) {
      const textParts = [note.title || '', note.content || ''];
      for (const page of ((note.board && note.board.pages) || [])) {
        for (const card of (page.cards || [])) {
          textParts.push(card.title || '', card.content || '');
        }
      }
      const combined = textParts.join('\n');

      // 1. [[Wiki-Links]]
      let m;
      linkRegex.lastIndex = 0;
      while ((m = linkRegex.exec(combined)) !== null) {
        const targetName = m[1].trim().toLowerCase();
        const targetId = titleToId.get(targetName);
        if (targetId && targetId !== note.id) {
          const key = note.id < targetId ? `${note.id}::${targetId}` : `${targetId}::${note.id}`;
          if (!edgeSet.has(key)) {
            edgeSet.add(key);
            edges.push({ sourceId: note.id, targetId });
          }
        }
      }

      // 2. Direct mentions of existing titles (len >= 3)
      for (const other of notes) {
        if (other.id === note.id) continue;
        const t = (other.title || '').trim();
        if (t.length < 3 || t.toLowerCase() === 'untitled' || t.toLowerCase() === 'untitled board') continue;
        if (combined.toLowerCase().includes(t.toLowerCase())) {
          const key = note.id < other.id ? `${note.id}::${other.id}` : `${other.id}::${note.id}`;
          if (!edgeSet.has(key)) {
            edgeSet.add(key);
            edges.push({ sourceId: note.id, targetId: other.id });
          }
        }
      }
    }

    const degreeMap = new Map();
    for (const e of edges) {
      degreeMap.set(e.sourceId, (degreeMap.get(e.sourceId) || 0) + 1);
      degreeMap.set(e.targetId, (degreeMap.get(e.targetId) || 0) + 1);
    }

    return {
      nodes: notes.map((n) => ({
        id: n.id,
        title: n.title || 'Untitled',
        folderId: n.folderId,
        degree: degreeMap.get(n.id) || 0
      })),
      edges
    };
  }

  /**
   * Geometric shape snapper for freehand ink: straight lines, circles, and rectangles.
   */
  function snapStrokeGeometry(points) {
    if (!Array.isArray(points) || points.length < 5) return points;

    const start = points[0];
    const end = points[points.length - 1];
    const endDist = Math.hypot(end.x - start.x, end.y - start.y);

    let totalLen = 0;
    for (let i = 1; i < points.length; i++) {
      totalLen += Math.hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y);
    }

    // 1. Straight line snap
    if (endDist > 36 && totalLen < endDist * 1.15) {
      return [
        { x: start.x, y: start.y },
        { x: end.x, y: end.y }
      ];
    }

    // 2. Closed shapes (circle or rectangle)
    if (endDist < 55 && totalLen > 95) {
      let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
      for (const p of points) {
        if (p.x < minX) minX = p.x;
        if (p.x > maxX) maxX = p.x;
        if (p.y < minY) minY = p.y;
        if (p.y > maxY) maxY = p.y;
      }
      const w = maxX - minX;
      const h = maxY - minY;
      const cx = (minX + maxX) / 2;
      const cy = (minY + maxY) / 2;
      const avgRadius = (w + h) / 4;

      if (avgRadius > 12) {
        let deviance = 0;
        for (const p of points) {
          deviance += Math.abs(Math.hypot(p.x - cx, p.y - cy) - avgRadius);
        }
        if (deviance / points.length < avgRadius * 0.23) {
          const ring = [];
          for (let s = 0; s <= 48; s++) {
            const a = (Math.PI * 2 * s) / 48;
            ring.push({
              x: cx + (w / 2) * Math.cos(a),
              y: cy + (h / 2) * Math.sin(a)
            });
          }
          return ring;
        }
      }

      if (w > 28 && h > 28) {
        return [
          { x: minX, y: minY },
          { x: maxX, y: minY },
          { x: maxX, y: maxY },
          { x: minX, y: maxY },
          { x: minX, y: minY }
        ];
      }
    }

    return points;
  }

  /**
   * Generates clean Obsidian-compatible Markdown for a note + board checklists & links.
   */
  function noteToMarkdown(note) {
    if (!note) return '';
    const lines = [`# ${note.title || 'Untitled'}`, ''];
    if (note.followUpAt) {
      lines.push(`> Follow-up Reminder: ${note.followUpAt}`, '');
    }
    if (note.content && note.content.trim()) {
      lines.push(note.content.trim(), '');
    }
    const pages = (note.board && note.board.pages) || [];
    pages.forEach((page, idx) => {
      const cards = page.cards || [];
      if (cards.length > 0) {
        lines.push(`## ${page.name || `Slide ${idx + 1}`}`, '');
        for (const card of cards) {
          if (card.kind === 'checklist') {
            lines.push(`### ${card.title || 'Checklist'}`);
            for (const item of (card.items || [])) {
              lines.push(`- [${item.isChecked ? 'x' : ' '}] ${item.text || ''}`);
            }
            lines.push('');
          } else if (card.kind === 'note' && card.id !== `main_${note.id}`) {
            if (card.title) lines.push(`### ${card.title}`);
            if (card.content) lines.push(card.content);
            lines.push('');
          }
        }
      }
    });
    return lines.join('\n');
  }

  return {
    SYNC_SCHEMA_VERSION,
    STARTER_FOLDERS,
    makeId,
    createEmptyBoardPage,
    createNoteRecord,
    createStarterVault,
    importAndroidNotesArray,
    exportToAndroidNotesArray,
    noteBoardToBlocksJson,
    blocksJsonToBoard,
    recordTombstone,
    buildSyncPackage,
    parseSyncPackage,
    mergeSyncPackage,
    searchNotes,
    buildGraphData,
    snapStrokeGeometry,
    noteToMarkdown
  };
});
