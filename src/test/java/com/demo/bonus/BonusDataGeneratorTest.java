package com.demo.bonus;

import com.demo.Main;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for BonusDataGenerator, in the same style as SimpleAutoHealTest: a real call
 * to the Gemini API, no mocking, for MVP simplicity. Requires GEMINI_API_KEY to be set.
 */
class BonusDataGeneratorTest {

    private BonusDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new BonusDataGenerator(Main.getApiKey());
    }

    @Test
    void positiveScenarioProducesAllRequiredFieldsAndNoUnresolvedFixtures() throws Exception {
        Map<String, String> data = generator.generate(
                "create a Special Cashout restricted bonus with validity days as 3 and campaign "
                        + "end date as 23 sep",
                Map.of("partnerName", "PARTNER_123")
        );

        assertNotNull(data);
        assertEquals("SPECIAL", data.get("bonusType"));
        assertEquals("CASHOUT_RESTRICT", data.get("bonusCategory"));
        assertEquals("3", data.get("validityDays"));
        assertEquals("PARTNER_123", data.get("partnerName"));
        assertFalse(data.containsValue("{{FIXTURE}}"),
                "No fixture placeholder should survive into the final map");
    }

    @Test
    void negativeScenarioIncludesExpectedValidationHint() throws Exception {
        Map<String, String> data = generator.generate(
                "create a Deposit bonus, cashout restricted, but set the campaign end date "
                        + "before the start date to test the date validation",
                Map.of("partnerName", "PARTNER_123")
        );

        assertNotNull(data.get("_expectedValidation"),
                "Negative scenarios should explain which validator is expected to fail");
    }

    @Test
    void missingFixtureValueFailsFastInsteadOfSubmittingBadData() {
        // No partnerName supplied, and the request needs one (every bonus needs a partner) —
        // should throw rather than silently submit "{{FIXTURE}}" as a real value.
        assertThrows(IllegalStateException.class, () ->
                generator.generate("create a Signup bonus, cashout restricted")
        );
    }

    @Test
    void secondCallForSameRequestUsesCacheNotAI() throws Exception {
        String request = "create a Special Cashout restricted bonus with validity days as 3";
        Map<String, String> fixtures = Map.of("partnerName", "PARTNER_123");

        Map<String, String> first = generator.generate(request, fixtures);
        assertNotNull(first);

        // Same request+fixtures again: should resolve instantly from the in-memory cache inside
        // BonusDataGenerator, without another AI round-trip (mirrors SimpleAutoHeal's caching test).
        Map<String, String> second = generator.generate(request, fixtures);
        assertEquals(first, second);
    }
}
