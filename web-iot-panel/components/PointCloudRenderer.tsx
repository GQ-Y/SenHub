import React, { useEffect, useRef, useMemo } from 'react';
import * as THREE from 'three';

interface Point {
  x: number;
  y: number;
  z: number;
  r?: number;
}

interface PointCloudRendererProps {
  points: Point[];
  color?: string | ((point: Point) => string);
  pointSize?: number;
  backgroundColor?: string;
  showGrid?: boolean;
  showAxes?: boolean;
  showRangeRings?: boolean; // 新增：显示范围环
  colorMode?: 'height' | 'distance' | 'reflectivity' | 'intensity' | 'fixed';
}

/**
 * 点云渲染组件（Three.js）
 * 增强功能：
 * 1. 动态坐标轴单位（厘米/米）
 * 2. 范围环（Range Rings）显示
 * 3. 优化反射率着色
 * 4. 性能优化支持大量点云
 */
export const PointCloudRenderer: React.FC<PointCloudRendererProps> = ({
  points,
  color = '#ffffff',
  pointSize = 0.01,
  backgroundColor = '#000000',
  showGrid = true,
  showAxes = true,
  showRangeRings = true, // 默认显示范围环
  colorMode = 'height'
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const controlsRef = useRef<any>(null);
  const pointsRef = useRef<THREE.Points | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const cameraInitializedRef = useRef<boolean>(false);

  // 范围环和辅助对象的引用
  const rangeRingsGroupRef = useRef<THREE.Group | null>(null);
  const axesLabelsRef = useRef<HTMLDivElement[]>([]);
  const gridHelperRef = useRef<THREE.GridHelper | null>(null);
  const axesHelperRef = useRef<THREE.AxesHelper | null>(null);

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
    // 使用更丰富的颜色渐变：深蓝 -> 青色 -> 绿色 -> 黄色 -> 红色
    if (normalizedR < 0.2) {
      // 深蓝色区域
      return [0.1, 0.1, 0.6 + normalizedR * 2];
    } else if (normalizedR < 0.4) {
      // 蓝色到青色
      const t = (normalizedR - 0.2) / 0.2;
      return [0.1, 0.3 + t * 0.5, 0.8 - t * 0.2];
    } else if (normalizedR < 0.6) {
      // 青色到绿色
      const t = (normalizedR - 0.4) / 0.2;
      return [0.1 + t * 0.2, 0.8 - t * 0.2, 0.6 - t * 0.4];
    } else if (normalizedR < 0.8) {
      // 绿色到黄色
      const t = (normalizedR - 0.6) / 0.2;
      return [0.3 + t * 0.7, 0.6 + t * 0.3, 0.2 - t * 0.1];
    } else {
      // 黄色到红色
      const t = (normalizedR - 0.8) / 0.2;
      return [1.0, 0.9 - t * 0.7, 0.1 - t * 0.1];
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
      if (pointsRef.current) {
        sceneRef.current?.remove(pointsRef.current);
        if (pointsRef.current.geometry) {
          pointsRef.current.geometry.dispose();
        }
        if (pointsRef.current.material) {
          (pointsRef.current.material as THREE.Material).dispose();
        }
        pointsRef.current = null;
      }
      return;
    }

    const scene = sceneRef.current;
    const oldPoints = pointsRef.current;

    // 移除旧的点云
    if (oldPoints) {
      scene.remove(oldPoints);
      if (oldPoints.geometry) {
        oldPoints.geometry.dispose();
      }
      if (oldPoints.material) {
        (oldPoints.material as THREE.Material).dispose();
      }
    }

    // 创建新的点云几何体
    const geometry = new THREE.BufferGeometry();
    const positions = new Float32Array(points.length * 3);
    const colors = new Float32Array(points.length * 3);

    // 计算点云的统计信息用于颜色映射
    let minZ = Infinity, maxZ = -Infinity;
    let minDist = Infinity, maxDist = -Infinity;
    let minR = Infinity, maxR = -Infinity;
    let minX = Infinity, maxX = -Infinity;
    let minY = Infinity, maxY = -Infinity;

    for (const point of points) {
      const dist = Math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z);
      minZ = Math.min(minZ, point.z);
      maxZ = Math.max(maxZ, point.z);
      minX = Math.min(minX, point.x);
      maxX = Math.max(maxX, point.x);
      minY = Math.min(minY, point.y);
      maxY = Math.max(maxY, point.y);
      minDist = Math.min(minDist, dist);
      maxDist = Math.max(maxDist, dist);
      if (point.r !== undefined) {
        minR = Math.min(minR, point.r);
        maxR = Math.max(maxR, point.r);
      }
    }

    const zRange = maxZ - minZ || 1;
    const distRange = maxDist - minDist || 1;
    const rRange = maxR - minR || 1;

    for (let i = 0; i < points.length; i++) {
      const point = points[i];
      positions[i * 3] = point.x;
      positions[i * 3 + 1] = point.y;
      positions[i * 3 + 2] = point.z;

      let r = 1, g = 1, b = 1;

      // 优先使用传入的color函数
      if (typeof color === 'function') {
        const colorStr = color(point);
        if (colorStr && colorStr.trim() !== '') {
          const colorObj = new THREE.Color(colorStr);
          r = colorObj.r;
          g = colorObj.g;
          b = colorObj.b;
        } else {
          // color函数返回空，使用colorMode
          if (colorMode === 'height') {
            const normalizedZ = (point.z - minZ) / zRange;
            [r, g, b] = getHeightColor(normalizedZ);
          } else if (colorMode === 'distance') {
            const dist = Math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z);
            const normalizedDist = (dist - minDist) / distRange;
            [r, g, b] = getHeightColor(normalizedDist);
          } else if (colorMode === 'reflectivity' && point.r !== undefined) {
            const normalizedR = (point.r - minR) / rRange;
            [r, g, b] = getReflectivityColor(normalizedR);
          } else {
            const colorObj = new THREE.Color(color);
            r = colorObj.r;
            g = colorObj.g;
            b = colorObj.b;
          }
        }
      } else if (colorMode === 'height') {
        const normalizedZ = (point.z - minZ) / zRange;
        [r, g, b] = getHeightColor(normalizedZ);
      } else if (colorMode === 'distance') {
        const dist = Math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z);
        const normalizedDist = (dist - minDist) / distRange;
        [r, g, b] = getHeightColor(normalizedDist);
      } else if (colorMode === 'reflectivity' && point.r !== undefined) {
        const normalizedR = (point.r - minR) / rRange;
        [r, g, b] = getReflectivityColor(normalizedR);
      } else {
        const colorObj = new THREE.Color(color);
        r = colorObj.r;
        g = colorObj.g;
        b = colorObj.b;
      }

      colors[i * 3] = r;
      colors[i * 3 + 1] = g;
      colors[i * 3 + 2] = b;
    }

    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));

    // 根据点云数量动态调整点大小
    const adaptivePointSize = points.length > 100000
      ? pointSize * 0.5
      : points.length > 50000
        ? pointSize * 0.75
        : pointSize;

    // 创建点云材质
    const material = new THREE.PointsMaterial({
      size: adaptivePointSize,
      vertexColors: true,
      transparent: false,
      opacity: 1.0,
      sizeAttenuation: true
    });

    // 创建点云对象
    const pointCloud = new THREE.Points(geometry, material);
    pointsRef.current = pointCloud;
    scene.add(pointCloud);

    // 动态更新范围环（根据点云范围调整）
    if (rangeRingsGroupRef.current && sceneRef.current) {
      // 计算点云的最大距离
      const maxDimXY = Math.max(maxX - minX, maxY - minY);
      const maxRange = Math.max(maxDist, maxDimXY / 2);

      // 根据点云范围调整范围环的可见性
      rangeRingsGroupRef.current.children.forEach((child, index) => {
        if (child instanceof THREE.Mesh) {
          // 只显示在点云范围内的环
          child.visible = index * 5 <= maxRange * 1.5;
        }
      });
    }

    // 只在首次加载时自动调整相机位置
    if (!cameraInitializedRef.current && cameraRef.current) {
      geometry.computeBoundingBox();
      const box = geometry.boundingBox;
      if (box) {
        const center = new THREE.Vector3();
        box.getCenter(center);
        const size = new THREE.Vector3();
        box.getSize(size);
        const maxDim = Math.max(size.x, size.y, size.z);

        const distance = maxDim > 0 ? Math.max(maxDim * 2.5, 10) : 10;

        if (maxDim > 0) {
          cameraRef.current.position.set(
            center.x + distance * 0.7,
            center.y + distance * 0.7,
            center.z + distance * 0.7
          );
          cameraRef.current.lookAt(center);
          cameraRef.current.updateProjectionMatrix();

          if (controlsRef.current) {
            controlsRef.current.target.copy(center);
            controlsRef.current.update();
          }

          cameraInitializedRef.current = true;
        }
      }
    }
  }, [points, color, pointSize, colorMode]);

  // 计算显示统计信息
  const statsInfo = useMemo(() => {
    if (points.length === 0) return null;

    let minX = Infinity, maxX = -Infinity;
    let minY = Infinity, maxY = -Infinity;
    let minZ = Infinity, maxZ = -Infinity;

    for (const point of points) {
      minX = Math.min(minX, point.x);
      maxX = Math.max(maxX, point.x);
      minY = Math.min(minY, point.y);
      maxY = Math.max(maxY, point.y);
      minZ = Math.min(minZ, point.z);
      maxZ = Math.max(maxZ, point.z);
    }

    const rangeX = maxX - minX;
    const rangeY = maxY - minY;
    const rangeZ = maxZ - minZ;
    const maxDim = Math.max(rangeX, rangeY, rangeZ);

    // 根据点云范围决定单位
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
      {/* 动态坐标轴单位显示 */}
      {statsInfo && (
        <div
          style={{
            position: 'absolute',
            bottom: '8px',
            left: '8px',
            backgroundColor: 'rgba(0, 0, 0, 0.7)',
            color: '#fff',
            padding: '6px 10px',
            borderRadius: '4px',
            fontSize: '11px',
            fontFamily: 'monospace',
            pointerEvents: 'none',
            zIndex: 10
          }}
        >
          <div style={{ marginBottom: '2px' }}>
            <span style={{ color: '#4a90d9' }}>■</span> X: {statsInfo.rangeX}{statsInfo.unit}
            <span style={{ marginLeft: '8px', color: '#50c878' }}>■</span> Y: {statsInfo.rangeY}{statsInfo.unit}
            <span style={{ marginLeft: '8px', color: '#ff6b6b' }}>■</span> Z: {statsInfo.rangeZ}{statsInfo.unit}
          </div>
          <div style={{ color: '#aaa' }}>
            点数: {statsInfo.pointCount.toLocaleString()}
          </div>
        </div>
      )}

      {/* 图例 */}
      {colorMode !== 'fixed' && (
        <div
          style={{
            position: 'absolute',
            top: '8px',
            right: '8px',
            backgroundColor: 'rgba(0, 0, 0, 0.7)',
            color: '#fff',
            padding: '6px 10px',
            borderRadius: '4px',
            fontSize: '10px',
            pointerEvents: 'none',
            zIndex: 10
          }}
        >
          <div style={{ marginBottom: '4px', fontWeight: 'bold' }}>
            {colorMode === 'height' ? '高度' : colorMode === 'distance' ? '距离' : '反射率'}
          </div>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            height: '80px',
            flexDirection: 'column',
            justifyContent: 'space-between'
          }}>
            <span>高</span>
            <div style={{
              width: '12px',
              height: '60px',
              background: 'linear-gradient(to bottom, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff)',
              borderRadius: '2px'
            }} />
            <span>低</span>
          </div>
        </div>
      )}
    </div>
  );
};
