/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front;

// see https://stackoverflow.com/questions/9905946/url-rewriting-solution-needed-for-jsf
import java.util.List;
import java.util.Map;
import jakarta.faces.application.ViewHandler;
import jakarta.faces.application.ViewHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletContext;

public class CustomViewHandler extends ViewHandlerWrapper {

    private ViewHandler wrappped;

    public CustomViewHandler(ViewHandler wrappped) {
        super();
        this.wrappped = wrappped;
    }

    @Override
    public ViewHandler getWrapped() {
        return wrappped;
    }

    @Override
    public String getActionURL(FacesContext context, String viewId) {
        String url = super.getActionURL(context, viewId);
        return removeContextPath(context, url);
    }

    @Override
    public String getRedirectURL(FacesContext context, String viewId, Map<String, List<String>> parameters, boolean includeViewParams) {
        String url = super.getRedirectURL(context, viewId, parameters, includeViewParams);
        return removeContextPath(context, url);
    }

    @Override
    public String getResourceURL(FacesContext context, String path) {
        String url = super.getResourceURL(context, path);
        return removeContextPath(context, url);
    }

    private String removeContextPath(FacesContext context, String url) {
        ServletContext servletContext = (ServletContext) context.getExternalContext().getContext();
        String contextPath = servletContext.getContextPath();
        if ("".equals(contextPath)) {
            return url; // root context path, nothing to remove
        }
        return url.startsWith(contextPath) ? url.substring(contextPath.length()) : url;
    }
}
