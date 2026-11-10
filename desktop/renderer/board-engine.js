(function (global) {
  'use strict';

  function clamp(val, min, max) {
    return val < min ? min : val > max ? max : val;
  }

  function hexToRgba(hex, alpha) {
    if (!hex || typeof hex !== 'string') return `rgba(255,255,255,${alpha})`;
    let clean = hex.replace('#', '').trim();
    if (clean.length === 3) {
      clean = clean.split('').map((c) => c + c).join('');
    }
    if (clean.length !== 6) return `rgba(255,255,255,${alpha})`;
    const num = parseInt(clean, 16);
    if (Number.isNaN(num)) return `rgba(255,255,255,${alpha})`;
    const r = (num >> 16) & 255;
    const g = (num >> 8) & 255;
    const b = num & 255;
    return `rgba(${r},${g},${b},${alpha})`;
  }

  function adaptInkColorForTheme(rawHex, themeMode) {
    if (!rawHex) return themeMode === 'KINDLE_PARCHMENT' ? '#2C2C2C' : '#FFFFFF';
    const upper = rawHex.toUpperCase();
    if (themeMode === 'KINDLE_PARCHMENT' && (upper === '#FFFFFF' || upper === '#E2E8F0')) {
      return '#2C2C2C';
    }
    if (themeMode !== 'KINDLE_PARCHMENT' && upper === '#2C2C2C') {
      return '#FFFFFF';
    }
    return rawHex;
  }

  function computeStrokeBounds(points, pad = 16) {
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (let i = 0; i < points.length; i++) {
      const p = points[i];
      if (p.x < minX) minX = p.x;
      if (p.y < minY) minY = p.y;
      if (p.x > maxX) maxX = p.x;
      if (p.y > maxY) maxY = p.y;
    }
    return {
      minX: minX - pad,
      minY: minY - pad,
      maxX: maxX + pad,
      maxY: maxY + pad
    };
  }

  function computeSelectedStrokesBounds(strokes, selectedIds) {
    if (!strokes || !selectedIds || selectedIds.size === 0) return null;
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    let hasPoints = false;
    for (let i = 0; i < strokes.length; i++) {
      const s = strokes[i];
      if (!selectedIds.has(s.id) || !s.points) continue;
      for (let j = 0; j < s.points.length; j++) {
        const p = s.points[j];
        hasPoints = true;
        if (p.x < minX) minX = p.x;
        if (p.y < minY) minY = p.y;
        if (p.x > maxX) maxX = p.x;
        if (p.y > maxY) maxY = p.y;
      }
    }
    return hasPoints ? { minX, minY, maxX, maxY } : null;
  }

  function translateSelectedStrokes(strokes, selectedIds, dx, dy) {
    if (!strokes || !selectedIds || selectedIds.size === 0 || (dx === 0 && dy === 0)) {
      return strokes;
    }
    return strokes.map((s) => {
      if (!selectedIds.has(s.id) || !s.points) return s;
      return {
        ...s,
        points: s.points.map((p) => ({ x: p.x + dx, y: p.y + dy }))
      };
    });
  }

  const LASER_TTL_MS = 1250;
  const LASER_MAX_ACTIVE_POINTS = 140;
  const LASER_MAX_COMPLETED_TRAILS = 6;
  const LASER_MIN_SPACING = 6;
  const LASER_HUE_BINS = 24;
  const LASER_ALPHA_STEPS = 5;

  // Precomputed static HSLA palette: 24 hues x 5 opacity steps = 120 strings (0 per-frame allocations)
  const LASER_COLOR_LUT = Array.from({ length: LASER_HUE_BINS }, (_, hIdx) => {
    const hue = Math.round((hIdx * 360) / LASER_HUE_BINS);
    return Array.from({ length: LASER_ALPHA_STEPS }, (__, aIdx) => {
      const alpha = ((aIdx + 1) / LASER_ALPHA_STEPS * 0.94).toFixed(2);
      return `hsla(${hue},100%,58%,${alpha})`;
    });
  });

  function pruneTimedPointsInPlace(points, nowMs, ttlMs = LASER_TTL_MS) {
    if (!Array.isArray(points) || points.length === 0) return points;
    const cutoff = nowMs - ttlMs;
    let firstLive = 0;
    while (firstLive < points.length && points[firstLive].t < cutoff) {
      firstLive++;
    }
    if (firstLive > 0) {
      points.splice(0, firstLive);
    }
    return points;
  }

  function appendLaserPoint(
    points,
    x,
    y,
    nowMs,
    minSpacing = LASER_MIN_SPACING,
    maxPoints = LASER_MAX_ACTIVE_POINTS,
    ttlMs = LASER_TTL_MS
  ) {
    pruneTimedPointsInPlace(points, nowMs, ttlMs);
    const len = points.length;
    if (len > 0) {
      const last = points[len - 1];
      const dx = x - last.x;
      const dy = y - last.y;
      if (dx * dx + dy * dy < minSpacing * minSpacing) {
        return false;
      }
    }
    points.push({ x, y, t: nowMs });
    if (points.length > maxPoints) {
      points.splice(0, points.length - maxPoints);
    }
    return true;
  }

  function pruneCompletedLaserTrails(
    trails,
    nowMs,
    ttlMs = LASER_TTL_MS,
    maxTrails = LASER_MAX_COMPLETED_TRAILS
  ) {
    if (!Array.isArray(trails) || trails.length === 0) return [];
    const alive = [];
    for (let i = 0; i < trails.length; i++) {
      const tr = trails[i];
      if (!tr || !Array.isArray(tr.points)) continue;
      pruneTimedPointsInPlace(tr.points, nowMs, ttlMs);
      if (tr.points.length >= 2) {
        alive.push(tr);
      }
    }
    if (alive.length > maxTrails) {
      return alive.slice(alive.length - maxTrails);
    }
    return alive;
  }

  function cloneStrokesShallow(strokes) {
    if (!Array.isArray(strokes)) return [];
    return strokes.map((s) => ({
      ...s,
      points: Array.isArray(s.points) ? s.points.map((p) => ({ x: p.x, y: p.y })) : []
    }));
  }

  const _strokePathCache = typeof WeakMap !== 'undefined' ? new WeakMap() : null;

  function getOrBuildStrokePath2D(stroke) {
    if (typeof Path2D === 'undefined' || !_strokePathCache || !stroke || !stroke.points) {
      return null;
    }
    let cached = _strokePathCache.get(stroke.points);
    if (cached) return cached;
    const pts = stroke.points;
    if (pts.length < 2) return null;
    const path = new Path2D();
    path.moveTo(pts[0].x, pts[0].y);
    if (pts.length === 2 || stroke.isArrow) {
      for (let i = 1; i < pts.length; i++) {
        path.lineTo(pts[i].x, pts[i].y);
      }
    } else {
      for (let i = 1; i < pts.length - 1; i++) {
        const midX = (pts[i].x + pts[i + 1].x) * 0.5;
        const midY = (pts[i].y + pts[i + 1].y) * 0.5;
        path.quadraticCurveTo(pts[i].x, pts[i].y, midX, midY);
      }
      const last = pts[pts.length - 1];
      path.lineTo(last.x, last.y);
    }
    _strokePathCache.set(stroke.points, path);
    return path;
  }

  function drawSmoothStroke(ctx, stroke, themeMode, isSelected) {
    const pts = stroke.points;
    if (!pts || pts.length < 2) return;

    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    const width = Number(stroke.strokeWidth) || 4;
    ctx.lineWidth = isSelected ? width + 2 : width;

    const color = isSelected
      ? '#00FFFF'
      : adaptInkColorForTheme(stroke.colorHex, themeMode);
    ctx.strokeStyle = stroke.isHighlighter ? hexToRgba(color, 0.36) : color;

    const cachedPath = getOrBuildStrokePath2D(stroke);
    if (cachedPath) {
      ctx.stroke(cachedPath);
    } else {
      ctx.beginPath();
      ctx.moveTo(pts[0].x, pts[0].y);
      if (pts.length === 2 || stroke.isArrow) {
        for (let i = 1; i < pts.length; i++) {
          ctx.lineTo(pts[i].x, pts[i].y);
        }
      } else {
        for (let i = 1; i < pts.length - 1; i++) {
          const midX = (pts[i].x + pts[i + 1].x) * 0.5;
          const midY = (pts[i].y + pts[i + 1].y) * 0.5;
          ctx.quadraticCurveTo(pts[i].x, pts[i].y, midX, midY);
        }
        const last = pts[pts.length - 1];
        ctx.lineTo(last.x, last.y);
      }
      ctx.stroke();
    }

    // Crisp vector arrowhead when arrow mode is active
    if (stroke.isArrow && pts.length >= 2) {
      const p1 = pts[pts.length - 2];
      const p2 = pts[pts.length - 1];
      const angle = Math.atan2(p2.y - p1.y, p2.x - p1.x);
      const headLen = Math.max(12, width * 3.4);
      ctx.beginPath();
      ctx.moveTo(p2.x, p2.y);
      ctx.lineTo(
        p2.x - headLen * Math.cos(angle - Math.PI / 6),
        p2.y - headLen * Math.sin(angle - Math.PI / 6)
      );
      ctx.moveTo(p2.x, p2.y);
      ctx.lineTo(
        p2.x - headLen * Math.cos(angle + Math.PI / 6),
        p2.y - headLen * Math.sin(angle + Math.PI / 6)
      );
      ctx.stroke();
    }

    ctx.restore();
  }

  function pointInPolygon(pt, poly) {
    if (!poly || poly.length < 3) return false;
    let inside = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const xi = poly[i].x, yi = poly[i].y;
      const xj = poly[j].x, yj = poly[j].y;
      const dy = yj - yi;
      const intersect = Math.abs(dy) > 1e-6 &&
        ((yi > pt.y) !== (yj > pt.y)) &&
        (pt.x < ((xj - xi) * (pt.y - yi)) / dy + xi);
      if (intersect) inside = !inside;
    }
    return inside;
  }

  function _cross2(o, a, b) {
    return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
  }

  function _segmentsIntersect(a, b, c, d) {
    if (Math.max(a.x, b.x) < Math.min(c.x, d.x) ||
        Math.max(c.x, d.x) < Math.min(a.x, b.x) ||
        Math.max(a.y, b.y) < Math.min(c.y, d.y) ||
        Math.max(c.y, d.y) < Math.min(a.y, b.y)) {
      return false;
    }
    const d1 = _cross2(c, d, a);
    const d2 = _cross2(c, d, b);
    const d3 = _cross2(a, b, c);
    const d4 = _cross2(a, b, d);
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
      ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
  }

  function strokeIntersectsPolygon(stroke, poly) {
    const pts = stroke && stroke.points;
    if (!pts || pts.length === 0 || !poly || poly.length < 3) return false;
    if (pts.some((p) => pointInPolygon(p, poly))) return true;
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1];
      const b = pts[i];
      const mid = { x: (a.x + b.x) * 0.5, y: (a.y + b.y) * 0.5 };
      if (pointInPolygon(mid, poly)) return true;
      for (let k = 0; k < poly.length; k++) {
        const c = poly[k];
        const d = poly[(k + 1) % poly.length];
        if (_segmentsIntersect(a, b, c, d)) return true;
      }
    }
    return false;
  }

  class BoardCanvasEngine {
    constructor(options) {
      this.stageEl = options.stageEl;
      this.gridCanvas = options.gridCanvas;
      this.staticCanvas = options.staticCanvas;
      this.liveCanvas = options.liveCanvas;
      this.cardsLayer = options.cardsLayer;

      this.gridCtx = this.gridCanvas.getContext('2d', { alpha: false }) || this.gridCanvas.getContext('2d');
      this.staticCtx = this.staticCanvas.getContext('2d');
      this.liveCtx = this.liveCanvas.getContext('2d', { desynchronized: true }) || this.liveCanvas.getContext('2d');

      this.dpr = Math.min(window.devicePixelRatio || 1, 2);
      this.width = 1000;
      this.height = 700;

      this.themeMode = 'DEEP_PURPLE_AMOLED';
      this.gridStyle = 'DOTS';
      this.gradientPreset = null;
      this.gradientsEnabled = true;
      this.tool = 'pen'; // 'select' | 'pan' | 'pen' | 'highlighter' | 'arrow' | 'laser' | 'eraser' | 'lasso' | 'connect'
      this.strokeColor = '#A855F7';
      this.strokeWidth = 4;
      this.shapeSnap = false;

      this.page = null;
      this.selectedStrokeIds = new Set();
      this.isLassoMoveMode = false;
      this.isDraggingLasso = false;
      this.dragLastWorld = null;
      this.redoStack = [];
      this.undoStack = [];
      this.vanishingTrails = [];
      this.activeLaserPoints = [];
      this.animFrameId = null;
      this.vanishingLoopActive = false;
      this._resizeRafId = null;
      this._laserBuckets = Array.from({ length: LASER_HUE_BINS }, () => []);

      this.activeStrokePoints = [];
      this.isDrawing = false;
      this.isPanning = false;
      this.spaceHeld = false;
      this.panStart = { sx: 0, sy: 0, vx: 0, vy: 0 };
      this.dragMovedStrokes = false;

      this.callbacks = {
        onStrokeCommitted: options.onStrokeCommitted || (() => {}),
        onStrokesModified: options.onStrokesModified || (() => {}),
        onViewportChanged: options.onViewportChanged || (() => {}),
        onSelectionChanged: options.onSelectionChanged || (() => {}),
        onLassoMoveModeChanged: options.onLassoMoveModeChanged || (() => {}),
        onBackgroundClick: options.onBackgroundClick || (() => {})
      };

      this._bindEvents();
      this.resize();
    }

    _bindEvents() {
      this._ro = new ResizeObserver(() => {
        if (this._resizeRafId) return;
        this._resizeRafId = requestAnimationFrame(() => {
          this._resizeRafId = null;
          this.resize();
        });
      });
      this._ro.observe(this.stageEl);

      this.liveCanvas.addEventListener('pointerdown', (e) => this._onPointerDown(e));
      this.liveCanvas.addEventListener('pointermove', (e) => this._onPointerMove(e));
      window.addEventListener('pointerup', (e) => this._onPointerUp(e));
      this.stageEl.addEventListener('wheel', (e) => this._onWheel(e), { passive: false });

      window.addEventListener('keydown', (e) => {
        if (e.code === 'Space' && !this._isTypingTarget(e.target)) {
          this.spaceHeld = true;
          this.updatePointerPassthrough();
        }
      });
      window.addEventListener('keyup', (e) => {
        if (e.code === 'Space') {
          this.spaceHeld = false;
          this.updatePointerPassthrough();
        }
      });
    }

    _isTypingTarget(el) {
      if (!el) return false;
      const tag = el.tagName;
      return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable;
    }

    setPage(page, themeMode, gridStyle, gradientPreset = undefined, gradientsEnabled = undefined) {
      this.page = page;
      this.themeMode = themeMode || this.themeMode;
      this.gridStyle = gridStyle || 'DOTS';
      if (gradientPreset !== undefined) this.gradientPreset = gradientPreset;
      if (typeof gradientsEnabled === 'boolean') this.gradientsEnabled = gradientsEnabled;
      this.selectedStrokeIds.clear();
      this.isLassoMoveMode = false;
      this.isDraggingLasso = false;
      this.dragLastWorld = null;
      this.activeLaserPoints = [];
      this.vanishingTrails = [];
      this.undoStack = [];
      this.redoStack = [];
      this.callbacks.onSelectionChanged(0);
      this.callbacks.onLassoMoveModeChanged(false);
      this.syncViewportTransform();
      this.redrawGrid();
      this.redrawStatic();
      this.redrawLive();
    }

    setAppearance(themeMode, gridStyle, gradientPreset = undefined, gradientsEnabled = undefined) {
      this.themeMode = themeMode;
      this.gridStyle = gridStyle;
      if (gradientPreset !== undefined) this.gradientPreset = gradientPreset;
      if (typeof gradientsEnabled === 'boolean') this.gradientsEnabled = gradientsEnabled;
      this.redrawGrid();
      this.redrawStatic();
    }

    setTool(tool) {
      this.tool = tool;
      if (tool !== 'lasso') {
        this.selectedStrokeIds.clear();
        this.isLassoMoveMode = false;
        this.isDraggingLasso = false;
        this.dragLastWorld = null;
        this.callbacks.onSelectionChanged(0);
        this.callbacks.onLassoMoveModeChanged(false);
        this.redrawStatic();
      }
      this.updatePointerPassthrough();
    }

    setLassoMoveMode(enabled) {
      this.isLassoMoveMode = Boolean(enabled);
      this.callbacks.onLassoMoveModeChanged(this.isLassoMoveMode);
      this.updatePointerPassthrough();
    }

    toggleLassoMoveMode() {
      this.setLassoMoveMode(!this.isLassoMoveMode);
    }

    updatePointerPassthrough() {
      const cardInteractive = !this.spaceHeld && (this.tool === 'select' || this.tool === 'connect');
      this.cardsLayer.style.pointerEvents = cardInteractive ? 'auto' : 'none';
      this.liveCanvas.style.pointerEvents = cardInteractive ? 'none' : 'auto';
      if (this.spaceHeld || this.tool === 'pan') {
        this.stageEl.style.cursor = this.isPanning ? 'grabbing' : 'grab';
      } else if (this.tool === 'lasso' && this.selectedStrokeIds.size > 0 && this.isLassoMoveMode) {
        this.stageEl.style.cursor = 'move';
      } else if (this.tool === 'select') {
        this.stageEl.style.cursor = 'default';
      } else if (this.tool === 'eraser') {
        this.stageEl.style.cursor = 'cell';
      } else {
        this.stageEl.style.cursor = 'crosshair';
      }
    }

    resize() {
      const rect = this.stageEl.getBoundingClientRect();
      this.width = Math.max(200, Math.floor(rect.width));
      this.height = Math.max(200, Math.floor(rect.height));
      this.dpr = Math.min(window.devicePixelRatio || 1, 2);

      [this.gridCanvas, this.staticCanvas, this.liveCanvas].forEach((cv) => {
        cv.width = Math.floor(this.width * this.dpr);
        cv.height = Math.floor(this.height * this.dpr);
        cv.style.width = `${this.width}px`;
        cv.style.height = `${this.height}px`;
      });

      this.redrawGrid();
      this.redrawStatic();
      this.redrawLive();
    }

    screenToWorld(sx, sy) {
      const vp = (this.page && this.page.viewport) || { x: 0, y: 0, zoom: 1 };
      return {
        x: (sx - vp.x) / vp.zoom,
        y: (sy - vp.y) / vp.zoom
      };
    }

    syncViewportTransform() {
      if (!this.page) return;
      const vp = this.page.viewport || (this.page.viewport = { x: 0, y: 0, zoom: 1 });
      this.cardsLayer.style.transform = `translate3d(${vp.x}px, ${vp.y}px, 0) scale(${vp.zoom})`;
    }

    zoomBy(factor, centerSx, centerSy) {
      if (!this.page) return;
      const vp = this.page.viewport || (this.page.viewport = { x: 0, y: 0, zoom: 1 });
      const cx = centerSx !== undefined ? centerSx : this.width / 2;
      const cy = centerSy !== undefined ? centerSy : this.height / 2;
      const oldZoom = vp.zoom;
      const newZoom = clamp(oldZoom * factor, 0.15, 4.0);
      const wx = (cx - vp.x) / oldZoom;
      const wy = (cy - vp.y) / oldZoom;
      vp.x = cx - wx * newZoom;
      vp.y = cy - wy * newZoom;
      vp.zoom = newZoom;
      this.syncViewportTransform();
      this.redrawGrid();
      this.redrawStatic();
      this.callbacks.onViewportChanged(vp);
    }

    resetViewport() {
      if (!this.page) return;
      this.page.viewport = { x: 0, y: 0, zoom: 1 };
      this.syncViewportTransform();
      this.redrawGrid();
      this.redrawStatic();
      this.callbacks.onViewportChanged(this.page.viewport);
    }

    fitToContent() {
      if (!this.page) return;
      const pts = [];
      for (const c of (this.page.cards || [])) {
        pts.push({ x: c.x, y: c.y }, { x: c.x + c.w, y: c.y + c.h });
      }
      for (const s of (this.page.strokes || [])) {
        for (const p of (s.points || [])) pts.push(p);
      }
      if (pts.length === 0) {
        this.resetViewport();
        return;
      }
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      for (const p of pts) {
        if (p.x < minX) minX = p.x;
        if (p.y < minY) minY = p.y;
        if (p.x > maxX) maxX = p.x;
        if (p.y > maxY) maxY = p.y;
      }
      const pad = 80;
      const contentW = Math.max(120, maxX - minX + pad * 2);
      const contentH = Math.max(120, maxY - minY + pad * 2);
      const zoom = clamp(Math.min(this.width / contentW, this.height / contentH), 0.25, 1.5);
      const cx = (minX + maxX) * 0.5;
      const cy = (minY + maxY) * 0.5;
      this.page.viewport = {
        x: this.width * 0.5 - cx * zoom,
        y: this.height * 0.5 - cy * zoom,
        zoom
      };
      this.syncViewportTransform();
      this.redrawGrid();
      this.redrawStatic();
      this.callbacks.onViewportChanged(this.page.viewport);
    }

    _onWheel(e) {
      e.preventDefault();
      if (!this.page) return;
      const rect = this.stageEl.getBoundingClientRect();
      const sx = e.clientX - rect.left;
      const sy = e.clientY - rect.top;

      if (e.ctrlKey || e.metaKey) {
        const zoomFactor = e.deltaY < 0 ? 1.12 : 0.89;
        this.zoomBy(zoomFactor, sx, sy);
      } else {
        const vp = this.page.viewport;
        vp.x -= e.deltaX;
        vp.y -= e.deltaY;
        this.syncViewportTransform();
        this.redrawGrid();
        this.redrawStatic();
        this.callbacks.onViewportChanged(vp);
      }
    }

    _onPointerDown(e) {
      if (!this.page) return;
      const rect = this.liveCanvas.getBoundingClientRect();
      const sx = e.clientX - rect.left;
      const sy = e.clientY - rect.top;

      // Middle click, spacebar pan, or Pan tool
      if (e.button === 1 || this.spaceHeld || this.tool === 'pan') {
        this.isPanning = true;
        this.panStart = {
          sx,
          sy,
          vx: this.page.viewport.x,
          vy: this.page.viewport.y
        };
        this.liveCanvas.setPointerCapture(e.pointerId);
        this.updatePointerPassthrough();
        return;
      }

      if (e.button !== 0) return;

      this.liveCanvas.setPointerCapture(e.pointerId);
      const pt = this.screenToWorld(sx, sy);

      if (this.tool === 'eraser') {
        this.isDrawing = true;
        this._eraseAtPoint(pt);
        return;
      }

      if (this.tool === 'lasso' && this.selectedStrokeIds.size > 0) {
        const bounds = computeSelectedStrokesBounds(this.page.strokes, this.selectedStrokeIds);
        const hitPad = 20 / Math.max(0.25, (this.page.viewport && this.page.viewport.zoom) || 1);
        const insideSelectionBox = bounds &&
          pt.x >= bounds.minX - hitPad &&
          pt.x <= bounds.maxX + hitPad &&
          pt.y >= bounds.minY - hitPad &&
          pt.y <= bounds.maxY + hitPad;
        if (this.isLassoMoveMode || insideSelectionBox) {
          this.isDraggingLasso = true;
          this.dragLastWorld = pt;
          this.dragMovedStrokes = false;
          this._pushUndoSnapshot();
          this.stageEl.style.cursor = 'move';
          return;
        }
      }

      if (this.tool === 'laser') {
        this.isDrawing = true;
        this.activeLaserPoints = [];
        appendLaserPoint(this.activeLaserPoints, pt.x, pt.y, performance.now());
        this._ensureVanishingAnimation();
        return;
      }

      if (['pen', 'highlighter', 'arrow', 'lasso'].includes(this.tool)) {
        this.isDrawing = true;
        this.activeStrokePoints = [pt];
        this.redrawLive();
      }
    }

    _onPointerMove(e) {
      if (!this.page) return;
      const rect = this.liveCanvas.getBoundingClientRect();

      if (this.isPanning) {
        const sx = e.clientX - rect.left;
        const sy = e.clientY - rect.top;
        this.page.viewport.x = this.panStart.vx + (sx - this.panStart.sx);
        this.page.viewport.y = this.panStart.vy + (sy - this.panStart.sy);
        this.syncViewportTransform();
        this.redrawGrid();
        this.redrawStatic();
        return;
      }

      if (this.isDraggingLasso) {
        const sx = e.clientX - rect.left;
        const sy = e.clientY - rect.top;
        const worldPt = this.screenToWorld(sx, sy);
        if (this.dragLastWorld) {
          const dx = worldPt.x - this.dragLastWorld.x;
          const dy = worldPt.y - this.dragLastWorld.y;
          if (dx !== 0 || dy !== 0) {
            this.page.strokes = translateSelectedStrokes(
              this.page.strokes,
              this.selectedStrokeIds,
              dx,
              dy
            );
            this.dragMovedStrokes = true;
            this.dragLastWorld = worldPt;
            this.redrawStatic();
          }
        } else {
          this.dragLastWorld = worldPt;
        }
        return;
      }

      if (!this.isDrawing) return;

      // Use high-rate PointerEvent coalescing for ultra-smooth stylus/mouse ink
      const events = typeof e.getCoalescedEvents === 'function'
        ? e.getCoalescedEvents()
        : [e];

      if (this.tool === 'eraser') {
        for (const ev of events) {
          const sx = ev.clientX - rect.left;
          const sy = ev.clientY - rect.top;
          this._eraseAtPoint(this.screenToWorld(sx, sy));
        }
        return;
      }

      if (this.tool === 'laser') {
        const nowMs = performance.now();
        let added = false;
        for (const ev of events) {
          const sx = ev.clientX - rect.left;
          const sy = ev.clientY - rect.top;
          const worldPt = this.screenToWorld(sx, sy);
          if (appendLaserPoint(this.activeLaserPoints, worldPt.x, worldPt.y, nowMs)) {
            added = true;
          }
        }
        if (added) {
          this._ensureVanishingAnimation();
        }
        return;
      }

      for (const ev of events) {
        const sx = ev.clientX - rect.left;
        const sy = ev.clientY - rect.top;
        const worldPt = this.screenToWorld(sx, sy);
        const last = this.activeStrokePoints[this.activeStrokePoints.length - 1];
        if (!last || Math.hypot(worldPt.x - last.x, worldPt.y - last.y) >= 1.2) {
          this.activeStrokePoints.push(worldPt);
        }
      }
      this.redrawLive();
    }

    _onPointerUp(e) {
      if (this.isPanning) {
        this.isPanning = false;
        this.updatePointerPassthrough();
        this.callbacks.onViewportChanged(this.page.viewport);
        return;
      }

      if (this.isDraggingLasso) {
        this.isDraggingLasso = false;
        this.dragLastWorld = null;
        this.updatePointerPassthrough();
        if (this.dragMovedStrokes) {
          this.dragMovedStrokes = false;
          this.redoStack = [];
          this.callbacks.onStrokesModified();
        } else {
          this.undoStack.pop();
        }
        return;
      }

      if (!this.isDrawing || !this.page) return;
      this.isDrawing = false;

      if (this.tool === 'eraser') {
        if (this.dragMovedStrokes) {
          this.dragMovedStrokes = false;
          this.callbacks.onStrokesModified();
        }
        return;
      }

      if (this.tool === 'laser') {
        const nowMs = performance.now();
        pruneTimedPointsInPlace(this.activeLaserPoints, nowMs, LASER_TTL_MS);
        if (this.activeLaserPoints.length >= 2) {
          this.vanishingTrails.push({
            points: this.activeLaserPoints.slice(),
            width: Math.max(5, this.strokeWidth + 2)
          });
          this.vanishingTrails = pruneCompletedLaserTrails(
            this.vanishingTrails,
            nowMs,
            LASER_TTL_MS,
            LASER_MAX_COMPLETED_TRAILS
          );
        }
        this.activeLaserPoints = [];
        this._ensureVanishingAnimation();
        return;
      }

      const rawPts = this.activeStrokePoints;
      this.activeStrokePoints = [];

      if (this.tool === 'lasso') {
        if (rawPts.length >= 3) {
          this.selectedStrokeIds.clear();
          for (const s of (this.page.strokes || [])) {
            if (strokeIntersectsPolygon(s, rawPts)) {
              this.selectedStrokeIds.add(s.id);
            }
          }
          if (this.selectedStrokeIds.size === 0) {
            this.setLassoMoveMode(false);
          }
        } else {
          this.selectedStrokeIds.clear();
          this.setLassoMoveMode(false);
        }
        this.callbacks.onSelectionChanged(this.selectedStrokeIds.size);
        this.redrawLive();
        this.redrawStatic();
        return;
      }

      if (rawPts.length < 2) {
        this.redrawLive();
        return;
      }

      let finalPoints = rawPts;
      if (this.tool === 'arrow') {
        finalPoints = [rawPts[0], rawPts[rawPts.length - 1]];
      } else if (this.shapeSnap && typeof global.VaultBridge !== 'undefined') {
        finalPoints = global.VaultBridge.snapStrokeGeometry(rawPts);
      }

      const newStroke = {
        id: global.VaultBridge ? global.VaultBridge.makeId('stk') : `stk_${Date.now()}`,
        points: finalPoints,
        colorHex: this.strokeColor,
        strokeWidth: this.tool === 'highlighter' ? Math.max(14, this.strokeWidth * 3) : this.strokeWidth,
        isHighlighter: this.tool === 'highlighter',
        isArrow: this.tool === 'arrow'
      };

      this._pushUndoSnapshot();
      this.redoStack = [];
      this.page.strokes.push(newStroke);
      this.redrawStatic();
      this.redrawLive();
      this.callbacks.onStrokeCommitted(newStroke);
    }

    _eraseAtPoint(worldPt) {
      if (!this.page || !this.page.strokes.length) return;
      const radiusSq = (20 / Math.max(0.25, this.page.viewport.zoom)) ** 2;
      const before = this.page.strokes.length;
      const remaining = this.page.strokes.filter((s) => {
        return !s.points.some((p) => {
          const dx = p.x - worldPt.x;
          const dy = p.y - worldPt.y;
          return dx * dx + dy * dy <= radiusSq;
        });
      });
      if (remaining.length !== before) {
        if (!this.dragMovedStrokes) {
          this._pushUndoSnapshot();
          this.dragMovedStrokes = true;
        }
        this.page.strokes = remaining;
        this.redrawStatic();
      }
    }

    _pushUndoSnapshot() {
      if (!this.page) return;
      this.undoStack.push(cloneStrokesShallow(this.page.strokes));
      if (this.undoStack.length > 40) this.undoStack.shift();
    }

    undo() {
      if (!this.page || this.undoStack.length === 0) return false;
      this.redoStack.push(cloneStrokesShallow(this.page.strokes));
      this.page.strokes = this.undoStack.pop();
      this.selectedStrokeIds.clear();
      this.setLassoMoveMode(false);
      this.callbacks.onSelectionChanged(0);
      this.redrawStatic();
      this.callbacks.onStrokesModified();
      return true;
    }

    redo() {
      if (!this.page || this.redoStack.length === 0) return false;
      this.undoStack.push(cloneStrokesShallow(this.page.strokes));
      this.page.strokes = this.redoStack.pop();
      this.selectedStrokeIds.clear();
      this.setLassoMoveMode(false);
      this.callbacks.onSelectionChanged(0);
      this.redrawStatic();
      this.callbacks.onStrokesModified();
      return true;
    }

    deleteSelectedStrokes() {
      if (!this.page || this.selectedStrokeIds.size === 0) return;
      this._pushUndoSnapshot();
      this.page.strokes = this.page.strokes.filter((s) => !this.selectedStrokeIds.has(s.id));
      this.selectedStrokeIds.clear();
      this.setLassoMoveMode(false);
      this.callbacks.onSelectionChanged(0);
      this.redrawStatic();
      this.callbacks.onStrokesModified();
    }

    nudgeSelectedStrokes(dx, dy) {
      if (!this.page || this.selectedStrokeIds.size === 0) return;
      this._pushUndoSnapshot();
      for (const s of this.page.strokes) {
        if (this.selectedStrokeIds.has(s.id)) {
          s.points = s.points.map((p) => ({ x: p.x + dx, y: p.y + dy }));
        }
      }
      this.redrawStatic();
      this.callbacks.onStrokesModified();
    }

    clearPageInk() {
      if (!this.page || this.page.strokes.length === 0) return;
      this._pushUndoSnapshot();
      this.page.strokes = [];
      this.selectedStrokeIds.clear();
      this.setLassoMoveMode(false);
      this.callbacks.onSelectionChanged(0);
      this.redrawStatic();
      this.callbacks.onStrokesModified();
    }

    _ensureVanishingAnimation() {
      if (this.vanishingLoopActive) return;
      this.vanishingLoopActive = true;
      const step = () => {
        const now = performance.now();
        this.vanishingTrails = pruneCompletedLaserTrails(
          this.vanishingTrails,
          now,
          LASER_TTL_MS,
          LASER_MAX_COMPLETED_TRAILS
        );
        if (this.activeLaserPoints.length > 0) {
          pruneTimedPointsInPlace(this.activeLaserPoints, now, LASER_TTL_MS);
        }
        this.redrawLive();
        if (
          this.vanishingTrails.length > 0 ||
          this.activeLaserPoints.length > 0 ||
          (this.isDrawing && this.tool === 'laser')
        ) {
          this.animFrameId = requestAnimationFrame(step);
        } else {
          this.vanishingLoopActive = false;
          this.animFrameId = null;
        }
      };
      this.animFrameId = requestAnimationFrame(step);
    }

    getPaletteColors() {
      let base;
      switch (this.themeMode) {
        case 'KINDLE_PARCHMENT':
          base = {
            bg: '#F4F1EA',
            dot: '#C8BFA8',
            line: '#DCD6CD',
            accent: '#B45309',
            accentEnd: '#D97706',
            cardBg: '#EAE4D7',
            text: '#2C2C2C'
          };
          break;
        case 'MATTE_STUDIO':
          base = {
            bg: '#1A1E25',
            dot: '#3B4252',
            line: '#2B313D',
            accent: '#38BDF8',
            accentEnd: '#6366F1',
            cardBg: '#212631',
            text: '#E5EAF2'
          };
          break;
        case 'BLACKBOARD_GREEN':
          base = {
            bg: '#0A2018',
            dot: '#1E4636',
            line: '#16382B',
            accent: '#34D399',
            accentEnd: '#0EA5E9',
            cardBg: '#0E281E',
            text: '#ECFDF5'
          };
          break;
        case 'DEEP_PURPLE_AMOLED':
        default:
          base = {
            bg: '#000000',
            dot: '#2A2238',
            line: '#191128',
            accent: '#A855F7',
            accentEnd: '#6366F1',
            cardBg: '#0E0819',
            text: '#FFFFFF'
          };
          break;
      }

      if (this.gradientPreset) {
        base.accent = this.gradientPreset.accentStart || base.accent;
        base.accentEnd = this.gradientPreset.accentEnd || base.accentEnd;
        base.bgTop = this.gradientPreset.bgTop || base.bg;
        base.bgMid = this.gradientPreset.bgMid || base.bg;
        base.bgBottom = this.gradientPreset.bgBottom || base.bg;
        if (this.gradientPreset.cardBg) base.cardBg = this.gradientPreset.cardBg;
        if (this.gradientPreset.dotColor) base.dot = this.gradientPreset.dotColor;
        if (this.gradientPreset.lineColor) base.line = this.gradientPreset.lineColor;
      }
      return base;
    }

    redrawGrid(targetCtx = this.gridCtx, targetW = this.width, targetH = this.height, vpOverride = null) {
      const ctx = targetCtx;
      const dpr = targetCtx === this.gridCtx ? this.dpr : 1;
      const pal = this.getPaletteColors();
      const vp = vpOverride || (this.page && this.page.viewport) || { x: 0, y: 0, zoom: 1 };

      ctx.save();
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      if (this.gradientsEnabled && pal.bgTop && pal.bgBottom) {
        const bgGrad = ctx.createLinearGradient(0, 0, 0, targetH);
        bgGrad.addColorStop(0, pal.bgTop);
        if (pal.bgMid) bgGrad.addColorStop(0.5, pal.bgMid);
        bgGrad.addColorStop(1, pal.bgBottom);
        ctx.fillStyle = bgGrad;
      } else {
        ctx.fillStyle = pal.bg;
      }
      ctx.fillRect(0, 0, targetW, targetH);

      if (this.gridStyle === 'NONE') {
        ctx.restore();
        return;
      }

      const stepWorld = 32;
      const screenStep = stepWorld * vp.zoom;
      if (screenStep >= 6) {
        const offsetX = ((vp.x % screenStep) + screenStep) % screenStep;
        const offsetY = ((vp.y % screenStep) + screenStep) % screenStep;

        if (this.gridStyle === 'DOTS') {
          ctx.fillStyle = pal.dot;
          const r = clamp(1.3 * Math.sqrt(vp.zoom), 0.9, 2.2);
          for (let gx = offsetX; gx < targetW; gx += screenStep) {
            for (let gy = offsetY; gy < targetH; gy += screenStep) {
              ctx.fillRect(gx - r * 0.5, gy - r * 0.5, r * 1.5, r * 1.5);
            }
          }
        } else if (this.gridStyle === 'RULED') {
          ctx.strokeStyle = pal.line;
          ctx.lineWidth = 1;
          ctx.beginPath();
          for (let gy = offsetY; gy < targetH; gy += screenStep) {
            ctx.moveTo(0, gy);
            ctx.lineTo(targetW, gy);
          }
          ctx.stroke();

          // Cornell vertical cue margin at world X = 96
          const cueScreenX = 96 * vp.zoom + vp.x;
          if (cueScreenX > 0 && cueScreenX < targetW) {
            ctx.strokeStyle = hexToRgba(pal.accent, 0.45);
            ctx.lineWidth = 2;
            ctx.beginPath();
            ctx.moveTo(cueScreenX, 0);
            ctx.lineTo(cueScreenX, targetH);
            ctx.stroke();
          }
        } else if (this.gridStyle === 'GRAPH') {
          ctx.strokeStyle = pal.line;
          ctx.lineWidth = 1;
          ctx.beginPath();
          for (let gx = offsetX; gx < targetW; gx += screenStep) {
            ctx.moveTo(gx, 0);
            ctx.lineTo(gx, targetH);
          }
          for (let gy = offsetY; gy < targetH; gy += screenStep) {
            ctx.moveTo(0, gy);
            ctx.lineTo(targetW, gy);
          }
          ctx.stroke();

          // Coordinate Origin Axes at World (0, 0) for Physics & Math classes
          ctx.strokeStyle = hexToRgba(pal.accent, 0.35);
          ctx.lineWidth = 1.6;
          ctx.beginPath();
          if (vp.x > 0 && vp.x < targetW) {
            ctx.moveTo(vp.x, 0);
            ctx.lineTo(vp.x, targetH);
          }
          if (vp.y > 0 && vp.y < targetH) {
            ctx.moveTo(0, vp.y);
            ctx.lineTo(targetW, vp.y);
          }
          ctx.stroke();
        }
      }

      ctx.restore();
    }

    redrawStatic() {
      const ctx = this.staticCtx;
      ctx.save();
      ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
      ctx.clearRect(0, 0, this.width, this.height);

      if (!this.page) {
        ctx.restore();
        return;
      }

      const vp = this.page.viewport || { x: 0, y: 0, zoom: 1 };
      ctx.translate(vp.x, vp.y);
      ctx.scale(vp.zoom, vp.zoom);

      // Compute visible world rect for fast viewport culling
      const viewMinX = -vp.x / vp.zoom - 60;
      const viewMinY = -vp.y / vp.zoom - 60;
      const viewMaxX = (this.width - vp.x) / vp.zoom + 60;
      const viewMaxY = (this.height - vp.y) / vp.zoom + 60;

      // 1. Draw Spatial Connector Arrows between Cards
      const cardMap = new Map((this.page.cards || []).map((c) => [c.id, c]));
      for (const conn of (this.page.connectors || [])) {
        const fromCard = cardMap.get(conn.fromCardId);
        const toCard = cardMap.get(conn.toCardId);
        if (!fromCard || !toCard) continue;
        this._drawConnectorArrow(ctx, fromCard, toCard, conn);
      }

      // 2. Draw Committed Strokes (Viewport-culled)
      for (const s of (this.page.strokes || [])) {
        if (!s.points || s.points.length < 2) continue;
        const b = computeStrokeBounds(s.points, s.strokeWidth || 8);
        if (b.maxX < viewMinX || b.minX > viewMaxX || b.maxY < viewMinY || b.minY > viewMaxY) {
          continue;
        }
        drawSmoothStroke(ctx, s, this.themeMode, this.selectedStrokeIds.has(s.id));
      }

      // 3. Draw unified dashed bounding box & handles around lasso selection
      const selBounds = computeSelectedStrokesBounds(this.page.strokes, this.selectedStrokeIds);
      if (selBounds) {
        const pad = 12;
        const left = selBounds.minX - pad;
        const top = selBounds.minY - pad;
        const w = Math.max(16, selBounds.maxX - selBounds.minX + pad * 2);
        const h = Math.max(16, selBounds.maxY - selBounds.minY + pad * 2);
        const boxColor = this.themeMode === 'KINDLE_PARCHMENT' ? '#0284C7' : '#00E5FF';

        ctx.save();
        ctx.fillStyle = this.isDraggingLasso ? hexToRgba(boxColor, 0.14) : hexToRgba(boxColor, 0.07);
        ctx.fillRect(left, top, w, h);
        ctx.setLineDash([10, 7]);
        ctx.lineWidth = (this.isDraggingLasso ? 2.4 : 1.75) / Math.max(0.25, vp.zoom);
        ctx.strokeStyle = boxColor;
        ctx.strokeRect(left, top, w, h);
        ctx.setLineDash([]);

        const handleR = 4.2 / Math.max(0.4, vp.zoom);
        ctx.fillStyle = boxColor;
        for (const [cx, cy] of [[left, top], [left + w, top], [left, top + h], [left + w, top + h]]) {
          ctx.beginPath();
          ctx.arc(cx, cy, handleR, 0, Math.PI * 2);
          ctx.fill();
        }
        ctx.restore();
      }

      ctx.restore();
    }

    _drawConnectorArrow(ctx, fromCard, toCard, conn) {
      const x1 = fromCard.x + fromCard.w * 0.5;
      const y1 = fromCard.y + fromCard.h * 0.5;
      const x2 = toCard.x + toCard.w * 0.5;
      const y2 = toCard.y + toCard.h * 0.5;
      const cpx1 = x1 + (x2 - x1) * 0.45;
      const cpy1 = y1;
      const cpx2 = x2 - (x2 - x1) * 0.45;
      const cpy2 = y2;

      ctx.save();
      const pal = this.getPaletteColors();
      if (this.gradientsEnabled) {
        const lineGrad = ctx.createLinearGradient(x1, y1, x2, y2);
        lineGrad.addColorStop(0, conn.colorHex || pal.accent);
        lineGrad.addColorStop(1, pal.accentEnd || conn.colorHex || pal.accent);
        ctx.strokeStyle = lineGrad;
      } else {
        ctx.strokeStyle = conn.colorHex || pal.accent;
      }
      ctx.lineWidth = 2.25;
      ctx.setLineDash([7, 5]);
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.bezierCurveTo(cpx1, cpy1, cpx2, cpy2, x2, y2);
      ctx.stroke();
      ctx.setLineDash([]);

      if (conn.label) {
        const mx = (x1 + x2) * 0.5;
        const my = (y1 + y2) * 0.5;
        ctx.font = '600 12px system-ui, -apple-system, sans-serif';
        const tw = ctx.measureText(conn.label).width + 14;
        ctx.fillStyle = this.getPaletteColors().cardBg;
        ctx.fillRect(mx - tw * 0.5, my - 11, tw, 22);
        ctx.strokeStyle = conn.colorHex || '#A855F7';
        ctx.lineWidth = 1;
        ctx.strokeRect(mx - tw * 0.5, my - 11, tw, 22);
        ctx.fillStyle = this.getPaletteColors().text;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(conn.label, mx, my);
      }
      ctx.restore();
    }

    redrawLive() {
      const ctx = this.liveCtx;
      ctx.save();
      ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
      ctx.clearRect(0, 0, this.width, this.height);

      if (!this.page) {
        ctx.restore();
        return;
      }

      const vp = this.page.viewport || { x: 0, y: 0, zoom: 1 };
      ctx.translate(vp.x, vp.y);
      ctx.scale(vp.zoom, vp.zoom);

      // 1. Draw Rainbow Vanishing Laser Trails (batched 24 hue bins, zero per-segment strings)
      const now = performance.now();
      for (let t = 0; t < this.vanishingTrails.length; t++) {
        const trail = this.vanishingTrails[t];
        if (trail && trail.points && trail.points.length >= 2) {
          this._drawRainbowTrailBatched(ctx, trail.points, trail.width, now);
        }
      }
      if (this.activeLaserPoints.length >= 2) {
        this._drawRainbowTrailBatched(ctx, this.activeLaserPoints, Math.max(5, this.strokeWidth + 2), now);
      }

      // 2. Draw In-Progress Active Stroke
      if (this.isDrawing && this.tool !== 'laser' && this.activeStrokePoints.length >= 2) {
        if (this.tool === 'lasso') {
          ctx.save();
          ctx.setLineDash([10, 7]);
          ctx.strokeStyle = '#00FFFF';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(this.activeStrokePoints[0].x, this.activeStrokePoints[0].y);
          for (let i = 1; i < this.activeStrokePoints.length; i++) {
            ctx.lineTo(this.activeStrokePoints[i].x, this.activeStrokePoints[i].y);
          }
          ctx.stroke();
          ctx.restore();
        } else {
          const pts = this.tool === 'arrow'
            ? [this.activeStrokePoints[0], this.activeStrokePoints[this.activeStrokePoints.length - 1]]
            : this.activeStrokePoints;
          drawSmoothStroke(
            ctx,
            {
              points: pts,
              colorHex: this.strokeColor,
              strokeWidth: this.tool === 'highlighter' ? Math.max(14, this.strokeWidth * 3) : this.strokeWidth,
              isHighlighter: this.tool === 'highlighter',
              isArrow: this.tool === 'arrow'
            },
            this.themeMode,
            false
          );
        }
      }

      ctx.restore();
    }

    _drawRainbowTrailBatched(ctx, points, baseWidth, nowMs) {
      if (!points || points.length < 2) return;
      const buckets = this._laserBuckets;
      for (let b = 0; b < LASER_HUE_BINS; b++) {
        buckets[b].length = 0;
      }

      const len = points.length;
      const phase = nowMs * 0.0025;
      let totalAlpha = 0;
      let segCount = 0;

      for (let i = 1; i < len; i++) {
        const p0 = points[i - 1];
        const p1 = points[i];
        const age = nowMs - p1.t;
        if (age >= LASER_TTL_MS) continue;
        const life = 1 - Math.max(0, age) / LASER_TTL_MS;
        totalAlpha += life;
        segCount++;
        const hueNorm = ((i * 0.042 + phase) % 1 + 1) % 1;
        const bin = Math.min(LASER_HUE_BINS - 1, Math.floor(hueNorm * LASER_HUE_BINS));
        buckets[bin].push(p0.x, p0.y, p1.x, p1.y);
      }

      if (segCount === 0) return;
      const avgLife = totalAlpha / segCount;
      const alphaStep = Math.max(
        0,
        Math.min(LASER_ALPHA_STEPS - 1, Math.floor(avgLife * (LASER_ALPHA_STEPS - 0.01)))
      );

      ctx.save();
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.lineWidth = Math.max(2, baseWidth * (0.65 + 0.35 * avgLife));

      for (let b = 0; b < LASER_HUE_BINS; b++) {
        const arr = buckets[b];
        if (arr.length === 0) continue;
        ctx.strokeStyle = LASER_COLOR_LUT[b][alphaStep];
        ctx.beginPath();
        for (let k = 0; k < arr.length; k += 4) {
          ctx.moveTo(arr[k], arr[k + 1]);
          ctx.lineTo(arr[k + 2], arr[k + 3]);
        }
        ctx.stroke();
      }
      ctx.restore();
    }

    /**
     * Renders an entire board page (Grid + Images/PDF cards + Notes/Checklists + Connectors + Strokes)
     * into an offscreen Canvas for sharp PNG or Multi-Page PDF export.
     */
    async renderPageToOffscreenCanvas(page, outW = 1600, outH = 1000, mime = 'image/png', quality = 0.92) {
      const offscreen = document.createElement('canvas');
      offscreen.width = outW;
      offscreen.height = outH;
      const ctx = offscreen.getContext('2d');

      const pts = [];
      for (const c of (page.cards || [])) {
        pts.push({ x: c.x, y: c.y }, { x: c.x + c.w, y: c.y + c.h });
      }
      for (const s of (page.strokes || [])) {
        for (const p of (s.points || [])) pts.push(p);
      }

      let vp = { x: 0, y: 0, zoom: 1 };
      if (pts.length > 0) {
        let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (const p of pts) {
          if (p.x < minX) minX = p.x;
          if (p.y < minY) minY = p.y;
          if (p.x > maxX) maxX = p.x;
          if (p.y > maxY) maxY = p.y;
        }
        const pad = 60;
        const cw = Math.max(200, maxX - minX + pad * 2);
        const ch = Math.max(200, maxY - minY + pad * 2);
        const zoom = Math.min(outW / cw, outH / ch);
        vp = {
          x: outW * 0.5 - ((minX + maxX) * 0.5) * zoom,
          y: outH * 0.5 - ((minY + maxY) * 0.5) * zoom,
          zoom
        };
      }

      this.redrawGrid(ctx, outW, outH, vp);

      ctx.save();
      ctx.translate(vp.x, vp.y);
      ctx.scale(vp.zoom, vp.zoom);

      const pal = this.getPaletteColors();
      const cardMap = new Map((page.cards || []).map((c) => [c.id, c]));

      for (const conn of (page.connectors || [])) {
        const f = cardMap.get(conn.fromCardId);
        const t = cardMap.get(conn.toCardId);
        if (f && t) this._drawConnectorArrow(ctx, f, t, conn);
      }

      for (const card of (page.cards || [])) {
        if ((card.kind === 'image' || card.kind === 'pdf') && card.imageDataUrl) {
          await new Promise((resolve) => {
            const img = new Image();
            img.onload = () => {
              ctx.fillStyle = '#FFFFFF';
              ctx.fillRect(card.x, card.y, card.w, card.h);
              ctx.drawImage(img, card.x, card.y, card.w, card.h);
              ctx.strokeStyle = card.colorHex || pal.accent;
              ctx.lineWidth = 2;
              ctx.strokeRect(card.x, card.y, card.w, card.h);
              resolve();
            };
            img.onerror = () => resolve();
            img.src = card.imageDataUrl;
          });
        } else {
          ctx.fillStyle = pal.cardBg;
          ctx.fillRect(card.x, card.y, card.w, card.h);
          ctx.strokeStyle = card.colorHex || pal.accent;
          ctx.lineWidth = 2;
          ctx.strokeRect(card.x, card.y, card.w, card.h);

          ctx.fillStyle = pal.accent;
          ctx.font = 'bold 16px system-ui, -apple-system, sans-serif';
          ctx.fillText((card.title || 'Card').slice(0, 42), card.x + 14, card.y + 26);

          ctx.fillStyle = pal.text;
          ctx.font = '13px system-ui, -apple-system, sans-serif';
          if (card.kind === 'checklist' && Array.isArray(card.items)) {
            let cy = card.y + 52;
            for (const item of card.items) {
              if (cy > card.y + card.h - 14) break;
              const prefix = item.isChecked ? '[✓]' : '[ ]';
              ctx.fillText(`${prefix} ${(item.text || '').slice(0, 46)}`, card.x + 14, cy);
              cy += 22;
            }
          } else if (card.content) {
            const lines = String(card.content).split('\n');
            let cy = card.y + 52;
            for (const line of lines) {
              if (cy > card.y + card.h - 14) break;
              ctx.fillText(line.slice(0, 50), card.x + 14, cy);
              cy += 20;
            }
          }
        }
      }

      for (const s of (page.strokes || [])) {
        drawSmoothStroke(ctx, s, this.themeMode, false);
      }

      ctx.restore();
      const dataUrl = offscreen.toDataURL(mime, quality);
      return dataUrl.split(',')[1];
    }
  }

  const BoardLassoUtils = {
    computeSelectedStrokesBounds,
    translateSelectedStrokes,
    strokeIntersectsPolygon,
    pointInPolygon
  };

  const BoardLaserUtils = {
    LASER_TTL_MS,
    LASER_MAX_ACTIVE_POINTS,
    LASER_MAX_COMPLETED_TRAILS,
    LASER_MIN_SPACING,
    LASER_HUE_BINS,
    LASER_ALPHA_STEPS,
    LASER_COLOR_LUT,
    appendLaserPoint,
    pruneTimedPointsInPlace,
    pruneCompletedLaserTrails,
    cloneStrokesShallow
  };

  global.BoardCanvasEngine = BoardCanvasEngine;
  global.BoardLassoUtils = BoardLassoUtils;
  global.BoardLaserUtils = BoardLaserUtils;

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = { BoardCanvasEngine, BoardLassoUtils, BoardLaserUtils };
  }
})(typeof window !== 'undefined' ? window : globalThis);
