#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Count this repository's tests from the tree, so a number in the docs has one source.

Every suite directory the tree holds is discovered rather than listed: `<module>/src/test`,
`<module>/src/test<Flavour>`, `<module>/src/androidTest` and `tools/tests`. A Kotlin test is an
`@Test` annotation and the `fun` that follows it; a Python test is a `test*` function in a class or
at module level. Comments and string literals are blanked before either is read, so prose about
`@Test` cannot be counted as one.

What the columns mean, because the difference is what the docs got wrong:

- `Tests` is every test method the tree declares.
- `Runs by default` excludes `@Ignore`d tests and tests whose first statement is an assertion
  that skips them, which is how `LevelCalibrationTest.printTrims` waits for
  `NIGHTFALL_CALIBRATE=1`. That fixture is the one place the old prose counted the same suite both
  ways ("97 in core (96 run, plus the opt-in ...)"): the honest answer is 119 declared, 118 run.
- `Robolectric` and `plain JVM` come from the runner a file declares. A file that declares two
  runners is named as unclassified instead of being put in one bucket.

`README.md` holds the table between the markers this module writes, and `check_release.py` verifies
it on every push, so the docs cannot drift from the tree they describe.

The launch kit cannot use that table: `docs/LAUNCH.md` and the drafts in `docs/launch/` are pasted
into public posts, where "120 tests" inline is prose a link would ruin. So those files are held to
the same tree by a second, smaller rule: a number immediately before `tests`, `sounds`, `sources` or
`clips` is a *claim*, and every claim in every guarded document must equal the value this tree
measures — the core suite's declared and default-run counts, the release tooling's own count, the
catalogue's size, and the number of clips in `docs/samples/`. `--update` rewrites them all in place,
so one command clears a red, and a count written in a shape the scanner does not know is refused
rather than skipped. Two phrasings agree with the scanner by rule:

- `N tests` (and `N JUnit tests`, `N JVM tests`) means the **core** suite. Quote another suite's
  count only as `N tests in `tools/tests``, or put it in the generated table instead.
- `N sounds` means the catalogue and `N clips` means `docs/samples/`.

The catalogue has no machine-readable census in the code — `Library` builds its list with loops — so
the row-per-sound list in `docs/LOCALIZATION.md`'s Appendix A is the census this reads. A sound added
to `Library.kt` without a row there still passes this check; that gap is stated here rather than
hidden, and it is the reason the appendix is the one number in copy a person should spot-check.
"""
import argparse
import ast
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
BEGIN = "<!-- test-counts:begin"
END = "<!-- test-counts:end -->"
SUITE_DIRS = ("test", "androidTest")
ROBOLECTRIC_RUNNERS = ("AndroidJUnit4", "RobolectricTestRunner")

ANNOTATION = r'@[A-Za-z_][\w.]*(?:\((?:[^()]|\([^()]*\))*\))?'
ANNOTATION_BLOCK = re.compile(rf'(?:{ANNOTATION}\s*)+')
TEST = re.compile(r'@\s*(?:[A-Za-z_][\w.]*\.)?Test\b')
IGNORE = re.compile(r'@\s*(?:[A-Za-z_][\w.]*\.)?Ignore\b')
FUN = re.compile(r'fun\s+([A-Za-z_]\w*)\s*\(')


def blank_comments_and_strings(text):
    """Replace comments and literal contents with spaces, keeping every character offset."""
    out = list(text)
    i, n = 0, len(text)

    def blank(k):
        if out[k] != '\n':
            out[k] = ' '

    while i < n:
        if text.startswith('//', i):
            while i < n and text[i] != '\n':
                blank(i)
                i += 1
        elif text.startswith('/*', i):
            depth = 0
            while i < n:
                if text.startswith('/*', i):
                    depth, i = depth + 1, i + 2
                    blank(i - 2), blank(i - 1)
                elif text.startswith('*/', i):
                    depth, i = depth - 1, i + 2
                    blank(i - 2), blank(i - 1)
                    if depth == 0:
                        break
                else:
                    blank(i)
                    i += 1
        elif text.startswith('"""', i):
            i += 3
            while i < n and not text.startswith('"""', i):
                blank(i)
                i += 1
            for k in range(i, min(i + 3, n)):
                blank(k)
            i += 3
        elif text[i] in '"\'':
            quote, i = text[i], i + 1
            blank(i - 1)
            while i < n:
                if text[i] == '\\':
                    blank(i)
                    i += 1
                    if i < n:
                        blank(i)
                        i += 1
                    continue
                if text[i] == quote:
                    blank(i)
                    i += 1
                    break
                if text[i] == '\n':
                    break
                blank(i)
                i += 1
        else:
            i += 1
    return ''.join(out)


