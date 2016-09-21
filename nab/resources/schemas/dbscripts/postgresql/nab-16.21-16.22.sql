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

-- conditionally create the index, this is necessary because the index creation did not exist initially
-- in script: nab-16.20-16.21 but was added later to fix issue 27040
CREATE FUNCTION nab.ensureIndex() RETURNS void AS $$
BEGIN
  IF NOT EXISTS(SELECT * FROM pg_indexes WHERE SchemaName = 'nab' AND TableName = 'welldata' AND IndexName = 'idx_welldata_dilutiondataid') THEN
    EXECUTE 'CREATE INDEX IDX_WellData_DilutionDataId ON nab.wellData(dilutionDataId)';
  END IF;
END
$$ LANGUAGE plpgsql;

SELECT nab.ensureIndex();
DROP FUNCTION nab.ensureIndex();
