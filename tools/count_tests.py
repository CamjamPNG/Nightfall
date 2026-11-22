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
    body = [BEGIN + " (generated by `python3 tools/count_tests.py --update README.md`; do not edit by hand) -->",
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


def main(argv=None):
    parser = argparse.ArgumentParser(description="Count the tests in every suite directory.")
    parser.add_argument('--files', action='store_true', help="print a row per file instead of per suite")
    parser.add_argument('--update', type=pathlib.Path, metavar='DOC',
                        help="rewrite DOC's generated count block from the tree")
    parser.add_argument('--check', type=pathlib.Path, metavar='DOC',
                        help="exit non-zero when DOC's count block differs from the tree")
    args = parser.parse_args(argv)
    if args.update:
        path = pathlib.Path(args.update)
        path.write_text(replace(path.read_text(encoding='utf-8')), encoding='utf-8')
        print(f"wrote the test counts in {path}")
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
