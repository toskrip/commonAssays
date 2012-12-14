<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.flow.FlowModule" %>
<%@ page import="org.labkey.flow.controllers.executescript.ImportAnalysisForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ImportAnalysisForm form = (ImportAnalysisForm)getModelBean();
    ViewContext context = getViewContext();
    Container container = context.getContainer();
    PipelineService pipeService = PipelineService.get();
    PipeRoot pipeRoot = pipeService.findPipelineRoot(container);

    ActionURL cancelUrl = urlProvider(ProjectUrls.class).getStartURL(container);
    boolean hasPipelineRoot = pipeRoot != null;
    boolean canSetPipelineRoot = context.getUser().isAdministrator() && (pipeRoot == null || container.equals(pipeRoot.getContainer()));
%>

<p>You may either upload a FlowJo workspace from your local computer or browse the pipeline
    for a FlowJo workspace available to the server.  Be sure to save your FlowJo workspace
    as XML so <%=h(FlowModule.getShortProductName())%> can read it.
</p>
<hr/>
<input type="radio" name="selectWorkspace" id="uploadWorkspace" value="uploadWorkspace" />
<label for="uploadWorkspace">Upload file from your computer</label>
<div style="padding-left: 2em; padding-bottom: 1em;">
    <input type="file" id="workspace.file" name="workspace.file" onchange="selectUploadWorkspace();">
    <script type="text/javascript">
        function selectUploadWorkspace()
        {
            document.getElementById("uploadWorkspace").checked = true;
        }
    </script>
</div>
<input type="radio" name="selectWorkspace" id="browseWorkspace" value="browseWorkspace" />
<label for="browseWorkspace">Browse the pipeline</label>
<div style="padding-left: 2em; padding-bottom: 1em;">
    <% if (hasPipelineRoot) {
        String inputId = "workspace.path";
    %>
    You can browse the pipeline directories and find the analysis archive or <b>workspace XML</b> to import.<br/><br/>
    <%  if (!form.getWorkspace().getHiddenFields().containsKey("path")) { %>
    <input type="hidden" id="<%=text(inputId)%>" name="<%=text(inputId)%>" value=""/>
    <%  }  %>
    <div id="treeDiv" class="extContainer"></div>
    <script type="text/javascript">
        LABKEY.requiresScript("applet.js");
        LABKEY.requiresScript("fileBrowser.js");
        LABKEY.requiresScript("applet.js",true);
        LABKEY.requiresScript("FileUploadField.js");
    </script>
    <script type="text/javascript">
        var inputId=<%=q(inputId)%>;
        var fileSystem;
        var fileBrowser;
        function selectRecord(path)
        {
            Ext.get(inputId).dom.value=path;
            if (path)
            {
                document.getElementById("browseWorkspace").checked = true;
                document.getElementById("workspace.file").value = null;
            }
            // setTitle...
        }

        Ext.onReady(function()
        {
            Ext.QuickTips.init();

            fileSystem = new LABKEY.FileSystem.WebdavFileSystem({
                baseUrl:<%=q(pipeRoot.getWebdavURL())%>,
                rootName:<%=PageFlowUtil.jsString(AppProps.getInstance().getServerName())%>});

            fileBrowser = new LABKEY.ext.FileBrowser({
                fileSystem:fileSystem
                ,helpEl:null
                ,showAddressBar:false
                ,showFolderTree:true
                ,showDetails:false
                ,showFileUpload:false
                ,allowChangeDirectory:true
                ,tbarItems:[]
                ,fileFilter : {test: function(data){ return !data.file || endsWith(data.name,".xml") || endsWith(data.name, ".wsp") || endsWith(data.name, ".zip"); }}
            });

            fileBrowser.on(LABKEY.FileSystem.BROWSER_EVENTS.doubleclick, function(record){
                if (record && record.data.file)
                {
                    var path = record.data.path;
                    selectRecord(path);
                    document.forms["importAnalysis"].submit();
                }
                return true;
            });
            fileBrowser.on(LABKEY.FileSystem.BROWSER_EVENTS.selectionchange, function(record){
                var path = null;
                if (record && record.data.file)
                    path = record.data.path;
                selectRecord(path);
                return true;
            });

            fileBrowser.render('treeDiv');
            fileBrowser.start('/');
        });
    </script>
    <%
    } else {
    %><p><em>The pipeline root has not been set for this folder.</em><br>
    Once the pipeline root has been set, you can save the workspace to
    the pipeline file server and manage your workspace and FCS files
    from a central location.
</p><%
    if (canSetPipelineRoot) {
%><%=generateButton("Set pipeline root", urlProvider(PipelineUrls.class).urlSetup(container))%><%
} else {
%>Contact your administrator to set the pipeline root for this folder.<%
        }
    } %>
</div>
