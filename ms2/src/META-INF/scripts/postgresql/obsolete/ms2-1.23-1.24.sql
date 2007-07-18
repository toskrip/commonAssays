ALTER TABLE ms2.ms2peptidememberships DROP CONSTRAINT FK_MS2PeptideMembership_MS2PeptidesData;
ALTER TABLE ms2.ms2peptidesdata DROP CONSTRAINT UQ_PeptidesData;

DROP VIEW ms2.ms2peptides;
DROP VIEW ms2.ms2simplepeptides;

ALTER TABLE ms2.ms2peptidesdata DROP COLUMN RowId;
ALTER TABLE ms2.ms2peptidesdata ADD COLUMN RowId BIGSERIAL NOT NULL;
ALTER TABLE ms2.ms2peptidesdata ADD CONSTRAINT UQ_PeptidesData UNIQUE (RowId);

DROP TABLE ms2.MS2PeptideMemberships;
CREATE TABLE ms2.MS2PeptideMemberships
(
	PeptideId BIGINT NOT NULL,
	ProteinGroupId INT NOT NULL,

	CONSTRAINT PK_MS2PeptideMemberships                PRIMARY KEY (ProteinGroupId, PeptideId),
	CONSTRAINT FK_MS2PeptideMembership_MS2PeptidesData        FOREIGN KEY (PeptideId)       REFERENCES ms2.MS2PeptidesData (RowId),
	CONSTRAINT FK_MS2PeptideMembership_MS2ProteinGroup   FOREIGN KEY (ProteinGroupId)  REFERENCES ms2.MS2ProteinGroups (RowId)
);

CREATE OR REPLACE VIEW ms2.ms2SimplePeptides AS
 SELECT frac.run, run.description AS rundescription, pep.fraction, pep.scan, pep.charge, pep.score1 AS rawscore, pep.score2 AS diffscore, pep.score3 AS zscore, pep.score1 AS spscore, pep.score2 AS deltacn, pep.score3 AS xcorr, pep.score4 AS sprank, pep.score1 AS hyper, pep.score2 AS "next", pep.score3 AS b, pep.score4 AS y, pep.score5 AS expect, pep.score1 AS ion, pep.score2 AS identity, pep.score3 AS homology, pep.ionpercent, pep.mass, pep.deltamass, pep.mass + pep.deltamass AS precursormass, abs(pep.deltamass - round(pep.deltamass::double precision)) AS fractionaldeltamass,
        CASE
            WHEN pep.mass = 0::double precision THEN 0::double precision
            ELSE abs(1000000::double precision * abs(pep.deltamass - round(pep.deltamass::double precision)) / (pep.mass + ((pep.charge - 1)::numeric * 1.007276)::double precision))
        END AS fractionaldeltamassppm,
        CASE
            WHEN pep.mass = 0::double precision THEN 0::double precision
            ELSE abs(1000000::double precision * pep.deltamass / (pep.mass + ((pep.charge - 1)::numeric * 1.007276)::double precision))
        END AS deltamassppm,
        CASE
            WHEN pep.charge = 0 THEN 0::double precision
            ELSE (pep.mass + pep.deltamass + ((pep.charge - 1)::numeric * 1.007276)::double precision) / pep.charge::double precision
        END AS mz, pep.peptideprophet, pep.peptide, pep.proteinhits, pep.protein, pep.prevaa, pep.trimmedpeptide, pep.nextaa, ltrim(rtrim((pep.prevaa::text || pep.trimmedpeptide::text) || pep.nextaa::text)) AS strippedpeptide, pep.sequenceposition, pep.seqid, pep.rowid
   FROM ms2.ms2peptidesdata pep
   JOIN ms2.ms2fractions frac ON pep.fraction = frac.fraction
   JOIN ms2.ms2runs run ON frac.run = run.run;

CREATE OR REPLACE VIEW ms2.ms2peptides AS
 SELECT pep.*, seq.description, seq.bestgenename AS genename
   FROM ms2.ms2SimplePeptides pep
   LEFT JOIN prot.protsequences seq ON seq.seqid = pep.seqid;