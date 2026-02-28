package personal.projects.sqlite.entities;

public enum PageType {
    INTERIOR_INDEX(0x02),
    INTERIOR_TABLE(0x05),
    LEAF_INDEX(0x0A),
    LEAF_TABLE(0x0D),
    UNKNOWN(-1);

    public final int code;

    PageType(int code) {
        this.code = code;
    }

    public static PageType fromByte(byte b) {
        return switch (Byte.toUnsignedInt(b)) {
            case 0x02 -> INTERIOR_INDEX;
            case 0x05 -> INTERIOR_TABLE;
            case 0x0A -> LEAF_INDEX;
            case 0x0D -> LEAF_TABLE;
            default -> UNKNOWN;
        };
    }
}