def kotlin_runner(text):
    """The runner a file declares: 'robolectric', 'jvm', or None when it declares both."""
    robolectric = any(name in text for name in ROBOLECTRIC_RUNNERS)
    explicit_jvm = re.search(r'RunWith\(\s*(?:[A-Za-z_][\w.]*\.)?JUnit4', text) is not None
    if robolectric and explicit_jvm:
        return None
    return 'robolectric' if robolectric else 'jvm'


def kotlin_tests(path):
    """Every `@Test` in one Kotlin file, classified, with the offset of the annotation block."""
    text = blank_comments_and_strings(path.read_text(encoding='utf-8'))
    runner = kotlin_runner(text)
    found = []
    for block in ANNOTATION_BLOCK.finditer(text):
        if not TEST.search(block.group()):
            continue
        name = FUN.match(text, block.end())
        if name is None:
            found.append({'name': '', 'line': text.count('\n', 0, block.start()) + 1,
                          'status': 'unclassified', 'runner': runner})
            continue
        status = 'ignored' if IGNORE.search(block.group()) else 'run'
        if status == 'run':
            body = re.compile(r'\{\s*').search(text, name.end())
            if body is not None and text.startswith('assumeTrue', body.end()):
                status = 'opt-in'
        found.append({'name': name[1], 'line': text.count('\n', 0, block.start()) + 1,
                      'status': status, 'runner': runner})
    return found


def skip_name(decorator):
    """The bare name of a decorator, with a unittest.skip(...) call reduced to its function."""
    if isinstance(decorator, ast.Call):
        decorator = decorator.func
    return decorator.attr if isinstance(decorator, ast.Attribute) else getattr(decorator, 'id', '')


def python_tests(path):
    """Every `test*` method of a class or `test*` function of a module, via the parser."""
    tree = ast.parse(path.read_text(encoding='utf-8'), str(path))
    scopes = [tree]
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            scopes.append(node)
    found = []
    for scope in scopes:
        for node in scope.body:
            if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) or not node.name.startswith('test'):
                continue
            skipped = any(skip_name(decorator) in ('skip', 'skipIf', 'skipUnless')
                          for decorator in node.decorator_list)
            found.append({'name': node.name, 'line': node.lineno,
                          'status': 'ignored' if skipped else 'run', 'runner': 'unittest'})
    return found


def suite_directories(root=ROOT):
    """Every suite directory that exists: the named source sets, plus `tools/tests`."""
    candidates = [path for path in sorted(root.glob('**/src/*'))
                  if path.is_dir() and (path.name in SUITE_DIRS or path.name.startswith('test'))]
    candidates.append(root / 'tools/tests')
    return [path for path in candidates if path.is_dir()]


def collect(root=ROOT):
    """Per-file results for every suite directory, in path order."""
    suites = []
    for directory in suite_directories(root):
        files = []
        for path in sorted(directory.rglob('*.kt')):
            tests = kotlin_tests(path)
            if tests:
                files.append({'path': path, 'language': 'kotlin', 'tests': tests})
        for path in sorted(directory.rglob('*.py')):
            if path.name.startswith('test'):
                tests = python_tests(path)
                if tests:
                    files.append({'path': path, 'language': 'unittest', 'tests': tests})
        if files:
            suites.append({'directory': directory, 'files': files})
    return suites


