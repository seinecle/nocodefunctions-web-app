/*
 * author: Clément Levallois
 */
package net.clementlevallois.nocodeapp.web.front;

import java.io.IOException;
import jakarta.faces.application.ResourceHandler;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author LEVALLOIS
 */
@WebFilter(servletNames = {"Faces Servlet"}) // Must match <servlet-name> of your FacesServlet.
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain){
        try {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;
            
            if (!req.getRequestURI().startsWith(req.getContextPath() + ResourceHandler.RESOURCE_IDENTIFIER)) { // Skip JSF resources (CSS/JS/Images/etc)
                res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
                res.setHeader("Pragma", "no-cache"); // HTTP 1.0.
                res.setDateHeader("Expires", 0); // Proxies.
            }
            
            chain.doFilter(request, response);
        } catch (IOException | ServletException ex) {
            Logger.getLogger(NoCacheFilter.class.getName()).log(Level.SEVERE, null, "*** error in filter ***");
        }
    }

    // ...
}
