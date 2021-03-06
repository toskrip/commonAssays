/*
 * Copyright (c) 2019 LabKey Corporation
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

/* luminex-0.00-10.10.sql */

CREATE SCHEMA luminex;

CREATE TABLE luminex.Analyte
(
    RowId SERIAL NOT NULL,
    LSID LSIDType NOT NULL,
    Name VARCHAR(50) NOT NULL,
    DataId INT NOT NULL,
    FitProb REAL,
    ResVar REAL,
    RegressionType VARCHAR(100),
    StdCurve VARCHAR(255),
    MinStandardRecovery INT NOT NULL,
    MaxStandardRecovery INT NOT NULL,

    CONSTRAINT PK_Luminex_Analyte PRIMARY KEY (RowId),
    CONSTRAINT FK_LuminexAnalyte_DataId FOREIGN KEY (DataId) REFERENCES exp.Data(RowId)
);
CREATE INDEX IX_LuminexAnalyte_DataId ON luminex.Analyte (DataId);

CREATE TABLE luminex.DataRow
(
    RowId SERIAL NOT NULL,
    DataId INT NOT NULL,
    AnalyteId INT NOT NULL,
    Type VARCHAR(10),
    Well VARCHAR(50),
    Outlier INT,
    Description VARCHAR(50),
    FIString VARCHAR(20),
    FI REAL,
    FIOORIndicator VARCHAR(10),
    FIBackgroundString VARCHAR(20),
    FIBackground REAL,
    FIBackgroundOORIndicator VARCHAR(10),
    StdDevString VARCHAR(20),
    StdDev REAL,
    StdDevOORIndicator VARCHAR(10),
    ObsConcString VARCHAR(20),
    ObsConc REAL,
    ObsConcOORIndicator VARCHAR(10),
    ExpConc REAL,
    ObsOverExp REAL,
    ConcInRangeString VARCHAR(20),
    ConcInRange REAL,
    ConcInRangeOORIndicator VARCHAR(10),

    Dilution REAL,
    DataRowGroup VARCHAR(25),
    Ratio VARCHAR(25),
    SamplingErrors VARCHAR(25),
    PTID VARCHAR(32),
    VisitID FLOAT,
    Date TIMESTAMP,
    ExtraSpecimenInfo VARCHAR(50),
    SpecimenID VARCHAR(50),
    Container UniqueIdentifier NOT NULL,
    ProtocolID INT NOT NULL,
    BeadCount INT,

    CONSTRAINT PK_Luminex_DataRow PRIMARY KEY (RowId),
    CONSTRAINT FK_LuminexDataRow_DataId FOREIGN KEY (DataId) REFERENCES exp.Data(RowId),
    CONSTRAINT FK_LuminexDataRow_AnalyteId FOREIGN KEY (AnalyteId) REFERENCES luminex.analyte(RowId),
    CONSTRAINT FK_DataRow_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityID),
    CONSTRAINT FK_DataRow_ProtocolID FOREIGN KEY (ProtocolID) REFERENCES exp.Protocol (RowID)
);
CREATE INDEX IX_LuminexDataRow_DataId ON luminex.DataRow (DataId);
CREATE INDEX IX_LuminexDataRow_AnalyteId ON luminex.DataRow (AnalyteId);
CREATE INDEX IDX_DataRow_Container_ProtocolID ON luminex.datarow(Container, ProtocolID);

/* luminex-10.30-11.10.sql */

ALTER TABLE luminex.DataRow
  ALTER COLUMN fistring TYPE VARCHAR(64),
  ALTER COLUMN fibackgroundstring TYPE VARCHAR(64),
  ALTER COLUMN stddevstring TYPE VARCHAR(64),
  ALTER COLUMN obsconcstring TYPE VARCHAR(64),
  ALTER COLUMN concinrangestring TYPE VARCHAR(64);

/* luminex-11.10-11.20.sql */

ALTER TABLE luminex.datarow ADD COLUMN LSID LSIDtype;

