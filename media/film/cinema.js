const video=document.querySelector('#film');
const chapters=[0,5,8,10.5,17,21,27,29,31];
function initialize(){
  document.body.dataset.ready='true';
  const start=Number(new URLSearchParams(location.search).get('t'));
  if(Number.isFinite(start)&&start>0&&start<video.duration)video.currentTime=start;
}
if(video.readyState>=1)initialize();else video.addEventListener('loadedmetadata',initialize,{once:true});
document.addEventListener('keydown',event=>{
  if(event.altKey||event.ctrlKey||event.metaKey)return;
  if(event.code==='Space'||event.key===' '){
    event.preventDefault();event.stopPropagation();
    if(video.paused)video.play().catch(()=>{});else video.pause();
  }else if(event.code==='ArrowRight'||event.key==='ArrowRight'){
    event.preventDefault();event.stopPropagation();video.currentTime=chapters.find(t=>t>video.currentTime+.4)??Math.max(0,video.duration-.01);
  }else if(event.code==='ArrowLeft'||event.key==='ArrowLeft'){
    event.preventDefault();event.stopPropagation();video.currentTime=[...chapters].reverse().find(t=>t<video.currentTime-.4)??0;
  }else if(event.key.toLowerCase()==='f'){
    if(document.fullscreenElement)document.exitFullscreen();else document.documentElement.requestFullscreen();
  }
},true);
