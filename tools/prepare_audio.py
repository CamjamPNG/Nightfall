#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Reproduce offline audio assets with ffmpeg; third-party source licenses are retained."""
import argparse,concurrent.futures,hashlib,html,json,pathlib,re,subprocess,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--cache',type=pathlib.Path,default=ROOT/'.audio-cache');args=p.parse_args();args.cache.mkdir(parents=True,exist_ok=True)
assets=json.loads((ROOT/'docs/audio-sources.json').read_text());out=ROOT/'app/src/main/assets/audio';out.mkdir(parents=True,exist_ok=True)
def prepare(asset):
    inputs=[];filters=[]
    for j,s in enumerate(asset['sources']):
        cached=args.cache/s['cacheName']
        if not cached.exists():
            req=urllib.request.Request(s['url'],headers={'User-Agent':'Nightfall/1.0 (open-source offline audio; attribution included)'})
            with urllib.request.urlopen(req,timeout=240) as response,cached.open('wb') as dest:
                while chunk:=response.read(1024*1024):dest.write(chunk)
        inputs+=['-t',str(s['seconds']),'-i',str(cached)]
        filters.append(f'[{j}:a]loudnorm=I=-24:TP=-8:LRA=7,aresample=24000,aformat=sample_fmts=fltp:channel_layouts=mono[a{j}]')
    if len(asset['sources'])==2:
        filters.append('[a0][a1]acrossfade=d=1:c1=tri:c2=tri[final]');mapping='[final]'
    else:mapping='[a0]'
    dest=out/(asset['id']+'.pcm')
    subprocess.run(['ffmpeg','-v','error','-y']+inputs+['-filter_complex',';'.join(filters),'-map',mapping,'-ar','24000','-ac','1','-f','s16le',str(dest)],check=True)
    cycle=dest.stat().st_size/48000-1
    assert cycle>=180,(asset['id'],cycle)
    asset['cycleSeconds']=cycle;asset['sha256']=hashlib.sha256(dest.read_bytes()).hexdigest()
    print(asset['id'],f'{cycle:.2f} seconds before repeating',flush=True)
    return asset
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:assets=list(pool.map(prepare,assets))
(ROOT/'docs/audio-credits.json').write_text(json.dumps(assets,indent=2))
lines=['# Audio attribution','','All audio is available offline. Procedural sources and compositions are original code. These field recordings are separately licensed, with their source attribution preserved below. Every bundled recording plays for at least three minutes before returning to the same point; synthesized sources do not use recorded loops.','']
for a in assets:
    lines+=['## '+a['id'],'',f"Cycle before repeating: {a['cycleSeconds']:.3f} seconds.",'','Processing: excerpt(s), mono 24 kHz signed 16-bit PCM; loudness adjusted. Multiple sources are joined with a one-second crossfade. Runtime applies a one-second loop overlap. Frogs combines two distinct frog-chorus recordings. Distant city rain combines a city-storm recording and a Quebec-storm recording. Nighttime forest specifically features a tawny owl at night. Derivative frog audio uses CC BY-SA 4.0; original source licenses are listed below.','']
    for s in a['sources']:
        m=s.get('metadata',{});artist=html.unescape(re.sub('<[^>]+>','',s['artist']))
        lines += [s['title'],'','Creator: '+artist,'','License: '+s['license']+' '+m.get('LicenseUrl',{}).get('value','(public-domain dedication; see source page)'),'',s['page'],'']
    lines+=['SHA-256 of bundled PCM: '+a['sha256'],'']
text='\n'.join(lines);(ROOT/'AUDIO_LICENSES.md').write_text(text)
(ROOT/'app/src/main/assets/AUDIO_LICENSES.md').write_text(text+'\n\n'+(ROOT/'THIRD_PARTY_NOTICES.md').read_text())