UPDATE luminex.datarow SET LSID = 'urn:lsid:' || COALESCE ((SELECT p.value
FROM prop.properties p, prop.propertysets ps, core.containers c
WHERE
	p.name = 'defaultLsidAuthority' AND
	ps.set = p.set AND
	ps.category = 'SiteConfig' AND
	ps.objectid = c.entityid AND
	c.name IS NULL AND c.parent IS NULL), 'localhost') || ':LuminexDataRow:' || RowId;

ALTER TABLE luminex.datarow ALTER COLUMN LSID SET NOT NULL;

CREATE INDEX IX_LuminexDataRow_LSID ON luminex.Analyte (LSID);

CREATE TABLE luminex.Titration
(
    RowId SERIAL NOT NULL,
    RunId INT NOT NULL,
    Name VARCHAR(255) NOT NULL,
    Standard BOOLEAN NOT NULL,
    QCControl BOOLEAN NOT NULL,

    CONSTRAINT PK_Luminex_Titration PRIMARY KEY (RowId),
    CONSTRAINT FK_Luminex_Titration_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId),
    CONSTRAINT UQ_Titration UNIQUE (Name, RunId)
);

CREATE TABLE luminex.AnalyteTitration
(
    AnalyteId INT NOT NULL,
    TitrationId INT NOT NULL,

    CONSTRAINT PK_Luminex_AnalyteTitration PRIMARY KEY (AnalyteId, TitrationId)
);

CREATE INDEX IX_LuminexTitration_RunId ON luminex.Titration (RunId);

-- Assume a single titration for all existing data
INSERT INTO luminex.Titration
    (RunId, Name, Standard, QCControl)
    SELECT RowId, 'Standard', true, false FROM exp.experimentrun WHERE protocollsid LIKE '%:LuminexAssayProtocol.%';

-- Assign all existing analytes to the single titration created above
INSERT INTO luminex.AnalyteTitration
    (AnalyteId, TitrationId)
    SELECT a.RowId, t.RowId
        FROM luminex.analyte a, exp.data d, luminex.titration t, exp.protocolapplication pa
        WHERE a.dataid = d.rowid AND t.runid = pa.runid AND d.sourceapplicationid = pa.rowid;

ALTER TABLE luminex.datarow ADD COLUMN TitrationId INT;

ALTER TABLE luminex.datarow ADD CONSTRAINT FK_Luminex_DataRow_TitrationId FOREIGN KEY (TitrationId) REFERENCES luminex.Titration(RowId);

CREATE INDEX IX_LuminexDataRow_TitrationId ON luminex.DataRow (TitrationId);

-- Assume that any existing data that has an expected concentration is part of the single standard titration
-- for that run
UPDATE luminex.datarow SET titrationid =
    (SELECT t.rowid FROM luminex.titration t, exp.data d, exp.protocolapplication pa
        WHERE pa.runid = t.runid AND d.sourceapplicationid = pa.rowid AND dataid = d.rowid)
    WHERE expconc IS NOT NULL;

-- Use the description as the titration name, grabbing an arbitrary one if they're not all the same
UPDATE luminex.titration SET name = (SELECT COALESCE(MIN(description), 'Standard') FROM luminex.datarow WHERE luminex.titration.rowid = titrationid);

-- Add the Unknown type for a titration
ALTER TABLE luminex.Titration ADD COLUMN "unknown" BOOLEAN;

UPDATE luminex.Titration SET "unknown" = FALSE;

ALTER TABLE luminex.Titration ALTER COLUMN "unknown" SET NOT NULL;

-- Make sure we have a row in exp.Object for all Luminex data files so that we can migrate the Excel run properties to it
INSERT INTO exp.Object (ObjectURI, Container)
	SELECT d.LSID, d.Container
	FROM exp.Data d, exp.ExperimentRun r, exp.ProtocolApplication pa
	WHERE d.SourceApplicationId = pa.RowId and r.RowId = pa.RunId AND r.ProtocolLSID LIKE '%:LuminexAssayProtocol.Folder-%' AND d.LSID NOT IN (SELECT ObjectURI FROM exp.Object);

-- Clean up run field values that were orphaned when we failed to delete them as part of deleting a Luminex run
DELETE FROM exp.ObjectProperty WHERE ObjectId IN (SELECT o.ObjectId FROM exp.Object o LEFT OUTER JOIN exp.ExperimentRun r ON o.ObjectURI = r.LSID WHERE o.ObjectURI like '%:LuminexAssayRun.Folder-%' AND r.LSID IS NULL);

