/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.middleware;

import io.javalin.Javalin;

/**
 *
 * @author LEVALLOIS
 */
public class APIController {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7000);
        SingletonBean singleton = new SingletonBean();
        System.out.println("running the api");

        app.get("/sendUmigonReport", ctx -> {
            new Operations().sendUmigonReport(ctx.queryParam("key"));
        });

        app.get("/alive", ctx -> {
            ctx.result("alive");
        });

        app.get("/sendEvent", ctx -> {
            new Operations().logEvent(singleton, ctx.queryParam("event"), ctx.queryParam("useragent"));
        });
    }
}
