/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;

/**
 *
 * @author clevallois
 */
@ApplicationScoped
public class Gexf2VvService {

    @Inject
    ExportToVosViewer exportToVosViewer;

    public String buildVosviewerUrl(String jobId, boolean share, String item, String link, String linkStrength) {
        return exportToVosViewer.exportAndReturnLinkForConversionToVV(jobId, share, item, link, linkStrength);
    }
}
