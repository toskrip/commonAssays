/*
 * Copyright (c) 2014 LabKey Corporation
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

-- Issue 19548: Feature annotations in expression matrix are missing columns from GEOMicroarray
-- Fix typo: rename 'Req' -> 'Ref'
EXEC sp_rename 'microarray.FeatureAnnotation.ReqSeqProteinId', 'RefSeqProteinId', 'COLUMN';
EXEC sp_rename 'microarray.FeatureAnnotation.ReqSeqTranscriptId', 'RefSeqTranscriptId', 'COLUMN';
