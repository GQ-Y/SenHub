import React, { useEffect, useRef } from 'react';
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
  showAxes = true
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const controlsRef = useRef<any>(null);
  const pointsRef = useRef<THREE.Points | null>(null);
  const animationFrameRef = useRef<number | null>(null);

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

    // 创建相机
    const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000);
    camera.position.set(5, 5, 5);
    camera.lookAt(0, 0, 0);
    cameraRef.current = camera;

    // 创建渲染器
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setSize(width, height);
    renderer.setPixelRatio(window.devicePixelRatio);
    container.appendChild(renderer.domElement);
    rendererRef.current = renderer;

    // 添加网格
    if (showGrid) {
      const gridHelper = new THREE.GridHelper(10, 10, 0x444444, 0x222222);
      scene.add(gridHelper);
    }

    // 添加坐标轴
    if (showAxes) {
      const axesHelper = new THREE.AxesHelper(2);
      scene.add(axesHelper);
    }

    // 添加光源
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    scene.add(ambientLight);
    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.4);
    directionalLight.position.set(5, 5, 5);
    scene.add(directionalLight);

    // 动态导入OrbitControls（如果可用）
    let OrbitControls: any = null;
    import('three/examples/jsm/controls/OrbitControls').then((module) => {
      OrbitControls = module.OrbitControls;
      if (OrbitControls && renderer) {
        const controls = new OrbitControls(camera, renderer.domElement);
        controls.enableDamping = true;
        controls.dampingFactor = 0.05;
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
    };
  }, [backgroundColor, showGrid, showAxes]);

  // 更新点云数据
  useEffect(() => {
    if (!sceneRef.current || !pointsRef.current || points.length === 0) {
      return;
    }

    const scene = sceneRef.current;
    const oldPoints = pointsRef.current;

    // 移除旧的点云
    scene.remove(oldPoints);
    if (oldPoints.geometry) {
      oldPoints.geometry.dispose();
    }
    if (oldPoints.material) {
      (oldPoints.material as THREE.Material).dispose();
    }

    // 创建新的点云几何体
    const geometry = new THREE.BufferGeometry();
    const positions = new Float32Array(points.length * 3);
    const colors = new Float32Array(points.length * 3);

    for (let i = 0; i < points.length; i++) {
      const point = points[i];
      positions[i * 3] = point.x;
      positions[i * 3 + 1] = point.y;
      positions[i * 3 + 2] = point.z;

      // 处理颜色
      let r = 1, g = 1, b = 1;
      if (typeof color === 'function') {
        const colorStr = color(point);
        const colorObj = new THREE.Color(colorStr);
        r = colorObj.r;
        g = colorObj.g;
        b = colorObj.b;
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

    // 创建点云材质
    const material = new THREE.PointsMaterial({
      size: pointSize,
      vertexColors: true,
      transparent: true,
      opacity: 0.8
    });

    // 创建点云对象
    const pointCloud = new THREE.Points(geometry, material);
    pointsRef.current = pointCloud;
    scene.add(pointCloud);

    // 自动调整相机位置以查看所有点
    geometry.computeBoundingBox();
    const box = geometry.boundingBox;
    if (box) {
      const center = new THREE.Vector3();
      box.getCenter(center);
      const size = new THREE.Vector3();
      box.getSize(size);
      const maxDim = Math.max(size.x, size.y, size.z);
      const distance = maxDim * 2;

      if (cameraRef.current) {
        cameraRef.current.position.set(
          center.x + distance * 0.7,
          center.y + distance * 0.7,
          center.z + distance * 0.7
        );
        cameraRef.current.lookAt(center);
        cameraRef.current.updateProjectionMatrix();
      }
    }
  }, [points, color, pointSize]);

  return (
    <div ref={containerRef} className="w-full h-full" style={{ position: 'relative' }} />
  );
};
