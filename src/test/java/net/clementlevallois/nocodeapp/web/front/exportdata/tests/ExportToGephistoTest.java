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
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephisto;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGephistoTest {

    @Test
    public void exportAndReturnLink() throws URISyntaxException, IOException {
        URL resource = ExportToGephistoTest.class.getClassLoader().getResource("empty-network.gexf");
        String gexf = Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        String link = ExportToGephisto.exportAndReturnLink(gexf, true);

        assertThat(link).startsWith("https://nocodefunctions.com/gephisto/index.html?gexf-file=");
        
        SingletonBean sb = new SingletonBean();
        String fileName = link.substring(link.lastIndexOf('/') + 1);
        Path createdFile = Path.of(SingletonBean.getRootOfProject(), "user_created_files", fileName);
        assertThat(createdFile).exists();

    }
}
