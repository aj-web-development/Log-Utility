package com.in10s.logutility.parser;

/**
 * Guesses the timestamp/level/logger token positions in a pasted sample log line, for the
 * wizard's confirm-or-correct step. Never throws — any input, including blank text, yields an
 * analysis with unmatched tokens left null.
 */
public interface SampleLineAnalyzer {

    SampleLineAnalysis analyze(String sampleLine);
}
