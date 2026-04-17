package personal.projects.sqlite.utils;

import java.lang.foreign.StructLayout;

/**
 * A hardware-level definition of the SQLite 100-byte file header using Project Panama.
 */
public class SQLiteHeaderLayout {
    public static final StructLayout LAYOUT = DynamicLayoutFactory.build("/sqlite_header.spec");
}
