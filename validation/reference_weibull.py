#!/usr/bin/env python3
"""Independent standard-library reference implementation for validation."""

import csv
import math
from pathlib import Path
import sys


def read_arff(path):
    rows, data = [], False
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("%"):
            continue
        if line.lower() == "@data":
            data = True
            continue
        if data:
            event, duration = line.split(",")
            rows.append((float(duration), int(float(event))))
    return rows


def fit(rows):
    logs = [math.log(time) for time, _ in rows]
    event_count = sum(event for _, event in rows)
    event_log_mean = sum(event * log for log, (_, event) in zip(logs, rows)) / event_count

    def score(beta):
        maximum = max(beta * value for value in logs)
        terms = [math.exp(beta * value - maximum) for value in logs]
        weighted_mean = sum(term * value for term, value in zip(terms, logs)) / sum(terms)
        return 1.0 / beta + event_log_mean - weighted_mean

    low, high = 1.0e-8, 1.0
    while score(high) > 0:
        high *= 2.0
    for _ in range(300):
        middle = (low + high) / 2.0
        if score(middle) > 0:
            low = middle
        else:
            high = middle
    beta = (low + high) / 2.0
    maximum = max(beta * value for value in logs)
    log_sum = maximum + math.log(sum(math.exp(beta * value - maximum) for value in logs))
    log_eta = (log_sum - math.log(event_count)) / beta
    eta = math.exp(log_eta)
    log_likelihood = sum(event * (math.log(beta) - beta * log_eta + (beta - 1) * math.log(time))
                         - math.exp(beta * (math.log(time) - log_eta))
                         for time, event in rows)
    n = len(rows)
    return {
        "observations": n,
        "events": event_count,
        "censored": n - event_count,
        "beta": beta,
        "eta": eta,
        "log_likelihood": log_likelihood,
        "aic": 4 - 2 * log_likelihood,
        "bic": 2 * math.log(n) - 2 * log_likelihood,
        "median": eta * math.log(2) ** (1 / beta),
    }


def main():
    output, paths = Path(sys.argv[1]), sys.argv[2:]
    fields = ["dataset", "observations", "events", "censored", "beta", "eta",
              "log_likelihood", "aic", "bic", "median"]
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for path in paths:
            result = fit(read_arff(path))
            result["dataset"] = Path(path).stem
            writer.writerow(result)


if __name__ == "__main__":
    main()
