import test from 'node:test';
import assert from 'node:assert/strict';
import {generateKeyPairSync,sign} from 'node:crypto';
import {secureCompare,cleanCommandResponse,parseModelInput,parseStreamEventBlock,readCompletedResponse,verifyIdToken,shaChallenge,selectableModelSlugs} from '../sidecar/protocol.mjs';
test('constant time compare',()=>{assert.equal(secureCompare('a','a'),true);assert.equal(secureCompare('a','b'),false);assert.equal(secureCompare('a','aa'),false)});
test('command sanitation',()=>{
 assert.deepEqual(JSON.parse(cleanCommandResponse('```json\n{"reason":"ok","command":"idle","message":""}\n```')), {reason:'ok',command:'idle',message:''});
 assert.throws(()=>cleanCommandResponse('{"command":"get log 1"}'),/must have/);
 assert.throws(()=>cleanCommandResponse('not-json'),/valid JSON/);
 assert.throws(()=>cleanCommandResponse(JSON.stringify({reason:'ok',command:'idle',message:'A'.repeat(300)})),/oversized/);
});
test('model input mapping',()=>{const x=parseModelInput({messages:[{role:'system',content:'RULES'},{role:'user',content:'world'},{role:'assistant',content:'hi'}]});assert.match(x.instructions,/RULES/);assert.equal(x.input.length,2)});
test('non-decision prompt preserves planner output contract',()=>{const x=parseModelInput({messages:[{role:'system',content:'Return a list of material steps'},{role:'user',content:'build it'}]},'PLANNING');assert.match(x.instructions,/material steps/);assert.doesNotMatch(x.instructions,/Return ONE valid JSON/)});
test('malformed model roles fail',()=>assert.throws(()=>parseModelInput({messages:[{role:'tool',content:'bad'}]}),/Unsupported/));
test('SSE strict completion',async()=>{
 const stream='event: response.output_text.delta\ndata: {"type":"response.output_text.delta","delta":"ok"}\n\nevent: response.completed\ndata: {"type":"response.completed"}\n\n';
 const response=new Response(stream,{status:200});assert.equal(await readCompletedResponse(response),'ok');
 await assert.rejects(readCompletedResponse(new Response('event: response.output_text.delta\ndata: {"type":"response.output_text.delta","delta":"ok"}\n\n',{status:200})),/stream_ended_without_completion/);
});
test('PKCE challenge stable',()=>assert.equal(shaChallenge('abc'),'ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0'));
test('OIDC signed token validation and nonce',async()=>{
 const {publicKey,privateKey}=generateKeyPairSync('rsa',{modulusLength:2048});const key=publicKey.export({format:'jwk'});key.kid='test';key.alg='RS256';
 const enc=x=>Buffer.from(JSON.stringify(x)).toString('base64url');const now=Math.floor(Date.now()/1000);
 const head=enc({alg:'RS256',kid:'test'});const claim=enc({iss:'https://auth.openai.com',aud:'client-test',nonce:'nonce-test',sub:'user',iat:now,exp:now+400});
 const payload=head+'.'+claim;const token=payload+'.'+sign('sha256',Buffer.from(payload),privateKey).toString('base64url');
 assert.equal((await verifyIdToken(token,{clientId:'client-test',nonce:'nonce-test',jwks:{keys:[key]}})).sub,'user');
 await assert.rejects(verifyIdToken(token,{clientId:'client-test',nonce:'wrong',jwks:{keys:[key]}}),/nonce/);
 await assert.rejects(verifyIdToken(token,{clientId:'wrong',nonce:'nonce-test',jwks:{keys:[key]}}),/audience/);
});

test('Sign in with ChatGPT model catalog: models[] slug, visible only, stable order',()=>{
  const catalog={models:[
    {slug:'gpt-6.1-sol',display_name:'GPT-6.1',visibility:'list'},
    {slug:'private-model',visibility:'hidden'},
    {slug:'gpt-6.1-terra',visibility:'list'},
    {slug:'gpt-6.1-sol',visibility:'list'},
    {slug:'',visibility:'list'},
  ]};
  assert.deepEqual(selectableModelSlugs(catalog),['gpt-6.1-sol','gpt-6.1-terra']);
});
test('legacy API model catalog data[] compatibility',()=>{
  assert.deepEqual(selectableModelSlugs({data:[{id:'gpt-example'},{id:'gpt-example'},{slug:'other'}]}),['gpt-example','other']);
});
test('catalog rejects no selectable models, not a false empty success',()=>{
  assert.throws(()=>selectableModelSlugs({models:[]}),/none are available/);
  assert.throws(()=>selectableModelSlugs({models:[{slug:'secret',visibility:'hidden'}]}),/none are available/);
  assert.throws(()=>selectableModelSlugs({data:[]}),/empty/);
  assert.throws(()=>selectableModelSlugs({somethingElse:[]}),/Unrecognized model catalog format/);
});
