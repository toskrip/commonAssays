/*
 * Copyright (c) 2011 LabKey Corporation
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

/* flow-0.00-8.10.sql */

CREATE SCHEMA Flow;

CREATE TABLE flow.Attribute
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(256) NOT NULL,

    CONSTRAINT PK_Attribute PRIMARY KEY (RowId),
    CONSTRAINT UQ_Attribute UNIQUE(Name)
);

CREATE TABLE flow.Object
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    DataId INT,
    TypeId INT NOT NULL,
    URI VARCHAR(400),
    CompId INT4,
    ScriptId INT4,
    FcsId INT4,

    CONSTRAINT FK_Object_Data FOREIGN KEY(DataId) REFERENCES exp.Data(RowId),
    CONSTRAINT PK_Object PRIMARY KEY(RowId),
    CONSTRAINT UQ_Object UNIQUE(DataId)
);
CREATE INDEX flow_object_typeid ON flow.object (container, typeid, dataid);
CLUSTER flow_object_typeid ON flow.object;

CREATE TABLE flow.Keyword
(
    ObjectId INT NOT NULL,
    KeywordId INT NOT NULL,
    Value TEXT,

    CONSTRAINT PK_Keyword PRIMARY KEY (ObjectId, KeywordId),
    CONSTRAINT FK_Keyword_Object FOREIGN KEY(ObjectId) REFERENCES flow.Object(RowId),
    CONSTRAINT FK_Keyword_Attribute FOREIGN KEY (KeywordId) REFERENCES flow.Attribute(RowId)
);

CLUSTER PK_Keyword ON flow.Keyword;

CREATE TABLE flow.Statistic
(
    ObjectId INT NOT NULL,
    StatisticId INT NOT NULL,
    Value FLOAT NOT NULL,

    CONSTRAINT PK_Statistic PRIMARY KEY (ObjectId, StatisticId),
    CONSTRAINT FK_Statistic_Object FOREIGN KEY (ObjectId) REFERENCES flow.Object(RowId),
    CONSTRAINT FK_Statistic_Attribute FOREIGN KEY (StatisticId) REFERENCES flow.Attribute(RowId)
);

CLUSTER PK_Statistic ON flow.Statistic;

CREATE TABLE flow.Graph
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    GraphId INT NOT NULL,
    Data BYTEA,

    CONSTRAINT PK_Graph PRIMARY KEY(RowId),
    CONSTRAINT UQ_Graph UNIQUE(ObjectId, GraphId),
    CONSTRAINT FK_Graph_Object FOREIGN KEY (ObjectId) REFERENCES flow.Object(RowId),
    CONSTRAINT FK_Graph_Attribute FOREIGN KEY (GraphId) REFERENCES flow.Attribute(RowId)
);

CREATE TABLE flow.Script
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    Text TEXT,

    CONSTRAINT PK_Script PRIMARY KEY(RowId),
    CONSTRAINT UQ_Script UNIQUE(ObjectId),
    CONSTRAINT FK_Script_Object FOREIGN KEY (ObjectId) REFERENCES flow.Object(RowId)
);
