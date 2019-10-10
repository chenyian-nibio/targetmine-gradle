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
   *
   */
  initColorAndShape(addXLabels=true){
    let self = this;
    this._colors = { 'Default': '#C0C0C0' };
    this._shapes = { 'Default': 'Circle' };

    if( addXLabels == true ){
      /* exclude the tips of the axis that do not represent any value */
      let labels = this._xLabels.slice(1, this._xLabels.length-1);
      labels.map( (label,i) => this._colors[label] = d3.schemeCategory10[i%d3.schemeCategory10.length] );
    }

    /* map each label to a color */
    this._data.forEach(function(item){
      item.color = self._colors[item[self._x]] ? self._colors[item[self._x]] : self._colors['Default'];
      item.shape = self._shapes['Default'];
    });

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


//
// /**
//  * @class GeneExpressionGraph
//  * @classdesc Used to display a Gene Expression level graph in the report page
//  * of genes
//  * @author Rodolfo Allendes
//  * @version 0.1
//  */
// class GeneExpressionGraph extends TargetMineGraph{
//
//   /**
//    * Initialize a new instance of GeneExpressionGraph
//    *
//    * @param {string} name The title for the graph
//    */
//   constructor(name, width, height){
//     /* initialize super-class attributes */
//     super(name, width, height);
//
//     /* the different levels of specificity at which we can look gene expression */
//     this._levels = [
//       'category',
//       'organ',
//       'name'
//     ];
//     Object.freeze(this._levels);
//
//     /* initial variables for X */
//     this._x = 'category';
//     this._y = 'value';
//
//     /* keep a reference to the level of specificity of each label in the X Axis*/
//     this._xLabelLevels = undefined;
//   }
//
//   /**
//    * Initialize the labels of the X axis of the Graph.
//    * Using the super-class method, not only set the text for the labels of the X
//    * axis of the graph, but also initialize the list of levels associated to each
//    * label.
//    */
//   initXLabels(){
//     // initialize labels
//     super.initXLabels();
//     // and the levels
//     this._xLabelLevels = Array(this._xLabels.length).fill(0);
//   }
//
//   /**
//    *
//    * @param {string} key the source element which we are trying to collapse into
//    * is parent category.
//    * @return {boolean} whether the collapsing took place or not
//    */
//   collapseXLabels(key){
//     let self = this;
//     /* recover the information on the value being expanded */
//     let i = this._xLabels.indexOf(key); // its position
//     let lvl = this._xLabelLevels[i]; // its level
//     console.log('Collapse:', key, i, lvl);
//
//     /* we can only collapse levels 1 and 2, otherwise, we do nothing */
//     if( lvl === 1 || lvl === 2 ){
//       let cat = this._levels[lvl];
//       let supcat = this._levels[lvl-1];
//
//       /* I need to know the value of the parent category of the current key */
//       let supkey = (this._data.find( ele => ele[cat] === key ))[supcat];
//
//       /* Search the list of all labels to see if its within the hierarchy rooted
//        * at the supkey element */
//       let delLabels = this._data.reduce(function(prev, current){
//         if( current[supcat] === supkey ){
//           if( !prev.includes(current[cat]) )
//             prev.push(current[cat]);
//           for( let i=lvl+1; i<self._levels.length; ++i ){
//             let subcat = self._levels[i];
//             if( !prev.includes(current[subcat]) )
//               prev.push(current[subcat]);
//           }
//         }
//         return prev;
//       }, []);
//
//       /* add the supkey value at the position of the clicked element in the
//        * xLabels */
//       this._xLabels.splice(i, 0, supkey);
//       this._xLabelLevels.splice(i, 0, lvl-1);
//
//       /* and remove all the labels that scheduled for removal */
//       let newLabel = [];
//       let newLevel = [];
//       this._xLabels.forEach(function (ele, i){
//         if( !delLabels.includes(ele) ){
//           newLabel.push(ele);
//           newLevel.push(self._xLabelLevels[i]);
//         }
//       });
//       this._xLabels = newLabel;
//       this._xLabelLevels = newLevel;
//
//       super.initXAxis();
//       this.plot();
//
//       return true;
//     }
//     else{
//       console.log('Not possible to collapse this level');
//       return false;
//     }
//   }
//
//   /**
//    *
//    * @param {string} key
//    * @return a boolean value indicating if the expansion was carried out or not
//    */
//   expandXLabels(key){
//     /* recover the information on the value being expanded */
//     let i = this._xLabels.indexOf(key); // its position
//     let lvl = this._xLabelLevels[i]; // its level
//     console.log('Expanding:', key, i, lvl);
//
//     /* we can only expand levels 0 and 1, otherwise, we do nothing */
//     if( lvl === 0 || lvl === 1 ){
//       let category = this._levels[lvl];
//       let subcat = this._levels[lvl+1];
//       let newLabels = this._data.reduce(function(prev, current){
//         if( current[category] === key && !prev.includes(current[subcat]) )
//         prev.push(current[subcat]);
//         return prev;
//       }, []);
//       /* update the text labels */
//       this._xLabels.splice(i, 1, newLabels);
//       this._xLabels = this._xLabels.flat();
//       /* and the level of the labels */
//       this._xLabelLevels.splice(i, 1, Array(newLabels.length).fill(lvl+1));
//       this._xLabelLevels = this._xLabelLevels.flat();
//
//       super.initXAxis();
//       this.plot();
//
//       return true;
//     }
//     else{
//       console.log('Not possible to expand this level');
//       return false;
//     }
//   }
//
//   /**
//    *
//    */
//   plotXAxis(angle){
//     let self = this;
//     super.plotXAxis(angle);
//
//     /* assign click function to axis labels if required */
//     let labels = d3.selectAll('g#bottom-axis > g.tick > text')
//       .on('click', function(d){ self.expandXLabels(d); })
//       .on('contextmenu', function(d){ d3.event.preventDefault(); self.collapseXLabels(d); })
//       ;
//   }
//
//   /**
//    *
//    */
//   plot(){
//     let self = this;
//     this.plotXAxis(45);
//     this.plotYAxis();
//
//     /* Generate an array of data points positions and colors based on the scale
//      * defined for each axis */
//     let xscale = this._xAxis.scale();
//     let yscale = this._yAxis.scale();
//     let points = [];
//     this._xLabels.slice(1,this._xLabels.length-1).forEach(function(label, i){
//       let col = self._levels[self._xLabelLevels[i+1]];
//       let current = self._data.reduce( function(prev, curr){
//         if( curr[col] === label ){
//           prev.push(
//             {
//               x: xscale(label),
//               y: yscale(curr['value']),
//               color: curr['color'],
//             }
//           );
//         }
//         return prev;
//       },[]);
//       points = points.concat(current);
//     });
//
//     /* redraw the points, using the updated positions and colors */
//     let canvas = d3.select('svg#canvas > g#graph');
//     canvas.selectAll('#points').remove();
//
//     canvas.append('g')
//       .attr('id', 'points')
//     ;
//
//     /* for each data point, generate a group where we can add multiple svg
//      * elements */
//     let pts = d3.select('#points').selectAll('g')
//       .data(points)
//     let point = pts.enter().append('circle')
//       .attr('cx', function(d){ return d.x; })
//       .attr('cy', function(d){ return d.y; })
//       .attr('r', '4')
//       .style('fill', function(d){ return d.color; })
//     ;
//   }
//
//
//
// }
