package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class SingletonBean {


    public SingletonBean() {
        setStage();
    }
    
    private void setStage(){
        if (RemoteLocal.isTest() || RemoteLocal.isLocal()){
            System.setProperty("projectStage", "Development");
            System.out.println("project stage set to DEVELOPMENT");
        }else{
            System.setProperty("projectStage", "Production");            
            System.out.println("project stage set to PRODUCTION");
        }
    }

}
