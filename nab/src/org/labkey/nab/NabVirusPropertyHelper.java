package org.labkey.nab;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.SamplePropertyHelper;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.PlateSamplePropertyHelper;
import org.labkey.api.view.InsertView;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 7/6/2014.
 */
public class NabVirusPropertyHelper extends PlateSamplePropertyHelper
{
    public NabVirusPropertyHelper(List<? extends DomainProperty> virusDomainProperties, PlateTemplate template)
    {
        super(virusDomainProperties, template, WellGroup.Type.VIRUS);
    }

    public Map<String, Map<DomainProperty, String>> getSampleProperties(HttpServletRequest request) throws ExperimentException
    {
        if (hasMultipleViruses())
        {
            return super.getSampleProperties(request);
        }
        else
        {
            // because of the optimization to display a single virus in the insert view, we need to
            // pull the posted values from the request instead of using the property helper
            String virusGroupName = (_sampleNames.size() == 1) ? _sampleNames.get(0) : null;

            Map<String, Map<DomainProperty, String>> result = new LinkedHashMap<>();
            Map<DomainProperty, String> sampleProperties = new HashMap<>();
            for (DomainProperty dp : getDomainProperties())
            {
                if (dp.isShownInInsertView())
                {
                    String inputName = UploadWizardAction.getInputName(dp, null);
                    sampleProperties.put(dp, request.getParameter(inputName));
                }
            }
            result.put(virusGroupName, sampleProperties);
            return result;
        }
    }

    public boolean hasMultipleViruses()
    {
        return _sampleNames.size() > 1;
    }

    @Override
    public void addSampleColumns(InsertView view, User user, @Nullable AssayRunUploadForm defaultValueContext, boolean errorReshow) throws ExperimentException
    {
        if (hasMultipleViruses())
            super.addSampleColumns(view, user, defaultValueContext, errorReshow);
        else
        {
            for (DomainProperty dp : getDomainProperties())
            {
                if (dp.isShownInInsertView())
                {
                    ColumnInfo info = dp.getPropertyDescriptor().createColumnInfo(ExperimentService.get().getTinfoExperimentRun(), "lsid", user, view.getViewContext().getContainer());
                    view.getDataRegion().addColumn(info);
                }
            }
        }
    }
}