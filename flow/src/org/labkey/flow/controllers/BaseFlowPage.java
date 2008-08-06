/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.flow.controllers;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.settings.AppProps;

import java.util.List;
import java.util.ArrayList;

abstract public class BaseFlowPage extends JspBase
{
    List _errors = new ArrayList();

    public void addError(String error)
    {
        _errors.add(error);
    }

    public List getErrors()
    {
        return _errors;
    }

    public boolean anyErrors()
    {
        return _errors.size() != 0;
    }

    public String formatErrors()
    {
        return formatErrors("Errors were encountered:");
    }

    public String formatErrors(String message)
    {
        if (_errors.size() == 0)
            return "";
        StringBuilder ret = new StringBuilder();
        ret.append("<p class=\"labkey-error\">");
        ret.append(message + "<br>");
        for (Object error : _errors)
        {
            ret.append(h(error.toString()));
            ret.append("<br>");
        }
        ret.append("</p>");
        return ret.toString();
    }
    public String resourceURL(String name)
    {
        return AppProps.getInstance().getContextPath() + "/Flow/" + name;
    }
}
