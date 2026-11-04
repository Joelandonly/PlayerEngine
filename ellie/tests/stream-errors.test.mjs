import test from 'node:test';
import assert from 'node:assert/strict';
import {OpenAIInferenceError,readCompletedResponse} from '../sidecar/protocol.mjs';
import {makeInferenceBody,requestInference,probeInference} from '../sidecar/inference.mjs';
const sse=(event,payload)=>`event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`;
const response=(text,{status=200,headers={}}={})=>new Response(text,{status,headers});
const done=(text='ELLIE_READY')=>sse('response.output_text.delta',{type:'response.output_text.delta',delta:text})+sse('response.completed',{type:'response.completed',response:{status:'completed'}});
test('stream failure exposes subscription error code without leaking API messages',async()=>{
  const source=sse('response.failed',{type:'response.failed',response:{error:{code:'subscription_sharing_usage_unavailable',type:'server_error',message:'PRIVATE TOKEN EXAMPLE'}}});
  await assert.rejects(readCompletedResponse(response(source,{headers:{'x-request-id':'req_42'}})),e=>{
    assert.ok(e instanceof OpenAIInferenceError);
    assert.equal(e.code,'subscription_sharing_usage_unavailable');
    assert.match(e.message,/request_id=req_42/);
    assert.doesNotMatch(e.message,/PRIVATE/);
    return true;
  });
});
test('stream incomplete and generic event distinguish terminal states',async()=>{
  await assert.rejects(readCompletedResponse(response(sse('response.incomplete',{type:'response.incomplete',response:{incomplete_details:{reason:'max_output_tokens'}}}))),/max_output_tokens/);
  await assert.rejects(readCompletedResponse(response(sse('error',{type:'error',code:'subscription_sharing_unsupported_capability',param:'tools',message:'secret model text'}))),/code=subscription_sharing_unsupported_capability.*param=tools/);
});
test('admission failure preserves safe metadata and body shape',async()=>{
  await assert.rejects(readCompletedResponse(response(JSON.stringify({detail:'Sensitive human string'}),{status:503,headers:{'x-request-id':'req_123'}})),e=>{
    assert.equal(e.code,'http_503');assert.equal(e.status,503);
    assert.match(e.message,/body=detail-string/);
    assert.doesNotMatch(e.message,/Sensitive human/);return true;
  });
  await assert.rejects(readCompletedResponse(response(JSON.stringify({error:{code:'subscription_sharing_route_not_supported',param:'stream',message:'secret'}}),{status:403})),e=>{
    assert.equal(e.code,'subscription_sharing_route_not_supported');assert.equal(e.param,'stream');return true;
  });
});
test('SSE decode survives CRLF chunk boundary and ignores heartbeat',async()=>{
  const bytes=new TextEncoder().encode(':'+ ' heartbeat\r\n\r\n'+done('ok').replace(/\n/g,'\r\n'));
  const chunks=Array.from(bytes,byte=>Uint8Array.of(byte));
  const readable=new ReadableStream({start(controller){for(const chunk of chunks)controller.enqueue(chunk);controller.close()}});
  assert.equal(await readCompletedResponse(new Response(readable)),'ok');
});
test('model output without response.completed is never accepted',async()=>{
  await assert.rejects(readCompletedResponse(response(sse('response.output_text.delta',{type:'response.output_text.delta',delta:'partial'}))),/stream_ended_without_completion/);
});
test('Responses request strictly enforces store:false and stream:true',async()=>{
  const sent=[];
  const output=await requestInference({token:'SUPER_SECRET_TOKEN',model:'gpt-6.1-sol',instructions:'INSTRUCTION',input:[{role:'user',content:'hello'}],fetchImpl:async(url,opts)=>{
    sent.push({url,opts});return response(done('ok'));
  }});
  assert.equal(output,'ok');assert.equal(sent[0].url,'https://api.openai.com/v1/responses');
  const body=JSON.parse(sent[0].opts.body);
  assert.deepEqual(body,{model:'gpt-6.1-sol',instructions:'INSTRUCTION',input:[{role:'user',content:'hello'}],store:false,stream:true});
  assert.equal(sent[0].opts.headers.authorization,'Bearer SUPER_SECRET_TOKEN');
});
test('minimal probe exercises actual response request shape',async()=>{
  let seen;
  assert.equal(await probeInference({model:'gpt-6.1-sol',token:'secret',fetchImpl:async(url,opts)=>{seen=JSON.parse(opts.body);return response(done())}}),true);
  assert.deepEqual(seen.input,[{role:'user',content:'Reply with exactly: ELLIE_READY'}]);
  assert.equal('instructions' in seen,false);
  await assert.rejects(probeInference({model:'gpt-6.1-sol',token:'secret',fetchImpl:async()=>response(sse('response.completed',{type:'response.completed'}))}),/without text/);
});
