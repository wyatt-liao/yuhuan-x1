const {render,duration,chapters}=window.film;
const $=s=>document.querySelector(s),score=$('#score'),play=$('#play'),sound=$('#sound'),seek=$('#seek');
let running=false,current=0,start=0,audioEnabled=false;
seek.max=duration;
function clock(t){return `${Math.floor(t/60).toString().padStart(2,'0')}:${Math.floor(t%60).toString().padStart(2,'0')}`;}
function update(){seek.value=current;seek.style.setProperty('--progress',`${current/duration*100}%`);$('#time').textContent=`${clock(current)} / ${clock(duration)}`;}
function soundState(){sound.style.opacity=audioEnabled?1:.45;sound.setAttribute('aria-label',audioEnabled?'关闭声音':'开启声音');}
function playing(value){
 if(value&&current>=duration-.1)current=0;
 running=value;start=performance.now()-current*1000;document.body.classList.toggle('paused',!running);play.setAttribute('aria-label',running?'暂停':'播放');
 play.innerHTML=running?'<svg viewBox="0 0 24 24"><path d="M7 5h4v14H7zm6 0h4v14h-4z"/></svg>':'<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
 if(audioEnabled){score.currentTime=current;if(running)score.play().catch(()=>{});else score.pause();}
}
function seekTo(t){current=Math.max(0,Math.min(duration-.001,t));start=performance.now()-current*1000;score.currentTime=current;render(current);update();}
play.onclick=()=>{if(!audioEnabled){audioEnabled=true;soundState();}playing(!running);};
sound.onclick=()=>{audioEnabled=!audioEnabled;soundState();if(audioEnabled&&running){score.currentTime=current;score.play().catch(()=>{});}else score.pause();};
seek.oninput=()=>seekTo(Number(seek.value));
$('#fullscreen').onclick=()=>document.fullscreenElement?document.exitFullscreen():document.documentElement.requestFullscreen();
document.addEventListener('keydown',e=>{if(e.target===seek)return;if(e.code==='Space'){e.preventDefault();play.click();}else if(e.code==='ArrowRight'){e.preventDefault();seekTo(chapters.find(t=>t>current+.4)??duration-.001);}else if(e.code==='ArrowLeft'){e.preventDefault();seekTo([...chapters].reverse().find(t=>t<current-.4)??0);}else if(e.key.toLowerCase()==='f')$('#fullscreen').click();});
window.film.canvas.addEventListener('click',()=>play.click());
document.addEventListener('visibilitychange',()=>{if(document.hidden&&running)playing(false);});
function tick(now){if(running){current=(now-start)/1000;if(current>=duration){current=duration-.001;playing(false);}render(current);update();}requestAnimationFrame(tick);}
window.film.seekTo=seekTo;window.film.setPlaying=playing;soundState();update();requestAnimationFrame(tick);
