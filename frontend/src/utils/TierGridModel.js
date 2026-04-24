/**
 * TierGridModel — JS port of the Python TierGridModel.
 * No UI dependencies; can be unit tested standalone.
 */

const SNAP_METERS = 25;

export class TierGridModel {
  constructor(worldSize = 15360, totalTiers = 5) {
    this.worldSize = worldSize;
    this.totalTiers = totalTiers;
    this.gridN = Math.ceil(worldSize / SNAP_METERS);

    // Flat Int16Array row-major: ownerGrid[iz * gridN + ix] = tier (-1 = empty)
    this.ownerGrid = new Int16Array(this.gridN * this.gridN).fill(-1);
    this.undoStack = [];          // Array of Arrays of {ix, iz, prev}
    this._pendingStroke = new Map(); // key = `${ix},${iz}` => prevOwner
    this._lastSignature = null;
  }

  clear() {
    this.ownerGrid.fill(-1);
    this.undoStack = [];
    this._pendingStroke.clear();
    this._lastSignature = null;
  }

  getCell(ix, iz) {
    return this.ownerGrid[iz * this.gridN + ix];
  }

  setCell(ix, iz, value) {
    this.ownerGrid[iz * this.gridN + ix] = value;
  }

  worldToCell(wx, wz) {
    let ix = Math.floor(wx / SNAP_METERS);
    let iz = Math.floor(wz / SNAP_METERS);
    ix = Math.max(0, Math.min(this.gridN - 1, ix));
    iz = Math.max(0, Math.min(this.gridN - 1, iz));
    return { ix, iz };
  }

  beginStroke() {
    this._pendingStroke.clear();
    this._lastSignature = null;
  }

  endStroke() {
    this._lastSignature = null;
    if (this._pendingStroke.size === 0) return false;

    const batch = [];
    for (const [key, prev] of this._pendingStroke) {
      const ix = key % this.gridN;
      const iz = Math.floor(key / this.gridN);
      batch.push({ ix, iz, prev });
    }
    this.undoStack.push(batch);
    this._pendingStroke.clear();
    return true;
  }

  // Returns true if any cells changed, false if no-op (same position as last call).
  applyBrush(ix, iz, radiusCells, tierIdx, erase) {
    const sig = `${ix},${iz},${radiusCells},${tierIdx},${erase}`;
    if (sig === this._lastSignature) return false;
    this._lastSignature = sig;

    const x0 = Math.max(0, ix - radiusCells);
    const x1 = Math.min(this.gridN - 1, ix + radiusCells);
    const z0 = Math.max(0, iz - radiusCells);
    const z1 = Math.min(this.gridN - 1, iz + radiusCells);
    const newOwner = erase ? -1 : tierIdx;
    const r2 = radiusCells * radiusCells;
    let anyChanged = false;

    for (let cx = x0; cx <= x1; cx++) {
      for (let cz = z0; cz <= z1; cz++) {
        const dx = cx - ix;
        const dz = cz - iz;
        if (dx * dx + dz * dz > r2) continue;

        const prev = this.getCell(cx, cz);
        if (prev === newOwner) continue;

        const key = cz * this.gridN + cx;  // integer key — no string allocation
        if (!this._pendingStroke.has(key)) {
          this._pendingStroke.set(key, prev);
        }

        this.setCell(cx, cz, newOwner);
        anyChanged = true;
      }
    }

    return anyChanged;
  }

  undo() {
    this._lastSignature = null;
    if (this.undoStack.length === 0) return [];

    const batch = this.undoStack.pop();
    const touched = [];
    for (let i = batch.length - 1; i >= 0; i--) {
      const { ix, iz, prev } = batch[i];
      this.setCell(ix, iz, prev);
      touched.push({ ix, iz });
    }
    return touched;
  }

