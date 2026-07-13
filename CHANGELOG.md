# Changelog

## Unreleased

## 4.1.0 (2026-07-13)

- Vector search: `@VectorIndex(dimensions, metric, encoding, m, efConstruction, efSearch, buildThreads)` on `BarqList<Float>` properties builds a persistent HNSW index, with compile-time validation of the annotated property type.
- KNN queries via `RealmResults.knn(property, queryVector, k, ef, exact)` including observable results (`asFlow()` re-ranks on writes) and frozen-snapshot support.
- Eager validation of query dimensions, `k`, and `ef` on the calling thread with clear error messages.
- Vector index configuration is reconciled at open time; an `efSearch`-only change is adopted in place instead of throwing.
- Fixed a JNI local-reference leak on failed KNN calls.
- Bundled barq-core v20.2.0.
- Note: files containing a vector index must not be opened by barq-kotlin < 4.1.0 (core < 20.2.0); older versions do not understand the index format.
- Rebranded Kotlin client packages and docs toward Barq.
- Removed legacy hosted sync service helpers from the Kotlin client.
- Moved sync auth toward token-based Barq sync users.
