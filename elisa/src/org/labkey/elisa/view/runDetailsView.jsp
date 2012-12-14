<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.reports.permissions.ShareReportPermission" %>
<%@ page import="org.labkey.api.util.ExtUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.elisa.ElisaController" %>
<%@ page import="com.google.gson.Gson" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ElisaController.GenericReportForm> me = (JspView<ElisaController.GenericReportForm>) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    ElisaController.GenericReportForm form = me.getModelBean();
    String numberFormat = PropertyManager.getProperties(ctx.getContainer(), "DefaultStudyFormatStrings").get("NumberFormatString");
    String numberFormatFn;
    if(numberFormat == null)
    {
        numberFormat = Formats.f1.toPattern();
    }
    numberFormatFn = ExtUtil.toExtNumberFormatFn(numberFormat);

    String renderId = "generic-report-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());

    ActionURL filterUrl = ctx.cloneActionURL().deleteParameters();
    filterUrl.addFilter(QueryView.DATAREGIONNAME_DEFAULT, FieldKey.fromParts("Run", "RowId"), CompareType.EQUAL, form.getRunId());
    ActionURL baseUrl = ctx.cloneActionURL().addParameter("filterUrl", filterUrl.getLocalURIString());
    Gson gson = new Gson();
%>

<script type="text/javascript">
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresExt4Sandbox(true);
    LABKEY.requiresScript("vis/genericChart/genericChartPanel.js");
    LABKEY.requiresVisualization();
    LABKEY.requiresScript("elisa/runDetailsPanel.js");
    LABKEY.requiresScript("elisa/runDataPanel.js");

</script>

<script type="text/javascript">
    Ext4.QuickTips.init();

    Ext4.onReady(function(){

        var items = [];

        items.push(Ext4.create('LABKEY.elisa.RunDetailsPanel', {

            schemaName      : <%=q(form.getSchemaName())%>,
            queryName       : <%=q(form.getQueryName())%>,
            runTableName    : <%=q(form.getRunTableName())%>,
            runId           : <%=form.getRunId()%>,
            dataRegionName  : <%=q(form.getDataRegionName())%>,
            baseUrl         : <%=q(baseUrl.getLocalURIString())%>
        }));

        items.push(Ext4.create('LABKEY.ext4.GenericChartPanel', {
            height          : 500,
            border          : true,
            schemaName      : <%=q(form.getSchemaName() != null ? form.getSchemaName() : null) %>,
            queryName       : <%=q(form.getQueryName() != null ? form.getQueryName() : null) %>,
            dataRegionName  : <%=q(form.getDataRegionName())%>,
            renderType      : <%=q(form.getRenderType())%>,
            id              : <%=q(form.getComponentId())%>,
            baseUrl         : <%=q(baseUrl.getLocalURIString())%>,
            allowShare      : <%=c.hasPermission(ctx.getUser(), ShareReportPermission.class)%>,
            isDeveloper     : <%=ctx.getUser().isDeveloper()%>,
            hideSave        : <%=ctx.getUser().isGuest()%>,
            autoColumnYName  : <%=q(form.getAutoColumnYName() != null ? form.getAutoColumnYName() : null)%>,
            autoColumnXName  : <%=q(form.getAutoColumnXName() != null ? form.getAutoColumnXName() : null)%>,
            defaultNumberFormat: eval(<%=q(numberFormatFn)%>),
            allowEditMode   : <%=!ctx.getUser().isGuest() && form.allowToggleMode()%>,
            curveFit        : {type : 'linear', min: 0, max: 100, points: 5, params : <%=text(gson.toJson(form.getFitParams()))%>}
        }));

        items.push(Ext4.create('LABKEY.elisa.RunDataPanel', {

            schemaName      : <%=q(form.getSchemaName())%>,
            queryName       : <%=q(form.getQueryName())%>
        }));

        var panel = Ext4.create('Ext.panel.Panel', {

            layout      : 'auto',
            border      : false,
            frame       : false,
            renderTo    : <%=q(renderId)%>,
            items       : items
        });

        var _resize = function(w,h) {
            LABKEY.Utils.resizeToViewport(panel, w, -1); // don't fit to height
        };

        Ext4.EventManager.onWindowResize(_resize);
    });

    function customizeGenericReport(elementId) {

        function initPanel() {
            var panel = Ext4.getCmp(elementId);

            if (panel) { panel.customize(); }
        }
        Ext4.onReady(initPanel);
    }

</script>

<div id="<%=h(renderId)%>" style="width:100%;"></div>
