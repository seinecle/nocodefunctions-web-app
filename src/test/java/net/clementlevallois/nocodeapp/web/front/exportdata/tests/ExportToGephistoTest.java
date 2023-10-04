/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata.tests;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephisto;
import net.clementlevallois.nocodeapp.web.front.utils.ApplicationProperties;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGephistoTest {

    @BeforeEach
    public void loadProperties() throws IOException {
        ApplicationProperties.load();
    }

    @AfterEach
    public void resetEnv() throws IOException {
        System.setProperty("os.name", "windows");
        System.setProperty("test", "no");
    }

    @Test
    public void exportAndReturnLinkOnLinuxOnPrincipalServer() throws URISyntaxException, IOException {
        URL resource = ExportToGephistoTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        System.setProperty("os.name", "linux");
        String link = ExportToGephisto.exportAndReturnLink(gexf, true);
        assertThat(link).startsWith("https://nocodefunctions.com/user_created_files\\gephisto/index.html?gexf-file=public\\gephisto_");
    }
    
    @Test
    public void exportAndReturnLinkOnLinuxOnTestServer() throws URISyntaxException, IOException {
        URL resource = ExportToGephistoTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        System.setProperty("os.name", "linux");
        System.setProperty("test", "yes");
        String link = ExportToGephisto.exportAndReturnLink(gexf, true);
        assertThat(link).startsWith("https://test.nocodefunctions.com/user_created_files\\gephisto/index.html?gexf-file=public\\gephisto_");
    }

    @Test
    public void exportOnWindowsAndCheckFileExistence() throws URISyntaxException, IOException {
        URL resource = ExportToGephistoTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        System.setProperty("test", "yes");
        String link = ExportToGephisto.exportAndReturnLink(gexf, false);
        Path createdFile = Path.of(link);
        assertThat(createdFile).exists();
    }
}
