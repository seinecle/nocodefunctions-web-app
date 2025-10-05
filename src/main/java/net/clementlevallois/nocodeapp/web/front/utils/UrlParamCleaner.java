/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.utils;

/**
 *
 * @author clevallois
 */
public class UrlParamCleaner {

    public static String getRightmostPart(String input) {
        if (input == null || !input.contains("=")) {
            return input; // Return as is if no '=' found or input is null
        }
        return input.substring(input.lastIndexOf("=") + 1);
    }

}
