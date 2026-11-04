import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdtempSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:net';
const listenPort=()=>new Promise((resolve,reject)=>{const s=createServer();s.once('error',reject);s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));});});
async function until(f,ms=3500){const stop=Date.now()+ms;while(Date.now()<stop){try{const r=await f();if(r)return r;}catch{}await new Promise(r=>setTimeout(r,60))}throw new Error('Not ready');}
test('local sidecar defaults to Player2; authenticates requests; never contacts OpenAI without consent',async()=>{
 const root=mkdtempSync(join(tmpdir(),'ellie-chatgpt-test-')),port=await listenPort();
 const proc=spawn(process.execPath,[new URL('../sidecar/bridge.mjs',import.meta.url).pathname,'--minecraft',root,'--port',String(port)],{stdio:['pipe','pipe','pipe']});
 try{
  const configFile=join(root,'config','ellie-chatgpt.properties');
  const cfg=await until(()=>{const text=readFileSync(configFile,'utf8');if(!text.includes('secret='))return null;return text});
  assert.match(cfg,/provider=player2/);
  const secret=/secret=(\w+)/.exec(cfg)[1];assert.equal(secret.length,64);
  const url=`http://127.0.0.1:${port}`;
  await until(async()=>{const r=await fetch(url+'/health',{headers:{'X-Ellie-Bridge-Secret':secret}});return r.status===200});
  assert.equal((await fetch(url+'/health')).status,403);
  assert.equal((await fetch(url+'/health',{headers:{'X-Ellie-Bridge-Secret':'wrong'}})).status,403);
  const ready=await fetch(url+'/health',{headers:{'X-Ellie-Bridge-Secret':secret}});const info=await ready.json();
  assert.equal(info.provider,'player2');assert.equal(info.connected,false);
  const response=await fetch(url+'/complete',{method:'POST',headers:{'X-Ellie-Bridge-Secret':secret,'Content-Type':'application/json'},body:'{"messages":[]}'});
  assert.equal(response.status,409);
 } finally{proc.kill('SIGTERM');await new Promise(r=>proc.once('exit',r));rmSync(root,{recursive:true,force:true})}
});
