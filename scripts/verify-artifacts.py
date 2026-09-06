"""Check release packaging without loading Minecraft or third-party Python packages."""
import hashlib
import json
from pathlib import Path
import sys
import tomllib
from zipfile import ZipFile


def verify(path: Path, loader: str) -> tuple[set[str], str]:
    metadata_paths = {
        "fabric": "fabric.mod.json",
        "neoforge": "META-INF/neoforge.mods.toml",
        "forge": "META-INF/mods.toml",
    }
    with ZipFile(path) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), f"Duplicate entries: {path.name}"
        assert path.stat().st_size < 96 * 1024, f"Unexpected artifact growth: {path.name}"
        assert metadata_paths[loader] in names
        for other, metadata in metadata_paths.items():
            assert other == loader or metadata not in names, f"Foreign metadata: {metadata}"
        assert any(name.lower() == "license_hangul" for name in names)
        assert "assets/hangul/icon.png" in names
        assert not any("verification" in name or name.endswith((".dll", ".jar", ".log")) for name in names)
        config = json.loads(archive.read("hangul.client.mixins.json"))
        assert config["required"] and len(config["client"]) == 14
        assert not config.get("mixins") and not config.get("server"), "Client hooks must remain client-only"
        assert config["compatibilityLevel"] == ("JAVA_21" if loader == "forge" else "JAVA_25")
        if loader == "forge":
            assert config["minVersion"] == "0.8.7"
            assert "pack.mcmeta" in names, "Forge artifact is missing pack.mcmeta"
            pack = json.loads(archive.read("pack.mcmeta"))["pack"]
            assert pack["description"] == "Hangul resources"
            # One pack is parsed for both resource (84–88) and data (101.1–107.1) formats.
            assert pack["min_format"] == [84, 0] and pack["max_format"] == [107, 1]
        metadata = archive.read(metadata_paths[loader]).decode("utf-8")
        assert "${" not in metadata, "Unexpanded version"
        if loader == "fabric":
            mod = json.loads(metadata)
            assert mod["environment"] == "client" and mod["id"] == "hangul"
            assert set(mod["depends"]) == {"fabricloader", "minecraft", "java"}
            version = mod["version"]
        else:
            mod = tomllib.loads(metadata)
            assert mod["mods"][0]["modId"] == "hangul" and mod["license"] == "MIT"
            assert {item["modId"] for item in mod["dependencies"]["hangul"]} == {loader, "minecraft"}
            version = mod["mods"][0]["version"]
            matrix = json.loads((Path(__file__).resolve().parents[1] / "platform-matrix.json").read_text())[loader]
            required = {item["modId"]: item["versionRange"] for item in mod["dependencies"]["hangul"]}
            assert required["minecraft"] == ",".join(f"[{item['minecraft']}]" for item in matrix)
            loader_versions = [item["loader"] if loader == "neoforge" else item["loader"].split("-", 1)[1] for item in matrix]
            assert required[loader] == ",".join(f"[{item}]" for item in loader_versions)
            if loader == "forge":
                assert mod["clientSideOnly"]
                assert b"MixinConfigs: hangul.client.mixins.json" in archive.read("META-INF/MANIFEST.MF")
            else:
                assert mod["mixins"][0]["config"] == "hangul.client.mixins.json"
        classes = {name for name in names if name.endswith(".class")}
        assert classes and all(name.startswith("kr/playcity/hangul/") for name in classes)
        for name in classes:
            data = archive.read(name)
            assert int.from_bytes(data[6:8], "big") == (65 if loader == "forge" else 69)
            assert b"net/fabricmc/" not in data, f"Runtime Fabric coupling: {name}"
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        print(f"PASS {loader}: {path.name}, {path.stat().st_size} bytes, SHA-256 {digest}")
        return {name for name in classes if not name.startswith("kr/playcity/hangul/neoforge/")}, version


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("Usage: verify-artifacts.py FABRIC_JAR NEOFORGE_JAR FORGE_JAR")
    classes = [verify(Path(path), loader) for path, loader in zip(sys.argv[1:], ("fabric", "neoforge", "forge"))]
    assert classes[0] == classes[1] == classes[2], "Shared classes or release versions differ between loaders"
    print("All platforms ship the same shared class set; no bundled runtime libraries or test fixtures.")


if __name__ == "__main__":
    main()
