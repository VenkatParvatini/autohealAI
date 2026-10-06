package com.demo.bonus;

import com.demo.GeminiClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a plain-English bonus description into a flat Map&lt;String, String&gt; matching the
 * spBonus1Data convention used across the bonus-creation automation suite.
 *
 * Built the same way SimpleAutoHeal/DescriptionGenerator use GeminiClient: one HTTP call per
 * request, a strict-JSON prompt, Jackson to parse the reply, and an in-memory cache so repeated
 * identical requests (e.g. re-running the same scenario, or a parameterized test) skip the AI
 * call entirely — same idea as SimpleAutoHeal's healedSelectorCache.
 *
 * Usage:
 *   BonusDataGenerator generator = new BonusDataGenerator(Main.getApiKey());
 *   Map<String, String> spBonus1Data = generator.generate(
 *       "create a Special Cashout restricted bonus with validity days as 3 and campaign end date as 23 sep",
 *       Map.of("partnerName", fwc.partnerid(), "FreeSpinProfile", fwc.getFreeSpinProfile())
 *   );
 */
public class BonusDataGenerator {

    private static final String FIXTURE_PLACEHOLDER = "{{FIXTURE}}";

    private final GeminiClient aiClient;
    private final ObjectMapper mapper = new ObjectMapper();

    // Same caching pattern as SimpleAutoHeal.healedSelectorCache: prompt+fixtures -> generated map.
    // In-memory only, for MVP simplicity — persist this if you want it to survive JVM restarts.
    private final Map<String, Map<String, String>> generationCache = new HashMap<>();

    public BonusDataGenerator(String geminiApiKey) {
        this.aiClient = new GeminiClient(geminiApiKey);
    }

    /** Generate bonus data from a prompt alone. No fixture values are substituted. */
    public Map<String, String> generate(String userRequest) throws Exception {
        return generate(userRequest, Map.of());
    }

    /**
     * Generate bonus data from a prompt, substituting any environment-specific values
     * (partner id, profile names, etc.) that the model cannot safely invent. Keys in
     * {@code fixtureValues} always win over whatever the model returned or defaulted to.
     */
    public Map<String, String> generate(String userRequest, Map<String, String> fixtureValues) throws Exception {

        String cacheKey = userRequest.trim().toLowerCase() + "::" + fixtureValues;
        Map<String, String> cached = generationCache.get(cacheKey);
        if (cached != null) {
            System.out.println("[BonusDataGenerator] Returning CACHED data for: " + userRequest);
            return new LinkedHashMap<>(cached);
        }

        Map<String, String> data = generateWithRetry(userRequest, 1);
        applyFixtureOverrides(data, fixtureValues);
        assertNoUnresolvedPlaceholders(data, userRequest, fixtureValues);
        assertAlwaysRequiredKeysPresent(data, userRequest);

        generationCache.put(cacheKey, new LinkedHashMap<>(data));
        return data;
    }

    // ------------------------------------------------------------------------------------

    private Map<String, String> generateWithRetry(String userRequest, int remainingRetries) throws Exception {
        String prompt = buildPrompt(userRequest);
        String rawReply = aiClient.ask(prompt);
        String cleanedJson = stripMarkdownFences(rawReply);

        try {
            return mapper.readValue(cleanedJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception badJson) {
            if (remainingRetries <= 0) {
                throw new RuntimeException(
                        "AI did not return valid JSON after retry for request: \"" + userRequest
                                + "\"\nLast raw reply:\n" + rawReply, badJson);
            }
            System.out.println("[BonusDataGenerator] Reply wasn't valid JSON, retrying once...");
            String correctiveRequest = userRequest
                    + "\n\n(Your previous reply was not a single valid JSON object. "
                    + "Reply with ONLY the JSON object this time, no other text, no markdown fences.)";
            return generateWithRetry(correctiveRequest, remainingRetries - 1);
        }
    }

    private void applyFixtureOverrides(Map<String, String> data, Map<String, String> fixtureValues) {
        data.putAll(fixtureValues);
    }

    private void assertNoUnresolvedPlaceholders(
            Map<String, String> data, String userRequest, Map<String, String> fixtureValues) {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (FIXTURE_PLACEHOLDER.equals(entry.getValue())) {
                throw new IllegalStateException(
                        "Generated data for key '" + entry.getKey() + "' needs a fixture value "
                                + "but none was supplied. Request: \"" + userRequest + "\", "
                                + "fixtureValues supplied: " + fixtureValues.keySet());
            }
        }
    }

