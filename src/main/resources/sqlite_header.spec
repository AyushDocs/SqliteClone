# SQLite Header Specification
# Format: name | bytes | type
magic | 16 | sequence:byte
pageSize | 2 | short
writeVersion | 1 | byte
readVersion | 1 | byte
reservedSpace | 1 | byte
maxPayloadFrac | 1 | byte
minPayloadFrac | 1 | byte
leafPayloadFrac | 1 | byte
fileChangeCounter | 4 | int
pageCount | 4 | int
firstFreelistTrunkPage | 4 | int
totalFreelistPages | 4 | int
schemaCookie | 4 | int
schemaFormat | 4 | int
defaultCacheSize | 4 | int
largestRootBtreePage | 4 | int
textEncodingId | 4 | int
userVersion | 4 | int
incrementalVacuum | 4 | int
applicationId | 4 | int
reserved | 20 | sequence:byte
versionValidFor | 4 | int
sqliteVersion | 4 | int
