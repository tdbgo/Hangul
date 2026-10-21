"""Derive local and CI verification targets from the declared compatibility data."""
import argparse
from collections import defaultdict
import json
import os
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]


def version_key(value):
    return tuple(int(part) if part.isdigit() else part for part in re.split(r"(\d+)", value))


def representatives(games):
    """Keep base releases, the last pre-release, and snapshots without a stable release."""
    stable = {game for game in games if re.fullmatch(r"\d+\.\d+", game)}
    prereleases = defaultdict(list)
    result = set(stable)
    for game in games:
        if "-pre-" in game:
            prereleases[game.split("-pre-")[0]].append(game)
        elif "-snapshot-" in game and game.split("-snapshot-")[0] not in stable:
            result.add(game)
    for targets in prereleases.values():
        result.add(max(targets, key=version_key))
    return [game for game in games if game in result]


def build_matrix(metadata, properties, platforms):
    games = metadata["custom"]["hangul:tested_game_versions"]
    if not games or len(games) != len(set(games)):
        raise ValueError("Game targets must be nonempty and unique")
    stable_loader = properties["loader_version"]
    minimum_loader = properties["loader_api_version"]
    representative_games = representatives(games)
    fabric = [dict(minecraft=game, loader=stable_loader, os="ubuntu-24.04", quilt=True, tier="stable") for game in games]
    fabric += [dict(minecraft=game, loader=stable_loader, os="windows-2025", quilt=True, tier="stable") for game in representative_games]
    if minimum_loader != stable_loader:
        fabric += [dict(minecraft=game, loader=minimum_loader, os="ubuntu-24.04", quilt=False, tier="minimum") for game in representative_games]
    preview_loader = properties.get("preview_loader_version")
    if preview_loader and preview_loader not in (stable_loader, minimum_loader):
        releases = [game for game in games if re.fullmatch(r"\d+\.\d+", game)]
        snapshots = [game for game in representative_games if "-snapshot-" in game]
        preview_games = list(dict.fromkeys(releases[:1] + releases[-1:] + snapshots[-2:]))
        fabric += [dict(minecraft=game, loader=preview_loader, os=os_name, quilt=False, tier="preview")
                   for game in preview_games for os_name in ("ubuntu-24.04", "windows-2025")]
    grouped = []
    for platform in ("neoforge", "forge"):
        seen = set()
        by_game = defaultdict(list)
        for target in platforms[platform]:
            pair = (target["minecraft"], target["loader"])
            if pair in seen or target["minecraft"] not in games:
                raise ValueError("Duplicate or undeclared platform target")
            seen.add(pair)
            by_game[target["minecraft"]].append(target)
        grouped += [dict(platform=platform, minecraft=game, targets=targets) for game, targets in by_game.items()]
    return {"fabric": {"include": fabric}, "platforms": {"include": grouped},
            "representatives": representative_games,
            "preview": [item for item in fabric if item["tier"] == "preview" and item["os"] == "windows-2025"]}


def read_matrix(root=ROOT):
    properties = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.startswith("#"))
    return build_matrix(json.loads((root / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8")),
                        properties, json.loads((root / "platform-matrix.json").read_text(encoding="utf-8")))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--github-output", action="store_true")
    args = parser.parse_args()
    matrix = read_matrix()
    if args.github_output:
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for name in ("fabric", "platforms"):
                output.write(name + "=" + json.dumps(matrix[name], separators=(",", ":")) + "\n")
    else:
        print(json.dumps(matrix, separators=(",", ":")))


if __name__ == "__main__":
    main()
