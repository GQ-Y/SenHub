import React, { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls';

interface PtzView3DProps {
  pan: number;           // 水平角度 (0-360°)
  tilt: number;          // 垂直角度 (-90°到90°)
  zoom: number;          // 变倍 (1.0-40.0)
  azimuth: number;       // 方位角 (0-360°)
  horizontalFov: number; // 水平视场角 (度)
  verticalFov: number;   // 垂直视场角 (度)
  visibleRadius: number; // 可视半径 (米)
  position?: {           // GIS位置信息
    latitude: number;
    longitude: number;
  };
}

/**
 * PTZ视角3D仿真组件
 * 使用Three.js渲染球机的3D视角效果
 * 包含：地面网格、球机模型、视锥体仿真、第三人称视角控制
 */
export const PtzView3D: React.FC<PtzView3DProps> = ({
  pan,
  tilt,
  zoom,
  azimuth,
  horizontalFov,
  verticalFov,
  visibleRadius,
  position
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const sceneRef = useRef<THREE.Scene | null>(null);
  const rendererRef = useRef<THREE.WebGLRenderer | null>(null);
  const cameraRef = useRef<THREE.PerspectiveCamera | null>(null);
  const controlsRef = useRef<OrbitControls | null>(null);
  const cameraHeadRef = useRef<THREE.Group | null>(null);
  const frustumRef = useRef<THREE.Mesh | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // 初始化场景
  useEffect(() => {
    if (!containerRef.current) return;

    // 1. 创建场景
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0xf3f4f6); // 浅灰色背景
    scene.fog = new THREE.Fog(0xf3f4f6, 20, 100);
    sceneRef.current = scene;

    // 2. 创建观察相机 (God View)
    const camera = new THREE.PerspectiveCamera(
      45,
      containerRef.current.clientWidth / containerRef.current.clientHeight,
      0.1,
      1000
    );
    // 设置初始视角位置 (右上角俯视)
    camera.position.set(15, 15, 15);
    camera.lookAt(0, 0, 0);
    cameraRef.current = camera;

    // 3. 渲染器
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    renderer.setSize(containerRef.current.clientWidth, containerRef.current.clientHeight);
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.shadowMap.enabled = true;
    containerRef.current.appendChild(renderer.domElement);
    rendererRef.current = renderer;

    // 4. 控制器
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.maxPolarAngle = Math.PI / 2 - 0.1; // 防止穿透地面
    controlsRef.current = controls;

    // 5. 灯光
    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    dirLight.position.set(10, 20, 10);
    dirLight.castShadow = true;
    dirLight.shadow.mapSize.width = 2048;
    dirLight.shadow.mapSize.height = 2048;
    scene.add(dirLight);

    // 6. 地面 & 网格
    const gridHelper = new THREE.GridHelper(100, 100, 0xcccccc, 0xe5e7eb);
    scene.add(gridHelper);

    const planeGeometry = new THREE.PlaneGeometry(100, 100);
    const planeMaterial = new THREE.MeshLambertMaterial({ color: 0xffffff });
    const plane = new THREE.Mesh(planeGeometry, planeMaterial);
    plane.rotation.x = -Math.PI / 2;
    plane.receiveShadow = true;
    scene.add(plane);

    // 7. 坐标轴辅助 (红X, 绿Y, 蓝Z)
    const axesHelper = new THREE.AxesHelper(5);
    scene.add(axesHelper);

    // 8. 创建球机模型
    const cameraGroup = new THREE.Group();
    
    // 立杆
    const poleGeo = new THREE.CylinderGeometry(0.1, 0.1, 2, 16);
    const poleMat = new THREE.MeshStandardMaterial({ color: 0x333333 });
    const pole = new THREE.Mesh(poleGeo, poleMat);
    pole.position.y = 1;
    pole.castShadow = true;
    cameraGroup.add(pole);

    // 云台头部 (可旋转部分)
    const headGroup = new THREE.Group();
    headGroup.position.y = 2; // 立杆顶部
    cameraGroup.add(headGroup);
    cameraHeadRef.current = headGroup;

    // 球体外壳
    const sphereGeo = new THREE.SphereGeometry(0.3, 32, 32);
    const sphereMat = new THREE.MeshStandardMaterial({ 
      color: 0xffffff,
      roughness: 0.2,
      metalness: 0.5
    });
    const sphere = new THREE.Mesh(sphereGeo, sphereMat);
    sphere.castShadow = true;
    headGroup.add(sphere);

    // 镜头 (指示方向)
    const lensGeo = new THREE.CylinderGeometry(0.1, 0.1, 0.4, 16);
    const lensMat = new THREE.MeshStandardMaterial({ color: 0x111111 });
    const lens = new THREE.Mesh(lensGeo, lensMat);
    lens.rotation.x = Math.PI / 2;
    lens.position.z = 0.2; // 向前突出
    headGroup.add(lens);

    scene.add(cameraGroup);

    // 9. 视锥体 (Frustum)
    // 初始创建一个空的Mesh，稍后在update中更新几何体
    const frustumMat = new THREE.MeshBasicMaterial({
      color: 0x3b82f6, // 蓝色
      transparent: true,
      opacity: 0.2,
      side: THREE.DoubleSide,
      depthWrite: false
    });
    
    const frustumMesh = new THREE.Mesh(new THREE.BufferGeometry(), frustumMat);
    // 将视锥体添加到头部，这样它会跟随头部旋转
    headGroup.add(frustumMesh);
    frustumRef.current = frustumMesh;

    // 动画循环
    const animate = () => {
      animationFrameRef.current = requestAnimationFrame(animate);
      if (controlsRef.current) controlsRef.current.update();
      if (rendererRef.current && sceneRef.current && cameraRef.current) {
        rendererRef.current.render(sceneRef.current, cameraRef.current);
      }
    };
    animate();
    setIsLoading(false);

    // Resize handler
    const handleResize = () => {
      if (!containerRef.current || !cameraRef.current || !rendererRef.current) return;
      const width = containerRef.current.clientWidth;
      const height = containerRef.current.clientHeight;
      cameraRef.current.aspect = width / height;
      cameraRef.current.updateProjectionMatrix();
      rendererRef.current.setSize(width, height);
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current);
      if (rendererRef.current) {
        rendererRef.current.dispose();
        const domElement = rendererRef.current.domElement;
        if (domElement && domElement.parentNode) {
          domElement.parentNode.removeChild(domElement);
        }
      }
    };
  }, []);

  // 更新视角和视锥体
  useEffect(() => {
    if (!cameraHeadRef.current || !frustumRef.current) return;

    // 1. 更新云台姿态
    // Pan: 0度为正北(-Z), 90度为正东(+X). 
    // ThreeJS中: 初始面向+Z. 
    // 让我们设定初始状态: 镜头指向+Z.
    // 实际上我们在模型构建时，镜头指向了+Z (lens.position.z = 0.2).
    // GIS定义: 0度正北. 90度正东.
    // ThreeJS: -Z是屏幕里(通常作为北). +X是右(东). +Z是屏幕外(南).
    // 如果镜头初始指向+Z(南).
    // Pan=0 (北) -> 需要旋转180度.
    // Pan=90 (东) -> 需要旋转-90度 (指向+X)? 不, +Z转到+X是+90度(逆时针)吗? 
    // 右手定则: Y轴向上, 拇指Y, 四指从+Z向+X弯曲 -> -90度.
    // 让我们简化:
    // 设定一个Group作为"NorthFrame", 它的-Z指向北.
    // 但这里我们直接操作Head.
    // 假设: 场景-Z是北. +X是东.
    // 镜头初始: 指向+Z (南).
    // 目标: Pan角度 (0=北, 90=东).
    // Pan 0 => 指向 -Z. (需要转 180度)
    // Pan 90 => 指向 +X. (需要转 +90度? 从+Z转到+X是-90度? 不, 逆时针是正. +Z(0,0,1) -> +X(1,0,0) 是 -90度旋转 around Y).
    // 公式: rotation.y = (pan - 180) * deg2rad ?
    // 0 -> -180 (指向-Z). Correct.
    // 90 -> -90 (指向+X). Correct.
    // 180 -> 0 (指向+Z). Correct.
    // 270 -> 90 (指向-X). Correct.
    // 注意: Pan通常是顺时针增加 (N->E->S->W). ThreeJS旋转是逆时针.
    // 所以: Pan 90 (东) 应该是 -90 (ThreeJS).
    // 初始+Z (南).
    // 目标 -Z (北, Pan 0). 旋转 180.
    // 目标 +X (东, Pan 90). 旋转 +90? (+Z -> +X is -90 around Y).
    // 让我们重新推导:
    // 0 (N) -> -Z
    // 90 (E) -> +X
    // 180 (S) -> +Z
    // 270 (W) -> -X
    // 初始模型指向 +Z.
    // rotationY = Math.PI - (pan * Math.PI / 180).
    // Pan=0 -> PI (180deg). +Z转180 -> -Z. OK.
    // Pan=90 -> PI - PI/2 = PI/2 (90deg). +Z转90(逆时针) -> +X. OK.
    // Pan=180 -> 0. +Z转0 -> +Z. OK.
    // Pan=270 -> PI - 1.5PI = -0.5PI (-90deg). +Z转-90 -> -X. OK.
    
    const panRad = (Math.PI) - (pan * Math.PI / 180);
    cameraHeadRef.current.rotation.y = panRad;

    // Tilt: 0度水平. 90度向上? 还是向下?
    // 通常球机: 0度水平, 90度垂直向下? 或者-90向上?
    // 假设: 0度水平, 正值向上(抬头), 负值向下(低头).
    // ThreeJS X轴旋转: +X是向下还是向上?
    // 初始指向+Z. 绕X轴旋转. +angle -> 向下(Y变负). -angle -> 向上.
    // 所以 rotation.x = -tilt * deg2rad.
    const tiltRad = -(tilt * Math.PI / 180);
    cameraHeadRef.current.rotation.x = tiltRad;

    // 2. 更新视锥体几何形状
    // 基于 horizontalFov, verticalFov, visibleRadius
    const R = visibleRadius || 20;
    const hFovRad = (horizontalFov || 60) * Math.PI / 180;
    const vFovRad = (verticalFov || 40) * Math.PI / 180;

    // 计算远裁剪面的宽高的一半
    // tan(theta/2) = (w/2) / R
    const halfW = R * Math.tan(hFovRad / 2);
    const halfH = R * Math.tan(vFovRad / 2);

    // 顶点: 0(原点), 1(左上), 2(右上), 3(右下), 4(左下)
    // 坐标系: 局部坐标系, 镜头指向+Z.
    const vertices = new Float32Array([
      0, 0, 0,           // 0: Tip
      halfW, halfH, R,   // 1: Top Right (looking from origin towards +Z) -> actually +X is Right, +Y is Up.
      -halfW, halfH, R,  // 2: Top Left
      -halfW, -halfH, R, // 3: Bottom Left
      halfW, -halfH, R   // 4: Bottom Right
    ]);

    // 索引: 构成三角形
    const indices = [
      0, 1, 2, // Top face
      0, 2, 3, // Left face
      0, 3, 4, // Bottom face
      0, 4, 1, // Right face
      2, 1, 4, 4, 3, 2 // Far face (quad -> 2 tris)
    ];

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(vertices, 3));
    geometry.setIndex(indices);
    geometry.computeVertexNormals();

    frustumRef.current.geometry.dispose();
    frustumRef.current.geometry = geometry;
    
    // 添加边缘线
    const edges = new THREE.EdgesGeometry(geometry);
    const line = new THREE.LineSegments(edges, new THREE.LineBasicMaterial({ color: 0x60a5fa }));
    
    // 移除旧的线条
    const oldLine = frustumRef.current.children.find(c => c instanceof THREE.LineSegments);
    if (oldLine) frustumRef.current.remove(oldLine);
    frustumRef.current.add(line);

  }, [pan, tilt, zoom, horizontalFov, verticalFov, visibleRadius]);

  return (
    <div className="relative w-full h-full bg-gray-100 rounded-lg overflow-hidden group">
      {isLoading && (
        <div className="absolute inset-0 flex items-center justify-center bg-gray-100 z-10">
          <div className="text-center text-gray-500">
            <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            <p className="text-sm">正在初始化3D引擎...</p>
          </div>
        </div>
      )}
      <div ref={containerRef} className="w-full h-full min-h-[500px]" />
      
      {/* 覆盖层信息 */}
      <div className="absolute top-4 left-4 bg-white/90 backdrop-blur p-3 rounded-lg shadow-sm border border-gray-200 text-xs space-y-1 pointer-events-none select-none">
        <div className="font-bold text-gray-700 mb-1">当前状态</div>
        <div className="flex justify-between gap-4"><span className="text-gray-500">水平角度:</span> <span className="font-mono">{pan != null && !Number.isNaN(pan) ? `${pan.toFixed(1)}°` : '—'}</span></div>
        <div className="flex justify-between gap-4"><span className="text-gray-500">垂直角度:</span> <span className="font-mono">{tilt != null && !Number.isNaN(tilt) ? `${tilt.toFixed(1)}°` : '—'}</span></div>
        <div className="flex justify-between gap-4"><span className="text-gray-500">可视距离:</span> <span className="font-mono">{visibleRadius != null && !Number.isNaN(visibleRadius) ? `${visibleRadius}m` : '—'}</span></div>
        <div className="flex justify-between gap-4"><span className="text-gray-500">视场角:</span> <span className="font-mono">{horizontalFov != null && !Number.isNaN(horizontalFov) && verticalFov != null && !Number.isNaN(verticalFov) ? `${horizontalFov.toFixed(1)}° x ${verticalFov.toFixed(1)}°` : '—'}</span></div>
      </div>
      
      <div className="absolute bottom-4 right-4 bg-black/70 text-white text-xs px-2 py-1 rounded pointer-events-none select-none">
        左键旋转 • 右键平移 • 滚轮缩放
      </div>
    </div>
  );
};