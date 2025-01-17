/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.middleware;

import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 *
 * @author LEVALLOIS
 */

public class SingletonBean {

    private UserAgentAnalyzer uaa;

    public SingletonBean() {
        uaa = UserAgentAnalyzer
                .newBuilder()
                .hideMatcherLoadStats()
                .withCache(10000)
                .build();
    }

    public UserAgentAnalyzer getUaa() {
        return uaa;
    }

}
