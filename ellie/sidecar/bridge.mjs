#!/usr/bin/env node
/** Ellie ChatGPT Interpreter sidecar: local OAuth, direct ChatGPT-plan Responses API.
 * No credentials are placed in Minecraft, Java class files, command line arguments, logs or URLs.
 * Credential persistence is Windows-DPAPI current-user encrypted; other OSes use RAM-only sessions.
 */
import http from 'node:http';
import {randomBytes,randomUUID} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawn, spawnSync} from 'node:child_process';
import readline from 'node:readline';
import {AUTH_ORIGIN,RESOURCE,MAX_REQUEST_BYTES,secureCompare,cleanCommandResponse,parseModelInput,
  readCompletedResponse,verifyIdToken,shaChallenge,selectableModelSlugs} from './protocol.mjs';
import {storeSecureSession} from './secure-session.mjs';
import {requestInference,probeInference} from './inference.mjs';

const args=process.argv.slice(2);
const arg=(name, fallback)=>{const idx=args.indexOf(name);return idx>=0?args[idx+1]:fallback;};
const minecraftRoot=path.resolve(arg('--minecraft',process.env.ELLIE_MINECRAFT_HOME || path.join(process.env.APPDATA||os.homedir(),'.minecraft')));
const configDir=path.join(minecraftRoot,'config');
const configPath=path.join(configDir,'ellie-chatgpt.properties');
const privateDir=path.join(configDir,'ellie-chatgpt-private');
const credentialsFile=path.join(privateDir,'connection.dpapi');
const hostFile=path.join(privateDir,'host.json');
const PORT=Number(arg('--port','38457'));
if(!Number.isInteger(PORT)||PORT<1025||PORT>65535)throw new Error('Port must be 1025..65535');
const signal=new AbortController();
const tokenEndpoint='https://auth.openai.com/api/accounts/oauth/token';
const authEndpoint='https://auth.openai.com/api/accounts/authorize';
const jwksEndpoint='https://auth.openai.com/.well-known/jwks.json';
let config={}, connection=null, credentialPersistence='none', activeModel='', busy=false, signingIn=false, refreshPending=null, lastInference='untested';
let portServer=null, callbackServer=null;

