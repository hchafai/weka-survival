#!/usr/bin/env python3
import csv
import math
import sys

left_path, right_path, tolerance = sys.argv[1], sys.argv[2], float(sys.argv[3])
with open(left_path, newline="", encoding="utf-8") as left_file, open(right_path, newline="", encoding="utf-8") as right_file:
    left = list(csv.DictReader(left_file))
    right = list(csv.DictReader(right_file))
if len(left) != len(right):
    raise SystemExit("Result row count differs")
for left_row, right_row in zip(left, right):
    if left_row["dataset"] != right_row["dataset"]:
        raise SystemExit("Dataset order/name differs")
    for field in left_row:
        if field == "dataset":
            continue
        first, second = float(left_row[field]), float(right_row[field])
        scale = max(1.0, abs(first), abs(second))
        if not math.isfinite(first) or not math.isfinite(second) or abs(first - second) > tolerance * scale:
            raise SystemExit(f"{left_row['dataset']} {field}: {first} != {second}")
print(f"Java/Weka agrees with the independent reference within relative tolerance {tolerance:g}.")
