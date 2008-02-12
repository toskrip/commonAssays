<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.opensso.OpenSSOController" %>
<%@ page import="org.labkey.opensso.OpenSSOController.ConfigProperties" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ConfigProperties> me = (JspView<ConfigProperties>)HttpView.currentView();
    ConfigProperties bean = me.getModelBean();
%>
<table>
    <tr><td colspan="2">[<a href="<%=bean.authLogoURL%>">Pick a logo and link to use for OpenSSO sign in</a>]</td></tr>
    <tr><td colspan="2">[<a href="<%=bean.pickRefererPrefixURL%>">Enter a referrer URL prefix that will automatically redirect to the OpenSSO link</a>]</td></tr>
    <tr><td colspan="2">&nbsp;</td></tr><%

    for (String key : bean.props.keySet())
    {
        String value = bean.props.get(key);
%>
<tr><td class="ms-searchform"><%=key%></td><td><%=value%></td></tr><%
    }
%>
</table><br>
<%=PageFlowUtil.buttonLink("Update", OpenSSOController.getConfigureURL(getViewContext().getActionURL()))%>
<%=PageFlowUtil.buttonLink("Done", bean.getReturnUrl())%>
