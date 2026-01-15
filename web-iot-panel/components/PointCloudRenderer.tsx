import React, { useEffect, useRef, useMemo } from 'react';
import * as THREE from 'three';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
  isStaticBackground?: boolean; // 标记是否为静态背景点，用于防止检测时的自误报
  zoneId?: string; // 标记该点所属的防区ID（如果是侵入点）
}

interface PointCloudRendererProps {
  points: Point[];
  color?: string | ((point: Point) => string);
  pointSize?: number;
  backgroundColor?: string;
  showGrid?: boolean;
  showAxes?: boolean;
  showRangeRings?: boolean;
  colorMode?: 'height' | 'distance' | 'reflectivity' | 'intensity' | 'fixed' | 'defense';
  showModeling?: boolean;
  modelingDistance?: number;
  modelingMaxConnections?: number;
  // 防区模式相关
  defenseBackgroundPoints?: Point[];
  shrinkDistance?: number; // 默认 0.2m (20cm)
}

/**
 * 点云渲染组件（Three.js）
 */
export const PointCloudRenderer: React.FC<PointCloudRendererProps> = ({
  points,
  color = '#ffffff',
  pointSize = 0.01,
  backgroundColor = '#000000',
  showGrid = true,
  showAxes = true,
  showRangeRings = true,
  colorMode = 'height',
  showModeling = false,
  modelingDistance = 0.2,
  modelingMaxConnections = 3,
  defenseBackgroundPoints = [],
  shrinkDistance = 0.2
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const controlsRef = useRef<any>(null);
  const pointsRef = useRef<THREE.Points | null>(null);
  const modelingRef = useRef<THREE.LineSegments | null>(null);
  const backgroundPointsRef = useRef<THREE.Points | null>(null); // 背景参考点云
  const animationFrameRef = useRef<number | null>(null);
  const cameraInitializedRef = useRef<boolean>(false);

  // 范围环和辅助对象的引用
  const rangeRingsGroupRef = useRef<THREE.Group | null>(null);
  const axesLabelsRef = useRef<HTMLDivElement[]>([]);
  const gridHelperRef = useRef<THREE.GridHelper | null>(null);
  const axesHelperRef = useRef<THREE.AxesHelper | null>(null);
  const colorModeRef = useRef(colorMode);

  useEffect(() => {
    colorModeRef.current = colorMode;
  }, [colorMode]);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

    const container = containerRef.current;
    const width = container.clientWidth;
    const height = container.clientHeight;

    // 创建场景
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(backgroundColor);
    sceneRef.current = scene;

    // 创建相机（PerspectiveCamera确保三维透视效果）
    const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 2000);
    camera.position.set(5, 5, 5);
    camera.lookAt(0, 0, 0);
    cameraRef.current = camera;

    // 创建渲染器（启用高性能模式）
    const renderer = new THREE.WebGLRenderer({
      antialias: true,
      powerPreference: 'high-performance' // 性能优先
    });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2)); // 限制像素比以提高性能
    container.appendChild(renderer.domElement);
    rendererRef.current = renderer;

    // 添加网格
    if (showGrid) {
      const gridSize = 50;
      const gridDivisions = 50;
      const gridHelper = new THREE.GridHelper(gridSize, gridDivisions, 0x444444, 0x222222);
      gridHelper.position.y = 0;
      scene.add(gridHelper);
      gridHelperRef.current = gridHelper;
    }

    // 添加坐标轴
    if (showAxes) {
      const axesSize = 10;
      const axesHelper = new THREE.AxesHelper(axesSize);
      scene.add(axesHelper);
      axesHelperRef.current = axesHelper;
    }

    // 添加范围环
    if (showRangeRings) {
      const rangeRingsGroup = createRangeRings();
      scene.add(rangeRingsGroup);
      rangeRingsGroupRef.current = rangeRingsGroup;
    }

    // 添加光源
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    scene.add(ambientLight);
    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.4);
    directionalLight.position.set(5, 5, 5);
    scene.add(directionalLight);

    // 动态导入OrbitControls
    let OrbitControls: any = null;
    import('three/examples/jsm/controls/OrbitControls').then((module) => {
      OrbitControls = module.OrbitControls;
      if (OrbitControls && renderer) {
        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.05;
        controls.enablePan = true;
        controls.enableZoom = true;
        controls.enableRotate = true;
        controls.minDistance = 0.1;
        controls.maxDistance = 1000;
        controls.minPolarAngle = 0;
        controls.maxPolarAngle = Math.PI;
        controls.panSpeed = 1.0;
        controls.zoomSpeed = 1.0;
        controls.rotateSpeed = 1.0;
        controlsRef.current = controls;
      }
    }).catch(() => {
      console.warn('OrbitControls not available, using basic controls');
    });

    // 渲染循环
    const animate = () => {
      animationFrameRef.current = requestAnimationFrame(animate);

      if (controlsRef.current) {
        controlsRef.current.update();
      }

      renderer.render(scene, camera);
    };
    animate();

    // 处理窗口大小变化
    const handleResize = () => {
      if (!containerRef.current || !camera || !renderer) return;
      const newWidth = containerRef.current.clientWidth;
      const newHeight = containerRef.current.clientHeight;
      camera.aspect = newWidth / newHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(newWidth, newHeight);
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (renderer) {
        container.removeChild(renderer.domElement);
        renderer.dispose();
      }
      cameraInitializedRef.current = false;
    };
  }, [backgroundColor, showGrid, showAxes, showRangeRings]);

  /**
   * 创建范围环（Range Rings）
   * 在XZ平面上创建同心圆环，表示距离
   */
  const createRangeRings = () => {
    const group = new THREE.Group();
    const distances = [5, 10, 15, 20, 25, 30]; // 距离值（米）

    distances.forEach((distance) => {
      // 创建圆环几何体
      const geometry = new THREE.RingGeometry(distance - 0.02, distance + 0.02, 64);
      const material = new THREE.MeshBasicMaterial({
        color: 0x4a90d9,
        side: THREE.DoubleSide,
        transparent: true,
        opacity: 0.4
      });
      const ring = new THREE.Mesh(geometry, material);
      ring.rotation.x = -Math.PI / 2; // 旋转到XZ平面
      ring.position.y = 0.01; // 略微抬高以避免z-fighting
      group.add(ring);

      // 添加距离标签（使用精灵）
      const canvas = document.createElement('canvas');
      const ctx = canvas.getContext('2d');
      if (ctx) {
        canvas.width = 128;
        canvas.height = 64;
        ctx.fillStyle = 'rgba(74, 144, 217, 0.8)';
        ctx.font = 'bold 32px Arial';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(`${distance}m`, 64, 32);

        const texture = new THREE.CanvasTexture(canvas);
        const spriteMaterial = new THREE.SpriteMaterial({ map: texture, transparent: true });
        const sprite = new THREE.Sprite(spriteMaterial);
        sprite.position.set(distance + 1, 0.5, 0);
        sprite.scale.set(2, 1, 1);
        group.add(sprite);
      }
    });

    return group;
  };

  /**
   * 优化的反射率着色算法
   * 参考上位机效果：低反射率深蓝色，高反射率红/黄色
   */
  const getReflectivityColor = (normalizedR: number): [number, number, number] => {
    // 使用更丰富的颜色渐变：浅蓝/蓝 -> 绿 -> 黄 -> 橙 -> 红
    // 0.0 -> 1.0 (低 -> 高)
    if (normalizedR < 0.25) {
      // 蓝色区域 (0.0 - 0.25)
      const t = normalizedR / 0.25;
      return [0.0, 0.5 * t, 1.0];
    } else if (normalizedR < 0.5) {
      // 蓝色到绿色 (0.25 - 0.5)
      const t = (normalizedR - 0.25) / 0.25;
      return [0.0, 0.5 + 0.5 * t, 1.0 - t];
    } else if (normalizedR < 0.75) {
      // 绿色到黄色 (0.5 - 0.75)
      const t = (normalizedR - 0.5) / 0.25;
      return [t, 1.0, 0.0];
    } else {
      // 黄色到红色 (0.75 - 1.0)
      const t = (normalizedR - 0.75) / 0.25;
      return [1.0, 1.0 - t, 0.0];
    }
  };

  /**
   * 优化的高度着色算法
   */
  const getHeightColor = (normalizedZ: number): [number, number, number] => {
    // 彩虹色渐变：蓝 -> 青 -> 绿 -> 黄 -> 红
    const hue = (1 - normalizedZ) * 0.7; // 0.7 = 从红到蓝的色相范围
    const rgb = hslToRgb(hue, 1.0, 0.5);
    return rgb;
  };

  /**
   * HSL到RGB转换
   */
  const hslToRgb = (h: number, s: number, l: number): [number, number, number] => {
    let r, g, b;

    if (s === 0) {
      r = g = b = l;
    } else {
      const hue2rgb = (p: number, q: number, t: number) => {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6) return p + (q - p) * 6 * t;
        if (t < 1 / 2) return q;
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
        return p;
      };

      const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
      const p = 2 * l - q;
      r = hue2rgb(p, q, h + 1 / 3);
      g = hue2rgb(p, q, h);
      b = hue2rgb(p, q, h - 1 / 3);
    }

    return [r, g, b];
  };

  // 更新点云数据
  useEffect(() => {
    if (!sceneRef.current || points.length === 0) {
      // 清空点云
      if (pointsRef.current) {
        sceneRef.current?.remove(pointsRef.current);
        pointsRef.current.geometry.dispose();
        (pointsRef.current.material as THREE.Material).dispose();
        pointsRef.current = null;
      }
      // 清空建模连线
      if (modelingRef.current) {
        sceneRef.current?.remove(modelingRef.current);
        modelingRef.current.geometry.dispose();
        (modelingRef.current.material as THREE.Material).dispose();
        modelingRef.current = null;
      }
      return;
    }

    const scene = sceneRef.current;

    // 1. 移除旧的对象
    if (pointsRef.current) {
      scene.remove(pointsRef.current);
      pointsRef.current.geometry.dispose();
      (pointsRef.current.material as THREE.Material).dispose();
    }
    if (modelingRef.current) {
      scene.remove(modelingRef.current);
      modelingRef.current.geometry.dispose();
      (modelingRef.current.material as THREE.Material).dispose();
    }
    if (backgroundPointsRef.current) {
      scene.remove(backgroundPointsRef.current);
      backgroundPointsRef.current.geometry.dispose();
      (backgroundPointsRef.current.material as THREE.Material).dispose();
    }

    // --- 防御模式逻辑：背景参考网格 ---
    const bgSearchGrid: Record<string, number> = {};
    const bgCellSize = 0.2; // 显著减小步长以提高侵入判定的空间分辨率
    if (defenseBackgroundPoints.length > 0) {
      defenseBackgroundPoints.forEach(p => {
        // 映射逻辑保持一致：Z 轴为高度
        const tx = p.x;
        const ty = p.z; // 三维空间高度
        const tz = p.y;

        const gx = Math.floor(tx / bgCellSize);
        const gy = Math.floor(ty / bgCellSize);
        const gz = Math.floor(tz / bgCellSize);
        const key = `${gx}_${gy}_${gz}`;
        const distSq = tx * tx + ty * ty + tz * tz;
        if (!bgSearchGrid[key] || distSq > bgSearchGrid[key]) {
          bgSearchGrid[key] = distSq;
        }
      });

      // 渲染背景点（淡色）作为底图
      const bgGeom = new THREE.BufferGeometry();
      const bgPos = new Float32Array(defenseBackgroundPoints.length * 3);
      defenseBackgroundPoints.forEach((p, i) => {
        bgPos[i * 3] = p.x;     // Three.js X
        bgPos[i * 3 + 1] = p.z; // Three.js Y (Height, 3.5m)
        bgPos[i * 3 + 2] = p.y; // Three.js Z
      });
      bgGeom.setAttribute('position', new THREE.BufferAttribute(bgPos, 3));
      const bgMat = new THREE.PointsMaterial({ size: 0.005, color: 0x333333, transparent: true, opacity: 0.3 });
      const bgPoints = new THREE.Points(bgGeom, bgMat);
      backgroundPointsRef.current = bgPoints;
      scene.add(bgPoints);
    }

    // 2. 统计信息计算
    let minX = Infinity, maxX = -Infinity;
    let minY = Infinity, maxY = -Infinity;
    let minZ = Infinity, maxZ = -Infinity;
    let minDist = Infinity, maxDist = -Infinity;
    let minR = Infinity, maxR = -Infinity;
    // 预计算统计信息和坐标轴映射 (通过维度数值判定：3.5m 的 Z 轴应为实际物理高度)
    for (let i = 0; i < points.length; i++) {
      const p = points[i];
      // 映射逻辑：Radar.x -> T3.x, Radar.y -> T3.z, Radar.z -> T3.y (高度)
      const lat = p.x;   // 水平 X
      const dep = p.y;   // 水平 Y (映射到深度 Z)
      const h = p.z;     // 垂直高度 (3.5m)

      minX = Math.min(minX, lat);
      maxX = Math.max(maxX, lat);
      minY = Math.min(minY, h);
      maxY = Math.max(maxY, h);
      minZ = Math.min(minZ, dep);
      maxZ = Math.max(maxZ, dep);

      const dist = Math.sqrt(h * h + lat * lat + dep * dep);
      minDist = Math.min(minDist, dist);
      maxDist = Math.max(maxDist, dist);
      if (p.r !== undefined) {
        minR = Math.min(minR, p.r);
        maxR = Math.max(maxR, p.r);
      }
    }

    const hRange = maxY - minY || 1; // 实际高度范围 (Radar Z)
    const distRange = maxDist - minDist || 1;
    const rRange = maxR - minR || 1;

    // 3. 创建实时点云
    const geometry = new THREE.BufferGeometry();
    const positions = new Float32Array(points.length * 3);
    const colors = new Float32Array(points.length * 3);
    const isIntruderArr = new Uint8Array(points.length); // 记录是否为侵入点

    for (let i = 0; i < points.length; i++) {
      const point = points[i];
      // 恢复正常坐标系映射：以 Z 为高度 (3.5m)，X/Y 为平铺坐标
      const tx = point.x;
      const ty = point.z; // 三维空间的高度 (Up)
      const tz = point.y;

      positions[i * 3] = tx;
      positions[i * 3 + 1] = ty;
      positions[i * 3 + 2] = tz;

      // 侵入检测判定 (优先使用后端返回的zoneId)
      let isIntruder = false;
      if (point.zoneId) {
        isIntruder = true;
      }
      // 只有非背景点才需要检测 (前端回退检测逻辑)
      else if (defenseBackgroundPoints.length > 0 && !point.isStaticBackground) {
        const gx = Math.floor(tx / bgCellSize);
        const gy = Math.floor(ty / bgCellSize);
        const gz = Math.floor(tz / bgCellSize);
        const key = `${gx}_${gy}_${gz}`;
        const pDistSq = tx * tx + ty * ty + tz * tz;

        const bgDistSq = bgSearchGrid[key];
        if (bgDistSq) {
          const bgDist = Math.sqrt(bgDistSq);
          const pDist = Math.sqrt(pDistSq);
          if (pDist < bgDist - shrinkDistance) {
            isIntruder = true;
          }
        }
      }
      isIntruderArr[i] = isIntruder ? 1 : 0;

      let r = 1, g = 1, b = 1;

      // 优先级 1: 检查自定义颜色函数
      let customColorStr = '';
      if (typeof color === 'function') {
        customColorStr = color(point);
      }

      if (customColorStr) {
        const c = new THREE.Color(customColorStr);
        r = c.r; g = c.g; b = c.b;
      }
      // 优先级 2: 模式着色
      else if (colorMode === 'defense') {
        if (isIntruder) {
          r = 1; g = 0; b = 0;
        } else {
          r = 0.8; g = 0.8; b = 0.8;
        }
      } else if (colorMode === 'height') {
        // 使用 Radar.z (当前 Three.js Y 轴) 进行高度着色
        const normalizedH = (ty - minY) / hRange;
        [r, g, b] = getHeightColor(normalizedH);
      } else if (colorMode === 'distance') {
        const dist = Math.sqrt(tx * tx + ty * ty + tz * tz);
        const normalizedDist = (dist - minDist) / distRange;
        [r, g, b] = getHeightColor(normalizedDist);
      } else if (colorMode === 'reflectivity' && point.r !== undefined) {
        const normalizedR = (point.r - minR) / rRange;
        [r, g, b] = getReflectivityColor(normalizedR);
      } else {
        const colorObj = new THREE.Color(typeof color === 'string' ? color : '#ffffff');
        r = colorObj.r; g = colorObj.g; b = colorObj.b;
      }

      colors[i * 3] = r;
      colors[i * 3 + 1] = g;
      colors[i * 3 + 2] = b;
    }

    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    const material = new THREE.PointsMaterial({
      size: (colorMode === 'defense') ? pointSize * 1.5 : pointSize, // 防区模式下点大一点
      vertexColors: true,
      sizeAttenuation: true
    });

    const pointCloud = new THREE.Points(geometry, material);
    pointsRef.current = pointCloud;
    scene.add(pointCloud);

    // 4. 实现具象建模 (Concrete Modeling)
    if (showModeling && points.length > 0) {
      const linePositions: number[] = [];
      const lineColors: number[] = [];

      // 使用空间索引加速
      const grid: Record<string, number[]> = {};
      const res = modelingDistance;
      points.forEach((p, idx) => {
        // 在防区模式下，只对侵入目标（红色点）进行建模
        if (colorMode === 'defense' && !isIntruderArr[idx]) return;

        // 统一映射逻辑：Radar.z -> Three.js Y (Height)
        const tx = p.x;
        const ty = p.z;
        const tz = p.y;

        const gx = Math.floor(tx / res);
        const gy = Math.floor(ty / res);
        const gz = Math.floor(tz / res);
        const key = `${gx}_${gy}_${gz}`;
        if (!grid[key]) grid[key] = [];
        grid[key].push(idx);
      });

      const maxDistSq = modelingDistance * modelingDistance;

      points.forEach((p, i) => {
        // 只处理需要建模的点
        if (colorMode === 'defense' && !isIntruderArr[i]) return;

        const tx = p.x;
        const ty = p.z;
        const tz = p.y;

        const gx = Math.floor(tx / res);
        const gy = Math.floor(ty / res);
        const gz = Math.floor(tz / res);
        const neighbors: { idx: number, distSq: number }[] = [];

        for (let dx = -1; dx <= 1; dx++) {
          for (let dy = -1; dy <= 1; dy++) {
            for (let dz = -1; dz <= 1; dz++) {
              const key = `${gx + dx}_${gy + dy}_${gz + dz}`;
              const cellPoints = grid[key];
              if (cellPoints) {
                cellPoints.forEach(j => {
                  if (i === j) return;
                  const dp = points[j];
                  // 邻居也需要映射
                  const dtx = dp.x;
                  const dty = dp.z;
                  const dtz = dp.y;

                  const distSq = (tx - dtx) ** 2 + (ty - dty) ** 2 + (tz - dtz) ** 2;
                  if (distSq < maxDistSq) {
                    neighbors.push({ idx: j, distSq });
                  }
                });
              }
            }
          }
        }

        // 挑选最近的邻居连线，形成“表面感”
        neighbors.sort((a, b) => a.distSq - b.distSq);
        neighbors.slice(0, modelingMaxConnections).forEach(n => {
          if (i < n.idx) { // 避免双向连线重复添加
            const p2 = points[n.idx];
            // 写入映射后的坐标到连线缓存
            linePositions.push(tx, ty, tz, p2.x, p2.z, p2.y);
            const r1 = colors[i * 3], g1 = colors[i * 3 + 1], b1 = colors[i * 3 + 2];
            const r2 = colors[n.idx * 3], g2 = colors[n.idx * 3 + 1], b2 = colors[n.idx * 3 + 2];
            lineColors.push(r1, g1, b1, r2, g2, b2);
          }
        });
      });

      if (linePositions.length > 0) {
        const lineGeom = new THREE.BufferGeometry();
        lineGeom.setAttribute('position', new THREE.Float32BufferAttribute(linePositions, 3));
        lineGeom.setAttribute('color', new THREE.Float32BufferAttribute(lineColors, 3));
        const lineMat = new THREE.LineBasicMaterial({
          vertexColors: true,
          transparent: true,
          opacity: colorMode === 'defense' ? 0.8 : 0.4,
          linewidth: 2
        });
        const segments = new THREE.LineSegments(lineGeom, lineMat);
        modelingRef.current = segments;
        scene.add(segments);
      }
    }

    // 5. 调整相机
    if (!cameraInitializedRef.current && cameraRef.current) {
      geometry.computeBoundingBox();
      const box = geometry.boundingBox;
      if (box) {
        const center = new THREE.Vector3();
        box.getCenter(center);
        const size = new THREE.Vector3();
        box.getSize(size);
        const maxDim = Math.max(size.x, size.y, size.z);
        const distance = maxDim > 0 ? Math.max(maxDim * 1.5, 8) : 8;
        cameraRef.current.position.set(center.x + distance, center.y + distance, center.z + distance);
        cameraRef.current.lookAt(center);
        if (controlsRef.current) {
          controlsRef.current.target.copy(center);
          controlsRef.current.update();
        }
        cameraInitializedRef.current = true;
      }
    }
  }, [points, color, pointSize, colorMode, showModeling, modelingDistance, defenseBackgroundPoints, shrinkDistance]);

  // 计算统计信息
  const statsInfo = useMemo(() => {
    if (points.length === 0) return null;
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, minZ = Infinity, maxZ = -Infinity;
    for (const point of points) {
      minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
      minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
      minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
    }
    const rangeX = maxX - minX, rangeY = maxY - minY, rangeZ = maxZ - minZ;
    const maxDim = Math.max(rangeX, rangeY, rangeZ);
    const unit = maxDim < 1 ? 'cm' : 'm';
    const scale = maxDim < 1 ? 100 : 1;
    return {
      pointCount: points.length,
      rangeX: (rangeX * scale).toFixed(1),
      rangeY: (rangeY * scale).toFixed(1),
      rangeZ: (rangeZ * scale).toFixed(1),
      unit
    };
  }, [points]);

  return (
    <div ref={containerRef} className="w-full h-full" style={{ position: 'relative' }}>
      {/* 坐标范围信息 */}
      {statsInfo && (
        <div style={{
          position: 'absolute', bottom: '12px', left: '12px',
          backgroundColor: 'rgba(0, 0, 0, 0.8)', color: '#fff',
          padding: '8px 12px', borderRadius: '8px', fontSize: '11px',
          fontFamily: 'monospace', pointerEvents: 'none', zIndex: 10,
          border: '1px solid rgba(255,255,255,0.1)', backdropFilter: 'blur(4px)'
        }}>
          <div style={{ marginBottom: '4px', display: 'flex', gap: '10px' }}>
            <span><span style={{ color: '#4a90d9' }}>●</span> X: {statsInfo.rangeX}{statsInfo.unit}</span>
            <span><span style={{ color: '#50c878' }}>●</span> Y: {statsInfo.rangeY}{statsInfo.unit}</span>
            <span><span style={{ color: '#ff6b6b' }}>●</span> Z: {statsInfo.rangeZ}{statsInfo.unit}</span>
          </div>
          <div style={{ color: '#888' }}>
            点数: {statsInfo.pointCount.toLocaleString()}
          </div>
        </div>
      )}

      {/* 增强型反射率/高度图例 */}
      {colorMode !== 'fixed' && (
        <div style={{
          position: 'absolute', top: '12px', right: '12px',
          backgroundColor: 'rgba(0, 0, 0, 0.8)', color: '#fff',
          padding: '12px', borderRadius: '12px', fontSize: '11px',
          pointerEvents: 'none', zIndex: 10, minWidth: '60px',
          border: '1px solid rgba(255,255,255,0.1)', backdropFilter: 'blur(4px)'
        }}>
          <div style={{ marginBottom: '8px', fontWeight: 'bold', textAlign: 'center' }}>
            {colorMode === 'height' ? '高度' : colorMode === 'distance' ? '距离' : '反射率'}
          </div>
          <div style={{
            display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px'
          }}>
            <span style={{ color: '#ff4d4d' }}>高</span>
            <div style={{
              width: '14px', height: '100px',
              background: 'linear-gradient(to bottom, #ff0000, #ffa500, #ffff00, #00ff00, #0080ff)',
              borderRadius: '10px', boxShadow: 'inset 0 0 5px rgba(0,0,0,0.5)'
            }} />
            <span style={{ color: '#4da6ff' }}>低</span>
          </div>
        </div>
      )}
    </div>
  );
};
