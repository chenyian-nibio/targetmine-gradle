'use strict';

//var jQuery = jQuery.noConflict();
// margins from the border of the canvas that wont be used for drawing
var margin = {top: 40, right: 40, bottom: 40, left: 40};
// available drawing space for the canvas
var width = 400;// - margin.left - margin.right;
var height = 400;// - margin.top - margin.bottom;

/**
 * @class CompoundGraph
 * @classdesc
 * @author
 * @version
 *
 */
class BioActivityGraph{

  /**
   *
   */
  constructor(name){
    this._name = name; // graph's title
    this._data = undefined; // data points to plot
    this._xPlot = 'Activity Type'; // default X axis for the graph
    this._yPlot = 'Activity Concentration'; // default Y axis for the graph
    this._xAxis = undefined; // X scale
    this._yAxis = undefined; // Y scale

    // the list of colors used in the graph display
    this._colors = { 'Default': 'black' };
    // the list of shapes used in the graph display
    this._shapes = { 'Default': 'Circle' };

    /* init listeners for interface components */
    let self = this;
    d3.select('#color-add')
      .on('click', function(){ self._displayModal(); })
    ;
    d3.select('#shape-add')
      .on('click', function(){ self._displayModal(); })
    ;
    d3.select('#modal-ok')
      .on('click', function(){ self._okModal(); })
    ;
    d3.select('#modal-cancel')
      .on('click', function(){ self._cancelModal(); })
    ;

    /* Add a base component to the SVG element. All posterior drawing will be
     * done nested to this basic component */
    let graph = d3.select('svg#canvas').append('g')
      .attr('id', 'graph')
    ;

    /* only to make visible the different areas within the graph
    * TO BE REMOVED */
    // graph.append('rect')
    //   .style('fill', 'lightblue')
    //   .style('width', width)
    //   .style('height', height)
    // ;
    // graph.append('rect')
    //   .attr('id', 'points-area')
    //   .attr('transform', 'translate('+margin.left+','+margin.top+')')
    //   .style('fill', 'lightgreen')
    //   .style('width', width-margin.left-margin.top)
    //   .style('height', height-margin.top-margin.bottom)
    // ;
  }

  /**
   * Set individual columns
   */
  _initColumns(){
    let self = this;
    this._columns = this._data.columns.reduce(function(prev, curr){
      if( typeof(self._data[0][curr]) === 'string' )
        prev.push( curr );
      return prev;
    }, [])
  }

  /**
   * Find individual values for a given column.
   * Indiviudual values are required to construct the categorical scales used
   * for color and shape, later used to map distinctive points at the time of
   * display.
   *
   * @param {string} column The column in the data for which we are trying to
   * find all individual values
   * @return An array with all the distinctive values found for a given column
   * in the data - Used to define the domain of a categorical scale.
   */
  _individualValues(column){
    let values = this._data.reduce(function(prev, current){
      if( ! prev.includes(current[column]) )
        prev.push( current[column] );
      return prev;
    }, []);
    return values;
  }

  /**
   *
   */
  _displayModal(){
    let type = d3.event.target.id.split('-')[0];
    let self = this;
    /* Show the modal */
    let content = d3.select('#modal')
      .style('display', 'flex')
      .attr('data-type', type)
    ;

    /* Update options for data columns */
    this._updateSelectOptions('#column-select', this._columns);
    let cols = d3.select('#column-select')
    /* dynamically update values options depending on the selected column */
    cols.on('change', function(){
      let values = self._individualValues(d3.event.target.value);
      self._updateSelectOptions('#value-select', values);
    });

    /* Define the type of input: color input for color scale, or radio buttons
     * for shape selection */
    let inp = d3.selectAll('#modal-input > *').remove();

    if( type === 'color' ){
      inp = d3.select('#modal-input')
        .append('input')
          .property('type', 'color')
          .property('value', '#000000')
      ;
    }
    else{
      let opts = d3.select('#modal-input').selectAll('label')
        .data(['Circle','Cross','Diamond','Square','Star','Triangle','Wye'])
      ;
      let opt = opts.enter().append('label')
        .text(function(d){ return d; })
      ;

      d3.select('#modal-input').selectAll('label')
        .insert('input')
          .attr('id', function(d){ return 'symbol-'+d; })
          .attr('value', function(d){ return d; })
          .attr('type', 'radio')
          .attr('name', 'shape')
      ;

      d3.select('#modal-input').selectAll('label')
        .append('br')
      ;

      d3.select('#symbol-Circle').property('checked', true);
    }
  }

