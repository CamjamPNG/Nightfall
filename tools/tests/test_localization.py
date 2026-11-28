# SPDX-License-Identifier: MIT
"""`values-es/` must say exactly what `values/` says, and the guard must say so when it does not.

The defect these hold: a locale is added once and then rots, because nothing in the build compares it
with the English it came from. Every case below is a way that happens — an English string added and
never translated, a translation left behind by a rename, a placeholder that moved while the sentence
was reordered, a plural the language does not inflect — and each one is planted in a throwaway res
tree so the failure is observed rather than assumed.
"""
import importlib.util
import pathlib
import tempfile
import unittest
import xml.etree.ElementTree as ET

TOOLS = pathlib.Path(__file__).resolve().parents[1]


def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


localization = load('check_localization')

VALUES = '<?xml version="1.0" encoding="utf-8"?><resources>%s</resources>'


class Fixture(unittest.TestCase):
    """A res tree of two files per locale, written from strings, so a mutation is one line."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.res = pathlib.Path(self.temporary.name) / 'res'

    def tearDown(self):
        self.temporary.cleanup()

    def write(self, locale, body, file='strings.xml'):
        directory = self.res / ('values' if locale == 'values' else f'values-{locale}')
        directory.mkdir(parents=True, exist_ok=True)
        (directory / file).write_text(VALUES % body, encoding='utf-8')

    def problems(self, locale='es'):
        return localization.check(self.res, locale)[0]

    def test_a_complete_translation_passes(self):
        self.write('values', '<string name="a">One layer</string><string name="b">%1$s and %2$d</string>')
        self.write('es', '<string name="a">Una capa</string><string name="b">%1$s y %2$d</string>')
        self.assertEqual(self.problems(), [])

    def test_an_english_string_with_no_translation_fails(self):
        self.write('values', '<string name="a">One layer</string><string name="b">A new sentence</string>')
        self.write('es', '<string name="a">Una capa</string>')
        self.assertEqual(self.problems(), ['es: missing b'])

    def test_a_translation_with_no_english_key_fails(self):
        self.write('values', '<string name="a">One layer</string>')
        self.write('es', '<string name="a">Una capa</string><string name="gone">Ya no existe</string>')
        self.assertEqual(self.problems(), ['es: gone is not in values/'])

    def test_a_moved_placeholder_fails(self):
        self.write('values', '<string name="b">Remove %1$s from the mix, %2$d percent</string>')
        self.write('es', '<string name="b">Quitar %2$d de la mezcla, %1$s por ciento</string>')
        self.assertEqual(self.problems(), [
            "es: b placeholders are ['%2$d', '%1$s'], values/ has ['%1$s', '%2$d']",
        ])

    def test_a_changed_placeholder_type_fails(self):
        self.write('values', '<string name="b">%1$d seconds</string>')
        self.write('es', '<string name="b">%1$s segundos</string>')
        self.assertEqual(self.problems(), [
            "es: b placeholders are ['%1$s'], values/ has ['%1$d']",
        ])

    def test_a_dropped_literal_percent_fails(self):
        self.write('values', '<string name="b">%1$d%% of buffer</string>')
        self.write('es', '<string name="b">%1$d de búfer</string>')
        self.assertEqual(self.problems(), [
            "es: b placeholders are ['%1$d'], values/ has ['%1$d', '%%']",
        ])

    def test_the_wrong_resource_kind_fails(self):
        self.write('values', '<string name="a">One layer</string>')
        self.write('es', '<string-array name="a"><item>Una capa</item></string-array>')
        self.assertEqual(self.problems(), ['es: a is a array where values/ has a string'])

    def test_an_untranslatable_key_is_neither_required_nor_allowed(self):
        self.write('values', '<string name="brand" translatable="false">Nightfall</string>')
        self.write('es', '')
        self.assertEqual(self.problems(), [])
        self.write('es', '<string name="brand">Nightfall</string>')
        self.assertEqual(self.problems(), [
            'es: brand is marked translatable="false" in values/, so es must not translate it'])

    def test_a_plural_missing_the_other_category_fails(self):
        self.write('values', '', 'plurals.xml')
        self.write('es', '', 'plurals.xml')
        self.write('values', '<plurals name="p"><item quantity="one">%1$d layer</item>'
                             '<item quantity="other">%1$d layers</item></plurals>', 'plurals.xml')
        self.write('es', '<plurals name="p"><item quantity="one">%1$d capa</item></plurals>', 'plurals.xml')
        self.assertIn('es: p is missing plural category "other"', self.problems())

    def test_a_plural_category_the_language_lacks_fails(self):
        self.write('values', '<plurals name="p"><item quantity="one">%1$d layer</item>'
                             '<item quantity="other">%1$d layers</item></plurals>', 'plurals.xml')
        self.write('es', '<plurals name="p"><item quantity="one">%1$d capa</item>'
                         '<item quantity="other">%1$d capas</item>'
                         '<item quantity="many">%1$d de capas</item></plurals>', 'plurals.xml')
        self.assertEqual(self.problems(), ['es: p declares plural category "many", which values/ does not'])

    def test_a_plural_that_drops_its_count_fails(self):
        self.write('values', '<plurals name="p"><item quantity="one">%1$d layer</item>'
                             '<item quantity="other">%1$d layers</item></plurals>', 'plurals.xml')
        self.write('es', '<plurals name="p"><item quantity="one">Una capa</item>'
                         '<item quantity="other">%1$d capas</item></plurals>', 'plurals.xml')
        self.assertIn('es: p plural "one" drops the count placeholder', self.problems())

    def test_an_array_with_a_different_length_fails(self):
        self.write('values', '<string-array name="t"><item>Soft pad</item><item>Bells</item></string-array>')
        self.write('es', '<string-array name="t"><item>Pad suave</item></string-array>')
        self.assertEqual(self.problems(), ['es: t has 1 items where values/ has 2'])

    def test_a_missing_locale_is_named_rather_than_ignored(self):
        self.write('values', '<string name="a">One layer</string>')
        problems, covered = localization.check(self.res, 'es')
        self.assertEqual(covered, 1)
        self.assertEqual(len(problems), 1)
        self.assertIn('no values-es/ at all', problems[0])


class RealTreeTest(unittest.TestCase):
    """The guard is pointed at this checkout, and it covers every translatable resource in it."""

    def translatable(self):
        counted = 0
        for res in localization.resource_dirs():
            for file in ('strings.xml', 'plurals.xml'):
                path = res / 'values' / file
                if not path.exists():
                    continue
                for node in ET.parse(path).getroot():
                    if node.tag in ('string', 'string-array', 'plurals') and node.get('translatable') != 'false':
                        counted += 1
        return counted

    def test_the_checkout_is_fully_translated(self):
        trees, keys, plurals = localization.require_all()
        self.assertEqual(trees, 2, 'main and the github flavour overlay are the two res trees with strings')
        self.assertEqual(keys, self.translatable(), 'the guard must cover every translatable resource, not a subset')
        self.assertEqual(plurals, 9, 'values/ declares nine plural resources')

    def test_a_planted_missing_key_fails_on_the_real_tree(self):
        source = TOOLS.parent / 'app/src/main/res/values-es/strings.xml'
        translated = source.read_text(encoding='utf-8')
        self.assertIn('name="home_tagline"', translated)
        with tempfile.TemporaryDirectory() as temporary:
            res = pathlib.Path(temporary) / 'res'
            for locale, body in (('values', None), ('es', translated.replace(
                    [line for line in translated.splitlines() if 'name="home_tagline"' in line][0] + '\n', ''))):
                directory = res / ('values' if locale == 'values' else 'values-es')
                directory.mkdir(parents=True)
                if body is None:
                    for file in ('strings.xml', 'plurals.xml'):
                        (directory / file).write_bytes((TOOLS.parent / 'app/src/main/res/values' / file).read_bytes())
                else:
                    (directory / 'strings.xml').write_text(body, encoding='utf-8')
                    (directory / 'plurals.xml').write_bytes(
                        (TOOLS.parent / 'app/src/main/res/values-es/plurals.xml').read_bytes())
            problems, _ = localization.check(res)
        self.assertEqual(problems, ['es: missing home_tagline'])


if __name__ == '__main__':
    unittest.main()
