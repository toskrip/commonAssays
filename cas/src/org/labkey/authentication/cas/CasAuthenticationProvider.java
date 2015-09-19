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
package org.labkey.authentication.cas;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * Created by adam on 3/29/2015.
 */
public class CasAuthenticationProvider implements SSOAuthenticationProvider
{
    private static final CasAuthenticationProvider INSTANCE = new CasAuthenticationProvider();
    static final String NAME = "CAS";

    private final LinkFactory _linkFactory = new LinkFactory(this);

    private CasAuthenticationProvider()
    {
    }

    public static CasAuthenticationProvider getInstance()
    {
        return INSTANCE;
    }

    @Override
    public URLHelper getURL(String secret)
    {
        return CasManager.getInstance().getLoginURL();
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
    }

    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return CasController.getConfigureURL();
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Apereo Central Authentication Service (CAS)";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
    }

    @Override
    public void activate()
    {
    }

    @Override
    public void deactivate()
    {
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}