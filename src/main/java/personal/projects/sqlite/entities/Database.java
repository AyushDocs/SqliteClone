package personal.projects.sqlite.entities;

import personal.projects.sqlite.utils.DatabaseHeaderParser;
import personal.projects.sqlite.utils.SchemaTableParser;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Database {

    public static final int HEADER_SIZE = 100;
    public Map<String,Integer> headerFields;
    public Charset textEncoding;
    public List<Page> pages;
    public String reserved;
    public String databaseFilePath;
    public List<String> tableNames;

    public Database(String databaseFilePath) {
        this.headerFields = new HashMap<>();
        this.databaseFilePath=databaseFilePath;
        try(FileInputStream fis = new FileInputStream(databaseFilePath)){
            extractHeader(fis);
            int pageSize=this.getField("pageSize");
            byte[] schemaBytes = new byte[pageSize];
            requireBytesPerSection(fis.read(schemaBytes), pageSize, "Failed to read full schema table");
            SchemaTableParser.parseSchema(schemaBytes,this);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private void extractHeader(FileInputStream fis) throws Exception {
        byte[] header = new byte[HEADER_SIZE];
        requireBytesPerSection(fis.read(header), HEADER_SIZE,"header bytes couldn't be read completely");
        DatabaseHeaderParser.parseHeader(header,this);
    }

    private static void requireBytesPerSection(int bytesRead, int len, String message) {
        if (bytesRead != len)
            throw new  RuntimeException(message);
    }

    public int getField(String name) {
        return headerFields.get(name);
    }
}
