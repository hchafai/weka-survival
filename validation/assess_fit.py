#!/usr/bin/env python3
"""Assess Weibull fit against Kaplan-Meier and an exponential baseline."""

import csv
import math
from collections import defaultdict
from pathlib import Path
import sys


def read_arff(path):
    rows, in_data = [], False
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("%"):
            continue
        if line.lower() == "@data":
            in_data = True
        elif in_data:
            event, duration = line.split(",")
            rows.append((float(duration), int(float(event))))
    return rows


def km_diagnostics(rows, beta, eta):
    grouped = defaultdict(lambda: [0, 0])
    for time, event in rows:
        grouped[time][0 if event else 1] += 1
    risk = len(rows)
    survival = 1.0
    squared_error = 0.0
    total_events = 0
    maximum_error = 0.0
    plot_x, plot_y = [], []
    for time in sorted(grouped):
        events, censored = grouped[time]
        if events:
            survival *= 1.0 - events / risk
            fitted = math.exp(-math.exp(beta * (math.log(time) - math.log(eta))))
            difference = fitted - survival
            squared_error += events * difference * difference
            total_events += events
            maximum_error = max(maximum_error, abs(difference))
            if 0.0 < survival < 1.0:
                plot_x.append(math.log(time))
                plot_y.append(math.log(-math.log(survival)))
        risk -= events + censored
    mean_x = sum(plot_x) / len(plot_x)
    mean_y = sum(plot_y) / len(plot_y)
    slope = sum((x - mean_x) * (y - mean_y) for x, y in zip(plot_x, plot_y)) / sum((x - mean_x) ** 2 for x in plot_x)
    intercept = mean_y - slope * mean_x
    residual = sum((y - intercept - slope * x) ** 2 for x, y in zip(plot_x, plot_y))
    total = sum((y - mean_y) ** 2 for y in plot_y)
    return math.sqrt(squared_error / total_events), maximum_error, 1.0 - residual / total


def exponential_log_likelihood(rows):
    events = sum(event for _, event in rows)
    eta = sum(time for time, _ in rows) / events
    return sum(event * (-math.log(eta)) - time / eta for time, event in rows)


def main():
    result_path, output_path, *arff_paths = sys.argv[1:]
    with open(result_path, newline="", encoding="utf-8") as stream:
        estimates = {row["dataset"]: row for row in csv.DictReader(stream)}
    fields = ["dataset", "exponential_log_likelihood", "likelihood_ratio",
              "delta_aic_exponential_minus_weibull", "km_event_weighted_rmse",
              "km_max_absolute_error", "weibull_plot_r_squared"]
    with open(output_path, "w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for path in arff_paths:
            name = Path(path).stem
            rows = read_arff(path)
            beta = float(estimates[name]["beta"])
            eta = float(estimates[name]["eta"])
            weibull_ll = float(estimates[name]["log_likelihood"])
            exponential_ll = exponential_log_likelihood(rows)
            rmse, maximum, r_squared = km_diagnostics(rows, beta, eta)
            exponential_aic = 2.0 - 2.0 * exponential_ll
            weibull_aic = 4.0 - 2.0 * weibull_ll
            writer.writerow({
                "dataset": name,
                "exponential_log_likelihood": exponential_ll,
                "likelihood_ratio": 2.0 * (weibull_ll - exponential_ll),
                "delta_aic_exponential_minus_weibull": exponential_aic - weibull_aic,
                "km_event_weighted_rmse": rmse,
                "km_max_absolute_error": maximum,
                "weibull_plot_r_squared": r_squared,
            })


if __name__ == "__main__":
    main()
