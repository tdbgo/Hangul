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

    def test_archive_order_and_text_line_endings_ignored(self):
        old = self.jar("fabric", "old", ["26.2"], previous=True)
        with ZipFile(old, "a") as archive:
            archive.writestr("LICENSE_hangul", "license\r\n")
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nFabric-Loom-Client-Only-Entries: b;a\r\n")
        new = self.root / "hangul-new-fabric.jar"
        with ZipFile(new, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Fabric-Loom-Client-Only-Entries: a;b\nManifest-Version: 1.0\n")
            archive.writestr("LICENSE_hangul", "license\n")
            archive.writestr("kr/playcity/hangul/Example.class", b"shared-class")
            archive.writestr("fabric.mod.json", json.dumps({"version": "new", "depends": {"minecraft": ["26.2"]}, "id": "hangul"}))
        with self.assertRaisesRegex(ValueError, "unchanged"):
            release.validate_scope([new], self.previous, self.versions)

    def test_required_resource_change_accepted(self):
        old = self.jar("fabric", "old", ["26.2"], previous=True)
        new = self.jar("fabric", "new", ["26.2"])
        with ZipFile(old, "a") as archive:
            archive.writestr("assets/hangul/icon.png", b"old-icon")
        with ZipFile(new, "a") as archive:
            archive.writestr("assets/hangul/icon.png", b"new-icon")
        release.validate_scope([new], self.previous, self.versions)

    def test_wrong_platform_version_rejected(self):
        new = self.jar("fabric", "wrong", ["26.2"])
        with self.assertRaisesRegex(ValueError, "artifact version"):
            release.validate_scope([new], self.previous, self.versions)

    def test_empty_release_selection_rejected(self):
        with self.assertRaisesRegex(ValueError, "No platform"):
            release.validate_scope([], self.previous, dict(self.versions, mod_version="unselected"))


if __name__ == "__main__":
    unittest.main()
