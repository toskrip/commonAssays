-- Previous to CPAS 1.5, some runs ended up with PeptideCount = 0 & SpectrumCount = 0; this corrects those runs.
-- Use old names here to allow running this easily on 1.6 installations.
UPDATE ms2.MS2Runs SET
    PeptideCount = (SELECT COUNT(*) AS PepCount FROM ms2.MS2Peptides pep WHERE pep.run = ms2.MS2Runs.run),
    SpectrumCount = (SELECT COUNT(*) AS SpecCount FROM ms2.MS2Spectra spec WHERE spec.run = ms2.MS2Runs.run)
WHERE (PeptideCount = 0)
GO

-- Index to speed up deletes from MS2PeptidesData.  Use old names here to allow running this on 1.6 installations.
IF NOT EXISTS (SELECT * FROM dbo.sysindexes WHERE name = 'IX_MS2PeptideMemberships_PeptideId' AND id = object_id('ms2.MS2PeptideMemberships'))
    CREATE INDEX IX_MS2PeptideMemberships_PeptideId ON ms2.MS2PeptideMemberships(PeptideId)
GO

-- Simplify MS2 table names
EXEC sp_rename 'ms2.MS2Fractions', 'Fractions'
EXEC sp_rename 'ms2.MS2History', 'History'
EXEC sp_rename 'ms2.MS2Modifications', 'Modifications'
EXEC sp_rename 'ms2.MS2PeptideMemberships', 'PeptideMemberships'
EXEC sp_rename 'ms2.MS2PeptidesData', 'PeptidesData'
EXEC sp_rename 'ms2.MS2ProteinGroupMemberships', 'ProteinGroupMemberships'
EXEC sp_rename 'ms2.MS2ProteinGroups', 'ProteinGroups'
EXEC sp_rename 'ms2.MS2ProteinProphetFiles', 'ProteinProphetFiles'
EXEC sp_rename 'ms2.MS2Runs', 'Runs'
EXEC sp_rename 'ms2.MS2SpectraData', 'SpectraData'
GO

-- Rebuild all views to point to the right table names
DROP VIEW ms2.MS2Spectra
DROP VIEW ms2.MS2ExperimentRuns
DROP VIEW ms2.MS2Peptides
DROP VIEW ms2.MS2SimplePeptides
DROP VIEW prot.FastaAdmin
DROP VIEW ms2.ProteinGroupsWithQuantitation
GO

CREATE VIEW ms2.Spectra AS
	SELECT f.Run AS Run, sd.*
	FROM ms2.SpectraData sd INNER JOIN
	ms2.Fractions f ON sd.Fraction = f.Fraction
GO

CREATE VIEW ms2.ExperimentRuns AS
    SELECT ms2.Runs.*, exp.ExperimentRun.RowId AS ExperimentRunRowId, exp.Protocol.Name AS ProtocolName
    FROM ms2.Runs
        LEFT OUTER JOIN exp.ExperimentRun ON exp.ExperimentRun.LSID=ms2.Runs.ExperimentRunLSID
        LEFT OUTER JOIN exp.Protocol ON exp.Protocol.LSID=exp.ExperimentRun.ProtocolLSID
GO

CREATE VIEW ms2.SimplePeptides AS SELECT
    frac.Run, run.Description AS RunDescription, pep.Fraction, LEFT(frac.FileName, CHARINDEX('.', frac.FileName) - 1) AS FractionName, Scan,
    RetentionTime, Charge, Score1 As RawScore, Score2 As DiffScore, Score3 As ZScore, Score1 As SpScore, Score2 As DeltaCn, Score3 As XCorr, Score4 As SpRank,
    Score1 As Hyper, Score2 As Next, Score3 As B, Score4 As Y, Score5 As Expect, Score1 As Ion, Score2 As "Identity", Score3 AS Homology,
    IonPercent, pep.Mass, DeltaMass, (pep.Mass + DeltaMass) AS PrecursorMass, ABS(DeltaMass - ROUND(DeltaMass, 0)) AS FractionalDeltaMass,
    CASE WHEN pep.Mass = 0 THEN 0 ELSE ABS(1000000 * ABS(DeltaMass - ROUND(DeltaMass, 0)) / (pep.Mass + (Charge - 1) * 1.007276)) END AS FractionalDeltaMassPPM,
	CASE WHEN pep.Mass = 0 THEN 0 ELSE ABS(1000000 * DeltaMass / (pep.Mass + (Charge - 1) * 1.007276)) END AS DeltaMassPPM,
    CASE WHEN Charge = 0 THEN 0 ELSE (pep.Mass + DeltaMass + (Charge - 1) * 1.007276) / Charge END AS MZ, PeptideProphet, Peptide, ProteinHits,
    Protein, PrevAA, TrimmedPeptide, NextAA, LTRIM(RTRIM(PrevAA + TrimmedPeptide + NextAA)) AS StrippedPeptide,	SequencePosition, pep.SeqId, pep.RowId,
    quant.DecimalRatio, quant.Heavy2LightRatio, quant.HeavyArea, quant.HeavyFirstScan, quant.HeavyLastScan, quant.HeavyMass, quant.LightArea, quant.LightFirstScan, quant.LightLastScan, quant.LightMass, quant.Ratio,
    proph.ProphetFVal, proph.ProphetDeltaMass, proph.ProphetNumTrypticTerm, proph.ProphetNumMissedCleav
    FROM ms2.PeptidesData pep
        INNER JOIN
            ms2.Fractions frac ON pep.Fraction = frac.Fraction
        INNER JOIN
            ms2.Runs run ON frac.Run = run.Run
    LEFT JOIN ms2.quantitation quant ON pep.rowid=quant.peptideid
    LEFT JOIN ms2.peptideprophetdata proph ON pep.rowid=proph.peptideid
GO

CREATE VIEW ms2.Peptides AS
    SELECT pep.*, seq.description, seq.bestgenename AS genename
    FROM ms2.SimplePeptides pep
    LEFT JOIN prot.sequences seq ON seq.seqid = pep.seqid
GO

CREATE VIEW prot.FastaAdmin AS
    SELECT ff.FileName, ff.FastaId, ff.Loaded, runs.Runs
    FROM prot.FastaFiles ff LEFT OUTER JOIN
        (SELECT FastaId, COUNT(Run) AS Runs
        FROM ms2.Runs
        GROUP BY FastaId) runs ON runs.FastaId = ff.FastaId
GO

CREATE VIEW ms2.ProteinGroupsWithQuantitation AS
    SELECT * FROM ms2.ProteinGroups LEFT JOIN ms2.ProteinQuantitation ON ProteinGroupId = RowId
GO
