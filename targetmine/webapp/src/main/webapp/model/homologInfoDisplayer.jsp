<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<div class="collection-table">

<c:set var="gene" value="${reportObject.object}"/>

<h3>Homology</h3>
<table>
	<thead>
		<tr><th colspan="4"><p style="margin-top: 10px; color: #000000; font-weight: bold;">Bi-directional Best Hit</b></th></tr>
		<tr>
			<th>Organism</th>
			<th>DB Identifier</th>
			<th>Symbol</th>
			<th>&nbsp;&nbsp;</th>
		</tr>
	</thead>
	<tbody>
	<c:choose>
		<c:when test="${empty orthologs}">
			<tr><td colspan="4"><p style="margin: 10px;">No orthologous gene.</p></td></tr>
		</c:when>
		<c:otherwise>
			<c:forEach var="entry" items="${orthologs}">
				<tr>
					<td><a href="report.do?id=${entry.organism.id}">${entry.organism.name}</a></td>
					<td><a href="report.do?id=${entry.id}">${entry.primaryIdentifier}</a></td>
					<td><a href="report.do?id=${entry.id}">${entry.symbol}</a></td>
					<td>&nbsp;&nbsp;</td>
				</tr>
			</c:forEach>
		</c:otherwise>
	</c:choose>
	</tbody>

	<thead>
		<tr><th colspan="4"><p style="margin-top: 10px; color: #000000; font-weight: bold;">Other homology annotations</p></th></tr>
		<tr>
			<th>Organism</th>
			<th>DB Identifier</th>
			<th>Symbol</th>
			<th>Source</th>
		</tr>
	</thead>
	<tbody>
	<c:choose>
		<c:when test="${empty gene.homology}">
			<tr><td colspan="4"><p style="margin: 10px;">No information.</p></td></tr>
		</c:when>
		<c:otherwise>
			<c:forEach var="homologEntry" items="${retHomologyList}">
    			<tr>
		    		<td><a href="report.do?id=${homologEntry.organism.id}">${homologEntry.organism.name}</a></td>
		    		<td><a href="report.do?id=${homologEntry.id}">${homologEntry.primaryIdentifier}</a></td>
		    		<td><a href="report.do?id=${homologEntry.id}">${homologEntry.symbol}</a></td>
		    		<td>${retHomologyMap[homologEntry]}</td>
	    		</tr>
			</c:forEach>
		</c:otherwise>
	</c:choose>
	</tbody>
</table>

</div>