function properties(text) {const out={};for(const line of text.split(/\r?\n/)){const match=/^([\w.]+)=(.*)$/.exec(line);if(match)out[match[1]]=match[2];}return out;}
function writeAtomic(file,txt) {const p=file+`.tmp-${process.pid}`;fs.writeFileSync(p,txt,{mode:0o600});fs.renameSync(p,file);}
function writeConfig() {writeAtomic(configPath,`# Ellie local ChatGPT interpreter; switch provider=player2 to disable.\nprovider=${config.provider}\nport=${PORT}\nsecret=${config.secret}\n`);}
function initialConfig() {fs.mkdirSync(configDir,{recursive:true});fs.mkdirSync(privateDir,{recursive:true,mode:0o700});const old=fs.existsSync(configPath)?properties(fs.readFileSync(configPath,'utf8')):{};
 config={provider:old.provider==='chatgpt'?'chatgpt':'player2',secret:/^[a-f\d]{64}$/i.test(old.secret||'')?old.secret:randomBytes(32).toString('hex')};writeConfig();
 let host;try{host=JSON.parse(fs.readFileSync(hostFile,'utf8')).ext_agent_host_id;}catch{}
 if(!/^urn:uuid:[0-9a-f-]{36}$/i.test(host||'')) {host='urn:uuid:'+randomUUID();writeAtomic(hostFile,JSON.stringify({ext_agent_host_id:host}));} return host;
}
const hostId=initialConfig();
// Use current-user DPAPI via Windows PowerShell. Resolve System.Security explicitly
// for hosts where it is not preloaded (including some remote desktop images).
const dpapiScript=(mode)=>{
 const prelude='$ErrorActionPreference="Stop";Add-Type -AssemblyName System.Security;';
 return prelude+(mode==='seal'
  ? '$v=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($v);$r=[Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($r))'
  : '$v=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($v);$r=[Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Text.Encoding]::UTF8.GetString($r))');
};
function dpapi(mode,value){if(process.platform!=='win32')return null;
 const p=spawnSync('powershell.exe',['-NoProfile','-NonInteractive','-Command',dpapiScript(mode)],{input:value,encoding:'utf8',timeout:15000,maxBuffer:1000_000,windowsHide:true});
 if(p.error || p.status!==0 || !p.stdout?.trim()) {
  // Print only a short, sanitized diagnostic code. Do not log stdin or stdout.
  const detail=p.error?.code || (p.status!==0?`exit ${p.status}`:'empty result');
  console.error(`[EllieChatGPT] Windows credential store: ${mode} failed (${detail}).`);
  throw new Error(`Windows credential encryption unavailable (${mode})`);
 }
 return p.stdout.trim();
}
function loadCredentials(){if(!fs.existsSync(credentialsFile))return;
 if(process.platform!=='win32') {console.error('[EllieChatGPT] Credential file exists but OS is not Windows. Refusing to read it.');return;}
 try {const raw=fs.readFileSync(credentialsFile,'utf8');const decoded=dpapi('open',raw);const value=JSON.parse(decoded);if(value&&value.client_id&&value.subject){connection=value;credentialPersistence='encrypted';}}
 catch(e){console.error('[EllieChatGPT] Cannot decrypt saved ChatGPT login. Existing encrypted file left untouched. Type login to reconnect.');}
}
function saveCredentials(value){
 const stored=storeSecureSession(value,{
  isWindows:process.platform==='win32',
  seal:(plain)=>dpapi('seal',plain),
  writeEncrypted:(ciphertext)=>writeAtomic(credentialsFile,ciphertext),
  warn:(message)=>console.warn('[EllieChatGPT] '+message)
 });
 // Crucial: a failed DPAPI save must not discard an otherwise valid OAuth login.
 connection=stored.connection;
 credentialPersistence=stored.persistence;
}
loadCredentials();
const fmtStatus=()=> `provider=${config.provider} connected=${Boolean(connection?.access_token)} allowed=${Boolean(connection?.scopes?.includes('chatgpt.tokens.use.direct'))} credential_storage=${credentialPersistence} model=${activeModel||'(choose after sign-in)'} pending=${busy} inference=${lastInference}`;
function setProvider(mode){if(mode==='chatgpt'&&!connection?.scopes?.includes('chatgpt.tokens.use.direct'))throw new Error('Sign in and allow ChatGPT plan usage first.');config.provider=mode;writeConfig();console.log(`[EllieChatGPT] Provider: ${mode}`);}
async function fetchAbort(url,opts={},timeoutMs=20000){const ac=new AbortController();const t=setTimeout(()=>ac.abort(),timeoutMs);try{return await fetch(url,{...opts,signal:ac.signal,redirect:'error'});}finally{clearTimeout(t)}}
async function requireJson(res,name){let obj;try{obj=await res.json()}catch{throw new Error(`${name} returned invalid JSON: HTTP ${res.status}`)}
 if(!res.ok){const code=typeof obj.error==='string'?obj.error:obj.error?.code||obj.error?.type||'failed';throw new Error(`${name}: HTTP ${res.status} (${String(code).slice(0,100)})`)}return obj;}
async function tokenPost(params){const res=await fetchAbort(tokenEndpoint,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(params)},20000);return requireJson(res,'ChatGPT token exchange');}
function isAuthorized(){return !!(connection?.scopes?.includes('chatgpt.tokens.use.direct')&&connection.access_token)}
async function getAccessToken(){if(!isAuthorized())throw new Error('ChatGPT plan permission missing; type login in bridge terminal');
 if(Date.now()<connection.expires_at-80000)return connection.access_token;
 if(refreshPending)return refreshPending;
 refreshPending=(async()=>{
  const old=connection; if(!old.refresh_token)throw new Error('Session expired; type login to reconnect');
  const obj=await tokenPost({grant_type:'refresh_token',client_id:old.client_id,refresh_token:old.refresh_token,resource:RESOURCE});
  if(!obj.access_token)throw new Error('ChatGPT did not provide a new access token');
  const updated={...old, access_token:obj.access_token,refresh_token:obj.refresh_token||old.refresh_token, expires_at:Date.now()+Number(obj.expires_in||3600)*1000,
    ...(obj.scope?{scopes:obj.scope.split(/\s+/)}:{})};
  // Rotated token set is saved together, not partially.
  saveCredentials(updated);return updated.access_token;
 })().finally(()=>{refreshPending=null});return refreshPending;
}
async function modelCatalog(){const token=await getAccessToken();const res=await fetchAbort(`${RESOURCE}/models`,{headers:{authorization:`Bearer ${token}`}},25000);const result=await requireJson(res,'ChatGPT model catalog');
 return selectableModelSlugs(result);
}
async function pickModel(){if(activeModel)return activeModel;const models=await modelCatalog();const prefer=models.find(m=>/^gpt-6(\.\d+)?-sol$/.test(m))||models.find(m=>m.startsWith('gpt-6'))||models[0];activeModel=prefer;
 console.log('[EllieChatGPT] Selected model: '+activeModel+' (change with `model MODEL_ID`)');return activeModel;}
