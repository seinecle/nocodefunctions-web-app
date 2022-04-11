/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.networkops;

import it.uniroma1.dis.wsngroup.gexf4j.core.Edge;
import it.uniroma1.dis.wsngroup.gexf4j.core.EdgeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import it.uniroma1.dis.wsngroup.gexf4j.core.Graph;
import it.uniroma1.dis.wsngroup.gexf4j.core.Mode;
import it.uniroma1.dis.wsngroup.gexf4j.core.Node;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeClass;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeList;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeType;
import it.uniroma1.dis.wsngroup.gexf4j.core.data.AttributeValue;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.GexfImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.StaxGraphWriter;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeListImpl;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.data.AttributeValueImpl;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Iterator;

/**
 *
 * @author LEVALLOIS
 */
public class GexfBuilder {

    public File buildCowo(org.gephi.graph.api.Graph graphGephi, Path outputPath) {

       File f = outputPath.toFile();
        if (graphGephi == null){
            return f;
        }
        if (graphGephi.getNodes() == null){
            return f;
        }
        Gexf gexf = new GexfImpl();
        Calendar date = Calendar.getInstance();

        gexf.getMetadata()
                .setLastModified(date.getTime())
                .setCreator("nocode functions")
                .setDescription("A word cloud where two words are connected if they appear on the same line");
        gexf.setVisualization(true);

        Graph graph = gexf.getGraph();
        graph.setDefaultEdgeType(EdgeType.UNDIRECTED).setMode(Mode.STATIC);

        AttributeList attrListNodes = new AttributeListImpl(AttributeClass.NODE);
        AttributeList attrListEdges = new AttributeListImpl(AttributeClass.EDGE);

        AttributeImpl countTerms = new AttributeImpl("countTerms", AttributeType.INTEGER, "Count");
        attrListNodes.add(countTerms);
        AttributeImpl countPairs = new AttributeImpl("countPairs", AttributeType.INTEGER, "Count");
        attrListEdges.add(countPairs);
        graph.getAttributeLists().add(attrListNodes);
        graph.getAttributeLists().add(attrListEdges);
        
        Iterator<org.gephi.graph.api.Node> iterator = graphGephi.getNodes().iterator();
        while (iterator.hasNext()) {
            org.gephi.graph.api.Node nodeGephi = iterator.next();
            Node nodeGexf = graph.createNode(nodeGephi.getLabel());
            nodeGexf.setLabel(nodeGephi.getLabel()).setSize(20);
            AttributeValue att = new AttributeValueImpl(countTerms);
            Integer countNodes = (Integer) nodeGephi.getAttribute("countTerms");
            att.setValue(countNodes.toString());
            nodeGexf.getAttributeValues().add(att);
//            idToNode.put(nodeGephi.getLabel(), nodeGexf);
        }

        //creating gexf edges
        Iterator<org.gephi.graph.api.Edge> it2 = graphGephi.getEdges().iterator();
        while (it2.hasNext()) {
            org.gephi.graph.api.Edge edgeGephi = it2.next();
            Node node1 = graph.getNode(edgeGephi.getSource().getLabel());
            Node node2 = graph.getNode(edgeGephi.getTarget().getLabel());
            Edge edge = node1.connectTo(node2);
            edge.setWeight((float) edgeGephi.getWeight());
            AttributeValue att = new AttributeValueImpl(countPairs);
            Integer countEdges = (Integer) edgeGephi.getAttribute("countPairs");
            att.setValue(countEdges.toString());
            edge.getAttributeValues().add(att);
        }

        StaxGraphWriter graphWriter = new StaxGraphWriter();

        Writer out;
        try {
            out = new OutputStreamWriter(new FileOutputStream(outputPath.toString()), StandardCharsets.UTF_8.name());
            graphWriter.writeToStream(gexf, out, f.getAbsolutePath(), StandardCharsets.UTF_8.name());
            System.out.println(f.getAbsolutePath());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return f;
    }
    public File buildGaze(org.gephi.graph.api.Graph graphGephi, Path outputPath) {

       File f = outputPath.toFile();
        if (graphGephi == null){
            return f;
        }
        if (graphGephi.getNodes() == null){
            return f;
        }
        
        
        Gexf gexf = new GexfImpl();
        Calendar date = Calendar.getInstance();

        gexf.getMetadata()
                .setLastModified(date.getTime())
                .setCreator("nocode functions")
                .setDescription("A word cloud created with the Gaze function: through similarities or co-occurrences.");
        gexf.setVisualization(true);

        Graph graph = gexf.getGraph();
        graph.setDefaultEdgeType(EdgeType.UNDIRECTED).setMode(Mode.STATIC);

        AttributeList attrListNodes = new AttributeListImpl(AttributeClass.NODE);
        AttributeList attrListEdges = new AttributeListImpl(AttributeClass.EDGE);

        AttributeImpl countTerms = new AttributeImpl("countTerms", AttributeType.INTEGER, "Count");
        attrListNodes.add(countTerms);
        AttributeImpl countPairs = new AttributeImpl("countPairs", AttributeType.INTEGER, "Count");
        attrListEdges.add(countPairs);
        graph.getAttributeLists().add(attrListNodes);
        graph.getAttributeLists().add(attrListEdges);
        
        Iterator<org.gephi.graph.api.Node> iterator = graphGephi.getNodes().iterator();
        while (iterator.hasNext()) {
            org.gephi.graph.api.Node nodeGephi = iterator.next();
            Node nodeGexf = graph.createNode(nodeGephi.getLabel());
            nodeGexf.setLabel(nodeGephi.getLabel()).setSize(20);
        }

        //creating gexf edges
        Iterator<org.gephi.graph.api.Edge> it2 = graphGephi.getEdges().iterator();
        while (it2.hasNext()) {
            org.gephi.graph.api.Edge edgeGephi = it2.next();
            Node node1 = graph.getNode(edgeGephi.getSource().getLabel());
            Node node2 = graph.getNode(edgeGephi.getTarget().getLabel());
            Edge edge = node1.connectTo(node2);
            edge.setWeight((float) edgeGephi.getWeight());
        }

        StaxGraphWriter graphWriter = new StaxGraphWriter();

        Writer out;
        try {
            out = new OutputStreamWriter(new FileOutputStream(outputPath.toString()), StandardCharsets.UTF_8.name());
            graphWriter.writeToStream(gexf, out, f.getAbsolutePath(), StandardCharsets.UTF_8.name());
            System.out.println(f.getAbsolutePath());
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        return f;
    }

}
