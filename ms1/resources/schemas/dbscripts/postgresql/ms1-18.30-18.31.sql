
UPDATE ms1.files SET MzXmlUrl = 'file:///' || substr(MzXmlUrl, 7) WHERE MzXmlUrl LIKE 'file:/_%' AND MzXmlUrl NOT LIKE 'file:///%' AND MzXmlUrl IS NOT NULL;