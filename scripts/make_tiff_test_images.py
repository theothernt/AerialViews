#!/usr/bin/env python3
"""Build a local set of TIFF test images for Immich / Aerial Views testing.

Downloads freely-licensed landscape photos from Wikimedia Commons, fits each one
to exactly 1920x1080 (centre-cropped) and converts it to TIFF with a rotating
compression mode, so the set exercises uncompressed and LZW TIFFs rather than
50 identical files.

Every image is a standard-width Commons thumbnail, so downloads stay small and
respect the API's thumbnail-served-elsewhere guidance; the output is fixed at
1080p because that is what the TV app displays. Provenance - source page and
licence - is written to sources.csv next to the images.

The output directory is under the gitignored local/ folder by default, so the
images never end up in a commit.

Usage:
    python3 scripts/make_tiff_test_images.py                  # 50 images
    python3 scripts/make_tiff_test_images.py --limit 10
    python3 scripts/make_tiff_test_images.py --out /tmp/tiffs --width 3840 --height 2160

Re-running is resumable: existing files are skipped, so a run that hits Commons
rate limits can simply be repeated.

Requires macOS (uses sips for the image work) and python3 (stdlib only).
"""

import argparse
import csv
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API_URL = "https://commons.wikimedia.org/w/api.php"
CATEGORY = "Category:Featured pictures on Wikimedia Commons"
USER_AGENT = "AerialViews-tiff-fixtures/1.0 (local test fixtures; python-urllib)"
SOURCE_MIMES = ("image/jpeg", "image/png")
# Rotated in order across the downloads, so the set covers each TIFF flavour.
# PackBits is deliberately absent: sips accepts the option but writes an uncompressed
# TIFF anyway, so including it would only produce mislabelled files.
COMPRESSIONS = ["none", "lzw"]
TIFF_COMPRESSION_TAGS = {1: "none", 2: "ccitt", 5: "lzw", 7: "jpeg", 8: "deflate", 32773: "packbits", 32946: "deflate"}
# Every nth file gets the long .tiff extension instead of .tif.
TIFF_EVERY = 17
# A few deliberately awkward names, to exercise filename/URL handling on import.
ODD_NAMES = {41: "tiff-041-with spaces.tif", 42: "tiff-042-ünïcode-名前.tif"}


def api(params):
    """Call the Commons API and return the parsed JSON body."""
    query = dict(params, format="json", formatversion="2")
    url = f"{API_URL}?{urllib.parse.urlencode(query)}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def strip_html(value):
    """Reduce the HTML-ish extmetadata values to plain text."""
    return html.unescape(re.sub(r"<[^>]+>", "", value or "")).strip()


def find_images(wanted, width, height):
    """Collect landscape candidates big enough to fill width x height."""
    found = []
    continuation = {}
    while len(found) < wanted:
        params = {
            "action": "query",
            "generator": "categorymembers",
            "gcmtitle": CATEGORY,
            "gcmtype": "file",
            "gcmlimit": "50",
            "prop": "imageinfo",
            "iiprop": "url|mime|size|extmetadata",
            "iiurlwidth": str(width),
        }
        params.update(continuation)
        data = api(params)

        for page in data.get("query", {}).get("pages", []):
            info = (page.get("imageinfo") or [{}])[0]
            source_width = info.get("width") or 0
            source_height = info.get("height") or 0
            if info.get("mime") not in SOURCE_MIMES:
                continue
            if not info.get("thumburl"):
                continue
            # Landscape only, and big enough that the 1080p fit is not an upscale.
            if source_width <= source_height or source_width < width or source_height < height:
                continue
            metadata = info.get("extmetadata") or {}
            found.append(
                {
                    "title": page.get("title", ""),
                    "url": info["thumburl"],
                    "page": info.get("descriptionurl", ""),
                    "licence": strip_html((metadata.get("LicenseShortName") or {}).get("value")),
                }
            )
            if len(found) >= wanted:
                break

        continuation = data.get("continue") or {}
        if not continuation:
            break
    return found


def download(url, destination, attempts=5):
    """Download url to destination, backing off when Commons rate limits."""
    for attempt in range(1, attempts + 1):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=120) as response:
                with open(destination, "wb") as output:
                    shutil.copyfileobj(response, output)
            return
        except urllib.error.HTTPError as error:
            if attempt == attempts:
                raise
            retry_after = (error.headers or {}).get("Retry-After")
            wait = float(retry_after) if retry_after and retry_after.isdigit() else 2.0**attempt
            wait = min(wait, 60.0)
            print(f"    HTTP {error.code}, waiting {wait:.0f}s (attempt {attempt}/{attempts})", flush=True)
            time.sleep(wait)
        except Exception as error:  # noqa: BLE001 - retried below, raised if final
            if attempt == attempts:
                raise
            print(f"    retry {attempt}/{attempts} after {error}", flush=True)
            time.sleep(float(attempt))


