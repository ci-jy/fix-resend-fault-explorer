"""Plots docs/results.png from results/schedules.csv (written by scripts/explore.sh)."""
import csv
import os
from collections import defaultdict

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LABELS = {"STOCK": "stock 2.3.2", "WORKAROUND": "stock +\nRedundantResends=Y", "PATCHED": "patched\n(#1114 fix)"}

totals = defaultdict(lambda: defaultdict(int))
with open(os.path.join(ROOT, "results", "schedules.csv")) as f:
    for r in csv.DictReader(f):
        t = totals[r["build"]]
        t["schedules"] += 1
        t["losing"] += int(r["lost"]) > 0
        t["dup_rr"] += int(r["duplicate_resend_requests"])

builds = [b for b in ("STOCK", "WORKAROUND", "PATCHED") if b in totals]
fig, axes = plt.subplots(1, 2, figsize=(10, 3.8))
colors = ["#c0392b", "#e67e22", "#27ae60"]
for ax, key, title in [(axes[0], "losing", "Schedules that lose application messages"),
                       (axes[1], "dup_rr", "Duplicate ResendRequests sent")]:
    vals = [totals[b][key] for b in builds]
    bars = ax.bar([LABELS[b] for b in builds], vals, color=colors[:len(builds)])
    ax.bar_label(bars, labels=[f"{v:,}" for v in vals])
    ax.set_title(title, fontsize=11)
    ax.spines[["top", "right"]].set_visible(False)
    ax.set_ylim(0, max(vals) * 1.18 + 1)
n = ", ".join(f"{b.lower()} {totals[b]['schedules']:,}" for b in builds)
fig.suptitle(f"Every single and double disconnect point, QuickFIX/J 2.3.2 (schedules: {n})", fontsize=10)
fig.tight_layout()
os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
fig.savefig(os.path.join(ROOT, "docs", "results.png"), dpi=130)
print("wrote docs/results.png")
