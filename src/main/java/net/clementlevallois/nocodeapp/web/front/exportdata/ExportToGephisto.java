/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.functions.BiblioCouplingBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGephisto {

    public static String exportAndReturnLink(String gexf, boolean shareGephistoPublicly) {

        try {

            byte[] readAllBytes = gexf.getBytes();
            String urlGephisto;
            try (InputStream inputStreamToSave = new ByteArrayInputStream(readAllBytes)) {
                String path = RemoteLocal.isLocal() ? "" : "gephisto/data/";
                String subfolder;
                long nextLong = ThreadLocalRandom.current().nextLong();
                String gephistoGexfFileName = "gephisto_" + String.valueOf(nextLong) + ".gexf";
                if (shareGephistoPublicly) {
                    subfolder = "public/";
                } else {
                    subfolder = "private/";
                }
                path = path + subfolder;
                if (RemoteLocal.isLocal()) {
                    path = SingletonBean.getRootOfProject() + "user_created_files";
                }
                File file = new File(path + gephistoGexfFileName);
                try (OutputStream output = new FileOutputStream(file, false)) {
                    inputStreamToSave.transferTo(output);
                }
                if (RemoteLocal.isTest()) {
                    urlGephisto = "https://test.nocodefunctions.com/gephisto/index.html?gexf-file=" + subfolder + gephistoGexfFileName;
                    
                } else {
                    urlGephisto = "https://nocodefunctions.com/gephisto/index.html?gexf-file=" + subfolder + gephistoGexfFileName;
                }
            }
            return urlGephisto;
        } catch (IOException ex) {
            Logger.getLogger(BiblioCouplingBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";

    }

}
