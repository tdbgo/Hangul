"""Run real-loader optional-widget profiles and isolate known upstream failures."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def classify_imblocker(returncode: int, output: str, pin: dict, isolated: bool) -> str:
    if isolated:
        loaded = re.search(r"^\s*- hangul\s|^\s*Hangul [^\n]*\(hangul\)|^\s*- hangul \(", output, re.MULTILINE)
        require(loaded is None, "Hangul was loaded in isolated control")
    expected = pin.get("expected_failure")
    if expected == "missing-minecraft-set-screen":
        require(returncode != 0 and "imblocker.mixins.json:MinecraftClientMixin" in output
                and "onScreenChanged" in output and "'setScreen'" in output
                and "net/minecraft/client/Minecraft" in output, "Unexpected IMBlocker loader result")
        return "KNOWN_UPSTREAM_FAILURE"
    require(expected is None, "Unrecognized pinned IMBlocker expectation")
    marker = "THIRD_PARTY_SMOKE_OK" if isolated else "OPTIONAL_WIDGET_OK rei=true imblocker=true"
    require(returncode == 0 and marker in output, "IMBlocker profile did not complete its checks")
    return "CONTROL_PASS" if isolated else "PASS"


def run_profile(profile: str, candidate: Path, imblocker: bool, isolated: bool = False) -> str:
    prepare = [sys.executable, str(ROOT / "scripts/prepare-mod-profile.py"), profile]
    if imblocker:
        prepare.append("--with-imblocker")
    prepared = subprocess.run(prepare, cwd=ROOT, check=True, timeout=300, stdout=subprocess.PIPE,
                              encoding="utf-8", errors="replace")
    print(prepared.stdout, end="")
    directory_match = re.search(r"^PROFILE_READY (.+)$", prepared.stdout, re.MULTILINE)
    require(directory_match is not None, "Missing prepared profile directory")
    mods_directory = Path(directory_match.group(1).strip()).resolve(strict=True)
    require(mods_directory.parent == (ROOT / ".toolchains/compatibility").resolve(), "Unexpected profile directory")
    platform, game = profile.split("-", 1)
    wrapper = ROOT / ("gradlew.bat" if sys.platform == "win32" else "gradlew")
    command = [str(wrapper), "--no-daemon", "--console=plain", f"-PcompatibilityJar={candidate}",
               f"-PcompatibilityMods={mods_directory}"]
    if platform == "fabric":
        command += ["verifyMixinApplication", f"-Pminecraft_version={game}"]
    else:
        matrix = json.loads((ROOT / "platform-matrix.json").read_text(encoding="utf-8"))
        loaders = [target["loader"] for target in matrix["neoforge"] if target["minecraft"] == game]
        require(bool(loaders), "No exact NeoForge loader declared for optional profile")
        command += ["-Pplatform=neoforge", ":neoforge:runVerification", "-Pneo_version=" + loaders[-1]]
    if imblocker:
        command.append("-PwithIMBlocker=true")
    if isolated:
        command.append("-PthirdPartyOnly=true")
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            encoding="utf-8", errors="replace", timeout=420)
    directory = ROOT / ".toolchains/checks"
    directory.mkdir(parents=True, exist_ok=True)
    run_id = str(time.time_ns())
    log = directory / (profile + ("-imblocker" if imblocker else "") + ("-isolated" if isolated else "") + "-" + run_id + ".log")
    log.write_text(result.stdout, encoding="utf-8")
    if not imblocker:
        require(result.returncode == 0 and "OPTIONAL_WIDGET_OK rei=true" in result.stdout, f"Profile failed: {log}")
        print("PASS", profile, "real REI widget behavior")
    else:
        pin = json.loads((ROOT / "mod-compatibility.json").read_text(encoding="utf-8"))["imblocker-" + platform]
        try:
            classification = classify_imblocker(result.returncode, result.stdout, pin, isolated)
        except ValueError as failure:
            raise ValueError(f"{failure}: {log}") from failure
        print(classification, profile, "IMBlocker " + pin["release"], "without Hangul" if isolated else "with Hangul")
    print("PROFILE_LOG", log)
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("profile", choices=["fabric-26.2", "fabric-26.3", "neoforge-26.3"])
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--check-imblocker", action="store_true")
    args = parser.parse_args()
    candidate = args.candidate.resolve(strict=True)
    original = hashlib.sha256(candidate.read_bytes()).digest()
    run_profile(args.profile, candidate, False)
    if args.check_imblocker:
        run_profile(args.profile, candidate, True)
        run_profile(args.profile, candidate, True, isolated=True)
    require(hashlib.sha256(candidate.read_bytes()).digest() == original, "Candidate changed during profile checks")
    print("PROFILE_CHECKS_COMPLETE (headless; native IME and candidate geometry not verified)")


if __name__ == "__main__":
    main()
