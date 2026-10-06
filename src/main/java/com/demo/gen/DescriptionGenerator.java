package com.demo.gen;

import com.demo.GeminiClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ONE-TIME, BUILD-TIME tool (not run during normal test execution).
 *
 * Reads a Page Object .java file, finds every locator constant in it, sends
 * ALL of them to the AI in a single batched call, and writes out a matching
 * Descriptions.java file with one generated description per locator.
 *
 * Run this once when a Page Object is first written, or again whenever
 * locators change — a human should skim the generated file before trusting it.
 */
public class DescriptionGenerator {

    // Matches lines like:  private static final String USERNAME_FIELD = "#username";
    private static final Pattern LOCATOR_PATTERN = Pattern.compile(
            "private\\s+static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"\\s*;"
    );

    private final GeminiClient aiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public DescriptionGenerator(String apiKey) {
        this.aiClient = new GeminiClient(apiKey);
    }

    /**
     * @param pageObjectSourcePath path to a .java Page Object file, e.g. "LoginPage.java"
     * @param outputClassName      name for the generated class, e.g. "LoginPageDescriptions"
     * @param outputPath           where to write the generated .java file
     */
    public void generate(Path pageObjectSourcePath, String outputClassName, Path outputPath) throws Exception {

        String sourceCode = Files.readString(pageObjectSourcePath);
        String pageClassName = pageObjectSourcePath.getFileName().toString().replace(".java", "");

        // --- Step 1: extract every locator constant from the source file ---
        List<LocatorEntry> locators = extractLocators(sourceCode);
        System.out.println("Found " + locators.size() + " locators in " + pageClassName);

        if (locators.isEmpty()) {
            System.out.println("No locators matched the expected pattern — nothing to generate.");
            return;
        }

        // --- Step 2: one batched AI call for ALL locators at once (not one call per locator) ---
        String prompt = buildBatchPrompt(pageClassName, locators);
        String rawReply = aiClient.ask(prompt);
        String cleanedJson = stripMarkdownFences(rawReply);

        List<GeneratedDescription> descriptions = mapper.readValue(
                cleanedJson, mapper.getTypeFactory().constructCollectionType(List.class, GeneratedDescription.class));

        System.out.println("AI generated " + descriptions.size() + " descriptions.");

        // --- Step 3: write out a ready-to-use constants class ---
        String generatedCode = buildJavaFile(outputClassName, locators, descriptions);
        Files.writeString(outputPath, generatedCode);
        System.out.println("Written to: " + outputPath);
    }

    private List<LocatorEntry> extractLocators(String sourceCode) {
        List<LocatorEntry> found = new ArrayList<>();
        Matcher matcher = LOCATOR_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            found.add(new LocatorEntry(matcher.group(1), matcher.group(2)));
        }
        return found;
    }

    /**
     * One prompt describing ALL locators from this page at once — this is the "batching" trick
     * that keeps this practical for a big framework: 1 API call per PAGE, not per LOCATOR.
     */
    private String buildBatchPrompt(String pageClassName, List<LocatorEntry> locators) {
        StringBuilder locatorList = new StringBuilder();
        for (LocatorEntry entry : locators) {
            locatorList.append("- constantName: \"").append(entry.constantName)
                    .append("\", selector: \"").append(entry.selectorValue).append("\"\n");
        }

        return """
                You are helping document a Selenium Page Object class called "%s".

                Below is a list of locator constants from this class. For EACH one, write a short,
                specific, human-readable description of what the element most likely is, based on
                its variable name and selector value. Assume standard naming conventions
                (e.g. a constant ending in "_BTN" is a button, "_FIELD" is an input field).

                Locators:
                %s

                Respond with a JSON ARRAY only, no markdown fences, one object per locator, in this exact shape:
                [
                  { "constantName": "USERNAME_FIELD", "description": "username input field on the login form" }
                ]
                """.formatted(pageClassName, locatorList);
    }

    /** Builds the final .java source text for the generated Descriptions class. */
    private String buildJavaFile(String outputClassName, List<LocatorEntry> locators,
                                   List<GeneratedDescription> descriptions) {

        StringBuilder sb = new StringBuilder();
        sb.append("package com.demo.pages;\n\n");
        sb.append("/**\n");
        sb.append(" * AUTO-GENERATED by DescriptionGenerator — review before trusting in production.\n");
        sb.append(" * Regenerate this file if the locators in the source Page Object change.\n");
        sb.append(" */\n");
        sb.append("public class ").append(outputClassName).append(" {\n\n");

        for (LocatorEntry locator : locators) {
            String description = findDescriptionFor(locator.constantName, descriptions);
            sb.append("    public static final String ").append(locator.constantName)
                    .append(" = \"").append(escape(description)).append("\";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String findDescriptionFor(String constantName, List<GeneratedDescription> descriptions) {
        for (GeneratedDescription d : descriptions) {
            if (d.constantName.equals(constantName)) {
                return d.description;
            }
        }
        return "NO DESCRIPTION GENERATED — review manually"; // fallback, should rarely happen
    }

    private String escape(String text) {
        return text.replace("\"", "\\\"");
    }

    private String stripMarkdownFences(String text) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : text.trim();
    }
}
