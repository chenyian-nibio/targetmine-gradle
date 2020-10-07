<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<%! int width = 400; %>
<%! int height = 400; %>

<div class="collection-table">
<h3>Bio-Activities Graph</h3>

<!-- Visualization Container -->
<div class='targetmineGraphDisplayer'></div>

<!-- Visualization Definition -->
<script type="text/javascript">
  import(window.location.origin+'/targetmine/js/BioActivityGraph.mjs')
    .then((module) => {
      window.bioActivityGraph = new module.BioActivityGraph(
        '${compound}',
        '${data}',
        <%= width %>,
        <%= height %>
      );
    });
</script>
</div>
