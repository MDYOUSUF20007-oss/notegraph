(function (root, factory) {
  if (typeof module === 'object' && module.exports) {
    module.exports = factory();
  } else {
    root.VaultBridge = factory();
  }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

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
      activeNoteId: welcomeBoardId,
      folders: STARTER_FOLDERS.map((f, idx) => ({ ...f, createdAt: now + idx })),
      notes: [welcomeBoard, dailyNote]
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
      let parsedBlocks = [];
      try {
        parsedBlocks = typeof raw.blocksJson === 'string'
          ? JSON.parse(raw.blocksJson)
          : Array.isArray(raw.blocksJson) ? raw.blocksJson : [];
      } catch (e) {
        parsedBlocks = [];
      }

      const cards = [];
      const strokes = [];
      let nextY = 90;

      if (raw.content && String(raw.content).trim().length > 0) {
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
        }
      }

      const prev = existingMap.get(raw.id);
      const mergedBoard = prev && prev.board && (!raw.updatedAt || prev.updatedAt > raw.updatedAt)
        ? prev.board
        : {
            gridStyle: 'DOTS',
            activePageIndex: 0,
            pages: [
              {
                id: makeId('page'),
                name: 'Imported Note',
                strokes,
                cards,
                connectors: [],
                viewport: { x: 0, y: 0, zoom: 1 }
              }
            ]
          };

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

  /**
   * Exports desktop vault notes to exact NoteGraph Android NoteEntity[] JSON schema.
   */
  function exportToAndroidNotesArray(vault) {
    if (!vault || !Array.isArray(vault.notes)) return [];
    return vault.notes
      .filter((n) => !n.isTrash)
      .map((note) => {
        const blocks = [];
        const pages = (note.board && Array.isArray(note.board.pages)) ? note.board.pages : [];
        const allCards = pages.flatMap((p) => Array.isArray(p.cards) ? p.cards : []);
        const allStrokes = pages.flatMap((p) => Array.isArray(p.strokes) ? p.strokes : []);

        for (const card of allCards) {
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

        return {
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
          blocksJson: JSON.stringify(blocks)
        };
      });
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
    STARTER_FOLDERS,
    makeId,
    createEmptyBoardPage,
    createNoteRecord,
    createStarterVault,
    importAndroidNotesArray,
    exportToAndroidNotesArray,
    searchNotes,
    buildGraphData,
    snapStrokeGeometry,
    noteToMarkdown
  };
});
