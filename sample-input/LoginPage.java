package com.demo.pages;

/**
 * A typical Page Object — just locators, no descriptions.
 * This is the kind of file most real frameworks already have hundreds of.
 */
public class LoginPage {

    private static final String USERNAME_FIELD = "#username";
    private static final String PASSWORD_FIELD = "#password";
    private static final String LOGIN_BTN = "button[type='submit']";
    private static final String FORGOT_PASSWORD_LINK = ".forgot-password";
    private static final String ERROR_MESSAGE_BANNER = "#flash";
    private static final String REMEMBER_ME_CHECKBOX = "input[name='remember']";
}
