package org.labkey.di.filters;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.steps.StepMeta;
import org.labkey.etl.xml.DeletedRowsSourceObjectType;

import java.util.List;

/**
 * User: tgaluhn
 * Date: 3/5/2015
 */
public abstract class FilterStrategyImpl implements FilterStrategy
{
    final TransformJobContext _context;
    final CopyConfig _config;
    final DeletedRowsSourceObjectType _deletedRowsSource;
    TableInfo _deletedRowsTinfo;
    String _deletedRowsKeyCol;
    String _targetDeletionKeyCol;
    boolean _isInit = false;

    public FilterStrategyImpl(StepMeta stepMeta, TransformJobContext context, DeletedRowsSourceObjectType deletedRowsSource)
    {
        if (!(stepMeta instanceof CopyConfig))
            throw new IllegalArgumentException(this.getClass().getName() + " is not compatible with " + stepMeta.getClass().getName());
        _config = (CopyConfig)stepMeta;
        _context = context;
        _deletedRowsSource = deletedRowsSource;
    }

    @Override
    public DeletedRowsSourceObjectType getDeletedRowsSource()
    {
        return _deletedRowsSource;
    }

    protected void init()
    {
        if (_isInit)
            return;
    }

    protected void initDeletedRowsSource()
    {
        if (null != _deletedRowsSource)
        {
            QuerySchema sourceSchema = DefaultSchema.get(_context.getUser(), _context.getContainer(), _deletedRowsSource.getSchemaName());
            if (null == sourceSchema)
                throw new IllegalArgumentException("Schema for deleted rows query not found: " + _deletedRowsSource.getSchemaName());

            _deletedRowsTinfo = sourceSchema.getTable(_deletedRowsSource.getQueryName());
            if (null == _deletedRowsTinfo)
                throw new IllegalArgumentException("Query for deleted rows not found: " + _deletedRowsSource.getQueryName());

            if (_deletedRowsSource.getDeletedSourceKeyColumnName() == null) // use the PK
            {
                List<String> delSrcPkCols = _deletedRowsTinfo.getPkColumnNames();
                if (delSrcPkCols.size() != 1)
                {
                    throw new IllegalArgumentException("Deleted rows query must either have exactly one primary key column, or the match column should be specified in the xml.");
                }
                _deletedRowsKeyCol = delSrcPkCols.get(0);
            }
            else
            {
                ColumnInfo deletedRowsCol = _deletedRowsTinfo.getColumn(FieldKey.fromParts(_deletedRowsSource.getDeletedSourceKeyColumnName()));
                if (null == deletedRowsCol)
                    throw new IllegalArgumentException("Match key for deleted rows not found: " + _deletedRowsSource.getQueryName() + "." + _deletedRowsSource.getDeletedSourceKeyColumnName());
                _deletedRowsKeyCol = deletedRowsCol.getColumnName();
            }
        }
    }

    @Override
    public TableInfo getDeletedRowsTinfo()
    {
        return _deletedRowsTinfo;
    }

    @Override
    public String getDeletedRowsKeyCol()
    {
        return _deletedRowsKeyCol;
    }

    @Override
    public String getTargetDeletionKeyCol()
    {
        return _targetDeletionKeyCol;
    }

    @Override
    public void setTargetDeletionKeyCol(String col)
    {
        _targetDeletionKeyCol = col;
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables)
    {
        return getFilter(variables, false);
    }

    @Override
    public SimpleFilter getFilter(VariableMap variables, boolean deleting)
    {
        throw new UnsupportedOperationException();
    }
}