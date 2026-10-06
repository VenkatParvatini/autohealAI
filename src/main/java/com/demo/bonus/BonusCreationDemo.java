package com.demo.bonus;

import com.demo.Main;

import java.util.Map;

/**
 * Runnable demo for BonusDataGenerator, in the same spirit as GenerateDescriptions.java:
 * a plain main() you can run directly via
 *   mvn exec:java -Dexec.mainClass="com.demo.bonus.BonusCreationDemo"
 * once GEMINI_API_KEY is set (reuses Main.getApiKey(), same as everything else in this project).
 */
public class BonusCreationDemo {

    public static void main(String[] args) throws Exception {
        String apiKey = Main.getApiKey(); // reuses the same env-variable reader as SimpleAutoHeal

        BonusDataGenerator generator = new BonusDataGenerator(apiKey);

        // --- Positive scenario ---------------------------------------------------------------
        Map<String, String> spBonus1Data = generator.generate(
                "create a Special Cashout restricted bonus with validity days as 3 and campaign "
                        + "end date as 23 sep",
                Map.of(
                        "partnerName", fakePartnerId(),
                        "FreeSpinProfile", fakeFreeSpinProfile()
                )
        );
        System.out.println("\n=== Positive scenario ===");
        spBonus1Data.forEach((k, v) -> System.out.println("  " + k + " = " + v));

        // --- Negative scenario: campaign end date before start date --------------------------
        Map<String, String> negativeData = generator.generate(
                "create a Deposit bonus, cashout restricted, but set the campaign end date "
                        + "before the start date to test the date validation",
                Map.of("partnerName", fakePartnerId())
        );
        System.out.println("\n=== Negative scenario ===");
        negativeData.forEach((k, v) -> System.out.println("  " + k + " = " + v));
        System.out.println("Expected validation failure: "
                + negativeData.getOrDefault("_expectedValidation", "(not provided)"));

        // From here, feed spBonus1Data / negativeData into your existing Selenium page-object /
        // step-definition code exactly like a hand-written map, e.g.:
        //   createBonusPage.fillBonusBasicInfo(spBonus1Data);
        //   createBonusPage.fillOfferCriteria(spBonus1Data);
        //   createBonusPage.submit();
    }

    private static String fakePartnerId() {
        // Replace with your real fixture call, e.g. fwc.partnerid()
        return "PARTNER_123";
    }

    private static String fakeFreeSpinProfile() {
        // Replace with your real fixture call, e.g. fwc.getFreeSpinProfile()
        return "pragmatic_7monkeys";
    }
}
