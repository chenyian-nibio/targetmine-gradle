'use strict';

import { TargetMineGraph } from "./TargetMineGraph.mjs";

/**
 * @class PathwayGraph
 * @classdesc Used to display pathways (using the information available in KEEG)
 * and (possibly) their interactiond in their corresponding report page
 * @author Rodolfo Allendes
 * @version 1.0
 */
export class PathwayGraph extends TargetMineGraph{

  /**
   * Initialize an instance of PathwayGraph
   *
   * @param {string} name The title for the graph
   * @param {int} width The width of the viewBox in the svg element
   * @param {int} height The height of the viewBox in the svg element
   */
  constructor(name, width, height){
    /* initialize super class attributes */
    super('pathway', name, width, height);

    /* initial variables for X and Y axis */
    // this._x = 'Activity Type';
    // this._y = 'Activity Concentration';
    console.log('hola mundo pathway');
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
  loadConnections(data){
    /* cleaning of the string provided triming of starting and end charachters
     * and replacement of ', ' for line separators */
    data = data.substring(1, data.length-1);
    data = data.replace(/, /g, '\n');
    /* local storage of the data */
    this._connections = d3.tsvParse(data, d3.autoType);
  }

  /**
   * Add the DOM elements required for the display of the graph
   */
  plot(){
    let self = this;

    /* A pathway is made from different graphical elements */
    let shapes = this._data.reduce(function(prev, current){
      /* filter out the points that are hidden from the visualization */
      prev.push(
        {
          x: current['x'],
          y: current['y'],
          'width': current['width'],
          'height': current['height'],
          'label': current['text'],
          'fill': current['fill'],
          'text': current['text'],
        }
      );
      return prev;
    }, []);

    /* redraw the points, using the updated positions and colors */
    let canvas = d3.select('svg#canvas_pathway > g#graph');
    canvas.selectAll('#shapes').remove();

    canvas.append('g')
      .attr('id', 'shapes')
      ;

    /* for each data point, generate a group where we can add multiple svg
     * elements */
    let pts = canvas.select('#shapes').selectAll('g')
      .data(shapes)
    let shape = pts.enter().append('rect')
      .attr('class', 'gene-rect')
      .attr('x', function(d){ return d.x; })
      .attr('y', function(d){ return d.y; })
      .attr('width', function(d){ return d.width; })
      .attr('height', function(d){ return d.height; })
      .style('fill', function(d){ return d.fill; })
    ;
    let tooltip = shape.append('svg:title')
      .text(function(d){ return d.text; })
    ;

    /* load the connections now */
    shapes = this._connections.reduce(function(prev, current){
      /* filter out the points that are hidden from the visualization */
      prev.push(
        {
          'x1': current['x1'],
          'x2': current['x2'],
          'y1': current['y1'],
          'y2': current['y2'],
        }
      );
      return prev;
    }, []);

    /* redraw the points, using the updated positions and colors */
    canvas.selectAll('#connections').remove();
    canvas.append('g')
      .attr('id', 'connections')
      ;

    /* for each data point, generate a group where we can add multiple svg
     * elements */
    pts = canvas.select('#connections').selectAll('g')
      .data(shapes)
    shape = pts.enter().append('line')
      .attr('class', 'connect-line')
      .attr('x1', function(d){ return d.x1; })
      .attr('y1', function(d){ return d.y1; })
      .attr('x2', function(d){ return d.x2; })
      .attr('y2', function(d){ return d.y2; })
      .style('stroke', 'black')
      .style('stroke-width', '2')
    ;
  }
}
