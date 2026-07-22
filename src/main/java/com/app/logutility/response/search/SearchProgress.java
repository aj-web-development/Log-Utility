package com.app.logutility.response.search;

/**
 * A lightweight "still working" signal for a streaming search, emitted once a node finishes
 * scanning (successfully, unreachable, or failed) — lets a client show e.g. "3/5 nodes scanned"
 * instead of sitting idle between chunk events while a slow node is still being read.
 */
public record SearchProgress(String nodeLabel, int nodesCompleted, int nodesTotal) {
}
