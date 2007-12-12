/*
 * Copyright (c) 2005 LabKey Software, LLC
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
package org.labkey.ms2.pipeline;

import org.labkey.api.pipeline.PipelineProviderCluster;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.ACL;
import org.labkey.api.util.AppProps;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewURLHelper;
import org.labkey.api.view.WebPartView;
import org.labkey.ms2.protocol.MS2SearchPipelineProtocol;
import org.labkey.ms2.protocol.MascotSearchProtocol;
import org.labkey.ms2.protocol.MascotSearchProtocolFactory;
import org.labkey.ms2.protocol.AbstractMS2SearchProtocolFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * MascotCPipelineProvider class
 * <p/>
 * Created: Nov 1, 2005
 *
 * @author bmaclean
 */
public class MascotCPipelineProvider extends PipelineProviderCluster  implements MS2SearchPipelineProvider
{
    public static String name = "Mascot";

    public MascotCPipelineProvider()
    {
        super(name);
    }

    public boolean isStatusViewableFile(String name, String basename)
    {
        if ("mascot.xml".equals(name))
            return true;

        return super.isStatusViewableFile(name, basename);
    }

    public void updateFileProperties(ViewContext context, List<FileEntry> entries)
    {
        if (!AppProps.getInstance().hasMascotServer())
            return;

        for (ListIterator<FileEntry> it = entries.listIterator(); it.hasNext();)
        {
            FileEntry entry = it.next();
            if (!entry.isDirectory())
            {
                continue;
            }

            addAction("MS2-Pipeline", "searchMascot", "Mascot Peptide Search",
                    entry, entry.listFiles(MS2PipelineManager.getAnalyzeFilter()));
        }
    }

    public HttpView getSetupWebPart()
    {
        if (!AppProps.getInstance().hasMascotServer())
            return null;
        if (AppProps.getInstance().hasPipelineCluster())
        {
            // No extra setup for cluster.
            return null;
        }
        return new SetupWebPart();
    }

    private static class SetupWebPart extends WebPartView
    {
        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            ViewContext context = getViewContext();
            if (!context.hasPermission(ACL.PERM_INSERT))
                return;
            StringBuilder html = new StringBuilder();
            if (!AppProps.getInstance().hasPipelineCluster())
            {
                html.append("<table><tr><td class=\"normal\" style=\"font-weight:bold;\">Mascot specific settings:</td></tr>");
                ViewURLHelper setDefaultsURL = context.getContainer().urlFor(PipelineController.SetMascotDefaultsAction.class);
                html.append("<tr><td class=\"normal\">&nbsp;&nbsp;&nbsp;&nbsp;")
                        .append("<a href=\"").append(setDefaultsURL.getLocalURIString()).append("\">Set defaults</a>")
                        .append(" - Specify the default XML parameters file for Mascot.</td></tr></table>");
            }
            out.write(html.toString());
        }
    }

    public AbstractMS2SearchProtocolFactory getProtocolFactory()
    {
        return MascotSearchProtocolFactory.get();
    }

    public Map<String, String[]> getSequenceFiles(URI sequenceRoot) throws IOException
    {
        AppProps appProps = AppProps.getInstance();
        if (!appProps.hasMascotServer())
            throw new IOException("Mascot server has not been specified in site customization.");

        MascotClientImpl mascotClient = new MascotClientImpl(appProps.getMascotServer(), null);
        mascotClient.setProxyURL(appProps.getMascotHTTPProxy());
        Map<String, String[]> sequenceDBs = mascotClient.getSequenceDBNames();

        if (0 == sequenceDBs.size())
        {
            // TODO: Would be nice if the Mascot client just threw its own connectivity exception.
            String connectivityResult = mascotClient.testConnectivity(false);
            if (!"".equals(connectivityResult))
                throw new IOException(connectivityResult);
        }

        return sequenceDBs;
    }

    public String getHelpTopic()
    {
        return "pipelineMascot";
    }

    public void ensureEnabled() throws PipelineValidationException
    {
        AppProps appProps = AppProps.getInstance();
        String mascotServer = appProps.getMascotServer();
        if ((!appProps.hasMascotServer() || 0==mascotServer.length()))
            throw new PipelineValidationException("Mascot server has not been specified in site customization.");
    }
}
