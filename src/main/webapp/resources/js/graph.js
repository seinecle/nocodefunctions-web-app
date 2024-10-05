/* global Viva */
var executions = 0;
function main(nodes, edges, mincount, maxcount) {
    if (executions > 0) {
        return;
    }
    executions++;


// Step 1. We create a graph object.
    var graph = Viva.Graph.graph();

    // Step 2. We add nodes and edges to the graph:
    var graphics = Viva.Graph.View.svgGraphics(),
            nodeSize = 24;
    
    
    const jsonNodes = JSON.parse(nodes);
    const jsonEdges = JSON.parse(edges);

    for (var key in jsonNodes) {
        if (jsonNodes.hasOwnProperty(key)) {
            console.log(key + " (node id) -> " + jsonNodes[key]);
            graph.addNode(key, key);
        }
    }

    for (var key in jsonEdges) {
        if (jsonEdges.hasOwnProperty(key)) {
            console.log(key + " (edge id) -> " + jsonEdges[key]);
            const edge = jsonEdges[key];
            let source;
            let target;
            for (let [key, value] of Object.entries(edge)) {
                if (`${key}` === "source") {
                    source = `${value}`;
                }
                if (`${key}` === "target") {
                    target = `${value}`;
                }
            }
            graph.addLink(source, target);
        }
    }

    graphics.node(function (node) {
        // This time it's a group of elements: http://www.w3.org/TR/SVG/struct.html#Groups
        var ui = Viva.Graph.svg('g'),
                // Create SVG text element with user id as content
                svgText = Viva.Graph.svg('text')
                .attr('y', '-4px')
                .attr('font-size', scaleValue(node.data, [mincount, maxcount], [15, 60]))
                .text(node.id),
                img = Viva.Graph.svg('image')
                .attr('width', node.data)
                .attr('height', node.data);

        ui.append(svgText);
        ui.append(img);
        return ui;
    }).placeNode(function (nodeUI, pos) {
        // 'g' element doesn't have convenient (x,y) attributes, instead
        // we have to deal with transforms: http://www.w3.org/TR/SVG/coords.html#SVGGlobalTransformAttribute
        nodeUI.attr('transform',
                'translate(' +
                (pos.x - nodeSize / 2) + ',' + (pos.y - nodeSize / 2) +
                ')');
    });


// Step 3. Render the graph.

    var renderer = Viva.Graph.View.renderer(graph, {
        graphics: graphics,
        container: document.getElementById('graphDiv')
    });
    renderer.run();
}

function scaleValue(value, from, to) {
    var scale = (to[1] - to[0]) / (from[1] - from[0]);
    var capped = Math.min(from[1], Math.max(from[0], value)) - from[0];
    return ~~(capped * scale + to[0]);
}