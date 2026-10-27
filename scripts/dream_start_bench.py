#!/usr/bin/env python3
"""Measure screensaver (dream) startup on a connected device.

The dream cannot be launched from adb on an unrooted device (`cmd dreams start-dreaming`
requires root), so the harness uses the path the system uses on its own:

  1. put the device to sleep (a dream never starts from a sleep key press)
  2. drop the screen-off timeout to a few seconds and wake it
  3. the timeout expires ~5-9s later, the system enters Dreaming and starts DreamActivity

The sleep/wake cycle matters: flipping the timeout on an already awake device does not re-arm
the idle timeout, and a screen-bright wake lock held by the system blocks it entirely.

Timestamps come from the `AerialDreamTiming` logcat marks (beta/debug builds only), so the
numbers cover the real dream path: process start -> controller built -> playlist -> first frame.
The system reported `Displayed ... DreamActivity: +NNNms` line is collected as a cross-check.

Usage:
    python scripts/dream_start_bench.py --iterations 10

The screen-off timeout and stay-awake settings are restored when the run finishes, including
on Ctrl+C.
"""

from __future__ import annotations

import argparse
import csv
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

DEFAULT_PACKAGE = "com.neilturner.aerialviews"

TEST_CONFIG_RECEIVER = "com.neilturner.aerialviews.testconfig.TestConfigReceiver"
TEST_CONFIG_ACTION = "com.neilturner.aerialviews.testconfig.APPLY"

TIMING_TAG = "AerialDreamTiming"
TIMING_RE = re.compile(rf"{TIMING_TAG}: ([a-zA-Z.]+);(\d+)(?:;(.*))?$")
DISPLAYED_RE = re.compile(r"Displayed \S*DreamActivity\b.*?: \+(\d+)ms")

# Milestones reported relative to `app.create`, in the order they should occur.
MARK_ORDER = [
    "app.create",
    "dream.attach",
    "dream.contentView",
    "screen.built",
    "playlist.resolved",
    "playback.start",
    "video.prepared",
    "video.firstFrame",
]

# The timeout that arms the dream trigger, and the benign values used between iterations.
TRIGGER_TIMEOUT_MS = 5000
SETTLE_TIMEOUT_MS = 600000
STAY_ON_PLUGGED_IN = 15


class Adb:
    def __init__(self, serial: str | None) -> None:
        self.serial = serial

    def _base(self) -> list[str]:
        return ["adb"] + (["-s", self.serial] if self.serial else [])

    def run(self, *args: str, timeout: float = 30.0) -> str:
        result = subprocess.run(
            self._base() + list(args),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
        )
        if result.returncode != 0:
            detail = (result.stderr or result.stdout).strip()
            raise RuntimeError(f"adb {' '.join(args)} failed: {detail}")
        return result.stdout

    def shell(self, command: str, timeout: float = 30.0) -> str:
        return self.run("shell", command, timeout=timeout)

    def get_setting(self, namespace: str, key: str) -> str:
        return self.shell(f"settings get {namespace} {key}").strip()

    def put_setting(self, namespace: str, key: str, value: str) -> None:
        self.shell(f"settings put {namespace} {key} {value}")

    def is_dreaming(self) -> bool:
        dump = self.shell("dumpsys dreams")
        match = re.search(r"mCurrentDream=(\S+)", dump)
        return bool(match) and match.group(1) != "null"

    def wakefulness(self) -> str:
        dump = self.shell("dumpsys power")
        match = re.search(r"mWakefulness=(\w+)", dump)
        return match.group(1) if match else ""

    def screen_wake_locks(self) -> str:
        dump = self.shell("dumpsys power")
        return "\n".join(
            line.strip() for line in dump.splitlines() if "SCREEN_BRIGHT_WAKE_LOCK" in line or "STAY_AWAKE" in line
        )

    def timing_marks(self) -> list[tuple[str, int, str]]:
        log = self.run("logcat", "-d", "-s", f"{TIMING_TAG}:I")
        marks = []
        for line in log.splitlines():
            match = TIMING_RE.search(line)
            if match:
                marks.append((match.group(1), int(match.group(2)), match.group(3) or ""))
        return marks

    def displayed_ms(self) -> int | None:
        log = self.run("logcat", "-d", "-v", "monotonic")
        hits = DISPLAYED_RE.findall(log)
        return int(hits[-1]) if hits else None


