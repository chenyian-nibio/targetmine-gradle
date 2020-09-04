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

    /* Add a base component to the SVG element. The drawing of each element in
     * the svg will be nested to this basic component */
    let graph = d3.select('svg#canvas_'+this._type).append('g')
      .attr('id', 'graph')
    ;
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
    console.log('yaxis',min,max);
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
    this._colors = [ {'key': 'Default', 'value': '#C0C0C0' } ];
    this._shapes = [ {'key': 'Default', 'value': 'Circle' } ];

    /* upon request, add individual color for each _xLabel */
    if( addXLabels == true ){
      this._xLabels.map( (label, i) => {
        this._colors.push( { 'key': label, 'value': d3.schemeCategory10[i%d3.schemeCategory10.length] });
      });
    }
  }

  /**
   * Assing color to data points
   * Each point in the dataset can be drawn using a specific color. The match
   * between property and color is stored in the _colors array. Here, we update
   * the display color of each point in the dataset, according to the current
   * color mapping
   */
  assignColors(){
    /* make a list of all the individual values that have an associated color */
    let keys = this._colors.reduce(function(prev, curr){
      prev.push(curr.key);
      return prev;
    },[]);
    /* for each data point, check if there is any color associated to its values
     * and use it for its display. In the case of multiple values having an
     * assigned color, the last one in the list is used */
    this._data.map(data =>{
      data.color = this._colors[0].value;
      let values = Object.values(data);
      for( let i=keys.length; i>0; --i ){
        if(values.includes(keys[i])){
          data.color = this._colors[i].value;
          return data;
        }
      }
      return data;
    });
  }

  /**
   * Add shape information to all points in the dataset
   * Each point in the dataset can be drawn using a specific shape. The match
   * between property and shape is stored in the _shapes array. Here, we update
   * the display shape of each point in the dataset, according to the current
   * shapes mapping
   */
  assignShapes(){
    /* get a list of the values associated to a specific shape for display */
    let keys = this._shapes.reduce(function(prev, curr){
      prev.push(curr.key);
      return prev;
    },[]);
    /* for each data point, check if there is any shape associated to its values
     * and use it for its display. In the case of multiple values having an
     * assigned shape, the last one in teh list is used */
    this._data.map(data=>{
      data.shape = this._shapes[0].value; // use default as first case
      let values = Object.values(data);
      for( let i=keys.length; i>0; --i ){
        if( values.includes(keys[i]) ){
          data.shape = this._shapes[i].value; // change if a match is found
          return data; // stop searching
        }
      }
      return data; // return default if nothing found
    });
  }

  /**
   * Initialize the DOM elements of a table
   *
   * @param {String} type The type of table that needs to be initialized.
   * @param {Array} data The array of objects used as data for the definition of
   * the values in the table
   */
  initTable(type, data){
    let self = this;
    /* remove previous table elements */
    let table = d3.select('#'+type+'-table')
      .selectAll('div').remove()
    ;
    table = d3.select('#'+type+'-table').selectAll('div')
      .data(data)
    ;
    /* create each row */
    let row = table.enter().append('div')
      .attr('class', 'flex-row')
      .attr('id', function(d){ return type+'-'+d.key; })
      ;
    /* first cell: a simple color background or an svg element with a symbol */
    let cell = row.append('div')
      .attr('class', 'flex-cell display')
      ;
    /* second cell: label */
    row.append('div')
      .attr('class', 'flex-cell label')
      .text( function(d){ return d.key; } )
      ;
    /* third cell */
    row.append('span')
      .attr('class', 'flex-cell small-close')
      .attr('data-index', function(d,i){ return i; })
      .html('&times;')
    ;
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
      d3.selectAll('svg#canvas_'+this._type+' > text#left-axis-label').remove();
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
