<%
/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.announcements.DiscussionService" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.query.QueryForm" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.DataView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.flow.FlowPreference" %>
<%@ page import="org.labkey.flow.controllers.executescript.ScriptOverview" %>
<%@ page import="org.labkey.flow.data.FlowScript" %>
<%@ page import="org.labkey.flow.query.FlowSchema" %>
<%@ page import="org.labkey.flow.query.FlowTableType" %>
<%@ page import="org.labkey.flow.view.SetCommentView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    final FlowScript script = (FlowScript)getModelBean();
    ViewContext context = getViewContext();
    ActionURL url = context.getActionURL();

    boolean canEdit = getViewContext().hasPermission(ACL.PERM_UPDATE);
%>
Analysis scripts may have up to two sections in them.
The compensation calculation describes how to locate the compensation controls in each run, and which gates need to be applied to them.
The analysis section describes which gates in the analysis, as well as the statistics that need to be calculated, and the graphs that need to be drawn.
<p>
<% if (canEdit || script.getExpObject().getComment() != null) { %>
    Script Comment: <% include(new SetCommentView(script), out); %>
<% } %>
</p>
<%
    ScriptOverview overview = new ScriptOverview(context.getUser(), context.getContainer(), script);
%>
<%=overview.toString()%>

<div>
<% if (script.getRunCount() > 0) {
    boolean showRuns = FlowPreference.showRuns.getBooleanValue(request);
    if (showRuns) {
        %><labkey:link href='<%=url.clone().replaceParameter("showRuns", "0")%>' text="Hide Runs"/><br/><%
        QueryForm form = new QueryForm();
        form.setViewContext(context);
        form.setSchemaName(FlowSchema.SCHEMANAME);
        form.setQueryName(FlowTableType.Runs.toString());

        // HACK: work around for bug 6520 : can't set filter/sort on QuerySettings progamatically
        QueryView view = new QueryView(form, null) {
            protected void setupDataView(DataView ret)
            {
                super.setupDataView(ret);
                SimpleFilter filter = (SimpleFilter)ret.getRenderContext().getBaseFilter();
                if (filter == null)
                    filter = new SimpleFilter();
                filter.addCondition("AnalysisScript/RowId", script.getScriptId(), CompareType.EQUAL);
                ret.getRenderContext().setBaseFilter(filter);
            }
        };
        view.setShadeAlternatingRows(true);
        view.setShowPagination(false);
        view.setShowBorders(true);
        view.setShowRecordSelectors(false);
        view.setShowExportButtons(false);
        view.getSettings().setMaxRows(0);
        view.getSettings().setAllowChooseQuery(false);
        view.getSettings().setAllowChooseView(false);
        view.getSettings().setAllowCustomizeView(false);

//        RunsForm runsForm = new RunsForm();
//        runsForm.setViewContext(getViewContext());
//        runsForm.setScriptId(script.getScriptId());
//        runsForm.setExperimentId(FlowObject.getIntParam(url, request, FlowParam.experimentId));
//        FlowQueryView view = new FlowQueryView(runsForm);
//        // HACK: work around for bug 6520 : can't set filter/sort on QuerySettings progamatically
//        view.getViewContext().setActionURL(script.getRunsUrl(getViewContext().getActionURL()));
//    view.setShadeAlternatingRows(true);
//    view.setShowPagination(false);
//    view.setShowBorders(true);
//    view.setShowRecordSelectors(false);
//    view.setShowExportButtons(false);
//    view.getSettings().setMaxRows(0);
//    view.getSettings().setAllowChooseQuery(false);
//    view.getSettings().setAllowChooseView(false);
//    view.getSettings().setAllowCustomizeView(false);

        include(view, out);
    } else {
        %><labkey:link href='<%=url.clone().replaceParameter("showRuns", "1")%>' text="Show Runs"/><%
    }
} %>
</div>

<%
    DiscussionService.Service service = DiscussionService.get();
    DiscussionService.DiscussionView discussion = service.getDisussionArea(
            context,
            script.getLSID(),
            script.urlShow(),
            "Discussion of " + script.getLabel(),
            false, true);
    include(discussion, out);
%>

