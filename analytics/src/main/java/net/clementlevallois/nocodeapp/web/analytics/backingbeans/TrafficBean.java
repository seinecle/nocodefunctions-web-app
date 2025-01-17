/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.analytics.backingbeans;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.primefaces.model.charts.bar.BarChartModel;

/**
 *
 * @author LEVALLOIS
 */
@Named(value = "trafficBean")
@ViewScoped
public class TrafficBean implements Serializable {

    private BarChartModel modelLaunch;
    @Inject
    ChartDataAndModelBean chartDataAndModelBean;

    @PostConstruct
    public void init() {
        try {
            chartDataAndModelBean.handleAndReadEvents();
        } catch (IOException ex) {
            Logger.getLogger(TrafficBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public BarChartModel getModelLaunch() throws IOException {
        return chartDataAndModelBean.getModelLaunch();
    }

    public void setModelLaunch(BarChartModel modelLaunch) {
        this.modelLaunch = modelLaunch;
    }

}
