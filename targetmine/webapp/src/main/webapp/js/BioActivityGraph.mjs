'use strict';

import { TargetMineGraph } from "./TargetMineGraph.mjs";

/**
 * @class BioActivityGraph
 * @classdesc Used to display the bioactivity levels of a given compound in their
 * corresponding report page
 * @author Rodolfo Allendes
 * @version 1.1 Adapted to use a scaleBand
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
    super('bioActivity', name, width, height);

    /* initial variables for X and Y axis */
    this._x = 'Activity Type';
    // this._y = 'Activity Concentration';
    this._y = 'Activity Concentration';
  }

  /**
   * Init modal components and its listeners
   */
  initModal(){
    let self = this;
    /* filter only categories within the data values */
    let options = this._data.columns.reduce(function(prev, curr){
      if( typeof(self._data[0][curr]) === 'string' ) prev.push(curr);
      return prev;
    }, []);
    /* add options based on the previous filtering */
    let opts = d3.select('#column-select').selectAll('option')
      .data(options)
    opts.enter()
      .append('option')
      .attr('value', function(d){ return d; })
      .text(function(d){ return d; })
    ;
    opts.attr('value', function(d){ return d; })
      .text(function(d){ return d; })
    ;
    /* init listeners for the different modal components */
    let cols = d3.select('#column-select')
      .on('change', function(){
        let values = [...new Set(self._data.map(pa => pa[d3.event.target.value]))];
        self._updateSelectOptions('#value-select', values);
      })
    ;
    d3.select('#column-select').dispatch('change');

    d3.select('#color-add').on('click', function(){ self._displayModal('color'); });
    d3.select('#shape-add').on('click', function(){ self._displayModal('shape'); });
    d3.select('#modal-ok').on('click', function(){ self._okModal(); });
    d3.select('#modal-cancel').on('click', function(){ d3.select('#modal').style('display', 'none'); });
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
    /* select all the elements */
    let opts = d3.select(id).selectAll('option')
      .data(values)
    /* add options according to the amount required */
    opts.enter()
      .append('option')
      .attr('value', function(d){ return d; })
      .text(function(d){ return d; })
    ;
    /* remove unnecesary options */
    opts.exit().remove();
    /* update values of re-used options */
    opts.attr('value', function(d){ return d; })
      .text(function(d){ return d; })
    ;
  }

  /**
   * Display the modal to allow user interaction
   *
   * @param {string} type An identifier of the type of modal to be shown, either
   * 'color' or 'shape'.
   */
  _displayModal(type){
    let self = this;
    let content = d3.select('#modal')
      .style('display', 'flex')
      .attr('data-type', type)
    ;
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
   * Handle application of color or shape to data.
   * Once the user selects to apply a specific color or shape to a category of
   * data, here we handle the update of the color or shape list, and apply the
   * corresponding change to the dataset.
   */
  _okModal(){
    /* hide the modal from view */
    let modal = d3.select('#modal').style('display', 'none');
    /* capture the type of modal and the values that the user selected */
    let type = modal.attr('data-type');
    let col = d3.select('#column-select').property('value');
    let val = d3.select('#value-select').property('value');
    let upd = type === 'color' ?
      d3.select('#modal-input > input').property('value') :
      d3.select('input[name="shape"]:checked').property('value')
    ;
    /* apply the changes in visual properties to the corresponding data points */
    this._data.map(data => {
      if(data[col] === val)
        data[type]=upd;
      return data;
    });

    /* update the corresponding table */
    if( type === 'color'){
      this._colors.push( {'key': val, 'value': upd} );
      this.initColorTable();
    }
    else{
      this._shapes.push( {'key': val, 'value': upd} );
      this.initShapeTable();
    }
    /* redraw the graph */
    this.plot();
  }

  /**
   * Initialize the display of the color table
   */
  initColorTable(){
    let self = this;
    super.initTable('color', this._colors);
    /* update the color backgroud of components */
    let displays = d3.select('#color-table').selectAll('.display')
      .data(this._colors)
      .style('background-color', function(d){ return d.value; })
    ;
    /* update the contents of the remove button */
    let close = d3.select('#color-table').selectAll('.small-close')
      .on('click', function(){
        if( this.dataset.key === 'Default' ) return;
        self._colors.splice(this.dataset.index, 1);
        self.assignColors();
        self.initColorTable();
        self.plot();
      })
    ;
  }

  /**
   * Initialize the display of the shape table
   */
  initShapeTable(){
    let self = this;
    super.initTable('shape', this._shapes);
    /* update the display of the corresponding shapes */
    let displays = d3.select('#shape-table').selectAll('.display')
      .data(this._shapes)
      .append('svg')
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
    /* update the contents of the remove button */
    let close = d3.select("#shape-table").selectAll('.small-close')
      .on('click', function(){
        if( this.dataset.key === 'Default' ) return;
        self._shapes.splice(this.dataset.index, 1);
        self.assignShapes();
        self.initShapeTable();
        self.plot();
      })
    ;
  }

  /**
   * Plot a BioActivity Graph
   */
  plot(){
    /* plot the axis of the graph */
    this.plotXAxis();
    this.plotYAxis();
    /* retrieve the scale of the axis for quick plotting of the data points */
    let x = this._xAxis.scale();
    let dx = x.bandwidth()/2; // needed to position points at the middle of the band
    let y = this._yAxis.scale();
    /* remove the previous points from the graph */
    let canvas = d3.select('svg#canvas_bioActivity > g#graph');
    canvas.selectAll('#points').remove();
    /* (re)draw the points, grouped in a single graphics element  */
    canvas.append('g')
      .attr('id', 'points')
      .attr('transform', 'translate('+this._margin.left+', 0)')
    ;

    /* Each data point will be d3 symbol (represented using svg paths) */
    let pts = d3.select('#points').selectAll('g')
      .data(this._data)
      // append the path for each data point
      .enter()
      .append('path')
        // and the properties of each point
        .attr('class', 'data-point')
        .attr('transform', (d) => {
          return 'translate('+(x(d[this._x])+dx)+','+y(d[this._y])+')';
        })
        .attr('fill', function(d){ return d.color; })
        .attr('d', function(d){
          let s = ['Circle','Cross','Diamond','Square','Star','Triangle','Wye']
          let symbol = d3.symbol()
            .size(50)
            .type(d3.symbols[s.indexOf(d.shape)])
            ;
          return symbol();
        })
        // each point will also have an associated svg title (tooltip)
        .append('svg:title')
          .text(function(d){
            return 'Organism: '+d.organism+
              '\nGene: '+d.symbol+
              '\nConcentation: '+d.label+'nM';
          })
    ;
  }
}