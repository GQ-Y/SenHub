/**
 * 点云 Web Worker：子线程解析二进制帧、维护滑动窗口，按 30fps 节流输出，确保 20 万点丰富度稳定显示。
 * 关键：收帧只做解析+入队，不每帧 flush；仅定时器每 33ms 做一次 flush（一边销毁过期帧一边输出当前窗口）。
 * 二进制格式（小端序）：1B type + 8B timestamp + 4B pointCount + 每点 13B (x,y,z float + r byte)
 */

const BINARY_HEADER = 1 + 8 + 4;
const BINARY_POINT_BYTES = 13;
const MAX_POINTS = 250000; // 滑动窗口最大点数（略大于 20 万/秒 × 1 秒）
const THROTTLE_MS = 33;    // 向主线程推送间隔 = 30fps，只在此节奏做一次「销毁过期 + 合并 + post」

interface Frame {
  timestamp: number;
  positions: Float32Array; // 已做坐标变换：tx,ty,tz = x,z,y
  reflectivity: Uint8Array;
  count: number;
}

let frames: Frame[] = [];
let totalPoints = 0;
let windowMs = 1000;
let lastPostTime = 0;
let pendingFrames: ArrayBuffer[] = [];

function parseBinary(ab: ArrayBuffer): { timestamp: number; count: number; positions: Float32Array; reflectivity: Uint8Array } | null {
  if (ab.byteLength < BINARY_HEADER) return null;
  const dv = new DataView(ab);
  let offset = 0;
  if (dv.getUint8(offset) !== 0) return null;
  offset += 1;
  const timestamp = Number(dv.getBigUint64(offset, true));
  offset += 8;
  const count = dv.getUint32(offset, true);
  offset += 4;
  const expectedLen = BINARY_HEADER + count * BINARY_POINT_BYTES;
  if (ab.byteLength < expectedLen) return null;

  const positions = new Float32Array(count * 3);
  const reflectivity = new Uint8Array(count);
  for (let i = 0; i < count; i++) {
    const x = dv.getFloat32(offset, true);
    const y = dv.getFloat32(offset + 4, true);
    const z = dv.getFloat32(offset + 8, true);
    const r = dv.getUint8(offset + 12);
    offset += BINARY_POINT_BYTES;
    // 坐标变换与 PointCloudRenderer 一致：tx=x, ty=z(高度), tz=y
    positions[i * 3] = x;
    positions[i * 3 + 1] = z;
    positions[i * 3 + 2] = y;
    reflectivity[i] = r;
  }
  return { timestamp, count, positions, reflectivity };
}

/** 反射率 -> RGB，与 PointCloudRenderer.getReflectivityColor 一致 */
function reflectivityToRgb(normalizedR: number): [number, number, number] {
  if (normalizedR < 0.25) {
    const t = normalizedR / 0.25;
    return [0.0, 0.5 * t, 1.0];
  }
  if (normalizedR < 0.5) {
    const t = (normalizedR - 0.25) / 0.25;
    return [0.0, 0.5 + 0.5 * t, 1.0 - t];
  }
  if (normalizedR < 0.75) {
    const t = (normalizedR - 0.5) / 0.25;
    return [t, 1.0, 0.0];
  }
  const t = (normalizedR - 0.75) / 0.25;
  return [1.0, 1.0 - t, 0.0];
}

function flushWindow() {
  const now = Date.now(); // 与服务器 timestamp（epoch ms）一致
  const cutoff = now - windowMs;

  // 移除过期帧
  while (frames.length > 0 && frames[0].timestamp < cutoff) {
    const f = frames.shift()!;
    totalPoints -= f.count;
  }
  // 超过最大点数时从最旧帧开始删
  while (totalPoints > MAX_POINTS && frames.length > 0) {
    const f = frames.shift()!;
    totalPoints -= f.count;
  }

  if (totalPoints === 0) {
    self.postMessage({ type: 'update', positions: new Float32Array(0), colors: new Float32Array(0), count: 0 });
    return;
  }

  // 使用固定的反射率范围 (0-255) 进行归一化，确保颜色梯度一致
  // 不使用动态范围，避免所有点反射率相近时颜色变化不明显
  const REFLECTIVITY_MIN = 0;
  const REFLECTIVITY_MAX = 255;
  const rRange = REFLECTIVITY_MAX - REFLECTIVITY_MIN;

  const positions = new Float32Array(totalPoints * 3);
  const colors = new Float32Array(totalPoints * 3);
  let idx = 0;
  for (const f of frames) {
    positions.set(f.positions, idx * 3);
    for (let i = 0; i < f.count; i++) {
      const normalizedR = (f.reflectivity[i] - REFLECTIVITY_MIN) / rRange;
      const [r, g, b] = reflectivityToRgb(normalizedR);
      colors[idx * 3] = r;
      colors[idx * 3 + 1] = g;
      colors[idx * 3 + 2] = b;
      idx++;
    }
  }

  const now2 = Date.now();
  if (now2 - lastPostTime >= THROTTLE_MS) {
    (self as any).postMessage(
      { type: 'update', positions, colors, count: totalPoints },
      { transfer: [positions.buffer, colors.buffer] }
    );
    lastPostTime = now2;
  }
}

/** 只解析并入队，不触发 flush（避免每帧 2000+ 次重建 20 万点数组） */
function processPending() {
  while (pendingFrames.length > 0) {
    const ab = pendingFrames.shift()!;
    const parsed = parseBinary(ab);
    if (parsed) {
      frames.push({
        timestamp: parsed.timestamp,
        positions: parsed.positions,
        reflectivity: parsed.reflectivity,
        count: parsed.count
      });
      totalPoints += parsed.count;
    }
  }
}

/** 定时刷新：每 33ms 做一次「销毁过期帧 + 合并窗口 + post」，30fps 稳定输出，保证 20 万点丰富度 */
let flushIntervalId: ReturnType<typeof setInterval> | null = null;

function startFlushTimer() {
  if (flushIntervalId != null) return;
  flushIntervalId = setInterval(() => {
    flushWindow();
  }, THROTTLE_MS);
}

self.onmessage = (e: MessageEvent) => {
  const msg = e.data;
  if (msg.type === 'binary' && msg.buffer instanceof ArrayBuffer) {
    pendingFrames.push(msg.buffer);
    processPending();
    startFlushTimer();
  } else if (msg.type === 'setWindow' && typeof msg.windowMs === 'number') {
    windowMs = msg.windowMs;
    lastPostTime = 0;
    flushWindow();
  }
};
