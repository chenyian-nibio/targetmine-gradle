'use strict';

/**
 * @class TargetMineGraph
 * @classdesc Generic description of graph elements required to display a graph
 * as part of a report page in TargetMine
 * @author Rodolfo Allendes
 * @version 0.1
 */
export class TargetMineGraph {

  /**
   * Initialize a new instance of TargetMineGraph
   * This is a general container for any graph definition added to TargetMine,
   * thus, information such as the type of graph and general aspects as the size
   * of it should be stored
   *
   * @param {string} type The type of graph being displayed
   * @param {string} name The title for the graph
   * @param {int} width The width of the viewbox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(type, name, width, height){
    /* the type of graph */
    this._type = type;
    /* the title of the graph */
    this._name = name;
    /* the dimensions of the canvas are defined in user coordinates and NOT
     * pixel values */
    this._width = width;
    this._height = height;
    /* margins are defined as blank space, in user coordinates, destined to
     * contain extra annotations to the graph */
    this._margin = {top: 40, right: 40, bottom: 40, left: 40};

    /* data used for the generation of the graph */
    this._data = undefined;

    /* used for the display of violin plots associated to the data points */
    this._bins = undefined;

    /* d3 axis used in the graph */
    this._xAxis = undefined;
    this._yAxis = undefined;

    /* variables (titles) used for the axis in the label */
    this._x = undefined;
    this._y = undefined;

    // the labels used in the x axis of the graph
    this._xLabels = undefined;

