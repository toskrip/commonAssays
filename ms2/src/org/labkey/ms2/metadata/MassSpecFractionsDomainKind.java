package org.labkey.ms2.metadata;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.AssayDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.api.SampleSetDomainKind;

import java.util.Set;

public class MassSpecFractionsDomainKind extends SampleSetDomainKind
{
    AssayDomainKind _assayDelegate;

    public MassSpecFractionsDomainKind()
    {
        super();
        _assayDelegate = new AssayDomainKind(MassSpecMetadataAssayProvider.FRACTION_DOMAIN_PREFIX)
        {
            @Override
            public String getKindName()
            {
                return null;
            }

            @Override
            public Set<String> getReservedPropertyNames(Domain domain)
            {
                return null;
            }
        };
    }

    public String getKindName()
    {
        return "Mass Spec Fractions Sample Set";
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        if (lsid.getNamespacePrefix().startsWith(MassSpecMetadataAssayProvider.FRACTION_DOMAIN_PREFIX))
            return Priority.MEDIUM;
        return null;
    }

    /*
     * AssayDomainKind delegating
     */

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        return _assayDelegate.urlShowData(domain, containerUser);
    }

    @Override
    public @Nullable ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        return _assayDelegate.urlEditDefinition(domain, containerUser);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return _assayDelegate.canEditDefinition(user, domain);
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return _assayDelegate.generateDomainURI(schemaName, queryName, container, user);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return _assayDelegate.canCreateDefinition(user, container);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return _assayDelegate.canDeleteDefinition(user, domain);
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return _assayDelegate.urlCreateDefinition(schemaName, queryName, container, user);
    }

    @Override
    public void appendNavTrail(NavTree root, Container c, User user)
    {
        _assayDelegate.appendNavTrail(root, c, user);
    }
}