-- Migrate Excel-based values from run to data 
UPDATE exp.ObjectProperty SET ObjectId =
	(SELECT MIN(dataO.ObjectId) FROM exp.Object dataO, exp.Object runO, exp.Data d, exp.ExperimentRun r, exp.ProtocolApplication pa
	WHERE dataO.ObjectURI = d.LSID AND runO.ObjectURI = r.LSID AND runO.ObjectId = exp.ObjectProperty.ObjectId AND d.SourceApplicationId = pa.RowId AND pa.RunId = r.RowId AND r.ProtocolLSID LIKE '%:LuminexAssayProtocol.Folder-%')
WHERE PropertyId IN (SELECT pd.PropertyId FROM exp.PropertyDescriptor p, exp.DomainDescriptor d, exp.PropertyDomain pd WHERE p.PropertyId = pd.PropertyId AND d.domainuri LIKE '%:AssayDomain-ExcelRun.Folder-%' AND pd.DomainId = d.DomainId);

-- Add the Unknown type for a titration
ALTER TABLE luminex.DataRow ADD COLUMN WellRole VARCHAR(50);

UPDATE luminex.DataRow SET WellRole = 'Standard' WHERE Type ILIKE 'S%' OR Type ILIKE 'ES%';
UPDATE luminex.DataRow SET WellRole = 'Control' WHERE Type ILIKE 'C%';
UPDATE luminex.DataRow SET WellRole = 'Background' WHERE Type ILIKE 'B%';
UPDATE luminex.DataRow SET WellRole = 'Unknown' WHERE Type ILIKE 'U%';

CREATE TABLE luminex.WellExclusion
(
    RowId SERIAL NOT NULL,
    Description VARCHAR(50),
    Dilution REAL,
    DataId INT NOT NULL,
    Comment VARCHAR(2000),
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_Luminex_WellExclusion PRIMARY KEY (RowId),
    CONSTRAINT FK_LuminexWellExclusion_DataId FOREIGN KEY (DataId) REFERENCES exp.Data(RowId)
);

CREATE UNIQUE INDEX UQ_WellExclusion ON luminex.WellExclusion(Description, Dilution, DataId);

CREATE TABLE luminex.WellExclusionAnalyte
(
    AnalyteId INT,
    WellExclusionId INT NOT NULL,

    CONSTRAINT FK_LuminexWellExclusionAnalyte_AnalyteId FOREIGN KEY (AnalyteId) REFERENCES luminex.Analyte(RowId),
    CONSTRAINT FK_LuminexWellExclusionAnalyte_WellExclusionId FOREIGN KEY (WellExclusionId) REFERENCES luminex.WellExclusion(RowId)
);

CREATE UNIQUE INDEX UQ_WellExclusionAnalyte ON luminex.WellExclusionAnalyte(AnalyteId, WellExclusionId);

CREATE TABLE luminex.RunExclusion
(
    RunId INT NOT NULL,
    Comment VARCHAR(2000),
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    CONSTRAINT PK_Luminex_RunExclusion PRIMARY KEY (RunId),
    CONSTRAINT FK_LuminexRunExclusion_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId)
);

CREATE TABLE luminex.RunExclusionAnalyte
(
    AnalyteId INT,
    RunId INT NOT NULL,

    CONSTRAINT FK_LuminexRunExclusionAnalyte_AnalyteId FOREIGN KEY (AnalyteId) REFERENCES luminex.Analyte(RowId),
    CONSTRAINT FK_LuminexRunExclusionAnalyte_RunId FOREIGN KEY (RunId) REFERENCES luminex.RunExclusion(RunId)
);

CREATE UNIQUE INDEX UQ_RunExclusionAnalyte ON luminex.RunExclusionAnalyte(AnalyteId, RunId);

-- Don't allow AnalyteId to be NULL in the exclusion tables

ALTER TABLE luminex.WellExclusionAnalyte ALTER COLUMN AnalyteId SET NOT NULL;

DROP INDEX luminex.UQ_WellExclusionAnalyte;

