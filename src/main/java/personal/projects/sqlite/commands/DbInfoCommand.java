package personal.projects.sqlite.commands;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

public class DbInfoCommand extends AbstractCommand {


    @Override
    public String getName() {
        return  ".dbinfo";
    }

    @Override
    public String execute(String[] args) {
        String databaseFilePath = args[0];
        try(FileInputStream databaseFile = new FileInputStream(new File(databaseFilePath));){
            databaseFile.skip(16); // Skip the first 16 bytes of the header
            byte[] pageSizeBytes = new byte[2]; // The following 2 bytes are the page size
            databaseFile.read(pageSizeBytes);
            short pageSizeSigned = ByteBuffer.wrap(pageSizeBytes).getShort();
            int pageSize = Short.toUnsignedInt(pageSizeSigned);
            System.err.println("Logs from your program will appear here!");
            System.out.println("database page size: " + pageSize);
            return "";
        }
        catch(Exception e){
            e.printStackTrace();
            return "";
        }

    }
}
