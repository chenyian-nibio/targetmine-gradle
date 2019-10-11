'use strict';

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
    super(name, width, height);

    /* initial variables for X */
    this._x = 'category';
    this._y = 'value';

    /* keep a reference to the level of specificity of each label in the X Axis*/
    this._xLabelLevels = undefined;

    /* the different levels of specificity at which we can look gene expression */
    this._levels = [
      'category',
      'organ',
      'name'
    ];
    Object.freeze(this._levels);


  }

  /**
   * Initialize the labels of the X axis of the Graph.
   * Using the super-class method, not only set the text for the labels of the X
   * axis of the graph, but also initialize the list of levels associated to each
   * label.
   */
  initXLabels(){
    // initialize labels
    super.initXLabels();
    // and the levels
    this._xLabelLevels = Array(this._xLabels.length).fill(0);
  }

  /**
   *
   * @param {string} key the source element which we are trying to collapse into
   * is parent category.
   * @return {boolean} whether the collapsing took place or not
   */
  collapseXLabels(key){
    let self = this;
    /* recover the information on the value being expanded */
    let i = this._xLabels.indexOf(key); // its position
    let lvl = this._xLabelLevels[i]; // its level
    console.log('Collapse:', key, i, lvl);

    /* we can only collapse levels 1 and 2, otherwise, we do nothing */
    if( lvl === 1 || lvl === 2 ){
      let cat = this._levels[lvl];
      let supcat = this._levels[lvl-1];

      /* I need to know the value of the parent category of the current key */
      let supkey = (this._data.find( ele => ele[cat] === key ))[supcat];

      /* Search the list of all labels to see if its within the hierarchy rooted
       * at the supkey element */
      let delLabels = this._data.reduce(function(prev, current){
        if( current[supcat] === supkey ){
          if( !prev.includes(current[cat]) )
            prev.push(current[cat]);
          for( let i=lvl+1; i<self._levels.length; ++i ){
            let subcat = self._levels[i];
            if( !prev.includes(current[subcat]) )
              prev.push(current[subcat]);
          }
        }
        return prev;
      }, []);

      /* add the supkey value at the position of the clicked element in the
       * xLabels */
      this._xLabels.splice(i, 0, supkey);
      this._xLabelLevels.splice(i, 0, lvl-1);

      /* and remove all the labels that scheduled for removal */
      let newLabel = [];
      let newLevel = [];
      this._xLabels.forEach(function (ele, i){
        if( !delLabels.includes(ele) ){
          newLabel.push(ele);
          newLevel.push(self._xLabelLevels[i]);
        }
      });
      this._xLabels = newLabel;
      this._xLabelLevels = newLevel;

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
   *
   * @param {string} key
   * @return a boolean value indicating if the expansion was carried out or not
   */
  expandXLabels(key){
    /* recover the information on the value being expanded */
    let i = this._xLabels.indexOf(key); // its position
    let lvl = this._xLabelLevels[i]; // its level
    console.log('Expanding:', key, i, lvl);

    /* we can only expand levels 0 and 1, otherwise, we do nothing */
    if( lvl === 0 || lvl === 1 ){
      let category = this._levels[lvl];
      let subcat = this._levels[lvl+1];
      let newLabels = this._data.reduce(function(prev, current){
        if( current[category] === key && !prev.includes(current[subcat]) )
        prev.push(current[subcat]);
        return prev;
      }, []);
      /* update the text labels */
      this._xLabels.splice(i, 1, newLabels);
      this._xLabels = this._xLabels.flat();
      /* and the level of the labels */
      this._xLabelLevels.splice(i, 1, Array(newLabels.length).fill(lvl+1));
      this._xLabelLevels = this._xLabelLevels.flat();

      super.initXAxis();
      this.plot();

      return true;
    }
    else{
      console.log('Not possible to expand this level');
      return false;
    }
  }

  /**
   *
   */
  plotXAxis(angle){
    let self = this;
    super.plotXAxis(angle);

    /* assign click function to axis labels if required */
    let labels = d3.selectAll('g#bottom-axis > g.tick > text')
      .on('click', function(d){ self.expandXLabels(d); })
      .on('contextmenu', function(d){ d3.event.preventDefault(); self.collapseXLabels(d); })
      ;
  }

  /**
   *
   */
  plot(){
    let self = this;
    this.plotXAxis(45);
    this.plotYAxis();

    /* Generate an array of data points positions and colors based on the scale
     * defined for each axis */
    let xscale = this._xAxis.scale();
    let yscale = this._yAxis.scale();
    let points = [];
    this._xLabels.slice(1,this._xLabels.length-1).forEach(function(label, i){
      let col = self._levels[self._xLabelLevels[i+1]];
      let current = self._data.reduce( function(prev, curr){
        if( curr[col] === label ){
          prev.push(
            {
              x: xscale(label),
              y: yscale(curr['value']),
              color: curr['color'],
            }
          );
        }
        return prev;
      },[]);
      points = points.concat(current);
    });

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
    let point = pts.enter().append('circle')
      .attr('cx', function(d){ return d.x; })
      .attr('cy', function(d){ return d.y; })
      .attr('r', '4')
      .style('fill', function(d){ return d.color; })
    ;
  }



}
