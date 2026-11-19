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
# The hidden diagnostics surface is the second thing only the GitHub channel may contain.
DIAGNOSTICS_SOURCE = ROOT / 'app/src/github/java/dev/nightfall/diagnostics'
# Its copy lives in the channel's own resources for the same reason, and is read at check time so a
# new string is covered without anybody extending a list.
DIAGNOSTICS_STRINGS = ROOT / 'app/src/github/res/values/strings.xml'
DIAGNOSTICS_PACKAGE = 'dev/nightfall/diagnostics'
# The one type the F-Droid channel must carry, because app/src/fdroid answers the same three names
# with stubs. It is the only diagnostics descriptor that APK may hold, so the negative check asserts
# exactly that rather than trusting a name list: anything else in this package is a leak.
DIAGNOSTICS_STUB = DIAGNOSTICS_PACKAGE + '/DiagnosticsSurface'
# A top-level type declaration, with the modifiers Kotlin puts in front of one. Nested declarations
# carry the enclosing class in their dex name, so the enclosing one is what the search needs.
DECLARATION = re.compile(r'(?m)^\s*(?:@\w+\s+)*(?:public |internal |private |abstract |sealed |open |data |enum |annotation |value |fun )*(?:class|object|interface)\s+([A-Z]\w*)')


def package_entry_points(source, package):
    """One group per source file in a flavour-only package, read from the directory it lives in.

    Derived rather than remembered: a class added to the package is covered by the F-Droid negative
    assertion the day it is written, instead of when somebody notices the list is short. A group is
    the file's own name, the facade class Kotlin gives a file of top-level functions, and every type
    the file declares, because those names prove different halves of one file: R8 keeps type names but
    may inline and drop a facade, and a file of only top-level functions has no type name at all. A
    group is satisfied by any one of its names being present.
    """
    directory = pathlib.Path(source)
    files = sorted(directory.glob('*.kt'))
    if not files: raise ValueError('Source is missing: ' + str(directory))
    groups = {}
    for path in files:
        names = dict.fromkeys(
            (path.stem, path.stem + 'Kt', *DECLARATION.findall(path.read_text(encoding='utf-8'))))
        groups[path.stem] = tuple(package + '/' + name for name in names)
    return groups


def updater_entry_points(source=None):
    """The updater's classes, one group per source file."""
    return package_entry_points(source or UPDATER_SOURCE, 'dev/nightfall/update')


def diagnostics_entry_points(source=None):
    """The hidden diagnostics surface's classes, one group per source file."""
    return package_entry_points(source or DIAGNOSTICS_SOURCE, 'dev/nightfall/diagnostics')


def markers(groups):
    """Every name that proves one of a package's classes is in a build."""
    return tuple(sorted({marker for group in groups.values() for marker in group}))


def updater_markers(groups=None):
    return markers(groups or updater_entry_points())


def diagnostics_markers(groups=None):
    return markers(groups or diagnostics_entry_points())


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


def dex_bytes(apk):
    with zipfile.ZipFile(apk) as z:
        return b''.join(z.read(name) for name in z.namelist() if name.endswith('.dex'))


def symbols(apk, names):
    """Which of [names] appear in the dex string tables of [apk], as whole type descriptors.

    The trailing semicolon is not decoration: without it `.../Diagnostics` matches the descriptor of
    `.../DiagnosticsSurface`, and a nested class matches its own enclosing one.
    """
    data = dex_bytes(apk)
    return sorted(name for name in names if ('L' + name + ';').encode('utf-8') in data)


def diagnostics_present(apk):
    """Every class this APK holds from the diagnostics package, whatever its name."""
    return sorted({match.decode() for match in
                   re.findall(rb'L(dev/nightfall/diagnostics/[A-Za-z0-9_$]+);', dex_bytes(apk))})


def diagnostics_copy(source=None):
    """The surface's copy, read from the channel-only resource file rather than remembered."""
    text = pathlib.Path(source or DIAGNOSTICS_STRINGS).read_text(encoding='utf-8')
    return dict(re.findall(r'<string name="(settings_diagnostics_[a-z_]+)"[^>]*>(.*?)</string>',
                           text, re.S))


def diagnostics_copy_present(apk, copy=None):
    """Which of the surface's strings this APK's resource table carries."""
    copy = diagnostics_copy() if copy is None else copy
    with zipfile.ZipFile(apk) as z:
        if 'resources.arsc' not in z.namelist(): return []
        table = z.read('resources.arsc')
    # The first words of each string survive aapt2's pooling whatever its encoding prefix is.
    return sorted(name for name, value in copy.items()
                  if value[:24].encode('utf-8') in table)


def updater_symbols(apk, markers=None):
    """The updater's own classes, read out of the dex string tables."""
    return symbols(apk, updater_markers() if markers is None else markers)


def diagnostics_symbols(apk, markers=None):
    """The diagnostics package's own classes, read out of the dex string tables."""
    return symbols(apk, diagnostics_markers() if markers is None else markers)


def fdroid_violation(apk):
    """Why this file cannot be the F-Droid-channel app, or None when it is clean."""
    binary = apk_manifest(apk)
    for permission in NETWORK_PERMISSIONS:
        if declares(binary, permission): return 'declares ' + permission
    updater = updater_symbols(apk)
    if updater: return 'contains updater code: ' + ', '.join(updater)
    present = diagnostics_present(apk)
    extra = [name for name in present if name != DIAGNOSTICS_STUB]
    if extra: return 'contains diagnostics code: ' + ', '.join(extra)
    copy = diagnostics_copy_present(apk)
    return 'contains diagnostics copy: ' + ', '.join(copy) if copy else None


def universal_abis(apk):
    """The ABIs whose native libraries are packaged, named from the lib/ directories."""
    with zipfile.ZipFile(apk) as z:
        return {n.split('/')[1] for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')}
