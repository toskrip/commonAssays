<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.flow.persist.FlowManager" %>
<%@ page import="org.labkey.flow.persist.ObjectType" %>
<%@ page import="org.labkey.flow.webparts.FlowSummaryWebPart" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.flow.controllers.run.RunController" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.flow.query.FlowTableType" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.flow.controllers.executescript.AnalysisScriptController" %>
<%@ page import="org.labkey.flow.controllers.editscript.ScriptController" %>
<%@ page import="org.labkey.flow.controllers.protocol.ProtocolController" %>
<%@ page import="org.labkey.flow.controllers.FlowController" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.flow.data.*" %>
<%@page extends="org.labkey.api.jsp.JspBase" %>
<%
    FlowSummaryWebPart me = (FlowSummaryWebPart) HttpView.currentModel();
    Container c = me.c;
    User user = getViewContext().getUser();

    PipelineService pipeService = PipelineService.get();
    PipeRoot pipeRoot = pipeService.findPipelineRoot(c);
    boolean _hasPipelineRoot = pipeRoot != null && pipeRoot.getUri(c) != null;
    boolean _canSetPipelineRoot = user.isAdministrator() && (pipeRoot == null || c.equals(pipeRoot.getContainer()));
    boolean _canInsert = c.hasPermission(user, ACL.PERM_INSERT);
    boolean _canUpdate = c.hasPermission(user, ACL.PERM_UPDATE);
    boolean _canCreateFolder = c.getParent() != null && !c.getParent().isRoot() &&
            c.getParent().hasPermission(user, ACL.PERM_ADMIN);

    int _fcsFileCount = FlowManager.get().getObjectCount(c, ObjectType.fcsKeywords);
    int _fcsRunCount = FlowManager.get().getRunCount(c, ObjectType.fcsKeywords);
    int _fcsRealRunCount = FlowManager.get().getFCSRunCount(c);
    int _fcsAnalysisCount = FlowManager.get().getObjectCount(c, ObjectType.fcsAnalysis);
    int _fcsAnalysisRunCount = FlowManager.get().getRunCount(c, ObjectType.fcsAnalysis);
    int _compensationMatrixCount = FlowManager.get().getObjectCount(c, ObjectType.compensationMatrix);
    int _compensationRunCount = FlowManager.get().getRunCount(c, ObjectType.compensationControl);

    FlowProtocol _protocol = FlowProtocol.getForContainer(c);
    ExpSampleSet _sampleSet = _protocol != null ? _protocol.getSampleSet() : null;

    FlowScript[] _scripts = FlowScript.getAnalysisScripts(c);
    Arrays.sort(_scripts);

    FlowExperiment[] _experiments = FlowExperiment.getAnalysesAndWorkspace(c);
    Arrays.sort(_experiments);

    ActionURL fcsFileRunsURL = new ActionURL(RunController.ShowRunsAction.class, c).addParameter("query.FCSFileCount~neq", 0);
    ActionURL fcsAnalysisRunsURL = new ActionURL(RunController.ShowRunsAction.class, c).addParameter("query.FCSAnalysisCount~neq", 0);
    ActionURL compMatricesURL = FlowTableType.CompensationMatrices.urlFor(c, QueryAction.executeQuery);

%>
<style type="text/css">
    h2.summary-header {
        margin-top:1em;
        margin-bottom:0.3em;
        border-bottom:1px solid lightgray
    }
</style>
<script type="text/javascript">
    var getQueryURL = LABKEY.ActionURL.buildURL("query", "getQuery");
</script>

<% if (_fcsRunCount > 0 || _fcsAnalysisRunCount > 0 || _compensationMatrixCount > 0) { %>
    <h2 class="summary-header">Summary</h2>
<% } %>

