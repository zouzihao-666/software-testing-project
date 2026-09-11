package com.meethere.reservation;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ChromeDriverSupport {
    private ChromeDriverSupport() {
    }

    static WebDriver openBrowser() throws IOException {
        System.setProperty("webdriver.chrome.driver", prepareDriver().toString());
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        return new ChromeDriver(options);
    }

    private static Path prepareDriver() throws IOException {
        File chromeDirectory = findChromeDirectory();
        String version = chromeDirectory.getName();
        Path driver = Paths.get(System.getProperty("user.home"), ".cache",
                "meethere-webdriver", "chrome", version, "chromedriver.exe");
        if (Files.exists(driver)) {
            return driver;
        }

        Files.createDirectories(driver.getParent());
        URL download = new URL("https://storage.googleapis.com/chrome-for-testing-public/"
                + version + "/win64/chromedriver-win64.zip");
        try (InputStream input = download.openStream();
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith("chromedriver.exe")) {
                    Files.copy(zip, driver, StandardCopyOption.REPLACE_EXISTING);
                    return driver;
                }
            }
        }
        throw new IOException("下载文件中未找到chromedriver.exe");
    }

    private static File findChromeDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String[] locations = {
                localAppData + "\\Google\\Chrome\\Application",
                "C:\\Program Files\\Google\\Chrome\\Application",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application"
        };
        for (String location : locations) {
            File application = new File(location);
            File[] versions = application.listFiles(file -> file.isDirectory()
                    && file.getName().matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
            if (versions != null && versions.length > 0) {
                return Arrays.stream(versions)
                        .max(Comparator.comparing(File::getName)).get();
            }
        }
        throw new IllegalStateException("未找到Google Chrome");
    }
}