def set_playlist_cache(adb: Adb, package: str, enabled: bool) -> None:
    """Flip the playlist cache preference through the app's shell-only test config receiver.

    Only the general block is applied (`partial`), so the device's providers, media selections
    and button mappings are left alone.
    """
    component = f"{package}/{TEST_CONFIG_RECEIVER}"
    try:
        adb.shell(f"pm enable {component}")
    except RuntimeError:
        # Builds made with -PtestConfig=true already have the receiver enabled; on other builds
        # the shell is not allowed to enable it and the broadcast below fails instead.
        pass
    payload = '{"general":{"playlistCache":' + ("true" if enabled else "false") + "}}"
    output = adb.shell(
        f"am broadcast -n {component} -a {TEST_CONFIG_ACTION} --es json '{payload}' --ez partial true"
    )
    if "Broadcast completed" not in output:
        raise RuntimeError(f"could not apply playlist cache={enabled}: {output.strip()}")
    time.sleep(1.5)


def wait_until(predicate: Any, timeout: float, interval: float = 0.3) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return True
        time.sleep(interval)
    return False


def trigger_dream(adb: Adb, package: str, trigger_timeout: float) -> tuple[bool, str]:
    """Sleep, wake with a short timeout, and wait for the system to start the dream."""
    adb.shell(f"am force-stop {package}")
    adb.put_setting("global", "stay_on_while_plugged_in", str(STAY_ON_PLUGGED_IN))
    adb.put_setting("system", "screen_off_timeout", str(SETTLE_TIMEOUT_MS))

    # A fresh wake is what arms the idle timeout; on an already awake device the screen simply
    # stays on. Sleeping first also releases the system's screen-bright wake locks.
    adb.shell("input keyevent 223")
    if not wait_until(lambda: adb.wakefulness() != "Awake", 10, 0.4):
        return False, "device did not sleep"

    adb.run("logcat", "-c")
    adb.put_setting("global", "stay_on_while_plugged_in", "0")
    adb.put_setting("system", "screen_off_timeout", str(TRIGGER_TIMEOUT_MS))
    if adb.get_setting("system", "screen_off_timeout") != str(TRIGGER_TIMEOUT_MS):
        # The TV settings app can overwrite this through its own screensaver_timeout_ms key.
        adb.put_setting("system", "screen_off_timeout", str(TRIGGER_TIMEOUT_MS))

    adb.shell("input keyevent 224")
    started = wait_until(adb.is_dreaming, trigger_timeout, 0.4)
    if not started:
        locks = adb.screen_wake_locks()
        return False, f"no dream after {trigger_timeout:.0f}s" + (f"; wake locks: {locks}" if locks else "")
    return True, ""


def collect_run(adb: Adb, frame_timeout: float) -> dict[str, Any]:
    """Wait for the first video frame (or give up) and fold the marks into one row."""
    deadline = time.monotonic() + frame_timeout
    marks: list[tuple[str, int, str]] = []
    while time.monotonic() < deadline:
        marks = adb.timing_marks()
        if any(event in ("video.firstFrame", "dream.stop") for event, _, _ in marks):
            # The first frame lands a frame or two before the "prepared" callback; give the
            # remaining marks for this item a moment to arrive.
            time.sleep(0.5)
            marks = adb.timing_marks()
            break
        time.sleep(0.4)

    first: dict[str, tuple[int, str]] = {}
    for event, timestamp, detail in marks:
        first.setdefault(event, (timestamp, detail))

    row: dict[str, Any] = {"detail": first.get("playlist.resolved", (0, ""))[1]}
    base = first.get("app.create", (None, ""))[0]
    for event in MARK_ORDER:
        timestamp = first.get(event, (None, ""))[0]
        row[event] = timestamp
        row[f"{event}.dt"] = None if base is None or timestamp is None else timestamp - base
    row["displayed_ms"] = adb.displayed_ms()
    row["completed"] = "video.firstFrame" in first
    return row


def stop_dream(adb: Adb, package: str) -> None:
    if not adb.is_dreaming():
        return
    adb.shell("input keyevent 224")
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        if not adb.is_dreaming():
            return
        time.sleep(0.4)
    adb.shell(f"am force-stop {package}")


