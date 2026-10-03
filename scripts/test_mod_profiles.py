import copy
import importlib.util
import json
from pathlib import Path
import re
import unittest

spec = importlib.util.spec_from_file_location("prepare_profile", Path(__file__).with_name("prepare-mod-profile.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class ModProfileTest(unittest.TestCase):
    def setUp(self):
        self.pin = {"project": "project1", "version": "version1", "sha512": "a" * 128}
        self.profile = {"minecraft": "26.3", "loader": "fabric"}
        self.metadata = {"id": "version1", "project_id": "project1", "game_versions": ["26.3"],
                         "loaders": ["fabric"], "dependencies": [], "files": [{"primary": True,
                         "url": "https://cdn.modrinth.com/data/project1/versions/version1/mod.jar",
                         "hashes": {"sha512": "a" * 128}}]}

    def validate(self, metadata):
        return prepare.validate_metadata(self.pin, self.profile, metadata, {"project1"}, {"version1"})

    def test_pinned_file_accepted(self):
        self.assertEqual(self.validate(self.metadata), self.metadata["files"][0])

    def test_identity_and_target_drift_rejected(self):
        for name, wrong in [("id", "another1"), ("project_id", "another1"),
                            ("game_versions", ["26.2"]), ("loaders", ["neoforge"])]:
            with self.subTest(name=name):
                metadata = copy.deepcopy(self.metadata)
                metadata[name] = wrong
                with self.assertRaises(ValueError):
                    self.validate(metadata)

    def test_file_replacement_rejected(self):
        metadata = copy.deepcopy(self.metadata)
        metadata["files"][0]["hashes"]["sha512"] = "b" * 128
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.validate(metadata)

    def test_nonofficial_origin_and_wrong_path_rejected(self):
        for url in ["http://cdn.modrinth.com/data/project1/versions/version1/mod.jar",
                    "https://example.com/mod.jar", "https://cdn.modrinth.com/data/other/versions/version1/mod.jar"]:
            with self.subTest(url=url):
                metadata = copy.deepcopy(self.metadata)
                metadata["files"][0]["url"] = url
                with self.assertRaises(ValueError):
                    self.validate(metadata)

    def test_required_dependency_drift_rejected(self):
        for project, version in [("another", None), ("project1", "another")]:
            metadata = copy.deepcopy(self.metadata)
            metadata["dependencies"] = [{"dependency_type": "required", "project_id": project, "version_id": version}]
            with self.assertRaises(ValueError):
                self.validate(metadata)

    def test_multiple_primary_files_rejected(self):
        metadata = copy.deepcopy(self.metadata)
        metadata["files"].append(copy.deepcopy(metadata["files"][0]))
        with self.assertRaises(ValueError):
            self.validate(metadata)

    def test_repository_pins_are_complete(self):
        profiles = json.loads((Path(__file__).resolve().parents[1] / "mod-compatibility.json").read_text())
        for name, profile in profiles.items():
            pins = profile["mods"] if "mods" in profile else [profile]
            self.assertEqual(len(pins), len({pin["project"] for pin in pins}), name)
            for pin in pins:
                self.assertRegex(pin["version"], r"^[A-Za-z0-9]{8}$")
                self.assertTrue(re.fullmatch(r"[0-9a-f]{128}", pin["sha512"]), name)


if __name__ == "__main__":
    unittest.main()
