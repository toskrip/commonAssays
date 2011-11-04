<%
    /*
    * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.flow.analysis.model.FlowJoWorkspace" %>
<%@ page import="org.labkey.flow.controllers.executescript.ImportAnalysisForm" %>
<%@ page import="org.labkey.flow.controllers.protocol.ProtocolController" %>
<%@ page import="org.labkey.flow.data.FlowProtocol" %>
<%@ page import="org.labkey.flow.util.KeywordUtil" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="org.json.JSONObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ImportAnalysisForm form = (ImportAnalysisForm)getModelBean();
    ViewContext context = getViewContext();
    Container container = context.getContainer();
    PipelineService pipeService = PipelineService.get();
    PipeRoot pipeRoot = pipeService.findPipelineRoot(container);
    FlowProtocol protocol = FlowProtocol.getForContainer(container);

    FlowJoWorkspace workspace = form.getWorkspace().getWorkspaceObject();

    Set<String> keywordOptions = new LinkedHashSet<String>();
    keywordOptions.add("");
    for (FlowJoWorkspace.SampleInfo sampleInfo : workspace.getSamples())
    {
        for (String keyword : sampleInfo.getKeywords().keySet())
        {
            if (!KeywordUtil.isHidden(keyword))
                keywordOptions.add(keyword);
        }
    }

    Map<String, String> opOptions = new LinkedHashMap<String, String>();
    opOptions.put(CompareType.EQUAL.getPreferredUrlKey(), CompareType.EQUAL.getDisplayValue());
    opOptions.put(CompareType.NEQ_OR_NULL.getPreferredUrlKey(), CompareType.NEQ_OR_NULL.getDisplayValue());
    opOptions.put(CompareType.CONTAINS.getPreferredUrlKey(), CompareType.CONTAINS.getDisplayValue());
    opOptions.put(CompareType.DOES_NOT_CONTAIN.getPreferredUrlKey(), CompareType.DOES_NOT_CONTAIN.getDisplayValue());
    opOptions.put(CompareType.STARTS_WITH.getPreferredUrlKey(), CompareType.STARTS_WITH.getDisplayValue());
    opOptions.put(CompareType.DOES_NOT_START_WITH.getPreferredUrlKey(), CompareType.DOES_NOT_START_WITH.getDisplayValue());
    opOptions.put(CompareType.IN.getPreferredUrlKey(), CompareType.IN.getDisplayValue());

    Map<String, Set<String>> groups = new TreeMap<String, Set<String>>();
    for (FlowJoWorkspace.GroupInfo group : workspace.getGroups())
    {
        Set<String> groupSamples = new TreeSet<String>();
        for (String sampleID : group.getSampleIds())
        {
            FlowJoWorkspace.SampleInfo sampleInfo = workspace.getSample(sampleID);
            if (sampleInfo != null)
                groupSamples.add(sampleInfo.getLabel());
        }
        if (groupSamples.size() > 0)
            groups.put(group.getGroupName().toString(), groupSamples);
    }

%>
<script>
    var groups = <%=new JSONObject(groups)%>;
</script>

<input type="hidden" name="existingKeywordRunId" id="existingKeywordRunId" value="<%=h(form.getExistingKeywordRunId())%>">
<input type="hidden" name="runFilePathRoot" id="runFilePathRoot" value="<%=h(form.getRunFilePathRoot())%>">
<input type="hidden" name="selectAnalysisEngine" id="selectAnalysisEngine" value="<%=h(form.getSelectAnalysisEngine())%>">

<p>Analysis engine options.
</p>
<hr/>

<p>Which samples should be imported?</p>
<div style="padding-left: 2em; padding-bottom: 1em;">
<%
if (protocol != null)
{
    if (protocol.getFCSAnalysisFilterString() != null)
    {
        %>
        Samples will be filtered by the current protocol <a href="<%=protocol.urlFor(ProtocolController.EditFCSAnalysisFilterAction.class)%>" target="_blank">FCS analysis filter</a>:
        <br>
        <div style="padding-left: 2em;">
            <%=protocol.getFCSAnalysisFilter().getFilterText()%>
        </div>
        <%
    }
    else
    {
        %>No protocol <a href="<%=protocol.urlFor(ProtocolController.EditFCSAnalysisFilterAction.class)%>" target="_blank">FCS analysis filter</a> has been defined in this folder.<%
    }
}
%>
    <p>
    <script>
        function onGroupChanged(selectedGroup)
        {
            var rEngineNormalizationReferenceSelect = document.getElementById("rEngineNormalizationReference");
            if (rEngineNormalizationReferenceSelect)
            {
                // Remove all but "<Select Sample>" option.
                var value = rEngineNormalizationReferenceSelect.value;
                while (rEngineNormalizationReferenceSelect.length > 1)
                    rEngineNormalizationReferenceSelect.remove(1);

                if (!selectedGroup)
                    selectedGroup = "All Samples";

                var group = groups[selectedGroup];
                if (group)
                {
                    for (var i = 0; i < group.length; i++)
                    {
                        var sample = group[i];
                        var opt = document.createElement("option");
                        opt.value = sample;
                        opt.text = sample;
                        if (value == sample)
                            opt.selected = true;
                        rEngineNormalizationReferenceSelect.add(opt, null);
                    }
                }
            }
        }
    </script>
    <label for="importGroupNames">Select a FlowJo group to import from the workspace.</label>
    <select id="importGroupNames" name="importGroupNames" onchange="onGroupChanged(this.value);">
        <labkey:options value="<%=form.getImportGroupNames()%>" set="<%=groups.keySet()%>" />
    </select>
</div>

<%
    if ("rEngine".equals(form.getSelectAnalysisEngine()))
    {
        JSONArray jsonParams = new JSONArray();
        for (String param : workspace.getParameters())
        {
            if (!"Time".equals(param) && !param.startsWith("FSC") && !param.startsWith("SSC"))
                jsonParams.put(new String[]{param, param});
        }
%>
<p>Normalization Options</p>
<script>
    function onNormalizationChange()
    {
        var disable = !document.getElementById("rEngineNormalization").checked;
        document.getElementById("rEngineNormalizationReference").disabled = disable;
        Ext.getCmp("rEngineNormalizationParameters").setDisabled(disable);
    }
</script>

<div style="padding-left: 2em; padding-bottom: 1em;">
    <input type="checkbox" name="rEngineNormalization" id="rEngineNormalization" <%=form.isrEngineNormalization() ? "checked" : ""%> onchange="onNormalizationChange();">
    <input type="hidden" name="<%=SpringActionController.FIELD_MARKER%>rEngineNormalization"/>
    <label for="rEngineNormalization">Perform normalization?</label>
</div>

<div style="padding-left: 2em; padding-bottom: 1em;">
    <label for="rEngineNormalizationReference">Select sample to be use as normalization reference.</label><br>
    <em>NOTE:</em> The list of available samples is restricted to those in the imported group above.<br>
    <select name="rEngineNormalizationReference" id="rEngineNormalizationReference">
        <option value="">&lt;Select sample&gt;</option>
        <%
            String rEngineNormalizationReference = form.getrEngineNormalizationReference();
            if (form.getImportGroupNames() != null && form.getImportGroupNames().length() > 0)
            {
                for (String group : groups.keySet())
                {
                    if (form.getImportGroupNames().contains(group))
                    {
                        Set<String> groupSamples = groups.get(group);
                        for (String sample : groupSamples)
                        {
        %><option value=<%=PageFlowUtil.filter(sample)%> <%=sample.equals(rEngineNormalizationReference) ? "selected" : ""%>><%=PageFlowUtil.filter(sample)%></option><%
                        }
                    }
                }
            }
        %>
    </select>
</div>

<div style="padding-left: 2em; padding-bottom: 1em;">
    <label for="rEngineNormalizationParameters">Select parameters to be normalized.  At least one parameter must be selected.</label>
    <div id="rEngineNormalizationParametersDiv"></div>
    <script>
        LABKEY.requiresScript('Ext.ux.form.LovCombo.js');
        LABKEY.requiresCss('Ext.ux.form.LovCombo.css');
    </script>
    <script>
        Ext.onReady(function () {
            var combo = new Ext.ux.form.LovCombo({
                id: "rEngineNormalizationParameters",
                renderTo: "rEngineNormalizationParametersDiv",
                value: <%=PageFlowUtil.jsString(form.getrEngineNormalizationParameters())%>,
                width: 275,
                triggerAction: "all",
                mode: "local",
                valueField: "myId",
                displayField: "displayText",
                allowBlank: false,
                store: new Ext.data.ArrayStore({
                    fields: ["myId", "displayText"],
                    data: <%=jsonParams%>
                })
            });
        });
    </script>
</div>

<p></p>
<%
    }
%>


