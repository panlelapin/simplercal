"""Exercise emulator startup failures with no real SDK, device or Gradle build."""

import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import textwrap
import unittest


PROJECT = Path(__file__).resolve().parents[2]


class CiDeviceTestsTest(unittest.TestCase):
    def test_failed_emulator_and_blocked_adb_exit_without_running_tests(self):
        with tempfile.TemporaryDirectory(prefix="simplercal-emulator-") as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            shutil.copy2(PROJECT / "scripts/ci-device-tests", root / "scripts/ci-device-tests")
            binaries = root / "bin"
            binaries.mkdir()
            sdk = root / "sdk"
            (sdk / "emulator").mkdir(parents=True)

            def executable(path, content):
                path.write_text(textwrap.dedent(content).lstrip())
                path.chmod(0o755)

            executable(binaries / "sdkmanager", """
                #!/usr/bin/env bash
                printf 'sdkmanager %s\\n' "$*" >> "$CI_TEST_CALLS"
            """)
            executable(binaries / "avdmanager", """
                #!/usr/bin/env bash
                printf 'avdmanager %s\\n' "$*" >> "$CI_TEST_CALLS"
            """)
            executable(root / "gradlew", """
                #!/usr/bin/env bash
                touch "$CI_TEST_GRADLE"
                exit 97
            """)
            executable(binaries / "adb", """
                #!/usr/bin/env python3
                import os, sys, time
                from pathlib import Path
                if 'getprop' in sys.argv:
                    Path(os.environ['CI_TEST_PROBE']).write_text(str(os.getpid()))
                    time.sleep(60)  # Would hold the boot loop open without a timeout.
            """)
            executable(sdk / "emulator/emulator", """
                #!/usr/bin/env python3
                import os, time
                from pathlib import Path
                Path(os.environ['CI_TEST_EMULATOR']).write_text(str(os.getpid()))
                while not Path(os.environ['CI_TEST_PROBE']).exists():
                    time.sleep(.01)
                print('FATAL | Not enough space to create userdata partition.', flush=True)
                time.sleep(60)  # The launcher remains alive after reporting failure.
            """)
            env = dict(os.environ, PATH=f"{binaries}:{os.environ['PATH']}",
                       GITHUB_ACTIONS="true", RUNNER_TEMP=str(root / "runner"),
                       ANDROID_HOME=str(sdk), CI_TEST_CALLS=str(root / "calls"),
                       CI_TEST_GRADLE=str(root / "gradle-ran"),
                       CI_TEST_PROBE=str(root / "probe-pid"),
                       CI_TEST_EMULATOR=str(root / "emulator-pid"))
            try:
                result = subprocess.run([str(root / "scripts/ci-device-tests"), "34"],
                                        env=env, text=True, capture_output=True, timeout=15)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Emulator startup failed", result.stderr)
                self.assertIn("Not enough space", result.stdout)
                self.assertTrue((root / "probe-pid").exists())
                self.assertFalse((root / "gradle-ran").exists())
                calls = (root / "calls").read_text()
                self.assertIn("avdmanager delete avd -n tests-34", calls)
                self.assertIn("sdkmanager --uninstall system-images;android-34;google_apis;x86_64", calls)
            finally:
                # Also contain a regression that times out before the production trap.
                for name in ("emulator-pid", "probe-pid"):
                    pid_file = root / name
                    if pid_file.exists():
                        try:
                            os.kill(int(pid_file.read_text()), signal.SIGKILL)
                        except ProcessLookupError:
                            pass
