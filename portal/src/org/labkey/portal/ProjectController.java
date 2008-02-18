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

package org.labkey.portal;

import org.apache.beehive.netui.pageflow.FormData;
import org.labkey.api.action.*;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.SearchResultsView;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;
import java.util.Map;


public class ProjectController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(ProjectController.class);

    public static class PipelineUrlsImp implements ProjectUrls
    {
        public ActionURL urlStart(Container container)
        {
            return new ActionURL(StartAction.class, container);
        }
    }


    public ProjectController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    ActionURL homeUrl()
    {
        return new ActionURL("Project", "begin", ContainerManager.HOME_PROJECT_PATH);
    }

    ActionURL projectUrl(String action)
    {
        return new ActionURL("Project", action, getContainer());

    }

    @RequiresPermission(ACL.PERM_NONE)
    public class StartAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();

            if (c.isProject())
            {
                // This block is to handle the case where a user does not have permissions
                // to a project folder, but does have access to a subfolder.  In this case, we'd like
                // to let them see something at the project root (since this is where you land after
                // selecting the project from the projects menu), so we display an access-denied
                // message within the frame.  If the user isn't logged on, we simply show an
                // access-denied error.  This is necessary to force the login prompt to show up
                // for users with access who simply haven't logged on yet.  (brittp, 5.4.2007)
                if (!c.hasPermission(getUser(), ACL.PERM_READ))
                {
                    if (getUser().isGuest())
                        HttpView.throwUnauthorized();
                    HtmlView htmlView = new HtmlView("You do not have permission to view this folder.<br>" +
                            "Please select another folder from the tree to the left.");
                    return htmlView;
                }
            }
            return HttpView.redirect(c.getStartURL(getViewContext()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(getContainer().getName());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            if (null == c)
            {
                HttpView.throwNotFound();
                return null;
            }
            
            boolean appendPath = true;
            String title;
            FolderType folderType = c.getFolderType();
            if (!FolderType.NONE.equals(c.getFolderType()))
            {
                title = folderType.getStartPageLabel(getViewContext());
            }
            else if (c.equals(ContainerManager.getHomeContainer()))
            {
                title = AppProps.getInstance().getSystemShortName();
                appendPath = false;
            }
            else
                title = c.getName();

            ActionURL url = getViewContext().getActionURL();
            if (null == url || url.getExtraPath().equals("/"))
                return HttpView.redirect(homeUrl());

            PageConfig page = getPageConfig();
            if (title != null)
                page.setTitle(title, appendPath);
            HttpView template = new HomeTemplate(getViewContext(), c, new VBox(), page, new NavTree[0]);

            Portal.populatePortalView(getViewContext(), c.getId(), template, getViewContext().getContextPath());
            
            getPageConfig().setTemplate(PageConfig.Template.None);
            return template;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
    

    /**
     * Same as begin, but used for our home page. We retain the action
     * for compatibility with old bookmarks, but redirect so doesn't show
     * up any more...
     */
    @RequiresPermission(ACL.PERM_NONE)
    public class HomeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(projectUrl("begin"));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class MoveWebPartAction extends FormViewAction<MovePortletForm>
    {
        public void validateCommand(MovePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(MovePortletForm movePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(movePortletForm,errors);
            return HttpView.redirect(getSuccessURL(movePortletForm));
        }

        public boolean handlePost(MovePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart[] parts = Portal.getParts(form.getPageId());
            if (null == parts)
                return true;

            //Find the portlet. Theoretically index should be 1-based & consecutive, but
            //code defensively.
            int i;
            int index = form.getIndex();
            for (i = 0; i < parts.length; i++)
                if (parts[i].getIndex() == index)
                    break;

            if (i == parts.length)
                return true;

            Portal.WebPart part = parts[i];
            String location = part.getLocation();
            if (form.getDirection() == Portal.MOVE_UP)
            {
                for (int j = i - 1; j >= 0; j--)
                {
                    if (location.equals(parts[j].getLocation()))
                    {
                        int newIndex = parts[j].getIndex();
                        part.setIndex(newIndex);
                        parts[j].setIndex(index);
                        break;
                    }
                }
            }
            else
            {
                for (int j = i + 1; j < parts.length; j++)
                {
                    if (location.equals(parts[j].getLocation()))
                    {
                        int newIndex = parts[j].getIndex();
                        part.setIndex(newIndex);
                        parts[j].setIndex(index);
                        break;
                    }
                }
            }

            Portal.saveParts(form.getPageId(), parts);
            return true;
        }

        public ActionURL getSuccessURL(MovePortletForm movePortletForm)
        {
            return projectUrl("begin");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AddWebPartAction extends FormViewAction<AddWebPartForm>
    {
        WebPartFactory _desc = null;
        Portal.WebPart _newPart = null;
        
        public void validateCommand(AddWebPartForm target, Errors errors)
        {
        }

        public ModelAndView getView(AddWebPartForm addWebPartForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(addWebPartForm,errors);
            return HttpView.redirect(getSuccessURL(addWebPartForm));
        }

        public boolean handlePost(AddWebPartForm form, BindException errors) throws Exception
        {
            _desc = Portal.getPortalPart(form.getName());
            if (null == _desc)
                return true;

            _newPart = Portal.addPart(getContainer(), _desc, form.getLocation());
            return true;
        }

        public ActionURL getSuccessURL(AddWebPartForm form)
        {
            if (null != _desc && _desc.isEditable() && _desc.showCustomizeOnInsert())
            {
                return projectUrl("customizeWebPart")
                        .addParameter("pageId", form.getPageId())
                        .addParameter("index", ""+_newPart.getIndex());
            }
            else
                return projectUrl("begin");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm customizePortletForm, boolean reshow, BindException errors) throws Exception
        {
            // UNDONE: this seems to be used a link, fix to make POST
            handlePost(customizePortletForm,errors);
            return HttpView.redirect(getSuccessURL(customizePortletForm));
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart[] parts = Portal.getParts(form.getPageId());
            //Changed on us..
            if (null == parts || parts.length == 0)
                return true;

            ArrayList<Portal.WebPart> newParts = new ArrayList<Portal.WebPart>();
            int index = form.getIndex();
            for (Portal.WebPart part : parts)
                if (part.getIndex() != index)
                    newParts.add(part);

            Portal.saveParts(form.getPageId(), newParts.toArray(new Portal.WebPart[0]));
            return true;
        }

        public ActionURL getSuccessURL(CustomizePortletForm customizePortletForm)
        {
            return projectUrl("begin");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin()
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            int rows = Portal.purge();
            return new HtmlView("deleted " + rows + " portlets<br>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class CustomizeWebPartView extends AbstractCustomizeWebPartView
    {
        /**
         * Create a default web part that allows users to edit properties
         * described with propertyDescriptors. Assumes that the
         * WebPart being editied will be set in a parameter called "webPart"
         *
         * @param propertyDescriptors
         */
        public CustomizeWebPartView(PropertyDescriptor[] propertyDescriptors)
        {
            super("/org/labkey/portal/customizeWebPart.gm");
            //Get nicer display names for default property names
            for (PropertyDescriptor desc : propertyDescriptors)
            {
                String displayName = desc.getDisplayName();
                if (Character.isLowerCase(displayName.charAt(0)) && displayName.equals(desc.getName()))
                    desc.setDisplayName(ColumnInfo.captionFromName(displayName));
            }
            addObject("propertyDescriptors", propertyDescriptors);
        }
    }

    //
    // FORMS
    //

    public static class CreateForm
    {
        private String name;


        public void setName(String name)
        {
            this.name = name;
        }


        public String getName()
        {
            return this.name;
        }
    }

    public static class AddWebPartForm extends CreateForm
    {
        private String pageId;
        private String location;

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }

        public String getLocation()
        {
            return location;
        }

        public void setLocation(String location)
        {
            this.location = location;
        }

    }

    public static class CustomizePortletForm extends ViewForm
    {
        private int index;
        private String pageId;

        public int getIndex()
        {
            return index;
        }

        public void setIndex(int index)
        {
            this.index = index;
        }

        public String getPageId()
        {
            return pageId;
        }

        public void setPageId(String pageId)
        {
            this.pageId = pageId;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class CustomizeWebPartAction extends FormViewAction<CustomizePortletForm>
    {
        Portal.WebPart _webPart;

        public void validateCommand(CustomizePortletForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomizePortletForm form, boolean reshow, BindException errors) throws Exception
        {
            _webPart = Portal.getPart(form.getPageId(), form.getIndex());
            if (null == _webPart)
                return HttpView.redirect(projectUrl("begin"));

            WebPartFactory desc = Portal.getPortalPart(_webPart.getName());
            assert (null != desc);
            if (null == desc)
                return HttpView.redirect(projectUrl("begin"));

            HttpView v = desc.getEditView(_webPart);
            assert(null != v);
            if (null == v)
                return HttpView.redirect(projectUrl("begin"));

            return v;
        }

        public boolean handlePost(CustomizePortletForm form, BindException errors) throws Exception
        {
            Portal.WebPart webPart = Portal.getPart(form.getPageId(), form.getIndex());
            populatePropertyMap(getViewContext().getRequest(), webPart);
            Portal.updatePart(getUser(), webPart);
            return true;
        }

        protected void populatePropertyMap(HttpServletRequest request, Portal.WebPart webPart)
        {
            // UNDONE: use getPropertyValues()
            Enumeration params = request.getParameterNames();

            webPart.getPropertyMap().clear();

            // TODO: Clean this up. Type checking... (though type conversion also must be done by the webpart)
            while (params.hasMoreElements())
            {
                String s = (String) params.nextElement();
                if (!"index".equals(s) && !"pageId".equals(s) && !"x".equals(s) && !"y".equals(s))
                {
                    String value = request.getParameter(s);
                    if (value != null && !"".equals(value.trim()))
                        webPart.getPropertyMap().put(s, value.trim());
                }
            }
        }

        public ActionURL getSuccessURL(CustomizePortletForm customizePortletForm)
        {
            return projectUrl("begin");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new BeginAction()).appendNavTrail(root)
                    .addChild("Customize " + _webPart.getName());
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class CustomizeSearchWebPartAction extends CustomizeWebPartAction
    {
        private static final String PARAM = "includeSubfolders";

        @Override
        protected void populatePropertyMap(HttpServletRequest request, Portal.WebPart webPart)
        {
            String value = request.getParameter(PARAM);     // This is the only param we care about right now -- need special handling for "uncheck" case
            if (null == value || "".equals(value.trim()))
                webPart.getPropertyMap().put(PARAM, "off");
            else
                webPart.getPropertyMap().put(PARAM, value);
        }
    }


    public static ActionURL getSearchUrl(Container c)
    {
        return new ActionURL("Project", "search", c);
    }


    @RequiresPermission(ACL.PERM_READ)
    public class SearchAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            String searchTerm = (String)getProperty("search", "");

            boolean includeSubfolders = "on".equals(getProperty("includeSubfolders", null));

            HttpView results = new SearchResultsView(c, Search.ALL_SEARCHABLES, searchTerm, getSearchUrl(c), getUser(), includeSubfolders, true);

            PageConfig config = getPageConfig();
            config.setFocus("forms[0].search");
            config.setTitle("Search Results");
            config.setHelpTopic(new HelpTopic("search", HelpTopic.Area.DEFAULT));
            return results;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class CustomizeSearchPartView extends AbstractCustomizeWebPartView<Object>
    {
        public CustomizeSearchPartView()
        {
            super("/org/labkey/portal/customizeSearchWebPart.gm");
        }


        @Override
        public void prepareWebPart(Object model) throws ServletException
        {
            super.prepareWebPart(model);
            Container c = getViewContext().getContainer(ACL.PERM_UPDATE);
            addObject("postURL", ActionURL.toPathString("Project", "customizeSearchWebPart", c.getPath()));  // TODO: Change to custom post action once subclassing of actions works
        }
    }


    public static class MovePortletForm extends CustomizePortletForm
    {
        private int direction;

        public int getDirection()
        {
            return direction;
        }

        public void setDirection(int direction)
        {
            this.direction = direction;
        }
    }

    public static class ButtonForm extends FormData
    {
        private String text;

        public String getText()
        {
            return text;
        }


        public void setText(String text)
        {
            this.text = text;
        }
    }



    @RequiresPermission(ACL.PERM_NONE)
    public static class ExpandCollapseAction extends SimpleViewAction<CollapseExpandForm>
    {
        public ModelAndView getView(CollapseExpandForm form, BindException errors) throws Exception
        {
            NavTreeManager.expandCollapsePath(getViewContext(), form.getTreeId(), form.getPath(), form.isCollapse());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class CollapseExpandForm
    {
        private boolean collapse;
        private String path;
        private String treeId;

        public boolean isCollapse()
        {
            return collapse;
        }

        public void setCollapse(boolean collapse)
        {
            this.collapse = collapse;
        }

        public String getTreeId()
        {
            return treeId;
        }

        public void setTreeId(String treeId)
        {
            this.treeId = treeId;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class GetWebPartAction extends ApiAction
    {
        public static final String PARAM_WEBPART = "webpart.name";

        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            String webPartName = request.getParameter(PARAM_WEBPART);

            if(null == webPartName || webPartName.isEmpty())
                throw new IllegalArgumentException("You must provide a value for the " + PARAM_WEBPART + " parameter!");

            WebPartFactory factory = Portal.getPortalPartCaseInsensitive(webPartName);
            Portal.WebPart part = factory.createWebPart();
            WebPartView view = factory.getWebPartViewSafe(getViewContext(), part);

            //view.prepare(view.getModelBean());

            view.render(request, getViewContext().getResponse());
            return null;
        }
    }
}
