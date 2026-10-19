import {ProductStudio} from './product.js';
import {PCB} from './product-data.js';
import {sampleMotion,MOTION_KEYS} from './motion.js';

export const DURATION=35;
export const CHAPTERS=[0,5,8,10.5,17,21,27,29,31];
const W=1920,H=1080,canvas=document.querySelector('#film');
const ctx=canvas.getContext('2d',{alpha:false});
const capture=new URLSearchParams(location.search).has('capture');
const studio=new ProductStudio();
const sans='-apple-system, BlinkMacSystemFont, "Helvetica Neue", "PingFang SC", sans-serif';
const brand='"Avenir Next", "Helvetica Neue", sans-serif';
const WHITE='#f5f5f7',INK='#161617',MUTED='#a1a1a6',SILVER='#ccd0d6';
const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const out=t=>1-Math.pow(1-clamp(t),4);
const smooth=t=>{t=clamp(t);return t*t*(3-2*t);};
function txt(str,x,y,size=30,color=WHITE,weight=400,align='left',font=sans,spacing=-.6){
 ctx.save();ctx.fillStyle=color;ctx.font=`${weight} ${size}px ${font}`;ctx.textAlign=align;ctx.textBaseline='alphabetic';ctx.letterSpacing=`${spacing}px`;ctx.fillText(str,x,y);ctx.restore();
}
function kinetic(str,x,y,size,local,color=WHITE,align='left',weight=500,font=sans,spacing=-1.8){
 const p=out(local/.20);ctx.save();ctx.globalAlpha*=p;ctx.translate(x,y+(1-p)*20);const scale=1.012-.012*p;ctx.scale(scale,scale);txt(str,0,0,size,color,weight,align,font,spacing);ctx.restore();
}
function wave(x,y,w,h,t,{alpha=1,selected=1,reverse=false,color=SILVER}={}){
 ctx.save();ctx.globalAlpha*=alpha;
 for(let i=0;i<180;i++){
  const phase=i*.30+(reverse?-1:1)*t*1.9;
  const height=Math.max(2,h*(.08+.72*Math.abs(Math.sin(phase)*Math.cos(i*.106-t*.17))**1.4)*(.55+.45*Math.sin(i*.052+1)**2));
  ctx.fillStyle=i/180<=selected?color:'#353538';ctx.beginPath();ctx.roundRect(x+i*w/180,y-height/2,Math.max(2,w/490),height,1.5);ctx.fill();
 }ctx.restore();
}
function darkBG(){
 ctx.fillStyle='#101114';ctx.fillRect(0,0,W,H);const g=ctx.createRadialGradient(1250,480,60,1170,510,1100);g.addColorStop(0,'#2e3037');g.addColorStop(.55,'#1c1e24');g.addColorStop(1,'#101114');ctx.fillStyle=g;ctx.fillRect(0,0,W,H);
}
function whiteBG(){ctx.fillStyle='#f5f5f7';ctx.fillRect(0,0,W,H);}
function product(shot,t){const frame=window.film?.offlineFrame;if(frame)ctx.drawImage(frame,0,0,W,H);else if(!window.film?.offline)ctx.drawImage(studio.render(shot,t),0,0,W,H);}
function archive(local){
 const labels=['一个灵感','一场对话','一个约定','一段会议','一个点子','一件待办'];
 for(let i=0;i<9;i++){
  const p=i-4,a=.11+.55*(1-Math.abs(p)/5);ctx.save();ctx.globalAlpha=a;
  ctx.translate(960+p*132,770+Math.abs(p)*6);ctx.rotate(p*.024);
  ctx.fillStyle='#202125';ctx.beginPath();ctx.roundRect(-185,-95,370,190,22);ctx.fill();
  txt(labels[i%labels.length],-142,-35,24,'#b8bbc2',400);wave(-142,28,284,45,local+i,{alpha:.6});ctx.restore();
 }
}