def summarise(rows: list[dict[str, Any]]) -> None:
    def stats(values: list[float]) -> str:
        if not values:
            return "n/a"
        ordered = sorted(values)
        median = statistics.median(ordered)
        p90 = ordered[min(len(ordered) - 1, int(round(0.9 * (len(ordered) - 1))))]
        return f"{median:7.0f}  {p90:7.0f}  {ordered[0]:7.0f}  {ordered[-1]:7.0f}"

    print()
    print(f"{'metric':<24}{'median':>8}{'p90':>8}{'min':>8}{'max':>8}")
    for event in MARK_ORDER[1:]:
        key = f"{event}.dt"
        values = [row[key] for row in rows if row.get(key) is not None]
        print(f"{event:<24}{stats(values)}")
    displayed = [row["displayed_ms"] for row in rows if row.get("displayed_ms")]
    print(f"{'system displayed':<24}{stats(displayed)}")

    completed = sum(1 for row in rows if row["completed"])
    print(f"\n{completed}/{len(rows)} runs reached the first video frame")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--iterations", type=int, default=10)
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--serial", default=None)
    parser.add_argument("--out", default="build/dream-start.csv")
    parser.add_argument("--trigger-timeout", type=float, default=25.0, help="seconds to wait for the dream to start")
    parser.add_argument("--frame-timeout", type=float, default=25.0, help="seconds to wait for the first frame mark")
    parser.add_argument("--retries", type=int, default=1, help="re-trigger attempts when the dream never starts")
    parser.add_argument("--warmup", type=int, default=0, help="iterations to run but exclude (e.g. to populate the cache)")
    parser.add_argument(
        "--cache",
        choices=("on", "off"),
        default=None,
        help="playlist cache for the run, applied via the test config receiver (restored to off)",
    )
    args = parser.parse_args()

    adb = Adb(args.serial)
    original = {
        ("global", "stay_on_while_plugged_in"): adb.get_setting("global", "stay_on_while_plugged_in"),
        ("system", "screen_off_timeout"): adb.get_setting("system", "screen_off_timeout"),
    }
    print(f"device settings before run: {original}")
    if original[("system", "screen_off_timeout")] == str(TRIGGER_TIMEOUT_MS):
        print("warning: device still has the benchmark timeout; an earlier run was interrupted")
    print(f"target package: {args.package}, iterations: {args.iterations}, warmup: {args.warmup}")
    if args.cache:
        print(f"playlist cache: {args.cache}")
        set_playlist_cache(adb, args.package, args.cache == "on")

    rows: list[dict[str, Any]] = []
    failed = 0
    total = args.warmup + args.iterations
    try:
        for index in range(1, total + 1):
            warmup = index <= args.warmup
            label = f"{index}{'w' if warmup else ''}"
            stop_dream(adb, args.package)
            started = False
            for attempt in range(args.retries + 1):
                started, reason = trigger_dream(adb, args.package, args.trigger_timeout)
                if started:
                    break
                print(f"  iteration {label}: dream did not start (attempt {attempt + 1}: {reason})")
                stop_dream(adb, args.package)

            if not started:
                if not warmup:
                    failed += 1
                continue

            row = collect_run(adb, args.frame_timeout)
            if not warmup:
                rows.append(row)
            print(
                f"  {label:>3}. attach {row['dream.attach.dt']}ms"
                f"  contentView {row['dream.contentView.dt']}ms"
                f"  playlist {row['playlist.resolved.dt']}ms"
                f"  prepared {row['video.prepared.dt']}ms"
                f"  firstFrame {row['video.firstFrame.dt']}ms"
                f"  [{row['detail']}]"
            )
            stop_dream(adb, args.package)
    finally:
        for (namespace, key), value in original.items():
            if value and value != "null":
                adb.put_setting(namespace, key, value)
        adb.shell(f"am force-stop {args.package}")
        print("restored device settings")
        if args.cache:
            # The playlist cache is off by default; leave the device as the baseline run found it.
            set_playlist_cache(adb, args.package, False)
            print("restored playlist cache off")

    if not rows:
        print("no runs completed", file=sys.stderr)
        return 1

    summarise(rows)

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"\nwrote {out_path}")
    return 0 if failed == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
