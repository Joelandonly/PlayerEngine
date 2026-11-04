import {readCompletedResponse} from './protocol.mjs';

/** A Responses API request: no stored conversation or other billing path. */
export function makeInferenceBody({model,input,instructions=''}) {
  if(typeof model!=='string'||!model)throw new Error('No ChatGPT model selected');
  if(!Array.isArray(input)||!input.length)throw new Error('No model input');
  return {
    model,
    ...(typeof instructions==='string'&&instructions.trim()?{instructions}:{}),
    input,store:false,stream:true,
  };
}
export async function requestInference({fetchImpl=fetch,token,model,input,instructions='',maxChars=32768,timeoutMs=110000,baseUrl='https://api.openai.com/v1'}){
  if(!token)throw new Error('ChatGPT session has no access token');
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),timeoutMs);
  try {
    const response=await fetchImpl(`${baseUrl}/responses`,{
      method:'POST',headers:{'Content-Type':'application/json','Accept':'text/event-stream',authorization:`Bearer ${token}`},
      body:JSON.stringify(makeInferenceBody({model,input,instructions})),signal:controller.signal,redirect:'error'
    });
    return await readCompletedResponse(response,maxChars);
  } finally {clearTimeout(timer);}
}
/** Check the selected account and model with a tiny inference, separate from Minecraft. */
export async function probeInference({fetchImpl=fetch,token,model,baseUrl}){
  const output=await requestInference({fetchImpl,token,model,baseUrl,maxChars:512,timeoutMs:30000,
    input:[{role:'user',content:'Reply with exactly: ELLIE_READY'}]});
  if(!output.trim())throw new Error('OpenAI completed a response without text');
  return true;
}