  /**
   *
   */
  _okModal(){

    let modal = d3.select('#modal')
      .style('display', 'none')
    ;
    let type = modal.attr('data-type');

    let col = d3.select('#column-select').property('value');
    let val = d3.select('#value-select').property('value');

    /* are the changes to be made to color or shape? */
    let upd = type === 'color' ?
      d3.select('#modal-input > input').property('value') :
      d3.select('input[name="shape"]:checked').property('value')
    ;

    this._data.forEach(function(d){
      if( d[col] === val )
      d[type] = upd;
    });

    /* update the corresponding table */
    if( type === 'color')
      this._colors[col+'-'+val] = upd;
    else
      this._shapes[col+'-'+val] = upd;
    this._updateTable(type);
    /* redraw the graph */
    this.plot(this._xPlot, this._yPlot);
  }

  /**
   *
   */
  _cancelModal(){
    d3.select('#modal')
      .style('display', 'none')
    ;
  }

  /**
   * Update the color scale used in the display of data.
   * Since the data to be visualized is multi-dimensional in nature, any of the
   * fields that are present can be used to define a categorical color scale.
   * Currently, a fixed amount of 10 base colours are sequentially assigned to
   * each individual value found for the chosen category in the dataset. If more
   * than 10 values are found, colors are re-used.
   *
   * @param {string} id
   * @param {string} column
   * @param {string} value
   */
  _updateColorScale(id, column, value){
  //   let self = this;
  //   /* retrieve the option based on which to define the color scale */
  //   let key = jQuery(id).val();
  //
  //   /* define the set of individual values that the current key takes, so that
  //    * we can assign an specific color to each of them */
  //   let values = this._individualValues(key);
  //   this._colors = {};
  //   values.forEach(function(k, i){
  //     this._colors[k] = 'black'; //d3.schemeCategory10[i%10];
  //   }, this );
  //
  //   /* Modify the color field according to the new list of values for each data
  //    * point */
  //   this._data.forEach(function (item){
  //     item['color'] = this._colors[item[key]];
  //   },this);
  //
  //   /* create the actual table of values  */
  //   this._updateTable('color');
  //
  //   /* Add a listener to show/hide elements based in color */
  //   // jQuery(document).on('click', '.color-checkbox', function(evt){
  //   //   self.plot(self._xPlot, self._yPlot);
  //   // });
  }

  // _updateShapeScale(id, column, value){
  //   let self = this;
  //   /* retrieve the option based on which to define the color scale */
  //   let key = jQuery(id).val();
  //
  //   /* define the set of individual values that the current key takes, so that
  //    * we can assign an specific color to each of them */
  //   let values = this._individualValues(key);
  //   this._shapes = {};
  //   values.forEach(function(k, i){
  //     this._shapes[k] = d3.symbols[i%7];
  //   }, this );
  //
  //   /* Modify the color field according to the new list of values for each data
  //    * point */
  //   this._data.forEach(function (item){
  //     item['shape'] = this._shapes[item[key]];
  //   },this);
  //
  //   /* update the table */
  //   this._updateTable('shape');
  //
  //   /* Add a listener to show/hide elements based in color */
  //   jQuery(document).on('click', '.shape-checkbox', function(evt){
  //     self.plot(self._xPlot, self._yPlot);
  //   });
  // }

  /**
   * Update the options available for a given Select DOM element.
   * Given the id of a select element, it updates the options available based on
   * the list of values provided
   *
   * @param {string} id The id of the select component that should be updated
   * @param {string} values The list of values to use for the definition of
   * options
   */
  _updateSelectOptions(id, values){

    /* delete all previous options */
    let sel = d3.select(id)
      .selectAll('option').remove();

    /* add new options based on the provided values */
    sel = d3.select(id).selectAll('option')
      .data(values);

    let opt = sel.enter().append('option')
      .attr('value', function(d){ return d; })
      .text(function(d){ return d; })
    ;
    /* add a first 'Select...' option */
    d3.select(id).append('option')
      .lower()
      .attr('value', undefined)
      .text('Select...')
      .property('selected', true)
    ;
  }

