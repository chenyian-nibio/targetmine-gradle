<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!-- footer.jsp -->
<br/>
<br/>
<br/>

<div class="body" align="center" style="clear:both">
    <!-- contact -->
    <c:if test="${pageName != 'contact'}">
        <div id="contactFormDivButton">
            <im:vspacer height="11" />
            <div class="contactButton">
                <a href="#" onclick="showContactForm();return false">
                    <b><fmt:message key="feedback.title"/></b>
                </a>
            </div>
        </div>
        <div id="contactFormDiv" style="display:none;">
            <im:vspacer height="11" />
            <tiles:get name="contactForm" />
        </div>
    </c:if>
    <br/>

    <!-- funding -->
    <div id="funding-footer">
        <fmt:message key="funding" />
        <br/>
        <br/>

        <!-- powered -->
        <p style="text-align: center;">Powered by 
            <a target="new" href="http://intermine.org" title="InterMine">
                <img src="/${WEB_PROPERTIES['webapp.path']}/images/icons/intermine-footer-logo.png" alt="InterMine logo" />
                &nbsp;<span style="font-style: italic; color: #666"><fmt:message key="im.version" /></span>
            </a>
        </p>
    </div>
</div>

<!-- /footer.jsp -->
