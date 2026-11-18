#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""What each published release app must prove, read out of the APK file itself."""
import os
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
NETWORK_PERMISSIONS = ('android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE', 'android.permission.REQUEST_INSTALL_PACKAGES', 'android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION')
UPDATER_SOURCE = ROOT / 'app/src/github/java/dev/nightfall/update'
# A top-level type declaration, with the modifiers Kotlin puts in front of one. Nested declarations
# carry the enclosing class in their dex name, so the enclosing one is what the search needs.
DECLARATION = re.compile(r'(?m)^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |enum |annotation |value |fun )*(?:class|object|interface)\s+([A-Z]\w*)')


def updater_entry_points(source=None):
    """The updater's classes, one group per source file, read from the directory they live in.

    Derived rather than remembered: a class added to the updater package is covered by the F-Droid
    "no updater code" assertion the day it is written, instead of when somebody notices the list is
    short. A group is the file's own name plus every type it declares, because the two names prove
    different halves of one file: R8 keeps type names but may inline and drop a facade class, and a
    file that declares only top-level functions has no type name of its own to look for.
    """
    directory = pathlib.Path(source or UPDATER_SOURCE)
    files = sorted(directory.glob('*.kt'))
    if not files: raise ValueError('The updater source is missing: ' + str(directory))
    groups = {}
    for path in files:
        names = dict.fromkeys((path.stem, *DECLARATION.findall(path.read_text(encoding='utf-8'))))
        groups[path.stem] = tuple('dev/nightfall/update/' + name for name in names)
    return groups


def updater_markers(groups=None):
    """Every name that proves one of the updater's classes is in a build."""
    return tuple(sorted({marker for group in (groups or updater_entry_points()).values() for marker in group}))


def sdk_levels(source=None):
    """The min and target SDK the app module declares, read from the module itself.

    The manifest the updater reads and the checks the publish job runs each used to carry their own
    copy of these two numbers, so an SDK bump left the tooling asserting the level before it.
    """
    text = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8') if source is None else source
    def level(name):
        found = re.search(rf'(?m)^\s*{name}\s*=\s*(\d+)\s*$', text)
        if not found: raise ValueError('The app module declares no ' + name)
        return int(found.group(1))
    return level('minSdk'), level('targetSdk')


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


def updater_symbols(apk, markers=None):
    """The updater's own classes, read out of the dex string tables."""
    markers = updater_markers() if markers is None else markers
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
