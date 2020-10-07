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
   * @param {string} data The Java ArrayList string representation of the data
   * retrieved from the database for the construction of the graph
   * @param {int} width The width of the viewBox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, data, width, height){
    /* initialize super class attributes */
    super('bioActivity', name, width, height);
    /* initial variables for X and Y axis */
    this._x = 'Activity Type';
    this._y = 'Activity Concentration';

    /* parse data to local storage */
    super.loadData(data);
    if( this._data.length === 0 ){
      d3.select('.targetmineGraphDisplayer').text('No BioActivity Data to Display.');
      return;
    }
    /* Initialize the Axis of the graph */
    super.initXLabels();
    super.initXAxis();
    super.initYAxis(true);
    /* Initialize data points, position, color and shape */
    super.setPointPositions();
    super.initColorsAndShapes(false);
    super.assignColors();
    super.assignShapes();
    /* Initialize histogram for violin plots display */
    super.initHistogramBins();

    /* init DOM elements */
    this.initDOM();
    /* assign functionality to different interface components */
    let self = this;
    d3.select('#input[type=checkbox]')
      .on('change', () => { self.plot();} )
    /* Plot the graph */
    this.plot();
  }

  /**
   * Initialize BioActivityGraph specific DOM elements
   */
  initDOM(){
    /* init Common DOM elements */
    let columnElements = [
      { 'name': 'color', 'text': 'Color Table', 'button': true },
      { 'name': 'shape', 'text': 'Shape Table', 'button': true },
      { 'name': 'visuals', 'text': 'Other Visuals', 'button': false },
    ];
    super.initDOM(columnElements);

    let self = this;
    /* First, we update the three tables used for visualization handling within
     * the graph */
    this.updateColorTable();
    this.updateShapeTable();
    this.updateVisualsTable();

    /* A modal component is used by the user to select colors and shapes used
     * for the display of points */
    let container = d3.select('.targetmineGraphDisplayer')
      .append('div')
        .attr('id', 'modal')
        .attr('class', 'modal')
        .append('div')
          .attr('id', 'modal-content')
          .attr('class', 'modal-content')
    ;
    /* add title to the modal */
    let content = d3.select('.targetmineGraphDisplayer').select('#modal-content');
    content.append('h3')
      .attr('id', 'modal-title')
      .attr('class', 'modal-title')
      ;
    /* In order to a color/shape to be applied, two different levels of selection
     * have to be specified. These are refered to as 'column' and 'value'. Here
     * we add the components required for user selection */
    let colSelect = content.append('select')
      .attr('id','modal-column-select')
      .attr('class', 'modal-select')
      ;
    content.append('select')
      .attr('id', 'modal-value-select')
      .attr('class', 'modal-select')
      ;
    /* 'column' options are fixed */
    let opts = this._data.columns.reduce((prev, curr, i) => {
      if( typeof(self._data[0][curr]) === 'string' ) prev.push(curr);
      return prev;
    },[]);
    colSelect.selectAll('option')
      .data(opts)
      .enter().append('option')
        .attr('value', d => d)
        .text(d => d)
      ;
    /* but each time a new column is selected, the options in the 'value' select
     * need to be updated */
    d3.select('#modal-column-select')//.selectAll('option')
      .on('change', function(e){
        let values = [...new Set(self._data.map(pa => pa[e.target.value]))];
        self.updateSelectOptions('#modal-value-select', values);
      })
      .dispatch('change') // make sure to initially update the values
      ;

    /* The space for the color/shape input elements */
    content.append('div')
      .attr('id', 'modal-input')
      .attr('class', 'modal-input')
    ;
    /* OK and Cancel buttons */
    content.append('button')
      .attr('id','modal-ok')
      .text('OK')
    ;
    content.append('button')
      .attr('id', 'modal-cancel')
      .text('Cancel')
    ;

    /* bind functionality to Righ-column interface elements */
    d3.select('#color-add').on('click', function(){ self.modalDisplay('color') });
    d3.select('#shape-add').on('click', function(){ self.modalDisplay('shape'); });
    d3.select('#modal-ok').on('click', function(){ self.modalOK(); });
    d3.select('#modal-cancel').on('click', function(){ d3.select('#modal').style('display', 'none'); });
  }

  /**
   * Display the modal to allow user interaction
   *
   * @param {string} type An identifier of the type of modal to be shown, either
   * 'color' or 'shape'.
   */
  modalDisplay(type){
    let self = this;
    /* Set display to True */
    let content = d3.select('#modal')
      .style('display', 'flex')
      .attr('data-type', type)
    ;
    /* define the title for the window */
    d3.select('#modal-title')
      .text('Select '+type+' to apply:')

    /* remove previous input elements */
    d3.selectAll('#modal-input > *').remove();
    /* If adding a color element, define a new color input  */
    if( type === 'color' ){
      d3.select('#modal-input')
        .append('input')
          .property('type', 'color')
          .property('value', '#000000')
      ;
    }
    /* else, incorporate the input required for shape elements */
    else{
      let opts = d3.select('#modal-input').selectAll('input')
        .data(['Circle','Cross','Diamond','Square','Star','Triangle','Wye'])
        .enter()
        .append('input')
          .attr('id', d => 'symbol-'+d)
          .attr('value', d => d)
          .attr('type', 'radio')
          .attr('name', 'shape')
        .append('label')
          .text(d => d)
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
  modalOK(){
    /* hide the modal from view */
    let modal = d3.select('#modal').style('display', 'none');
    /* capture the type of modal and the values that the user selected */
    let type = modal.attr('data-type');
    let col = d3.select('#modal-column-select').property('value');
    let val = d3.select('#modal-value-select').property('value');
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
    if( type === 'color' ){
      this._colors[val] = upd;
      this.updateColorTable();
    }
    else{
      this._shapes[val] = upd;
      this.updateShapeTable();
    }
    /* redraw the graph */
    this.plot();
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
  updateSelectOptions(id, values){
    /* select all the elements */
    d3.select(id).selectAll('option').remove();
    d3.select(id).selectAll('option')
      .data(values)
      .enter().append('option')
        .attr('value', function(d){ return d; })
        .text(function(d){ return d; })
      ;
  }

  /**
   * Initialize the display of the color table
   *
   */
  updateColorTable(){
    let self = this;
    let keys = Object.keys(this._colors);
    let values = Object.values(this._colors);
    /* these are the DOM elements in each row of the table */
    let rowComponents = [
      { 'type': 'div', 'attr':[['class', 'flex-cell display']] },
      { 'type': 'div', 'attr':[['class', 'flex-cell label']] },
      { 'type': 'span', 'attr':[['class', 'flex-cell small-close']] },
    ];
    super.initTableRows('#color-table', 'color', keys, rowComponents);
    /* update the color backgroud of the display are of each row */
    d3.select('#color-table').selectAll('.display')
      .data(values)
      .style('background-color', d => d )
    ;
    /* set the labels for each row */
    d3.select('#color-table').selectAll('.label')
      .data(keys)
      .text(d => d)
    ;
    /* update the small close span element */
    d3.select('#color-table').selectAll('.small-close')
      .html('&times;')
      .on('click', function(){
        if( this.dataset.key === 'Default' ) return;
        delete( self._colors[this.dataset.key] );
        self.assignColors();
        self.updateColorTable();
        self.plot();
      })
    ;
  }


  /**
   * Initialize the display of the shape table
   */
  updateShapeTable(){
    let self = this;
    let keys = Object.keys(this._shapes);
    let values = Object.values(this._shapes);
    /* these are the DOM elements in each row of the table */
    let rowComponents = [
      { 'type': 'div', 'attr':[['class', 'flex-cell display']] },
      { 'type': 'div', 'attr':[['class', 'flex-cell label']] },
      { 'type': 'span', 'attr':[['class', 'flex-cell small-close']] },
    ];
    super.initTableRows('#shape-table', 'shape', keys, rowComponents);
    /* we customize the DOM elements according to the values of the shapes list */
    d3.select('#shape-table').selectAll('.display')
      .data(values)
      .append('svg')
        .attr('class', 'display-cell')
        .attr('viewBox', '-5 -5 10 10')
        .append('path')
          .attr('fill', 'black')
          .attr('d', (d) => { return d3.symbol().type(d3['symbol'+d]).size(10)(); })
    ;
    /* set the label for each row */
    d3.select('#shape-table').selectAll('.label')
      .data(keys)
      .text(d => d)
    ;
    /* update the small-close span element */
    let close = d3.select("#shape-table").selectAll('.small-close')
      .html('&times;')
      .on('click', function(){
        if( this.dataset.key === 'Default' ) return;
        delete( self._shapes[this.dataset.key] );
        self.assignShapes();
        self.updateShapeTable();
        self.plot();
      })
    ;
  }

  /**
  *
  */
  updateVisualsTable(){
    let self = this;
    /* these are the DOM elements in each row of the table */
    let rowElements =[ 'violin', 'jitter' ];
    let rowComponents = [
      { 'type': 'input', 'attr': [['type', 'checkbox'], ['class','flex-cell display']] },
      { 'type': 'div', 'attr':[['class', 'flex-cell label']] },
    ];
    super.initTableRows('#visuals-table', 'visual', rowElements, rowComponents);
    /* Customization of DOM elements */
    d3.select('#visuals-table').selectAll('.label')
      .data(rowElements)
      .text( d => 'Add '+d )
    d3.select('#visuals-table').selectAll('input')
      .data(rowElements)
      .attr('id', d => 'cb-'+d)
    /* Event handlers association */
    d3.select('#cb-violin').on('change', function(){
      if( this.checked )
        self.plotViolins();
      else{
        d3.selectAll("#violins").remove();
      }
    });
    d3.select('#cb-jitter').on('change', function(){
      self.setPointPositions(this.checked);
      self.plot();
    });
  }

  /**
   * Plot a BioActivity Graph
   *
   */
  plot(){
    /* plot the X and Y axis of the graph */
    super.plotXAxis();
    super.plotYAxis();

    /* (re)draw the points, grouped in a single graphics element  */
    let canvas = d3.select('svg#canvas_bioActivity > g#graph');
    canvas.selectAll('#points').remove();
    canvas.append('g')
      .attr('id', 'points')
      .attr('transform', 'translate('+this._margin.left+', 0)')
    ;
    /* Each data point will be d3 symbol (represented using svg paths) */
    let pts = d3.select('#points').selectAll('g')
      .data(this._data)
    /* each point belongs to the 'data-point' class its positioned in the graph
     * according to the associated (x,y) coordinates and its drawn using its
     * color and shape */
    let point = pts.enter().append('path')
      .attr('class', 'data-point')
      .attr('transform', d => 'translate('+d.x+' '+d.y+')')
      .attr('fill', d => d.color )
      .attr('d', function(d){
        let s = ['Circle','Cross','Diamond','Square','Star','Triangle','Wye']
        let symbol = d3.symbol()
          .size(50)
          .type(d3.symbols[s.indexOf(d.shape)])
          ;
        return symbol();
      })
      ;
    /* each point will also have an associated svg title (tooltip) */
    point.append('svg:title')
      .text((d) => {
        return 'Organism: '+d['Organism Name']+
          '\nGene: '+d['Gene Symbol']+
          '\nConcentation: '+d['Activity Concentration']+'nM';
      })
      ;
  }
}