ALTER TABLE luminex.WellExclusionAnalyte ADD CONSTRAINT PK_LuminexWellExclusionAnalyte PRIMARY KEY (AnalyteId, WellExclusionId);

CREATE INDEX IDX_LuminexWellExclusionAnalyte_WellExclusionID ON luminex.WellExclusionAnalyte(WellExclusionId);

ALTER TABLE luminex.RunExclusionAnalyte ALTER COLUMN AnalyteId SET NOT NULL;

DROP INDEX luminex.UQ_RunExclusionAnalyte;

ALTER TABLE luminex.RunExclusionAnalyte ADD CONSTRAINT PK_LuminexRunExclusionAnalyte PRIMARY KEY (AnalyteId, RunId);

CREATE INDEX IDX_LuminexRunExclusionAnalyte_RunID ON luminex.RunExclusionAnalyte(RunId);

/* luminex-11.20-11.30.sql */

CREATE TABLE luminex.CurveFit
(
	RowId serial NOT NULL,
	TitrationId INT NOT NULL,
	AnalyteId INT NOT NULL,
	CurveType VARCHAR(20) NOT NULL,
	MaxFI REAL NOT NULL,
	EC50 REAL NOT NULL,
	AUC REAL NOT NULL,

	CONSTRAINT PK_luminex_CurveFit PRIMARY KEY (rowid),
	CONSTRAINT FK_CurveFit_AnalyteIdTitrationId FOREIGN KEY (AnalyteId, TitrationId) REFERENCES luminex.AnalyteTitration (AnalyteId, TitrationId),
	CONSTRAINT UQ_CurveFit UNIQUE (AnalyteId, TitrationId, CurveType)
);

ALTER TABLE luminex.CurveFit ALTER COLUMN CurveType TYPE VARCHAR(30);

ALTER TABLE luminex.CurveFit ALTER COLUMN MaxFI DROP NOT NULL;
ALTER TABLE luminex.CurveFit ALTER COLUMN AUC DROP NOT NULL;
ALTER TABLE luminex.CurveFit ALTER COLUMN EC50 DROP NOT NULL;

-- Add the 4 and 5 PL curve fit values
ALTER TABLE luminex.CurveFit ADD COLUMN MinAsymptote REAL;
ALTER TABLE luminex.CurveFit ADD COLUMN MaxAsymptote REAL;
ALTER TABLE luminex.CurveFit ADD COLUMN Asymmetry REAL;
ALTER TABLE luminex.CurveFit ADD COLUMN Inflection REAL;
ALTER TABLE luminex.CurveFit ADD COLUMN Slope REAL;

-- Move MaxFI from CurveFit to Titration as it doesn't depend on the fit parameters
-- Don't bother to migrate values, no real data MaxFI has been stored in the CurveFit table yet
ALTER TABLE luminex.CurveFit DROP COLUMN MaxFI;
ALTER TABLE luminex.AnalyteTitration ADD COLUMN MaxFI REAL;

CREATE TABLE luminex.GuideSet
(
	RowId SERIAL NOT NULL,
    ProtocolId INT NOT NULL,
    AnalyteName VARCHAR(50) NOT NULL,
    CurrentGuideSet BOOLEAN NOT NULL,
    Conjugate VARCHAR(50),
    Isotype VARCHAR(50),
    MaxFIAverage REAL,
    MaxFIStdDev REAL,

	CONSTRAINT PK_luminex_GuideSet PRIMARY KEY (RowId),
	CONSTRAINT FK_luminex_GuideSet_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol(RowId)
);

CREATE INDEX IDX_GuideSet_ProtocolId ON luminex.GuideSet(ProtocolId);

CREATE TABLE luminex.GuideSetCurveFit
(
	GuideSetId INT NOT NULL,
	CurveType VARCHAR(30) NOT NULL,
    AUCAverage REAL,
    AUCStdDev REAL,
    EC50Average REAL,
    EC50StdDev REAL,

	CONSTRAINT PK_luminex_GuideSetCurveFit PRIMARY KEY (GuideSetId, CurveType)
);

ALTER TABLE luminex.Analyte ADD COLUMN GuideSetId INT;

ALTER TABLE luminex.Analyte ADD CONSTRAINT FK_Analyte_GuideSetId FOREIGN KEY (GuideSetId) REFERENCES luminex.GuideSet(RowId);

