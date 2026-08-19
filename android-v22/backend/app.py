import os, io, gzip, base64, numpy as np
from fastapi import FastAPI, Request, HTTPException
from pydantic import BaseModel
from faster_whisper import WhisperModel

MODEL=os.getenv("TANU_WHISPER_MODEL","small")
DEVICE=os.getenv("TANU_DEVICE","cuda")
COMPUTE=os.getenv("TANU_COMPUTE_TYPE","float16" if DEVICE=="cuda" else "int8")
TOKEN=os.getenv("TANU_SERVER_TOKEN","")
model=WhisperModel(MODEL,device=DEVICE,compute_type=COMPUTE)
app=FastAPI(title="TANU Fast Transcription")

@app.get('/health')
def health(): return {"ok":True,"model":MODEL,"device":DEVICE}

@app.post('/v1/transcribe/chunk')
async def transcribe_chunk(request:Request):
    if TOKEN and request.headers.get('x-tanu-token','')!=TOKEN: raise HTTPException(401,'unauthorized')
    raw=await request.body()
    if request.headers.get("content-encoding","").lower()=="gzip": raw=gzip.decompress(raw)
    if len(raw)<3200:return {"segments":[]}
    audio=np.frombuffer(raw,dtype='<i2').astype(np.float32)/32768.0
    prompt_b64=request.headers.get('x-tanu-prompt-b64','')
    try: prompt=base64.b64decode(prompt_b64).decode('utf-8')[:1200] if prompt_b64 else None
    except Exception: prompt=None
    segments,info=model.transcribe(audio,task='translate',language=None,vad_filter=True,vad_parameters=dict(min_silence_duration_ms=350),initial_prompt=prompt,beam_size=1,best_of=1,condition_on_previous_text=False)
    out=[]
    for s in segments:
        text=s.text.strip()
        if text: out.append({"start_ms":int(s.start*1000),"end_ms":int(s.end*1000),"speaker":"Speaker","text":text})
    return {"language":getattr(info,'language',None),"segments":out}
