package org.labkey.ms1;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSchema;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Provides a customized experiment run grid with features specific to MS1 runs.
 */
public class MS1Schema extends UserSchema
{
    public static final String SCHEMA_NAME = "ms1";
    public static final String MSINSPECT_FEATURE_EXPERIMENT_RUNS_TABLE_NAME = "MSInspectFeatureRuns";

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new MS1Schema(schema.getUser(), schema.getContainer());
            }
        });
    }

    private ExpSchema _expSchema;

    public MS1Schema(User user, Container container)
    {
        super(SCHEMA_NAME, user, container, ExperimentService.get().getSchema());
        _expSchema = new ExpSchema(user, container);
    }
    
    public Set<String> getTableNames()
    {
        // Define a single table in this schema, for msInspect runs
        return PageFlowUtil.set(MSINSPECT_FEATURE_EXPERIMENT_RUNS_TABLE_NAME);
    }

    public TableInfo getTable(String name, String alias)
    {
        if (MSINSPECT_FEATURE_EXPERIMENT_RUNS_TABLE_NAME.equalsIgnoreCase(name))
        {
            // Start with a standard experiment run table
            ExpRunTable result = _expSchema.createRunsTable(alias);

            // Filter to just the runs with the MS1 protocol
            result.setProtocolPatterns("urn:lsid:%:Protocol.%:MS1.msInspectFeatureFindingAnalysis%");

            List<FieldKey> columns = new ArrayList(result.getDefaultVisibleColumns());
            columns.add(FieldKey.fromParts("Input", "msInspectDefFile"));
            columns.add(FieldKey.fromParts("Input", "mzXMLFile"));
            result.setDefaultVisibleColumns(columns);
            return result;
        }
        return super.getTable(name, alias);
    }
}
