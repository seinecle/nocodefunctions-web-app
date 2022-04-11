// Step 1. We create a graph object.
var graph = Viva.Graph.graph();
// Step 2. We add nodes and edges to the graph:
graph.addNode('anvaka', '91bad8ceeec43ae303790f8fe238164b');
graph.addNode('indexzero', 'd43e8ea63b61e7669ded5b9d3c2e980f');
graph.addLink('anvaka', 'indexzero');

var graphics = Viva.Graph.View.svgGraphics(),
        nodeSize = 24;

graphics.node(function (node) {
    // This time it's a group of elements: http://www.w3.org/TR/SVG/struct.html#Groups
    var ui = Viva.Graph.svg('g'),
            // Create SVG text element with user id as content
            svgText = Viva.Graph.svg('text').attr('y', '-4px').text(node.id),
            img = Viva.Graph.svg('image')
            .attr('width', nodeSize)
            .attr('height', nodeSize);

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
// Render the graph
            var renderer = Viva.Graph.View.renderer(graph, {
                    graphics : graphics
                });
            renderer.run();
console.log("yey from script file");