"""Report upstream compatibility changes without editing or publishing the project."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import urllib.request
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
URLS = {
    "minecraft": "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
    "fabric": "https://meta.fabricmc.net/v2/versions/loader",
    "quilt": "https://meta.quiltmc.org/v3/versions/loader",
    "neoforge": "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
    "forge": "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
    "published": "https://api.modrinth.com/v2/project/hangul/version",
}


def upstream(url):
    request = urllib.request.Request(url, headers={"User-Agent": "Hangul-compatibility-check"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def loader_key(version):
    core, _, suffix = version.partition("-")
    return tuple(int(part) for part in core.split(".")), not suffix, suffix


def analyze(data, declared_games, matrix, properties):
    latest = data["minecraft"]["latest"]
    preview_base = latest["snapshot"].split("-")[0]
    games = [item["id"] for item in data["minecraft"]["versions"]
             if item["id"] == latest["release"] or item["id"].startswith(preview_base + "-")]
    published = next((version for version in data["published"] if "fabric" in version["loaders"]), None)
    changes = {"undeclared_games": [game for game in games if game not in declared_games],
               "unpublished_fabric_games": [game for game in games if not published or game not in published["game_versions"]],
               "neoforge": [], "forge": []}
    neo_games = {target["minecraft"] for target in matrix["neoforge"]}
    for version in data["neoforge"]:
        match = re.fullmatch(r"(26\.\d+)\.(\d+)\.\d+(?:-[A-Za-z0-9.-]+)?", version)
        if match:
            neo_games.add(match[1] + ("." + match[2] if match[2] != "0" else ""))
    for game in sorted(neo_games):
        prefix = game + "." if game.count(".") == 2 else game + ".0."
        versions = [version for version in data["neoforge"] if version.startswith(prefix)]
        if not versions:
            raise ValueError("No NeoForge metadata for declared game " + game)
        newest = max(versions, key=loader_key)
        if not any(target["minecraft"] == game and target["loader"] == newest for target in matrix["neoforge"]):
            changes["neoforge"].append({"minecraft": game, "loader": newest})
    forge_games = {target["minecraft"] for target in matrix["forge"]}
    forge_games.update(key.removesuffix("-latest") for key in data["forge"]["promos"]
                       if re.fullmatch(r"26\.\d+(?:\.\d+)?-latest", key))
    for game in sorted(forge_games):
        newest = data["forge"]["promos"].get(game + "-latest")
        if not newest:
            raise ValueError("No Forge metadata for declared game " + game)
        if not any(target["minecraft"] == game and target["loader"] == game + "-" + newest for target in matrix["forge"]):
            changes["forge"].append({"minecraft": game, "loader": game + "-" + newest})
    stable_fabric = next(version["version"] for version in data["fabric"] if version["stable"])
    loader_updates = {}
    for key, version in (("loader_version", stable_fabric), ("preview_loader_version", data["fabric"][0]["version"]),
                         ("quilt_version", data["quilt"][0]["version"])):
        if properties.get(key) != version:
            loader_updates[key] = version
    changes["loader_updates"] = loader_updates
    return {"latest_minecraft": latest, "latest_stable_fabric": stable_fabric,
            "published_fabric": published["version_number"] if published else None,
            "changes": changes, "update_available": any(changes.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    data = {key: json.loads(upstream(url)) for key, url in URLS.items() if key != "neoforge"}
    data["neoforge"] = [node.text for node in ET.fromstring(upstream(URLS["neoforge"])).findall("./versioning/versions/version")]
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
                      if "=" in line and not line.startswith("#"))
    games = json.loads((ROOT / "src/main/resources/fabric.mod.json").read_text(encoding="utf-8"))["custom"]["hangul:tested_game_versions"]
    matrix = json.loads((ROOT / "platform-matrix.json").read_text(encoding="utf-8"))
    report = analyze(data, games, matrix, properties)
    report["checked_at"] = datetime.now(timezone.utc).isoformat()
    report["sources"] = URLS
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8", newline="\n")
    print(text, end="")


if __name__ == "__main__":
    main()
