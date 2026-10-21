import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location("check_updates", Path(__file__).with_name("check-updates.py"))
updates = importlib.util.module_from_spec(spec)
spec.loader.exec_module(updates)


class UpdateDetectionTest(unittest.TestCase):
    def setUp(self):
        self.games = ["26.3", "26.4-snapshot-4"]
        self.matrix = {"neoforge": [{"minecraft": "26.3", "loader": "26.3.0.95"}],
                       "forge": [{"minecraft": "26.3", "loader": "26.3-66.1.0"}]}
        self.properties = {"loader_version": "0.19.5", "preview_loader_version": "0.20.1", "quilt_version": "0.31.0-beta.4"}
        self.data = {"minecraft": {"latest": {"release": "26.3", "snapshot": "26.4-snapshot-5"},
                                   "versions": [{"id": "26.4-snapshot-5"}, {"id": "26.4-snapshot-4"}, {"id": "26.3"}]},
                     "fabric": [{"version": "0.20.1", "stable": False}, {"version": "0.19.5", "stable": True}],
                     "quilt": [{"version": "0.31.0-beta.4"}], "neoforge": ["26.3.0.95"],
                     "forge": {"promos": {"26.3-latest": "66.1.0"}},
                     "published": [{"loaders": ["fabric"], "version_number": "beta.10", "game_versions": ["26.3"]}]}

    def analyze(self):
        return updates.analyze(self.data, self.games, self.matrix, self.properties)

    def test_development_and_publication_gaps_stay_separate(self):
        changes = self.analyze()["changes"]
        self.assertEqual(changes["undeclared_games"], ["26.4-snapshot-5"])
        self.assertEqual(changes["unpublished_fabric_games"], ["26.4-snapshot-5", "26.4-snapshot-4"])

    def test_preview_loader_is_not_a_stable_update(self):
        self.assertEqual(self.analyze()["latest_stable_fabric"], "0.19.5")
        self.assertFalse(self.analyze()["changes"]["loader_updates"])

    def test_new_loader_pair_is_reported(self):
        self.data["neoforge"].append("26.3.0.96")
        self.assertEqual(self.analyze()["changes"]["neoforge"], [{"minecraft": "26.3", "loader": "26.3.0.96"}])

    def test_complete_coverage_has_no_update(self):
        self.games.append("26.4-snapshot-5")
        self.data["published"][0]["game_versions"] = self.games[:]
        self.assertFalse(self.analyze()["update_available"])

    def test_missing_declared_loader_metadata_fails(self):
        self.data["neoforge"] = []
        with self.assertRaisesRegex(ValueError, "No NeoForge"):
            self.analyze()

    def test_maven_order_does_not_hide_newest_loader(self):
        self.data["neoforge"] = ["26.3.0.96", "26.3.0.9", "26.3.0.95"]
        self.assertEqual(self.analyze()["changes"]["neoforge"], [{"minecraft": "26.3", "loader": "26.3.0.96"}])

    def test_new_platform_game_is_reported_without_declaring_support(self):
        self.data["neoforge"].append("26.4.0.1-beta")
        self.data["forge"]["promos"]["26.4-latest"] = "67.0.1"
        changes = self.analyze()["changes"]
        self.assertEqual(changes["neoforge"], [{"minecraft": "26.4", "loader": "26.4.0.1-beta"}])
        self.assertEqual(changes["forge"], [{"minecraft": "26.4", "loader": "26.4-67.0.1"}])
        self.assertNotIn("26.4", self.games)

    def test_patch_game_and_stable_loader_sort_correctly(self):
        self.data["neoforge"] += ["26.1.2.121", "26.1.2.122-beta", "26.1.2.122"]
        self.assertEqual(self.analyze()["changes"]["neoforge"], [{"minecraft": "26.1.2", "loader": "26.1.2.122"}])


if __name__ == "__main__":
    unittest.main()
