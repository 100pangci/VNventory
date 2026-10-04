#!/usr/bin/env python3
"""Validate versioned, signed Android releases and prepare their APK asset."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil


INT_MAX = 2_147_483_647
VERSION_PATTERN = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")
SIGNING_SECRETS = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)


def version_code(version_name: str) -> int:
    match = VERSION_PATTERN.fullmatch(version_name)
    if match is None:
        raise ValueError("version must use MAJOR.MINOR.PATCH without leading zeroes")
    major, minor, patch = map(int, match.groups())
    if minor >= 1000 or patch >= 1000:
        raise ValueError("MINOR and PATCH must each be below 1000")
    if major > INT_MAX // 1_000_000:
        raise ValueError("version produces an Android versionCode that is too large")
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= code <= INT_MAX:
        raise ValueError("version produces an invalid Android versionCode")
    return code


def validate_tag(tag: str) -> tuple[str, int]:
    if not tag.startswith("v"):
        raise ValueError("Release tags must use vMAJOR.MINOR.PATCH, for example v1.2.3")
    version_name = tag[1:]
    try:
        code = version_code(version_name)
    except ValueError as error:
        raise ValueError(
            "Release tags must use a supported vMAJOR.MINOR.PATCH version, for example v1.2.3"
        ) from error
    return version_name, code


def missing_signing_secrets(values: dict[str, str | None]) -> list[str]:
    return [name for name in SIGNING_SECRETS if not (values.get(name) or "").strip()]


def prepare_release_asset(apk: Path, version_name: str, output_dir: Path) -> Path:
    version_code(version_name)
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError(f"Release APK is missing or empty: {apk}")
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / f"VNventory-v{version_name}.apk"
    if apk.resolve() == target.resolve():
        raise ValueError("Release APK source and asset destination must differ")
    shutil.copyfile(apk, target)
    return target


def validate_apk_metadata(metadata: Path, apk: Path, version_name: str) -> None:
    expected_code = version_code(version_name)
    document = json.loads(metadata.read_text(encoding="utf-8"))
    elements = document.get("elements", [])
    if len(elements) != 1:
        raise ValueError("Release must contain exactly one APK metadata entry")
    entry = elements[0]
    if entry.get("versionName") != version_name or entry.get("versionCode") != expected_code:
        raise ValueError("Built APK version does not match the release tag")
    if entry.get("outputFile") != apk.name:
        raise ValueError("Release APK does not match build output metadata")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    tag_parser = subparsers.add_parser("validate-tag", help="validate and export tag version")
    tag_parser.add_argument("tag")

    subparsers.add_parser("validate-signing", help="require all release signing secrets")

    asset_parser = subparsers.add_parser("prepare-apk", help="check and name the signed APK asset")
    asset_parser.add_argument("version")
    asset_parser.add_argument("apk", type=Path)
    asset_parser.add_argument("output_dir", type=Path)
    asset_parser.add_argument("--metadata", type=Path, required=True)

    args = parser.parse_args()
    try:
        if args.command == "validate-tag":
            version_name, code = validate_tag(args.tag)
            print(f"name={version_name}")
            print(f"version_code={code}")
        elif args.command == "validate-signing":
            missing = missing_signing_secrets({name: os.environ.get(name) for name in SIGNING_SECRETS})
            if missing:
                raise ValueError("Missing required release signing secrets: " + ", ".join(missing))
            print("configured=true")
        else:
            validate_apk_metadata(args.metadata, args.apk, args.version)
            target = prepare_release_asset(args.apk, args.version, args.output_dir)
            print(f"path={target.name}")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
