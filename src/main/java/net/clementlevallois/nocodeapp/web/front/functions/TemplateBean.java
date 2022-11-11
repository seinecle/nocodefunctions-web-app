/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.Serializable;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.primefaces.component.colorpicker.ColorPicker;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class TemplateBean implements Serializable {

    @Inject
    NotificationService service;


    public TemplateBean() {
    }

    public void onload() {
    }

}
