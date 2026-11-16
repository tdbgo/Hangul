import copy
import importlib.util
import json
from pathlib import Path
import re
import unittest

spec = importlib.util.spec_from_file_location("prepare_profile", Path(__file__).with_name("prepare-mod-profile.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)
runner_spec = importlib.util.spec_from_file_location("verify_profile", Path(__file__).with_name("verify-mod-compatibility.py"))
runner = importlib.util.module_from_spec(runner_spec)
runner_spec.loader.exec_module(runner)


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

    def test_release_label_must_match_pinned_identity(self):
        self.pin["release"] = "7.3.3"
        self.metadata["version_number"] = "7.3.2"
        with self.assertRaisesRegex(ValueError, "release name"):
            self.validate(self.metadata)

    def test_known_failure_does_not_accept_unexpected_errors_or_success(self):
        pin = {"expected_failure": "missing-minecraft-set-screen"}
        output = "imblocker.mixins.json:MinecraftClientMixin onScreenChanged 'setScreen' net/minecraft/client/Minecraft"
        self.assertEqual(runner.classify_imblocker(1, output, pin, False), "KNOWN_UPSTREAM_FAILURE")
        for code, text in [(0, output), (1, "another Mixin failed"), (1, output.replace("'setScreen'", "another"))]:
            with self.subTest(code=code, text=text), self.assertRaises(ValueError):
                runner.classify_imblocker(code, text, pin, False)
        with self.assertRaisesRegex(ValueError, "Hangul was loaded"):
            runner.classify_imblocker(1, output + "\n  - hangul 1.3.0-beta.12\n", pin, True)

    def test_success_requires_its_own_completed_fixture(self):
        with self.assertRaises(ValueError):
            runner.classify_imblocker(0, "BUILD SUCCESSFUL", {}, False)
        self.assertEqual(runner.classify_imblocker(0, "OPTIONAL_WIDGET_OK rei=true imblocker=true", {}, False), "PASS")
        self.assertEqual(runner.classify_imblocker(0, "THIRD_PARTY_SMOKE_OK", {}, True), "CONTROL_PASS")


if __name__ == "__main__":
    unittest.main()
