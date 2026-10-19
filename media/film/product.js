import * as THREE from './vendor/three.module.min.js';
import {PCB} from './product-data.js';
import {sampleMotion,ease} from './motion.js';
const R03=await fetch(new URL('./assets/r03-assembly.json',import.meta.url)).then(response=>{if(!response.ok)throw new Error('R03 CAD mesh missing');return response.json();});
const appImages=await Promise.all(['android-today.png','android-detail.png'].map(async name=>{const image=new Image();image.src=new URL('./assets/'+name,import.meta.url);await image.decode();return image;}));

// All XY coordinates use the same rigid transform of the real PCB. One unit = 10 mm.
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
const lerp=(a,b,t)=>a+(b-a)*t;
const smooth=t=>{t=clamp(t);return t*t*(3-2*t);};
const sans='-apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", sans-serif';
const brand='"Avenir Next", "Helvetica Neue", sans-serif';
function rounded(w,h,r){
 const x=-w/2,y=-h/2,s=new THREE.Shape();
 s.moveTo(x+r,y);s.lineTo(x+w-r,y);s.quadraticCurveTo(x+w,y,x+w,y+r);
 s.lineTo(x+w,y+h-r);s.quadraticCurveTo(x+w,y+h,x+w-r,y+h);
 s.lineTo(x+r,y+h);s.quadraticCurveTo(x,y+h,x,y+h-r);s.lineTo(x,y+r);s.quadraticCurveTo(x,y,x+r,y);return s;
}
function hole(x,y,r){const p=new THREE.Path();p.absarc(x,y,r,0,Math.PI*2,true);return p;}
function solid(w,h,r,d,b=.03){const g=new THREE.ExtrudeGeometry(rounded(w,h,r),{depth:d,bevelEnabled:b>0,bevelSegments:4,steps:1,bevelSize:b,bevelThickness:b,curveSegments:22});g.translate(0,0,-d/2);return g;}
function mesh(g,m,parent,p=[0,0,0]){const obj=new THREE.Mesh(g,m);obj.position.set(...p);parent.add(obj);return obj;}
function plane(shape,material,parent,z,w,h){
 const g=new THREE.ShapeGeometry(shape,48),uv=g.attributes.uv;
 for(let i=0;i<uv.count;i++)uv.setXY(i,uv.getX(i)/w+.5,uv.getY(i)/h+.5);
 return mesh(g,material,parent,[0,0,z]);
}
function texture(canvas){const t=new THREE.CanvasTexture(canvas);t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=8;return t;}
function outline(w,h,r,z,color,alpha=.45){return new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(rounded(w,h,r).getPoints(120).map(v=>new THREE.Vector3(v.x,v.y,z))),new THREE.LineBasicMaterial({color,transparent:true,opacity:alpha}));}

