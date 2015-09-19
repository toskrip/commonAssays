<%
/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.authentication.oauth.OAuthController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    PropertyManager.PropertyMap map = (PropertyManager.PropertyMap)HttpView.currentModel();
    String clientId = StringUtils.defaultString(map.get("client_id"), "");
    String clientSecret = StringUtils.defaultString(map.get("client_secret"), "");

    String redirectURI = new ActionURL(OAuthController.RedirectAction.class, ContainerManager.getRoot()).getURIString();
%>
<p>Configure your Google API credentials as described here: <a href="https://developers.google.com/accounts/docs/OpenIDConnect#getcredentials">Google Accounts</a></p>
<p>You should set your "REDIRECT URIS" to <b><%=h(redirectURI)%></b></p>

<labkey:form method="POST">
    <table>
        <tr><td><label for="clientId">Client Id</label></td><td><input id="clientId" name="clientId" size=80 value="<%=h(clientId)%>"></td></tr>
        <tr><td><label for="clientSecret">Client Secret</label></td><td><input id="clientSecret" name="clientSecret" size=40 value="<%=h(clientSecret)%>"></td></tr>
    </table>
    <input type="submit" value="Save">
</labkey:form>