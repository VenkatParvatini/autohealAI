package com.demo;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for SimpleAutoHeal: proves that a deliberately broken locator
 * still resolves to the correct element via the AI-healing path.
 *
 * Requires the GEMINI_API_KEY environment variable to be set before running,
 * since this test makes a real call to the Gemini API (no mocking, for MVP simplicity).
 */
class SimpleAutoHealTest {

    private WebDriver driver;
    private SimpleAutoHeal autoHeal;

    @BeforeEach
    void setUp() {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless=new"); // run without opening a visible browser window
        driver = new ChromeDriver(options);

        String apiKey = Main.getApiKey();
        autoHeal = new SimpleAutoHeal(driver, apiKey);

        driver.get("https://qa-controlcenter.uatsecure.com/login.html");
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void healsBrokenUsernameLocatorAndFindsRealField() {
        // This id is intentionally wrong — the real field's id is "username".
        By brokenLocator = By.id("this-id-does-not-exist");

        WebElement usernameField = autoHeal.findWithHealing(
                brokenLocator,
                "the Username input field on the login page"
        );

        assertNotNull(usernameField, "Healing should have returned a non-null element");

        usernameField.sendKeys("ps_venkatp_casinofriday");
        assertTrue(usernameField.getAttribute("value").equals("ps_venkatp_casinofriday"),
                "Should be able to type into the healed element, proving it's the real username field");
    }

//    @Test
//    void secondCallForSameBrokenLocatorUsesCacheNotAI() {
//        By brokenLocator = By.id("this-id-does-not-exist");
//
//        // First call: goes through the full AI healing path.
//        WebElement first = autoHeal.findWithHealing(brokenLocator, "the username input field");
//        assertNotNull(first);
//
//        // Second call with the SAME broken locator: should resolve instantly from the in-memory
//        // cache inside SimpleAutoHeal, without another AI round-trip.
//        // (We can't directly assert "no AI call happened" without more instrumentation,
//        //  but this at minimum proves the cached path still returns a valid, usable element.)
//        WebElement second = autoHeal.findWithHealing(brokenLocator, "the username input field");
//        assertNotNull(second);
//    }
}
