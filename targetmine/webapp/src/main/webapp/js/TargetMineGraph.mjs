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
   *
   * @param {string} name The title for the graph
   * @param {int} width The width of the viewbox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, width, height){
    /* the title of the graph */
    this._name = name;
    /* dimensions of the canvas and margins for display */
    this._width = width;
    this._height = height;
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
    let graph = d3.select('svg#canvas').append('g')
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
   * Indiviudual values are required to construct the categorical scales used
   * for color and shape, later used to map distinctive points at the time of
   * display.
   */
  initXLabels(column){
    let self = this;
    this._xLabels = this._data.reduce(function(prev, current){
      if( ! prev.includes(current[self._x]) )
        prev.push( current[self._x] );
      return prev;
    }, ['']);
    this._xLabels = this._xLabels.concat(['']);
  }

  /**
   * Initialize the X axis of the graph
   * The X axis will always be ordinal, so in order to generate the
   * corresponding list of ticks in the axis, we use the list of this._xLabels
   * currently available.
   */
  initXAxis(){
    /* The bottom axis will map to a series of discrete pixel values, evenly
     * distributed along the drawing area, we use startx and dx to define the
     * starting and step that define the ticks in the axis */
    let startx = this._margin.left;
    let dx = (this._width-this._margin.left-this._margin.right)/(this._xLabels.length-1);
    /* define an ordinal scale */
    let scale = d3.scaleOrdinal()
      .domain(this._xLabels)
      .range(this._xLabels.map(function(k,i){ return startx+i*dx; }))
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
    let self = this;
    /* find min and max */
    let min = +Infinity;
    let max = -Infinity;
    this._data.forEach( x => {
      max = Math.max( +x[self._y], max );
      min = Math.min( +x[self._y], min );
    });
    let scale = undefined;
    /* define a linear scale */
    /* and change it to logarithmic if required */
    if( logScale == true ){
      scale = d3.scaleLog()
        .domain([min, max])
        .range( [this._height-this._margin.bottom, this._margin.top] )
        .nice()
        ;
    }
    else{
      scale = d3.scaleLinear()
        .domain([0, max])
        .range( [this._height-this._margin.bottom, this._margin.top] )
        .nice()
      ;
    }
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
    let self = this;
    // include default color and shape for the graph
    this._colors = [ {'key': 'Default', 'value': '#C0C0C0' } ];
    this._shapes = [ {'key': 'Default', 'value': 'Circle' } ];

    // and if specified, add also color for each X-axis category
    if( addXLabels == true ){
      /* exclude the tips of the axis that do not represent any value */
      let labels = this._xLabels.slice(1, this._xLabels.length-1);
      labels.map( (label,i) => {
        this._colors.push( { 'key': label, 'value': d3.schemeCategory10[i%d3.schemeCategory10.length] });
      });
    }
  }

  /**
   * Add color information to all points in the dataset
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
      // console.log(data, keys, values);
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
   * Re-draw the X-axis of the Graph
   *
   * @param {int} labelAngle Rotation angle that can be used for the label ticks
   * @param {function} clickCallback
   * @param {function} contextCallback
   */
  plotXAxis(labelAngle=0){
    /* remove previous axis components */
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#bottom-axis').remove();

    /* add the axis to the display */
    let g = canvas.append('g')
      .attr('id', 'bottom-axis')
      .attr('transform', 'translate(0,'+(this._height-this._margin.bottom)+')')
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
    if( this._x != undefined ){
      d3.selectAll('svg#canvas > text#bottom-axis-label').remove();
      let label = canvas.append('text')
        .attr('id', 'bottom-axis-label')
        .attr('transform', 'translate('+this._width/2+','+(this._height-this._margin.bottom/3)+')')
        .style('text-anchor', 'middle')
        .text(this._x)
      ;
    }
  }

  /**
   *
   */
  plotYAxis(){
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#left-axis').remove();
    canvas.append("g")
      .attr('id', 'left-axis')
      .attr('transform', 'translate('+this._margin.left+',0)')
      .call(this._yAxis)
    ;

    d3.selectAll('svg#canvas > text#left-axis-label').remove();
    let label = canvas.append('text')
      .attr('id', 'left-axis-label')
      .attr('transform', 'rotate(-90)')
      .attr('y', -this._margin.left/3)
      .attr('x', -this._height/2)
      .attr('dy', '1em')
      .style('text-anchor', 'middle')
      .text('Concentration (nM)')
  }
}
