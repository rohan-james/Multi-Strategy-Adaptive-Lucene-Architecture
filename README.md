# A Multi-Strategy Adaptive Lucene Architecture with Random Indexing for Enhanced TREC Retrieval

## 🎯 **Overview**

This repository provides a framework on optimizing search and ranking on the TREC Disks 4 and 5

## **Quick Start Guide**
clone this repository then follow the below steps:

1) Add topics file and Assignment Two folder containing the documents to the project root

2) Run mvn clean install from assignment-two-19

3) Run 
```
java -jar target/cs7is3-search-1.0.0.jar index --docs "Assignment Two" --index index
```
to run the indexer (which parses the documents and saves the indexes)

4) Run
```
 java -jar target/cs7is3-search-1.0.0.jar search --index index --topics topics --output runs/student.run --numDocs 1000
 ```
to run the searcher (which parses the topic)

5) (Optional) view the runs by
```
ls runs
head runs/student.run
wc -l runs/student.run
```
6) Download trec eval software
```
wget https://trec.nist.gov/trec_eval/trec_eval-9.0.7.tar.gz
```
Extract the files
```
tar -xzf trec_eval-9.0.7.tar.gz
```
Compile
```
cd trec_eval-9.0.7
make
```
Return to the project root and run trec_eval on results file 
```
./trec_eval-9.0.7/trec_eval -m map -m P.20 -m ndcg_cut.20 qrels.assignment2.part1 runs/student.run
```

