/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans.tests;

import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class SingletonBeanTest {
    
    @Test
    public void constructor(){
        ApplicationPropertiesBean app = new ApplicationPropertiesBean();
        assertThat(app.getGephistoRootFullPath()).isNotNull();
    }
    
}
