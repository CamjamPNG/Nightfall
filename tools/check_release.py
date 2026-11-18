#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Offline validation of release metadata and bundled audio; no network requests."""
import argparse, hashlib, json, pathlib, re, struct, subprocess, sys, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from release_artifact import NETWORK_PERMISSIONS, apk_manifest, build_tools, declares, fdroid_asset_name, fdroid_violation, universal_abis, updater_entry_points, updater_symbols
p=argparse.ArgumentParser()
p.add_argument('--apk',type=pathlib.Path,help='GitHub-channel APK, the one published for updates')
p.add_argument('--fdroid-apk',type=pathlib.Path,help='F-Droid-channel APK, which must carry no network access')
p.add_argument('--github-apk',type=pathlib.Path,help='GitHub-channel APK whose updater must be present')
p.add_argument('--fdroid-asset',type=pathlib.Path,help='Published F-Droid-channel asset, signed and named for the repository index')
args=p.parse_args()
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
require(re.search(rf"(?m)^\s+-\s+versionName: ['\"]?{re.escape(version)}['\"]?$",text) and f'versionCode: {code}' in text,'Recipe version mismatch')
require(re.search(rf"(?m)^CurrentVersion: ['\"]?{re.escape(version)}['\"]?$",text) and f'CurrentVersionCode: {code}' in text,'Current recipe version mismatch')
require(f'commit: v{version}' in text,'Recipe tag mismatch')
require('https://github.com/CamjamPNG/Nightfall.git' in text,'Missing verified source repository')
require('scanignore:' not in text,'Broad scanner exclusions must not be introduced')
require('License: MIT' in text,'Code license must match the selected MIT license')
def selects_fdroid(block):
    """True when an F-Droid build block asks for the flavour without an updater."""
    selected = re.search(r'(?m)^    gradle:\n((?:      - \S+\n)+)', block)
    return selected is not None and '      - fdroid\n' in selected[1]


def tag_source(tag):
    """The app build file at a release tag, or None when this checkout does not have the tag."""
    try:
        return subprocess.check_output(['git','-C',str(ROOT),'show',tag+':app/build.gradle.kts'],
                                       stderr=subprocess.DEVNULL,text=True)
    except Exception:
        return None

# A block may select the fdroid flavour only if the tag it pins defines the flavours; a block
# pinned to a tag from before the split must keep the default build, which has no flavor task.
# Tags missing from this checkout are left to the release-tool tests, which pin the template.
checked_tags=[]
for block in re.split(r'(?m)^  - versionName:', text)[1:]:
    tag = re.search(r'commit:\s*(\S+)', block)
    if not tag: continue
    source = tag_source(tag[1])
    if source is None: continue
    checked_tags.append(tag[1])
    flavoured = 'productFlavors' in source
    fdroid = selects_fdroid(block)
    if flavoured:
        require(fdroid, tag[1] + ' defines the flavours, so its block must select the fdroid flavour')
    else:
        require(not fdroid, tag[1] + ' predates the flavours, so its block must keep gradle: yes')
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
    violation=fdroid_violation(args.fdroid_apk)
    if violation:raise ValueError('F-Droid APK '+violation)
updater_check=None
if args.github_apk or args.apk:
    updater_apk=args.github_apk or args.apk
    binary=apk_manifest(updater_apk)
    for permission in NETWORK_PERMISSIONS:
        require(declares(binary,permission),'GitHub APK is missing the updater permission: '+permission)
    # The F-Droid half of this check is a negative one, so it can only be trusted when every name it
    # looks for is provably in the build that must contain it: a marker R8 dropped would find nothing
    # in either APK and pass the F-Droid assertion vacuously.
    found=set(updater_symbols(updater_apk))
    entry_points=updater_entry_points()
    absent=sorted(name for name,group in entry_points.items() if not found.intersection(group))
    require(not absent,'GitHub APK carries no code from '+', '.join(absent)+', so the F-Droid no-updater assertion would search for names this build does not contain')
    updater_check=dict(sources=len(entry_points),markers=len(found))
if args.fdroid_asset:
    # The published F-Droid asset is what an outside repository index serves, so its name and its
    # signer are part of the contract, not just its contents.
    require(args.fdroid_asset.name==fdroid_asset_name(version),'F-Droid asset must be named '+fdroid_asset_name(version))
    certificate=subprocess.check_output([str(build_tools()/'apksigner'),'verify','--verbose','--print-certs',str(args.fdroid_asset)],text=True)
    pins=re.findall(r'certificate SHA-256 digest: ([0-9a-f]+)',certificate)
    require(pins==[(ROOT/'docs/release-signing-certificate.sha256').read_text().strip()],'F-Droid asset is not signed with the pinned certificate')
    require(universal_abis(args.fdroid_asset)=={'arm64-v8a','armeabi-v7a','x86','x86_64'},'F-Droid asset must carry every architecture')
    violation=fdroid_violation(args.fdroid_asset)
    if violation:raise ValueError('F-Droid asset '+violation)
print(json.dumps({'result':'pass','applicationId':appid,'versionName':version,'versionCode':code,'recordings':len(assets),'screenshots':len(shots),'codeLicense':'MIT','commercialCourtesy':'voluntary','apkChecked':str(args.apk) if args.apk else None,'fdroidApkChecked':str(args.fdroid_apk) if args.fdroid_apk else None,'fdroidAssetChecked':str(args.fdroid_asset) if args.fdroid_asset else None,'updaterCode':bool(args.github_apk or args.apk),'updaterSources':updater_check['sources'] if updater_check else None,'updaterMarkers':updater_check['markers'] if updater_check else None,'recipeTagsChecked':checked_tags},indent=2))
