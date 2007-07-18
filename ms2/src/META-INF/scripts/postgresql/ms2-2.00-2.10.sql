CREATE TABLE prot.CustomAnnotationSet
(
	CustomAnnotationSetId SERIAL NOT NULL PRIMARY KEY,
	Container EntityId NOT NULL,
	Name VARCHAR(200) NOT NULL,
	CreatedBy UserId,
	Created timestamp without time zone,
	ModifiedBy userid,
	Modified timestamp without time zone,
	CustomAnnotationType VARCHAR(20) NOT NULL,
	Lsid lsidtype,
	CONSTRAINT fk_CustomAnnotationSet_Container FOREIGN KEY (container) REFERENCES core.containers(EntityId),
	CONSTRAINT fk_CustomAnnotationSet_CreatedBy FOREIGN KEY (createdby) REFERENCES core.usersdata(userid),
	CONSTRAINT fk_CustomAnnotationSet_ModifiedBy FOREIGN KEY (modifiedby) REFERENCES core.usersdata(userid),
	CONSTRAINT uq_CustomAnnotationSet UNIQUE (Container, Name)
);

CREATE INDEX IX_CustomAnnotationSet_Container ON prot.CustomAnnotationSet(Container);

CREATE TABLE prot.CustomAnnotation
(
	CustomAnnotationId SERIAL NOT NULL PRIMARY KEY,
	CustomAnnotationSetId INT NOT NULL,
	ObjectURI LsidType NOT NULL,
	LookupString VARCHAR(200) NOT NULL,

	CONSTRAINT fk_CustomAnnotation_CustomAnnotationSetId FOREIGN KEY (CustomAnnotationSetId) REFERENCES prot.CustomAnnotationSet(CustomAnnotationSetId),
	CONSTRAINT UQ_CustomAnnotation_LookupString_SetId UNIQUE (LookupString, CustomAnnotationSetId),
	CONSTRAINT UQ_CustomAnnotation_ObjectURI UNIQUE (ObjectURI)
);

CREATE INDEX IX_CustomAnnotation_CustomAnnotationSetId ON prot.CustomAnnotation(CustomAnnotationSetId);

ALTER TABLE ms2.ProteinGroups ALTER COLUMN pctspectrumids DROP NOT NULL;
ALTER TABLE ms2.ProteinGroups ALTER COLUMN percentcoverage DROP NOT NULL;
