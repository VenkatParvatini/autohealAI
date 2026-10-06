package com.demo.gen;

/** One locator constant extracted from a Page Object source file, before it has a description. */
public class LocatorEntry {
    public String constantName;   // e.g. "USERNAME_FIELD"
    public String selectorValue;  // e.g. "#username"

    public LocatorEntry(String constantName, String selectorValue) {
        this.constantName = constantName;
        this.selectorValue = selectorValue;
    }
}