def sips(*arguments):
    subprocess.run(["sips", *arguments], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def probe_size(path):
    """Return the pixel dimensions sips reports for a file."""
    output = subprocess.run(
        ["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(path)],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout
    width = int(output.split("pixelWidth:")[1].split()[0])
    height = int(output.split("pixelHeight:")[1].split()[0])
    return width, height


def tiff_compression(path):
    """Read the TIFF Compression tag (259) so the label can be trusted."""
    import struct

    with open(path, "rb") as handle:
        header = handle.read(8)
        order = ">" if header[:2] == b"MM" else "<"
        _, ifd_offset = struct.unpack(order + "HI", header[2:8])
        handle.seek(ifd_offset)
        (entry_count,) = struct.unpack(order + "H", handle.read(2))
        for _ in range(entry_count):
            entry = handle.read(12)
            tag, field_type, count = struct.unpack(order + "HHI", entry[:8])
            if tag == 259 and field_type == 3 and count == 1:
                (value,) = struct.unpack(order + "H", entry[8:10])
                return TIFF_COMPRESSION_TAGS.get(value, str(value))
    return "unknown"


def fit_and_convert(source, fitted, destination, compression, width, height):
    """Fit source to width x height by centre crop, then write the TIFF."""
    source_width, source_height = probe_size(source)
    # Fill whichever dimension the aspect ratio makes tight, then crop the overflow.
    if source_width * height >= source_height * width:
        resize = ["--resampleHeight", str(height)]
    else:
        resize = ["--resampleWidth", str(width)]
    sips(*resize, "-c", str(height), str(width), "-s", "format", "png", str(source), "--out", str(fitted))

    # A separate pass, because sips drops formatOptions when it also resamples.
    command = ["-s", "format", "tiff"]
    if compression != "none":
        command += ["-s", "formatOptions", compression]
    sips(*command, str(fitted), "--out", str(destination))


def on_disk_name(directory, name):
    """Return the directory entry as stored, which macOS may keep decomposed."""
    for entry in os.listdir(directory):
        if unicodedata.normalize("NFC", entry) == unicodedata.normalize("NFC", name):
            return entry
    return name


def output_name(index, compression):
    """Pick the file name for an entry, including the extension/.tiff variety."""
    if index in ODD_NAMES:
        return ODD_NAMES[index]
    extension = "tiff" if index % TIFF_EVERY == 0 else "tif"
    return f"tiff-{index:03d}-{compression}.{extension}"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--limit", type=int, default=50, help="number of images to produce (default 50)")
    parser.add_argument("--out", default="local/tiff-test-images", help="output directory")
    parser.add_argument("--width", type=int, default=1920, help="output width in pixels (default 1920)")
    parser.add_argument("--height", type=int, default=1080, help="output height in pixels (default 1080)")
    parser.add_argument("--delay", type=float, default=1.0, help="seconds to wait between downloads (default 1.0)")
    parser.add_argument("--force", action="store_true", help="re-download and overwrite existing images")
    args = parser.parse_args()

    if shutil.which("sips") is None:
        sys.exit("sips not found - this script needs macOS for the image work")

    out_dir = Path(args.out).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Searching Wikimedia Commons for {args.limit} landscape images ({args.width}x{args.height})...", flush=True)
    images = find_images(args.limit, args.width, args.height)
    if not images:
        sys.exit("No usable images found - the Commons category layout may have changed")
    if len(images) < args.limit:
        print(f"Only found {len(images)} suitable images", flush=True)

    rows = []
    created = 0
    with tempfile.TemporaryDirectory() as scratch:
        for index, image in enumerate(images, start=1):
            compression = COMPRESSIONS[(index - 1) % len(COMPRESSIONS)]
            name = output_name(index, compression)
            destination = out_dir / name

            if destination.exists() and not args.force:
                print(f"[{index}/{len(images)}] {name} (exists, skipped)", flush=True)
                continue

            print(f"[{index}/{len(images)}] {name}", flush=True)
            original = Path(scratch) / f"source-{index}"
            fitted = Path(scratch) / f"fitted-{index}.png"
            try:
                download(image["url"], original)
                fit_and_convert(original, fitted, destination, compression, args.width, args.height)
            except Exception as error:  # noqa: BLE001 - one failure should not stop the set
                print(f"    failed: {error}", flush=True)
                destination.unlink(missing_ok=True)
                continue

            actual = tiff_compression(destination)
            if actual != compression:
                # Never leave a file labelled with a compression it does not use.
                renamed = destination.with_name(name.replace(f"-{compression}", f"-{actual}"))
                destination.rename(renamed)
                destination = renamed
                name = renamed.name
                print(f"    note: sips wrote {actual} instead of {compression}, renamed to {name}", flush=True)

            created += 1
            rows.append(
                {
                    "file": on_disk_name(out_dir, name),
                    "compression": actual,
                    "source_title": image["title"],
                    "licence": image["licence"],
                    "source_url": image["page"],
                    "download_url": image["url"],
                }
            )
            time.sleep(args.delay)  # be polite to Wikimedia

    if rows:
        csv_path = out_dir / "sources.csv"
        mode = "w" if args.force else "a"
        write_header = mode == "w" or not csv_path.exists()
        with open(csv_path, mode, newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
            if write_header:
                writer.writeheader()
            writer.writerows(rows)

    tiffs = sorted(out_dir.glob("tiff*"))
    total = sum(path.stat().st_size for path in tiffs)
    print(
        f"\nDone: {created} converted, {len(tiffs)} TIFFs in {out_dir}"
        f" ({total / (1024 * 1024):.1f} MB). Sources: {out_dir / 'sources.csv'}",
        flush=True,
    )


if __name__ == "__main__":
    main()
