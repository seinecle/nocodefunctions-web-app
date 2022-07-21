/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.textops;

import java.util.Map;
import net.clementlevallois.umigon.ngram.ops.NGramFinder;
import net.clementlevallois.utils.Multiset;

/**
 *
 * @author LEVALLOIS
 */
public class TextOps {

    public Multiset<String> extractNGramsFromMapOfLines(Map<Integer, String> mapOfLines) {
        NGramFinder ngf;
        Multiset<String> freqNGramsGlobal = new Multiset();
        for (Integer i : mapOfLines.keySet()) {
            String line = mapOfLines.get(i);
            ngf = new NGramFinder(line);
            Map<String, Integer> runIt = ngf.runIt(4, false);
            freqNGramsGlobal.addAllFromMap(runIt);
        }
        return freqNGramsGlobal;

    }

}
