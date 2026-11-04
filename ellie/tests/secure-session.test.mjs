import test from 'node:test';
import assert from 'node:assert/strict';
import {storeSecureSession} from '../sidecar/secure-session.mjs';
const sample={access_token:'fake_access_token_NOT_REAL',refresh_token:'fake_refresh_token_NOT_REAL',scopes:['chatgpt.tokens.use.direct']};

test('Windows success writes only sealed ciphertext and retains the session',()=>{
 const writes=[];const notes=[];
 const result=storeSecureSession(sample,{isWindows:true,seal:(payload)=>{
  assert.equal(JSON.parse(payload).access_token,sample.access_token);
  return 'fake-ciphertext-not-plaintext';},writeEncrypted:(text)=>writes.push(text),warn:(text)=>notes.push(text)});
 assert.strictEqual(result.connection,sample);assert.equal(result.persistence,'encrypted');
 assert.deepEqual(writes,['fake-ciphertext-not-plaintext']);assert.deepEqual(notes,[]);
});

test('DPAPI failure keeps authorized session usable and never writes plaintext',()=>{
 let writes=0;const notes=[];
 const result=storeSecureSession(sample,{isWindows:true,seal:()=>{throw new Error('simulated missing DPAPI');},writeEncrypted:()=>{writes++},warn:m=>notes.push(m)});
 assert.strictEqual(result.connection,sample);assert.equal(result.persistence,'memory-only');assert.equal(writes,0);
 assert.match(notes.join(' '),/session only/);
 assert.doesNotMatch(notes.join(' '),/fake_access_token/);
});

test('filesystem permission failure keeps authorized session, does not fall back to plaintext',()=>{
 let tries=0;
 const result=storeSecureSession(sample,{isWindows:true,seal:()=> 'encrypted',writeEncrypted:()=>{tries++;throw new Error('EACCES')},warn:()=>{}});
 assert.strictEqual(result.connection,sample);assert.equal(result.persistence,'memory-only');assert.equal(tries,1);
});

test('non-Windows session is memory-only without touching platform encryption',()=>{
 let calls=0;
 const result=storeSecureSession(sample,{isWindows:false,seal:()=>{calls++},writeEncrypted:()=>{calls++},warn:()=>{}});
 assert.strictEqual(result.connection,sample);assert.equal(result.persistence,'memory-only');assert.equal(calls,0);
});

test('empty ciphertext is treated as failed persistence, never written',()=>{
 let writes=0;
 const result=storeSecureSession(sample,{isWindows:true,seal:()=>'',writeEncrypted:()=>{writes++},warn:()=>{}});
 assert.strictEqual(result.connection,sample);assert.equal(result.persistence,'memory-only');assert.equal(writes,0);
});
