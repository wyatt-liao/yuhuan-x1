(() => {
 'use strict';
 const $ = (selector, parent=document) => parent.querySelector(selector);
 const $$ = (selector, parent=document) => [...parent.querySelectorAll(selector)];
 const slides=$$('.slide'), data=JSON.parse($('#speaker-data').textContent);
 const dialogs=$$('dialog');
 let index=0, returnFocus=null, totalMs=0, startAt=0, timer=null;
 const titleBase='语环 X1 · 产品研发汇报';
 function resize(){
  const scale=Math.min(innerWidth/1600,Math.max(1,innerHeight-62)/900);
  $('#stage').style.transform=`scale(${scale})`;
  $('#frame').style.width=`${1600*scale}px`;
  $('#frame').style.height=`${900*scale}px`;
 }
 function hashIndex(){
  const match=location.hash.match(/^#slide=(\d+)$/);
  return match?Math.max(0,Math.min(slides.length-1,Number(match[1])-1)):0;
 }
 function updateNotes(){
  const current=data[index];
  $('#notes-title').textContent=`${String(index+1).padStart(2,'0')} / ${current.title}`;
  $('#notes-body').textContent=current.notes;
  const source=$('#notes-sources');
  source.replaceChildren();
  current.sources.forEach(item=>{
   const a=document.createElement('a');a.textContent=item.title;a.href=item.url;a.target='_blank';a.rel='noopener noreferrer';source.append(a);
  });
  source.hidden=current.sources.length===0;
 }
 function showSlide(next,{hash=true,announce=true}={}){
  index=Math.max(0,Math.min(slides.length-1,next));
  slides.forEach((slide,i)=>{
   const active=i===index;slide.classList.toggle('active',active);slide.setAttribute('aria-hidden',String(!active));slide.inert=!active;
  });
  $('#counter').textContent=`${String(index+1).padStart(2,'0')} / ${slides.length}`;
  $('#prev').disabled=index===0;$('#next').disabled=index===slides.length-1;
  $('#progress').style.width=`${(index+1)/slides.length*100}%`;
  $$('[data-slide]').forEach(button=>button.setAttribute('aria-current',String(Number(button.dataset.slide)===index)));
  document.title=`${index+1}. ${data[index].title} · ${titleBase}`;
  if(hash && hashIndex()!==index || hash && !location.hash){
   try{history.replaceState(null,'',`#slide=${index+1}`);}catch{location.hash=`slide=${index+1}`;}
  }
  if(announce)$('#status').textContent=`第 ${index+1} 页，共 ${slides.length} 页。${data[index].title}`;
  updateNotes();
 }
 function closeDialogs(){dialogs.forEach(dialog=>{if(dialog.open)dialog.close();});}
 function openDialog(dialog){
  if(dialog.open){dialog.close();return;}
  closeDialogs();returnFocus=document.activeElement;
  if(dialog.id==='notes')updateNotes();
  dialog.showModal();
  const focus=dialog.id==='overview'?$('[aria-current=true]',dialog):$('.close-dialog',dialog);
  focus?.focus();
 }
 dialogs.forEach(dialog=>{
  $('.close-dialog',dialog).addEventListener('click',()=>dialog.close());
  dialog.addEventListener('click',event=>{
   if(event.target!==dialog)return;
   const rect=dialog.getBoundingClientRect();
   if(event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom)dialog.close();
  });
  dialog.addEventListener('close',()=>{if(returnFocus?.isConnected&&!returnFocus.closest('[inert]'))returnFocus.focus();});
 });
 $('#prev').addEventListener('click',()=>showSlide(index-1));
 $('#next').addEventListener('click',()=>showSlide(index+1));
 $('#counter').addEventListener('click',()=>openDialog($('#overview')));
 $('#overview-button').addEventListener('click',()=>openDialog($('#overview')));
 $('#notes-button').addEventListener('click',()=>openDialog($('#notes')));
 $('#help-button').addEventListener('click',()=>openDialog($('#help')));
 $$('[data-slide]').forEach(button=>button.addEventListener('click',()=>{closeDialogs();showSlide(Number(button.dataset.slide));}));
 async function fullscreen(){
  try{
   if(document.fullscreenElement)await document.exitFullscreen();
   else if(document.documentElement.requestFullscreen)await document.documentElement.requestFullscreen();
   else if(document.documentElement.webkitRequestFullscreen)document.documentElement.webkitRequestFullscreen();
   else throw new Error('fullscreen unavailable');
  }catch{
   $('#status').textContent='浏览器未开启全屏。可通过浏览器菜单进入全屏。';
   $('#fullscreen').textContent='浏览器菜单全屏';
   setTimeout(()=>$('#fullscreen').textContent='全屏',3500);
  }
 }
 $('#fullscreen').addEventListener('click',fullscreen);
 document.addEventListener('fullscreenchange',()=>{$('#fullscreen').textContent=document.fullscreenElement?'退出全屏':'全屏';resize();});
 function blackout(on){$('#blackout').hidden=!on;$('#toolbar').inert=on;$('#frame').inert=on;}
 $('#blackout').addEventListener('click',()=>blackout(false));
 document.addEventListener('keydown',event=>{
  if(event.altKey||event.ctrlKey||event.metaKey)return;
  if(event.key==='Escape'){
   if(!$('#blackout').hidden){blackout(false);event.preventDefault();}return;
  }
  if(!$('#blackout').hidden){if(event.key.toLowerCase()==='b'){blackout(false);event.preventDefault();}return;}
  if(dialogs.some(dialog=>dialog.open))return;
  if(event.target.closest('input,textarea,select,[contenteditable=true]'))return;
  const key=event.key.toLowerCase();
  const onControl=event.target.closest('button,a');
  if(key===' '&&onControl)return;
  const navigation={arrowright:1,pagedown:1,' ':1,arrowleft:-1,pageup:-1};
  if(key in navigation){event.preventDefault();showSlide(index+navigation[key]*(event.shiftKey&&key===' '?-1:1));}
  else if(key==='home'){event.preventDefault();showSlide(0);}
  else if(key==='end'){event.preventDefault();showSlide(slides.length-1);}
  else if(key==='f'){event.preventDefault();fullscreen();}
  else if(key==='o'){event.preventDefault();openDialog($('#overview'));}
  else if(key==='n'){event.preventDefault();openDialog($('#notes'));}
  else if(key==='b'){event.preventDefault();blackout(true);}
  else if(key==='?'){event.preventDefault();openDialog($('#help'));}
 });
 let touch=null;
 $('#frame').addEventListener('touchstart',event=>{
  if(event.touches.length!==1||event.target.closest('button,a'))return;
  touch={x:event.touches[0].clientX,y:event.touches[0].clientY};
 },{passive:true});
 $('#frame').addEventListener('touchend',event=>{
  if(!touch||!event.changedTouches.length)return;
  const dx=event.changedTouches[0].clientX-touch.x,dy=event.changedTouches[0].clientY-touch.y;
  touch=null;if(Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)*1.7)showSlide(index+(dx<0?1:-1));
 },{passive:true});
 $('#frame').addEventListener('touchcancel',()=>touch=null,{passive:true});
 $$('[data-recall]').forEach(button=>button.addEventListener('click',()=>{
  const hours=Number(button.dataset.recall);
  $('#recall-hours').textContent=hours;
  $('#recall-title').textContent=`最近 ${hours} 小时获得保护`;
  $$('.time-segment').forEach((segment,i)=>segment.classList.toggle('protected',i>=24-hours));
  $$('[data-recall]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));
  $('#status').textContent=`机制演示：最近 ${hours} 小时获得保护。`;
 }));
 function updateTimer(){
  const elapsed=totalMs+(timer?performance.now()-startAt:0),seconds=Math.floor(elapsed/1000);
  $('#elapsed').textContent=`${String(Math.floor(seconds/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;
 }
 $('#timer-toggle').addEventListener('click',()=>{
  if(timer){totalMs+=performance.now()-startAt;clearInterval(timer);timer=null;$('#timer-toggle').textContent='继续计时';}
  else{startAt=performance.now();timer=setInterval(updateTimer,300);$('#timer-toggle').textContent='暂停计时';}
  updateTimer();
 });
 $('#timer-reset').addEventListener('click',()=>{
  totalMs=0;startAt=performance.now();updateTimer();if(!timer)$('#timer-toggle').textContent='开始计时';
 });
 addEventListener('resize',resize);
 addEventListener('hashchange',()=>showSlide(hashIndex(),{hash:false}));
 resize();showSlide(hashIndex(),{announce:false});
})();
