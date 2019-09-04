'use strict';

// margins from the border of the canvas that wont be used for drawing
var margin = {top: 40, right: 40, bottom: 80, left: 40};
// available drawing space for the canvas
// var width = 800;
// var height = 400;

/**
 * @class TargetMineGraph
 * @classdesc Generic description of graph elements required to display a graph
 * as part of a report page in TargetMine
 * @author Rodolfo Allendes
 * @version 0.1
 */
class TargetMineGraph{

  /**
   * Initialize a new instance of TargetMineGraph
   *
   * @param {string} name The title for the graph
   * @param {int} width The width of the viewbox in the svg element
   */
  constructor(name, width, height){
    this.name = name;
    this.data = undefined;

    this.width = width;
    this.height = height;

    this.xAxis = undefined;
    this.xLabels = undefined;
  }

  /**
   * Load data for graph display
   * Data is provided by TargetMine in the form of an ArrayList. The first
   * element includes the names for the data columns, with the following n-1
   * elements of the array each representing a tab separated data point. Since
   * the ArrayList is converted to its String representation before being
   * transfered, items in the Array are separated by the character ','
   *
   * @param {string} data The Java ArrayList string representation of the data
   * retrieved from the database for the construction of the graph.
   */
  loadData(data){
    /* cleaning of the string provided triming of starting and end charachters
     * and replacement of ', ' for line separators */
    data = data.substring(1, data.length-1);
    data = data.replace(/, /g, '\n');
    /* local storage of the data */
    this.data = d3.tsvParse(data, d3.autoType);
  }

  /**
   * Initialize the labels of the X axis of the Graph.
   * Indiviudual values are required to construct the categorical scales used
   * for color and shape, later used to map distinctive points at the time of
   * display.
   *
   * @param {string} column The column in the data whose individual values will
   * be used for the initial labesl
   */
  initXLabels(column){
    this.xLabels = this.data.reduce(function(prev, current){
      if( ! prev.includes(current[column]) )
        prev.push( current[column] );
      return prev;
    }, ['']);
    this.xLabels = this.xLabels.concat(['']);
  }

  /**
   * Dynamically generate the X-Axis used in the graph
   * Generate a categorical scale and axis, to be used in the display of the
   * current graph, based in the given key. The key must be a valid identifier
   * for the graph's data element.
   *
   * @param {string} key The key (in the dataset) used to define the axis
   */
  initXAxis(){
    /* The bottom axis will map to a series of discrete pixel values, evenly
     * distributed along the drawing area, we use startx and dx to define the
     * starting and step that define the ticks in the axis */
    let startx = margin.left;
    let dx = (this.width-margin.left-margin.right)/(this.xLabels.length-1);
    /* define an ordinal scale */
    let scale = d3.scaleOrdinal()
      .domain(this.xLabels)
      .range(this.xLabels.map(function(k,i){ return startx+i*dx; }))
      ;
    /* create the corresponding axis */
    this.xAxis = d3.axisBottom(scale);
  }
}

/**
 * @class GeneExpressionGraph
 * @classdesc Used to display a Gene Expression level graph in the report page of genes
 * @author Rodolfo Allendes
 * @version 0.1
 */
class GeneExpressionGraph extends TargetMineGraph{

  /**
   * Initialize a new instance of GeneExpressionGraph
   *
   * @param {string} name The title for the graph
   */
  constructor(name, width, height){
    /** initialize super-class attributes */
    super(name, width, height);

    /* Add a base component to the SVG element. The drawing of each element in
     * the svg will be nested to this basic component */
    let graph = d3.select('svg#canvas').append('g')
      .attr('id', 'graph')
    ;
  }

  /**
   *
   */
  expandXLabels(key, category, subcat){
    let newLabels = this.data.reduce(function(prev, current){
      if( current[category] === key && !prev.includes(current[subcat]) )
        prev.push(current[subcat]);
      return prev;
    }, []);
    // newLabels = newLabels.concat(['']);
    console.log(newLabels);

    let i = this.xLabels.indexOf(key);
    this.xLabels.splice(i, 1, newLabels);
    this.xLabels = this.xLabels.flat();

    this.initXAxis();
    this.plot();
  }

  /**
  *
  */
  plotXAxis(angle){
    let self = this;
    /* remove previous axis components */
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#bottom-axis').remove();

    let g = canvas.append('g')
      .attr('id', 'bottom-axis')
      .attr('transform', 'translate(0,'+(this.height-margin.bottom)+')')
      .call(this.xAxis)
      .selectAll("text")
        .attr("y", 0)
        .attr("x", 9)
        .attr("dy", ".35em")
        .attr("transform", "rotate("+angle+")")
        .style("text-anchor", "start")
        .on('click', function(d){ self.expandXLabels(d, 'category', 'organ'); })
    ;
  }

  /**
   *
   */
  plot(){
    this.plotXAxis(45);
    /* remove previous axis components */
    // let canvas = d3.select('svg#canvas > g#graph');
    // canvas.selectAll('#bottom-axis').remove();
    //
    // let g = canvas.append('g')
    //   .attr('id', 'bottom-axis')
    //   .attr('transform', 'translate(0,'+(this.height-margin.bottom)+')')
    //   .call(this.xAxis)
    // ;
  }



}
