/*
 * Copyright (c) 2015 LabKey Corporation
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

package org.labkey.saml;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.WebPartFactory;
import org.labkey.authentication.saml.SamlController;
import org.labkey.authentication.saml.SamlProvider;

import java.util.Collection;
import java.util.Collections;

public class SamlModule extends DefaultModule
{
    public static final String NAME = "SAML";
    public static final String EXPERIMENTAL_SAML_SERVICE_PROVIDER = "experimental-saml-sp";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController("saml", SamlController.class);

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_SAML_SERVICE_PROVIDER, "Login using SAML", "Authenticate using a SAML Identity Provider.", true);
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_SAML_SERVICE_PROVIDER))
            AuthenticationManager.registerProvider(new SamlProvider(), AuthenticationManager.Priority.Low);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
    }
}