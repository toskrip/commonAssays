<%@ page extends="announcements.EmailResponsePage" %>

<%=responseAnnouncement.getCreatedByName() + (responseAnnouncement.getParent() != null ? " responded" : " created a new " + settings.getConversationName().toLowerCase()) %>
<%=formatDateTime(responseAnnouncement.getCreated())%><%

if (null != responseBody)
{  %>
    <%=responseBody%><%
}  %>
View this <%=settings.getConversationName().toLowerCase()%> at this URL: <%=threadURL%>

You have received this email because <%
    switch(reason)
    {
        case broadcast:
%>a site administrator sent this notification to all users of <%=siteURL%>.<%
        break;

        case signedUp:
%>you are signed up to receive notifications about new posts to <%=boardPath%> at <%=siteURL%>.
If you no longer wish to receive these notifications, please change your email preferences by
navigating to this URL: <%=removeUrl%>.<%
        break;

        case userList:
%>you are on the Members list for this <%=settings.getConversationName().toLowerCase()%>.  If you no longer wish to receive these notifications,
please navigate to this URL: <%=removeUrl%> to remove yourself from this <%=settings.getConversationName().toLowerCase()%>.<%
        break;
    }
%>
