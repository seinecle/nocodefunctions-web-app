/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.Serializable;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.event.AjaxBehaviorEvent;
import javax.inject.Inject;
import javax.inject.Named;
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
