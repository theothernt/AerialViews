#!/usr/bin/env python3
"""
Android TV Emulator Messenger
- Finds a running Android emulator via ADB
- Sets up port forwarding on port 8081
- Sends random numbers to /message/1 via POST every 2 seconds
- Press 'q' to quit and clean up port forwarding
- Optionally use --ip to connect directly to a device over the network
"""

import argparse
import json
import subprocess
import sys
import time
import urllib.request
import urllib.error
import threading

PORT = 8081

# Rotating list of test messages with varying lengths and content
TEST_MESSAGES = [
    "Now Playing: Bohemian Rhapsody - Queen",
    "Weather: 72°F Sunny ☀️",
    "New email from John: Meeting Tomorrow",
    "Sports: Lakers 98 - Celtics 102",
    "Calendar: Team Standup in 5 minutes",
    "Breaking News: Tech Stocks Rise",
    "Reminder: Call mom back",
    "Traffic: Heavy congestion on I-95",
    "Stock Update: AAPL +2.4%",
    "Message #1: Test notification",
    "Now Playing: Blinding Lights - The Weeknd",
    "Weather: 58°F Rainy 🌧️",
    "New email from Sarah: Project Update",
    "Sports: Yankees 3 - Red Sox 1",
    "Calendar: Lunch break in 10 minutes",
    "Breaking News: Local Election Results",
    "Reminder: Pick up dry cleaning",
    "Traffic: Accident cleared on Highway 101",
    "Stock Update: GOOGL -1.2%",
    "Message #2: Testing overlay display",
    "Now Playing: Hotel California - Eagles",
    "Weather: 45°F Cloudy ⛅",
    "New email from Boss: Urgent Review",
    "Sports: Manchester United 2 - Liverpool 2",
    "Calendar: Doctor appointment at 3 PM",
    "Breaking News: Weather Alert Issued",
    "Reminder: Pay electricity bill",
    "Traffic: Road work on Main Street",
    "Stock Update: TSLA +5.7%",
    "Message #3: Verifying fade animation",
    "Now Playing: Stairway to Heaven - Led Zeppelin",
    "Weather: 82°F Partly Sunny ⛅",
    "New email from Team: Weekly Report",
    "Sports: Warriors 115 - Nets 108",
    "Calendar: Gym session in 30 minutes",
    "Breaking News: International Summit Begins",
    "Reminder: Birthday gift for Alex",
    "Traffic: Light delay on Route 66",
    "Stock Update: AMZN +0.8%",
    "Message #4: Checking text wrapping",
    "Now Playing: Imagine - John Lennon",
    "Weather: 68°F Windy 💨",
    "New email from Support: Ticket #12345",
    "Sports: Chiefs 24 - Packers 21",
    "Calendar: Conference call starting now",
    "Breaking News: New Product Launch Today",
    "Reminder: Water the plants",
    "Traffic: Bridge closed for maintenance",
    "Stock Update: MSFT +1.5%",
    "Message #5: Final test message",
]


