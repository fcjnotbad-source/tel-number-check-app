#!/usr/bin/env python3
"""Import pinned MIT administrative names, never phone-number attribution data."""
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import urllib.request

COMMIT = "a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc"
REPOSITORY = "uiwjs/province-city-china"
SOURCE_FILES = {
    "province.json": "7d7ee447e058bec7e2918ee78241be1341c4471ac8483e6b34eecfd2e154796e",
    "city.json": "450f63fd503878c5a5fee36aa539dce0916a91e48200aa49034320eb4ff9185e",
    "area.json": "c286dffaf5d8b28316f77a343d3d0bba01efbc0570780bc90ef81a71fc73dca1",
    "LICENSE": "becf557b4dbb188022e699c2a2eb84828325252e992796b9e102deb303348359",
}
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/resources/geo/province-cities.tsv"


def source_path(name):
    return name if name == "LICENSE" else "packages/core/dist/" + name


def download(directory):
    for name in SOURCE_FILES:
        url = f"https://api.github.com/repos/{REPOSITORY}/contents/{source_path(name)}?ref={COMMIT}"
        request = urllib.request.Request(url, headers={"Accept": "application/vnd.github.raw+json"})
        with urllib.request.urlopen(request, timeout=30) as response:
            (directory / name).write_bytes(response.read())


def read_verified(directory, name):
    content = (directory / name).read_bytes()
    actual_hash = hashlib.sha256(content).hexdigest()
    if actual_hash != SOURCE_FILES[name]:
        raise ValueError(f"Pinned {name} SHA-256 mismatch: {actual_hash}")
    return content


def is_aggregate(name):
    return name in {"市辖区", "县"} or "直辖县级行政区划" in name


def convert(directory):
    provinces = json.loads(read_verified(directory, "province.json"))
    cities = json.loads(read_verified(directory, "city.json"))
    areas = json.loads(read_verified(directory, "area.json"))
    read_verified(directory, "LICENSE")
    province_by_prefix = {row["province"]: row for row in provinces}
    pairs = {}

    def add(row):
        parent = province_by_prefix[row["province"]]
        assert len(row["code"]) == 6 and row["code"].startswith(row["province"])
        assert not is_aggregate(row["name"])
        key = (parent["code"], row["code"])
        value = (parent["code"], parent["name"], row["code"], row["name"])
        if key in pairs and pairs[key] != value:
            raise ValueError(f"Conflicting source pair: {key}")
        pairs[key] = value

    for row in cities:
        if not is_aggregate(row["name"]):
            add(row)
    # These municipalities have no separate real prefecture row: use their own source name/code.
    for prefix in ("11", "12", "31", "50"):
        add(province_by_prefix[prefix])
    aggregate_prefixes = {row["code"][:4] for row in cities if "直辖县级行政区划" in row["name"]}
    for row in areas:
        if row["code"][:4] in aggregate_prefixes and not is_aggregate(row["name"]):
            add(row)

    rows = [pairs[key] for key in sorted(pairs)]
    assert len(rows) == 370, "Unexpected source coverage; inspect any upstream/schema change."
    assert len({row[0] for row in rows}) == 31
    assert all("\t" not in value and "\n" not in value for row in rows for value in row)
    return "".join("\t".join(row) + "\n" for row in rows).encode("utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, help="Reuse already downloaded pinned JSON/LICENSE files.")
    parser.add_argument("--check", action="store_true", help="Compare regenerated content without modifying the resource.")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="tel-geo-import-") as temporary:
        directory = args.source_dir or Path(temporary)
        if args.source_dir is None:
            download(directory)
        content = convert(directory)
    if args.check:
        if OUTPUT.read_bytes() != content:
            raise SystemExit("Checked-in province-cities.tsv differs from the pinned import.")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(content)
    print(f"Verified {len(content.splitlines())} province/city pairs; SHA-256 {hashlib.sha256(content).hexdigest()}")


if __name__ == "__main__":
    main()