  /**
  *
  */
  _updateTable(type){
    /* Generate an array of data elements that we can use to generate a 'table'
     * of elements using D3 */
    let self = this;
    let values = (type === 'color') ? Object.keys(this._colors) : Object.keys(this._shapes);
    let data = values.reduce( function(prev, current){
      prev.push(
        {
          'key': current,
          'value': (type === 'color') ? self._colors[current] : self._shapes[current],
        }
      )
      return prev;
    }, []);

    /* Before we can (re)build the 'table' we need to clean any previous elements */
    let table = d3.select('#'+type+'-table')
      .selectAll('div').remove();

    /* We use D3 to build all the rows in the table at the same time */
    table = d3.select('#'+type+'-table').selectAll('div')
      .data(data);

    /* create each row */
    let row = table.enter().append('div')
      .attr('class', 'flex-row')
      ;

    /* second cell: a simple color background or an svg element with a symbol */
    let cell = row.append('div')
      .attr('class', 'flex-cell')
      ;
    if( type === 'color' )
      cell.style('background-color', function(d){ return d.value; });

    else{ // shape
      cell.append('svg')
        .attr('viewBox', '-5 -5 10 10')
        .style('height', 'inherit')
        .append('path')
          .attr('fill', 'black')
          .attr('d', function(d){
            let s = ['Circle','Cross','Diamond','Square','Star','Triangle','Wye']
            let symbol = d3.symbol()
              .size(10)
              .type(d3.symbols[s.indexOf(d.value)])
            ;
            return symbol();
          })
      ;
    }

    /* third cell: label */
    row.append('div')
      .attr('class', 'flex-cell label')
      .attr('data-key', function(d){ return d.key; })
      .text( function(d){ return d.key; } )
      ;

    row.append('span')
      .attr('class', 'flex-cell small-close')
      .attr('data-type', type)
      .attr('data-key', function(d){ return d.key; })
      .html('&times;')
      .on('click', function() {
        if (this.dataset.key === 'Default') return;
        self._removeHighlight(this.dataset.type, this.dataset.key);
        self._updateTable(this.dataset.type);
      })
    ;
  }

  /**
   *
   */
  _removeHighlight(type, key){
    let col = key.split('-')[0];
    let val = key.split('-')[1];
    let upd = type === 'color' ? '#000000' : 'Circle';
    // console.log(this);
    this._data.forEach(function(d){
      if( d[col] === val )
        d[type] = upd;
    });
    /* remove from table */
    if( type === 'color' )
      delete this._colors[key];
    else
      delete this._shapes[key];

    this.plot(this._xPlot, this._yPlot);
  }


  /**
   * Dynamically generate the X-Axis used in the graph
   * Compounds can have multiple types of bio-activity measurements registered,
   * thus, whenever a new graph is generated, the axis used for the measurements
   * needs to be generated. The key should always define a categorical scale.
   *
   * @param {string} key The key, on of the available fields in the dataset,
   * that will be used to define the axis. Typically, this will be 'Activity Type'
   */
  _updateXAxis(key){
    /* retrieve the amount of ticks required for the axis */
    let values = [''].concat(this._individualValues(key)).concat(['']);
    /* The bottom axis will map to a series of discrete pixel values, evenly
     * distributed along the drawing area, we use startx and dx to define the
     * starting and step that define the ticks in the axis */
    let startx = margin.left;
    let dx = (width-margin.left-margin.right)/(values.length-1);
    /* define an ordinal scale */
    let scale = d3.scaleOrdinal()
      .domain(values)
      .range(values.map(function(k,i){ return startx+i*dx; }))
      ;
    /* create the corresponding axis */
    this._xAxis = d3.axisBottom(scale);

    /* remove previous axis components */
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#bottom-axis').remove();

    let g = canvas.append('g')
      .attr('id', 'bottom-axis')
      .attr('transform', 'translate(0,'+(height-margin.bottom)+')')
      .call(this._xAxis)
    ;
  }

