## 2024-06-11 - [LazyColumn Recomposition Bottleneck]
**Learning:** O(N) operations inside a Jetpack Compose `LazyColumn` builder block (like `.groupBy`) run on *every* recomposition, even if the underlying list hasn't changed. This is a common performance anti-pattern.
**Action:** Always wrap heavy list transformations (filtering, grouping, mapping) in `remember(key)` blocks before passing them into the LazyColumn builder.
