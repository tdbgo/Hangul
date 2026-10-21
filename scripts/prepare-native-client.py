"""Prepare an isolated Windows client from checksum-verified official artifacts."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import shutil
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parents[1]
HOSTS = {"piston-meta.mojang.com", "piston-data.mojang.com", "resources.download.minecraft.net",
         "libraries.minecraft.net", "meta.fabricmc.net", "maven.fabricmc.net"}


def fetch(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname not in HOSTS or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Unexpected download origin")
    request = urllib.request.Request(url, headers={"User-Agent": "Hangul-compatibility-check"})
    with urllib.request.urlopen(request, timeout=45) as response:
        if urllib.parse.urlsplit(response.url).hostname not in HOSTS:
            raise ValueError("Unexpected redirect origin")
        return response.read()


def checked_file(path, url, digest, borrow=()):
    if not re.fullmatch(r"[0-9a-f]{40}", digest):
        raise ValueError("Missing SHA-1 checksum")
    for existing in (path, *borrow):
        if not existing.is_file():
            continue
        with existing.open("rb") as stream:
            matches = hashlib.file_digest(stream, "sha1").hexdigest() == digest
        if matches:
            if existing != path:
                path.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(existing, path)
            return path
    data = fetch(url)
    if hashlib.sha1(data).hexdigest() != digest:
        raise ValueError("Checksum mismatch: " + path.name)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return path


def safe_relative(value):
    path = Path(value)
    if path.anchor or path.is_absolute() or ".." in path.parts or "\\" in value or ":" in value:
        raise ValueError("Unsafe artifact path")
    return path


def allowed(rules):
    result = not rules
    for rule in rules:
        os_rule = rule.get("os", {})
        matches = os_rule.get("name", "windows") == "windows"
        if "arch" in os_rule:
            matches = matches and os_rule["arch"] in ("amd64", "x86_64")
        if rule.get("features") or "version" in os_rule or "versionRange" in os_rule:
            matches = False
        if matches:
            result = rule["action"] == "allow"
    return result


def quoted(value):
    if any(char in value for char in ("\n", "\r", '"')):
        raise ValueError("Unsupported Java argument")
    return '"' + value.replace("\\", "/") + '"'


def prepare(game, loader, name, candidate, expected_hash):
    if not re.fullmatch(r"[0-9A-Za-z.-]+", game) or not re.fullmatch(r"[0-9A-Za-z.-]+", loader) or not re.fullmatch(r"[a-z0-9-]+", name):
        raise ValueError("Invalid client identity")
    if hashlib.sha256(candidate.read_bytes()).hexdigest() != expected_hash:
        raise ValueError("Candidate hash mismatch")
    cache = ROOT / ".toolchains/native-client-cache"
    instance = ROOT / ".toolchains/native-clients" / (game + "-" + loader + "-" + name)
    instance.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    entry = next(item for item in manifest["versions"] if item["id"] == game)
    metadata_path = checked_file(cache / "versions" / (game + ".json"), entry["url"], entry["sha1"])
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    profile = json.loads(fetch(f"https://meta.fabricmc.net/v2/versions/loader/{game}/{loader}/profile/json"))
    if profile["inheritsFrom"] != game or profile["mainClass"] != "net.fabricmc.loader.impl.launch.knot.KnotClient":
        raise ValueError("Unexpected Fabric profile")
    libraries = []
    for item in metadata["libraries"]:
        if not allowed(item.get("rules", [])):
            continue
        artifact = item.get("downloads", {}).get("artifact")
        if artifact:
            libraries.append((item["name"], artifact))
    loader_names = set()
    for item in profile["libraries"]:
        parts = item["name"].split(":")
        if len(parts) != 3:
            raise ValueError("Unsupported loader coordinate")
        group, artifact, version = parts
        relative = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"
        url = item["url"].rstrip("/") + "/" + relative
        digest = item.get("sha1") or fetch(url + ".sha1").decode().split()[0]
        loader_names.add(":".join(parts[:2]))
        libraries.append((item["name"], {"path": relative, "url": url, "sha1": digest}))
    # A loader's own ASM/Mixin versions replace any duplicate game coordinates.
    libraries = [(name, item) for name, item in libraries
                 if ":".join(name.split(":")[:2]) not in loader_names or name in {lib["name"] for lib in profile["libraries"]}]
    classpath = []
    for coordinate, artifact in libraries:
        relative = safe_relative(artifact["path"])
        parts = coordinate.split(":")
        gradle_cache = Path.home() / ".gradle/caches/modules-2/files-2.1" / parts[0] / parts[1] / parts[2]
        borrowed = list(gradle_cache.glob("*/" + relative.name)) if gradle_cache.is_dir() else []
        classpath.append(checked_file(cache / "libraries" / relative, artifact["url"], artifact["sha1"], borrowed))
    client = metadata["downloads"]["client"]
    classpath.append(checked_file(cache / "versions" / (game + ".jar"), client["url"], client["sha1"]))
    index = metadata["assetIndex"]
    index_path = checked_file(cache / "assets/indexes" / (index["id"] + ".json"), index["url"], index["sha1"])
    objects = json.loads(index_path.read_text(encoding="utf-8"))["objects"]
    hashes = sorted({item["hash"] for item in objects.values()})
    def asset(digest):
        relative = Path(digest[:2]) / digest
        existing = Path.home() / "AppData/Roaming/.minecraft/assets/objects" / relative
        return checked_file(cache / "assets/objects" / relative,
                            "https://resources.download.minecraft.net/" + relative.as_posix(), digest, [existing])
    with ThreadPoolExecutor(max_workers=12) as pool:
        list(pool.map(asset, hashes))
    mods = instance / "mods"
    mods.mkdir(exist_ok=True)
    if any(path.name != "hangul.jar" for path in mods.iterdir()):
        raise ValueError("Unexpected mod in isolated client")
    shutil.copyfile(candidate, mods / "hangul.jar")
    native_root = instance / "natives"
    native_root.mkdir(exist_ok=True)
    args = ["-Xms512M", "-Xmx2G", "--enable-native-access=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
            "-Djava.library.path=" + str(native_root / "java"), "-Djna.tmpdir=" + str(native_root / "jna"),
            "-Dorg.lwjgl.system.SharedLibraryExtractPath=" + str(native_root / "lwjgl"),
            "-Dio.netty.native.workdir=" + str(native_root / "netty"), "-Dfabric.gameVersion=" + game]
    args += profile.get("arguments", {}).get("jvm", [])
    args += ["-cp", ";".join(str(path) for path in classpath), profile["mainClass"],
             "--username", "HangulTest", "--version", game, "--gameDir", str(instance),
             "--assetsDir", str(cache / "assets"), "--assetIndex", index["id"],
             "--uuid", "00000000000000000000000000000001", "--accessToken", "0", "--versionType", "snapshot",
             "--width", "1280", "--height", "800"]
    argument_file = instance / "launch.args"
    argument_file.write_text("\n".join(quoted(arg) for arg in args) + "\n", encoding="utf-8", newline="\n")
    receipt = {"game": game, "loader": loader, "candidate_sha256": expected_hash,
               "asset_index": index["id"], "asset_count": len(hashes), "libraries": len(classpath),
               "account": "offline-test", "instance": str(instance), "arguments": str(argument_file)}
    (instance / "preparation.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game", required=True)
    parser.add_argument("--loader", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    args = parser.parse_args()
    prepare(args.game, args.loader, args.name, args.candidate.resolve(), args.sha256)


if __name__ == "__main__":
    main()
