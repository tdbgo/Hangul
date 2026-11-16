"""Inspect the declared NeoForge early-window IME policy without starting a window."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
MAVEN = "https://maven.neoforged.net/releases/"


def fetch(path):
    if not re.fullmatch(r"[A-Za-z0-9_./+-]+", path) or ".." in path.split("/"):
        raise ValueError("Unsafe Maven artifact path")
    request = Request(MAVEN + path, headers={"User-Agent": "Hangul-compatibility-check"})
    with urlopen(request, timeout=40) as response:
        if not response.url.startswith(MAVEN):
            raise ValueError("Unexpected Maven redirect")
        return response.read()


def earlydisplay_version(pom):
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    matches = [dependency.findtext("m:version", namespaces=namespace)
               for dependency in ET.fromstring(pom).findall("./m:dependencies/m:dependency", namespace)
               if dependency.findtext("m:groupId", namespaces=namespace) == "net.neoforged.fancymodloader"
               and dependency.findtext("m:artifactId", namespaces=namespace) == "earlydisplay"]
    if len(matches) != 1 or not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+(?:-[A-Za-z0-9.-]+)?", matches[0] or ""):
        raise ValueError("Expected one exact FML earlydisplay dependency")
    return matches[0]


def inspect_sources(data):
    hints = []
    with ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            if not name.endswith(".java"):
                continue
            source = archive.read(name).decode("utf-8")
            for match in re.finditer(r'SDL_SetHint\s*\(\s*(?:SDLHints\.)?SDL_HINT_IME_IMPLEMENTED_UI\s*,\s*"([^"]*)"\s*\)', source):
                tokens = [token.strip() for token in match.group(1).split(",")]
                hints.append({"source": name, "line": source.count("\n", 0, match.start()) + 1, "elements": tokens})
    return {"hints": hints,
            "policy": "application-owned-candidates" if any("candidates" in hint["elements"] for hint in hints)
                      else "no-application-candidate-hint-found",
            "native_candidate_display_verified": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loader")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    matrix = json.loads((ROOT / "platform-matrix.json").read_text(encoding="utf-8"))
    loader = args.loader or [target["loader"] for target in matrix["neoforge"] if target["minecraft"] == "26.3"][-1]
    if loader not in {target["loader"] for target in matrix["neoforge"]}:
        raise ValueError("Loader must be an exact declared matrix target")
    pom_path = f"net/neoforged/neoforge/{loader}/neoforge-{loader}.pom"
    fml = earlydisplay_version(fetch(pom_path))
    source_path = f"net/neoforged/fancymodloader/earlydisplay/{fml}/earlydisplay-{fml}-sources.jar"
    data = fetch(source_path)
    checksum = fetch(source_path + ".sha1").decode("ascii").strip()
    if not re.fullmatch(r"[0-9a-fA-F]{40}", checksum) or hashlib.sha1(data).hexdigest() != checksum.lower():
        raise ValueError("FML source checksum mismatch")
    report = dict(inspect_sources(data), neoforge=loader, fml=fml, source_sha256=hashlib.sha256(data).hexdigest(),
                  sources={"pom": MAVEN + pom_path, "source_archive": MAVEN + source_path,
                           "sdl_hint": "https://wiki.libsdl.org/SDL3/SDL_HINT_IME_IMPLEMENTED_UI",
                           "upstream_issue": "https://github.com/neoforged/NeoForge/issues/3447"})
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()
