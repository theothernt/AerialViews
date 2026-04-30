#!/usr/bin/env python3
"""
Copy `timeOfDay` and `scene` metadata from `tvos15.json` into matching assets in
`tvos26.json` based on shared video IDs.

Usage:
    python3 scripts/copy_tvos15_metadata_to_tvos26.py --check
    python3 scripts/copy_tvos15_metadata_to_tvos26.py --write
"""

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "app/src/main/res/raw"
TVOS15_PATH = RAW_DIR / "tvos15.json"
TVOS26_PATH = RAW_DIR / "tvos26.json"

VALID_TIME_OF_DAY = {"day", "night", "sunrise", "sunset"}
VALID_SCENE = {
    "nature",
    "countryside",
    "waterfall",
    "beach",
    "city",
    "sea",
    "space",
    "patterns",
    "fire",
}


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_json(path, payload):
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=4, ensure_ascii=False)
        handle.write("\n")


def normalize_value(value):
    if value is None:
        return None
    if not isinstance(value, str):
        return value
    stripped = value.strip().lower()
    return stripped or None


def validate_tvos15_metadata(assets):
    errors = []

    for asset in assets:
        video_id = asset.get("id")
        time_of_day = normalize_value(asset.get("timeOfDay"))
        scene = normalize_value(asset.get("scene"))

        if time_of_day not in VALID_TIME_OF_DAY:
            errors.append(
                "Invalid or missing timeOfDay for %s: %r" % (video_id, time_of_day)
            )

        if scene not in VALID_SCENE:
            errors.append("Invalid or missing scene for %s: %r" % (video_id, scene))

    return errors


def insert_metadata_fields(asset, time_of_day, scene):
    result = {}
    inserted = False

    for key, value in asset.items():
        if key in {"timeOfDay", "scene"}:
            continue

        result[key] = value
        if key == "pointsOfInterest":
            result["timeOfDay"] = time_of_day
            result["scene"] = scene
            inserted = True

    if not inserted:
        result["timeOfDay"] = time_of_day
        result["scene"] = scene

    return result


def build_tvos15_index(assets):
    by_id = {}
    duplicates = []

    for asset in assets:
        video_id = asset.get("id")
        if video_id in by_id:
            duplicates.append(video_id)
            continue
        by_id[video_id] = {
            "timeOfDay": normalize_value(asset.get("timeOfDay")),
            "scene": normalize_value(asset.get("scene")),
            "title": asset.get("accessibilityLabel", ""),
        }

    return by_id, duplicates


def summarize_transfer(tvos15_assets, tvos26_assets):
    tvos15_by_id, duplicates = build_tvos15_index(tvos15_assets)
    common_ids = []
    tvos26_only = []
    already_populated = []

    for asset in tvos26_assets:
        video_id = asset.get("id")
        if video_id in tvos15_by_id:
            common_ids.append(video_id)
            if asset.get("timeOfDay") or asset.get("scene"):
                already_populated.append(video_id)
        else:
            tvos26_only.append(
                {
                    "id": video_id,
                    "title": asset.get("accessibilityLabel", ""),
                }
            )

    return tvos15_by_id, duplicates, common_ids, tvos26_only, already_populated


def run_check(tvos15_assets, tvos26_assets):
    errors = validate_tvos15_metadata(tvos15_assets)
    if errors:
        for error in errors:
            print("ERROR:", error, file=sys.stderr)
        return 1

    tvos15_by_id, duplicates, common_ids, tvos26_only, already_populated = summarize_transfer(
        tvos15_assets, tvos26_assets
    )

    if duplicates:
        print("ERROR: duplicate ids found in tvos15.json:", file=sys.stderr)
        for video_id in duplicates:
            print(" - %s" % video_id, file=sys.stderr)
        return 1

    print("tvos15 assets:", len(tvos15_assets))
    print("tvos26 assets:", len(tvos26_assets))
    print("shared ids:", len(common_ids))
    print("tvos26-only ids:", len(tvos26_only))
    print("tvos26 assets already containing metadata:", len(already_populated))

    if tvos26_only:
        print("Newer tvos26 assets without tvos15 match:")
        for asset in tvos26_only:
            print("- %s | %s" % (asset["id"], asset["title"]))

    sample_id = common_ids[0] if common_ids else None
    if sample_id:
        print(
            "Example copied metadata: %s -> timeOfDay=%s scene=%s"
            % (
                sample_id,
                tvos15_by_id[sample_id]["timeOfDay"],
                tvos15_by_id[sample_id]["scene"],
            )
        )

    return 0


def run_write(tvos15_assets, tvos26_payload, force):
    tvos26_assets = tvos26_payload.get("assets", [])
    errors = validate_tvos15_metadata(tvos15_assets)
    if errors:
        for error in errors:
            print("ERROR:", error, file=sys.stderr)
        return 1

    tvos15_by_id, duplicates, common_ids, tvos26_only, already_populated = summarize_transfer(
        tvos15_assets, tvos26_assets
    )

    if duplicates:
        print("ERROR: duplicate ids found in tvos15.json:", file=sys.stderr)
        for video_id in duplicates:
            print(" - %s" % video_id, file=sys.stderr)
        return 1

    if already_populated and not force:
        print(
            "ERROR: some tvos26 assets already have timeOfDay/scene. Use --force to overwrite.",
            file=sys.stderr,
        )
        for video_id in already_populated[:10]:
            print(" - %s" % video_id, file=sys.stderr)
        if len(already_populated) > 10:
            print(" - ... and %d more" % (len(already_populated) - 10), file=sys.stderr)
        return 1

    updated_assets = []
    copied = 0
    untouched = 0

    for asset in tvos26_assets:
        video_id = asset.get("id")
        metadata = tvos15_by_id.get(video_id)
        if metadata is None:
            updated_assets.append(asset)
            untouched += 1
            continue

        updated_assets.append(
            insert_metadata_fields(asset, metadata["timeOfDay"], metadata["scene"])
        )
        copied += 1

    tvos26_payload["assets"] = updated_assets
    write_json(TVOS26_PATH, tvos26_payload)

    print("Copied metadata for %d shared assets." % copied)
    print("Left %d tvos26-only assets unchanged." % untouched)
    if tvos26_only:
        print("Remaining unmatched tvos26 assets: %d" % len(tvos26_only))

    return 0


def main():
    parser = argparse.ArgumentParser(
        description="Copy timeOfDay/scene data from tvos15.json to matching ids in tvos26.json."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate inputs and print a transfer summary without writing.",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Write copied metadata into tvos26.json.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Overwrite existing tvos26 timeOfDay/scene values if present.",
    )
    args = parser.parse_args()

    tvos15_payload = load_json(TVOS15_PATH)
    tvos26_payload = load_json(TVOS26_PATH)
    tvos15_assets = tvos15_payload.get("assets", [])
    tvos26_assets = tvos26_payload.get("assets", [])

    if args.write:
        return run_write(tvos15_assets, tvos26_payload, force=args.force)

    return run_check(tvos15_assets, tvos26_assets)


if __name__ == "__main__":
    raise SystemExit(main())
