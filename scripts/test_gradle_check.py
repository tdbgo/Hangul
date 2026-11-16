import contextlib
import importlib.util
import io
from pathlib import Path
import subprocess
import unittest


spec = importlib.util.spec_from_file_location("gradle_check", Path(__file__).with_name("run-gradle-check.py"))
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)
NETWORK = "FAILURE: Build failed with an exception.\nCaused by: java.net.SocketException: Connection reset"


class GradleCheckTest(unittest.TestCase):
    def run_results(self, results):
        calls = []
        delays = []
        def execute(command):
            calls.append(tuple(command))
            code, output = results[len(calls) - 1]
            return subprocess.CompletedProcess(command, code, output)
        with contextlib.redirect_stdout(io.StringIO()):
            code = check.run(["gradlew", "verify"], executor=execute, sleep=delays.append)
        return code, calls, delays

    def test_transport_failure_retries_the_identical_command(self):
        code, calls, delays = self.run_results([(1, NETWORK), (0, "BUILD SUCCESSFUL")])
        self.assertEqual(code, 0)
        self.assertEqual(calls, [("gradlew", "verify")] * 2)
        self.assertEqual(delays, [5])

    def test_persistent_failure_is_bounded_and_keeps_exit_code(self):
        code, calls, delays = self.run_results([(7, NETWORK)] * 3)
        self.assertEqual(code, 7)
        self.assertEqual(len(calls), 3)
        self.assertEqual(delays, [5, 10])

    def test_game_assertion_and_runtime_timeout_never_retry(self):
        for failure in ("AssertionError", "NoSuchMethodError", "NoSuchFieldError", "NoSuchMethodException",
                        "NoClassDefFoundError", "ClassNotFoundException", "VerifyError", "MixinApplyError",
                        "InvalidInjectionException", "Compilation failed", "Timeout has been exceeded",
                        "Verification exited without a success receipt"):
            with self.subTest(failure=failure):
                code, calls, _ = self.run_results([(1, NETWORK + "\n" + failure)])
                self.assertEqual(code, 1)
                self.assertEqual(len(calls), 1)

    def test_unclassified_failure_is_not_retried(self):
        code, calls, delays = self.run_results([(3, "FAILURE: Build failed with an exception.\nUnknown error")])
        self.assertEqual((code, len(calls), delays), (3, 1, []))

    def test_missing_plugin_resolution_is_bounded_dependency_failure(self):
        output = "FAILURE: Build failed\nPlugin [id: 'example', version: '1'] was not found in any source"
        self.assertTrue(check.dependency_failure(output))
        self.assertFalse(check.dependency_failure("Connection reset"))

    def test_invalid_attempt_count_is_rejected(self):
        for attempts in (0, 4):
            with self.assertRaises(ValueError):
                check.run(["gradlew"], attempts)


if __name__ == "__main__":
    unittest.main()
