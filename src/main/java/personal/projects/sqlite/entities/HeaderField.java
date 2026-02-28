package personal.projects.sqlite.entities;

public class HeaderField<T> {
    private final String name;
    private final int offset;
    private final int size;
    private T value;

    public HeaderField(String name, int offset, int size) {
        this.name = name;
        this.offset = offset;
        this.size = size;
    }

    public String getName() { return name; }
    public int getOffset() { return offset; }
    public int getSize() { return size; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }

    @Override
    public String toString() {
        return name + ": " + value;
    }
}
