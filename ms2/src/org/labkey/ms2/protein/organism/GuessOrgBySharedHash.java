/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.ms2.protein.organism;

import org.labkey.ms2.protein.ProteinManager;
import org.labkey.ms2.protein.ProteinPlus;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Table;

import java.sql.*;
import java.util.Map;
import java.util.HashMap;

/**
 * User: brittp
 * Date: Jan 2, 2006
 * Time: 3:42:03 PM
 */
public class GuessOrgBySharedHash extends Timer implements OrganismGuessStrategy
{
    private static final String CACHED_MISS_VALUE = "GuessOrgBySharedHash.CACHED_MISS_VALUE";
    private Map<String, String> _cache = new HashMap<String, String>();  // TODO: This could easily blow out all available memory for large FASTA; once we enable this guessing strategy, switch to Map with limit
    private static final DbSchema _schema = ProteinManager.getSchema();
    private static final String HASHCMD;

    static
    {
        StringBuilder sb = new StringBuilder("SELECT d.genus" + _schema.getSqlDialect().getConcatenationOperator() + "' '" + _schema.getSqlDialect().getConcatenationOperator() + "d.species " +
                " FROM " + ProteinManager.getTableInfoSequences() + " c JOIN " + ProteinManager.getTableInfoOrganisms() + " d ON (c.orgid=d.orgid)" +
                " WHERE c.orgid IN " +
                "   (SELECT a.orgid FROM " + ProteinManager.getTableInfoSequences() + " a JOIN " + ProteinManager.getTableInfoOrganisms() + " b ON (a.orgid=b.orgid) WHERE a.hash=?) " +
                " GROUP BY d.orgid,d.genus,d.species ORDER BY COUNT(*) DESC");
        _schema.getSqlDialect().limitRows(sb, 1);
        HASHCMD = sb.toString();
    }

    public String guess(ProteinPlus p) throws SQLException
    {
        startTimer();
        String retVal = _cache.get(p.getHash());
        if (CACHED_MISS_VALUE.equals(retVal))
            return null;
        if (retVal != null)
            return retVal;

        retVal = Table.executeSingleton(_schema, HASHCMD, new Object[]{p.getHash()}, String.class);
        _cache.put(p.getHash(), retVal != null ? retVal : CACHED_MISS_VALUE);
        stopTimer();
        return retVal;
    }

    public void close()
    {
    }
}
