# SPDX-License-Identifier: MIT
"""The suite counter: what it counts, what it refuses to guess, and the README block it writes."""
import importlib.util
import pathlib
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]
ROOT = TOOLS.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


counting = load("count_tests")

ROBO = """
package demo
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
@RunWith(AndroidJUnit4::class)
class RobolectricOne {
    @Test
    fun playsThroughTheSession() { }
}
"""

PLAIN = """
package demo
import org.junit.Test
class PlainOne {
    @Test fun quiet() { }
}
"""


class Tree:
    """A throwaway checkout with the source-set layout the counter discovers."""

    def __init__(self, files):
        self.directory = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.directory.name)
        for relative, text in files.items():
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.directory.cleanup()


class KotlinCountingTest(unittest.TestCase):
    def count(self, source, name="demo/One.kt"):
        with Tree({f"app/src/test/{name}": source}) as tree:
            suites = counting.collect(tree.root)
            return [test for suite in suites for file in suite['files'] for test in file['tests']]

    def test_a_test_annotation_in_a_line_comment_is_not_a_test(self):
        tests = self.count(PLAIN + "// @Test\n// fun prose() { }\n")
        self.assertEqual([test['name'] for test in tests], ['quiet'])

    def test_a_test_annotation_inside_a_block_comment_is_not_a_test(self):
        tests = self.count(PLAIN + "/* @Test\n   fun prose() { }\n   /* nested */\n*/\n")
        self.assertEqual([test['name'] for test in tests], ['quiet'])

    def test_a_test_annotation_inside_a_string_is_not_a_test(self):
        tests = self.count(PLAIN + 'val doc = """\n@test fun prose() { }\n"""\n')
        self.assertEqual([test['name'] for test in tests], ['quiet'])

    def test_annotations_between_test_and_fun_do_not_hide_the_fun(self):
        tests = self.count(PLAIN.replace("    @Test fun quiet() { }",
                                         "    @Test\n    @Config(qualifiers = \"w411dp\")\n    fun quiet() { }"))
        self.assertEqual([test['name'] for test in tests], ['quiet'])

    def test_an_ignored_test_is_counted_and_kept_out_of_the_default_run(self):
        tests = self.count(PLAIN + """
class Ignored {
    @Test
    @Ignore("flaky on a cold cache")
    fun someday() { }
}
""")
        self.assertEqual([(test['name'], test['status']) for test in tests],
                         [('quiet', 'run'), ('someday', 'ignored')])

    def test_a_test_gated_by_an_assumption_is_opt_in_rather_than_default_run(self):
        tests = self.count(PLAIN + """
class Generator {
    @Test
    fun printTrims() {
        assumeTrue(System.getenv("NIGHTFALL_CALIBRATE") == "1")
        println("table")
    }
}
""")
        self.assertEqual([(test['name'], test['status']) for test in tests],
                         [('quiet', 'run'), ('printTrims', 'opt-in')])

    def test_an_annotation_with_no_fun_after_it_is_named_not_dropped(self):
        tests = self.count(PLAIN + """
class Broken {
    @Test
    val notAFunction = 1
}
""")
        self.assertEqual([test['status'] for test in tests], ['run', 'unclassified'])

    def test_a_file_declaring_two_runners_is_unclassified_rather_than_guessed(self):
        mixed = PLAIN + """
@RunWith(AndroidJUnit4::class)
class AlsoRobolectric {
    @Test
    fun draws() { }
}

@RunWith(JUnit4::class)
class AlsoPlain {
    @Test
    fun quietToo() { }
}
"""
        with Tree({"app/src/test/demo/Mixed.kt": mixed}) as tree:
            suites = counting.collect(tree.root)
            rows = counting.summarize(suites, tree.root)
            self.assertEqual((rows[0]['declared'], rows[0]['robolectric'], rows[0]['jvm']), (3, 0, 0))
            self.assertEqual(rows[0]['mixed_runner'], [tree.root / "app/src/test/demo/Mixed.kt"])
            self.assertIn("two runners in one file", counting.block(tree.root))

    def test_the_runner_is_read_from_the_file_that_declares_it(self):
        with Tree({"app/src/test/demo/Robo.kt": ROBO, "app/src/test/demo/Plain.kt": PLAIN}) as tree:
            row = counting.summarize(counting.collect(tree.root), tree.root)[0]
            self.assertEqual((row['declared'], row['robolectric'], row['jvm']), (2, 1, 1))


