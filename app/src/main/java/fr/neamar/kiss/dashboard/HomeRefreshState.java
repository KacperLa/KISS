package fr.neamar.kiss.dashboard;

/** Data changes request a refresh; gestures only invalidate older position restorations. */
final class HomeRefreshState {
    private boolean pendingRefresh;
    private long generation;

    void requestRefresh() { pendingRefresh = true; }
    boolean hasPendingRefresh() { return pendingRefresh; }
    void rendered() { pendingRefresh = false; generation++; }
    void navigationStarted() { generation++; }
    long generation() { return generation; }
    boolean canRestore(long savedGeneration) { return generation == savedGeneration; }
}
