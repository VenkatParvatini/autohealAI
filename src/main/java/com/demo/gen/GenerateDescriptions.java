package com.demo.gen;

import com.demo.Main;

import java.nio.file.Path;

public class GenerateDescriptions {

    public static void main(String[] args) throws Exception {
        String apiKey = Main.getApiKey(); // reuses the same env-variable reader from Main.java

        DescriptionGenerator generator = new DescriptionGenerator(apiKey);

        generator.generate(
                Path.of("sample-input/LoginPage.java"),   // input: the Page Object to scan
                "LoginPageDescriptions",                   // name for the generated class
                Path.of("sample-input/LoginPageDescriptions.java") // output path
        );
    }
}