CREATE INDEX IDX_Analyte_GuideSetId ON luminex.Analyte(GuideSetId);

ALTER TABLE luminex.Analyte ADD COLUMN IncludeInGuideSetCalculation BOOLEAN;

UPDATE luminex.analyte SET IncludeInGuideSetCalculation = FALSE;

ALTER TABLE luminex.analyte ALTER COLUMN IncludeInGuideSetCalculation SET NOT NULL;

DROP TABLE luminex.GuideSetCurveFit;

ALTER TABLE luminex.GuideSet ADD COLUMN TitrationName VARCHAR(255);
ALTER TABLE luminex.GuideSet ADD COLUMN Comment TEXT;
ALTER TABLE luminex.GuideSet ADD COLUMN CreatedBy USERID;
ALTER TABLE luminex.GuideSet ADD COLUMN Created TIMESTAMP;
ALTER TABLE luminex.GuideSet ADD COLUMN ModifiedBy USERID;
ALTER TABLE luminex.GuideSet ADD COLUMN Modified TIMESTAMP;

ALTER TABLE luminex.AnalyteTitration ADD COLUMN GuideSetId INT;
ALTER TABLE luminex.AnalyteTitration ADD CONSTRAINT FK_AnalytTitration_GuideSetId FOREIGN KEY (GuideSetId) REFERENCES luminex.GuideSet(RowId);
CREATE INDEX IDX_AnalyteTitration_GuideSetId ON luminex.AnalyteTitration(GuideSetId);

ALTER TABLE luminex.AnalyteTitration ADD COLUMN IncludeInGuideSetCalculation BOOLEAN;
UPDATE luminex.AnalyteTitration SET IncludeInGuideSetCalculation = FALSE;
ALTER TABLE luminex.AnalyteTitration ALTER COLUMN IncludeInGuideSetCalculation SET NOT NULL;

ALTER TABLE luminex.Analyte DROP COLUMN GuideSetId;
ALTER TABLE luminex.Analyte DROP COLUMN IncludeInGuideSetCalculation;

ALTER TABLE luminex.GuideSet DROP COLUMN MaxFIAverage;
ALTER TABLE luminex.GuideSet DROP COLUMN MaxFIStdDev;

ALTER TABLE luminex.DataRow ADD COLUMN Summary Boolean;
ALTER TABLE luminex.DataRow ADD COLUMN CV REAL;

UPDATE luminex.DataRow SET Summary = TRUE WHERE POSITION(',' IN Well) > 0;
UPDATE luminex.DataRow SET Summary = FALSE WHERE Summary IS NULL;

ALTER TABLE luminex.DataRow ALTER COLUMN Summary SET NOT NULL;

-- Calculate the StdDev for any summary rows that have already been uploaded
UPDATE luminex.DataRow dr1 SET StdDev =
	(SELECT STDDEV(FI) FROM luminex.DataRow dr2 WHERE
		dr2.description = dr1.description AND
		((dr2.dilution IS NULL AND dr1.dilution IS NULL) OR dr1.dilution = dr2.dilution) AND
		dr1.dataid = dr2.dataid AND
		dr1.analyteid = dr2.analyteid AND
		((dr2.expConc IS NULL AND dr1.expConc IS NULL) OR dr1.expConc = dr2.expConc))
	WHERE StdDev IS NULL;

-- Calculate the %CV for any summary rows that have already been uploaded
UPDATE luminex.DataRow dr1 SET CV =
	StdDev / (SELECT AVG(FI) FROM luminex.DataRow dr2 WHERE
		dr2.description = dr1.description AND
		((dr2.dilution IS NULL AND dr1.dilution IS NULL) OR dr1.dilution = dr2.dilution) AND
		dr1.dataid = dr2.dataid AND
		dr1.analyteid = dr2.analyteid AND
		((dr2.expConc IS NULL AND dr1.expConc IS NULL) OR dr1.expConc = dr2.expConc))
	WHERE StdDev IS NOT NULL AND
	(SELECT AVG(FI) FROM luminex.DataRow dr2 WHERE
		dr2.description = dr1.description AND
		((dr2.dilution IS NULL AND dr1.dilution IS NULL) OR dr1.dilution = dr2.dilution) AND
		dr1.dataid = dr2.dataid AND
		dr1.analyteid = dr2.analyteid AND
		((dr2.expConc IS NULL AND dr1.expConc IS NULL) OR dr1.expConc = dr2.expConc)) != 0;