export function render(time){
 const t=clamp(time,0,DURATION-.0001);
 if((t>=1&&t<2)||(t>=20&&t<21))whiteBG();else darkBG();
 product('timeline',t);
 if(t<1){kinetic('灵感，闪过。',960,587,158,t,WHITE,'center',500);}
 else if(t<2){kinetic('那句话，错过。',960,587,150,t-1,INK,'center',500);}
 else if(t<3){kinetic('约定，忘了。',960,587,158,t-2,WHITE,'center',500);}
 else if(t<4){kinetic('还没按',960,620,220,t-3,WHITE,'center',500);}
 else if(t<4.5){kinetic('录音。',960,620,220,t-4,WHITE,'center',500);}
 else if(t<5){txt('就过去了。',960,590,112,'#b6b6bc',400,'center');}
 else if(t<6.3){
  const s=t-5;kinetic('往回。',960,550,196,s,WHITE,'center',500);wave(390,780,1140,85,s,{reverse:true,selected:clamp(s),alpha:.65});
 }else if(t<8){
  const s=t-6.3;kinetic(s<1?'保存。':'语环 X1',130,548,s<1?158:132,s<1?s:s-1,WHITE,'left',s<1?500:400,sans,-3.5);
 }else if(t<10.5){
  const s=t-8;
  ctx.save();ctx.globalAlpha*=1-smooth((t-9.35)/.55);
  kinetic('24',115,674,368,s,WHITE,'left',300,sans,-14);kinetic('h',565,660,108,s+.03,'#c7c7cd','left',300);
  kinetic('循环记录。',140,784,59,s-.1,WHITE,'left',400);txt('循环保留窗口 · 续航待实测',145,854,27,MUTED,400);
  ctx.restore();
 }else if(t<11.5){
  const s=t-10.5;kinetic('按一下。',420,730,112,s,INK,'left',400);
 }else if(t<13){
  const s=t-11.5;kinetic('前 1 小时。',420,730,112,s,INK,'left',400);kinetic('已为你保存。',428,805,30,s-.12,'#4f5b62','left',400);
 }else if(t<14.2){
  const s=t-13;kinetic('连按几次。',420,730,104,s,INK,'left',400);
  const n=t<13.1?0:t<13.32?1:t<13.54?2:3;txt(String(n),1720,827,134,INK,300,'right',sans,-5);txt('h',1745,820,54,'#4f5b62',300);
 }else if(t<17){
  const s=t-14.8;kinetic('3',125,655,366,s,WHITE,'left',300,sans,-12);kinetic('h',377,645,98,s+.05,'#c7c7cd','left',300);
  kinetic('保存几小时。',145,768,59,s-.08,WHITE,'left',400);ctx.save();ctx.globalAlpha*=out(s/.2);txt('连按 N 次，保存前 N 小时。',150,840,26,MUTED,400);ctx.restore();
 }else if(t<18){archive(t-17);kinetic('录下来了。',960,424,140,t-17,WHITE,'center',500);}
 else if(t<19){archive(t-17);kinetic('然后呢？',960,422,160,t-18,WHITE,'center',500);}
 else if(t<20){archive(t-17);kinetic('还要，一段段重听？',960,418,110,t-19,WHITE,'center',500);}
 else if(t<21){kinetic('重要的，',960,455,138,t-20,INK,'center',500);kinetic('别埋在录音里。',960,647,138,t-20-.08,INK,'center',500);}
 else if(t<27){
  
  const cuts=[21,22,23.25,24.5,25.75],i=cuts.findLastIndex(v=>t>=v);
  const labels=['连接，即同步。','整理想法。','提取待办。','生成提醒。','让记录，成为下一步。'];
  kinetic(labels[i],128,319,i===4?66:92,t-cuts[i],WHITE,'left',400);
  const details=['在手机上，继续。','把灵感，留给下一次创造。','对话里的事，有了着落。','重要约定，及时想起。','每一天，多一份从容。'];
  kinetic(details[i],136,403,28,t-cuts[i]-.06,MUTED,'left',400);
  txt('当前 Android Demo 界面 · AI 整理与提醒为后续规划',960,1015,20,'#93939a',400,'center');
 }else if(t<29){
  const s=t-27;kinetic('轻薄，随身',134,288,58,s,WHITE,'left',400);
  kinetic('6.5',112,700,240,s,WHITE,'left',300,sans,-9);kinetic('mm',565,685,65,s+.02,'#c3c3cb','left',300);
  txt('目标厚度描述 · 结构实物待复核',143,790,25,MUTED,400);
 }else if(t<31){
  const active=t>=30,s=t-(active?30:29);
  kinetic(active?'人声四麦。':'静时一麦。',128,455,81,s,WHITE,'left',400);
  kinetic(active?'该听清时，听清。':'安静时，更省电。',136,544,30,s-.1,MUTED,'left',400);
  wave(136,696,470,active?72:24,t,{alpha:.5,selected:1});
 }else{
  const s=t-31;kinetic('语环 X1',124,492,132,s,WHITE,'left',400,sans,-4);
  kinetic('重要的，回头还在',138,630,48,s-.45,'#c5c5cd','left',400);
  ctx.save();ctx.globalAlpha*=out((s-.6)/.5);txt('X1 · 轻薄录音卡',142,733,26,'#98989f',400);ctx.restore();
  ctx.save();ctx.globalAlpha*=smooth((s-1)/.5);txt('R03 结构渲染 · 手机 AI 为功能规划 · 24h 为循环保留窗口 · 实物待验证',960,1030,18,'#6e6e75',400,'center');ctx.restore();
 }
 const fade=smooth(t/.08)*smooth((35-t)/.4);if(fade<1){ctx.fillStyle=`rgba(3,3,4,${1-fade})`;ctx.fillRect(0,0,W,H);}
 return t;
}

await document.fonts.ready;
render(32.5);
window.film={render,canvas,studio,renderer:studio.renderer,duration:DURATION,chapters:CHAPTERS,ready:true,PCB,motion:{sample:sampleMotion,keys:MOTION_KEYS}};
document.querySelector('#loading').classList.add('done');
if(capture){document.querySelector('.controls').hidden=true;document.querySelector('.hint').hidden=true;document.querySelector('#loading').hidden=true;}
else await import('./player.js');
