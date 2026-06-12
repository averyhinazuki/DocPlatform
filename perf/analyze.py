#!/usr/bin/env python3
"""Summarize the quota load test: per-tenant status codes, latency percentiles,
and (optionally) max concurrent jobs observed in the Redis counter samples.

Usage: analyze.py <results.jtl> [counter-samples.txt]
"""
import csv
import sys
from collections import Counter, defaultdict


def pct(sorted_vals, p):
    if not sorted_vals:
        return 0
    idx = min(len(sorted_vals) - 1, round(p / 100 * len(sorted_vals)) - 1)
    return sorted_vals[max(idx, 0)]


def main():
    jtl = sys.argv[1]
    codes = defaultdict(Counter)
    lat = defaultdict(list)
    with open(jtl, newline="") as f:
        for row in csv.DictReader(f):
            label = row["label"]
            codes[label][row["responseCode"]] += 1
            lat[label].append(int(row["elapsed"]))

    for label in sorted(codes):
        ls = sorted(lat[label])
        total = sum(codes[label].values())
        print(f"\n{label}  ({total} samples)")
        for code, n in sorted(codes[label].items()):
            print(f"  HTTP {code}: {n}  ({100 * n / total:.1f}%)")
        print(f"  latency ms: p50={pct(ls, 50)} p95={pct(ls, 95)} p99={pct(ls, 99)} max={ls[-1] if ls else 0}")

    if len(sys.argv) > 2:
        maxes = [0, 0]
        with open(sys.argv[2]) as f:
            for line in f:
                parts = line.split()
                if len(parts) == 2:
                    for i, v in enumerate(parts):
                        try:
                            maxes[i] = max(maxes[i], int(v))
                        except ValueError:
                            pass
        print(f"\nmax concurrent jobs observed (Redis counter): noisy={maxes[0]} quiet={maxes[1]}")


if __name__ == "__main__":
    main()
