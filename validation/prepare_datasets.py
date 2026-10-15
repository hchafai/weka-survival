#!/usr/bin/env python3
"""Download, verify and reduce the two public validation datasets."""

import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import urllib.request
import zipfile

SOURCES = {
    "php": {
        "url": "https://zenodo.org/api/records/7626150/files/data.zip/content",
        "sha256": "0207b0869664121c2bf49e98ab4fc950b65749445079ae48bd923f223bfd926c",
        "member": "data/18CS_survival_format/all_apps_ss_evol_18.csv",
        "doi": "10.5281/zenodo.7626150",
        "license": "CC-BY-4.0",
    },
    "java": {
        "url": "https://zenodo.org/api/records/16230542/files/Smell%20Survival%20data.zip/content",
        "sha256": "a46def35c3e94fde1e650c418363e94944766d257682be6d300c311a194e5ff6",
        "member": "Smell Survival data/RQ1 Data/survival_regression_ml_ready_final_cleaned (Used for survival regression).csv",
        "doi": "10.5281/zenodo.16230542",
        "license": "CC-BY-4.0",
    },
}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def archive(name, source, cache, supplied):
    target = Path(supplied).resolve() if supplied else cache / (name + ".zip")
    if not target.exists():
        cache.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(".part")
        with urllib.request.urlopen(source["url"], timeout=120) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output)
        os.replace(partial, target)
    actual = digest(target)
    if actual != source["sha256"]:
        raise RuntimeError(f"SHA-256 mismatch for {target}: {actual}")
    return target


def write_arff(path, relation, rows):
    with path.open("w", encoding="utf-8", newline="\n") as output:
        output.write(f"@relation {relation}\n\n@attribute event numeric\n@attribute duration numeric\n\n@data\n")
        for event, duration in rows:
            output.write(f"{event},{duration:.15g}\n")


def php_rows(path, member):
    result, excluded = [], 0
    with zipfile.ZipFile(path) as archive_file, archive_file.open(member) as raw:
        reader = csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""), delimiter=";")
        for row in reader:
            duration = float(row["diff"])
            if not duration > 0:
                excluded += 1
                continue
            # Source semantics: 1 means the smell was removed (event observed).
            result.append((1 if row["censored"].strip() == "1" else 0, duration))
    return result, excluded


def java_rows(path, member):
    result, excluded = [], 0
    with zipfile.ZipFile(path) as archive_file, archive_file.open(member) as raw:
        reader = csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8-sig", newline=""))
        for row in reader:
            try:
                duration = float(row["duration"])
                event = int(float(row["event"]))
            except (TypeError, ValueError):
                excluded += 1
                continue
            if duration <= 0 or event not in (0, 1):
                excluded += 1
                continue
            result.append((event, duration))
    return result, excluded


def summary(name, source, rows, excluded):
    events = sum(row[0] for row in rows)
    return {
        "dataset": name,
        "doi": source["doi"],
        "license": source["license"],
        "observations": len(rows),
        "events": events,
        "censored": len(rows) - events,
        "excluded_nonpositive_or_invalid": excluded,
        "archive_sha256": source["sha256"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--php-archive")
    parser.add_argument("--java-archive")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    args.manifest.parent.mkdir(parents=True, exist_ok=True)

    php_archive = archive("php-code-smells", SOURCES["php"], args.cache, args.php_archive)
    java_archive = archive("java-code-test-smells", SOURCES["java"], args.cache, args.java_archive)
    php, php_excluded = php_rows(php_archive, SOURCES["php"]["member"])
    java, java_excluded = java_rows(java_archive, SOURCES["java"]["member"])
    write_arff(args.output / "php-code-smells.arff", "php_code_smell_survival", php)
    write_arff(args.output / "java-code-test-smells.arff", "java_code_test_smell_survival", java)
    manifest = [summary("php-code-smells", SOURCES["php"], php, php_excluded),
                summary("java-code-test-smells", SOURCES["java"], java, java_excluded)]
    args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
