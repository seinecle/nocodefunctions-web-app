/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import java.util.ResourceBundle;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;
import javax.servlet.http.HttpServletRequest;
import net.clementlevallois.umigon.model.Categories.Category;
import net.clementlevallois.umigon.model.Document;

/**
 *
 * @author LEVALLOIS
 */
@FacesConverter(forClass = Document.class, managed = true)
public class DocumentConverter implements Converter<Document> {

    private final String PATHLOCALE = "net.clementlevallois.nocodeapp.web.front.resources.i18n.text";

    @Override
    public Document getAsObject(FacesContext context, UIComponent component, String value) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, Document document) {
        if (document == null) {
            return "";
        }
        ResourceBundle bundle = ResourceBundle.getBundle(PATHLOCALE, FacesContext.getCurrentInstance().getViewRoot().getLocale());
        HttpServletRequest origRequest = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
        String requestURI = origRequest.getRequestURI();
        if (requestURI.contains("umigon")) {
            if (document.getSentiment().equals(new Category("11"))) {
                return bundle.getString("general.nouns.sentiment_positive");
            }
            if (document.getSentiment().equals(new Category("12"))) {
                return bundle.getString("general.nouns.sentiment_negative");
            } else if (document.getSentiment().equals(Category._10)) {
                return bundle.getString("general.nouns.sentiment_neutral");
            }
        }
        return "conditions not met";
    }

}
