"""Behavioral tests in disposable repositories, with no real gh/adb/signing calls."""

import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


PROJECT = Path(__file__).resolve().parents[2]


def function(script, name):
    source = (PROJECT / "scripts" / script).read_text()
    match = re.search(r"^" + re.escape(name) + r"\(\) \{\n.*?^\}", source, re.M | re.S)
    if not match:
        raise AssertionError(f"Missing production function {script}:{name}")
    return match.group()


class PipelineTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="simplercal-pipeline-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "repo"
        self.root.mkdir()
        self.bin = Path(self.temporary.name) / "bin"
        self.bin.mkdir()
        self.trace = Path(self.temporary.name) / "calls"
        self.environment = dict(os.environ, PATH=f"{self.bin}:{os.environ['PATH']}",
                                TEST_TRACE=str(self.trace), GIT_CONFIG_GLOBAL=os.devnull,
                                GIT_CONFIG_SYSTEM=os.devnull, GIT_TERMINAL_PROMPT="0")
        # Fail closed: even a test that accidentally takes a new path cannot reach
        # the real GitHub client, ADB device, keytool or OpenSSL key generation.
        for name in ("gh", "adb", "keytool", "openssl"):
            self.stub(name, 'printf "unexpected external command\\n" >&2\nexit 97\n')
        self.call("git", "init", "-q")
        self.call("git", "config", "user.email", "tests@example.invalid")
        self.call("git", "config", "user.name", "Pipeline test")
        self.write(".gitignore", "check-local.log\nbuild/\nsigning/\n")
        self.write("source", "original\n")
        self.call("git", "add", ".")
        self.call("git", "commit", "-qm", "Fixture")

    def call(self, *args, check=True, input=None, env=None):
        result = subprocess.run(args, cwd=self.root, env=env or self.environment,
                                input=input, text=True, capture_output=True, timeout=15)
        if check and result.returncode:
            self.fail(f"{args!r}: {result.returncode}\n{result.stdout}\n{result.stderr}")
        return result

    def write(self, name, text, executable=False):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        if executable:
            path.chmod(0o755)
        return path

    def stub(self, name, body):
        path = self.bin / name
        path.write_text("#!/usr/bin/env bash\nset -Eeuo pipefail\n" + body)
        path.chmod(0o755)

    def shell(self, text, **kwargs):
        return self.call("bash", "-c", "set -Eeuo pipefail\n" + text, **kwargs)

    def digest(self, script="check-local"):
        return self.shell(function(script, "current_source_digest") + "\ncurrent_source_digest").stdout.strip()

    def attest(self):
        digest = self.digest()
        self.write("check-local.log", f"CHECK_LOCAL_SOURCE_DIGEST={digest}\nCHECK_LOCAL_RESULT=PASS\n")
        return digest

    def require_check(self, check=True):
        return self.shell("LOCAL_LOG=check-local.log\nSCRIPT_NAME=fixture\n" +
                          function("make-remote", "die") + "\n" +
                          function("make-remote", "current_source_digest") + "\n" +
                          function("make-remote", "require_local_check") + "\nrequire_local_check", check=check)

    def test_deletion_has_same_digest_before_and_after_staging(self):
        before = self.digest()
        (self.root / "source").unlink()
        deleted = self.digest()
        self.assertNotEqual(before, deleted)
        self.call("git", "add", "-A")
        self.assertEqual(deleted, self.digest())
        self.assertEqual(deleted, self.digest("make-remote"))

    def test_digest_represents_modes_paths_and_full_symlink_target(self):
        before = self.digest()
        (self.root / "source").chmod(0o755)
        self.assertNotEqual(before, self.digest())
        link = self.root / "link"
        link.symlink_to("target\n")
        newline = self.digest()
        link.unlink()
        link.symlink_to("target")
        self.assertNotEqual(newline, self.digest())
        (self.root / "source").rename(self.root / "renamed")
        self.assertNotEqual(before, self.digest())

    def test_attestation_survives_staging_and_commit_but_rejects_source_mutation(self):
        self.write("source", "modified\n")
        self.attest()
        self.call("git", "add", "-A")
        self.require_check()
        self.call("git", "commit", "-qm", "Validated fixture")
        self.require_check()
        self.write("source", "changed after commit\n")
        result = self.require_check(check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stale", result.stderr)

    def test_attestation_requires_a_final_success_record(self):
        digest = self.digest()
        self.write("check-local.log", f"CHECK_LOCAL_SOURCE_DIGEST={digest}\nCHECK_LOCAL_RESULT=PASS\nlater failure\n")
        result = self.require_check(check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("did not pass", result.stderr)

    def local_gate_fixture(self):
        for name in ("check-github-stuff", "check-local", "make-remote", "verify-apk", "configure-signing"):
            source = PROJECT / "scripts" / name
            shutil.copy2(source, self.write("scripts/" + name, ""))
            shutil.copy2(source, self.write("skill/make_android_app/scripts/" + name, ""))
        self.write("skill/make_android_app/SKILL.md", "Fixture\n")
        self.write("skill/make_android_app/agents/openai.yaml", "interface: {}\n")
        for directory in ("assets", "references"):
            (self.root / "skill/make_android_app" / directory).mkdir()
        self.write("gradle/wrapper/gradle-wrapper.jar", "fixture, never executed\n")
        self.write("gradle/wrapper/gradle-wrapper.properties", "distributionSha256Sum=fixture\n")
        self.write("app/build.gradle.kts", 'minSdk = 34\napplicationId = "fixture.app"\n')
        shutil.copy2(PROJECT / ".github/workflows/android-app.yml", self.write(".github/workflows/android-app.yml", ""))
        self.write("app/src/main/AndroidManifest.xml", "<manifest />\n")
        self.write("gradlew", '''#!/usr/bin/env bash
set -Eeuo pipefail
case "${TEST_GRADLE_MODE:-pass}" in
  fail) exit 19 ;;
  mutate) printf 'changed during gate\\n' >> source ;;
esac
''', True)
        tools = Path(self.temporary.name) / "toolchain"
        for name in ("sdk/build-tools/36.0.0/aapt2", "sdk/build-tools/36.0.0/apksigner",
                     "sdk/cmdline-tools/latest/bin/apkanalyzer", "jdk/bin/java"):
            path = tools / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('#!/usr/bin/env bash\nprintf \'openjdk version "17.0.fixture"\\n\'\n')
            path.chmod(0o755)
        self.environment.update(ANDROID_HOME=str(tools / "sdk"), ANDROID_SDK_ROOT=str(tools / "sdk"),
                                JAVA_HOME=str(tools / "jdk"))

    def test_local_gate_propagates_gradle_failure_and_flushes_its_log(self):
        self.local_gate_fixture()
        self.environment["TEST_GRADLE_MODE"] = "fail"
        result = self.call("scripts/check-local", check=False)
        self.assertNotEqual(0, result.returncode)
        log = (self.root / "check-local.log").read_text()
        self.assertIn("FAIL: Gradle functionalCheck", log)
        self.assertIn("CHECK_LOCAL_RESULT=FAIL", log)
        self.assertEqual(result.stdout, log)

    def test_local_gate_rejects_source_changes_during_validation(self):
        self.local_gate_fixture()
        self.environment["TEST_GRADLE_MODE"] = "mutate"
        result = self.call("scripts/check-local", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("source files changed during validation", result.stdout)
        self.assertNotIn("CHECK_LOCAL_RESULT=PASS", (self.root / "check-local.log").read_text())

    def test_failed_project_contract_is_not_masked_by_later_success(self):
        self.local_gate_fixture()
        successful = self.call("scripts/check-local")
        self.assertTrue(successful.stdout.endswith("CHECK_LOCAL_RESULT=PASS\n"))
        self.write("skill/make_android_app/scripts/verify-apk", "#!/usr/bin/env bash\nexit 0\n", True)
        result = self.call("scripts/check-local", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("FAIL: project and script contract", result.stdout)
        self.assertNotIn("CHECK_LOCAL_RESULT=PASS", (self.root / "check-local.log").read_text())

    def wrapper_fixture(self, check_status=0):
        shutil.copy2(PROJECT / "scripts/build-remote", self.write("scripts/build-remote", ""))
        self.stub("gh", '[[ "$*" == "auth status" ]]\n')
        self.write("scripts/check-local", f"#!/usr/bin/env bash\nprintf 'check\\n' >> \"$TEST_TRACE\"\nexit {check_status}\n", True)
        self.write("scripts/make-remote", '#!/usr/bin/env bash\nprintf "remote:%s\\n" "$*" >> "$TEST_TRACE"\n', True)

    def test_failed_local_gate_stops_the_wrapper_without_remote_side_effects(self):
        self.wrapper_fixture(check_status=23)
        result = self.call("scripts/build-remote", check=False)
        self.assertEqual(23, result.returncode)
        self.assertEqual("check\n", self.trace.read_text())

    def test_wrapper_forwards_resume_and_help_does_not_validate(self):
        self.wrapper_fixture()
        self.call("scripts/build-remote", "--help")
        self.assertFalse(self.trace.exists())
        self.call("scripts/build-remote", "--resume", "42")
        self.assertEqual("check\nremote:--resume 42\n", self.trace.read_text())
        result = self.call("scripts/build-remote", "--resume", "not-a-run", check=False)
        self.assertEqual(2, result.returncode)

    def resume(self, status="completed", conclusion="success", sha=None):
        self.environment["TEST_RUN_STATE"] = f"{status}\t{conclusion}\t{sha or self.call('git', 'rev-parse', 'HEAD').stdout.strip()}"
        self.stub("gh", '[[ "$1 $2" == "run view" ]]\nprintf "%s\\n" "$TEST_RUN_STATE"\n')
        source = (PROJECT / "scripts/make-remote").read_text()
        branch = source.split('if [[ -n "$RESUME_RUN_ID" ]]; then\n', 1)[1].split("\nelse\n  stage_source_changes", 1)[0]
        return self.shell('RESUME_RUN_ID=42\nREPOSITORY=fixture/repo\nSCRIPT_NAME=fixture\n' +
                          function("make-remote", "die") + "\n" + branch, check=False)

    def test_resume_rejects_failed_incomplete_and_wrong_sha_runs(self):
        for status, conclusion, sha in (("completed", "failure", None), ("in_progress", "", None),
                                        ("completed", "success", "0" * 40)):
            with self.subTest(status=status, conclusion=conclusion, sha=sha):
                self.assertNotEqual(0, self.resume(status, conclusion, sha).returncode)
        self.assertEqual(0, self.resume().returncode)

    def test_resume_rejects_modified_and_untracked_source(self):
        self.write("source", "dirty\n")
        result = self.resume()
        self.assertIn("clean source worktree", result.stderr)
        self.call("git", "restore", "source")
        self.write("untracked", "not in the successful run\n")
        self.assertIn("clean source worktree", self.resume().stderr)

    def test_lost_preflight_device_never_falls_back_to_another_device(self):
        self.stub("adb", 'printf "%s\\n" "$*" >> "$TEST_TRACE"\n[[ "$*" == "-s chosen get-state" ]]\nprintf "offline\\n"\n')
        result = self.shell('SCRIPT_NAME=fixture\nPREFLIGHT_DEVICE=chosen\n' +
                            function("make-remote", "die") + "\n" +
                            function("make-remote", "install_on_adb_device") + "\ninstall_on_adb_device", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("refusing to select another device", result.stderr)
        self.assertEqual("-s chosen get-state\n", self.trace.read_text())

    def test_signing_mismatch_never_uninstalls_or_selects_another_device(self):
        self.stub("adb", '''printf "%s\\n" "$*" >> "$TEST_TRACE"
[[ "$1 $2" == "-s chosen" ]]
case "$3" in
  get-state) printf 'device\\n' ;;
  shell) printf 'package:/data/app/fixture/base.apk\\n' ;;
  install) printf 'INSTALL_FAILED_UPDATE_INCOMPATIBLE\\n' >&2; exit 1 ;;
  *) exit 97 ;;
esac
''')
        result = self.shell('SCRIPT_NAME=fixture\nPREFLIGHT_DEVICE=chosen\nEXPECTED_PACKAGE=fixture.app\nAPK=fixture.apk\n' +
                            function("make-remote", "die") + "\n" +
                            function("make-remote", "install_on_adb_device") + "\ninstall_on_adb_device", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("data were kept", result.stderr)
        calls = self.trace.read_text()
        self.assertIn("-s chosen install -r fixture.apk", calls)
        self.assertNotIn("uninstall", calls)

    def signing_fixture(self):
        shutil.copy2(PROJECT / "scripts/configure-signing", self.write("scripts/configure-signing", ""))
        self.write("signing/release.jks", "SYNTHETIC_KEY_NO_PRIVATE_MATERIAL")
        self.write("signing/credentials", "SYNTHETIC_PASSWORD_DO_NOT_LOG\n")
        self.stub("keytool", 'exit 0\n')
        self.stub("gh", '''case "$1 $2" in
  'repo view') printf 'fixture/repo\\n' ;;
  'secret set') printf '%s\\n' "$3" >> "$TEST_TRACE"; cat >/dev/null ;;
  *) exit 97 ;;
esac
''')

    def test_signing_backup_inside_repo_is_rejected_even_through_symlink_ancestor(self):
        self.signing_fixture()
        link = Path(self.temporary.name) / "alias"
        link.symlink_to(self.root, target_is_directory=True)
        for backup in (self.root / "backup", link / "nested" / "backup"):
            env = dict(self.environment, SIGNING_BACKUP_ROOT=str(backup))
            result = self.call("scripts/configure-signing", check=False, input="y\n", env=env)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("outside the repository", result.stderr)
            self.assertNotIn("SYNTHETIC_PASSWORD", result.stdout + result.stderr)
        self.assertFalse(self.trace.exists())

    def test_signing_upload_requires_verified_external_backup_and_never_logs_password(self):
        self.signing_fixture()
        backup = Path(self.temporary.name) / "external-backup"
        env = dict(self.environment, SIGNING_BACKUP_ROOT=str(backup))
        result = self.call("scripts/configure-signing", input="y\n", env=env)
        self.assertNotIn("SYNTHETIC_PASSWORD", result.stdout + result.stderr)
        self.assertEqual(4, len(self.trace.read_text().splitlines()))
        copies = list(backup.glob("*/credentials"))
        self.assertEqual(1, len(copies))
        self.assertEqual((self.root / "signing/credentials").read_bytes(), copies[0].read_bytes())
        self.assertEqual(0o600, copies[0].stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