    private void assertAlwaysRequiredKeysPresent(Map<String, String> data, String userRequest) {
        for (String key : new String[]{"BonusName", "bonusCategory", "bonusType"}) {
            if (!data.containsKey(key) || data.get(key).isBlank()) {
                throw new IllegalStateException(
                        "Generated bonus data is missing required key '" + key
                                + "' for request: \"" + userRequest + "\"");
            }
        }
    }

    /** Same defensive fence-stripping as SimpleAutoHeal/DescriptionGenerator use. */
    private String stripMarkdownFences(String text) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : text.trim();
    }

    /**
     * The full grounding spec, embedded the same way SimpleAutoHeal/DescriptionGenerator embed
     * their prompts as text blocks rather than loading an external resource file.
     */
    private String buildPrompt(String userRequest) {
        return ("""
                You generate test data for a bonus-creation automation suite. You will be given a
                plain-English request describing a bonus to create (positive or negative test
                scenario). You must output ONE JSON object and nothing else — no markdown fences,
                no commentary, no explanation before or after.

                Every value in the JSON object must be a STRING (numbers and booleans as their
                string form, e.g. "10", "true"), because the values are consumed by
                Map<String, String> and fed into spBonus1Data.put(key, value) calls in a Java
                Selenium suite.

                ======================================================================
                RESOLUTION ORDER
                ======================================================================
                1. Determine bonusType and bonusCategory FIRST — every other field's legality
                   depends on this combination (see MATRIX below). Extract them from the request
                   if stated; otherwise default bonusCategory=CASHOUT_RESTRICT, bonusType=SPECIAL.
                2. Determine if this is a POSITIVE (happy-path) or NEGATIVE (should-fail)
                   scenario. Negative language: "invalid", "missing", "without", "should fail",
                   "test the validation for X", "negative scenario for X". Default: positive.
                3. Fill every field the MATRIX allows for this bonusType/bonusCategory using
                   DEFAULTS below for anything not stated in the request.
                4. If negative: keep every field at its valid default EXCEPT the field(s) the
                   request names — set only those to a value that violates the matching rule in
                   NEGATIVE TRIGGERS below. Do not break unrelated fields. Add a field
                   "_expectedValidation" describing, in one short phrase, which check should fail
                   and why (this key is for the tester, never submitted to the app UI).
                5. Never invent a value for "partnerName", "FreeSpinProfile", or "FreeBetProfile"
                   — if the request doesn't give them, output the literal string "{{FIXTURE}}" for
                   that key. The calling Java code substitutes real fixture values afterward.
                6. If a field is not applicable to the resolved bonusType/bonusCategory (per
                   MATRIX), do not include that key in the output at all.

                ======================================================================
                MATRIX (bonusType x bonusCategory — resolve before anything else)
                ======================================================================
                - DEPOSIT (either category): depositRestricted defaults "true"; showOnCashier
                  applies; award table needs min/max deposit amount fields; recurring-offer
                  fields allowed.
                - SPECIAL (either category): no depositRestricted; recurring-offer allowed.
                - SIGNUP (either category): targetExpiration only if targetType=XTREMEPUSH;
                  recurring-offer allowed.
                - LOGIN (either category): includeactiveplayers fields allowed; recurring-offer
                  NOT allowed.
                - FREEBET: only legal when bonusCategory=CASHOUT_RESTRICT. If the request asks for
                  FREEBET + RELEASE_RESTRICT, this is a UI-level impossibility (the option doesn't
                  exist in the dropdown) — treat this as its own negative case: output
                  bonusType/bonusCategory as requested and set "_expectedValidation":"FREEBET
                  option is not selectable when bonusCategory is RELEASE_RESTRICT (UI exclusion,
                  not a server validator)".
                - RELEASE_RESTRICT (any bonusType): isFreeSpin must be "false" (checkbox
                  disabled). Wager/Balance issue-criteria section (bbt) is hidden whenever
                  depositRestricted=true, or bonusType is one of SPECIAL/LOGIN/SIGNUP, or
                  bonusType=DEPOSIT — in those cases omit wagerBased/balanceBased and all
                  issue-criteria keys entirely.
                - isFreeBet="true" forces products="SPORTS_BOOK" and disables isFreeSpin.
                - isFreeSpin="true" forces products="CASINO" and disables isFreeBet.

                ======================================================================
                DEFAULTS (used only when the request doesn't state the field, and MATRIX allows)
                ======================================================================
                BonusName          -> "<BonusType><BonusCategoryShort>Bonus<random7alpha>"
                partnerName        -> "{{FIXTURE}}"
                refCurrency        -> "{{FIXTURE}}"
                bonusCategory      -> "CASHOUT_RESTRICT"
                bonusType          -> "SPECIAL"
                awardType          -> "ABS"
                products           -> "CASINO" (unless isFreeBet=true, then "SPORTS_BOOK")
                targetType         -> "ALL"
                startDate          -> "0"   (offset in days from today)
                EndDate            -> "30"  (offset in days from today, unless an explicit date given)
                maxoffervalue      -> "10"
                maxBonus           -> "1"
                maxCap             -> "-1"
                maxBonusAmt        -> "-1"
                validityOnDate     -> "false"
                validityDays       -> "30"
                isFreeSpin         -> "false"
                isFreeBet          -> "false"
                freespinCount      -> "1"   (only if isFreeSpin=true)
                freespinValue      -> "10"  (only if isFreeSpin=true)
                wagerBased         -> "true"  (only if MATRIX allows issue-criteria section)
                issueMeasure1      -> "ABS"
                maxIssueMeasure1   -> "ABS"
                balanceBased       -> "false"
                needBonus          -> "true"
                bonusCode          -> "<BonusName>_CODE"
                needincludeactiveplayers -> "false"
                recurringOfferCheckbox   -> "false"
                multiBonus         -> "false"
                vendorBonus        -> "false"
                linkedBonus        -> "false"
                sendEmailSms       -> "false"
                sendEmailSms1      -> "false"
                forfeitBonusCheckBox -> "false"

                ======================================================================
                NEGATIVE TRIGGERS (field to set, and how to break it, per validator)
                ======================================================================
                - startDate in the past -> greaterThanCurrentDate
                - EndDate <= startDate -> greaterThan
                - reuse an existing bonusCode with needBonus=true -> validateBonusCodeGiven
                - no products value -> validateproducts
                - wagerBased=false AND balanceBased=false -> atLeastOneCriteriaChecked
                - wagerBased=true but issueMeasure1/maxIssueMeasure1 unset -> checkboxCheck
                - bonusbalType=PERCENTAGE with maxIssueMultiValue2 >100, <0, blank, or non-numeric
                  -> percentageCheck
                - issueValueBbt <= maxIssueValBbt for same currency -> targetBalanceCheck
                - validityDays > 180 while a freespin vendor in {quickspin,pushgaming,felt,relax,
                  stakelogic} selected -> vaidateExpiryDays
                - validityDays > 1825 -> validateExpiryNumber
                - vendorsPct<vendor> outside 0-100 or non-numeric -> validateVendorBonusPct
                - any award-table numeric cell blank/non-numeric -> validateCurrencyMappingTableValues
                - new deposit slab min <= previous slab max -> validateForMinDuplicateSlabValues /
                  validateForMaxDuplicateSlabValues
                - includeactiveplayers >1440 or <=0 -> maxminutscheck / greaterthanzeroandnonnegitive
                - recurringOfferCheckbox=true with no Daily/Weekly/Monthly checked ->
                  validateFrequencySelection
                - per-period user-level max = -1 but all-users-level != -1, or user-level >
                  all-users-level -> validateDailyOffer/validateWeeklyOffer/validateMonthlyOffer/
                  validateOfferComparison
                - profile mapping table empty while products=SPORTS_BOOK and isFreeBet=false ->
                  profileMappingTablevalidate
                - bonusName length >50, or bonusInternalName >250 -> checkLengthOfString

                ======================================================================
                OUTPUT
                ======================================================================
                Return only the JSON object. Example shape (keys present depend on resolved
                bonusType/category per MATRIX — omit keys that don't apply):

                {"BonusName":"SplCROfferBonusA1B2C3D","partnerName":"{{FIXTURE}}","bonusCategory":"CASHOUT_RESTRICT","bonusType":"SPECIAL","maxoffervalue":"10","startDate":"0","EndDate":"23sep","validityDays":"3","targetType":"ALL","awardType":"ABS","products":"CASINO"}

                ======================================================================
                USER REQUEST
                ======================================================================
                %s
                """).formatted(userRequest);
    }
}
