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
<%@ page import="org.labkey.flow.controllers.protocol.ProtocolController"%>
<%@ page import="org.labkey.flow.controllers.protocol.ProtocolController.Action"%>
<%@ page import="org.labkey.flow.data.FlowProtocol" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.flow.data.FlowFCSFile" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.flow.persist.FlowManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.flow.query.FlowTableType" %>
<%@ page import="org.labkey.api.query.QueryAction" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    ProtocolController.ShowSamplesForm form = (ProtocolController.ShowSamplesForm) __form;
    FlowProtocol protocol = form.getProtocol();
    ExpSampleSet ss = protocol.getSampleSet();
    ExpMaterial[] samples = ss == null ? null : ss.getSamples();
    boolean unlinkedOnly = form.isUnlinkedOnly();
    int unlinkedCount = protocol.getUnlinkedSampleCount(samples);
    int fcsFilesWithSamplesCount = FlowManager.get().getFCSFileSamplesCount(getUser(), getContainer(), true);
    int fcsFilesWithoutSamplesCount = FlowManager.get().getFCSFileSamplesCount(getUser(), getContainer(), false);

    ActionURL urlFcsFilesWithSamples = FlowTableType.FCSFiles.urlFor(getUser(), getContainer(), QueryAction.executeQuery)
            .addParameter("query.Sample/Name~isnonblank", "");

    ActionURL urlFcsFilesWithoutSamples = FlowTableType.FCSFiles.urlFor(getUser(), getContainer(), QueryAction.executeQuery)
            .addParameter("query.Sample/Name~isblank", "");
%>
<% if (protocol.getSampleSet() == null || samples == null || samples.length == 0) { %>
    No samples have been uploaded in this folder.<br>
    <labkey:link href="<%=protocol.urlUploadSamples(false)%>" text="Upload samples from a spreadsheet" /><br>
<% } else { %>
<p>
There are <a href="<%=h(protocol.urlShowSamples(false))%>"><%=samples.length%> sample descriptions</a> in this folder,
of which <a href="<%=h(protocol.urlShowSamples(true))%>"><%=unlinkedCount%> are not joined</a> to any FCS Files.
</p>
<p>
<a href="<%=h(urlFcsFilesWithSamples)%>"><%=fcsFilesWithSamplesCount%> FCS Files</a> are have been joined with a sample and
<a href="<%=h(urlFcsFilesWithoutSamples)%>"><%=fcsFilesWithoutSamplesCount%> FCS Files</a> are not joined with any samples.
</p>

<p><% if (unlinkedOnly) { %><b>Showing Unlinked Samples</b><% } %>
<table class="labkey-data-region labkey-show-borders">
    <thead>
    <tr>
        <td class="labkey-column-header">Sample Name</td>
        <td class="labkey-column-header">FCS Files</td>
    </tr>
    </thead>
    <%
        for (int i = 0; i < samples.length; i++)
        {
            ExpMaterial sample = samples[i];
            List<FlowFCSFile> fcsFiles = FlowProtocol.getFCSFiles(sample);
            if (unlinkedOnly && fcsFiles.size() > 0)
                continue;

            %>
            <tr class="<%=i%2==0 ? "labkey-alternate-row":"labkey-row"%>">
            <td valign="top"><a href="<%=h(sample.detailsURL())%>"><%= sample.getName()%></a></td>
            <td>
                <% if (fcsFiles.size() > 0) { %>
                    <% for (FlowFCSFile fcsFile : fcsFiles) { %>
                        <a href="<%=h(fcsFile.urlShow())%>"><%=fcsFile.getName()%></a><br>
                    <% } %>
                <% } else { %>
                    <em>unlinked</em>
                <% } %>
            </td>
            </tr>
            <%
        }
    %>
</table>
</p>

<p>
    <labkey:link href="<%=ss.detailsURL()%>" text="Show sample set"/><br>
    <labkey:link href="<%=protocol.urlUploadSamples(true)%>" text="Upload more samples from a spreadsheet" /><br>
    <% if (protocol.getSampleSetJoinFields().size() != 0) { %>
        <labkey:link href="<%=protocol.urlFor(Action.joinSampleSet)%>" text="Modify sample join fields" /><br>
    <% } else { %>
        <labkey:link href="<%=protocol.urlFor(Action.joinSampleSet)%>" text="Join samples to FCS File Data" /><br>
    <% } %>
</p>
<% } %>

