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
   * @param {int} width The width of the viewBox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, width, height){
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
      super.initXAxis();
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
      super.initXAxis();
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
      .on('click', (d) => {  self.expandXLabels(d); })
      /* on right click, collapse the current level */
      .on('contextmenu', (d) => {
        d3.event.preventDefault();
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

    /* Generate an array of data points positions and colors based on the scale
     * defined for each axis */
    let xscale = this._xAxis.scale();
    let dx = xscale.bandwidth()/2; // distance to the midpoint of the scale band
    let yscale = this._yAxis.scale();
    let points = [];
    this._xLabels.forEach( (label, i) => {
      let level = this._levels[this._xLevels[i]];
      let current = this._data.reduce( function(prev, curr){
        let x = xscale(label)+dx;

        /* jitter the position of the points if requested */
        if( d3.select('#cb-jitter').property('checked') )
          x -= dx/2*Math.random();

        if( curr[level] === label ){
          prev.push(
            {
              x: x,
              y: yscale(curr['value']),
              color: curr['color'],
              value: curr['value'],
            }
          );
        }
        return prev;
      },[]);
      points = points.concat(current);

    },this);

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
      .data(points)
    let point = pts.enter().append('circle')
      .attr('cx', function(d){ return d.x; })
      .attr('cy', function(d){ return d.y; })
      .attr('r', '3')
      .style('fill', function(d){ return d.color; })
    ;
    let tooltip = point.append('svg:title')
      .text( (d) => { return 'Value: '+d.value; } )

    /* add violin strips if requested */
    if( d3.select('#cb-violin').property('checked') ){
      console.log('violin checked')
    //   canvas.selectAll("#violins").remove();
    //   canvas.append('g')
    //     .attr('id', '#violins')
    //   ;
    //
    //   let vls = d3.select('#violins').selectAll('g')
    //     .data(this._bins)
    //   let violin = vls.enter().append('g')        // So now we are working group per group
    //   .attr("transform", function(d){ return("translate(" + x(d.key) +" ,0)") } ) // Translation on the right to be at the group position
    //     .append("path")
    //       .datum(function(d){ return(d.value)})     // So now we are working bin per bin
    //       .style("stroke", "none")
    //       .style("fill","grey")
    //       .attr("d", d3.area()
    //         .x0( xNum(0) )
    //         .x1(function(d){ return(xNum(d.length)) } )
    //         .y(function(d){ return(y(d.x0)) } )
    //         .curve(d3.curveCatmullRom)    // This makes the line smoother to give the violin appearance. Try d3.curveStep to see the difference
    //       )
    }
  }

}
