<%@ page import="org.fhcrc.cpas.pipeline.PipelineQueue"%>
<%@ page import="org.fhcrc.cpas.pipeline.PipelineService"%>
<%@ page import="org.fhcrc.cpas.pipeline.PipelineJob"%>
<%@ page import="org.fhcrc.cpas.flow.script.ScriptJob"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="org.fhcrc.cpas.view.ViewURLHelper"%>
<%@ page extends="org.fhcrc.cpas.jsp.FormPage" %>
<%@ taglib prefix="cpas" uri="http://cpas.fhcrc.org/taglib/cpas" %>
<%
PipelineQueue.JobData data = PipelineService.get().getPipelineQueue().getJobData(null);
List<ScriptJob> jobs = new ArrayList();
for (PipelineJob job : data.getPendingJobs())
{
    if (job instanceof ScriptJob)
        jobs.add((ScriptJob) job);
}
for (PipelineJob job : data.getRunningJobs())
{
    if (job instanceof ScriptJob)
        jobs.add((ScriptJob) job);
}
%>
<% if (jobs.size() == 0) { %>
<p>There are no running or pending flow jobs.</p>
<% } else {
%>
<table>
    <tr><th>Status</th><th>Description</th><th>Owner</th></tr>
<% for (ScriptJob job : jobs) {
%>
    <tr><td><a href="<%=h(job.urlStatus())%>"><%=h(job.getStatusText())%></a></td>
        <td><%=h(job.getDescription())%></td>
        <td><%=h(String.valueOf(job.getUser()))%></td><td><cpas:button text="Cancel" href="<%=job.urlCancel()%>"/></tr>
<% } %>
</table>
<% } %>
<cpas:link href="<%=h(new ViewURLHelper("Pipeline-Status", "showList", getContainer()))%>" text="all jobs in this folder"/>