def summarize(suites, root=ROOT):
    """One row per suite directory and one total row, each counting the same columns."""
    rows = []
    for suite in suites:
        tests = [test for file in suite['files'] for test in file['tests']]
        rows.append({
            'suite': suite['directory'].relative_to(root).as_posix(),
            'declared': len(tests),
            'run': sum(1 for test in tests if test['status'] == 'run'),
            'opt-in': sum(1 for test in tests if test['status'] == 'opt-in'),
            'ignored': sum(1 for test in tests if test['status'] == 'ignored'),
            'unclassified': sum(1 for test in tests if test['status'] == 'unclassified'),
            'robolectric': sum(1 for test in tests if test['runner'] == 'robolectric'),
            'jvm': sum(1 for test in tests if test['runner'] == 'jvm'),
            'unittest': sum(1 for test in tests if test['runner'] == 'unittest'),
            'mixed_runner': [file['path'] for file in suite['files'] if file['tests'][0]['runner'] is None],
        })
    total = {'suite': '**Total**', 'declared': sum(row['declared'] for row in rows),
             'run': sum(row['run'] for row in rows), 'opt-in': sum(row['opt-in'] for row in rows),
             'ignored': sum(row['ignored'] for row in rows),
             'unclassified': sum(row['unclassified'] for row in rows),
             'robolectric': sum(row['robolectric'] for row in rows),
             'jvm': sum(row['jvm'] for row in rows), 'unittest': sum(row['unittest'] for row in rows),
             'mixed_runner': [path for row in rows for path in row['mixed_runner']]}
    return rows + [total]


