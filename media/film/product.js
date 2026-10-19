import * as THREE from './vendor/three.module.min.js';
import {PCB} from './product-data.js';
import {sampleMotion,ease} from './motion.js';

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
  this.renderer.toneMappingExposure=1.00;
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
  // Main shell: exactly 6 mm visual envelope, including flush controls; mechanical target only.
  mesh(solid(10.23,7.63,.29,.43,.035),this.metal,card);
  const front=rounded(10.22,7.62,.28);for(const b of PCB.buttons)front.holes.push(hole(...b.modelXY,.253));
  const frontGeometry=new THREE.ExtrudeGeometry(front,{depth:.018,bevelEnabled:true,bevelSize:.006,bevelThickness:.004,bevelSegments:4,steps:1,curveSegments:32});
  const pos=frontGeometry.attributes.position,uv=frontGeometry.attributes.uv;
  for(let i=0;i<uv.count;i++)uv.setXY(i,pos.getX(i)/10.22+.5,pos.getY(i)/7.62+.5);
  mesh(frontGeometry,this.graphite,card,[0,0,.251]);
  const back=rounded(10.22,7.62,.28);for(const mic of PCB.microphones)back.holes.push(hole(...mic.modelXY,.067));
  plane(back,this.rearMaterial,card,-.295,10.22,7.62);
  card.add(outline(10.25,7.65,.29,.265,0xe2e3e7,.28));card.add(outline(10.25,7.65,.29,-.265,0xc6c9ce,.25));
  this.frontFaces=[];
  const decalCanvas=document.createElement('canvas');decalCanvas.width=2048;decalCanvas.height=1536;const d=decalCanvas.getContext('2d');
  function at(x,y){return [(x/10.22+.5)*2048,(.5-y/7.62)*1536];}
  d.fillStyle='#3c4149';d.font=`400 92px ${brand}`;d.letterSpacing='-2px';d.fillText('Voloop',146,1280);
  const decal=mesh(new THREE.PlaneGeometry(10.22,7.62),new THREE.MeshBasicMaterial({map:texture(decalCanvas),transparent:true,depthWrite:false}),card,[0,0,.275]);
  this.buttons=[];
  PCB.buttons.forEach((b,i)=>{
   const [x,y]=b.modelXY;
   mesh(new THREE.CircleGeometry(.251,64),this.black,card,[x,y,.260]);
   mesh(new THREE.RingGeometry(.229,.253,72),this.metal,card,[x,y,.275]);
   const cap=new THREE.Group();cap.position.set(x,y,.279);card.add(cap);
   const body=mesh(new THREE.CylinderGeometry(.222,.224,.038,72),this.capMaterial,cap);body.rotation.x=Math.PI/2;
   if(i===0)mesh(new THREE.CircleGeometry(.038,32),new THREE.MeshBasicMaterial({color:0xbd6055}),cap,[0,0,.020]);
   else{
    const path=new THREE.Shape();path.moveTo(-.046,.061);path.lineTo(.046,.061);path.lineTo(.046,-.063);path.lineTo(0,-.027);path.lineTo(-.046,-.063);path.closePath();
    mesh(new THREE.ShapeGeometry(path),new THREE.MeshBasicMaterial({color:0xe6e8ed}),cap,[0,0,.021]);
   }
   cap.userData={designator:b.designator,baseZ:.279};this.buttons.push(cap);
  });
  this.micRims=[];
  PCB.microphones.forEach(m=>{
   const [x,y]=m.modelXY;const disc=mesh(new THREE.CircleGeometry(.066,48),this.black,card,[x,y,-.298]);disc.rotation.y=Math.PI;
   const ring=mesh(new THREE.RingGeometry(.067,.092,48),this.capMaterial,card,[x,y,-.2995]);ring.rotation.y=Math.PI;this.micRims.push(ring);
   // Fine acoustic mesh, aligned to the actual footprint's all-layer sound opening.
   for(const [a,b]of [[-.022,0],[.022,0],[0,.022],[0,-.022]]){
    const dot=mesh(new THREE.CircleGeometry(.006,8),new THREE.MeshBasicMaterial({color:0x667385}),card,[x+a,y+b,-.300]);dot.rotation.y=Math.PI;
   }
  });
  const bc=document.createElement('canvas');bc.width=2048;bc.height=1536;const bx=bc.getContext('2d');bx.textAlign='center';bx.fillStyle='#484d55';bx.font=`400 44px ${brand}`;bx.letterSpacing='-1px';bx.fillText('Voloop',1024,1270);
  const backLogo=mesh(new THREE.PlaneGeometry(10.22,7.62),new THREE.MeshBasicMaterial({map:texture(bc),transparent:true,depthWrite:false}),card,[0,0,-.299]);backLogo.rotation.y=Math.PI;
  // Connector openings are on the correct edges, with their tangential coordinates from PCB1.
  const usb=new THREE.Group();usb.position.set(5.153,PCB.usb.modelXY[1],-.025);usb.rotation.set(0,Math.PI/2,Math.PI/2);card.add(usb);
  plane(rounded(.91,.29,.135),this.metal,usb,.003,.91,.29);plane(rounded(.82,.21,.10),this.black,usb,.007,.82,.21);
  plane(rounded(.56,.033,.015),new THREE.MeshStandardMaterial({color:0x69768a,metalness:.8,roughness:.35}),usb,.011,.56,.033);
  const sd=new THREE.Group();sd.position.set(PCB.sd.modelXY[0],-3.851,-.025);sd.rotation.x=Math.PI/2;card.add(sd);plane(rounded(1.28,.105,.04),this.black,sd,.003,1.28,.105);
  // Front face and control caps remain independently addressable for the alignment view.
  this.frontFaces=[decal];
 }
 buildBoard(){
  const board=this.board=new THREE.Group();this.card.add(board);board.visible=false;
  const shape=new THREE.Shape();PCB.board.outlineXY.forEach(([x,y],i)=>i?shape.lineTo(x,y):shape.moveTo(x,y));shape.closePath();
  mesh(new THREE.ExtrudeGeometry(shape,{depth:.065,bevelEnabled:false}),new THREE.MeshStandardMaterial({color:0x122b2b,metalness:.3,roughness:.55}),board,[0,0,-.04]);
  PCB.components.forEach(c=>{
   const [x,y]=c.xy,ww=Math.max(.06,c.bboxMm.x/10),hh=Math.max(.05,c.bboxMm.y/10);
   const isModule=c.designator==='U1',isSwitch=c.designator.startsWith('SW'),isConnector=c.designator==='J1'||c.designator==='J2';
   const mat=new THREE.MeshStandardMaterial({color:isModule||isConnector?0x8793a1:isSwitch?0xbac4ce:0x28313a,metalness:isModule||isConnector?.8:.2,roughness:.36});
   mesh(new THREE.BoxGeometry(ww,hh,isModule?.12:.035),mat,board,[x,y,.04]);
  });
 }
 buildPhone(){
  const p=this.phone=new THREE.Group();this.scene.add(p);p.visible=false;
  mesh(solid(3.5,7.2,.43,.20,.045),this.metal,p);plane(rounded(3.36,7.06,.38),this.black,p,.151,3.36,7.06);
  this.screenCanvas=document.createElement('canvas');this.screenCanvas.width=700;this.screenCanvas.height=1440;this.screenTexture=texture(this.screenCanvas);
  plane(rounded(3.2,6.80,.30),new THREE.MeshBasicMaterial({map:this.screenTexture}),p,.157,3.2,6.8);
 }
 phoneUI(t){
  const x=this.screenCanvas.getContext('2d');x.fillStyle='#111114';x.fillRect(0,0,700,1440);
  const g=x.createRadialGradient(630,300,0,500,400,950);g.addColorStop(0,'#29292f');g.addColorStop(1,'#111114');x.fillStyle=g;x.fillRect(0,0,700,1440);
  x.fillStyle='#bfc9d5';x.font=`24px ${sans}`;x.fillText('09:41',52,70);x.fillStyle='#050608';x.beginPath();x.roundRect(268,35,164,40,22);x.fill();
  x.fillStyle='#a0a0a7';x.font=`400 34px ${brand}`;x.fillText('Voloop',52,210);x.fillStyle='#f5f5f7';x.font=`400 52px ${sans}`;x.fillText('记忆，开始有用。',50,305);
  x.fillStyle='#93a5bb';x.font=`24px ${sans}`;x.fillText('来自 Voloop X1 的片段',52,360);
  const cards=[{at:22,y:470,k:'灵感',s:'把灵感，留给下一次创造。',c:'#aac9e7'},
   {at:23.25,y:740,k:'待办',s:'周五，把方案发给同事。',c:'#d4c8a8'},
   {at:24.5,y:1010,k:'提醒',s:'周五 14:00，记得赴约。',c:'#a7c7b9'}];
  for(const o of cards){let a=smooth((t-o.at)/.38);x.save();x.globalAlpha=a;x.translate(0,(1-a)*40);x.fillStyle='#28282e';x.beginPath();x.roundRect(36,o.y,628,218,24);x.fill();x.strokeStyle='#3b3b42';x.lineWidth=1;x.stroke();x.fillStyle=o.c;x.font=`24px ${sans}`;x.fillText(o.k,65,o.y+49);x.fillStyle='#e2eaf5';x.font=`28px ${sans}`;x.fillText(o.s,65,o.y+113);x.fillStyle='#8599af';x.font=`21px ${sans}`;x.fillText('整理自最近保存的录音',65,o.y+170);x.restore();}
  x.textAlign='center';x.fillStyle='#7e91a9';x.font=`19px ${sans}`;x.fillText('手机 AI · 功能愿景',350,1350);x.fillStyle='#aeb9c7';x.beginPath();x.roundRect(252,1390,196,6,3);x.fill();x.textAlign='left';this.screenTexture.needsUpdate=true;
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
  this.key.intensity=2.8+.45*macro;
  this.strip.position.set(lerp(-7+Math.sin(t*.7)*4,-10+(t-27)*6,edge),-5+edge*2,4-edge);
  this.strip.intensity=2.0+1.2*edge;this.rim.intensity=2.4;this.scene.environmentIntensity=.95+.10*macro;this.scene.environmentRotation.y=.10*Math.sin(t*.22);
 }
 render(shot,t){this.configure(shot,t);this.renderer.render(this.scene,this.camera);return this.renderer.domElement;}
 point(x,y,z=.3,obj=this.card){const v=new THREE.Vector3(x,y,z);obj.localToWorld(v);v.project(this.camera);return [(v.x+1)*960,(1-v.y)*540];}
 buttonPoint(i=1){return this.point(...PCB.buttons[i].modelXY,.30);}
 micPoint(i){return this.point(...PCB.microphones[i].modelXY,-.301);}
 phonePoint(x=0,y=0){return this.point(x,y,.16,this.phone);}
}