ALTER TABLE luminex.WellExclusion ADD COLUMN "type" VARCHAR(10);

-- Populate the WellExclusion Type column based on the value in the DataRow table for the given DataId/Description/Dilution
UPDATE luminex.wellexclusion we SET "type" =
	(SELECT types."type" FROM (SELECT dr.dataid, dr.dilution, dr.description, min(dr."type") AS "type"
			FROM luminex.datarow AS dr
			GROUP BY dr.dataid, dr.dilution, dr.description) AS types
		WHERE we.dataid = types.dataid
		AND ((we.dilution IS NULL AND types.dilution IS NULL) OR we.dilution = types.dilution)
		AND ((we.description IS NULL AND types.description IS NULL) OR we.description = types.description))
	WHERE "type" IS NULL;

DROP INDEX luminex.UQ_WellExclusion;

CREATE UNIQUE INDEX UQ_WellExclusion ON luminex.WellExclusion(Description, Type, DataId);	

ALTER TABLE luminex.wellexclusion DROP COLUMN dilution;

DROP INDEX luminex.ix_luminexdatarow_lsid;

CREATE UNIQUE INDEX UQ_Analyte_LSID ON luminex.Analyte(LSID);

/* luminex-11.30-12.10.sql */

/* NOTE: this change was added on the 11.3 branch after installers for 11.3 were posted;
   this script is for servers that haven't upgraded to 11.31 yet.  */
CREATE INDEX IX_LuminexDataRow_LSID ON luminex.DataRow (LSID);

ALTER TABLE luminex.Analyte ADD COLUMN PositivityThreshold INT;

ALTER TABLE luminex.CurveFit ADD COLUMN FailureFlag BOOLEAN;

/* luminex-13.20-13.30.sql */

CREATE TABLE luminex.SinglePointControl
(
    RowId SERIAL NOT NULL,
    RunId INT NOT NULL,
    Name VARCHAR(255) NOT NULL,

    CONSTRAINT PK_Luminex_SinglePointControl PRIMARY KEY (RowId),
    CONSTRAINT FK_Luminex_SinglePointControl_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun(RowId),
    CONSTRAINT UQ_SinglePointControl UNIQUE (Name, RunId)
);

ALTER TABLE luminex.GuideSet RENAME COLUMN TitrationName TO ControlName;

ALTER TABLE luminex.GuideSet ADD COLUMN Titration BOOLEAN;

UPDATE luminex.GuideSet SET Titration = TRUE;

ALTER TABLE luminex.GuideSet ALTER COLUMN Titration SET NOT NULL;

CREATE TABLE luminex.AnalyteSinglePointControl
(
    SinglePointControlId INT NOT NULL,
    AnalyteId INT NOT NULL,
    GuideSetId INT,
    IncludeInGuideSetCalculation BOOLEAN NOT NULL,

    CONSTRAINT PK_AnalyteSinglePointControl PRIMARY KEY (AnalyteId, SinglePointControlId),
    CONSTRAINT FK_AnalyteSinglePointControl_AnalyteId FOREIGN KEY (AnalyteId) REFERENCES luminex.Analyte (RowId),
    CONSTRAINT FK_AnalyteSinglePointControl_SinglePointControlId FOREIGN KEY (SinglePointControlId) REFERENCES luminex.SinglePointControl (RowId),
    CONSTRAINT FK_AnalyteSinglePointControl_GuideSetId FOREIGN KEY (GuideSetId) REFERENCES luminex.GuideSet (RowId)
);

CREATE INDEX IDX_AnalyteSinglePointControl_GuideSetId ON luminex.AnalyteSinglePointControl(GuideSetId);
CREATE INDEX IDX_AnalyteSinglePointControl_SinglePointControlId ON luminex.AnalyteSinglePointControl(SinglePointControlId);

ALTER TABLE luminex.GuideSet DROP COLUMN Titration;

