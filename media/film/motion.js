/**
 * One continuous, deterministic motion timeline for the entire film.
 * Quintic Hermite segments share velocities and zero endpoint accelerations (C2).
 * Euler angles remain unwrapped so the final physical turn continues through 360°.
 * This uses only product poses; it never changes PCB coordinates or model geometry.
 */
const clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v));
export const ease=t=>{t=clamp(t);return t*t*t*(t*(t*6-15)+10);};
function key(t,label,card,rotation,scale=1,camera=[0,0,26],target=[0,0,0],fov=34,phone=[25,-.25,.2],phoneRotation=[-.02,-.2,.025]){
 return {t,label,values:[...card,...rotation,scale,...camera,...target,fov,...phone,...phoneRotation,1.12]};
}
export const MOTION_KEYS=[
 key(0,'offstage',[22,-2,-3],[.65,-.8,-.15]),
 key(5.65,'enter',[22,-2,-3],[.65,-.8,-.15]),
 key(6.3,'edge reveal',[12,-1,0],[.70,-.48,-.12],1,[0,0,25.5]),
 key(7.5,'wordmark',[3.4,-.35,0],[-.12,-.27,.055],1,[0,0,25.5]),
 key(8.5,'recording window',[4.1,.15,0],[.04,-.23,-.055],.86),
 key(9.05,'approach key',[4.3,.2,0],[.08,-.21,-.07],.83),
 key(10.85,'key macro',[0,0,0],[0,0,.020],1,[2.55,6.1,7.15],[2.50,2.20,0],30),
 key(11.7,'save hour',[.012,0,0],[.01,.015,.019],1,[2.59,6.0,7.04],[2.54,2.22,0],30),
 key(13.7,'multiple presses',[.028,0,0],[.02,.030,.016],1,[2.66,5.85,6.86],[2.60,2.24,0],30),
 key(14.1,'pull away',[.032,0,0],[.022,.032,.015],1,[2.67,5.83,6.85],[2.61,2.24,0],30),
 key(15.25,'hours saved',[4.3,.2,0],[.04,-.25,-.06],.83),
 key(16.1,'leave archive',[4.5,.26,0],[-.03,-.18,-.04],.83),
 key(18.1,'move offstage',[19,1.2,-2],[.3,.95,.18],.83),
 key(20.85,'phone handoff',[20,1.3,-2],[.35,1.0,.2],.83,[0,0,27]),
 key(22.1,'phone arrives',[-4.7,-1.45,0],[.15,.25,-.06],.65,[0,0,27],[0,0,0],34,[5.2,-.25,.2]),
 key(25.9,'phone settles',[-4.5,-1.37,0],[.10,.17,-.045],.65,[0,0,27],[0,0,0],34,[5.05,-.19,.2],[-.035,-.16,.02]),
 key(27.75,'thin profile',[3.5,-.65,0],[1.48,-.035,.055],1,[0,0,23],[0,0,0],34,[24,-.4,-1],[-.04,-.5,.02]),
 key(28.25,'edge light',[3.54,-.63,0],[1.49,-.04,.05],1,[0,0,23],[0,0,0],34,[25,-.4,-1],[-.04,-.5,.02]),
 key(29.45,'acoustic face',[3.5,-.15,0],[3.05,.18,-.06]),
 key(30.5,'listen',[3.55,-.10,0],[3.12,.16,-.05]),
 key(32.25,'hero return',[3.7,-.05,0],[6.16,-.30,.06]),
 key(35,'hero hold',[3.76,0,0],[6.19,-.22,.052])
];
const channels=MOTION_KEYS[0].values.length;
// Shape-preserving harmonic tangents prevent position, scale, and FOV overshoot.
const velocities=MOTION_KEYS.map((key,i)=>Array.from({length:channels},(_,c)=>{
 if(i===0||i===MOTION_KEYS.length-1)return 0;
 const prev=MOTION_KEYS[i-1],next=MOTION_KEYS[i+1];
 const left=(key.values[c]-prev.values[c])/(key.t-prev.t);
 const right=(next.values[c]-key.values[c])/(next.t-key.t);
 if(left*right<=0)return 0;
 const a=key.t-prev.t,b=next.t-key.t,w1=2*b+a,w2=b+2*a;
 const tangent=(w1+w2)/(w1/left+w2/right),limit=1.4*Math.min(Math.abs(left),Math.abs(right));
 return Math.sign(tangent)*Math.min(Math.abs(tangent),limit);
}));
export function sampleMotion(seconds){
 const t=clamp(seconds,0,35);let i=0;while(i<MOTION_KEYS.length-2&&MOTION_KEYS[i+1].t<t)i++;
 const a=MOTION_KEYS[i],b=MOTION_KEYS[i+1],dt=b.t-a.t,u=clamp((t-a.t)/dt),u2=u*u,u3=u2*u,u4=u3*u,u5=u4*u;
 const h00=1-10*u3+15*u4-6*u5,h01=10*u3-15*u4+6*u5,h10=u-6*u3+8*u4-3*u5,h11=-4*u3+7*u4-3*u5;
 const values=a.values.map((v,c)=>h00*v+h01*b.values[c]+h10*velocities[i][c]*dt+h11*velocities[i+1][c]*dt);
 return {card:values.slice(0,3),rotation:values.slice(3,6),scale:values[6],camera:values.slice(7,10),target:values.slice(10,13),fov:values[13],phone:values.slice(14,17),phoneRotation:values.slice(17,20),phoneScale:values[20]};
}
