<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>


<c:set var="item" value="${reportObject.object}"/>

<c:choose>
	<c:when test="${empty item.references}">
		<div class="collection-table gray">
			<h3>0 Reference</h3>
		</div>
	</c:when>
	<c:otherwise>
		<div class="collection-table">
			<h3>
				${fn:length(item.references)} Reference<c:if test="${fn:length(item.references) > 1}">s</c:if>
			</h3>
			<table>
			<thead>
			<tr>
				<th>Source</th>
				<th>Identifier</th>
				<th>External Link</th>
			</tr>
			</thead>
			<tbody>
				<c:forEach var="ref" items="${item.references}">
				    <tr>
				    	<td>${ref.source}</td>
				    	<td><a href="report.do?id=${ref.id}">
				    	<c:choose>
				    		<c:when test="${fn:length(ref.identifier) < 60}">
				    			${ref.identifier}
							</c:when>
							<c:otherwise>
								${fn:substring(ref.identifier, 0, 55)} ...
							</c:otherwise>
						</c:choose>
				    	</a></td>
				    	<td><a href="${ref.url}" target="_blank" class="external">${ref.source}</a></td>
				    </tr>
				</c:forEach>
			</tbody>
			</table>
		</div>
	</c:otherwise>
</c:choose>

