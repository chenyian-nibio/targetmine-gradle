'use strict';
/**
 * Violin and jitter display based on the code from:
 * https://www.d3-graph-gallery.com/graph/violin_jitter.html
 */

import { TargetMineGraph } from './TargetMineGraph.mjs';

/**
 * @class GeneExpressionGraph
 * @classdesc Used to display a Gene Expression level graph in the report page
 * of genes
 * @author Rodolfo Allendes
 * @version 0.1
 */
export class GeneExpressionGraph extends TargetMineGraph{

  /**
   * Initialize a new instance of GeneExpressionGraph
   *
   * @param {string} name The title for the graph
   * @param {data} data The Java ArrayList string representation of the data
   * retrieved from the database for the construction of the graph
   * @param {int} width The width of the viewBox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, data, width, height){
    /* initialize super-class attributes */
    super('geneExpression', name, width, height);

    /* Title for each axis in the graph */
    this._x = undefined;
    this._y = 'value';
    /* Different levels of specificity at which we can look gene expression */
    this._levels = {
      0: 'category',
      1: 'organ',
      2: 'name',
    };
    Object.freeze(this._levels);

    /* The display tree contains the information required for the correct display
    of data points along the X axis */
    this._displayTree = undefined;

    /* parse data to local storage */
    this.loadData(data);
    if( this._data.length === 0 ){
      d3.select('.targetmineGraphDisplayer').text('No Gene Expression Data to Display');
      return;
    }
    /* Initialize the tree structure of levels for the graph */
    this.initDisplayTree();
    /* Initialize the Axis of the graph */
    this.initXLabels();
    this.initXAxis();
    this.initYAxis();
    /* Initialize data points position and color */
    this.setPointPositions();
    this.initColorsAndShapes();
    this.assignColors();
    /* Initialize histogram for violin plots display */
    this.initHistogramBins();

    this.initDOM();

    this.updateVisualsTable();

