import assert from 'node:assert/strict';
import fs from 'node:fs';
import {sampleMotion,MOTION_KEYS} from '../motion.js';
const flat=p=>[...p.card,...p.rotation,p.scale,...p.camera,...p.target,p.fov,...p.phone,...p.phoneRotation,p.phoneScale];
const h=1e-5;let maxVelocityMismatch=0,maxAccelerationMismatch=0;
for(const k of MOTION_KEYS.slice(1,-1)){
 const mm=flat(sampleMotion(k.t-2*h)),m=flat(sampleMotion(k.t-h)),z=flat(sampleMotion(k.t)),p=flat(sampleMotion(k.t+h)),pp=flat(sampleMotion(k.t+2*h));
 for(let i=0;i<z.length;i++){
  const vl=(z[i]-m[i])/h,vr=(p[i]-z[i])/h,al=(z[i]-2*m[i]+mm[i])/(h*h),ar=(pp[i]-2*p[i]+z[i])/(h*h);
  maxVelocityMismatch=Math.max(maxVelocityMismatch,Math.abs(vr-vl));maxAccelerationMismatch=Math.max(maxAccelerationMismatch,Math.abs(ar-al));
 }
}
assert(maxVelocityMismatch<.0001,`velocity jump ${maxVelocityMismatch}`);assert(maxAccelerationMismatch<.12,`acceleration jump ${maxAccelerationMismatch}`);
for(let frame=0;frame<=2100;frame++){
 const t=frame/60,p=sampleMotion(t);assert(flat(p).every(Number.isFinite));assert(p.scale>=.65-1e-9&&p.scale<=1+1e-9);assert(p.camera[2]>=6.8);assert(p.fov>=30-1e-8&&p.fov<=34+1e-8);
}
const order=[32.7,6.3,27.8,10.95,32.7].map(t=>flat(sampleMotion(t)));assert.deepEqual(order[0],order[4]);
const report={timelineSeconds:35,sampledFrames:2101,deterministicSeek:true,continuous:'C2 quintic Hermite',unwrappedRotation:true,maxVelocityMismatch,maxAccelerationMismatch,finiteValues:true};
fs.writeFileSync(new URL('../verification/motion-check.json',import.meta.url),JSON.stringify(report,null,2));console.log(report);
