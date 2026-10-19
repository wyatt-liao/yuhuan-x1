#!/usr/bin/env node
/** Deterministic MP4 export using an isolated browser. Requires FFmpeg and Playwright. */
import {createRequire} from 'node:module';
import {createServer} from 'node:http';
import {readFile,stat,mkdir,writeFile} from 'node:fs/promises';
import {resolve,dirname,extname,join,sep} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {once} from 'node:events';

const require=createRequire(import.meta.url);
const {chromium}=require(require.resolve('playwright',{paths:[process.env.X1_NODE_MODULES||'',resolve(dirname(fileURLToPath(import.meta.url)),'..')]}));
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
function arg(name,fallback){const i=process.argv.indexOf(name);return i<0?fallback:process.argv[i+1];}
const cgRoot=arg('--cg-dir','')?resolve(arg('--cg-dir','')):null;
const cgFps=Number(arg('--cg-fps','30'));
const cgStart=Number(arg('--cg-start','0'));
const fps=Number(arg('--fps',cgRoot?'30':'60'));
const seconds=Number(arg('--seconds','35'));
const target=resolve(arg('--out',join(root,'yuhuan-x1-film.mp4')));
const chrome=arg('--chrome',process.env.CHROME_PATH||'');
if(!Number.isFinite(fps)||fps<1||fps>60||seconds<=0||seconds>35)throw new Error('Invalid frame rate or duration');
await mkdir(dirname(target),{recursive:true});
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.m4a':'audio/mp4','.png':'image/png','.jpg':'image/jpeg','.json':'application/json'};
const server=createServer(async(req,res)=>{
 try{
   let name=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
   if(name==='/favicon.ico'){res.writeHead(204);res.end();return;}
   if(name==='/')name='/index.html';
   const base=cgRoot&&name.startsWith('/cg/')?cgRoot:root;
   const relative=base===cgRoot?name.slice(3):name;
   const path=resolve(base,'.'+relative);
   if(!path.startsWith(base+sep)){res.writeHead(403);res.end();return;}
   const data=await readFile(path);res.writeHead(200,{'Content-Type':mime[extname(path)]||'application/octet-stream','Cache-Control':'no-store'});res.end(data);
 }catch{res.writeHead(404);res.end('Not found');}
});
await new Promise(ok=>server.listen(0,'127.0.0.1',ok));
const url=`http://127.0.0.1:${server.address().port}/?capture=1`;
let browser,encoder;
const start=Date.now();let finished=false;
try{
 browser=await chromium.launch({headless:true,...(chrome?{executablePath:chrome}:{}),args:['--enable-gpu','--ignore-gpu-blocklist','--use-angle=metal']});
 const page=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(url);await page.waitForFunction(()=>window.film?.ready,{timeout:90000});
 const hardware=await page.evaluate(()=>{const gl=film.renderer.getContext();const ext=gl.getExtension('WEBGL_debug_renderer_info');return {renderer:ext?gl.getParameter(ext.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),version:gl.getParameter(gl.VERSION)};});
 console.log(JSON.stringify({phase:'ready',fps,seconds,hardware}));
 const encoderArgs=['-hide_banner','-loglevel','error','-y','-f','image2pipe','-vcodec','mjpeg','-r',String(fps),'-i','pipe:0','-i',join(root,'assets/score.m4a'),'-t',String(seconds),'-map','0:v:0','-map','1:a:0','-vf','scale=in_range=pc:out_range=tv:in_color_matrix=bt601:out_color_matrix=bt709,format=yuv420p,setparams=range=limited:color_primaries=bt709:color_trc=bt709:colorspace=bt709','-color_range','tv','-c:v','libx264','-preset','medium','-crf','17','-pix_fmt','yuv420p','-profile:v','high','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-c:a','copy','-movflags','+faststart','-metadata','title=Voloop X1 — 重要的，回头还在。','-metadata','comment=Procedural product concept film; exterior concept and mobile AI vision, not measured hardware performance.',target];
 encoder=spawn(process.env.FFMPEG_PATH||'ffmpeg',encoderArgs,{stdio:['pipe','ignore','pipe']});
 let encoderError='';encoder.stderr.on('data',s=>encoderError+=s.toString());
 const completion=new Promise((ok,fail)=>{encoder.once('error',fail);encoder.once('exit',code=>code===0?ok():fail(new Error(`FFmpeg ${code}: ${encoderError}`)));});
 encoder.stdin.on('error',()=>{});
 const frames=Math.round(seconds*fps);
 for(let i=0;i<frames;i++){
   const jpg=await page.evaluate(async({t,cg,cgFps,cgStart})=>{
    if(cg){const image=new Image();image.src='/cg/frame-'+(Math.min(1049,Math.round(t*cgFps))+cgStart).toString().padStart(4,'0')+'.png';await image.decode();film.offline=true;film.offlineFrame=image;}
    film.render(t);return film.canvas.toDataURL('image/jpeg',.97).split(',')[1];
   },{t:i/fps,cg:Boolean(cgRoot),cgFps,cgStart});
   if(!encoder.stdin.write(Buffer.from(jpg,'base64')))await once(encoder.stdin,'drain');
   if(i%90===0)console.log(JSON.stringify({phase:'rendering',frame:i,total:frames,percent:Math.round(i/frames*100),elapsedSeconds:Math.round((Date.now()-start)/1000)}));
   if(errors.length)throw new Error(errors.join('\n'));
 }
 encoder.stdin.end();await completion;
 // A still from the final hero shot is also generated from exactly the same code.
 const poster=await page.evaluate(async({cg,cgFps,cgStart})=>{
  if(cg){const image=new Image();image.src='/cg/frame-'+(Math.round(32.7*cgFps)+cgStart).toString().padStart(4,'0')+'.png';await image.decode();film.offline=true;film.offlineFrame=image;}
  film.render(32.7);return film.canvas.toDataURL('image/png').split(',')[1];
 },{cg:Boolean(cgRoot),cgFps,cgStart});
 await writeFile(join(dirname(target),'Voloop-X1-封面.png'),Buffer.from(poster,'base64'));
 const report={product:'Voloop X1',durationSeconds:seconds,fps,frames,width:1920,height:1080,codec:'H.264 High / yuv420p + AAC stereo',renderer:hardware,elapsedSeconds:Math.round((Date.now()-start)/1000),browserErrors:errors,bytes:(await stat(target)).size,source:cgRoot?'Blender EEVEE Next + film.js compositing':'Three.js + film.js',motion:'C2 continuous quintic Hermite, unwrapped rotation',cgFrames:cgRoot?1050:null,exterior:'Industrial design concept, not fabrication model',ai:'Mobile AI vision, not delivered app feature'};
 await writeFile(join(dirname(target),'render-report.json'),JSON.stringify(report,null,2)+'\n');
 finished=true;console.log(JSON.stringify({phase:'complete',target,...report}));
}finally{
 if(encoder&&!finished)encoder.kill('SIGTERM');
 if(browser)await browser.close();
 server.close();
}
