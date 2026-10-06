package com.demo;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class Main {

    public static void main(String[] args) {

        String apiKey = getApiKey();

        WebDriverManager.chromedriver().setup(); // auto-downloads matching chromedriver, no manual PATH setup
        WebDriver driver = new ChromeDriver();

        try {
            driver.get("https://the-internet.herokuapp.com/login");

            SimpleAutoHeal autoHeal = new SimpleAutoHeal(driver, apiKey);

            // Intentionally wrong locators, to force a healing event.
            By brokenUsernameLocator = By.id("this-id-does-not-exist");
            WebElement usernameField = autoHeal.findWithHealing(
                    brokenUsernameLocator,
                    "the username input field on the login form"
            );
            usernameField.sendKeys("tomsmith");
            System.out.println("SUCCESS: typed into the healed username field.");

            By brokenPasswordLocator = By.id("also-does-not-exist");
            WebElement passwordField = autoHeal.findWithHealing(
                    brokenPasswordLocator,
                    "the password input field on the login form"
            );
            passwordField.sendKeys("SuperSecretPassword!");
            System.out.println("SUCCESS: typed into the healed password field.");

        } finally {
            driver.quit();
        }
    }

    /**
     * Reads the Gemini API key from an environment variable instead of hardcoding it in source —
     * standard practice so you never accidentally commit a real key to Git.
     *
     * Set it before running, e.g. in Git Bash:
     *   export GEMINI_API_KEY="your-real-key-here"
     */
    public static String getApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is not set.\n" +
                    "Set it first, e.g.:\n" +
                    "  export GEMINI_API_KEY=\"your-real-key-here\"   (Git Bash / Linux / Mac)\n" +
                    "  setx GEMINI_API_KEY \"your-real-key-here\"     (Windows CMD, then restart terminal)"
            );
        }
        return key;
    }
}