    this.plot();

  }

  /**
   * Assert if two elements belong to the same branch of the displayTree
   *
   * @param {string} source
   * @param {string} target
   * @param {int} sourceLevel
   * @return {boolean} whether the source element belongs to the same branch in
   * the display tree as the target element.
   */
  belongToSameBranch(source, target, sourceLevel, targetLevel){
    /* if both are on the same lvl and share the parent, they are on the same branch */
    if(
      sourceLevel === targetLevel &&
      this._displayTree[sourceLevel][source].parent === this._displayTree[targetLevel][target].parent
    ){
      return true;
    }

    /* if source is below target, then they are on the same branch only if target
      and source share a common ancestor */
    if( sourceLevel > targetLevel ){
      return this.belongToSameBranch(this._displayTree[sourceLevel][source].parent, target, sourceLevel-1, targetLevel);
    }

    /* if source is above target, then they will only be displayed together if
      they are on different branches, thus always false.
      Same applies for all other cases. */
    return false;
  }

  /**
   * Initialize a tree of the whole structure of the graph.
   * In order to handle the transitions between different levels within the tree
   * being displayed, a whole tree is build on load.
   */
  initDisplayTree(){
    this._displayTree = {
      0: {},
      1: {},
      2: {},
    };
    /* traversing of the data once is required to build all the links between
      the different elements within the tree */
    this._data.forEach( (item,i) => {
      let category = item.category;
      let organ = item.organ;
      let name = item.name;

      // if a node at the category level is not yet included, we can add it
      // together with the first of his childrend and grand-children
      if( !this._displayTree[0].hasOwnProperty(category) ){
        this._displayTree[0][category] = {
          parent: undefined,
          children: [organ],
          display: category.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
        };
        this._displayTree[1][organ] = {
          parent: category,
          children: [name],
          display: organ.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
         };
        this._displayTree[2][name] = {
          parent: organ,
          children: undefined,
          display: name.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
        };
      }
      // when only the node at the organ level has not been added, the parent is
      // already part of the tree, so we only need to update its children and
      // include the new member, together with its first child
      else if( !this._displayTree[1].hasOwnProperty(organ) ) {
        this._displayTree[0][category].children.push(organ);
        this._displayTree[1][organ] = {
          parent: category,
          children: [name],
          display: organ.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
        };
        this._displayTree[2][name] = {
          parent: organ,
          children: undefined,
          display: name.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
        };
      }
      // when a node at the name level has not been added to the tree, we
      // only need to update its parent's list of children and add it to the tree
      else if ( !this._displayTree[2].hasOwnProperty(name) ) {
        this._displayTree[1][organ].children.push(name);
        this._displayTree[2][name] = {
          parent: organ,
          children: undefined,
          display: name.replace(/[-_\/]/g,' ').replace(/(?:^|\s)+\S/g, match => match.toUpperCase()).split(' '),
        };
      }
    },this);
  }

  /**
   * Initialize DOM elements
   */
  initDOM(){
    /* init common DOM elements */
    let columnElements = [
      { 'name': 'visuals', 'text': 'Other Visuals', 'button': false },
    ];
    super.initDOM(columnElements);
  }

  /**
   *
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
    /* pre-process the data array to extract the label and values only */
    let filteredData = this._data.map( d => {
      if( self._xLabels.indexOf(d['category']) !== -1 )
        return {label: d['category'], value: d['value']};
      else if ( self._xLabels.indexOf(d['organ']) !== -1 )
        return {label: d['organ'], value: d['value']};
      return {label: d['name'], value: d['value']};
    });

    this._bins = d3.rollup(
      filteredData, //self._data,
      d => {
        let input = d.map(g => g.value);
        let bins = histogram(input);
        return bins;
      },
      d => d.label
    );
  }

  /**
   * Initialize the labels of the X axis of the Graph.
   * As the _displayTree contains a list of all the different labels across the
   * different levels of the structure, we can simply copy that information as
   * the initial labels for the graph.
   * We also initialize the list of levels associated to each label.
   */
  initXLabels(){
    /* copy labels from the _displayTree */
    this._xLabels = [];
    this._xLabels.push(Object.keys(this._displayTree[0]));
    this._xLabels = this._xLabels.flat();
    /* and initialize the level of each of the labels */
    this._xLevels = Array(this._xLabels.length).fill(0);
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
    this.initTableRows('#visuals-table', 'visual', rowElements, rowComponents);
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
   * Set the position (in display coordinates) of each point in the data
   *
   * @param {boolean} jitter Should the position of the point be randomly
   * jittered along the X axis or not.
   */
  setPointPositions(jitter=false){
    let self = this;
    let X = this._xAxis.scale();
    let dx = X.bandwidth()/2;
    let Y = this._yAxis.scale();

    this._xLabels.forEach((item,i)=>{
      let key = self._levels[self._xLevels[i]]; //one of [category, organ, name]
      self._data.forEach(d=>{
        if( d[key] === self._xLabels[i] ){
          d.x = X(d[key])+dx;
          if( jitter ) d.x -= (dx/2)*Math.random();
          d.y = Y(d[this._y]);
        }
      });
    });
  }


  /**
   * Collapse the labels associated to the X axis
   *
   * @param {string} target the label of the x Axis tick we are trying to collapse
   * into is parent category.
   * @return {boolean} whether the collapsing took place or not
   */
  collapseXLabels(target){

    let i = this._xLabels.indexOf(target);
    let lvl = this._xLevels[i];

    /* we can only collapse levels that are above the root level */
    if( lvl >= 1 ){
      /* we will reconstruct the label and level arrays, including only the
       elements on different branches of the display tree than the target, and
       the parent element for the target */
      let newLabels = [];
      let newLevels = [];

      this._xLabels.forEach( (item, j) => {
        // if the current element is the target, then we add its parent element
        // to the new labels
        if( item === target ){
          newLabels.push(this._displayTree[lvl][item].parent);
          newLevels.push(lvl-1);
        }
        // if the current element is on a different branch of the tree, we simply
        // copy it to the new elements
        else if( !this.belongToSameBranch(item, target, this._xLevels[j] ,lvl) ){
          newLabels.push(item);
          newLevels.push(this._xLevels[j]);
        }
        /* else, we do nothing, as it means the element is part of the branch
         to be replaced by the parent of the target element */
      },this);

      // Once we have completed the definition of the label and level arrays, we
      // simply copy them and recreate the axis and plot
      this._xLabels = newLabels;
      this._xLevels = newLevels;
      this.initXAxis();
      this.setPointPositions(d3.select('#cb-jitter').property('checked'));
      this.initHistogramBins();
      this.plot();
      return true;
    }
    else{
      console.log('Not possible to collapse this level');
      return false;
    }
  }

  /**
   * Expand the labels associated to the X axis
   *
   * @param {string} target the label on the X axis that the user clicked, so
   * to expand into its components
   * @return a boolean value indicating if the expansion was carried out or not
   */
  expandXLabels(target){
    let i = this._xLabels.indexOf(target);
    let lvl = this._xLevels[i];
    if( lvl < 2 ){
      let newLabels = this._displayTree[lvl][target].children;
      let newLevels = Array(newLabels.length).fill(lvl+1);

      this._xLabels.splice(i, 1, newLabels);
      this._xLabels = this._xLabels.flat();

      this._xLevels.splice(i, 1, newLevels);
      this._xLevels = this._xLevels.flat();

      /* redefine the x Axis with the new list of labels */
      this.initXAxis();
      this.setPointPositions(d3.select('#cb-jitter').property('checked'));
      this.initHistogramBins();
      /* re-plot the graph */
      this.plot();
      return true;
    }
    console.log('Not possible to expand this level');
    return false;
  }

  /**
   * Plot the X Axis of a Gene Expression Graph
   * An expression graph has the property that the labels using for the X axis
   * can be expanded or collapsed through three different levels of specificity.
   */
  plotXAxis(angle){
    let self = this;
    super.plotXAxis();//angle);

    /* assign an id to all text used for ticks in the axis */
    let labels = d3.selectAll('g#bottom-axis > g.tick > text')
      .attr('id', function(d){ return d; })
    ;

    /* change the text aassociated to the ticks to something more pleasent */
    this._xLabels.forEach( (item,i) => {
      if( item !== undefined ){
        let label = d3.select('text#'+item)
        .text('')
        .selectAll('tspan')
          .data(this._displayTree[this._xLevels[i]][item].display)
          .enter().append('tspan')
            .text(function(d){ return d; })
            .attr('dy', '1em')
            .attr('x', '0')
      }
    },this);

    /* assign click function to axis labels if required */
    labels = d3.selectAll('g#bottom-axis > g.tick > text')
      /* on left click, expand the current level */
      .on('click', (ev,d) => { self.expandXLabels(d); })
      /* on right click, collapse the current level */
      .on('contextmenu', (ev,d) => {
        ev.preventDefault();
        self.collapseXLabels(d);
      })
      ;
  }

  /**
   * Plot a Gene Expression Graph
   */
  plot(){
    /* Display the X and Y axis of the graph */
    this.plotXAxis();
    this.plotYAxis();

    /* redraw the points, using the updated positions and colors */
    let canvas = d3.select('svg#canvas_geneExpression > g#graph');
    canvas.selectAll('#points').remove();
    canvas.append('g')
      .attr('id', 'points')
      .attr('transform', 'translate('+this._margin.left+',0)')
    ;

    /* for each data point, generate a group where we can add multiple svg
     * elements */
    let pts = d3.select('#points').selectAll('g')
      .data(this._data)
    let point = pts.enter().append('circle')
      .attr('cx', d => d.x)
      .attr('cy', d => d.y)
      .attr('r', '3')
      .style('fill', d => d.color)
      // let tooltip = point.append('svg:title')
      .append('svg:title').text( d => {
        return 'Category: '+d.category+
          '\nOrgan: '+d.organ+
          '\nName: '+d.name+
          '\nValue: '+d.value;
        })
      ;

      /* add violin plots if selected by the user */
      if( d3.select('#cb-violin').property('checked') )
        this.plotViolins();

  }

}
