#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline validation of release metadata and bundled audio; no network requests."""
import argparse, hashlib, json, pathlib, re, struct, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--apk',type=pathlib.Path);args=p.parse_args()
def require(condition,message):
    if not condition:raise ValueError(message)
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        while chunk:=f.read(1024*1024):h.update(chunk)
    return h.hexdigest()
config=(ROOT/'app/build.gradle.kts').read_text()
appid=re.search(r'applicationId\s*=\s*"([^"]+)"',config)[1]
version=re.search(r'versionName\s*=\s*"([^"]+)"',config)[1]
code=int(re.search(r'versionCode\s*=\s*(\d+)',config)[1])
meta=ROOT/'fastlane/metadata/android/en-US'
require(0<len((meta/'title.txt').read_text().strip())<=50,'Invalid store title')
summary=(meta/'short_description.txt').read_text().strip()
require(0<len(summary)<80 and not summary.endswith('.'),'Summary must be under 80 characters without a trailing dot')
require((meta/'full_description.txt').read_text().strip(),'Missing description')
require(0<len((meta/f'changelogs/{code}.txt').read_text().strip())<=500,'Invalid changelog')
icon=(meta/'images/icon.png').read_bytes()
require(icon.startswith(b'\x89PNG\r\n\x1a\n'),'Store icon is not PNG')
require(struct.unpack('>II',icon[16:24])==(512,512),'Store icon must be 512 square')
shots=list((meta/'images/phoneScreenshots').glob('*.png'))
require(len(shots)>=2,'At least two real phone screenshots are required')
for shot in shots:require(shot.read_bytes().startswith(b'\x89PNG\r\n\x1a\n'),'Invalid screenshot: '+str(shot))
recipe=ROOT/f'fdroid/metadata/{appid}.yml'
require(recipe.exists(),'F-Droid metadata filename does not match applicationId')
text=recipe.read_text()
require(f"versionName: '{version}'" in text and f'versionCode: {code}' in text,'Recipe version mismatch')
require(f'commit: v{version}' in text,'Recipe tag mismatch')
require('https://github.com/CamjamPNG/Nightfall.git' in text,'Missing verified source repository')
require('scanignore:' not in text,'Broad scanner exclusions must not be introduced')
require('License: MIT' in text,'Code license must match the selected MIT license')
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
for permission in ('android.permission.INTERNET','android.permission.RECORD_AUDIO','com.google.android.gms.permission.AD_ID'):
    require(permission not in manifest,'Unexpected permission: '+permission)
assets=json.loads((ROOT/'docs/audio-credits.json').read_text())
for a in assets:
    file=ROOT/f'app/src/main/assets/audio/{a["id"]}.pcm'
    require(file.exists(),'Missing recording: '+a['id'])
    require(digest(file)==a['sha256'],'Recording checksum mismatch: '+a['id'])
    require(file.stat().st_size/48000-1>=180,'Recording repeats before three minutes: '+a['id'])
    require(file.with_suffix('.pcm.license').exists(),'Missing asset license sidecar: '+a['id'])
    if args.apk:
        with zipfile.ZipFile(args.apk) as z:
            entry=z.getinfo(f'assets/audio/{a["id"]}.pcm')
            require(entry.compress_type==zipfile.ZIP_STORED,'PCM must remain uncompressed for memory mapping')
            h=hashlib.sha256()
            with z.open(entry) as f:
                while chunk:=f.read(1024*1024):h.update(chunk)
            require(h.hexdigest()==a['sha256'],'APK recording mismatch: '+a['id'])
require('not a license condition' in (ROOT/'COMMERCIAL_COURTESY.md').read_text(),'Commercial request must remain voluntary')
print(json.dumps({'result':'pass','applicationId':appid,'versionName':version,'versionCode':code,'recordings':len(assets),'screenshots':len(shots),'codeLicense':'MIT','commercialCourtesy':'voluntary','apkChecked':str(args.apk) if args.apk else None},indent=2))
