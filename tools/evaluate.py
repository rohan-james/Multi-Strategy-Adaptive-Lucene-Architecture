#!/usr/bin/env python3
import argparse
import csv
from collections import defaultdict
from pathlib import Path

# Simple evaluator for TREC-style runs. Computes MAP, P@20, nDCG@20.

def read_qrels(qrels_path):
    qrels = defaultdict(dict)  # topic -> docid -> rel (0/1)
    with open(qrels_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            # Expected: topic q0 docid rel
            if len(parts) < 4:
                continue
            qid, _, docid, rel = parts[0], parts[1], parts[2], int(parts[3])
            qrels[qid][docid] = 1 if rel > 0 else 0
    return qrels

def read_run(run_path):
    run = defaultdict(list)  # topic -> list of docids in rank order
    with open(run_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            # Expected: qid Q0 docid rank score tag
            if len(parts) < 6:
                continue
            qid, docid, rank = parts[0], parts[2], int(parts[3])
            run[qid].append((rank, docid))
    # sort by rank
    for qid in list(run.keys()):
        run[qid].sort(key=lambda x: x[0])
        run[qid] = [docid for _, docid in run[qid]]
    return run

def average_precision(retrieved, relevant_set):
    if not relevant_set:
        return 0.0
    num_rel = 0
    precisions = []
    for i, d in enumerate(retrieved, start=1):
        if d in relevant_set:
            num_rel += 1
            precisions.append(num_rel / i)
    if not precisions:
        return 0.0
    return sum(precisions) / len(relevant_set)

def precision_at_k(retrieved, relevant_set, k):
    if k <= 0:
        return 0.0
    topk = retrieved[:k]
    hits = sum(1 for d in topk if d in relevant_set)
    return hits / k

def dcg_at_k(retrieved, relevant_set, k):
    import math
    dcg = 0.0
    for i, d in enumerate(retrieved[:k], start=1):
        rel = 1.0 if d in relevant_set else 0.0
        if i == 1:
            dcg += rel
        else:
            dcg += rel / math.log2(i)
    return dcg

def ndcg_at_k(retrieved, relevant_set, k):
    ideal = dcg_at_k(sorted(list(relevant_set)), relevant_set, k)  # ideal: all rel first
    if ideal == 0.0:
        return 0.0
    return dcg_at_k(retrieved, relevant_set, k) / ideal

def evaluate(qrels, run):
    ap_scores = []
    p20_scores = []
    ndcg20_scores = []
    for qid, rels in qrels.items():
        relevant_set = {d for d, r in rels.items() if r > 0}
        retrieved = run.get(qid, [])
        ap_scores.append(average_precision(retrieved, relevant_set))
        p20_scores.append(precision_at_k(retrieved, relevant_set, 20))
        ndcg20_scores.append(ndcg_at_k(retrieved, relevant_set, 20))
    metrics = {
        "MAP": sum(ap_scores) / len(ap_scores) if ap_scores else 0.0,
        "P@20": sum(p20_scores) / len(p20_scores) if p20_scores else 0.0,
        "nDCG@20": sum(ndcg20_scores) / len(ndcg20_scores) if ndcg20_scores else 0.0,
    }
    return metrics

def main():
    p = argparse.ArgumentParser(description="Evaluate TREC run(s) and output standings")
    p.add_argument("qrels", type=Path, help="Path to qrels file")
    p.add_argument("runs", nargs='+', type=Path, help="Run files to evaluate (one or more)")
    p.add_argument("--out_csv", type=Path, default=Path("standings.csv"), help="Output CSV path")
    p.add_argument("--out_md", type=Path, default=Path("standings.md"), help="Output Markdown path")
    args = p.parse_args()

    qrels = read_qrels(args.qrels)
    rows = []
    for run_path in args.runs:
        run = read_run(run_path)
        metrics = evaluate(qrels, run)
        rows.append({
            "run": run_path.name,
            "MAP": metrics["MAP"],
            "P@20": metrics["P@20"],
            "nDCG@20": metrics["nDCG@20"],
        })

    rows.sort(key=lambda r: (r["MAP"], r["nDCG@20"], r["P@20"]), reverse=True)

    # Write CSV
    args.out_csv.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["rank", "run", "MAP", "P@20", "nDCG@20"])
        w.writeheader()
        for i, r in enumerate(rows, start=1):
            w.writerow({
                "rank": i,
                **r,
            })

    # Write Markdown
    with open(args.out_md, "w", encoding="utf-8") as f:
        f.write("| Rank | Run | MAP | P@20 | nDCG@20 |\n")
        f.write("|---:|:---|---:|---:|---:|\n")
        for i, r in enumerate(rows, start=1):
            f.write(f"| {i} | {r['run']} | {r['MAP']:.4f} | {r['P@20']:.4f} | {r['nDCG@20']:.4f} |\n")

    print(f"Wrote standings to {args.out_csv} and {args.out_md}")

if __name__ == "__main__":
    main()


