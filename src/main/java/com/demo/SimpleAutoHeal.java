package com.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stripped-down, single-file version of the "AutoHeal Locator" concept:
 *
 *   1. Try the original locator.
 *   2. If it fails, grab the page HTML and ask an AI for a replacement CSS selector.
 *   3. Try the AI's suggestion. If it works, return it (and cache it for next time).
 *
 * This is deliberately minimal — no multi-provider support, no visual analysis,
 * no persistent cache — just enough to see the real mechanism working end to end.
 */
public class SimpleAutoHeal {

    private final WebDriver driver;
    private final GeminiClient aiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // In-memory cache: original locator string -> healed CSS selector.
    // Real tools (Healenium, AutoHeal Locator) persist this to a DB/file so it survives restarts.
    private final Map<String, String> healedSelectorCache = new HashMap<>();

    public SimpleAutoHeal(WebDriver driver, String geminiApiKey) {
        this.driver = driver;
        this.aiClient = new GeminiClient(geminiApiKey);
    }

    /**
     * @param originalLocator the locator you'd normally use, e.g. By.id("loginBtn")
     * @param description     plain-English description of what the element IS, for the AI's benefit
     *                         e.g. "the main login submit button"
     */
    public WebElement findWithHealing(By originalLocator, String description) {

        // --- Step 1: try the original locator, exactly like a normal Selenium call ---
        try {
            WebElement element = driver.findElement(originalLocator);
            System.out.println("[AutoHeal] Original locator worked: " + originalLocator);
            return element;
        } catch (NoSuchElementException e) {
            System.out.println("[AutoHeal] Locator broke: " + originalLocator + " -> starting healing...");
        }

        String cacheKey = originalLocator.toString();

        // --- Step 2: check the cache before spending an AI call ---
        String cachedSelector = healedSelectorCache.get(cacheKey);
        if (cachedSelector != null) {
            try {
                WebElement element = driver.findElement(By.cssSelector(cachedSelector));
                System.out.println("[AutoHeal] Healed via CACHE: " + cachedSelector);
                return element;
            } catch (NoSuchElementException e) {
                System.out.println("[AutoHeal] Cached selector no longer works either, asking AI again...");
            }
        }

        // --- Step 3: ask the AI ---
        try {
            String html = driver.getPageSource();
            String prompt = buildPrompt(html, description, originalLocator.toString());

            String rawReply = aiClient.ask(prompt);
            String cleanedJson = stripMarkdownFences(rawReply);

            AIResult result = mapper.readValue(cleanedJson, AIResult.class);
            System.out.println("[AutoHeal] AI suggested: " + result);

            // --- Step 4: validate the AI's suggestion actually works ---
            WebElement healedElement = driver.findElement(By.cssSelector(result.selector));

            // Success — cache it so next failure skips the AI call entirely
            healedSelectorCache.put(cacheKey, result.selector);
            System.out.println("[AutoHeal] Healing SUCCESSFUL. New selector cached.");

            return healedElement;

        } catch (Exception e) {
            throw new NoSuchElementException(
                    "AutoHeal failed to find element for locator '" + originalLocator
                            + "' (description: '" + description + "'). AI healing error: " + e.getMessage());
        }
    }

    /**
     * This prompt structure is directly modeled on the AutoHeal Locator repo's
     * buildDOMAnalysisPrompt() method — the key trick is forcing a strict JSON-only reply
     * so the response can be parsed programmatically instead of being free-form text.
     */
    private String buildPrompt(String html, String description, String brokenSelector) {
        // Trim the HTML if it's huge — keeps token usage sane for an MVP.
        String trimmedHtml = html.length() > 15000 ? html.substring(0, 15000) : html;

        return """
                You are a web automation expert. Find the best CSS selector for: "%s"

                The selector "%s" is broken. Analyze the HTML below and find the correct element.

                HTML:
                %s

                REQUIREMENTS:
                - Look for elements with matching id, name, class, or text content
                - Prefer ID selectors (#id) when available
                - Ensure the selector matches exactly one element
                - The selector must be valid CSS syntax
                - Do NOT include any prefixes like "selector:" or "css:"

                Respond with valid JSON only, no markdown formatting, no code fences:
                {
                    "selector": "css-selector-here",
                    "confidence": 0.95,
                    "reasoning": "brief explanation"
                }
                """.formatted(description, brokenSelector, trimmedHtml);
    }

    /** Some models wrap JSON in ```json ... ``` even when told not to — strip that defensively. */
    private String stripMarkdownFences(String text) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : text.trim();
    }
}
