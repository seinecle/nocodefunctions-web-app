/** 
 * PrimeBlocks Layout
 */
$(function() {
    var loc = location.pathname;
    var links = $('.topbar-menu').find('a');

    links.each(function() {
        $(this).toggleClass('router-link-active', $(this).attr('href').endsWith(loc));
    });

    var activeLink = links.filter('.router-link-active');
    if (!activeLink.length) {
        links.eq(0).addClass('router-link-active');
    }
});

/**
 * home.xhtml scroll function
 */
function scrollToSection(section) {
    jQuery('html, body').animate({
        scrollTop: jQuery("#" + section).offset().top
    }, 500);
}

/**
 * PrimeBlocks Configurator
 */
App = {
    updateInputStyle: function(value) {
        if (value === 'filled')
            $(document.body).addClass('ui-input-filled');
        else
            $(document.body).removeClass('ui-input-filled');
    },

    changeTheme: function(theme, dark) {
        // change component theme
        var themeLink = PrimeFaces.getThemeLink();
        var themeURL = themeLink.attr('href'),
            plainURL = themeURL.split('&')[0],
            oldTheme = plainURL.split('ln=')[1],
            newThemeURL = themeURL.replace(oldTheme, 'primefaces-' + theme);

        this.replaceLink(themeLink, newThemeURL);

        // change primeflex theme
        var linkElement = $('link[href*="primeflex/themes"]');
        var href = linkElement.attr('href');
        var index = href.indexOf('themes') + 7;
        var css_index = href.indexOf('.css');
        var currentTheme = href.substring(index, css_index);
        this.replaceLink(linkElement, href.replace(currentTheme, theme));

        var wrapper = $(document.body).children('.layout-wrapper');
        if (dark)
            wrapper.addClass('layout-wrapper-dark');
        else
            wrapper.removeClass('layout-wrapper-dark');
    },

    replaceLink: function(linkElement, href) {
        PrimeFaces.ajax.RESOURCE = 'javax.faces.Resource';
        
        var isIE = /(MSIE|Trident\/|Edge\/)/i.test(navigator.userAgent);

        if (isIE) {
            linkElement.attr('href', href);
        }
        else {
            var cloneLinkElement = linkElement.clone(false);

            cloneLinkElement.attr('href', href);
            linkElement.after(cloneLinkElement);
            
            cloneLinkElement.off('load').on('load', function() {
                linkElement.remove();
            });
        }
    },

    beforeResourceChange: function() {
        PrimeFaces.ajax.RESOURCE = null;    //prevent resource append
    }
    
    
}