/* luminex-14.10-14.20.sql */

ALTER TABLE luminex.GuideSet ADD COLUMN ValueBased BOOLEAN;
UPDATE luminex.GuideSet SET ValueBased = FALSE;
ALTER TABLE luminex.GuideSet ALTER COLUMN ValueBased SET NOT NULL;

ALTER TABLE luminex.GuideSet ADD COLUMN EC504PLAverage REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN EC504PLStdDev REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN EC505PLAverage REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN EC505PLStdDev REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN AUCAverage REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN AUCStdDev REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN MaxFIAverage REAL;
ALTER TABLE luminex.GuideSet ADD COLUMN MaxFIStdDev REAL;

ALTER TABLE luminex.GuideSet ALTER COLUMN EC504PLAverage TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC504PLStdDev TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC505PLAverage TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC505PLStdDev TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN AUCAverage TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN AUCStdDev TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN MaxFIAverage TYPE DOUBLE PRECISION;
ALTER TABLE luminex.GuideSet ALTER COLUMN MaxFIStdDev TYPE DOUBLE PRECISION;

-- Clear out empty strings that were imported instead of treating them as null values. See issue 19837.
-- OntologyManager doesn't bother storing NULL values, so just delete the rows from exp.ObjectProperty

DELETE FROM exp.objectproperty
WHERE
  PropertyId IN (
    SELECT
      PropertyId
    FROM
      exp.propertydescriptor
    WHERE
      PropertyURI LIKE '%:AssayDomain-ExcelRun.%' AND
      RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
  ) AND
  StringValue = '' AND
  MVIndicator IS NULL;

/* luminex-14.20-14.30.sql */

ALTER TABLE luminex.Titration ADD COLUMN OtherControl BOOLEAN;
 UPDATE luminex.Titration SET OtherControl=FALSE;
 ALTER TABLE luminex.Titration ALTER COLUMN OtherControl SET NOT NULL;

ALTER TABLE luminex.Analyte ADD COLUMN NegativeBead VARCHAR(50);

-- assume that any run/dataid that has a "Blank" analyte was using that as the Negative Control bead
UPDATE luminex.analyte SET NegativeBead = (
	SELECT x.Name FROM luminex.analyte AS a
	JOIN (SELECT DISTINCT DataId, Name FROM luminex.analyte WHERE Name LIKE 'Blank %') AS x ON a.DataId = x.DataId
	WHERE a.DataId IN (SELECT DataId FROM luminex.analyte WHERE Name LIKE 'Blank %') AND a.Name NOT LIKE 'Blank %'
	AND luminex.analyte.RowId = a.RowId)
WHERE NegativeBead IS NULL;

ALTER TABLE luminex.datarow ADD COLUMN SinglePointControlId INT;
ALTER TABLE luminex.datarow ADD CONSTRAINT FK_Luminex_DataRow_SinglePointControlId FOREIGN KEY (SinglePointControlId) REFERENCES luminex.SinglePointControl(RowId);
CREATE INDEX IX_LuminexDataRow_SinglePointControlId ON luminex.DataRow (SinglePointControlId);

UPDATE luminex.datarow SET SinglePointControlId =
  (SELECT s.rowid FROM luminex.singlepointcontrol s, exp.data d, exp.protocolapplication pa
    WHERE pa.runid = s.runid AND d.sourceapplicationid = pa.rowid AND dataid = d.rowid AND description = s.name)
WHERE SinglePointControlId IS NULL;

/* luminex-14.30-14.31.sql */

ALTER TABLE luminex.GuideSet ADD EC504PLEnabled BOOLEAN;
ALTER TABLE luminex.GuideSet ADD EC505PLEnabled BOOLEAN;
ALTER TABLE luminex.GuideSet ADD AUCEnabled BOOLEAN;
ALTER TABLE luminex.GuideSet ADD MaxFIEnabled BOOLEAN;
UPDATE luminex.GuideSet SET EC504PLEnabled=TRUE, EC505PLEnabled=TRUE, AUCEnabled=TRUE, MaxFIEnabled=TRUE;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC504PLEnabled SET NOT NULL;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC505PLEnabled SET NOT NULL;
ALTER TABLE luminex.GuideSet ALTER COLUMN AUCEnabled SET NOT NULL;
ALTER TABLE luminex.GuideSet ALTER COLUMN MaxFIEnabled SET NOT NULL;

