"""Structural contracts that cannot be inferred from JVM/Compose behavior."""

from pathlib import Path
import unittest
from xml.etree import ElementTree

import yaml


PROJECT = Path(__file__).resolve().parents[2]


class ProjectContractsTest(unittest.TestCase):
    def test_ci_tests_both_android_versions_before_publishing_and_always_keeps_reports(self):
        workflow = yaml.safe_load((PROJECT / ".github/workflows/android-app.yml").read_text())
        self.assertEqual(["workflow_dispatch"], list(workflow.get("on", workflow.get(True))))
        steps = workflow["jobs"]["build"]["steps"]
        publication = next(index for index, step in enumerate(steps) if step.get("with", {}).get("name") == "android-app-arm64-v8a")
        for command in ("functionalCheck", "assembleSmoke", "./scripts/ci-device-tests 34", "./scripts/ci-device-tests 36"):
            index = next(index for index, step in enumerate(steps) if command in step.get("run", ""))
            self.assertLess(index, publication)
            self.assertNotIn("continue-on-error", steps[index])
            self.assertNotIn("if", steps[index])
        reports = next(step for step in steps if step.get("with", {}).get("name") == "android-test-reports")
        self.assertEqual("always()", reports["if"])
        self.assertIn("build/ci-diagnostics/", reports["with"]["path"])
        self.assertNotIn("signing", reports["with"]["path"])

    def test_backup_excludes_device_ids_and_private_snapshots_in_every_transport(self):
        root = PROJECT / "app/src/main/res/xml"
        legacy = ElementTree.parse(root / "backup_rules.xml").getroot()
        modern = ElementTree.parse(root / "data_extraction_rules.xml").getroot()
        for section in (legacy, modern.find("cloud-backup"), modern.find("device-transfer")):
            self.assertIsNotNone(section)
            exclusions = {(node.get("domain"), node.get("path")) for node in section.findall("exclude")}
            self.assertIn(("sharedpref", "calendar_device.xml"), exclusions)
            self.assertIn(("file", "ical_sources/"), exclusions)

    def test_reusable_delivery_scripts_match_the_tracked_snapshot(self):
        for name in ("check-github-stuff", "check-local", "make-remote", "verify-apk", "configure-signing"):
            with self.subTest(script=name):
                source = PROJECT / "scripts" / name
                snapshot = PROJECT / "skill/make_android_app/scripts" / name
                self.assertEqual(source.read_bytes(), snapshot.read_bytes())
                self.assertTrue(source.stat().st_mode & 0o111)
                self.assertTrue(snapshot.stat().st_mode & 0o111)
