"""Fetch checksum-pinned public test mods. These files are never shipped with Hangul."""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
USER_AGENT = "Hangul-compatibility-verification/1.0 (https://github.com/tdbgo/Hangul)"


def fetch(url: str) -> bytes:
    with urlopen(Request(url, headers={"User-Agent": USER_AGENT}), timeout=40) as response:
        return response.read()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_metadata(pinned: dict, profile: dict, metadata: dict, projects: set, versions: set) -> dict:
    require(metadata["id"] == pinned["version"] and metadata["project_id"] == pinned["project"], "Pinned identity mismatch")
    require(profile["minecraft"] in metadata["game_versions"] and profile["loader"] in metadata["loaders"], "Game or loader mismatch")
    for dependency in metadata["dependencies"]:
        if dependency["dependency_type"] != "required":
            continue
        require(dependency["project_id"] in projects, "Unpinned required project dependency")
        require(not dependency["version_id"] or dependency["version_id"] in versions, "Unpinned required version dependency")
    files = [item for item in metadata["files"] if item["primary"]]
    require(len(files) == 1, "Expected one primary file")
    artifact = files[0]
    parsed = urlparse(artifact["url"])
    require(parsed.scheme == "https" and parsed.netloc == "cdn.modrinth.com", "Unexpected artifact origin")
    require(parsed.path.startswith(f'/data/{pinned["project"]}/versions/{pinned["version"]}/'), "Artifact identity mismatch")
    require(artifact["hashes"]["sha512"] == pinned["sha512"], "Published checksum differs from pin")
    return artifact


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["fabric-26.2", "fabric-26.3", "neoforge-26.3"])
    parser.add_argument("--with-imblocker", action="store_true")
    args = parser.parse_args()
    config = json.loads((ROOT / "mod-compatibility.json").read_text(encoding="utf-8"))
    profile = config[args.profile]
    selected = list(profile["mods"])
    if args.with_imblocker:
        selected.append(config["imblocker-" + profile["loader"]])
    projects = {item["project"] for item in selected}
    versions = {item["version"] for item in selected}
    pin_digest = hashlib.sha256(json.dumps(selected, sort_keys=True).encode()).hexdigest()[:12]
    destination = ROOT / ".toolchains" / "compatibility" / (args.profile + ("-imblocker" if args.with_imblocker else "") + "-" + pin_digest)
    destination.mkdir(parents=True, exist_ok=True)
    expected = set()
    for pinned in selected:
        metadata = json.loads(fetch("https://api.modrinth.com/v2/version/" + pinned["version"]))
        artifact = validate_metadata(pinned, profile, metadata, projects, versions)
        path = destination / (pinned["version"] + ".jar")
        data = path.read_bytes() if path.exists() else fetch(artifact["url"])
        require(hashlib.sha512(data).hexdigest() == pinned["sha512"], f"Checksum mismatch: {pinned['version']}")
        if not path.exists():
            path.write_bytes(data)
        expected.add(path.name)
        print("PINNED", metadata["project_id"], metadata["version_number"], path.name)
    require({path.name for path in destination.glob("*.jar")} == expected, "Unexpected JAR in profile directory")
    print("PROFILE_READY", destination)


if __name__ == "__main__":
    main()