/* luminex-14.31-14.32.sql */

ALTER TABLE luminex.GuideSet ALTER COLUMN EC504PLEnabled SET DEFAULT TRUE;
ALTER TABLE luminex.GuideSet ALTER COLUMN EC505PLEnabled SET DEFAULT TRUE;
ALTER TABLE luminex.GuideSet ALTER COLUMN AUCEnabled SET DEFAULT TRUE;
ALTER TABLE luminex.GuideSet ALTER COLUMN MaxFIEnabled SET DEFAULT TRUE;

/* luminex-14.32-14.33.sql */

CREATE INDEX IX_LuminexSinglePointControl_RunId ON luminex.SinglePointControl (RunId);
CREATE INDEX IDX_AnalyteSinglePointControl_AnalyteId ON luminex.AnalyteSinglePointControl(AnalyteId);

/* luminex-14.33-14.34.sql */

ALTER TABLE luminex.GuideSet ADD IsTitration BOOLEAN;
UPDATE luminex.GuideSet SET IsTitration =
  (
  /* Set the control type to single point control if the GuideSet is used in the AnalyteSinglePointControl table.
     Default to setting the control type to titration if not found. */
    CASE WHEN RowId IN (SELECT DISTINCT GuideSetId FROM luminex.AnalyteSinglePointControl WHERE GuideSetId IS NOT NULL) THEN FALSE ELSE TRUE END
  )
;
ALTER TABLE luminex.GuideSet ALTER COLUMN IsTitration SET NOT NULL;

/* luminex-16.20-16.30.sql */

ALTER TABLE luminex.Analyte ADD BeadNumber VARCHAR(50);

-- parse out the bead number from the analyte name
UPDATE luminex.Analyte
  SET BeadNumber =
    CASE WHEN STRPOS(REVERSE(RTRIM(Name)), ')') = 1 THEN
      TRIM(both FROM (SUBSTRING(Name FROM (LENGTH(Name) - STRPOS(REVERSE(Name), '(') + 2) FOR (STRPOS(REVERSE(Name), '(') - STRPOS(REVERSE(Name), ')') - 1))))
    ELSE Name END,
  Name =
    CASE WHEN STRPOS(REVERSE(RTRIM(Name)), ')') = 1 THEN
      SUBSTRING(Name, 1, LENGTH(Name) - STRPOS(REVERSE(Name), '('))
    ELSE Name END;

-- parse out the bead number from the guide set analyte name
UPDATE luminex.GuideSet SET AnalyteName =
CASE WHEN STRPOS(REVERSE(RTRIM(AnalyteName)), ')') = 1 THEN
  SUBSTRING(AnalyteName, 1, LENGTH(AnalyteName) - STRPOS(REVERSE(AnalyteName), '('))
ELSE AnalyteName END;

UPDATE luminex.GuideSet set CurrentGuideSet = false;
UPDATE luminex.GuideSet SET CurrentGuideSet = true WHERE rowId IN (SELECT MAX(rowId) FROM luminex.GuideSet GROUP BY analyteName, protocolId, conjugate, isotype, controlname);

-- need to parse the bead number off of the negative bead name and trim the analyte name
UPDATE luminex.Analyte SET Name = TRIM(both FROM Name),
  NegativeBead =
    CASE WHEN STRPOS(REVERSE(RTRIM(NegativeBead)), ')') = 1 THEN
      TRIM(both FROM SUBSTRING(NegativeBead, 1, LENGTH(NegativeBead) - STRPOS(REVERSE(NegativeBead), '(')))
    ELSE NegativeBead END;

-- trim the analyte name from the guide set table
UPDATE luminex.GuideSet SET AnalyteName = TRIM(both FROM AnalyteName);

/* luminex-16.30-17.10.sql */

ALTER TABLE luminex.WellExclusion ADD COLUMN Dilution REAL;

DROP INDEX luminex.UQ_WellExclusion;
CREATE UNIQUE INDEX UQ_WellExclusion ON luminex.WellExclusion(Description, Dilution, Type, DataId);