'use strict';

import { TargetMineGraph } from "./TargetMineGraph.mjs";

/**
 * @class BioActivityGraph
 * @classdesc Used to display the bioactivity levels of a given compound in their
 * corresponding report page
 * @author Rodolfo Allendes
 * @version 1.0
 */
export class BioActivityGraph extends TargetMineGraph{

  /**
   * Initialize an instance of BioActivityGraph
   *
   * @param {string} name The title for the graph
   * @param {int} width The width of the viewBox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, width, height){
    /* initialize super class attributes */
    super(name, width, height);

    /* initial variables for X and Y axis */
    this._x = 'Activity Type';
    this._y = 'Activity Concentration';

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

    /* Set default value selection */
    let val = d3.select('#value-select')
      .property('value', 'undefined')

    /* Define the type of input: color input for color scale, or radio buttons
     * for shape selection */
    let inp = d3.selectAll('#modal-input > *').remove();

    if( type === 'color' ){
      let title = d3.select('#modal-title')
        .text('Select color to apply:')
      ;

      inp = d3.select('#modal-input')
        .append('input')
          .property('type', 'color')
          .property('value', '#000000')
      ;
    }
    else{
      let title = d3.select('#modal-title')
        .text('Select shape to apply:')
      ;

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
      .attr('value', 'undefined')
      .text('Select...')
      .property('selected', true)
    ;
  }

  /**
   *
   */
  initColorTable(){
    /* Generate an array of data elements that we can use to generate a 'table'
     * of elements using D3 */
    // let data = Object.entries(this._colors).map( function(d){
    //   return { 'key': d[0], 'value': d[1] };
    // });

    super.initTable('color', this._colors);
    /* display the colors in the color cell */
    // let cell = row.append('div')
    //   .attr('class', 'flex-cell')
    //   ;
    // if( type === 'color' )
    //   cell.style('background-color', function(d){ return d.value; });
    //

    /* have to add the function to the close span */
    // .on('click', function(){
    //   if( this.dataset.key === 'Default' ) return;
    //   self._colors.splice(this.dataset.index, 1);
    //   self.assignColor();
    //   self.initTable('color', self._colors);

    //   .on('click', function() {
    //     if (this.dataset.key === 'Default') return;
    //     self._removeHighlight(this.dataset.type, this.dataset.key, this.dataset.value);
    //     self._updateTable(this.dataset.type);
    //   })
    // ;
  }

  /**
   *
   */
  initShapeTable(){
    super.initTable('color', this._colors);
    /* display the colors in the color cell */
    //   cell.append('svg')
    //     .attr('viewBox', '-5 -5 10 10')
    //     .style('height', 'inherit')
    //     .append('path')
    //       .attr('fill', 'black')
    //       .attr('d', function(d){
    //         let s = ['Circle','Cross','Diamond','Square','Star','Triangle','Wye']
    //         let symbol = d3.symbol()
    //           .size(10)
    //           .type(d3.symbols[s.indexOf(d.value)])
    //         ;
    //         return symbol();
    //       })
    //

    /* have to add the function to the close span */
    // row.append('span')
    // .on('click', function(){
    //   if( this.dataset.key === 'Default' ) return;
    //   self._colors.splice(this.dataset.index, 1);
    //   self.assignColor();
    //   self.initTable('color', self._colors);
    //   .on('click', function() {
    //     if (this.dataset.key === 'Default') return;
    //     self._removeHighlight(this.dataset.type, this.dataset.key, this.dataset.value);
    //     self._updateTable(this.dataset.type);
    //   })
    // ;

  }

  /**
   *
   * @param {string} type The type of highlight (color or shape) we are trying
   * to remove
   * @param {string} key A combination of column and value for the points in the
   * dataset, whose highlight we are trying to remove
   * @param {string} value The value associated to the key that we are trying to
   * remove
   */
  _removeHighlight(type, key, value){
    let col = key.split('-')[0];
    let val = key.split('-')[1];
    /* return the property (color or shape) to its default values */
    let upd = type === 'color' ? '#C0C0C0' : 'Circle';
    this._data.forEach(function(d){
      /* we only remove the hightlight of the points that match both the column/
       * value combination, and that have been highlighted with the color or
       * shape we are removing */
      if( d[col] === val && d[type] === value )
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
   * Initialize a graph display
   * Whenever a new data file is loaded, a graph display is initialized using a
   * default Y-axis.
   * Notice that the first step required to display a graph is to clear whatever
   * content the display area might have already have.
   */
  plot(){
    let self = this;
    this.plotXAxis();
    this.plotYAxis();

    /* Generate an array of data points positions and colors based on the scale
     * defined for each axis */
    let xscale = this._xAxis.scale();
    let yscale = this._yAxis.scale();
    let points = this._data.reduce(function(prev, current){
      /* filter out the points that are hidden from the visualization */
      prev.push(
        {
          x: xscale(current[self._x]),
          y: yscale(current[self._y]),
          'color': current['color'],
          'shape': current['shape'],
          'organism': current['Organism name'],
          'symbol': current['Gene Symbol'],
          'label': current[self._yPlot],
        }
      );
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
    ;
    let tooltip = point.append('svg:title')
      .text(function(d){ return 'Organism: '+d.organism+'\nGene: '+d.symbol+'\nConcentation: '+d.label+'nM'; })
    ;
  }
}