async function complete(payload,taskClass='DECISION'){
 const {instructions,input}=parseModelInput(payload,taskClass);
 if(!input.length)throw new Error('No conversation turns to interpret');
 const token=await getAccessToken(),model=await pickModel();
 const raw=await requestInference({token,model,instructions,input});
 lastInference='ok';
 const content=taskClass==='DECISION'?cleanCommandResponse(raw):raw;
 return {choices:[{message:{role:'assistant',content}}]};
}
async function runProbe(){
 if(busy)throw new Error('Bridge is busy with a Minecraft request');
 busy=true;
 try{
  const token=await getAccessToken(),model=await pickModel();
  await probeInference({token,model});lastInference='ok';
  console.log(`[EllieChatGPT] PROBE OK: model=${model}; completed real inference; account and model access verified.`);
 }catch(e){lastInference='failed';throw e;}finally{busy=false;}
}

function openBrowser(url){const safeUrl=new URL(url);safeUrl.searchParams.delete('id_token_hint');console.log('\n[EllieChatGPT] Continue with ChatGPT in your browser:\n'+safeUrl.toString()+'\n');
 if(process.platform==='win32'){try{const child=spawn('explorer.exe',[url],{detached:true,stdio:'ignore',windowsHide:true});child.unref();}catch{}}
}
async function signIn(){if(signingIn)return console.log('[EllieChatGPT] Sign-in already in progress.');signingIn=true;
 let listener;let timeout;
 try{
  const state=randomBytes(24).toString('base64url'),nonce=randomBytes(24).toString('base64url'),verifier=randomBytes(48).toString('base64url');
  const issued=connection?.client_id||'dynamic_agent_client';
  const answer=new Promise((resolve,reject)=>{
   let settled=false;
   const finish=(error,data)=>{if(settled)return;settled=true;clearTimeout(timeout);listener.close();error?reject(error):resolve(data)};
   listener=http.createServer((req,res)=>{
    const url=new URL(req.url||'/',`http://127.0.0.1`);
    if(req.method!=='GET'||url.pathname!=='/auth/callback'){res.writeHead(404).end('Not found');return;}
    if(url.searchParams.get('state')!==state){res.writeHead(400).end('Invalid authorization state');return;}
    const code=url.searchParams.get('code');const err=url.searchParams.get('error');
    if(!code||err){res.writeHead(400).end('ChatGPT sign-in cancelled');finish(new Error(err||'Missing code'));return;}
    res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'none'"}).end('<h3>Ellie authorization code received.</h3><p>Return to the bridge terminal to finish sign-in.</p>');
    finish(null,{code, clientId:url.searchParams.get('client_id')});
   });
   listener.on('error',e=>finish(e));
   timeout=setTimeout(()=>finish(new Error('Sign-in timed out')),180_000);
  });
  await new Promise((resolve,reject)=>{listener.listen(0,'127.0.0.1',resolve);listener.once('error',reject)});
  const callback=`http://127.0.0.1:${listener.address().port}/auth/callback`;
  const url=new URL(authEndpoint);const params=url.searchParams;
  params.set('client_id',issued);params.set('response_type','code');params.set('redirect_uri',callback);
  params.set('scope','openid profile email offline_access resource.invoke chatgpt.tokens.use.direct');params.set('resource',RESOURCE);
  params.set('state',state);params.set('nonce',nonce);params.set('code_challenge',shaChallenge(verifier));params.set('code_challenge_method','S256');
  params.set('ext_agent_host_id',hostId);
  if(issued==='dynamic_agent_client')params.set('agent_name_hint','Ellie Minecraft Interpreter');
  // Omit id_token_hint: keeping identity tokens out of terminal output and process arguments.
  openBrowser(url.toString());
  const returned=await answer;
  const clientId=issued==='dynamic_agent_client'?returned.clientId:issued;
  if(!clientId||clientId==='dynamic_agent_client'||(issued!=='dynamic_agent_client'&&returned.clientId&&returned.clientId!==issued))throw new Error('OAuth client registration mismatch');
  const token=await tokenPost({grant_type:'authorization_code',client_id:clientId,code:returned.code,code_verifier:verifier,redirect_uri:callback,resource:RESOURCE});
  if(!token.id_token||!token.access_token)throw new Error('ChatGPT authorization did not return identity and access tokens');
  const keys=await requireJson(await fetchAbort(jwksEndpoint,{},20000),'OpenAI JWKS');
  const identity=await verifyIdToken(token.id_token,{clientId,nonce,jwks:keys});
  if(connection?.subject&&connection.subject!==identity.sub)throw new Error('Account mismatch. Disable existing connection before registering another.');
  const scopes=String(token.scope||'').split(/\s+/).filter(Boolean);
  if(!scopes.includes('chatgpt.tokens.use.direct'))throw new Error('Signed in without ChatGPT plan usage. Reauthorize and grant the separate permission.');
  saveCredentials({client_id:clientId,subject:identity.sub,email:identity.email||'',id_token:token.id_token,access_token:token.access_token,refresh_token:token.refresh_token,
   scopes,expires_at:Date.now()+Number(token.expires_in||3600)*1000,host_id:hostId});
  activeModel='';
  await pickModel();setProvider('chatgpt');
  console.log('[EllieChatGPT] Connected and authorized. Minecraft chat-completions now use ChatGPT.');
 }catch(e){console.error('[EllieChatGPT] Sign-in failed: '+(e instanceof Error?e.message:'unknown error'));
 }finally{signingIn=false;if(listener?.listening)listener.close();}
}
function sendJson(res,status,object){const text=JSON.stringify(object);res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(text)}
const server=http.createServer(async(req,res)=>{
 // Local-only API. Reject unauthenticated requests before parsing any user-provided text.
 if(!secureCompare(req.headers['x-ellie-bridge-secret'],config.secret))return sendJson(res,403,{error:'forbidden'});
 if(req.method==='GET'&&req.url==='/health')return sendJson(res,200,{status:'running',provider:config.provider,connected:isAuthorized()});
 if(req.method!=='POST'||req.url!=='/complete')return sendJson(res,404,{error:'not_found'});
 if(config.provider!=='chatgpt')return sendJson(res,409,{error:'not_enabled'});
 if(busy)return sendJson(res,429,{error:'busy'});
 busy=true;let bytes=0,chunks=[];
 try{
  for await(const chunk of req){bytes+=chunk.length;if(bytes>MAX_REQUEST_BYTES)throw new Error('Request too large');chunks.push(chunk)}
  const payload=JSON.parse(Buffer.concat(chunks).toString('utf8'));
  const taskClass=String(req.headers['x-ellie-task-class']||'DECISION');
  if(!['DECISION','PLANNING','RETRIEVAL','RERANKING','SUMMARIZATION'].includes(taskClass))throw new Error('Unknown AI task class');
  const output=await complete(payload,taskClass);
  return sendJson(res,200,output);
 }catch(e){
  const message=e instanceof Error?e.message:'unknown';
  lastInference='failed';
  console.error(`[EllieChatGPT] Request failed: ${message.slice(0,300)}`);
  return sendJson(res,503,{error:'interpreter_unavailable',upstream_code:e?.code||'unknown',upstream_status:e?.status||0});
 }finally{busy=false;}
});
server.on('error',err=>{console.error('[EllieChatGPT] Cannot bind loopback bridge:',err.message);process.exitCode=1});
server.listen(PORT,'127.0.0.1',()=>{
 console.log(`[EllieChatGPT] Listening only on 127.0.0.1:${PORT}`);
 console.log(`[EllieChatGPT] Minecraft directory: ${minecraftRoot}`);
 console.log('[EllieChatGPT] '+fmtStatus());
 console.log('Commands: login | models | model MODEL_ID | probe | use-chatgpt | use-player2 | status | quit');
 if(args.includes('--login'))void signIn();
});
const cli=readline.createInterface({input:process.stdin,output:process.stdout,terminal:process.stdin.isTTY});
cli.on('line',line=>{const [command,...rest]=line.trim().split(/\s+/);
 const run=async()=>{
  switch(command?.toLowerCase()){
   case 'login': await signIn();break;
   case 'models':console.log((await modelCatalog()).slice(0,40).join('\n'));break;
   case 'model':if(!rest[0])throw new Error('Use: model MODEL_ID');if(!(await modelCatalog()).includes(rest[0]))throw new Error('Model not authorized for connected account');activeModel=rest[0];console.log('[EllieChatGPT] Model: '+activeModel);break;
   case 'use-chatgpt':setProvider('chatgpt');break;
   case 'use-player2':setProvider('player2');break;
   case 'status':console.log('[EllieChatGPT] '+fmtStatus());break;
   case 'probe':await runProbe();break;
   case 'quit':case 'exit':server.close();cli.close();break;
   case '':break;
   default:console.log('Commands: login | models | model MODEL_ID | probe | use-chatgpt | use-player2 | status | quit');
  }
 };void run().catch(e=>console.error('[EllieChatGPT] '+e.message));
});
for(const s of ['SIGTERM','SIGINT'])process.on(s,()=>{server.close();cli.close();if(callbackServer)callbackServer.close();});
