"""Retry dependency transport failures without retrying failed game or Mixin checks."""
import argparse
import re
import subprocess
import sys
import time


def dependency_failure(output):
    if re.search(r"AssertionError|NoSuch(?:Method|Field)(?:Error|Exception)|NoClassDefFoundError|ClassNotFoundException|"
                 r"IncompatibleClassChangeError|VerifyError|ClassFormatError|MixinApplyError|InvalidInjectionException|"
                 r"Compilation failed|compilation error|Timeout has been exceeded|Verification exited without", output, re.I):
        return False
    if "FAILURE: Build failed" not in output:
        return False
    return bool(re.search(r"Connection reset|Read timed out|UnknownHostException|Could not (?:GET|HEAD|resolve)|"
                          r"Plugin \[id: [^\n]+\] was not found", output, re.I))


def execute(command):
    lines = []
    with subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, encoding="utf-8", errors="replace") as process:
        for line in process.stdout:
            print(line, end="", flush=True)
            lines.append(line)
        code = process.wait()
    return subprocess.CompletedProcess(command, code, "".join(lines))


def run(command, attempts=3, executor=execute, sleep=time.sleep):
    if not command or attempts not in (1, 2, 3):
        raise ValueError("Expected a command and one to three attempts")
    for attempt in range(1, attempts + 1):
        result = executor(command)
        if result.returncode == 0 or attempt == attempts or not dependency_failure(result.stdout):
            return result.returncode
        print(f"Dependency resolution failed; retrying the same check ({attempt + 1}/{attempts}).", flush=True)
        sleep(5 * attempt)
    raise AssertionError("Unreachable retry state")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--attempts", type=int, choices=(1, 2, 3), default=3)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("Missing Gradle command")
    raise SystemExit(run(command, args.attempts))


if __name__ == "__main__":
    main()
