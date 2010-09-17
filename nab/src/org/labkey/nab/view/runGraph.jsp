<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.nab.NabAssayRun" %>
<%@ page import="org.labkey.nab.NabAssayController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<NabAssayController.RenderAssayBean> me = (JspView<NabAssayController.RenderAssayBean>) HttpView.currentView();
    NabAssayController.RenderAssayBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
%>
<%
    ActionURL graphAction = new ActionURL(NabAssayController.GraphAction.class, context.getContainer());
    graphAction.addParameter("rowId", bean.getRunId());
    if (bean.getFitType() != null)
        graphAction.addParameter("fitType", bean.getFitType().name());
    int maxSamplesPerGraph = 8;
    int sampleCount = bean.getSampleResults().size();
    if (sampleCount > maxSamplesPerGraph)
    {
        graphAction.addParameter("width", 300);
        graphAction.addParameter("height", 300);
    }
    int graphCount = 0;
    for (int firstSample = 0; firstSample < sampleCount; firstSample += maxSamplesPerGraph)
    {
        graphAction.replaceParameter("firstSample", "" + firstSample);
        graphAction.replaceParameter("maxSamples", "" + maxSamplesPerGraph);
        ActionURL zoomGraphURL = graphAction.clone();
        zoomGraphURL.replaceParameter("width", "" + 800);
        zoomGraphURL.replaceParameter("height", "" + 600);
%>
<a href="<%= zoomGraphURL.getLocalURIString() %>">
    <img src="<%= graphAction.getLocalURIString() %>" alt="Neutralization Graph">
</a>
<%
        if (++graphCount % 2 == 0)
            out.write("<br>");
    }
%>
