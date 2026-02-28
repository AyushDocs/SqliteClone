package personal.projects.sqlite.entities;

public abstract class Page {
    protected final int pageNumber;
    protected final PageType type;
    protected final byte[] data;

    public Page(int pageNumber, PageType type, byte[] data) {
        this.pageNumber = pageNumber;
        this.type = type;
        this.data = data;
    }

    public int getPageNumber() { return pageNumber; }
    public PageType getType() { return type; }
    public byte[] getRawData() { return data; }
}

