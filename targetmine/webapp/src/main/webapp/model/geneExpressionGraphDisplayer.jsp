<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<%! int width = 1000; %>
<%! int height = 400; %>

<div class='collection-table'>

  <!-- Verify that there isnt an empty collection of data -->
  <c:choose>
    <c:when test="${empty data}">
      <h3>No Gene Expression Data to Display</h3>
    </c:when>

    <c:otherwise>
      <h3>Gene Expression Graph</h3>
      <!-- Visualization Container -->
      <div class='targetmineGraphDisplayer'>

        <!-- Left Column of the Visualization (main display) -->
        <svg class='targetmineGraphSVG' id='canvas_geneExpression' viewbox="0 0 <%= width %> <%= ' ' %> <%= height%>"></svg>

        <!-- Right Column, reserved for visualization controls -->
        <div class='rightColumn'>
          <!-- Provide user control for the use of violin diplsays and jitter
          effects  -->
          <div id='visuals-div' style='flex-direction: column;'>
            <label for='visuals-table'>Display Options:</label>
            <br />
            <table id='visuals-table'>
              <tbody>
                <div class='flex-row' id='visuals-violin'>
                  <input type='checkbox' id='cb-violin'></input>
                  <div class='flex-cell label'>Add violin</div>
                </div>
                <div class='flex-row' id='visuals-jitter'>
                  <input type="checkbox" id='cb-jitter'></input>
                  <div class='flex-cell label'>Add jitter</div>
                </div>
              </tbody>
            </table>
          </div>
        </div>

      </div>

      <script type="text/javascript">
        import(window.location.origin+'/targetmine/js/GeneExpressionGraph.mjs')
          .then((module) => {
            window.geneExpressionGraph = new module.GeneExpressionGraph('${gene}', <%= width %>, <%= height %>);
            window.geneExpressionGraph.loadData('${data}');
            window.geneExpressionGraph.initDisplayTree();
            window.geneExpressionGraph.initXLabels();
            window.geneExpressionGraph.initXAxis();
            window.geneExpressionGraph.initYAxis();
            window.geneExpressionGraph.initColorsAndShapes();
            window.geneExpressionGraph.assignColors();
            window.geneExpressionGraph.plot();
          });
      </script>

    </c:otherwise>
  </c:choose>
</div>