    /* the list of colors and shapes used to display data points */
    this._colors = undefined;
    this._shapes = undefined;
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
    this._data = d3.tsvParse(data, d3.autoType);
  }

  /**
   * Initialize Visualization Handling elements
   * A panel with controls is added to the right side of each graph. These
   * controls are grouped in tables, each of which has a Title and an optional
   * 'Add' button (that allows the user to add elements during execution).
   *
   * @param {Object} columnElements The list of tables that will be added to the
   * right column of the container
   */
  initDOM(columnElements){
    let container = d3.select('.targetmineGraphDisplayer');
    // Left Column of the Visualization (main display)
    container.append('svg')
      .attr('id', 'canvas_'+this._type)
      .attr('class', 'targetmineGraphSVG')
      .attr('viewBox', '0 0 '+this._width+' '+this._height)
      .append('g')
        .attr('id', 'graph')
    ;
    // Right Column, reserved for visualization controls
    container.append('div')
      .attr('class', 'rightColumn')
    ;

    // Add individual right column elements
    d3.select('.rightColumn').selectAll('div')
      .data(columnElements)
      .enter().append('div')
      .attr('id', d => d.name+'-div')
      .each(function(d) {
        d3.select(this).append('br');
        d3.select(this).append('label')
          .attr('for', d.name+'-table')
          .text(d.text)
        ;
        d3.select(this).append('table')
          .attr('id', d.name+'-table')
          .append('tbody')
          ;
        if( d.button ){
          d3.select(this).append('button')
            .attr('id', d.name+'-add' )
            .text('Add')
          ;
        }
      })
      .exit().remove()
    ;
  }

  /**
   * Initialize the labels of the X axis of the Graph.
   * Initialize a list of the values used as ordinal categories across the X
   * axis of the graph.
   *
   * @param {string} column The data column from the data object that is used to
   * construct the list of labels for the X axis. If no value is provided, the
   * default _x value is used.
   */
  initXLabels(column = this._x){
    this._xLabels = [];
    this._data.forEach( (item,i) => {
      if( !this._xLabels.includes(item[column]) )
        this._xLabels.push(item[column]);
    }, this);
  }

  /**
   * Initialize the X axis of the graph
   * The X axis will always be ordinal, so in order to generate the corresponding
   * list of ticks in the axis, we use the list of this._xLabels currently available.
   */
  initXAxis(){
    /* The bottom axis will map to a series of discrete pixel values, evenly
     * distributed along the drawing area, for this, we use the scaleBand scale
     * provided by D3 */
    let scale = d3.scaleBand()
      .domain(this._xLabels)
      .range( [0, this._width-this._margin.left-this._margin.right] )
      .padding(0.05)
    ;
    /* create the corresponding axis */
    this._xAxis = d3.axisBottom(scale);
  }

  /**
   * Initialize the Y axis of the graph
   * The Y axis will always be numerical, but the scale can be switched between
   * linear and logarithmic.
   * The axis will always be generated based on the minimum and maximum values
   * found in the dataset for the corresponding this._y variable.
   *
   * @param {boolean} logScale Flag that indicates if the axis should be generated
   * using a logarithmic scale or not. Default value: false.
   */
  initYAxis(logScale=false){
    /* find the minimum and maximum values for _y */
    let min = +Infinity;
    let max = -Infinity;
    this._data.forEach( x => {
      max = Math.max( +x[this._y], max );
      min = Math.min( +x[this._y], min );
    }, this);
    /* initialize the correct type of scale */
    let scale = logScale == true ? d3.scaleLog().domain([min,max]) : d3.scaleLinear().domain([0,max]);
    scale.range( [this._height-this._margin.bottom, this._margin.top] )
    scale.nice()
    /* create the corresponding axis */
    this._yAxis = d3.axisLeft(scale);
    this._yAxis.ticks(10, '~g');
  }

  /**
   * Initialize list of colors and Shapes
   * Different colors and shapes can be used to differentiate the categories of
   * data points according to their X-axis dimension. Here, we generate the list
   * of all colors and shapes according to the currently displayed categories.
   *
   * @param {boolean} addXLabels Flag that indicates if individual shapes and
   * colors should be assiged for each individual category available in the
   * X axis of the graph. Default value: true.
   */
  initColorsAndShapes(addXLabels=true){
    /* init the default color and shape of data elements */
    this._colors = { 'Default': '#C0C0C0' };
    this._shapes = { 'Default': 'Circle' };
    /* upon request, add individual color for each _xLabel */
    if( addXLabels == true ){
      this._xLabels.map( (label, i) => {
        this._colors[label] = d3.schemeCategory10[i%d3.schemeCategory10.length];
      });
    }
  }

  /**
   * Initialize the graph's data distribution bins
   * Histogram bins are used for the display of violin of the data. A single
   * violin plot is associated to each tick along the xAxis of the graph.
   * tic
   *
   * @param {number} nBins The number of bins to use. Default value 10
   */
  initHistogramBins(nBins=10){
    let self = this;
    /* function used to define the number of bins and the bounds for each of
     * them */
    let histogram = d3.bin()
      .domain(self._yAxis.scale().domain())
      .thresholds(self._yAxis.scale().ticks(nBins))
      .value(d => d)
      ;
    /* actually bin the data points */
    this._bins = d3.rollup(
      self._data,
      d => {
        let input = d.map( g => g['Activity Concentration']);
        let bins = histogram(input);
        return bins;
      },
      d => d['Activity Type']
    );
  }

  /**
   * Assing color to data points.
   * The list of current colors is stored in the _colors object. Each item in
   * the data-set has its VALUES matched to the KEYS of the _colors list, in
   * order to find a match.
   */
  assignColors(){
    /* extract the list of values with a color code (keys from the _colors list) */
    let colorkeys = Object.keys(this._colors);
    /* for each data point, check if any of its values is part of this list, and
     * assign a color accordingly. Default color is assigned otherwise. */
    this._data.forEach( (item,i) => {
      for(let j=colorkeys.length-1; j>0; --j){
        if( Object.values(item).includes(colorkeys[j]) ){
          item.color = this._colors[colorkeys[j]];
          return;
        }
      }
      item.color = this._colors.Default;
    }, this);
  }

  /**
   * Assign shape to data points.
   * The list of current shapes is stored in the _shapes object. Each item in
   * the data-set has its VALUES matched to the KEYS of the _shapes list, in
   * order to find a match.
   */
  assignShapes(){
    /* extract the list of the values with a shape code (keys from _shapes) */
    let shapekeys = Object.keys(this._shapes);
    /* for each data point, check if any of its values is part of the list, and
     * assing a shape accordingly. Default shape is assigned otherwise */
    this._data.forEach( (item,i) => {
      for( let j=shapekeys.length-1; j>0; --j){
        if( Object.values(item).includes(shapekeys[j]) ){
          item.shape = this._shapes[shapekeys[j]];
          return;
        }
      }
      item.shape = this._shapes.Default;
    }, this);
  }

  /**
   * Initialize table components
   *
   * @param {string} tableid The id of the table whose elements we are going to
   * modify
   * @param {string} type A string that describes the visual elemets for which
   * the table is used
   * @param {array} rowElements List of identifiers for each row of the table
   * @param {object} rowComponents List of elements to be added to each row
   */
  initTableRows(tableid, type, rowElements, rowComponents){
    d3.select(tableid+' > tbody').selectAll('div').remove();
    d3.select(tableid+' > tbody').selectAll('div')
      .data(rowElements)
      .enter().append('div')
        .attr('class', 'flex-row')
        .attr('id', d => type+'-'+d)
        .each(function(d,i){
          rowComponents.forEach(g => {
            let ele = d3.select(this).append(g.type);
            g.attr.forEach( h => {
              ele.attr(h[0], h[1]);
            });
          });
        })
  }

  /**
   * Set the position (in display coordinates) of each point in the data
   *
   * @param {boolean} jitter Should the position of the point be randomly
   * jittered along the X axis or not.
   */
  setPointPositions(jitter=false){
    let X = this._xAxis.scale();
    let dx = X.bandwidth()/2;
    let Y = this._yAxis.scale();
    this._data.forEach(d => {
      d.x = X(d[this._x])+dx;
      if( jitter ) dx -= (dx/2)*Math.random();
      d.y = Y(d[this._y]);
    },this);
  }

  /**
   *
   */
  plotViolins(){
    /* add violin strips if requested */
    let canvas = d3.select('svg#canvas_'+this._type+' > g#graph');
    let X = this._xAxis.scale();
    let Y = this._yAxis.scale();
    canvas.selectAll("#violins").remove();

    // What is the biggest number of value in a bin? We need it cause this value
    // will have a width of 100% of the bandwidth.
    let maxNum = 0
    this._bins.forEach(d => {
      let lengths = d.map(g => g.length);
      let longest = d3.max(lengths);
      maxNum = longest > maxNum ? longest : maxNum;
    });
    let xNum = d3.scaleLinear()
      .range([0, X.bandwidth()])
      .domain([-maxNum, maxNum])

    canvas.append('g')
      .attr('id', 'violins')
      .attr('transform', 'translate('+this._margin.left+', 0)')
    ;

    let vls = d3.select('#violins').selectAll('g')
      .data(this._bins)
    let violin = vls.enter().append('g')        // So now we are working group per group
      .attr('class', 'violin')
      .attr("transform", d => "translate(" + (X(d[0])+(X.bandwidth()/10)) +" ,0)")
        .append("path")
          .datum(d => d[1]) //extract only the bins
          .attr("class", "violin")
          .attr("d", d3.area()
            .x0( xNum(0) )
            .x1(function(d){ return(xNum(d.length)) } )
            .y(function(d){ return(Y(d.x0)) } )
            .curve(d3.curveCatmullRom)    // This makes the line smoother to give the violin appearance. Try d3.curveStep to see the difference
          )
    // }
  }

  /**
   * Add the X axis to the graph
   *
   * @param {int} labelAngle If a value is given, the labels displayed for every
   * tick in the axis will be rotated clock-wise accordingly
   * @param {boolean} showTitle Display a title for the axis
   */
  plotXAxis(labelAngle=0, showTitle=false){
    /* remove previous axis components */
    let canvas = d3.select('svg#canvas_'+this._type+' > g#graph');
    canvas.selectAll('#bottom-axis').remove();

    /* add the axis to the display, making sure it is positioned only within the
     * area of the graph allocated for that */
    let g = canvas.append('g')
      .attr('id', 'bottom-axis')
      .attr('transform', 'translate('+this._margin.left+', '+(this._height-this._margin.bottom)+')')
      .call(this._xAxis)
    ;

    /* rotate the labels of the axis - if the rotation angle is != 0 */
    if( labelAngle != 0 ){
      let labels = g.selectAll('text')
        .attr("y", 0)
        .attr("x", 9)
        .attr("dy", ".35em")
        .attr("transform", "rotate("+labelAngle+")")
        .style("text-anchor", "start")
    }

    /* add title to the axis, if defined
     * The title is always positioned anchored to the mid-point of the bottom
     * margin */
    if( showTitle ){
      d3.selectAll('svg#canvas_'+this._type+' > text#bottom-axis-label').remove();
      let label = canvas.append('text')
        .attr('id', 'bottom-axis-label')
        .attr('transform', 'translate('+this._width/2+','+(this._height-this._margin.bottom/3)+')')
        .style('text-anchor', 'middle')
        .text(this._x)
      ;
    }
  }

  /**
   * Add DOM elements required for Y-axis display
   */
  plotYAxis(){
    let canvas = d3.select('svg#canvas_'+this._type+' > g#graph');
    canvas.selectAll('#left-axis').remove();
    canvas.append("g")
      .attr('id', 'left-axis')
      .attr('transform', 'translate('+this._margin.left+',0)')
      .call(this._yAxis)
    ;

    /* if defined, add a title to the axis */
    if( this._y !== undefined ){
      canvas.selectAll('text#left-axis-label').remove();
      let label = canvas.append('text')
        .attr('id', 'left-axis-label')
        .attr('transform', 'rotate(-90)')
        .attr('y', -this._margin.left/3)
        .attr('x', -this._height/2)
        .attr('dy', '1em')
        .style('text-anchor', 'middle')
        .text(this._y)
      ;
    }
  }
}