<% if (_fcsRunCount > 0) { %>
    <script type="text/javascript">
    Ext.onReady(function () {
        var tip = new LABKEY.ext.CalloutTip({
            target: "fcsFileRuns-div",
            autoLoad: {
              url: LABKEY.ActionURL.buildURL("query", "getQuery", null, {
                  schemaName: "flow",
                  "query.queryName": "Runs",
                  "query.FCSFileCount~neq": 0,
                  "query.columns": encodeURI("Name,Flag/Comment,FCSFileCount"),
                  "query.sort": "Name",
                  apiVersion: 9.1
              })
            },
            tpl: new Ext.XTemplate(
              '<table boder=0>',
              '<tpl for="rows">',
              '  <tr>',
              '    <td nowrap><a href="{[values.Name.url]}">{[values.Name.value]}</a>',
              '    <td align="right" nowrap>({[values.FCSFileCount.value]} files)',
              '  </tr>',
              '</tpl>',
              '</table>')
        });
    });
    </script>
    <div id="fcsFileRuns-div">
        <a href="<%=fcsFileRunsURL%>">FCS Files (<%=_fcsRunCount%> <%=_fcsRunCount == 1 ? "run" : "runs"%>)</a>
    </div>
<% } %>

<% if (_fcsAnalysisRunCount > 0) { %>
    <script type="text/javascript">
    Ext.onReady(function () {
        var tip = new LABKEY.ext.CalloutTip({
            target: "fcsAnalysisRuns-div",
            autoLoad: {
              url: LABKEY.ActionURL.buildURL("query", "getQuery", null, {
                  schemaName: "flow",
                  "query.queryName": "Runs",
                  "query.FCSAnalysisCount~neq": 0,
                  "query.columns": encodeURI("Name,Flag/Comment,FCSAnalysisCount"),
                  "query.sort": "Name",
                  apiVersion: 9.1
              })
            },
            tpl: new Ext.XTemplate(
              '<table boder=0>',
              '<tpl for="rows">',
              '  <tr>',
              '    <td nowrap><a href="{[values.Name.url]}">{[values.Name.value]}</a>',
              '    <td align="right" nowrap>({[values.FCSAnalysisCount.value]} wells)',
              '  </tr>',
              '</tpl>',
              '</table>')
        });
    });
    </script>
    <div id="fcsAnalysisRuns-div">
        <a href="<%=fcsAnalysisRunsURL%>">FCS Analyses (<%=_fcsAnalysisRunCount%> <%=_fcsAnalysisRunCount == 1 ? "run" : "runs"%>)</a>
    </div>
<% } %>

<% if (_compensationMatrixCount > 0) { %>
    <script type="text/javascript">
    Ext.onReady(function () {
        var tip = new LABKEY.ext.CalloutTip({
            target: "compensationMatrices-div",
            autoLoad: {
              url: LABKEY.ActionURL.buildURL("query", "getQuery", null, {
                  schemaName: "flow",
                  "query.queryName": "CompensationMatrices",
                  "query.columns": encodeURI("Name,Flag/Comment"),
                  "query.sort": "Name",
                  apiVersion: 9.1
              })
            },
            tpl: new Ext.XTemplate(
              '<table boder=0>',
              '<tpl for="rows">',
              '  <tr>',
              '    <td nowrap><a href="{[values.Name.url]}">{[values.Name.value]}</a>',
              '  </tr>',
              '</tpl>',
              '</table>')
        });
    });
    </script>
    <div id="compensationMatrices-div">
        <a href="<%=compMatricesURL%>">Compensation (<%=_compensationMatrixCount%> <%=_compensationMatrixCount == 1 ? "matrix" : "matrices"%>)</a>
    </div>
<% } %>

<%
    if (_sampleSet != null)
    {
        %>
        <div id="samples-div"><a href="<%=_sampleSet.detailsURL()%>">Samples (<%=_sampleSet.getSamples().length%>)</a> </div>
        <%
    }
%>

