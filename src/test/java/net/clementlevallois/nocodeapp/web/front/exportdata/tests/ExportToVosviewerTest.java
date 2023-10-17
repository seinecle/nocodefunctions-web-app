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
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToVosviewerTest {

    private static ApplicationPropertiesBean applicationProperties;
    
    
    @BeforeAll
    public static void loadProperties() throws IOException {
        applicationProperties = new ApplicationPropertiesBean();
    }

    @AfterEach
    public void resetEnv() throws IOException {
        System.setProperty("os.name", "windows");
        System.setProperty("test", "no");
    }

    @Test
    public void exportAndReturnLinkOnLinuxOnPrincipalServer() throws URISyntaxException, IOException {
        URL resource = ExportToVosviewerTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        System.setProperty("os.name", "linux");
        boolean sharePublicly = false;
        String apiPort = applicationProperties.getPrivateProperties().getProperty("nocode_api_port");
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(sharePublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(gexf, apiPort, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
        assertThat(linkToVosViewer).startsWith("https://nocodefunctions.com/user_created_files\\vosviewer/index.html?json=private\\vosviewer_");
    }
    
    @Test
    public void exportAndReturnLinkOnLinuxOnTestServer() throws URISyntaxException, IOException {
        URL resource = ExportToVosviewerTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        String apiPort = applicationProperties.getPrivateProperties().getProperty("nocode_api_port");
        System.setProperty("os.name", "linux");
        System.setProperty("test", "yes");
        boolean sharePublicly = true;
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(sharePublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(gexf, apiPort, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
        assertThat(linkToVosViewer).startsWith("https://test.nocodefunctions.com/user_created_files\\vosviewer/index.html?json=public\\vosviewer_");
    }

    @Test
    public void exportOnWindowsAndCheckFileExistence() throws URISyntaxException, IOException {
        URL resource = ExportToVosviewerTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        System.setProperty("test", "yes");
        boolean sharePublicly = false;
        String apiPort = applicationProperties.getPrivateProperties().getProperty("nocode_api_port");
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(sharePublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(gexf, apiPort, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
        Path createdFile = Path.of(linkToVosViewer);
        assertThat(createdFile).exists();
    }
}
