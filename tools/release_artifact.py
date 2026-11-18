#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""What each published release app must prove, read out of the APK file itself."""
import os
import pathlib
import zipfile

NETWORK_PERMISSIONS = ('android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE', 'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION')
UPDATER_ENTRY_POINTS = ('dev/nightfall/update/GithubUpdater', 'dev/nightfall/update/ReleaseFetch', 'dev/nightfall/update/ReleaseManifestParser', 'dev/nightfall/update/PackageInstalls', 'dev/nightfall/update/UpdateInstallReceiver', 'dev/nightfall/update/CheckThrottle', 'dev/nightfall/update/UpdatePrefs', 'dev/nightfall/update/InstallResults', 'dev/nightfall/update/Apks', 'dev/nightfall/update/UpdatePolicy', 'dev/nightfall/update/ReleaseManifest', 'dev/nightfall/update/UpdateState', 'dev/nightfall/update/UpdateApi', 'dev/nightfall/update/UpdateUi')


def build_tools():
    """The Android build-tools the release pipeline signs and inspects with."""
    home = os.environ.get('ANDROID_HOME')
    if not home: raise ValueError('ANDROID_HOME must name an installed Android SDK')
    tools = pathlib.Path(home) / 'build-tools/35.0.0'
    if not tools.is_dir(): raise ValueError('Missing Android build-tools 35.0.0 in ' + home)
    return tools


def fdroid_asset_name(version):
    """The published F-Droid-channel asset; the repository index selects it by this name."""
    return f'Nightfall-{version}-fdroid-universal.apk'


def apk_manifest(apk):
    with zipfile.ZipFile(apk) as z:
        return z.read('AndroidManifest.xml')


def declares(binary_manifest, name):
    """Binary AXML keeps names in a string pool; aapt2 writes UTF-8, some tools UTF-16."""
    return name.encode('utf-8') in binary_manifest or name.encode('utf-16-le') in binary_manifest


def updater_symbols(apk, markers=UPDATER_ENTRY_POINTS):
    """The updater's own classes, read out of the dex string tables."""
    found = set()
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not name.endswith('.dex'): continue
            data = z.read(name)
            for marker in markers:
                if marker.encode('utf-8') in data: found.add(marker)
    return sorted(found)


def fdroid_violation(apk):
    """Why this file cannot be the F-Droid-channel app, or None when it is clean."""
    binary = apk_manifest(apk)
    for permission in NETWORK_PERMISSIONS:
        if declares(binary, permission): return 'declares ' + permission
    markers = updater_symbols(apk)
    return 'contains updater code: ' + ', '.join(markers) if markers else None


def universal_abis(apk):
    """The ABIs whose native libraries are packaged, named from the lib/ directories."""
    with zipfile.ZipFile(apk) as z:
        return {n.split('/')[1] for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')}
