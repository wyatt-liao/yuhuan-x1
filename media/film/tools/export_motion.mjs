import {sampleMotion} from '../motion.js';
import {writeFile,mkdir} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
const target=resolve(process.argv[2]||'motion-30fps.json');
await mkdir(dirname(target),{recursive:true});
await writeFile(target,JSON.stringify(Array.from({length:1050},(_,i)=>({t:i/30,...sampleMotion(i/30)}))));
console.log(target);