def table(rows):
    """The suite table, as Markdown, which is what README quotes."""
    lines = [
        "| Suite | Tests | Runs by default | Opt-in | Ignored | Robolectric | Plain JVM | `unittest` |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        label = f"`{row['suite']}`" if row['suite'] != '**Total**' else row['suite']
        cells = [label, row['declared'], row['run'], row['opt-in'], row['ignored'],
                 row['robolectric'], row['jvm'], row['unittest']]
        lines.append('| ' + ' | '.join(str(cell) for cell in cells) + ' |')
    return '\n'.join(lines)


def file_rows(suites, root=ROOT):
    """One line per file, so a count can be traced to the file that declares it."""
    lines = []
    for suite in suites:
        for file in suite['files']:
            relative = file['path'].relative_to(root)
            counts = {}
            for test in file['tests']:
                counts[test['status']] = counts.get(test['status'], 0) + 1
            detail = ', '.join(f'{count} {status}' for status, count in sorted(counts.items()))
            lines.append(f"{relative}  {len(file['tests'])}  {file['tests'][0]['runner'] or 'unclassified'}  {detail}")
    return '\n'.join(lines)


def block(root=ROOT):
    """The marker-delimited section README holds, generated rather than typed."""
    suites = collect(root)
    rows = summarize(suites, root)
    unclassified = [f"unclassified: {path.relative_to(root)}, two runners in one file"
                    for row in rows[:-1] for path in row['mixed_runner']]
    unclassified += [f"unclassified: {file['path'].relative_to(root)}:{test['line']}"
                     for suite in suites for file in suite['files'] for test in file['tests']
                     if test['status'] == 'unclassified']
    body = [BEGIN + " (generated by `python3 tools/count_tests.py --update`; do not edit by hand) -->",
            table(rows)]
    body += [f"<!-- {line} -->" for line in unclassified]
    body.append(END)
    return '\n'.join(body)


def replace(text, root=ROOT):
    """The same document with its count block regenerated, or a ValueError when the markers are gone."""
    start, end = text.find(BEGIN), text.find(END)
    if start < 0 or end < start:
        raise ValueError(f"no {BEGIN} ... {END} block to fill")
    return text[:start] + block(root) + text[end + len(END):]


def require_matches(path=ROOT / 'README.md', root=ROOT):
    """Raise unless the document's count block is what the tree counts today."""
    text = pathlib.Path(path).read_text(encoding='utf-8')
    expected = replace(text, root)
    if expected != text:
        raise ValueError(f"{path} test counts differ from the tree: run python3 tools/count_tests.py "
                         f"--update {path}")


# Names a fact this module measures, so a claim in prose can be corrected from the tree.
CORE = 'core tests'
CORE_RUN = 'core run'
TOOLING = 'tooling tests'
CATALOGUE = 'catalogue'
CLIPS = 'sample clips'

# Phrasings a guarded document may use, most specific first: a claim already taken by an earlier
# pattern is not offered to a later one, so "85 tests in `tools/tests`" wins over "85 tests". The gap
# between a number and its unit may wrap a line but never crosses a blank one, so a number ending a
# paragraph cannot capture the next one's opening words.
GAP = r'(?:[ \t]+|\n(?![ \t]*\n)[ \t]*)'
CLAIMS = (
    (re.compile(rf'(\d+){GAP}tests are defined, (\d+){GAP}run'), (CORE, CORE_RUN)),
    (re.compile(rf'(\d+){GAP}of which run'), (CORE_RUN,)),
    (re.compile(rf'(\d+){GAP}tests in `tools/tests`'), (TOOLING,)),
    (re.compile(rf'(\d+){GAP}(?:JVM|JUnit|unit){GAP}tests'), (CORE,)),
    (re.compile(rf'(\d+){GAP}tests?'), (CORE,)),
    (re.compile(rf'(\d+){GAP}declared'), (CORE,)),
    (re.compile(rf'(\d+){GAP}execute'), (CORE_RUN,)),
    (re.compile(rf'(\d+){GAP}run\b'), (CORE_RUN,)),
    (re.compile(rf'(\d+){GAP}(?:sounds?|sources?)'), (CATALOGUE,)),
    (re.compile(rf'(\d+){GAP}clips?'), (CLIPS,)),
)
# Every count the scanner must be able to place: a number this close to a tracked unit that no claim
# pattern took is refused, because a count nobody regenerates is the drift this module exists for.
UNRECOGNISED = re.compile(
    rf'(?<![\w.])(\d[\d,]*){GAP}(?:[A-Za-z][\w-]*{GAP})?(?:tests?|sounds?|sources?|clips?)')
GUARDED = ('README.md', 'docs/LAUNCH.md', 'docs/launch')
LOCALIZATION = 'docs/LOCALIZATION.md'
APPENDIX = '## Appendix A'
SAMPLES = 'docs/samples'


def guarded_documents(root=ROOT):
    """Every document the launch-word guard covers: README, the manual, and each draft."""
    paths = [root / 'README.md', root / 'docs/LAUNCH.md']
    paths += sorted((root / 'docs/launch').rglob('*.md'))
    return [path for path in paths if path.exists()]


def catalogue_size(root=ROOT):
    """Rows in the one-row-per-sound appendix of `docs/LOCALIZATION.md`, minus its header rows."""
    path = root / LOCALIZATION
    text = path.read_text(encoding='utf-8')
    if APPENDIX not in text:
        raise ValueError(f"{path} has no '{APPENDIX}' section, which is the catalogue census")
    section = text.split(APPENDIX, 1)[1].split('\n## ', 1)[0]
    rows = [line for line in section.splitlines() if line.startswith('| ')]
    if len(rows) < 3:
        raise ValueError(f"{path}'s '{APPENDIX}' table has {len(rows)} rows, which is too few to be a census")
    return len(rows) - 2


def facts(root=ROOT):
    """The values every guarded document is allowed to quote, measured from this tree."""
    rows = summarize(collect(root), root)
    by_suite = {row['suite']: row for row in rows[:-1]}
    if 'core/src/test' not in by_suite:
        raise ValueError('this tree has no core/src/test suite to count')
    return {
        CORE: by_suite['core/src/test']['declared'],
        CORE_RUN: by_suite['core/src/test']['run'],
        TOOLING: by_suite.get('tools/tests', {}).get('declared', 0),
        CATALOGUE: catalogue_size(root),
        CLIPS: len(list((root / SAMPLES).glob('*.ogg'))),
    }


def claims(text):
    """Every recognised count in the document: `(start, end, fact)` for each number, in text order."""
    found = []
    for pattern, names in CLAIMS:
        for match in pattern.finditer(text):
            for index, name in enumerate(names, start=1):
                start, end = match.span(index)
                if any(claimed <= start < claimed_end for claimed, claimed_end, _ in found):
                    continue
                found.append((start, end, name))
    return sorted(found)


def unrecognised(text):
    """Counts that no claim pattern accounts for, which would otherwise sit outside the guard."""
    claimed = {start for start, _, _ in claims(text)}
    return [match for match in UNRECOGNISED.finditer(text) if match.start(1) not in claimed]


def line_of(text, offset):
    return text.count('\n', 0, offset) + 1


def update_document(path, root=ROOT):
    """Rewrite one document's generated block and every count it states, from this tree."""
    path = pathlib.Path(path)
    text = path.read_text(encoding='utf-8')
    if BEGIN in text:
        text = replace(text, root)
    values = facts(root)
    for start, end, name in sorted(claims(text), reverse=True):
        text = text[:start] + str(values[name]) + text[end:]
    path.write_text(text, encoding='utf-8')


def require_document(path, root=ROOT):
    """Raise unless every count in the document equals what this tree measures."""
    path = pathlib.Path(path)
    text = path.read_text(encoding='utf-8')
    problems = []
    if BEGIN in text and replace(text, root) != text:
        problems.append(f'{path}: the generated table differs from the tree')
    values = facts(root)
    for start, end, name in claims(text):
        if int(text[start:end].replace(',', '')) != values[name]:
            problems.append(f'{path}:{line_of(text, start)}: says {text[start:end]} for {name}, '
                            f'the tree has {values[name]}')
    for match in unrecognised(text):
        problems.append(f'{path}:{line_of(text, match.start(1))}: "{match.group()}" is a count in a '
                        f'shape the guard cannot place, so it would never be regenerated')
    if problems:
        raise ValueError('\n'.join(problems) +
                         f'\nrun python3 tools/count_tests.py --update to fix the counts from the tree')


def require_all(root=ROOT):
    """Raise unless every guarded document matches this tree."""
    for path in guarded_documents(root):
        require_document(path, root)


def update_all(root=ROOT):
    """Rewrite every guarded document from this tree, and say which ones it touched."""
    for path in guarded_documents(root):
        update_document(path, root)
        print(f"wrote the counts in {path}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Count the tests in every suite directory.")
    parser.add_argument('--files', action='store_true', help="print a row per file instead of per suite")
    parser.add_argument('--update', nargs='?', const='all', type=pathlib.Path, metavar='DOC',
                        help="rewrite DOC's generated count block from the tree, or every guarded "
                             "document when no DOC is given")
    parser.add_argument('--check', type=pathlib.Path, metavar='DOC',
                        help="exit non-zero when DOC's count block differs from the tree")
    parser.add_argument('--claims', action='store_true',
                        help="print every count the launch guard reads from the guarded documents")
    args = parser.parse_args(argv)
    if args.claims:
        for path in guarded_documents():
            text = path.read_text(encoding='utf-8')
            for start, end, name in claims(text):
                print(f'{path}:{line_of(text, start)}  {text[start:end]}  {name}')
        return 0
    if args.update is not None:
        if str(args.update) == 'all':
            update_all()
            return 0
        path = pathlib.Path(args.update)
        if BEGIN in path.read_text(encoding='utf-8'):
            path.write_text(replace(path.read_text(encoding='utf-8')), encoding='utf-8')
            print(f"wrote the test counts in {path}")
        else:
            update_document(path)
        return 0
    if args.check:
        try:
            require_matches(pathlib.Path(args.check))
        except ValueError as problem:
            print(problem, file=sys.stderr)
            return 1
        print(f"{args.check} matches the tree")
        return 0
    suites = collect()
    print(file_rows(suites) if args.files else table(summarize(suites)))
    for suite in suites:
        for file in suite['files']:
            for test in file['tests']:
                if test['status'] == 'unclassified':
                    print(f"unclassified: {file['path']}:{test['line']}", file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
