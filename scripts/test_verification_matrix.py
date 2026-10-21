import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location("verification_matrix", Path(__file__).with_name("verification-matrix.py"))
matrix = importlib.util.module_from_spec(spec)
spec.loader.exec_module(matrix)


class VerificationMatrixTest(unittest.TestCase):
    def setUp(self):
        self.games = ["26.1", "26.1.2", "26.2", "26.3-snapshot-1", "26.3-pre-1", "26.3-pre-2", "26.3",
                      "26.4-snapshot-3", "26.4-snapshot-4", "26.4-snapshot-5"]
        self.metadata = {"custom": {"hangul:tested_game_versions": self.games}}
        self.properties = {"loader_version": "0.19.5", "loader_api_version": "0.19.3", "preview_loader_version": "0.20.1"}
        self.platforms = {name: [{"minecraft": "26.3", "loader": "old"}, {"minecraft": "26.3", "loader": "new"}]
                          for name in ("neoforge", "forge")}

    def test_new_snapshots_reach_windows_and_minimum_loader(self):
        result = matrix.build_matrix(self.metadata, self.properties, self.platforms)
        for game in ("26.4-snapshot-4", "26.4-snapshot-5"):
            for loader, os_name in (("0.19.5", "ubuntu-24.04"), ("0.19.5", "windows-2025"), ("0.19.3", "ubuntu-24.04")):
                self.assertTrue(any(item["minecraft"] == game and item["loader"] == loader and item["os"] == os_name
                                    for item in result["fabric"]["include"]))

    def test_preview_is_separate_and_never_runs_quilt(self):
        result = matrix.build_matrix(self.metadata, self.properties, self.platforms)
        preview = [item for item in result["fabric"]["include"] if item["tier"] == "preview"]
        self.assertEqual({item["minecraft"] for item in preview}, {"26.1", "26.3", "26.4-snapshot-4", "26.4-snapshot-5"})
        self.assertTrue(all(not item["quilt"] for item in preview))

    def test_groups_keep_every_exact_loader_without_duplication(self):
        result = matrix.build_matrix(self.metadata, self.properties, self.platforms)
        self.assertEqual(len(result["platforms"]["include"]), 2)
        self.assertTrue(all(len(item["targets"]) == 2 for item in result["platforms"]["include"]))

    def test_latest_prerelease_only_in_representative_set(self):
        selected = matrix.representatives(self.games)
        self.assertIn("26.3-pre-2", selected)
        self.assertNotIn("26.3-pre-1", selected)
        self.assertNotIn("26.3-snapshot-1", selected)

    def test_duplicate_game_rejected(self):
        self.games.append("26.3")
        with self.assertRaisesRegex(ValueError, "unique"):
            matrix.build_matrix(self.metadata, self.properties, self.platforms)

    def test_duplicate_platform_pair_rejected(self):
        self.platforms["forge"].append(self.platforms["forge"][0])
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            matrix.build_matrix(self.metadata, self.properties, self.platforms)

    def test_undeclared_platform_game_rejected(self):
        self.platforms["forge"][0] = {"minecraft": "26.5", "loader": "new"}
        with self.assertRaisesRegex(ValueError, "undeclared"):
            matrix.build_matrix(self.metadata, self.properties, self.platforms)

    def test_repository_matrix_matches_descriptors(self):
        result = matrix.read_matrix()
        jobs = result["fabric"]["include"]
        keys = [(item["minecraft"], item["loader"], item["os"]) for item in jobs]
        self.assertEqual(len(keys), len(set(keys)))


if __name__ == "__main__":
    unittest.main()
