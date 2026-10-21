import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("native_preparation", Path(__file__).with_name("prepare-native-client.py"))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class NativePreparationTest(unittest.TestCase):
    def test_rules_select_only_windows_x64(self):
        self.assertTrue(native.allowed([]))
        self.assertTrue(native.allowed([{"action": "allow", "os": {"name": "windows", "arch": "amd64"}}]))
        self.assertFalse(native.allowed([{"action": "allow", "os": {"name": "linux"}}]))
        self.assertFalse(native.allowed([{"action": "allow", "os": {"arch": "x86"}}]))

    def test_feature_arguments_are_not_enabled(self):
        self.assertFalse(native.allowed([{"action": "allow", "features": {"is_demo_user": True}}]))

    def test_artifact_path_cannot_escape_cache(self):
        for value in ("../secret", "/absolute", "C:/absolute", "a\\b"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                native.safe_relative(value)

    def test_java_argument_cannot_add_another_argument_line(self):
        for value in ('a"b', "a\nb", "a\rb"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                native.quoted(value)

    def test_unexpected_network_origins_rejected_without_fetching(self):
        for url in ("http://maven.fabricmc.net/a", "https://example.org/a", "https://user:pass@maven.fabricmc.net/a"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                native.fetch(url)

    def test_checksum_failure_never_writes_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "artifact.jar"
            with patch.object(native, "fetch", return_value=b"incorrect"):
                with self.assertRaisesRegex(ValueError, "Checksum"):
                    native.checked_file(target, "https://maven.fabricmc.net/a.jar", "0" * 40)
            self.assertFalse(target.exists())

    def test_borrowed_cache_is_verified_and_source_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / "source", Path(directory) / "copy"
            source.write_bytes(b"verified")
            digest = hashlib.sha1(b"verified").hexdigest()
            with patch.object(native, "fetch", side_effect=AssertionError("Unexpected network request")):
                native.checked_file(target, "https://maven.fabricmc.net/a.jar", digest, [source])
            self.assertEqual(source.read_bytes(), b"verified")
            self.assertEqual(target.read_bytes(), b"verified")


if __name__ == "__main__":
    unittest.main()
