"""Stage only changed platform artifacts; reject version-only republication."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tomllib
from zipfile import ZipFile


PLATFORMS = ("fabric", "neoforge", "forge")
METADATA = {"fabric": "fabric.mod.json", "neoforge": "META-INF/neoforge.mods.toml", "forge": "META-INF/mods.toml"}


def properties(path: Path) -> dict[str, str]:
    return dict(line.split("=", 1) for line in path.read_text().splitlines() if "=" in line and not line.startswith("#"))


def identity(path: Path) -> tuple[str, str]:
    with ZipFile(path) as archive:
        matches = [platform for platform, name in METADATA.items() if name in archive.namelist()]
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one platform descriptor: {path.name}")
        platform = matches[0]
        text = archive.read(METADATA[platform]).decode("utf-8")
        mod = json.loads(text) if platform == "fabric" else tomllib.loads(text)
        version = mod["version"] if platform == "fabric" else mod["mods"][0]["version"]
        return platform, version


def content_fingerprint(path: Path) -> dict[str, str]:
    """Ignore archive layout, text line endings and the mod's version field only."""
    result = {}
    with ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith("/"):
                continue
            data = archive.read(name)
            if name == METADATA["fabric"]:
                mod = json.loads(data)
                mod.pop("version")
                data = json.dumps(mod, sort_keys=True, ensure_ascii=False).encode()
            elif name in (METADATA["neoforge"], METADATA["forge"]):
                mod = tomllib.loads(data.decode("utf-8"))
                for entry in mod["mods"]:
                    entry.pop("version")
                data = json.dumps(mod, sort_keys=True, ensure_ascii=False).encode()
            elif name == "META-INF/MANIFEST.MF":
                # Unfold and sort attributes; Loom's client-only list is unordered.
                lines = data.decode("utf-8").replace("\r\n", "\n").replace("\n ", "").splitlines()
                attributes = []
                for line in lines:
                    if not line:
                        continue
                    key, value = line.split(": ", 1)
                    if key == "Fabric-Loom-Client-Only-Entries":
                        value = ";".join(sorted(value.split(";")))
                    attributes.append((key, value))
                data = json.dumps(sorted(attributes)).encode()
            elif name.endswith((".json", ".toml", ".mcmeta")) or name.lower().startswith("license"):
                data = data.replace(b"\r\n", b"\n")
            result[name] = hashlib.sha256(data).hexdigest()
    return result


def validate_scope(jars: list[Path], previous_dir: Path, versions: dict[str, str]) -> list[Path]:
    expected = {platform for platform in PLATFORMS if versions[f"{platform}_mod_version"] == versions["mod_version"]}
    if not expected:
        raise ValueError("No platform selected for this release")
    seen = set()
    for jar in jars:
        platform, version = identity(jar)
        if platform not in expected or platform in seen:
            raise ValueError(f"Unexpected or duplicate release platform: {platform}")
        if version != versions[f"{platform}_mod_version"]:
            raise ValueError(f"Unexpected {platform} artifact version: {version}")
        seen.add(platform)
        previous = list(previous_dir.glob(f"*-{platform}.jar"))
        if len(previous) != 1:
            raise ValueError(f"Provide exactly one previous published {platform} JAR")
        old_platform, old_version = identity(previous[0])
        if old_platform != platform or old_version == version:
            raise ValueError(f"Invalid previous {platform} artifact")
        if content_fingerprint(jar) == content_fingerprint(previous[0]):
            raise ValueError(f"Rejecting unchanged {platform} artifact: only its version or archive layout changed")
    if seen != expected:
        raise ValueError(f"Missing release platforms: {sorted(expected - seen)}")
    return jars


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jars", nargs="+", type=Path)
    parser.add_argument("--previous-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    versions = properties(Path(__file__).resolve().parents[1] / "gradle.properties")
    jars = validate_scope(args.jars, args.previous_dir, versions)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    # A stale artifact must never silently become part of this release bundle.
    expected_names = {jar.name for jar in jars} | {"SHA256SUMS.txt"}
    if any(path.name not in expected_names for path in args.output_dir.iterdir()):
        raise ValueError("Output directory contains files outside this release scope")
    sums = []
    for jar in jars:
        digest = hashlib.sha256(jar.read_bytes()).hexdigest()
        destination = args.output_dir / jar.name
        if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Refusing to overwrite a different artifact: {destination.name}")
        shutil.copyfile(jar, destination)
        sums.append(f"{digest}  {jar.name}\n")
    (args.output_dir / "SHA256SUMS.txt").write_text("".join(sums), encoding="utf-8", newline="\n")
    print(f"Staged changed platforms only: {', '.join(identity(jar)[0] for jar in jars)}")


if __name__ == "__main__":
    main()
