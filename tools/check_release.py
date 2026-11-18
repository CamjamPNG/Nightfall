#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline validation of release metadata and bundled audio; no network requests."""
import argparse, hashlib, json, pathlib, re, struct, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--apk',type=pathlib.Path,help='GitHub-channel APK, the one published for updates')
p.add_argument('--fdroid-apk',type=pathlib.Path,help='F-Droid-channel APK, which must carry no network access')
p.add_argument('--github-apk',type=pathlib.Path,help='GitHub-channel APK whose updater must be present')
args=p.parse_args()
def require(condition,message):
    if not condition:raise ValueError(message)
# The last release published before the flavours existed; earlier recipe entries keep their own build.
CHANNEL_SPLIT_CODE=4
NETWORK_PERMISSIONS=('android.permission.INTERNET','android.permission.ACCESS_NETWORK_STATE','android.permission.REQUEST_INSTALL_PACKAGES','android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION')
UPDATER_ENTRY_POINTS=('dev/nightfall/update/GithubUpdater','dev/nightfall/update/ReleaseFetch','dev/nightfall/update/ReleaseManifestParser','dev/nightfall/update/PackageInstalls','dev/nightfall/update/UpdateInstallReceiver')
def apk_manifest(apk):
    with zipfile.ZipFile(apk) as z:
        return z.read('AndroidManifest.xml')
def declares(binary_manifest,name):
    """Binary AXML keeps names in a string pool; aapt2 writes UTF-8, some tools UTF-16."""
    return name.encode('utf-8') in binary_manifest or name.encode('utf-16-le') in binary_manifest
def updater_symbols(apk,markers=UPDATER_ENTRY_POINTS):
    """The updater's own classes, read out of the dex string tables."""
    found=set()
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not name.endswith('.dex'): continue
            data=z.read(name)
            for marker in markers:
                if marker.encode('utf-8') in data: found.add(marker)
    return sorted(found)
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
require(re.search(rf"(?m)^\s+-\s+versionName: ['\"]?{re.escape(version)}['\"]?$",text) and f'versionCode: {code}' in text,'Recipe version mismatch')
require(re.search(rf"(?m)^CurrentVersion: ['\"]?{re.escape(version)}['\"]?$",text) and f'CurrentVersionCode: {code}' in text,'Current recipe version mismatch')
require(f'commit: v{version}' in text,'Recipe tag mismatch')
require('https://github.com/CamjamPNG/Nightfall.git' in text,'Missing verified source repository')
require('scanignore:' not in text,'Broad scanner exclusions must not be introduced')
require('License: MIT' in text,'Code license must match the selected MIT license')
# Releases after the two-channel split must build the flavour without an updater.
for block in re.split(r'(?m)^  - versionName:', text)[1:]:
    entry = re.search(r'versionCode:\s*(\d+)', block)
    code = int(entry[1]) if entry else 0
    if code > CHANNEL_SPLIT_CODE:
        require('gradle:\n      - fdroid\n' in block,'F-Droid build ' + str(code) + ' must select the fdroid flavour')
manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
for permission in ('android.permission.INTERNET','android.permission.RECORD_AUDIO','com.google.android.gms.permission.AD_ID'):
    require(permission not in manifest,'Unexpected permission: '+permission)
require('android.permission.ACCESS_NETWORK_STATE' not in manifest,'Unexpected permission: android.permission.ACCESS_NETWORK_STATE')
github_flavor=(ROOT/'app/src/github/AndroidManifest.xml').read_text()
fdroid_flavor=(ROOT/'app/src/fdroid/AndroidManifest.xml').read_text()
for permission in NETWORK_PERMISSIONS:
    require(f'android:name="{permission}"' in github_flavor,'GitHub channel must declare '+permission)
    require(f'android:name="{permission}" tools:node="remove"' in fdroid_flavor,'F-Droid channel must strip '+permission)
require('REQUEST_INSTALL_PACKAGES' not in [line for line in fdroid_flavor.splitlines() if 'tools:node="remove"' not in line][0].replace('<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />',''),'F-Droid channel must not request install permission')
require('INTERNET' not in [line for line in fdroid_flavor.splitlines() if 'tools:node="remove"' not in line][0].replace('<uses-permission android:name="android.permission.INTERNET" />',''),'F-Droid channel must not request internet')
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
if args.fdroid_apk:
    binary=apk_manifest(args.fdroid_apk)
    for permission in NETWORK_PERMISSIONS:
        require(not declares(binary,permission),'F-Droid APK declares network access: '+permission)
    require(not updater_symbols(args.fdroid_apk),'F-Droid APK contains updater code: '+str(updater_symbols(args.fdroid_apk)))
if args.github_apk or args.apk:
    updater_apk=args.github_apk or args.apk
    binary=apk_manifest(updater_apk)
    for permission in NETWORK_PERMISSIONS:
        require(declares(binary,permission),'GitHub APK is missing the updater permission: '+permission)
    require('dev/nightfall/update/GithubUpdater' in updater_symbols(updater_apk),'GitHub APK has no updater code')
print(json.dumps({'result':'pass','applicationId':appid,'versionName':version,'versionCode':code,'recordings':len(assets),'screenshots':len(shots),'codeLicense':'MIT','commercialCourtesy':'voluntary','apkChecked':str(args.apk) if args.apk else None,'fdroidApkChecked':str(args.fdroid_apk) if args.fdroid_apk else None,'updaterCode':bool(args.github_apk or args.apk)},indent=2))
