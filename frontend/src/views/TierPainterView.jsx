import { useCallback, useEffect, useRef, useState } from 'react';
import { TierGridModel } from '../utils/TierGridModel';
import {
  deleteTierZoneMap,
  downloadTierZoneMapUrl,
  getTierZoneMap,
  listTierZoneMaps,
  saveTierZoneMap,
} from '../services/tierZoneService';
import GoogleSheetsImportView from './GoogleSheetsImportView.jsx';

// ── Tier colours (RGBA 0-255) ──────────────────────────────────────────────
const TIER_COLORS = [
  [255, 80,  80,  1],   // 0 red
  [255, 160, 60,  1],   // 1 orange
  [255, 230, 50,  1],   // 2 yellow
  [80,  200, 80,  1],   // 3 green
  [60,  140, 255, 1],   // 4 blue
  [180, 80,  255, 1],   // 5 purple
  [0,   210, 210, 1],   // 6 cyan
  [255, 120, 200, 1],   // 7 pink
  [180, 230, 120, 1],   // 8 lime
  [140, 80,  40,  1],   // 9 brown
];

const ALPHA_ACTIVE   = 0.69;
const ALPHA_INACTIVE = 0.27;

function tierColor(tierIdx, isActive) {
  const [r, g, b] = TIER_COLORS[tierIdx % TIER_COLORS.length];
  const a = isActive ? ALPHA_ACTIVE : ALPHA_INACTIVE;
  return `rgba(${r},${g},${b},${a})`;
}

const DEFAULT_WORLD_SIZE = 15360;
const DEFAULT_TOTAL_TIERS = 5;

// ── Map presets ─────────────────────────────────────────────────────────────
const MAP_PRESETS = [
  { label: 'Chernarus (15360m)',   worldSize: 15360 },
  { label: 'Livonia (7360m)',      worldSize: 7360  },
  { label: 'Namalsk (7360m)',      worldSize: 7360  },
  { label: 'Sakhal (12800m)',      worldSize: 12800 },
  { label: 'Custom…',             worldSize: null  },
];

