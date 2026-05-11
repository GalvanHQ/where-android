## 2024-05-11 - [LazyColumn groupBy bottleneck]
**Learning:** O(N) operations inside a LazyColumn's block run during the layout/scroll phase causing expensive computations and dropped frames.
**Action:** Extract list transformations (like groupBy) out of `LazyColumn` and wrap them with `remember` to ensure they only run when the list data actually changes.
