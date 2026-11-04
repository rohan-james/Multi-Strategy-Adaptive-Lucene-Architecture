#!/usr/bin/env python3
"""Per-topic analysis to identify weak queries."""
import argparse
import math
from collections import defaultdict
from pathlib import Path

def read_qrels(qrels_path):
    qrels = defaultdict(dict)
    with open(qrels_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 4:
                continue
            qid, _, docid, rel = parts[0], parts[1], parts[2], int(parts[3])
            qrels[qid][docid] = 1 if rel > 0 else 0
    return qrels

def read_run(run_path):
    run = defaultdict(list)
    with open(run_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 6:
                continue
            qid, docid, rank = parts[0], parts[2], int(parts[3])
            run[qid].append((rank, docid))
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
    dcg = 0.0
    for i, d in enumerate(retrieved[:k], start=1):
        rel = 1.0 if d in relevant_set else 0.0
        if i == 1:
            dcg += rel
        else:
            dcg += rel / math.log2(i)
    return dcg

def ndcg_at_k(retrieved, relevant_set, k):
    ideal = dcg_at_k(sorted(list(relevant_set)), relevant_set, k)
    if ideal == 0.0:
        return 0.0
    return dcg_at_k(retrieved, relevant_set, k) / ideal

def recall_at_k(retrieved, relevant_set, k):
    if not relevant_set:
        return 0.0
    topk = retrieved[:k]
    hits = sum(1 for d in topk if d in relevant_set)
    return hits / len(relevant_set)

def main():
    p = argparse.ArgumentParser(description="Per-topic analysis")
    p.add_argument("qrels", type=Path, help="Path to qrels file")
    p.add_argument("run", type=Path, help="Run file to evaluate")
    p.add_argument("--topics", type=Path, help="Topics file for context")
    args = p.parse_args()

    qrels = read_qrels(args.qrels)
    run = read_run(args.run)

    # Parse topics for context if provided
    topic_titles = {}
    if args.topics and args.topics.exists():
        content = args.topics.read_text()
        import re
        nums = re.findall(r'<num>\s*Number:\s*(\d+)', content)
        titles = re.findall(r'<title>\s*(.+?)(?=\n|<)', content)
        for num, title in zip(nums, titles):
            topic_titles[num] = title.strip()

    results = []
    for qid, rels in qrels.items():
        relevant_set = {d for d, r in rels.items() if r > 0}
        retrieved = run.get(qid, [])

        ap = average_precision(retrieved, relevant_set)
        p10 = precision_at_k(retrieved, relevant_set, 10)
        p20 = precision_at_k(retrieved, relevant_set, 20)
        ndcg = ndcg_at_k(retrieved, relevant_set, 20)
        r100 = recall_at_k(retrieved, relevant_set, 100)
        r1000 = recall_at_k(retrieved, relevant_set, 1000)

        # Find rank of first relevant doc
        first_rel_rank = None
        for i, d in enumerate(retrieved, start=1):
            if d in relevant_set:
                first_rel_rank = i
                break

        results.append({
            'qid': qid,
            'title': topic_titles.get(qid, ''),
            'ap': ap,
            'p10': p10,
            'p20': p20,
            'ndcg20': ndcg,
            'r100': r100,
            'r1000': r1000,
            'num_relevant': len(relevant_set),
            'first_rel': first_rel_rank if first_rel_rank else '>1000'
        })

    # Sort by AP (worst first)
    results.sort(key=lambda x: x['ap'])

    print("=" * 100)
    print("PER-TOPIC ANALYSIS (sorted by AP, worst first)")
    print("=" * 100)
    print(f"{'QID':<6} {'AP':>7} {'P@10':>6} {'P@20':>6} {'nDCG':>6} {'R@100':>6} {'R@1K':>6} {'#Rel':>5} {'1stRel':>7} Title")
    print("-" * 100)

    total_ap = 0
    for r in results:
        total_ap += r['ap']
        print(f"{r['qid']:<6} {r['ap']:>7.4f} {r['p10']:>6.3f} {r['p20']:>6.3f} {r['ndcg20']:>6.3f} {r['r100']:>6.3f} {r['r1000']:>6.3f} {r['num_relevant']:>5} {str(r['first_rel']):>7} {r['title'][:35]}")

    print("-" * 100)
    map_score = total_ap / len(results) if results else 0
    print(f"MAP: {map_score:.4f}")

    print("\n" + "=" * 100)
    print("WORST 10 TOPICS (biggest opportunities for improvement)")
    print("=" * 100)
    for r in results[:10]:
        print(f"\nTopic {r['qid']}: {r['title']}")
        print(f"  AP={r['ap']:.4f}, P@10={r['p10']:.3f}, Recall@1000={r['r1000']:.3f}, #Relevant={r['num_relevant']}, FirstRel={r['first_rel']}")
        if r['r1000'] < 0.5:
            print(f"  WARNING: Low recall - many relevant docs not in top 1000!")
        if r['first_rel'] == '>1000' or (isinstance(r['first_rel'], int) and r['first_rel'] > 100):
            print(f"  WARNING: First relevant doc ranked very low!")

if __name__ == "__main__":
    main()