export default function TierPainterView({ token, onBack }) {
  const canvasRef = useRef(null);

  // Model (not state — mutated directly, canvas redraws via requestAnimationFrame)
  const modelRef    = useRef(null);
  const overlayRef  = useRef(null); // ImageData pixel cache (gridN*gridN*4)
  const dirtyRef    = useRef(true); // force full redraw on next frame
  const cellsDirtyRef = useRef(true); // true when cells/viewport changed (offscreen cache stale)
  const offscreenRef  = useRef(null); // cached static scene (bg + cells + grid lines)
  const bgImageRef  = useRef(null); // background map image

  useEffect(() => {
    const candidates = [
      '/maps/Chernarus.png',
      '/maps/chernarus.png',
      '/maps/Chernarus.jpg',
      '/maps/chernarus.jpg',
      '/maps/Chernarus.webp',
    ];
    let idx = 0;
    function tryNext() {
      if (idx >= candidates.length) return;
      const src = candidates[idx++];
      const img = new Image();
      img.onload = () => { bgImageRef.current = img; cellsDirtyRef.current = true; dirtyRef.current = true; };
      img.onerror = () => tryNext();
      img.src = src;
    }
    tryNext();
  }, []);

  function loadBgImage(baseName) {
    if (!baseName) {
      bgImageRef.current = null;
      cellsDirtyRef.current = true; dirtyRef.current = true;
      return;
    }

    const candidates = [
      `/maps/${baseName}.png`,
      `/maps/${baseName}.jpg`,
      `/maps/${baseName}.webp`,
      `/maps/${baseName.toLowerCase()}.png`,
    ];
    let idx = 0;
    bgImageRef.current = null;
    cellsDirtyRef.current = true; dirtyRef.current = true;

    function tryNext() {
      if (idx >= candidates.length) return;
      const src = candidates[idx++];
      const img = new Image();
      img.onload = () => { bgImageRef.current = img; cellsDirtyRef.current = true; dirtyRef.current = true; };
      img.onerror = () => tryNext();
      img.src = src;
    }

    tryNext();
  }

  // Viewport
  const viewportRef = useRef({ panX: 0, panY: 0, zoom: 1 });

  // Painting state
  const paintingRef   = useRef(false);
  const panningRef    = useRef(false);
  const lastMouseRef  = useRef({ x: 0, y: 0 });
  const pendingPaintRef = useRef(null); // {ix, iz}
  const lastPaintCellRef = useRef(null);
  const pointerRef = useRef({ x: 0, y: 0, inside: false });

  // Tool state (managed as React state for sidebar display)
  const [tierIdx,      setTierIdx]      = useState(0);
  const [brushRadius,  setBrushRadius]  = useState(3);
  const [eraseMode,    setEraseMode]    = useState(false);
  const [totalTiers,   setTotalTiers]   = useState(DEFAULT_TOTAL_TIERS);
  const [worldSize,    setWorldSize]    = useState(DEFAULT_WORLD_SIZE);
  const [showImportModal, setShowImportModal] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState('');

  // Map slot management
  const [savedMaps,    setSavedMaps]    = useState([]);
  const [activeMap,    setActiveMap]    = useState('');
  const [newMapName,   setNewMapName]   = useState('');
  const [mapStatus,    setMapStatus]    = useState('');
  const [mapLoading,   setMapLoading]   = useState(false);
  const pendingPolygonsRef = useRef(null);

  // Refs so keyboard handlers always have fresh values
  const tierIdxRef     = useRef(tierIdx);
  const brushRadiusRef = useRef(brushRadius);
  const eraseModeRef   = useRef(eraseMode);
  tierIdxRef.current     = tierIdx;
  brushRadiusRef.current = brushRadius;
  eraseModeRef.current   = eraseMode;

  // ── Initialise / reinitialise model when worldSize or totalTiers changes ──
  useEffect(() => {
    const model = new TierGridModel(worldSize, totalTiers);
    modelRef.current = model;
    overlayRef.current = null;   // force full rebuild
    cellsDirtyRef.current = true; dirtyRef.current = true;
    // If we have pending polygons that were loaded alongside a worldSize/totalTiers change,
    // import them into the newly-created model now.
    if (pendingPolygonsRef.current) {
      try {
        modelRef.current.loadPolygons(pendingPolygonsRef.current);
        pendingPolygonsRef.current = null;
        setMapStatus('Loaded map.');
      } catch (e) {
        setMapStatus('Load failed: ' + String(e));
      }
    }
    resetViewport();
  }, [worldSize, totalTiers]);

  // ── Canvas render loop ────────────────────────────────────────────────────
  useEffect(() => {
    let rafId;

    function frame() {
      rafId = requestAnimationFrame(frame);
      const canvas = canvasRef.current;
      const model  = modelRef.current;
      if (!canvas || !model) return;
      if (!dirtyRef.current) return;
      dirtyRef.current = false;

      const ctx    = canvas.getContext('2d');
      const vp     = viewportRef.current;
      const cellPx = (canvas.width / model.gridN) * vp.zoom;
      const offsetX = vp.panX + canvas.width  / 2 - (model.gridN * cellPx) / 2;
      const offsetY = vp.panY + canvas.height / 2 - (model.gridN * cellPx) / 2;

      // ── Rebuild offscreen cache when cells or viewport changed ────────
      if (cellsDirtyRef.current) {
        cellsDirtyRef.current = false;
        if (!offscreenRef.current) offscreenRef.current = document.createElement('canvas');
        const off = offscreenRef.current;
        if (off.width !== canvas.width || off.height !== canvas.height) {
          off.width  = canvas.width;
          off.height = canvas.height;
        }
        const oc = off.getContext('2d');
        oc.clearRect(0, 0, off.width, off.height);
        oc.save();
        oc.translate(offsetX, offsetY);

        // Grid background
        oc.fillStyle = '#1a1a2e';
        oc.fillRect(0, 0, model.gridN * cellPx, model.gridN * cellPx);

        // Background map image
        if (bgImageRef.current) {
          oc.drawImage(bgImageRef.current, 0, 0, model.gridN * cellPx, model.gridN * cellPx);
        }

        // Tier cells — single O(gridN²) pass, batch rects per tier via Path2D
        const tierPaths   = Array.from({ length: model.totalTiers }, () => new Path2D());
        const tierHasCell = new Uint8Array(model.totalTiers);
        for (let iz = 0; iz < model.gridN; iz++) {
          for (let ix = 0; ix < model.gridN; ix++) {
            const t = model.getCell(ix, iz);
            if (t < 0 || t >= model.totalTiers) continue;
            tierPaths[t].rect(ix * cellPx, iz * cellPx, cellPx, cellPx);
            tierHasCell[t] = 1;
          }
        }
        for (let tier = 0; tier < model.totalTiers; tier++) {
          if (!tierHasCell[tier]) continue;
          oc.fillStyle = tierColor(tier, tier === tierIdxRef.current);
          oc.fill(tierPaths[tier]);
        }

        // Grid lines — single batched path (was ~1200 individual stroke calls)
        if (cellPx > 2) {
          oc.strokeStyle = 'rgba(255,255,255,0.06)';
          oc.lineWidth = 0.5;
          oc.beginPath();
          for (let i = 0; i <= model.gridN; i++) {
            const p = i * cellPx;
            oc.moveTo(p, 0); oc.lineTo(p, model.gridN * cellPx);
            oc.moveTo(0, p); oc.lineTo(model.gridN * cellPx, p);
          }
          oc.stroke();
        }

        oc.restore();
        // Canvas border on offscreen
        oc.strokeStyle = '#444';
        oc.lineWidth = 1;
        oc.strokeRect(0, 0, off.width, off.height);
      }

      // ── Blit cached scene ─────────────────────────────────────────────
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (offscreenRef.current) ctx.drawImage(offscreenRef.current, 0, 0);

      // ── Brush preview cursor (always redrawn, never cached) ───────────
      if (pointerRef.current.inside) {
        const ix = Math.floor((pointerRef.current.x - offsetX) / cellPx);
        const iz = Math.floor((pointerRef.current.y - offsetY) / cellPx);
        const clampedIX = Math.max(0, Math.min(model.gridN - 1, ix));
        const clampedIZ = Math.max(0, Math.min(model.gridN - 1, iz));
        const centerX = (clampedIX + 0.5) * cellPx + offsetX;
        const centerZ = (clampedIZ + 0.5) * cellPx + offsetY;
        const radiusPx = brushRadiusRef.current * cellPx;
        ctx.save();
        ctx.beginPath();
        ctx.arc(centerX, centerZ, radiusPx, 0, Math.PI * 2);
        ctx.strokeStyle = eraseModeRef.current ? 'rgba(255,80,80,0.9)' : 'rgba(255,255,255,0.9)';
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(centerX, centerZ, radiusPx, 0, Math.PI * 2);
        ctx.fillStyle = eraseModeRef.current ? 'rgba(255,80,80,0.15)' : 'rgba(255,255,255,0.08)';
        ctx.fill();
        ctx.restore();
      }
    } // end frame

    rafId = requestAnimationFrame(frame);
    return () => cancelAnimationFrame(rafId);
  }, []);

  // ── Resize canvas to fill container ──────────────────────────────────────
  useEffect(() => {
    function onResize() {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const parent = canvas.parentElement;
      canvas.width  = parent.clientWidth  || 800;
      canvas.height = parent.clientHeight || 600;
      if (offscreenRef.current) { offscreenRef.current.width = 0; } // invalidate cached size
      cellsDirtyRef.current = true; dirtyRef.current = true;
    }
    onResize();
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  // ── Keyboard shortcuts ────────────────────────────────────────────────────
  useEffect(() => {
    function onKey(e) {
      if (e.target.tagName === 'INPUT') return;
      if (e.key === 'e' || e.key === 'E') {
        setEraseMode((v) => !v);
        return;
      }
      if (e.key === ']') {
        setTierIdx((v) => Math.min(v + 1, totalTiers - 1));
        cellsDirtyRef.current = true; dirtyRef.current = true;
        return;
      }
      if (e.key === '[') {
        setTierIdx((v) => Math.max(v - 1, 0));
        cellsDirtyRef.current = true; dirtyRef.current = true;
        return;
      }
      if (e.key === '+' || e.key === '=') {
        setBrushRadius((v) => Math.min(v + 1, 30));
        return;
      }
      if (e.key === '-' || e.key === '_') {
        setBrushRadius((v) => Math.max(v - 1, 1));
        return;
      }
      if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
        e.preventDefault();
        if (!modelRef.current) return;
        modelRef.current.undo();
        cellsDirtyRef.current = true; dirtyRef.current = true;
        return;
      }
      if (e.key === 'Backspace') {
        if (!modelRef.current) return;
        modelRef.current.undo();
        cellsDirtyRef.current = true; dirtyRef.current = true;
        return;
      }
      if (e.key === 'r' || e.key === 'R') {
        resetViewport();
        return;
      }
    }

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [totalTiers]);

  // ── Helpers ───────────────────────────────────────────────────────────────
  function resetViewport() {
    viewportRef.current = { panX: 0, panY: 0, zoom: 1 };
    cellsDirtyRef.current = true; dirtyRef.current = true;
  }

  function canvasToCell(canvasX, canvasY) {
    const canvas = canvasRef.current;
    const model  = modelRef.current;
    if (!canvas || !model) return null;
    const vp     = viewportRef.current;
    const cellPx = (canvas.width / model.gridN) * vp.zoom;
    const offsetX = vp.panX + canvas.width  / 2 - (model.gridN * cellPx) / 2;
    const offsetY = vp.panY + canvas.height / 2 - (model.gridN * cellPx) / 2;
    const ix = Math.floor((canvasX - offsetX) / cellPx);
    const iz = Math.floor((canvasY - offsetY) / cellPx);
    if (ix < 0 || iz < 0 || ix >= model.gridN || iz >= model.gridN) return null;
    return { ix, iz };
  }

  // ── Mouse events ──────────────────────────────────────────────────────────

  // Incrementally repaints only the brush bounding-box region on the offscreen
  // canvas — O((2r+1)²) instead of O(gridN²). Falls back to full rebuild when
  // the offscreen isn't ready or its size doesn't match the canvas.
  function incrementalPaintBrush(centerIX, centerIZ, radius) {
    const off    = offscreenRef.current;
    const canvas = canvasRef.current;
    const model  = modelRef.current;
    if (!off || !canvas || !model || off.width !== canvas.width || off.height !== canvas.height) {
      cellsDirtyRef.current = true;
      return;
    }
    const oc      = off.getContext('2d');
    const vp      = viewportRef.current;
    const cellPx  = (canvas.width / model.gridN) * vp.zoom;
    const offsetX = vp.panX + canvas.width  / 2 - (model.gridN * cellPx) / 2;
    const offsetY = vp.panY + canvas.height / 2 - (model.gridN * cellPx) / 2;

    const minIX = Math.max(0, centerIX - radius);
    const maxIX = Math.min(model.gridN - 1, centerIX + radius);
    const minIZ = Math.max(0, centerIZ - radius);
    const maxIZ = Math.min(model.gridN - 1, centerIZ + radius);

    const bx = minIX * cellPx + offsetX;
    const bz = minIZ * cellPx + offsetY;
    const bw = (maxIX - minIX + 1) * cellPx;
    const bh = (maxIZ - minIZ + 1) * cellPx;

    oc.save();
    oc.beginPath();
    oc.rect(bx, bz, bw, bh);
    oc.clip();

    // Repaint background in region
    oc.fillStyle = '#1a1a2e';
    oc.fillRect(bx, bz, bw, bh);
    if (bgImageRef.current) {
      oc.drawImage(bgImageRef.current, offsetX, offsetY, model.gridN * cellPx, model.gridN * cellPx);
    }

    // Repaint all tier cells in region (reads from model — already updated by applyBrush)
    for (let iz = minIZ; iz <= maxIZ; iz++) {
      for (let ix = minIX; ix <= maxIX; ix++) {
        const t = model.getCell(ix, iz);
        if (t < 0 || t >= model.totalTiers) continue;
        oc.fillStyle = tierColor(t, t === tierIdxRef.current);
        oc.fillRect(ix * cellPx + offsetX, iz * cellPx + offsetY, cellPx, cellPx);
      }
    }

    // Repaint grid lines in region
    if (cellPx > 2) {
      oc.strokeStyle = 'rgba(255,255,255,0.06)';
      oc.lineWidth = 0.5;
      oc.beginPath();
      for (let i = minIX; i <= maxIX + 1; i++) {
        const x = i * cellPx + offsetX;
        oc.moveTo(x, bz); oc.lineTo(x, bz + bh);
      }
      for (let i = minIZ; i <= maxIZ + 1; i++) {
        const z = i * cellPx + offsetY;
        oc.moveTo(bx, z); oc.lineTo(bx + bw, z);
      }
      oc.stroke();
    }

    oc.restore();
  }

  const onMouseDown = useCallback((e) => {
    if (e.button === 2) {
      panningRef.current = true;
      lastMouseRef.current = { x: e.clientX, y: e.clientY };
      return;
    }
    if (e.button === 0) {
      paintingRef.current = true;
      modelRef.current?.beginStroke();
      const rect = canvasRef.current.getBoundingClientRect();
      const cell = canvasToCell(e.clientX - rect.left, e.clientY - rect.top);
      if (cell) {
        const changed = modelRef.current?.applyBrush(cell.ix, cell.iz, brushRadiusRef.current, tierIdxRef.current, eraseModeRef.current);
        if (changed) incrementalPaintBrush(cell.ix, cell.iz, brushRadiusRef.current);
        dirtyRef.current = true;
      }
    }
  }, []);

  const onMouseLeaveCanvas = () => {
    pointerRef.current.inside = false;
    dirtyRef.current = true; // cursor hidden — blit only, no cell rebuild needed
    };

  const onMouseMove = useCallback((e) => {
    const rect = canvasRef.current.getBoundingClientRect();
    pointerRef.current.x = e.clientX - rect.left;
    pointerRef.current.y = e.clientY - rect.top;
    pointerRef.current.inside = true;
    dirtyRef.current = true; // cursor moved — always blit + redraw cursor
    if (panningRef.current) {
      const dx = e.clientX - lastMouseRef.current.x;
      const dy = e.clientY - lastMouseRef.current.y;
      viewportRef.current.panX += dx;
      viewportRef.current.panY += dy;
      lastMouseRef.current = { x: e.clientX, y: e.clientY };
      cellsDirtyRef.current = true; // viewport changed — rebuild offscreen
      return;
    }
    if (paintingRef.current) {
      const cell = canvasToCell(e.clientX - rect.left, e.clientY - rect.top);
      if (cell) {
        const changed = modelRef.current?.applyBrush(cell.ix, cell.iz, brushRadiusRef.current, tierIdxRef.current, eraseModeRef.current);
        if (changed) incrementalPaintBrush(cell.ix, cell.iz, brushRadiusRef.current);
        // dirtyRef already set above for cursor movement — no full offscreen rebuild needed
      }
    }
  }, []);

  const onMouseUp = useCallback((e) => {
    if (e.button === 2) { panningRef.current = false; return; }
    if (e.button === 0) {
      paintingRef.current = false;
      modelRef.current?.endStroke();
    }
  }, []);

  const onWheel = useCallback((e) => {
    e.preventDefault();
    const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
    viewportRef.current.zoom = Math.max(0.1, Math.min(40, viewportRef.current.zoom * factor));
    cellsDirtyRef.current = true; dirtyRef.current = true;
  }, []);

  // ── Map slot operations ───────────────────────────────────────────────────
  async function fetchMapList() {
    try {
      const maps = await listTierZoneMaps(token);
      setSavedMaps(maps);
    } catch {
      // Non-critical — just leave list empty
    }
  }

  useEffect(() => {
    fetchMapList();
  }, [token]);

  async function handleSave() {
    const name = (activeMap || newMapName).trim();
    if (!name) { setMapStatus('Enter a map name first.'); return; }
    if (!modelRef.current) return;

    setMapLoading(true);
    setMapStatus('Saving…');
    try {
      const payload = {
        worldSize: worldSize,
        totalTiers: totalTiers,
        polygons: modelRef.current.buildExportPolygons(),
      };
      const polygonsJson = JSON.stringify(payload);
      await saveTierZoneMap(token, name, polygonsJson);
      setActiveMap(name);
      setNewMapName('');
      setMapStatus(`Saved "${name}".`);
      await fetchMapList();
    } catch (err) {
      setMapStatus(`Save failed: ${err.message}`);
    } finally {
      setMapLoading(false);
    }
  }

  async function handleLoad(mapName) {
    setMapLoading(true);
    setMapStatus(`Loading "${mapName}"…`);
    try {
      const data = await getTierZoneMap(token, mapName);
      const parsed = JSON.parse(data.polygonsJson);
      // Backwards compatible: old format stored raw polygons array
      if (Array.isArray(parsed)) {
        modelRef.current?.loadPolygons(parsed);
        cellsDirtyRef.current = true; dirtyRef.current = true;
        setActiveMap(mapName);
        setMapStatus(`Loaded "${mapName}".`);
      } else if (parsed && parsed.polygons) {
        // If the saved payload included metadata, reinitialise model with worldSize/tiers
        pendingPolygonsRef.current = parsed.polygons;
        const ws = parsed.worldSize || DEFAULT_WORLD_SIZE;
        const tt = parsed.totalTiers || DEFAULT_TOTAL_TIERS;
        // If model already matches the saved metadata, import immediately
        if (modelRef.current && modelRef.current.worldSize === ws && modelRef.current.totalTiers === tt) {
          try {
            modelRef.current.loadPolygons(parsed.polygons);
            pendingPolygonsRef.current = null;
            setMapStatus(`Loaded "${mapName}".`);
          } catch (e) {
            setMapStatus('Load failed: ' + String(e));
          }
        } else {
          if (parsed.worldSize) setWorldSize(parsed.worldSize);
          if (parsed.totalTiers) setTotalTiers(parsed.totalTiers);
          setMapStatus(`Loaded "${mapName}".`);
        }
        setActiveMap(mapName);
      } else {
        throw new Error('Invalid map format');
      }
    } catch (err) {
      setMapStatus(`Load failed: ${err.message}`);
    } finally {
      setMapLoading(false);
    }
  }

  async function handleDelete(mapName) {
    if (!window.confirm(`Delete map "${mapName}"?`)) return;
    setMapLoading(true);
    try {
      await deleteTierZoneMap(token, mapName);
      if (activeMap === mapName) setActiveMap('');
      setMapStatus(`Deleted "${mapName}".`);
      await fetchMapList();
    } catch (err) {
      setMapStatus(`Delete failed: ${err.message}`);
    } finally {
      setMapLoading(false);
    }
  }

  function handleDownload() {
    const name = activeMap || newMapName.trim();
    if (!name) {
      // Download unsaved data directly from model
      if (!modelRef.current) return;
      const payload = {
        worldSize: worldSize,
        totalTiers: totalTiers,
        polygons: modelRef.current.buildExportPolygons(),
      };
      const json = JSON.stringify(payload, null, 2);
      const blob = new Blob([json], { type: 'application/json' });
      const url  = URL.createObjectURL(blob);
      const a    = document.createElement('a');
      a.href = url; a.download = 'tier_zones.json';
      document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
      return;
    }
    // Trigger backend download endpoint (includes auth header via window.open workaround)
    // For simplicity, export from model directly (avoids cross-origin token issues with window.open)
    if (!modelRef.current) return;
    const payload = {
      worldSize: worldSize,
      totalTiers: totalTiers,
      polygons: modelRef.current.buildExportPolygons(),
    };
    const json = JSON.stringify(payload, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href = url; a.download = `${name}_tiers.json`;
    document.body.appendChild(a); a.click(); a.remove();
    URL.revokeObjectURL(url);
  }

  function handleClear() {
    if (!window.confirm('Clear the entire canvas?')) return;
    modelRef.current?.clear();
    cellsDirtyRef.current = true; dirtyRef.current = true;
  }

  function handleUndo() {
    modelRef.current?.undo();
    cellsDirtyRef.current = true; dirtyRef.current = true;
  }

  function handlePresetChange(e) {
    const preset = MAP_PRESETS.find((p) => p.label === e.target.value);
    if (!preset) return;
    setSelectedPreset(preset.label);
    if (preset.worldSize !== null) {
      // apply preset world size (hide size input for builtin maps)
      setWorldSize(preset.worldSize);
    }
    // Load matching background image for known presets
    if (/chernarus/i.test(preset.label)) {
      loadBgImage('Chernarus');
    } else {
      loadBgImage(null);
    }
  }

  const nameToBeSaved = (activeMap || newMapName).trim();

  return (
    <div style={styles.root}>
      {/* ── Sidebar ──────────────────────────────────────────────────────── */}
      <div style={styles.sidebar}>
        <button onClick={onBack} style={styles.backBtn}>← Back</button>
        <h2 style={styles.title}>DayZ Tools</h2>

        {/* Map preset */}
        <section style={styles.section}>
          <label style={styles.label}>Map Preset</label>
          <select onChange={handlePresetChange} style={styles.select} value={selectedPreset}>
            <option value="" disabled>Select a map…</option>
            {MAP_PRESETS.map((p) => (
              <option key={p.label} value={p.label}>{p.label}</option>
            ))}
          </select>
          {/* World size is hidden for built-in presets — shown only for Custom… */}
          {selectedPreset === 'Custom…' && (
            <>
              <label style={styles.label}>World Size (m)</label>
              <input
                type="number"
                value={worldSize}
                min={1000}
                max={32768}
                step={256}
                onChange={(e) => setWorldSize(Number(e.target.value) || DEFAULT_WORLD_SIZE)}
                style={styles.input}
              />
            </>
          )}
          <label style={styles.label}>Total Tiers</label>
          <input
            type="number"
            value={totalTiers}
            min={1}
            max={10}
            onChange={(e) => setTotalTiers(Math.max(1, Math.min(10, Number(e.target.value) || DEFAULT_TOTAL_TIERS)))}
            style={styles.input}
          />
        </section>

        {/* Tier selector */}
        <section style={styles.section}>
          <label style={styles.label}>Active Tier</label>
          <div style={styles.tierGrid}>
            {Array.from({ length: totalTiers }, (_, i) => (
              <button
                key={i}
                onClick={() => { setTierIdx(i); cellsDirtyRef.current = true; dirtyRef.current = true; }}
                style={{
                  ...styles.tierBtn,
                  background: tierColor(i, i === tierIdx),
                  outline: i === tierIdx ? '2px solid #fff' : 'none',
                }}
              >
                T{i + 1}
              </button>
            ))}
          </div>
        </section>

        {/* Brush */}
        <section style={styles.section}>
          <label style={styles.label}>Brush Radius: {brushRadius} cells</label>
          <input
            type="range"
            min={1}
            max={30}
            value={brushRadius}
            onChange={(e) => setBrushRadius(Number(e.target.value))}
            style={styles.range}
          />
          <button
            onClick={() => setEraseMode((v) => !v)}
            style={{ ...styles.btn, background: eraseMode ? '#c0392b' : '#555' }}
          >
            {eraseMode ? 'Erasing (E)' : 'Painting (E)'}
          </button>
        </section>

        {/* Actions */}
        <section style={styles.section}>
          <button onClick={handleUndo}  style={styles.btn}>Undo (Ctrl+Z)</button>
          <button onClick={handleClear} style={{ ...styles.btn, marginTop: 4 }}>Clear Canvas</button>
          <button onClick={resetViewport} style={{ ...styles.btn, marginTop: 4 }}>Reset View (R)</button>
        </section>

        {/* Map slots */}
        <section style={styles.section}>
          <label style={styles.label}>Map Slots</label>
          {savedMaps.length > 0 && (
            <ul style={styles.mapList}>
              {savedMaps.map((m) => (
                <li key={m.mapName} style={styles.mapItem}>
                  <span
                    style={{ ...styles.mapName, fontWeight: activeMap === m.mapName ? 700 : 400 }}
                    title={`Updated: ${m.updatedAt}`}
                  >
                    {m.mapName}
                  </span>
                  <button onClick={() => handleLoad(m.mapName)}   style={styles.microBtn} disabled={mapLoading}>Load</button>
                  <button onClick={() => handleDelete(m.mapName)} style={{ ...styles.microBtn, color: '#f88' }} disabled={mapLoading}>Del</button>
                </li>
              ))}
            </ul>
          )}
          <input
            type="text"
            placeholder={activeMap || 'New map name…'}
            value={newMapName}
            onChange={(e) => setNewMapName(e.target.value)}
            style={styles.input}
            maxLength={100}
          />
          <button onClick={handleSave}     style={{ ...styles.btn, marginTop: 4 }} disabled={mapLoading || !nameToBeSaved}>
            {mapLoading ? 'Saving…' : `Save${nameToBeSaved ? ` "${nameToBeSaved}"` : ''}`}
          </button>
          <button onClick={handleDownload} style={{ ...styles.btn, marginTop: 4, background: '#27ae60' }}>
            Download JSON
          </button>
          {mapStatus && <p style={styles.mapStatus}>{mapStatus}</p>}
        </section>

        {/* Keybinds */}
        <section style={styles.section}>
          <p style={styles.hint}>LMB paint · RMB pan · Wheel zoom</p>
          <p style={styles.hint}>[/] cycle tier · +/- brush · E erase</p>
          <p style={styles.hint}>Ctrl+Z / Backspace undo · R reset view</p>
        </section>

        {/* ── Loot Table Tools ─────────────────────────────────────────── */}
        <div style={styles.toolsDivider} />
        <section style={styles.section}>
          <label style={{ ...styles.label, color: '#7ab3f0', letterSpacing: '0.05em', textTransform: 'uppercase', fontSize: 11 }}>
            Loot Table Tools
          </label>
          <p style={{ ...styles.hint, marginBottom: 6 }}>
            Import a loot table from your Google Sheets template.
          </p>
          <button onClick={() => setShowImportModal(true)} style={{ ...styles.btn, background: '#1a3a5e', border: '1px solid #2a5a9e' }}>
            Google Sheets Import
          </button>
        </section>
      </div>

      {/* ── Canvas ───────────────────────────────────────────────────────── */}
      <div style={styles.canvasWrap}>
        <canvas
          ref={canvasRef}
          style={styles.canvas}
          onMouseDown={onMouseDown}
          onMouseMove={onMouseMove}
          onMouseUp={onMouseUp}
          onMouseLeave={(e) => {
            onMouseUp(e);
            onMouseLeaveCanvas();
            }}
          onWheel={onWheel}
          onContextMenu={(e) => e.preventDefault()}
        />
      </div>
      {showImportModal && <GoogleSheetsImportView token={token} onClose={() => setShowImportModal(false)} />}
    </div>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────
const styles = {
  root: {
    display: 'flex',
    height: '100vh',
    background: '#111',
    color: '#eee',
    fontFamily: 'sans-serif',
    overflow: 'hidden',
  },
  sidebar: {
    width: 220,
    minWidth: 220,
    background: '#1e1e2e',
    padding: '12px 10px',
    overflowY: 'auto',
    borderRight: '1px solid #333',
    display: 'flex',
    flexDirection: 'column',
    gap: 0,
  },
  backBtn: {
    background: 'transparent',
    border: '1px solid #555',
    color: '#aaa',
    borderRadius: 4,
    padding: '4px 8px',
    cursor: 'pointer',
    marginBottom: 8,
    alignSelf: 'flex-start',
  },
  title: {
    fontSize: 15,
    margin: '0 0 10px',
    color: '#cdf',
  },
  section: {
    borderTop: '1px solid #333',
    paddingTop: 8,
    marginTop: 6,
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  },
  label: {
    fontSize: 11,
    color: '#888',
    marginBottom: 2,
  },
  select: {
    background: '#2a2a3e',
    color: '#eee',
    border: '1px solid #444',
    borderRadius: 4,
    padding: '3px 6px',
    fontSize: 12,
  },
  input: {
    background: '#2a2a3e',
    color: '#eee',
    border: '1px solid #444',
    borderRadius: 4,
    padding: '3px 6px',
    fontSize: 12,
    width: '100%',
    boxSizing: 'border-box',
  },
  tierGrid: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 3,
  },
  tierBtn: {
    width: 36,
    height: 26,
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 11,
    fontWeight: 700,
    color: '#fff',
    textShadow: '0 0 3px #000',
  },
  range: {
    width: '100%',
  },
  btn: {
    background: '#3a3a5e',
    color: '#eee',
    border: 'none',
    borderRadius: 4,
    padding: '5px 8px',
    cursor: 'pointer',
    fontSize: 12,
    textAlign: 'left',
  },
  mapList: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    maxHeight: 150,
    overflowY: 'auto',
  },
  mapItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    padding: '2px 0',
    borderBottom: '1px solid #2a2a3e',
  },
  mapName: {
    flex: 1,
    fontSize: 11,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    color: '#ccc',
  },
  microBtn: {
    background: 'transparent',
    border: '1px solid #444',
    color: '#aaa',
    borderRadius: 3,
    padding: '1px 5px',
    cursor: 'pointer',
    fontSize: 10,
    whiteSpace: 'nowrap',
  },
  mapStatus: {
    fontSize: 10,
    color: '#adf',
    margin: '2px 0 0',
  },
  hint: {
    fontSize: 10,
    color: '#666',
    margin: '1px 0',
    lineHeight: 1.4,
  },
  toolsDivider: {
    borderTop: '1px solid #2a2a3e',
    margin: '10px 0 2px',
  },
  canvasWrap: {
    flex: 1,
    overflow: 'hidden',
    position: 'relative',
  },
  canvas: {
    display: 'block',
    cursor: 'crosshair',
    width: '100%',
    height: '100%',
  },
};
