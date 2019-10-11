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
    <h3>No data to visualize</h3>
  </c:when>

  <c:otherwise>
    <h3>Bio-Activities Graph</h3>
    <!-- Visualization Container -->
    <div class='targetmineGraphDisplayer'>
      <!-- Left Column of the Visualization (main display) -->
      <svg class='targetmineGraphSVG' id='canvas' viewbox="0 0 <%= width %> <%= ' ' %> <%= height%>"></svg>
      <!-- Right Column, reserved for visualization controls -->
      <div class='rightColumn'>
        <!-- Choose the property used to display color, and to make (in)visible
        data points associated to specific colors in the scale -->
        <div id='color-div' style='flex-direction: column;'>
          <label for='color-table'>Color Scale:</label>
          <br />
          <table id='color-table'><tbody></tbody></table>
          <%-- <select id='color-select'> --%>
          <%-- <ul id='color-select'> --%>
            <%-- <option value=undefined>Select...</option> --%>
          <%-- </ul> --%>
          <%-- </select> --%>

          <button id='color-add'>Add</button>
        </div>
        <!-- Choose the property used to map display shape, and to make (in)visible
        data points associated to specific shapes in the scale -->
        <div id='shape-div' style='flex-direction: column;'>
          <label for='shape-select'>Shape based on:</label>
          <br />
          <%-- <select id='shape-select'>
            <option value=undefined>Select...</option>
          </select> --%>
          <table id='shape-table'><tbody></tbody></table>
          <button id='shape-add'>Add</button>

        </div>
      </div>
    </div>

    <!-- The Modal -->
    <div id="modal" class="modal">
      <!-- Modal content -->
      <div id='modal-content' class="modal-content">
        <h3 id='modal-title'></h3>
        <span class="close">&times;</span>
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

    <script type="text/javascript">
      import(window.location.origin+'/targetmine/js/BioActivityGraph.mjs')
        .then((module) => {
          window.graph = new module.BioActivityGraph('${compound}', <%= width %>, <%= height %>);
          window.graph.loadData('${data}');
          window.graph.initXLabels();
          window.graph.initXAxis();
          window.graph.initYAxis(true);
          window.graph.initColorsAndShapes(false);
          // graph._initColumns();
          // /* update the colors used for the data points in the graph */
          window.graph.initColorTable();
          // /* update the shapes used for the data points in the graph */
          // window.graph.initTable('shape');
          /* plot the data points */
          window.graph.plot();
        });
    </script>
  </c:otherwise>
</c:choose>

</div>