<%
    if (_scripts.length > 0)
    {
        %>
        <br/>
        <div>Analysis Scripts</div>
        <div class="labkey-indented">
        <%
        for (FlowScript script : _scripts)
        {
            int runCount = script.getRunCount();
            if (runCount > 0)
            {
                %>
                <script type="text/javascript">
                Ext.onReady(function () {
                    var tip = new LABKEY.ext.CalloutTip({
                        target: "script-<%=script.getScriptId()%>-div",
                        autoLoad: {
                          url: LABKEY.ActionURL.buildURL("query", "getQuery", null, {
                              schemaName: "flow",
                              "query.queryName": "Runs",
                              "query.AnalysisScript/RowId~eq": <%=script.getScriptId()%>,
                              "query.columns": encodeURI("Name,Flag/Comment,WellCount"),
                              "query.sort": "Name",
                              apiVersion: 9.1
                          })
                        },
                        tpl: new Ext.XTemplate(
                          '<table boder=0>',
                          '<tpl for="rows">',
                          '  <tr>',
                          '    <td nowrap><a href="{[values.Name.url]}">{[values.Name.value]}</a>',
                          '    <td align="right" nowrap>({[values.WellCount.value]} wells)',
                          '  </tr>',
                          '</tpl>',
                          '</table>')
                    });
                });
                </script>
                <%
            }
            %>
            <div id="script-<%=script.getScriptId()%>-div">
                <a href="<%=script.urlShow()%>"><%=script.getName()%> (<%=runCount%> <%=runCount == 1 ? "run" : "runs"%>)</a>
            </div>
            <%
        }
        %></div><%
    }
%>

<%
    if (_fcsAnalysisRunCount > 0)
    {
        %>
        <br/>
        <div>Analysis Folders</div>
        <div class="labkey-indented">
        <%
        for (FlowExperiment experiment : _experiments)
        {
            int runCount = experiment.getRunCount(FlowProtocolStep.analysis);
            if (runCount > 0)
            {
                %>
                <script type="text/javascript">
                Ext.onReady(function () {
                    var tip = new LABKEY.ext.CalloutTip({
                        target: "script-<%=experiment.getExperimentId()%>-div",
                        autoLoad: {
                          url: LABKEY.ActionURL.buildURL("query", "getQuery", null, {
                              schemaName: "flow",
                              "query.queryName": "Runs",
                              "experimentId": <%=experiment.getExperimentId()%>,
                              "query.columns": encodeURI("Name,Flag/Comment,WellCount"),
                              "query.sort": "Name,ProtocolStep",
                              apiVersion: 9.1
                          })
                        },
                        tpl: new Ext.XTemplate(
                          '<table boder=0>',
                          '<tpl for="rows">',
                          '  <tr>',
                          '    <td nowrap><a href="{[values.Name.url]}">{[values.Name.value]}</a>',
                          '    <td align="right" nowrap>({[values.WellCount.value]} wells)',
                          '  </tr>',
                          '</tpl>',
                          '</table>')
                    });
                });
                </script>
                <%
            }
            %>
            <div id="script-<%=experiment.getExperimentId()%>-div">
                <a href="<%=experiment.urlShow()%>"><%=experiment.getName()%> (<%=runCount%> <%=runCount == 1 ? "run" : "runs"%>)</a>
            </div>
            <%
        }
        %></div><%
    }
%>

<%--
<div>
    <br>
    <img src="/labkey/_images/minus.gif"> Flagged
    <div class="labkey-indented">
        Runs: <a href="#">1 run</a><br>
        Analysis: <a href="#">10 wells</a>
    </div>
</div>
--%>

