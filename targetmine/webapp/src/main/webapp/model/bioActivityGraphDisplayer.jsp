<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<%! int width = 400; %>
<%! int height = 400; %>

<div class="collection-table">

<!-- Verify that there isnt an empty collection of data -->
<c:choose>
  <c:when test="${empty data}">
    <h3>No BioActivity Data to Display</h3>
  </c:when>

  <c:otherwise>
    <h3>Bio-Activities Graph</h3>
    <!-- Visualization Container -->
    <div class='targetmineGraphDisplayer'>

      <!-- Left Column of the Visualization (main display) -->
      <svg class='targetmineGraphSVG' id='canvas_bioActivity' viewbox="0 0 <%= width %> <%= ' ' %> <%= height%>"></svg>

      <!-- Right Column, reserved for visualization controls -->
      <div class='rightColumn'>
        <!-- Choose the property used to display color, and to make (in)visible
        data points associated to specific colors in the scale -->
        <div id='color-div' style='flex-direction: column;'>
          <label for='color-table'>Color Scale:</label>
          <br />
          <table id='color-table'>
            <tbody>
            </tbody>
          </table>
          <button id='color-add'>Add</button>
        </div>
        <!-- Choose the property used to map display shape, and to make (in)visible
        data points associated to specific shapes in the scale -->
        <div id='shape-div' style='flex-direction: column;'>
          <label for='shape-select'>Shape Scale:</label>
          <br />
          <table id='shape-table'>
            <tbody>
            </tbody>
          </table>
          <button id='shape-add'>Add</button>
        </div>
      </div>

      <!-- The Modal -->
      <div id="modal" class="modal">
        <!-- Modal content -->
        <div id='modal-content' class="modal-content">
          <h3 id='modal-title'></h3>
          <select id='column-select'>
            <option value=undefined>Select...</option>
          </select>
          <select id='value-select'>
            <option value=undefined>Select...</option>
          </select>
          <br />
          <div id="modal-input">
          </div>
          <br />
          <button id='modal-ok'>OK</button>
          <button id='modal-cancel'>Cancel</button>
        </div>
      </div>


    </div>


    <script type="text/javascript">
      import(window.location.origin+'/targetmine/js/BioActivityGraph.mjs')
        .then((module) => {
          window.bioActivityGraph = new module.BioActivityGraph('${compound}', <%= width %>, <%= height %>);
          // trasfer data to javascript object
          window.bioActivityGraph.loadData('${data}');
          // initialize the bands to display on the x Axis
          window.bioActivityGraph.initXLabels();
          window.bioActivityGraph.initXAxis();
          // initialize the Y axis using a log scale
          window.bioActivityGraph.initYAxis(true);
          // set the default color and shape for data points and assign the
          // corresponding value to each data point
          window.bioActivityGraph.initColorsAndShapes(false);
          window.bioActivityGraph.assignColors();
          window.bioActivityGraph.assignShapes();
          // plot the graph
          window.bioActivityGraph.plot();
          // initialize controls used for user interaction
          window.bioActivityGraph._initModal();
          window.bioActivityGraph.initColorTable();
          window.bioActivityGraph.initShapeTable();
        });
    </script>
  </c:otherwise>
</c:choose>

</div>
