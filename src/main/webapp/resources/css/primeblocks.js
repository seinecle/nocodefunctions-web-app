var PrimeBlocks = {
    styleClass: function(el) {
        var props = PrimeBlocks.utils.getProps(el, 'pbStyleclass');

        return {
            init: function() {
                if (el) {
                    el['styleclass-initialized'] = true;

                    this.bindEvents();
                }

                return this;
            },

            run: function() {
                this.target = this.resolveTarget();
    
                if (props.toggleClass) {
                    if (PrimeBlocks.utils.hasClass(this.target, props.toggleClass))
                        PrimeBlocks.utils.removeClass(this.target, props.toggleClass);
                    else
                        PrimeBlocks.utils.addClass(this.target, props.toggleClass);
                    this.toggleDocumentListener(PrimeBlocks.utils.isVisible(this.target));
                }
                else {
                    if (this.target.offsetParent === null)
                        this.enter();
                    else
                        this.leave();
                }
            },

            enter: function() {
                var $this = this;
    
                if (props.enterActiveClass) {
                    if (!this.animating) {
                        this.animating = true;
    
                        if (props.enterActiveClass === 'slidedown') {
                            this.target.style.height = '0px';
                            PrimeBlocks.utils.removeClass(this.target, 'hidden');
                            this.target.style.maxHeight = this.target.scrollHeight + 'px';
                            PrimeBlocks.utils.addClass(this.target, 'hidden');
                            this.target.style.height = '';
                        }
    
                        PrimeBlocks.utils.addClass(this.target, props.enterActiveClass);
                        if (props.enterClass) {
                            PrimeBlocks.utils.removeClass(this.target, props.enterClass);
                        }
    
                        this.enterListener = function() {
                            PrimeBlocks.utils.removeClass($this.target, props.enterActiveClass);
                            if (props.enterToClass) {
                                PrimeBlocks.utils.addClass($this.target, props.enterToClass);
                            }
                            $this.target.removeEventListener('animationend', $this.enterListener);
    
                            if (props.enterActiveClass === 'slidedown') {
                                $this.target.style.maxHeight = '';
                            }
                            $this.animating = false;
                        };
    
                        this.target.addEventListener('animationend', this.enterListener);
                    }
                }
                else {
                    if (props.enterClass) {
                        PrimeBlocks.utils.removeClass(this.target, props.enterClass);
                    }
    
                    if (props.enterToClass) {
                        PrimeBlocks.utils.addClass(this.target, props.enterToClass);
                    }
                }

                this.toggleDocumentListener(true);
            },
    
            leave: function() {
                var $this = this;
    
                if (props.leaveActiveClass) {
                    if (!this.animating) {
                        this.animating = true;
                        PrimeBlocks.utils.addClass(this.target, props.leaveActiveClass);
                        if (props.leaveClass) {
                            PrimeBlocks.utils.removeClass(this.target, props.leaveClass);
                        }
    
                        this.leaveListener = function() {
                            PrimeBlocks.utils.removeClass($this.target, props.leaveActiveClass);
                            if (props.leaveToClass) {
                                PrimeBlocks.utils.addClass($this.target, props.leaveToClass);
                            }
                            $this.target.removeEventListener('animationend', $this.leaveListener);
                            $this.animating = false;
                        };
    
                        this.target.addEventListener('animationend', this.leaveListener);
                    }
                }
                else {
                    if (props.leaveClass) {
                        PrimeBlocks.utils.removeClass(this.target, props.leaveClass);
                    }
    
                    if (props.leaveToClass) {
                        PrimeBlocks.utils.addClass(this.target, props.leaveToClass);
                    }
                }

                this.toggleDocumentListener(false);
            },

            reset: function() {
                if (this.target) {
                    if (props.toggleClass) {
                        if (PrimeBlocks.utils.hasClass(this.target, props.toggleClass))
                            PrimeBlocks.utils.removeClass(this.target, props.toggleClass);
                        else
                            PrimeBlocks.utils.addClass(this.target, props.toggleClass);
                    }
                    else {
                        this.leave();
                    }

                    this.toggleDocumentListener(false);
                }
            },
    
            resolveTarget: function() {
                if (this.target) {
                    return this.target;
                }
    
                switch (props.selector) {
                    case '@next':
                        return el.nextElementSibling;
    
                    case '@prev':
                        return el.previousElementSibling;
    
                    case '@parent':
                        return el.parentElement;
    
                    case '@grandparent':
                        return el.parentElement.parentElement;
    
                    default:
                        return document.querySelector(props.selector);
                }
            },

            toggleDocumentListener: function(when) {
                if (props.hideOnOutsideClick) {
                    if (when)
                        this.bindDocumentListener();
                    else
                        this.unbindDocumentListener();
                }
            },
    
            bindDocumentListener: function() {
                if (!this.documentListener) {
                    var $this = this;
    
                    this.documentListener = function(event) {
                        if (!el.isSameNode(event.target) && !el.contains(event.target) && !$this.target.contains(event.target)) {
                            $this.reset();
                        }
                    };
    
                    el.ownerDocument.addEventListener('click', this.documentListener);
                }
            },
    
            unbindDocumentListener: function() {
                if (this.documentListener) {
                    el.ownerDocument.removeEventListener('click', this.documentListener);
                    this.documentListener = null;
                }
            },
    
            bindEvents: function() {
                var $this = this;
                this.eventListener = function() {
                    $this.run();
                }
    
                if (el.dataset.pbStyleclassEvent === 'hover') {
                    el.addEventListener('mouseenter', this.eventListener);
                    el.addEventListener('mouseleave', this.eventListener);
                }
                else {
                    el.addEventListener('click', this.eventListener);
                }
            }
        }
    },
    tabList: function(list, tabs, tab) {
        var props = PrimeBlocks.utils.getProps(tab, 'pbTab');

        return {
            init: function() {
                if (tab) {
                    list['tablist-initialized'] = true;

                    this.bindEvents();
                }

                return this;
            },

            run: function() {
                this.selector = this.resolveSelector();
                var clearTabs = function() {
                    for (var i = 0; i < tabs.length; i++) {
                        var tb = tabs[i];
                        var tb_data = tb.dataset.pbTab.replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/g, '"$2": ').replaceAll('\'', '"');
                        var tb_props = JSON.parse(tb_data) || {};
    
                        PrimeBlocks.utils.removeMultipleClasses(tb, tb_props.toggleClass);
                        PrimeBlocks.utils.removeMultipleClasses(tb, tb_props.enterClass);
                        PrimeBlocks.utils.addMultipleClasses(tb, tb_props.leaveClass);
                        PrimeBlocks.utils.addMultipleClasses(document.querySelector(tb_props.selector), tb_props.selectorToggleClass);
                    }
                };

                if (props.toggleClass && !PrimeBlocks.utils.hasMultipleClasses(tab, props.toggleClass)) {
                    clearTabs();
                    
                    PrimeBlocks.utils.addMultipleClasses(tab, props.toggleClass);
                    PrimeBlocks.utils.removeMultipleClasses(this.selector, props.selectorToggleClass);
                }

                if (props.enterClass && props.leaveClass && !PrimeBlocks.utils.hasMultipleClasses(tab, props.enterClass)) {
                    clearTabs();

                    PrimeBlocks.utils.addMultipleClasses(tab, props.enterClass);
                    PrimeBlocks.utils.removeMultipleClasses(tab, props.leaveClass);
                    PrimeBlocks.utils.removeMultipleClasses(this.selector, props.selectorToggleClass);
                }
            },

            bindEvents: function() {
                var $this = this;
                this.eventListener = function() {
                    $this.run();
                }
    
                if (tab.dataset.pbTabEvent === 'hover') {
                    tab.addEventListener('mouseenter', this.eventListener);
                    tab.addEventListener('mouseleave', this.eventListener);
                }
                else {
                    tab.addEventListener('click', this.eventListener);
                }
            },

            resolveSelector: function() {
                if (this.selector) {
                    return this.selector;
                }
    
                return document.querySelector(props.selector);
            }
        }
    },
    utils: {
        getProps: function(el, dataName) {
            if (el) {
                var data = el.dataset[dataName].replace(/(['"])?([a-z0-9A-Z_]+)(['"])?:/g, '"$2": ').replaceAll('\'', '"');
                var props = JSON.parse(data) || {};
        
                return props;
            }

            return {};
        },

        addClass: function(element, className) {
            if (element && className) {
                if (element.classList)
                    element.classList.add(className);
                else
                    element.className += ' ' + className;
            }
        },

        removeClass: function(element, className) {
            if (element && className) {
                if (element.classList)
                    element.classList.remove(className);
                else
                    element.className = element.className.replace(new RegExp('(^|\\b)' + className.split(' ').join('|') + '(\\b|$)', 'gi'), ' ');
            }
        },

        hasClass: function(element, className) {
            if (element) {
                if (element.classList)
                    return element.classList.contains(className);
                else
                    return new RegExp('(^| )' + className + '( |$)', 'gi').test(element.className);
            }
        },

        addMultipleClasses: function(element, className) {
            if (element && className) {
                var styles = className.split(' ');
                for (var i = 0; i < styles.length; i++) {
                    this.addClass(element, styles[i]);
                }
            }
        },
    
        removeMultipleClasses: function(element, className) {
            if (element && className) {
                var styles = className.split(' ');
                for (var i = 0; i < styles.length; i++) {
                    this.removeClass(element, styles[i]);
                }
            }
        },

        hasMultipleClasses: function(element, className) {
            if (element && className) {
                var styles = className.split(' ');
                var hasClass = true;
                for (var i = 0; i < styles.length; i++) {
                    if (!this.hasClass(element, styles[i])) {
                        return false;
                    }
                }

                return hasClass;
            }

            return false;
        },

        find: function(element, selector) {
            return element ? Array.from(element.querySelectorAll(selector)) : [];
        },

        isVisible: function(element) {
            return element && element.offsetParent != null;
        }
    }
}

$(function() {
    /* StyleClass */
    /** For click event */
    $(document.body).off('click.primeblock-styleclass', '*[data-pb-styleclass]').on('click.primeblock-styleclass', '*[data-pb-styleclass]', function() {
        if (!this['styleclass-initialized']) {
            PrimeBlocks.styleClass(this).init().run();
        }
    });

    /** For hover event */
    $(document.body).off('mouseenter.primeblock-styleclass', '*[data-pb-styleclass][data-pb-styleclass-event="hover"]').on('mouseenter.primeblock-styleclass', '*[data-pb-styleclass][data-pb-styleclass-event="hover"]', function() {
        if (!this['styleclass-initialized']) {
            PrimeBlocks.styleClass(this).init();
        }
    });

    /* TabList */
    var tabCallback = function(c_tab) {
        var list = $(c_tab).closest('*[data-pb-tablist]')[0];
        if (list && !list['tablist-initialized']) {
            var tabs = $(list).find('*[data-pb-tab]');

            for (var i = 0; i < tabs.length; i++) {
                var tab = tabs[i];
                var model = PrimeBlocks.tabList(list, tabs, tab).init();
                if (tab === c_tab) model.run();
            }
        }
    };

    /** For click event */
    $(document.body).off('click.primeblock-tablist', '*[data-pb-tab]').on('click.primeblock-tablist', '*[data-pb-tab]', function() {
        tabCallback(this);
    });

    /** For hover event */
    $(document.body).off('mouseenter.primeblock-tablist', '*[data-pb-tab][data-pb-tab-event="hover"]').on('mouseenter.primeblock-tablist', '*[data-pb-tab][data-pb-tab-event="hover"]', function() {
        tabCallback(this);
    });
}); 