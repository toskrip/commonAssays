package org.labkey.flow.controllers.editscript;

import org.labkey.api.view.ViewForm;
import org.labkey.api.util.URIUtil;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.net.URI;

public class NewProtocolForm extends ViewForm
{
    public String ff_name;

    public void reset(ActionMapping actionMapping, HttpServletRequest servletRequest)
    {
        super.reset(actionMapping, servletRequest);
    }

    public void setFf_name(String name)
    {
        ff_name = name;
    }

    public List<String> getTemplateNames(String suffix) throws Exception
    {
        Set<String> uris = getRequest().getSession().getServletContext().getResourcePaths("/Flow/templates/");
        List<String> ret = new ArrayList();
        for (String uri : uris)
        {
            String name = URIUtil.getFilename(new URI(uri));
            if (name.endsWith(suffix))
                ret.add(name);
        }
        return ret;
    }
}
