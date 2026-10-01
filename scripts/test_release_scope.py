import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("stage_release", Path(__file__).with_name("stage-release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseScopeTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.previous = self.root / "previous"
        self.previous.mkdir()
        self.versions = {"mod_version": "new", "fabric_mod_version": "new", "neoforge_mod_version": "old", "forge_mod_version": "old"}

    def jar(self, platform, version, targets, previous=False, code=b"shared-class"):
        path = (self.previous if previous else self.root) / f"hangul-{version}-{platform}.jar"
        with ZipFile(path, "w") as archive:
            if platform == "fabric":
                archive.writestr("fabric.mod.json", json.dumps({"id": "hangul", "version": version, "depends": {"minecraft": targets}}))
            else:
                archive.writestr(release.METADATA[platform], f'[[mods]]\nmodId="hangul"\nversion="{version}"\n')
            archive.writestr("kr/playcity/hangul/Example.class", code)
        return path

    def test_version_only_change_rejected(self):
        self.jar("fabric", "old", ["26.2"], previous=True)
        new = self.jar("fabric", "new", ["26.2"])
        with self.assertRaisesRegex(ValueError, "unchanged"):
            release.validate_scope([new], self.previous, self.versions)

    def test_new_game_target_accepted(self):
        self.jar("fabric", "old", ["26.2"], previous=True)
        new = self.jar("fabric", "new", ["26.2", "26.4-snapshot-2"])
        self.assertEqual([new], release.validate_scope([new], self.previous, self.versions))

    def test_unchanged_unselected_platform_rejected(self):
        other = self.jar("forge", "old", [])
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            release.validate_scope([other], self.previous, self.versions)

    def test_missing_previous_rejected(self):
        new = self.jar("fabric", "new", ["26.2"])
        with self.assertRaisesRegex(ValueError, "previous published"):
            release.validate_scope([new], self.previous, self.versions)

    def test_missing_selected_platform_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing release"):
            release.validate_scope([], self.previous, self.versions)

    def test_shared_code_change_accepted(self):
        self.jar("fabric", "old", ["26.2"], previous=True)
        new = self.jar("fabric", "new", ["26.2"], code=b"fixed-class")
        release.validate_scope([new], self.previous, self.versions)

    def test_platform_metadata_version_only_change_rejected(self):
        for platform in ("neoforge", "forge"):
            with self.subTest(platform=platform):
                self.jar(platform, "old", [], previous=True)
                new = self.jar(platform, "new", [])
                versions = dict(self.versions, fabric_mod_version="old", **{f"{platform}_mod_version": "new"})
                with self.assertRaisesRegex(ValueError, "unchanged"):
                    release.validate_scope([new], self.previous, versions)

    def test_duplicate_platform_rejected(self):
        self.jar("fabric", "old", ["26.2"], previous=True)
        new = self.jar("fabric", "new", ["26.3"])
        with self.assertRaisesRegex(ValueError, "duplicate"):
            release.validate_scope([new, new], self.previous, self.versions)


if __name__ == "__main__":
    unittest.main()