class PythonCountingTest(unittest.TestCase):
    def test_class_methods_module_functions_and_skips(self):
        with Tree({"tools/tests/test_demo.py": """
import unittest

def test_at_module_level():
    pass

def helper():
    pass

class Case(unittest.TestCase):
    def test_one(self):
        pass

    async def test_two(self):
        pass

    @unittest.skip("no network")
    def test_three(self):
        pass

    def not_a_test(self):
        pass

"""}) as tree:
            rows = counting.summarize(counting.collect(tree.root), tree.root)
            self.assertEqual((rows[0]['declared'], rows[0]['run'], rows[0]['ignored']), (4, 3, 1))
            self.assertEqual(rows[0]['unittest'], 4)


class DiscoveryTest(unittest.TestCase):
    def test_every_source_set_layout_is_found(self):
        files = {
            "core/src/test/demo/A.kt": PLAIN,
            "app/src/test/demo/B.kt": ROBO,
            "app/src/testFdroid/demo/C.kt": PLAIN,
            "app/src/androidTest/demo/D.kt": PLAIN,
            "tools/tests/test_a.py": "def test_x():\n    pass\n",
            "app/src/main/demo/E.kt": PLAIN,
        }
        with Tree(files) as tree:
            found = [suite['directory'].relative_to(tree.root).as_posix()
                     for suite in counting.collect(tree.root)]
            self.assertEqual(found, ["app/src/androidTest", "app/src/test", "app/src/testFdroid", "core/src/test", "tools/tests"])


class DocumentTest(unittest.TestCase):
    DOC = "# Counts\n\nprose\n\n<!-- test-counts:begin -->\nstale table\n<!-- test-counts:end -->\n\ntrailing prose\n"

    def test_update_fills_the_block_and_the_check_accepts_it(self):
        with Tree({"core/src/test/demo/A.kt": PLAIN, "README.md": self.DOC}) as tree:
            path = tree.root / "README.md"
            path.write_text(counting.replace(path.read_text(), tree.root))
            self.assertIn("| `core/src/test` | 1 | 1 | 0 | 0 | 0 | 1 | 0 |", path.read_text())
            self.assertTrue(path.read_text().endswith("trailing prose\n"))
            counting.require_matches(path, tree.root)

    def test_the_check_rejects_a_tree_that_gained_a_test(self):
        with Tree({"core/src/test/demo/A.kt": PLAIN, "README.md": self.DOC}) as tree:
            path = tree.root / "README.md"
            path.write_text(counting.replace(path.read_text(), tree.root))
            (tree.root / "core/src/test/demo/B.kt").write_text(PLAIN)
            with self.assertRaises(ValueError) as raised:
                counting.require_matches(path, tree.root)
            self.assertIn("--update", str(raised.exception))

    def test_a_document_with_no_markers_is_refused_rather_than_appended_to(self):
        with self.assertRaises(ValueError):
            counting.replace("# no markers here\n")


class ThisTreeTest(unittest.TestCase):
    """The repository itself: every test classifiable, and the README quoting this tree."""

    def test_no_test_in_this_tree_is_unclassified(self):
        suites = counting.collect(ROOT)
        rows = counting.summarize(suites, ROOT)
        self.assertEqual(rows[-1]['unclassified'], 0, counting.block(ROOT))
        self.assertEqual(rows[-1]['mixed_runner'], [])

    def test_every_suite_this_tree_has_is_counted(self):
        found = {suite['directory'].relative_to(ROOT).as_posix() for suite in counting.collect(ROOT)}
        for expected in ("core/src/test", "app/src/test", "app/src/testGithub", "tools/tests"):
            self.assertIn(expected, found)

    def test_readme_quotes_this_tree(self):
        counting.require_matches(ROOT / "README.md", ROOT)


if __name__ == '__main__':
    unittest.main()