export class ProductStudio {
 constructor(){
  this.renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,preserveDrawingBuffer:true,powerPreference:'high-performance'});
  this.renderer.setSize(1920,1080);this.renderer.setPixelRatio(1);this.renderer.setClearColor(0,0);
  this.renderer.outputColorSpace=THREE.SRGBColorSpace;this.renderer.toneMapping=THREE.ACESFilmicToneMapping;
  this.renderer.toneMappingExposure=.72;
  this.scene=new THREE.Scene();this.camera=new THREE.PerspectiveCamera(34,16/9,.1,160);
  this.scene.add(new THREE.HemisphereLight(0xffffff,0x73737a,.65));
  this.key=this.light(0xffffff,2.8,[-5,7,10]);this.rim=this.light(0xe7efff,2.4,[8,-2,-5]);
  this.strip=this.light(0xfff6eb,2.0,[-7,-5,3]);this.light(0xffffff,1.0,[0,1,9]);
  this.environment();this.materials();this.buildCard();this.buildPhone();this.buildBoard();
 }
 light(color,intensity,p){const l=new THREE.DirectionalLight(color,intensity);l.position.set(...p);this.scene.add(l);return l;}
 environment(){
  // Linear HDR radiance from broad, feathered photography softboxes.
  const w=1024,h=512,pixels=new Float32Array(w*h*4);
  const boxes=[
   [.23,.34,.18,.105,3.2,[1,.985,.97]],
   [.63,.48,.055,.32,3.0,[.93,.97,1]],
   [.80,.42,.20,.23,2.3,[1,1,1]],
   [.50,.18,.35,.045,2.4,[1,.985,.97]],
   [.04,.56,.17,.22,1.4,[.94,.97,1]]
  ];
  for(let y=0;y<h;y++)for(let x=0;x<w;x++){
   const u=x/w,v=y/h,idx=(y*w+x)*4,rgb=[.20,.21,.235];
   for(const [cx,cy,sx,sy,power,color]of boxes){
    const du=Math.min(Math.abs(u-cx),1-Math.abs(u-cx))/sx,dv=Math.abs(v-cy)/sy;
    const gain=power*Math.exp(-2.4*(du**6+dv**6));
    for(let c=0;c<3;c++)rgb[c]+=gain*color[c];
   }
   pixels.set([...rgb,1],idx);
  }
  const env=new THREE.DataTexture(pixels,w,h,THREE.RGBAFormat,THREE.FloatType);env.mapping=THREE.EquirectangularReflectionMapping;env.colorSpace=THREE.LinearSRGBColorSpace;env.needsUpdate=true;
  const pm=new THREE.PMREMGenerator(this.renderer);this.scene.environment=pm.fromEquirectangular(env).texture;env.dispose();pm.dispose();this.scene.environmentIntensity=.95;
 }
 materials(){
  // Fine satin grain influences normals and roughness, never a dark painted albedo.
  const c=document.createElement('canvas');c.width=1024;c.height=1024;const x=c.getContext('2d');
  x.fillStyle='#d8d8d8';x.fillRect(0,0,1024,1024);
  let seed=123;for(let i=0;i<2200;i++){
   seed=(Math.imul(seed,1664525)+1013904223)|0;const n=(seed>>>0)/4294967296;
   x.strokeStyle=`rgba(255,255,255,${.025+n*.09})`;x.lineWidth=.35;const y=n*1024;x.beginPath();x.moveTo(0,y);x.lineTo(1024,y);x.stroke();
  }
  const grain=new THREE.CanvasTexture(c);grain.anisotropy=8;
  this.metal=new THREE.MeshPhysicalMaterial({color:0xc3c6cb,metalness:1,roughness:.20,clearcoat:.10,envMapIntensity:1.25});
  this.graphite=new THREE.MeshPhysicalMaterial({color:0xb7b9be,metalness:.92,roughness:.38,roughnessMap:grain,bumpMap:grain,bumpScale:.0012,clearcoat:.08,clearcoatRoughness:.25,envMapIntensity:1.25});
  this.rearMaterial=new THREE.MeshPhysicalMaterial({color:0xabadb2,metalness:.90,roughness:.40,roughnessMap:grain,bumpMap:grain,bumpScale:.0010,side:THREE.DoubleSide,envMapIntensity:1.2});
  this.black=new THREE.MeshStandardMaterial({color:0x08090c,roughness:.50,metalness:.25});
  this.capMaterial=new THREE.MeshPhysicalMaterial({color:0x7a7e85,metalness:.96,roughness:.26,clearcoat:.10,envMapIntensity:1.2});
 }
 buildCard(){
  const card=this.card=new THREE.Group();this.scene.add(card);
  this.buttons=[];this.micRims=[];this.frontFaces=[];this.r03parts=[];
  const materials={
   metal:new THREE.MeshPhysicalMaterial({color:0xbcc3c9,metalness:.96,roughness:.30,flatShading:true}),
   plastic:new THREE.MeshPhysicalMaterial({color:0xb5bdc2,metalness:0,roughness:.39,flatShading:true}),
   button:new THREE.MeshStandardMaterial({color:0x596169,metalness:0,roughness:.42,flatShading:true}),
   pcb:new THREE.MeshStandardMaterial({color:0x14564a,metalness:.15,roughness:.52}),
   battery:new THREE.MeshStandardMaterial({color:0xa8b2b9,metalness:.55,roughness:.42}),
   fastener:new THREE.MeshStandardMaterial({color:0x7b858d,metalness:.85,roughness:.31}),
   nylon:new THREE.MeshStandardMaterial({color:0x7b858d,metalness:0,roughness:.45}),
   component:new THREE.MeshStandardMaterial({color:0x474f56,metalness:.2,roughness:.44})
  };
  // Same tessellation as R03 CAD: actual 110.80 × 84.84 × 9.80 mm shell.
  for(const item of R03){
   const name=item.name;if(name==='BUTTONS_SPRUE_OPTIONAL')continue;
   const vertices=item.vertices.flatMap(v=>[(v[0]-49.9999)/10,(v[1]-37.0205)/10,(v[2]-4.9)/10]);
   const geometry=new THREE.BufferGeometry();geometry.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));geometry.setIndex(item.faces.flat());geometry.computeVertexNormals();
   const key=['base_metal','lid_metal'].includes(name)?'metal':name.includes('rf_plastic')?'plastic':name.startsWith('button')?'button':name==='PCB'?'pcb':name==='battery'?'battery':name.includes('NYLON')?'nylon':name.includes('reference')?'fastener':'component';
   const object=mesh(geometry,materials[key],card);object.name=name;this.r03parts.push(object);
   if(name.startsWith('lid'))this.frontFaces.push(object);
   if(name==='button_record'||name==='button_recall'){
    object.userData={designator:name==='button_record'?'SW1':'SW2',baseZ:0};
    this.buttons[name==='button_record'?0:1]=object;
   }
  }
 }
 buildBoard(){
  this.board=new THREE.Group();this.card.add(this.board);this.board.visible=false;
 }
 buildPhone(){
  const p=this.phone=new THREE.Group();this.scene.add(p);p.visible=false;
  mesh(solid(3.5,7.2,.43,.20,.045),this.metal,p);plane(rounded(3.36,7.06,.38),this.black,p,.151,3.36,7.06);
  this.screenCanvas=document.createElement('canvas');this.screenCanvas.width=700;this.screenCanvas.height=1440;this.screenTexture=texture(this.screenCanvas);
  plane(rounded(3.2,6.80,.30),new THREE.MeshBasicMaterial({map:this.screenTexture}),p,.157,3.2,6.8);
 }
 phoneUI(t){
  const context=this.screenCanvas.getContext('2d');
  const fade=smooth((t-23)/.5);
  context.drawImage(appImages[0],0,0,700,1440);
  context.save();context.globalAlpha=fade;context.drawImage(appImages[1],0,0,700,1440);context.restore();
  this.screenTexture.needsUpdate=true;
 }
 configure(shot,t){
  const diagnostic=shot==='alignment',pose=sampleMotion(t);
  // A single absolute-time trajectory, independent of text chapters or playback history.
  this.card.visible=shot!=='none';this.board.visible=diagnostic;this.phone.visible=!diagnostic;
  this.card.position.set(...pose.card);this.card.rotation.set(...pose.rotation);this.card.scale.setScalar(pose.scale);
  this.camera.position.set(...pose.camera);this.camera.fov=pose.fov;this.camera.lookAt(...pose.target);
  this.phone.position.set(...pose.phone);this.phone.rotation.set(...pose.phoneRotation);this.phone.scale.setScalar(pose.phoneScale);
  if(pose.phone[0]<24)this.phoneUI(t);
  this.buttons.forEach((b,i)=>{
   const pulse=i===1?Math.max(0,...[10.95,13.10,13.32,13.54].map(hit=>1-ease(Math.abs(t-hit)/.10))):0;
   b.position.z=b.userData.baseZ-.020*pulse;
  });
  if(diagnostic){
   this.card.position.set(0,0,0);this.card.rotation.set(0,0,0);this.card.scale.setScalar(1);
   this.camera.position.set(0,0,25);this.camera.fov=24;this.camera.lookAt(0,0,0);
  }
  this.camera.updateProjectionMatrix();this.camera.updateMatrixWorld(true);this.card.updateMatrixWorld(true);this.phone.updateMatrixWorld(true);
  // Lighting has the same continuous timing as the dolly and orbit.
  const macro=ease((t-10.05)/.8)*(1-ease((t-14.1)/1.15));
  const edge=ease((t-26.3)/1.45)*(1-ease((t-28.25)/1.2));
  this.key.position.set(lerp(-6+Math.sin(t*.55)*5,1,macro),lerp(7,6,macro),10);
  this.key.intensity=1.5+.3*macro;
  this.strip.position.set(lerp(-7+Math.sin(t*.7)*4,-10+(t-27)*6,edge),-5+edge*2,4-edge);
  this.strip.intensity=1.1+.65*edge;this.rim.intensity=1.3;this.scene.environmentIntensity=.55+.07*macro;this.scene.environmentRotation.y=.10*Math.sin(t*.22);
 }
 render(shot,t){this.configure(shot,t);this.renderer.render(this.scene,this.camera);return this.renderer.domElement;}
 point(x,y,z=.49,obj=this.card){const v=new THREE.Vector3(x,y,z);obj.localToWorld(v);v.project(this.camera);return [(v.x+1)*960,(1-v.y)*540];}
 buttonPoint(i=1){return this.point(...PCB.buttons[i].modelXY,.525);}
 micPoint(i){return this.point(...PCB.microphones[i].modelXY,-.49);}
 phonePoint(x=0,y=0){return this.point(x,y,.16,this.phone);}
}
