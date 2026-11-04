#!/usr/bin/env python3
import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path

# Comprehensive evaluator for TREC-style runs with all metrics for final grading

def read_qrels(qrels_path):
    qrels = defaultdict(dict)  # topic -> docid -> rel (0/1)
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
    run = defaultdict(list)  # topic -> list of docids in rank order
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

def recall_at_k(retrieved, relevant_set, k):
    if not relevant_set:
        return 0.0
    topk = retrieved[:k]
    hits = sum(1 for d in topk if d in relevant_set)
    return hits / len(relevant_set)

def total_recall(retrieved, relevant_set):
    if not relevant_set:
        return 0.0
    hits = sum(1 for d in retrieved if d in relevant_set)
    return hits / len(relevant_set)

def reciprocal_rank(retrieved, relevant_set):
    for i, d in enumerate(retrieved, start=1):
        if d in relevant_set:
            return 1.0 / i
    return 0.0

def r_precision(retrieved, relevant_set):
    if not relevant_set:
        return 0.0
    r = len(relevant_set)
    if r == 0:
        return 0.0
    top_r = retrieved[:r]
    hits = sum(1 for d in top_r if d in relevant_set)
    return hits / r

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

def evaluate(qrels, run):
    ap_scores = []
    p5_scores = []
    p10_scores = []
    p20_scores = []
    p100_scores = []
    recall20_scores = []
    recall100_scores = []
    total_recall_scores = []
    mrr_scores = []
    rprec_scores = []
    ndcg5_scores = []
    ndcg10_scores = []
    ndcg20_scores = []
    ndcg100_scores = []
    
    topics_with_results = 0
    topics_with_relevant = 0
    total_relevant_docs = 0
    total_retrieved_docs = 0
    topics_evaluated = 0
    
    for qid, rels in qrels.items():
        relevant_set = {d for d, r in rels.items() if r > 0}
        retrieved = run.get(qid, [])
        
        if not relevant_set:
            continue
            
        topics_evaluated += 1
        total_relevant_docs += len(relevant_set)
        total_retrieved_docs += len(retrieved)
        
        if retrieved:
            topics_with_results += 1
        if retrieved and any(d in relevant_set for d in retrieved):
            topics_with_relevant += 1
        
        ap_scores.append(average_precision(retrieved, relevant_set))
        p5_scores.append(precision_at_k(retrieved, relevant_set, 5))
        p10_scores.append(precision_at_k(retrieved, relevant_set, 10))
        p20_scores.append(precision_at_k(retrieved, relevant_set, 20))
        p100_scores.append(precision_at_k(retrieved, relevant_set, 100))
        recall20_scores.append(recall_at_k(retrieved, relevant_set, 20))
        recall100_scores.append(recall_at_k(retrieved, relevant_set, 100))
        total_recall_scores.append(total_recall(retrieved, relevant_set))
        mrr_scores.append(reciprocal_rank(retrieved, relevant_set))
        rprec_scores.append(r_precision(retrieved, relevant_set))
        ndcg5_scores.append(ndcg_at_k(retrieved, relevant_set, 5))
        ndcg10_scores.append(ndcg_at_k(retrieved, relevant_set, 10))
        ndcg20_scores.append(ndcg_at_k(retrieved, relevant_set, 20))
        ndcg100_scores.append(ndcg_at_k(retrieved, relevant_set, 100))
    
    avg_docs_per_topic = total_retrieved_docs / topics_evaluated if topics_evaluated > 0 else 0.0
    
    metrics = {
        "MAP": sum(ap_scores) / len(ap_scores) if ap_scores else 0.0,
        "P@5": sum(p5_scores) / len(p5_scores) if p5_scores else 0.0,
        "P@10": sum(p10_scores) / len(p10_scores) if p10_scores else 0.0,
        "P@20": sum(p20_scores) / len(p20_scores) if p20_scores else 0.0,
        "P@100": sum(p100_scores) / len(p100_scores) if p100_scores else 0.0,
        "Recall@20": sum(recall20_scores) / len(recall20_scores) if recall20_scores else 0.0,
        "Recall@100": sum(recall100_scores) / len(recall100_scores) if recall100_scores else 0.0,
        "Total_Recall": sum(total_recall_scores) / len(total_recall_scores) if total_recall_scores else 0.0,
        "MRR": sum(mrr_scores) / len(mrr_scores) if mrr_scores else 0.0,
        "R_Precision": sum(rprec_scores) / len(rprec_scores) if rprec_scores else 0.0,
        "nDCG@5": sum(ndcg5_scores) / len(ndcg5_scores) if ndcg5_scores else 0.0,
        "nDCG@10": sum(ndcg10_scores) / len(ndcg10_scores) if ndcg10_scores else 0.0,
        "nDCG@20": sum(ndcg20_scores) / len(ndcg20_scores) if ndcg20_scores else 0.0,
        "nDCG@100": sum(ndcg100_scores) / len(ndcg100_scores) if ndcg100_scores else 0.0,
        "Topics_Evaluated": topics_evaluated,
        "Topics_With_Results": topics_with_results,
        "Topics_With_Relevant": topics_with_relevant,
        "Total_Relevant_Docs": total_relevant_docs,
        "Total_Retrieved_Docs": total_retrieved_docs,
        "Avg_Docs_Per_Topic": avg_docs_per_topic,
    }
    return metrics

