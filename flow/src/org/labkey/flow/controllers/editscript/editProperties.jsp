<%
/*
 * Copyright (c) 2007-2019 LabKey Corporation
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
<%@ page import="org.labkey.flow.controllers.editscript.EditPropertiesForm"%>
<%@ page import="org.labkey.flow.controllers.editscript.ScriptController" %>
<%@ page extends="org.labkey.flow.controllers.editscript.ScriptController.Page" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<% EditPropertiesForm form = (EditPropertiesForm) getForm(); %>
<labkey:errors/>
<labkey:form action="<%=form.urlFor(ScriptController.EditPropertiesAction.class)%>" method="POST">
    <p>Description:<br>
        <textarea rows="5" cols="40" name="ff_description"><%=h(form.ff_description)%></textarea>
    </p>
    <labkey:button text="Update" /> <labkey:button text="Cancel" href="<%=form.urlFor(ScriptController.BeginAction.class)%>" />
</labkey:form>