def run_adb(args: list[str]) -> tuple[str, str, int]:
    """Run an ADB command and return (stdout, stderr, returncode)."""
    try:
        result = subprocess.run(
            ["adb"] + args,
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.stdout.strip(), result.stderr.strip(), result.returncode
    except FileNotFoundError:
        print("❌  ERROR: 'adb' not found. Make sure Android SDK platform-tools is in your PATH.")
        sys.exit(1)
    except subprocess.TimeoutExpired:
        return "", "ADB command timed out", 1


def find_emulator() -> str | None:
    """Return the serial of the first running emulator, or None."""
    stdout, _, rc = run_adb(["devices"])
    if rc != 0:
        return None

    for line in stdout.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device" and parts[0].startswith("emulator-"):
            return parts[0]
    return None


def setup_port_forward(serial: str) -> bool:
    """Set up TCP port forwarding for the given emulator serial."""
    _, stderr, rc = run_adb(["-s", serial, "forward", f"tcp:{PORT}", f"tcp:{PORT}"])
    if rc != 0:
        print(f"❌  Port forwarding failed: {stderr}")
        return False
    return True


def remove_port_forward(serial: str):
    """Remove the TCP port forwarding."""
    run_adb(["-s", serial, "forward", "--remove", f"tcp:{PORT}"])


def send_message(message: str, host: str = "localhost") -> tuple[bool, str]:
    """Send a message to the Android TV app. Returns (success, response_or_error)."""
    url = f"http://{host}:{PORT}/message/1"
    data = {
        "text": message,
    }
    json_data = json.dumps(data).encode("utf-8")
    try:
        req = urllib.request.Request(
            url,
            data=json_data,
            method="POST",
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            body = response.read().decode("utf-8")
            return True, body
    except urllib.error.URLError as e:
        return False, str(e.reason)
    except Exception as e:
        return False, str(e)


def check_status(host: str = "localhost") -> tuple[bool, str]:
    """Check if the Aerial Views message API is running. Returns (success, response_or_error)."""
    url = f"http://{host}:{PORT}/status"
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            body = response.read().decode("utf-8")
            return True, body
    except urllib.error.URLError as e:
        return False, str(e.reason)
    except Exception as e:
        return False, str(e)


# ── Cross-platform non-blocking key read ──────────────────────────────────────

def make_quit_listener() -> threading.Event:
    """
    Returns a threading.Event that gets set when the user presses 'q' + Enter
    (works on Windows, Mac and Linux).
    """
    quit_event = threading.Event()

    def _listen():
        while not quit_event.is_set():
            try:
                key = input()
                if key.strip().lower() == "q":
                    quit_event.set()
            except (EOFError, KeyboardInterrupt):
                quit_event.set()

    t = threading.Thread(target=_listen, daemon=True)
    t.start()
    return quit_event


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Send messages to Android TV app via emulator or network device"
    )
    parser.add_argument(
        "--ip",
        type=str,
        help="IP address of device (e.g., 192.168.1.100). If not provided, uses emulator."
    )
    args = parser.parse_args()

    print("=" * 55)
    print("  Android TV Emulator Messenger")
    print("=" * 55)

    serial = None

    # If --ip is provided, skip emulator detection
    if args.ip:
        print(f"\n🌐  Using network device at {args.ip}:{PORT}")
        print("    Ensure the device is running the Aerial Views app")
        print("    and has the Message API enabled in settings.")
    else:
        # 1. Find emulator
        print("\n🔍  Scanning for Android emulator...")
        serial = find_emulator()
        if not serial:
            print("❌  No running Android emulator found.")
            print()
            print("    You can either:")
            print("    1. Start an emulator in Android Studio and try again")
            print(f"    2. Use --ip to connect to a device on the network")
            print(f"       Example: python {sys.argv[0]} --ip 192.168.1.100")
            sys.exit(1)
        print(f"✅  Found emulator: {serial}")

        # 2. Set up port forwarding
        print(f"\n🔗  Setting up port forwarding  localhost:{PORT} → emulator:{PORT} ...")
        if not setup_port_forward(serial):
            sys.exit(1)
        print(f"✅  Port forwarding active on port {PORT}")

    # 3. Check API status
    host = args.ip if args.ip else "localhost"

    print(f"\n🔍  Checking Aerial Views API status at {host}:{PORT}...")
    success, response = check_status(host=host)
    if not success:
        print(f"❌  API status check failed: {response}")
        print()
        print("    Make sure:")
        print("    1. The Aerial Views app is running on the device/emulator")
        print("    2. Message API is enabled in the app settings")
        print("    3. The correct port is configured (default: 8081)")
        if serial:
            print("    4. Port forwarding is active")
        sys.exit(1)
    print(f"✅  API responded: {response}")

    # 4. Start quit listener
    print("\n💬  Sending messages every 2 seconds.")
    print("    Type  q  and press Enter to stop.\n")
    quit_event = make_quit_listener()

    sent = 0
    errors = 0
    message_index = 0

    try:
        while not quit_event.is_set():
            message = TEST_MESSAGES[message_index % len(TEST_MESSAGES)]
            message_index += 1
            success, response = send_message(message, host=host)

            if success:
                sent += 1
                print(f"  ➤  Sent: {message[:50]}")
            else:
                errors += 1
                print(f"  ✗  Sent: {message[:50]}   │  Error: {response[:40]}")

            # Sleep in small increments so we can react to 'q' quickly
            for _ in range(20):
                if quit_event.is_set():
                    break
                time.sleep(0.1)

    except KeyboardInterrupt:
        print("\n\n  Ctrl-C detected.")

    # 5. Cleanup
    print(f"\n🛑  Stopping. (sent={sent}, errors={errors})")
    if serial:
        print(f"🔧  Removing port forwarding for port {PORT}...")
        remove_port_forward(serial)
        print("✅  Port forwarding removed. Bye!")
    else:
        print("👋  Bye!")


if __name__ == "__main__":
    main()