<br/>
<h2 class="summary-header">Actions</h2>
<%
    if (_canSetPipelineRoot)
    {
        ActionURL urlPipelineRoot = PageFlowUtil.urlProvider(PipelineUrls.class).urlSetup(c);
        if (!_hasPipelineRoot)
        {
            %><div><%=PageFlowUtil.textLink("Setup Pipeline", urlPipelineRoot)%></div><%
        }
        else if (_fcsFileCount == 0)
        {
            %><div><%=PageFlowUtil.textLink("Change Pipeline", urlPipelineRoot)%></div><%
        }
    }

    if (!_hasPipelineRoot)
    {
        %><div><%=PageFlowUtil.textLink("Import Workspace", new ActionURL(AnalysisScriptController.ImportAnalysisAction.class, c))%></div><%
    }
    else
    {
        %><div><%=PageFlowUtil.textLink("Upload and Import", PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, getViewContext().getActionURL().getLocalURIString()))%></div><%
    }

    if (_fcsRealRunCount > 0)
    {
        %><div><%=PageFlowUtil.textLink("Create Analysis Script", new ActionURL(ScriptController.NewProtocolAction.class, c))%></div><%
    }
%>

<div>[Calculate compensation matrices]</div>
<div>[Analyze FCS Files]</div>

<%
    if (_protocol != null)
    {
        %><br/><h2 class="summary-header">Settings</h2><%
        if (_canUpdate)
        {
            %><div><%=PageFlowUtil.textLink("Upload Samples", _protocol.urlUploadSamples(_sampleSet != null))%></div><%
            if (_sampleSet != null)
            {
                %><div><%=PageFlowUtil.textLink("Sample Join Fields", _protocol.urlFor(ProtocolController.Action.joinSampleSet))%></div><%
            }
            if (_fcsAnalysisCount > 0)
            {
                %><div><%=PageFlowUtil.textLink("Identify Background", new ActionURL(ProtocolController.EditICSMetadataAction.class, c))%></div><%
            }

            int jobCount = PipelineService.get().getQueuedStatusFiles(c).length;
            %><div><%=PageFlowUtil.textLink("Show Jobs" + (jobCount > 0 ? " (" + jobCount + " running)" : ""), PageFlowUtil.urlProvider(PipelineStatusUrls.class).urlBegin(c))%></div><%
        }
        %><div><%= PageFlowUtil.textLink("Other Settings", _protocol.urlShow())%></div><%

        if (_canCreateFolder && _hasPipelineRoot)
        {
            %><div><%=PageFlowUtil.textLink("Copy Folder", new ActionURL(FlowController.NewFolderAction.class, c))%></div><%
        }
    }
%>


<%--
<br><hr><br>

<h2 class="summary-header">Import Data</h2>
<div>Upload and import experimental data to LabKey server.</div>
<div><%= PageFlowUtil.textLink("Upload and Import", PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(c, getViewContext().getActionURL().getLocalURIString()))%></div>
<div class="labkey-indented">
<%
    ActionURL urlShowRuns = new ActionURL(RunController.ShowRunsAction.class, c).addParameter("query.FCSFileCount~neq", 0);
    if (_fcsRunCount > 0)
    {
%>
        <a href="<%=urlShowRuns.getLocalURIString()%>">FCSFile Runs (<%=_fcsRunCount%>)</a>
        |<img src="/labkey/ext-2.2/resources/images/default/grid/sort_desc.gif" xmlns:ext="http://www.extjs.com" ext:qtip="hello world">
    <%
    }
%>
    <br>
    <a href="#">4 Workspaces</a>
</div>

<h2 class="summary-header">Analyze Data</h2>
<div>Create scripts and perform analysis on uploaded FCS Files.</div>
<div><%= PageFlowUtil.textLink("Create Script", "#")%></div>
<div class="labkey-indented">
    <a href="#">5 Scripts</a>|<img src="/labkey/ext-2.2/resources/images/default/grid/sort_desc.gif">
</div>
<div><%= PageFlowUtil.textLink("Analyze", "#")%></div>
<div class="labkey-indented">
    <a href="#">3 Compensation Matrices</a>|<img src="/labkey/ext-2.2/resources/images/default/grid/sort_desc.gif">
    <br>
    <a href="#">15 Analysis runs</a>|<img src="/labkey/ext-2.2/resources/images/default/grid/sort_desc.gif">
</div>

<hr>
--%>

