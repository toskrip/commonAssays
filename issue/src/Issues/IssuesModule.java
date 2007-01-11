/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package Issues;

import Issues.model.IssueManager;
import Issues.query.IssuesQuerySchema;
import junit.framework.TestCase;
import org.apache.commons.collections.MultiMap;
//import org.apache.log4j.Logger;
import org.fhcrc.cpas.data.Container;
import org.fhcrc.cpas.data.ContainerManager;
import org.fhcrc.cpas.data.DbSchema;
import org.fhcrc.cpas.module.Module;
import org.fhcrc.cpas.module.DefaultModule;
import org.fhcrc.cpas.module.ModuleContext;
import org.fhcrc.cpas.util.Search;
import org.fhcrc.cpas.util.PageFlowUtil;
import org.fhcrc.cpas.view.*;
import org.fhcrc.cpas.security.SecurityManager;
import org.fhcrc.cpas.security.User;
import org.fhcrc.cpas.security.Group;
import org.fhcrc.cpas.security.UserPrincipal;
import org.fhcrc.cpas.issues.IssuesSchema;

import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.*;
import java.lang.reflect.InvocationTargetException;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:48:21 PM
 */
public class IssuesModule extends DefaultModule implements ContainerManager.ContainerListener, SecurityManager.GroupListener, Search.Searchable
{
//    private static final Logger _log = Logger.getLogger(IssuesModule.class);

    public static final String NAME = "Issues";

    public IssuesModule()
    {
        super(NAME, 1.70, "/Issues", new IssuesWebPartFactory());
        addController("Issues", IssuesController.class);

        IssuesQuerySchema.register();
    }

    static class IssuesWebPartFactory extends WebPartFactory
    {
        public IssuesWebPartFactory()
        {
            this(NAME, null);
        }

        public IssuesWebPartFactory(String name, String location)
        {
            super(name, location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            WebPartView v = new IssuesController.SummaryWebPart();
            populateProperties(v, webPart.getPropertyMap());
            return v;
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            WebPartView v = new IssuesController.CustomizeIssuesPartView();
            v.addObject("webPart", webPart);
            return v;
        }

    }



    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        ContainerManager.addContainerListener(this);
        SecurityManager.addGroupListener(this);

        Search.register(this);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c)
    {
        IssueManager.purgeContainer(c);
    }

    public void propertyChange(PropertyChangeEvent event)
    {
    }

    public void principalAddedToGroup(Group g, UserPrincipal user)
    {
        Container c = ContainerManager.getForId(g.getContainer());
        IssueManager.uncache(c, c.getProject().getName() + "AssignedTo");
    }

    public void principalDeletedFromGroup(Group g, UserPrincipal user)
    {
        Container c = ContainerManager.getForId(g.getContainer());
        IssueManager.uncache(c, c.getProject().getName() + "AssignedTo");
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        try
        {
            long count = IssueManager.getIssueCount(c);
            if (count > 0)
                list.add("" + count + " Issue" + (count > 1 ? "s" : ""));
        }
        catch (SQLException x)
        {
            list.add(x.getMessage());
        }
        return list;
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    public ViewURLHelper getTabURL(HttpServletRequest request, Container c, User user)
    {
        ViewURLHelper url = new ViewURLHelper(request, getName(), "list", c == null ? null : c.getPath());
        url.addParameter(".lastFilter", "true");
        return url;
    }

    public MultiMap search(Set<Container> containers, String csvContainerIds, String searchTerm)
    {
        return IssueManager.search(containers, csvContainerIds, searchTerm);
    }


    public String getSearchResultName()
    {
        return "Issue";
    }


    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            Issues.IssuesController.TestCase.class,
            Issues.model.IssueManager.TestCase.class ));
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchema());
    }

    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Wiki");
        return result;
    }
}
