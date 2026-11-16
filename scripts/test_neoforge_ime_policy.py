import importlib.util
import io
from pathlib import Path
import unittest
from zipfile import ZipFile


spec = importlib.util.spec_from_file_location("ime_policy", Path(__file__).with_name("inspect-neoforge-ime.py"))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class NeoForgeImePolicyTest(unittest.TestCase):
    def archive(self, hint):
        output = io.BytesIO()
        with ZipFile(output, "w") as archive:
            archive.writestr("display/DisplayWindow.java", hint)
        return output.getvalue()

    def test_candidate_ownership_is_reported_not_native_success(self):
        data = self.archive('SDLHints.SDL_SetHint(SDLHints.SDL_HINT_IME_IMPLEMENTED_UI, "composition, candidates");')
        result = policy.inspect_sources(data)
        self.assertEqual(result["policy"], "application-owned-candidates")
        self.assertFalse(result["native_candidate_display_verified"])
        self.assertEqual(result["hints"][0]["elements"], ["composition", "candidates"])

    def test_absent_hint_does_not_claim_native_verification(self):
        result = policy.inspect_sources(self.archive("class DisplayWindow {}"))
        self.assertEqual(result["policy"], "no-application-candidate-hint-found")
        self.assertFalse(result["native_candidate_display_verified"])

    def test_exact_fml_dependency_is_required(self):
        dependency = "<dependency><groupId>net.neoforged.fancymodloader</groupId><artifactId>earlydisplay</artifactId><version>12.0.12</version></dependency>"
        def pom(contents):
            return ('<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencies>' + contents + '</dependencies></project>').encode()
        self.assertEqual(policy.earlydisplay_version(pom(dependency)), "12.0.12")
        for contents in ("", dependency + dependency, dependency.replace("12.0.12", "../bad")):
            with self.assertRaises(ValueError):
                policy.earlydisplay_version(pom(contents))


if __name__ == "__main__":
    unittest.main()