  /**
   * Update the scale and redraw the left-side axis.
   * Different scales can be used for the display of the data. The left-side
   * axis in particular must correspond to a numerical value, and the scale
   * generated is logarithmic.
   *
   * @param {string} key The key value, from the one availables in the dataset,
   * used to construct the scale.
   */
  _updateYAxis(key){
    /* we need to define min and maximum values for the scale */
    let max = this._data.reduce(function(prev, current){
      return Math.max(+current[key], prev);
    }, -Infinity);
    let min = this._data.reduce(function(prev, current){
      return Math.min(+current[key], prev);
    }, +Infinity);
    /* define a logarithmic scale */
    let scale = d3.scaleLog()
      .domain([min,  max])
      .range([height-margin.bottom,margin.top])
      .nice() // make the scale have rounded endpoints based on min and max
    ;
    /* create the corresponding axis */
    this._yAxis = d3.axisLeft(scale);
    this._yAxis.ticks(10, '~g');
    /* remove previous axis components */
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#left-axis').remove();
    canvas.append("g")
      .attr('id', 'left-axis')
      .attr('transform', 'translate('+margin.left+',0)')
      .call(this._yAxis)
    ;
  }

  /**
   * Initialize the graph data
   * Process the data string provided from Targetmine into a data structure that
   * can be used for the visualization
   *
   * @param {string} data A string representation of the data included in the
   * graph
   */
  loadData(data){
    /* the data provided by Targetmine's Api is the string representation of a
     * Java ArrayList. To convert this to an array of Javascript objects we
     * first need to dispose the initial and trailing '[' ']' characters */
    data = data.substring(1, data.length-1);
    /* second, replace the ',' chars for line separators  */
    data = data.replace(/,/g, '\n');
    /* third, we parse the resulting array of rows into an array of objects by
     * using tab separators, and the first row as keys for the mapping */
    this._data = d3.tsvParse(data, d3.autoType);
    /* add a new field to data points to store color information */
    this._data.forEach(function(item){
      item.color = '#000000';
      item.shape = 'Circle';
    });
    /* init an array of columns */
    this._initColumns();
    /* log the results of the loading proces */
    console.log('Loaded Data: ', this._data);
    /* update the axis of the graph */
    this._updateXAxis(this._xPlot);
    this._updateYAxis(this._yPlot);
    /* update the colors used for the data points in the graph */
    this._updateTable('color');
    /* update the shapes used for the data points in the graph */
    this._updateTable('shape');
    /* plot the data points */
    this.plot(this._xPlot, this._yPlot);
  }

  /**
   * Initialize a graph display
   * Whenever a new data file is loaded, a graph display is initialized using a
   * default Y-axis.
   * Notice that the first step required to display a graph is to clear whatever
   * content the display area might have already have.
   * @param {string} X the name of the column used as default Y-axis for the
   * graph
   */
  plot(X, Y){
    let self = this;

    /* Generate an array of data points positions and colors based on the scale
     * defined for each axis */
    let xscale = this._xAxis.scale();
    let yscale = this._yAxis.scale();


    let points = this._data.reduce(function(prev, current){
      /* filter out the points that are hidden from the visualization */
      // if( cb.includes(current[colorMap]) && cb.includes(current[shapeMap]) ){
        prev.push(
          {
            x: xscale(current[X]),
            y: yscale(current[Y]),
            'color': current['color'],
            'shape': current['shape'],
            'label': current[self._yPlot],
          }
        );
      // }
      return prev;
    }, []);

    /* redraw the points, using the updated positions and colors */
    let canvas = d3.select('svg#canvas > g#graph');
    canvas.selectAll('#points').remove();

    canvas.append('g')
      .attr('id', 'points')
      ;

    /* for each data point, generate a group where we can add multiple svg
     * elements */
    let pts = d3.select('#points').selectAll('g')
      .data(points)
    let point = pts.enter().append('g')
      .attr('class', 'data-point')
      .append('path')
        .attr('transform', function(d){ return 'translate('+d.x+','+d.y+')'; })
        .attr('fill', function(d){return d.color;})
        .attr('d', function(d){
          let s = ['Circle','Cross','Diamond','Square','Star','Triangle','Wye']
          let symbol = d3.symbol()
            .size(50)
            .type(d3.symbols[s.indexOf(d.shape)])
          ;
          return symbol();
        })
      .append("svg:title")
        .text(function(d){ return d.label; })
    ;
  }
}
