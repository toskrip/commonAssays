<%@ page import="org.labkey.ms2.scoring.ScoringController" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.ms2.MS2Run" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ScoringController.ChartForm> me = (JspView<ScoringController.ChartForm>) HttpView.currentView();
    ScoringController.ChartForm form = me.getModelBean();
    
    StringBuffer params = new StringBuffer();

    MS2Run[] runs = form.getRuns();
%>
<form method=get action="compare.post">
<labkey:errors/>
<% if (runs.length > 0)
{ %>
<table>
    <tr><td colspan=2 style="font-size:<%=ThemeFont.getThemeFont().getHeader_1Size()%>">
    Choose discrimate values to display in ROC chart.<br>
    </td></tr>
    <tr><td valign="top">
<table border="0">
<%
    int validRuns = 0;
    for (int i = 0; i < runs.length; i++)
    {
        MS2Run run = runs[i];
        if (run == null)
            continue;
%>
    <tr><td colspan="2" class="heading-1"><%=h(run.getDescription())%></td></tr>
    <tr><td>&nbsp;</td>
<%      if (run.getNegativeHitCount() < run.getPeptideCount() / 3)
        {
            %><td class="labkey-error">Insufficient negative hit data to perform analysis.</td><%
        }
        else
        {
            %><td class="normal">
                <input type="hidden" name="runIds_<%=i%>" value="<%=run.getRun()%>"><%

            if (validRuns > 0)
                params.append('&');
            params.append("runIds_").append(i).append("=").append(run.getRun());
            validRuns++;

            String s = run.getDiscriminateExpressions();
            String[] discriminates = s.split("\\s*,\\s*");
            int discIndex = 0;
            for (String discriminate : discriminates)
            {
                if (discriminate.startsWith("-"))
                    discriminate = discriminate.substring(1);

                %><input type="checkbox" <%=form.getDiscriminates()[i][discIndex] ? "checked=\"true\"" : ""%> name="discriminates_<%=i%>_<%=discIndex%>"> <%=h(discriminate)%><br><%
                discIndex++;
            }
            %></td><%
        }
    %></tr><%
    }

    if (validRuns > 0)
    { %>
        <tr><td>&nbsp;</td></tr>
        <tr><td colspan=2>
            <table border="0">
                <tr><td class="ms-searchform">Title</td>
                    <td class="normal"><input type="text" size="15" name="title" value="<%=form.getTitle()%>"></td></tr>
                <tr><td class="ms-searchform">Correct Amino Acids</td>
                    <td class="normal"><input type="text" size="4" name="percentAACorrect" value="<%=form.getPercentAACorrect()%>"> %</td></tr>
                <tr><td class="ms-searchform">Increment</td>
                    <td class="normal"><input type="text" size="4" name="increment" value="<%=form.getIncrement()%>"></td></tr>
                <tr><td class="ms-searchform">Limit</td>
                    <td class="normal"><input type="text" size="4" name="limit" value="<%=form.getLimit()%>"></td></tr>
                <tr><td class="ms-searchform">Chart width</td>
                    <td class="normal"><input type="text" size="4" name="width" value="<%=form.getWidth()%>"></td></tr>
                <tr><td class="ms-searchform">Chart height</td>
                    <td class="normal"><input type="text" size="4" name="height" value="<%=form.getHeight()%>"></td></tr>
                <tr><td class="ms-searchform">Marks</td>
                    <td class="normal"><input type="text" size="15" name="marks" value="<%=form.getMarks()%>"></td></tr>
                <tr><td class="ms-searchform">Mark FDR</td>
                    <td class="normal"><input type="checkbox" name="markFdr" <%=form.isMarkFdr() ? "checked=\"true\"" : ""%>"></td></tr>
                <tr><td class="ms-searchform">Save TSVs</td>
                    <td class="normal"><input type="checkbox" name="saveTsvs" <%=form.isSaveTsvs() ? "checked=\"true\"" : ""%>"></td></tr>
            </table>
        </td></tr>
        <tr>
            <td colspan="2"><input type=image src="<%=PageFlowUtil.submitSrc()%>"></td>
        </tr>
<%
        params.append("&size=").append(validRuns);
    } %>
    <input type="hidden" name="size" value="<%=validRuns%>">
</table>
</td>
<%
    params.append("&title=").append(u(form.getTitle()))
        .append("&increment=").append(u(Double.toString(form.getIncrement())))
        .append("&limit=").append(u(Integer.toString(form.getLimit())))
        .append("&width=").append(u(Integer.toString(form.getWidth())))
        .append("&height=").append(u(Integer.toString(form.getHeight())))
        .append("&marks=").append(u(form.getMarks()));

    if (form.isMarkFdr())
        params.append("&markFdr=true");
    if (form.isSaveTsvs())
        params.append("&saveTsvs=true");

    boolean hasData = false;
    int d1 = 0;
    boolean[][] discs = form.getDiscriminates();
    while(d1 < discs.length)
    {
        int d2 = 0;
        while (d2 < discs[d1].length)
        {
            if (discs[d1][d2])
            {
                params.append("&discriminates_").append(d1).append("_").append(d2).append("=on");
                hasData = true;
            }

            d2++;
        }

        d1++;
    }

    if (hasData)
    {    %>
    <td valign="top">
        <img src="chartCompare.view?<%=params%>"><br><br>
        <img src="chartCompareProt.view?<%=params%>">
    </td>
<%  } %>

</tr>
</table>
<% } %>
</form>
