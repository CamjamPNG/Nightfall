#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Every locale says exactly what `values/` says, key by key, placeholder by placeholder.

The other half of the hardcoded-copy guard. `:app:verifyNoHardcodedCopy` stops a new string from being
typed into a Compose call; this stops a string from being added to `values/` and never reaching
`values-es/`, which is how a translated app rots one English sentence at a time. It also refuses the
failure modes a translation tool cannot see: a key that no longer exists, a placeholder that moved or
changed type, and a plural category the language does not have. Everything here is read from the XML
files, so it needs neither Gradle nor the Android SDK and runs in the `tools` CI job in milliseconds.

Exit status is 1 with every problem printed at once, so one run fixes them all.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# The CLDR plural categories each supported language actually inflects. A category in this set that
# `values/` does not declare, or one in `values/` that this set lacks, is a defect in both directions:
# the first renders a form the platform never asks for, the second falls back silently to `other`.
CATEGORIES = {'es': ('one', 'other')}
PLACEHOLDER = re.compile(r'%(?:\d+\$[A-Za-z]|[A-Za-z]|%)')


def resource_dirs(root=ROOT):
    """Every res tree in the app that holds translatable XML: `main`'s, and each flavour's overlay."""
    found = []
    for res in sorted((Path(root) / 'app' / 'src').glob('*/res')):
        if (res / 'values' / 'strings.xml').exists() or (res / 'values' / 'plurals.xml').exists():
            found.append(res)
    return found


def read(path):
    """The resources in one file, keyed by name, with their kind and any translatable="false"."""
    entries = {}
    for node in ET.parse(path).getroot():
        if node.tag not in ('string', 'string-array', 'plurals'):
            continue
        name = node.get('name')
        if node.tag == 'string':
            entries[name] = ('string', (node.text or ''), node.get('translatable') != 'false')
        elif node.tag == 'string-array':
            entries[name] = ('array', [(item.text or '') for item in node.findall('item')], True)
        else:
            entries[name] = ('plurals', {item.get('quantity'): (item.text or '') for item in node.findall('item')}, True)
    return entries


def placeholders(text):
    """The format arguments in the order they appear, `%%` included: a dropped percent is a bug too."""
    return PLACEHOLDER.findall(text)


def compare(base, translated, locale):
    """Every way `translated` can disagree with `base`. `base` is `values/`, the source of truth."""
    problems = []
    wanted = {name for name, entry in base.items() if entry[2]}
    present = set(translated)
    for name in sorted(wanted - present):
        problems.append(f'{locale}: missing {name}')
    for name in sorted(present - set(base)):
        problems.append(f'{locale}: {name} is not in values/')
    for name in sorted(present & (set(base) - wanted)):
        problems.append(f'{locale}: {name} is marked translatable="false" in values/, so {locale} must not translate it')
    for name in sorted(present & wanted):
        kind, value, _ = base[name]
        other = translated[name]
        if other[0] != kind:
            problems.append(f'{locale}: {name} is a {other[0]} where values/ has a {kind}')
            continue
        if kind == 'plurals':
            for category in sorted(set(value) | set(other[1])):
                if category not in value:
                    problems.append(f'{locale}: {name} declares plural category "{category}", which values/ does not')
                elif category not in other[1]:
                    problems.append(f'{locale}: {name} is missing plural category "{category}"')
                elif value[category].count('%1$') and not other[1][category].count('%1$'):
                    problems.append(f'{locale}: {name} plural "{category}" drops the count placeholder')
            for category in sorted(set(value) & set(other[1])):
                left, right = placeholders(value[category]), placeholders(other[1][category])
                if left != right:
                    problems.append(f'{locale}: {name} plural "{category}" placeholders are {right}, values/ has {left}')
        elif kind == 'array':
            if len(other[1]) != len(value):
                problems.append(f'{locale}: {name} has {len(other[1])} items where values/ has {len(value)}')
            for index, (expected, actual) in enumerate(zip(value, other[1]), start=1):
                if placeholders(expected) != placeholders(actual):
                    problems.append(f'{locale}: {name} item {index} placeholders are {placeholders(actual)}, values/ has {placeholders(expected)}')
        else:
            if placeholders(value) != placeholders(other[1]):
                problems.append(f'{locale}: {name} placeholders are {placeholders(other[1])}, values/ has {placeholders(value)}')
    for category in CATEGORIES[locale]:
        for name, entry in translated.items():
            if entry[0] == 'plurals' and category not in entry[1]:
                problems.append(f'{locale}: {name} is missing the "{category}" category this language inflects')
    return problems


def check(res, locale='es'):
    """Every problem in one res tree for one locale, and the number of keys it covered."""
    base = {}
    for file in ('strings.xml', 'plurals.xml'):
        if (res / 'values' / file).exists():
            base.update(read(res / 'values' / file))
    translated, present = {}, False
    for file in ('strings.xml', 'plurals.xml'):
        if (res / f'values-{locale}' / file).exists():
            present = True
            translated.update(read(res / f'values-{locale}' / file))
    problems = []
    if not present:
        problems.append(f'{res}: no values-{locale}/ at all, so {len([n for n, e in base.items() if e[2]])} keys would render in English')
        return problems, len(base)
    return compare(base, translated, locale), len([n for n, e in base.items() if e[2]])


def require_all(root=ROOT, locale='es'):
    """Raise unless every res tree agrees with its values/ exactly; return (trees, keys, plurals)."""
    problems, trees, keys, plurals = [], 0, 0, 0
    for res in resource_dirs(root):
        found, covered = check(res, locale)
        problems += found
        trees += 1
        keys += covered
        for file in ('strings.xml', 'plurals.xml'):
            path = res / 'values' / file
            if path.exists():
                plurals += len([1 for entry in read(path).values() if entry[0] == 'plurals'])
    if problems:
        raise ValueError(f'values-{locale} does not match values/:\n' + '\n'.join(problems))
    return trees, keys, plurals


def main():
    locale = sys.argv[1] if len(sys.argv) > 1 else 'es'
    problems, trees, keys = [], 0, 0
    for res in resource_dirs():
        found, covered = check(res, locale)
        problems += found
        trees += 1
        keys += covered
    if problems:
        print(f'values-{locale} does not match values/:')
        for problem in problems:
            print('  ' + problem)
        print(f'{len(problems)} problems across {trees} res trees; run python3 tools/check_localization.py after fixing them')
        return 1
    print(f'values-{locale} matches values/: {keys} translatable resources in {trees} res trees, every key, placeholder and plural category')
    return 0


if __name__ == '__main__':
    sys.exit(main())