def main():
    p = argparse.ArgumentParser(description="Comprehensive evaluation for final grades")
    p.add_argument("qrels", type=Path, help="Path to qrels file")
    p.add_argument("runs", nargs='+', type=Path, help="Run files to evaluate")
    p.add_argument("--out_csv", type=Path, default=Path("final_grades.csv"), help="Output CSV path")
    p.add_argument("--out_md", type=Path, default=Path("final_grades.md"), help="Output Markdown path")
    args = p.parse_args()

    qrels = read_qrels(args.qrels)
    rows = []
    for run_path in args.runs:
        run = read_run(run_path)
        metrics = evaluate(qrels, run)
        rows.append({
            "run": run_path.name,
            **metrics
        })

    rows.sort(key=lambda r: (r["MAP"], r["nDCG@20"], r["P@20"]), reverse=True)

    fieldnames = ["rank", "run", "MAP", "P@5", "P@10", "P@20", "P@100", 
                  "Recall@20", "Recall@100", "Total_Recall", "MRR", "R_Precision",
                  "nDCG@5", "nDCG@10", "nDCG@20", "nDCG@100",
                  "Topics_Evaluated", "Topics_With_Results", "Topics_With_Relevant",
                  "Total_Relevant_Docs", "Total_Retrieved_Docs", "Avg_Docs_Per_Topic"]

    args.out_csv.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out_csv, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames)
        w.writeheader()
        for i, r in enumerate(rows, start=1):
            w.writerow({
                "rank": i,
                **r,
            })

    with open(args.out_md, "w", encoding="utf-8") as f:
        f.write("| Rank | Run | MAP | P@5 | P@10 | P@20 | P@100 | Recall@20 | Recall@100 | MRR | nDCG@20 |\n")
        f.write("|---:|:---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n")
        for i, r in enumerate(rows, start=1):
            f.write(f"| {i} | {r['run']} | {r['MAP']:.4f} | {r['P@5']:.4f} | {r['P@10']:.4f} | {r['P@20']:.4f} | {r['P@100']:.4f} | {r['Recall@20']:.4f} | {r['Recall@100']:.4f} | {r['MRR']:.4f} | {r['nDCG@20']:.4f} |\n")
        f.write("\n### Coverage Statistics\n\n")
        if rows:
            f.write(f"- **Topics Evaluated**: {rows[0]['Topics_Evaluated']}\n")
            f.write(f"- **Topics With Results**: {rows[0]['Topics_With_Results']}\n")
            f.write(f"- **Topics With Relevant Docs**: {rows[0]['Topics_With_Relevant']}\n")
            f.write(f"- **Total Relevant Docs**: {rows[0]['Total_Relevant_Docs']}\n")
            f.write(f"- **Total Retrieved Docs**: {rows[0]['Total_Retrieved_Docs']}\n")
            f.write(f"- **Avg Docs Per Topic**: {rows[0]['Avg_Docs_Per_Topic']:.1f}\n")

    print(f"Wrote final grades to {args.out_csv} and {args.out_md}")

def output_github_actions_metrics(csv_path, github_output_path=None):
    """Read metrics from CSV and output in GitHub Actions format."""
    import os
    
    if github_output_path is None:
        github_output_path = os.environ.get('GITHUB_OUTPUT', '/dev/stdout')
    
    if not Path(csv_path).exists():
        return
    
    with open(csv_path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        row = next(reader, None)
        if row:
            metrics = {
                'map': row.get('MAP', '0'),
                'p5': row.get('P@5', '0'),
                'p10': row.get('P@10', '0'),
                'p20': row.get('P@20', '0'),
                'p100': row.get('P@100', '0'),
                'recall20': row.get('Recall@20', '0'),
                'recall100': row.get('Recall@100', '0'),
                'total_recall': row.get('Total_Recall', '0'),
                'mrr': row.get('MRR', '0'),
                'r_precision': row.get('R_Precision', '0'),
                'ndcg5': row.get('nDCG@5', '0'),
                'ndcg10': row.get('nDCG@10', '0'),
                'ndcg20': row.get('nDCG@20', '0'),
                'ndcg100': row.get('nDCG@100', '0'),
                'topics_evaluated': row.get('Topics_Evaluated', '0'),
                'topics_with_results': row.get('Topics_With_Results', '0'),
                'topics_with_relevant': row.get('Topics_With_Relevant', '0'),
                'total_relevant_docs': row.get('Total_Relevant_Docs', '0'),
                'total_retrieved_docs': row.get('Total_Retrieved_Docs', '0'),
                'avg_docs_per_topic': row.get('Avg_Docs_Per_Topic', '0')
            }
            
            with open(github_output_path, 'a', encoding='utf-8') as out:
                for key, value in metrics.items():
                    out.write(f"{key}={value}\n")

if __name__ == "__main__":
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "--github-output":
        if len(sys.argv) < 3:
            print("Usage: evaluate_final.py --github-output <csv_path> [github_output_path]", file=sys.stderr)
            sys.exit(1)
        csv_path = sys.argv[2]
        github_output_path = sys.argv[3] if len(sys.argv) > 3 else None
        output_github_actions_metrics(csv_path, github_output_path)
    else:
        main()