  // --- JSON import ---
  loadPolygons(polygons) {
    this.clear();
    for (const entry of polygons) {
      const tier = (parseInt(entry.tier, 10) || 0) - 1;
      if (tier < 0 || tier >= this.totalTiers) continue;
      const pts = entry.points;
      if (!Array.isArray(pts) || pts.length < 3) continue;

      // Axis-aligned rectangle fast path
      if (pts.length === 4) {
        const xs = pts.map((p) => parseFloat(p[0]));
        const zs = pts.map((p) => parseFloat(p[1]));
        const uniqueX = [...new Set(xs)];
        const uniqueZ = [...new Set(zs)];
        if (uniqueX.length === 2 && uniqueZ.length === 2) {
          const minX = Math.min(...uniqueX);
          const maxX = Math.max(...uniqueX);
          const minZ = Math.min(...uniqueZ);
          const maxZ = Math.max(...uniqueZ);
          const ix0 = Math.max(0, Math.min(this.gridN - 1, Math.floor(minX / SNAP_METERS)));
          const ix1 = Math.max(0, Math.min(this.gridN - 1, Math.ceil(maxX / SNAP_METERS) - 1));
          const iz0 = Math.max(0, Math.min(this.gridN - 1, Math.floor(minZ / SNAP_METERS)));
          const iz1 = Math.max(0, Math.min(this.gridN - 1, Math.ceil(maxZ / SNAP_METERS) - 1));
          for (let iz = iz0; iz <= iz1; iz++) {
            for (let ix = ix0; ix <= ix1; ix++) {
              this.setCell(ix, iz, tier);
            }
          }
          continue;
        }
      }

      // General polygon — point-in-polygon check per cell centre
      this._fillPolygon(pts, tier);
    }
  }

  _fillPolygon(pts, tier) {
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    for (const p of pts) {
      const x = parseFloat(p[0]);
      const z = parseFloat(p[1]);
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (z < minZ) minZ = z;
      if (z > maxZ) maxZ = z;
    }
    const ix0 = Math.max(0, Math.min(this.gridN - 1, Math.floor(minX / SNAP_METERS)));
    const ix1 = Math.max(0, Math.min(this.gridN - 1, Math.floor(maxX / SNAP_METERS)));
    const iz0 = Math.max(0, Math.min(this.gridN - 1, Math.floor(minZ / SNAP_METERS)));
    const iz1 = Math.max(0, Math.min(this.gridN - 1, Math.floor(maxZ / SNAP_METERS)));

    for (let iz = iz0; iz <= iz1; iz++) {
      const cz = (iz + 0.5) * SNAP_METERS;
      for (let ix = ix0; ix <= ix1; ix++) {
        const cx = (ix + 0.5) * SNAP_METERS;
        if (pointInPolygon(cx, cz, pts)) {
          this.setCell(ix, iz, tier);
        }
      }
    }
  }

  // --- JSON export (axis-aligned rectangle merging) ---
  buildExportPolygons() {
    const polygons = [];
    for (let tier = this.totalTiers - 1; tier >= 0; tier--) {
      const rects = this._maskToMergedRectangles(tier);
      for (const [x0, x1, z0, z1] of rects) {
        const minX = x0 * SNAP_METERS;
        const maxX = (x1 + 1) * SNAP_METERS;
        const minZ = z0 * SNAP_METERS;
        const maxZ = (z1 + 1) * SNAP_METERS;
        polygons.push({
          tier: tier + 1,
          points: [
            [minX, minZ],
            [maxX, minZ],
            [maxX, maxZ],
            [minX, maxZ],
          ],
        });
      }
    }
    return polygons;
  }

  _maskToMergedRectangles(tier) {
    const active = new Map(); // key `${x0},${x1}` => [x0, x1, z0, z1]
    const merged = [];

    for (let iz = 0; iz < this.gridN; iz++) {
      const runs = [];
      let inRun = false;
      let runStart = 0;

      for (let ix = 0; ix < this.gridN; ix++) {
        const owned = this.getCell(ix, iz) === tier;
        if (owned && !inRun) { inRun = true; runStart = ix; }
        else if (!owned && inRun) { inRun = false; runs.push([runStart, ix - 1]); }
      }
      if (inRun) runs.push([runStart, this.gridN - 1]);

      const extended = new Set();
      for (const [x0, x1] of runs) {
        const key = `${x0},${x1}`;
        const existing = active.get(key);
        if (existing && existing[3] === iz - 1) {
          existing[3] = iz;
        } else {
          if (existing) merged.push(existing);
          active.set(key, [x0, x1, iz, iz]);
        }
        extended.add(key);
      }

      for (const [key, rect] of active) {
        if (!extended.has(key)) {
          merged.push(rect);
          active.delete(key);
        }
      }
    }

    for (const rect of active.values()) merged.push(rect);
    return merged;
  }

  toJsonString() {
    return JSON.stringify(this.buildExportPolygons(), null, 2);
  }
}

function pointInPolygon(px, pz, pts) {
  let inside = false;
  const n = pts.length;
  for (let i = 0, j = n - 1; i < n; j = i++) {
    const xi = parseFloat(pts[i][0]);
    const zi = parseFloat(pts[i][1]);
    const xj = parseFloat(pts[j][0]);
    const zj = parseFloat(pts[j][1]);
    const intersects = zi > pz !== zj > pz && px < ((xj - xi) * (pz - zi)) / (zj - zi) + xi;
    if (intersects) inside = !inside;
  }
  return inside;
}
