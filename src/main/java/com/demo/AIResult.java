package com.demo;

/** Holds the parsed JSON reply from the AI: the suggested selector + why it chose it. */
public class AIResult {
    public String selector;
    public double confidence;
    public String reasoning;

    @Override
    public String toString() {
        return "AIResult{selector='" + selector + "', confidence=" + confidence
                + ", reasoning='" + reasoning + "'}";
    }
}
