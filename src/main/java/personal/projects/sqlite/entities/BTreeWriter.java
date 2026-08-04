package personal.projects.sqlite.entities;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import personal.projects.sqlite.utils.SegmentReader;

/**
 * Handles writing rows to B-Tree pages.
 *
 * <p>Table b-trees are modified in place with a recursive insert that splits
 * full pages and allocates new pages from the database file. Index b-trees are
 * rebuilt from scratch on every insert so the key ordering always matches
 * SQLite's comparison rules exactly.</p>
 */
public class BTreeWriter {

    private static final int INTERIOR_INDEX = 0x02;
    private static final int INTERIOR_TABLE = 0x05;
    private static final int LEAF_INDEX = 0x0A;
    private static final int LEAF_TABLE = 0x0D;

    private static final ValueLayout.OfByte B = ValueLayout.JAVA_BYTE;
    private static final ValueLayout.OfShort SHORT_BE = ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt INT_BE = ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Database database;
    private final MemorySegment dbFile;
    private final int pageSize;

    public BTreeWriter(Database database) {
        this.database = database;
        this.dbFile = database.memoryMap;
        this.pageSize = database.pageSize;
    }

    public BTreeWriter(MemorySegment dbFile, int pageSize) {
        this.database = null;
        this.dbFile = dbFile;
        this.pageSize = pageSize;
    }

    /** Allocates a fresh page and initializes it as an empty table leaf. */
    public int createTableRoot() {
        int pageNo = database.allocatePage();
        rewriteTableLeaf(pageNo, List.of());
        return pageNo;
    }

    /** Allocates a fresh page and initializes it as an empty index leaf. */
    public int createIndexRoot() {
        int pageNo = database.allocatePage();
        rewriteIndexLeaf(pageNo, List.of());
        return pageNo;
    }

    /** Returns true when an index already contains the given column values (used for UNIQUE checks). */
    public synchronized boolean indexHasKeyPrefix(int rootPage, List<Object> keyValues) {
        byte[] key = encodeRecord(keyValues);
        int keyColumns = countSerialTypes(key);
        List<byte[]> records = new ArrayList<>();
        collectIndexRecords(rootPage, records);
        for (byte[] rec : records) {
            if (compareRecords(rec, key, keyColumns) == 0) return true;
        }
        return false;
    }

    private int countSerialTypes(byte[] record) {
        SegmentReader r = new SegmentReader(MemorySegment.ofArray(record));
        int headerSize = (int) r.readVarInt();
        long headerEnd = r.getOffset() + (headerSize - 1);
        int count = 0;
        while (r.getOffset() < headerEnd) {
            r.readVarInt();
            count++;
        }
        return count;
    }

    // ============================ TABLE b-tree ============================

    /** Inserts a row into the table rooted at {@code rootPage} and returns the assigned rowid. */
    public synchronized long insertRow(int rootPage, List<Object> values) {
        long rowId = nextRowId(rootPage);
        insertRowWithId(rootPage, rowId, values);
        return rowId;
    }

    public synchronized long nextRowId(int rootPage) {
        BTreeReader reader = new BTreeReader(dbFile, pageSize);
        return reader.readTable(rootPage).stream().mapToLong(BTreeReader.Row::rowId).max().orElse(0) + 1;
    }

    public synchronized void insertRowWithId(int rootPage, long rowId, List<Object> values) {
        byte[] payload = encodeRecord(values);
        TableSplit split = insertTable(rootPage, rowId, payload);
        if (split != null) {
            splitRootAsInterior(rootPage, split);
        }
        database.bumpChangeCounter();
    }

    private record TableSplit(int newPage, long splitRowId) {}

    /** The parent-page state after folding in a child split: cells, new right-most child and the added cell. */
    private record TableInteriorUpdate(List<TableInteriorCell> cells, int rightMost, byte[] addedCell) {}

    /** Returns a split to propagate up the tree, or null when the cell was inserted without splitting. */
    private TableSplit insertTable(int pageNo, long rowId, byte[] payload) {
        int type = pageType(pageNo);
        if (type == LEAF_TABLE) {
            if (fitsTableLeaf(pageNo, rowId, payload)) {
                insertIntoTableLeaf(pageNo, rowId, payload);
                return null;
            }
            return splitTableLeaf(pageNo, rowId, payload);
        }
        if (type != INTERIOR_TABLE) {
            throw new IllegalStateException("Unexpected page type " + type + " on page " + pageNo);
        }
        int child = childForRowId(pageNo, rowId);
        TableSplit split = insertTable(child, rowId, payload);
        if (split == null) return null;
        TableInteriorUpdate update = tableInteriorUpdate(pageNo, child, split);
        if (fitsTableInterior(pageNo, update)) {
            applyTableInteriorUpdate(pageNo, update);
            return null;
        }
        return splitTableInterior(pageNo, update);
    }

    /** Splits a full leaf page in two, keeping the left half on the original page. */
    private TableSplit splitTableLeaf(int pageNo, long newRowId, byte[] newPayload) {
        List<TableLeafCell> cells = readTableLeafCells(pageNo);
        cells.add(new TableLeafCell(newRowId, newPayload));
        cells.sort(Comparator.comparingLong(TableLeafCell::rowId));

        int mid = cells.size() / 2;
        List<TableLeafCell> left = new ArrayList<>(cells.subList(0, mid));
        List<TableLeafCell> right = new ArrayList<>(cells.subList(mid, cells.size()));

        rewriteTableLeaf(pageNo, left);
        int newPage = database.allocatePage();
        rewriteTableLeaf(newPage, right);
        return new TableSplit(newPage, right.get(0).rowId());
    }

    /**
     * Computes how a child split is reflected in its parent. A table interior
     * cell holds the largest rowid of its left child's subtree, so the cell for
     * the splitted child keeps pointing at the left half with its key reduced to
     * {@code splitRowId - 1}, and the right half becomes a new cell inserted
     * immediately after it, keyed by the child's previous separator. When the
     * splitted child was the right-most child it is demoted to the final cell and
     * the new right half takes over the right-most pointer.
     */
    private TableInteriorUpdate tableInteriorUpdate(int pageNo, int oldChild, TableSplit split) {
        List<TableInteriorCell> cells = readTableInteriorCells(pageNo);
        long leftMax = split.splitRowId() - 1;
        if (oldChild == rightMostChild(pageNo)) {
            TableInteriorCell demoted = new TableInteriorCell(oldChild, leftMax);
            cells.add(demoted);
            return new TableInteriorUpdate(cells, split.newPage(), tableInteriorCellBytes(oldChild, leftMax));
        }
        int pos = 0;
        while (pos < cells.size() && cells.get(pos).childPage() != oldChild) pos++;
        if (pos == cells.size()) {
            throw new IllegalStateException("Split child page " + oldChild + " not found in interior page " + pageNo);
        }
        long oldKey = cells.get(pos).keyRowId();
        cells.set(pos, new TableInteriorCell(oldChild, leftMax));
        cells.add(pos + 1, new TableInteriorCell(split.newPage(), oldKey));
        return new TableInteriorUpdate(cells, rightMostChild(pageNo), tableInteriorCellBytes(split.newPage(), oldKey));
    }

    private void applyTableInteriorUpdate(int pageNo, TableInteriorUpdate update) {
        rewriteTableInterior(pageNo, update.cells(), update.rightMost());
    }

    /** Splits a full interior page that already includes the pending child-split update. */
    private TableSplit splitTableInterior(int pageNo, TableInteriorUpdate update) {
        List<TableInteriorCell> cells = update.cells();
        int mid = cells.size() / 2;
        List<TableInteriorCell> left = new ArrayList<>(cells.subList(0, mid));
        // cells[mid] is promoted to the left page's right-most pointer, so it must
        // NOT also be written as a cell on the right page (no duplicate child refs).
        List<TableInteriorCell> right = new ArrayList<>(cells.subList(mid + 1, cells.size()));

        int leftRightMost = cells.get(mid).childPage();
        int rightRightMost = update.rightMost();

        rewriteTableInterior(pageNo, left, leftRightMost);
        int newPage = database.allocatePage();
        rewriteTableInterior(newPage, right, rightRightMost);
        // Left page's largest rowid is held by its new right-most pointer.
        return new TableSplit(newPage, cells.get(mid).keyRowId() + 1);
    }

    /**
     * Turns the root page into an interior page after a root split. Both child
     * halves must live on fresh pages because the root page is reused for the
     * interior header/cells.
     */
    private void splitRootAsInterior(int rootPage, TableSplit split) {
        int type = pageType(rootPage);
        int leftPage = database.allocatePage();
        if (type == LEAF_TABLE) {
            rewriteTableLeaf(leftPage, readTableLeafCells(rootPage));
        } else if (type == INTERIOR_TABLE) {
            int rm = rightMostChild(rootPage);
            rewriteTableInterior(leftPage, readTableInteriorCells(rootPage), rm);
        } else {
            throw new IllegalStateException("Cannot split root of page type " + type);
        }
        rewriteTableInterior(rootPage, List.of(new TableInteriorCell(leftPage, split.splitRowId() - 1)), split.newPage());
    }

    private int childForRowId(int pageNo, long rowId) {
        List<TableInteriorCell> cells = readTableInteriorCells(pageNo);
        for (TableInteriorCell cell : cells) {
            if (rowId <= cell.keyRowId()) return cell.childPage();
        }
        return rightMostChild(pageNo);
    }

    private void insertIntoTableLeaf(int pageNo, long rowId, byte[] payload) {
        int pos = insertionPosTableLeaf(pageNo, rowId);
        byte[] cell = tableLeafCellBytes(rowId, payload);
        insertCellAt(pageNo, pos, cell);
    }

    private boolean fitsTableLeaf(int pageNo, long rowId, byte[] payload) {
        return fitsCell(pageNo, headerLen(LEAF_TABLE), tableLeafCellBytes(rowId, payload).length);
    }

    private boolean fitsTableInterior(int pageNo, TableInteriorUpdate update) {
        return fitsCell(pageNo, headerLen(INTERIOR_TABLE), update.addedCell().length);
    }

    // ============================ INDEX b-tree ============================

    /** Inserts an entry into a table index: the indexed column values plus the rowid as the last key field. */
    public synchronized void insertIndexKey(int rootPage, List<Object> keyValues, long rowId) {
        List<Object> fullKey = new ArrayList<>(keyValues);
        fullKey.add(rowId);
        byte[] newRecord = encodeRecord(fullKey);

        List<byte[]> records = new ArrayList<>();
        collectIndexRecords(rootPage, records);
        records.add(newRecord);
        records.sort(this::compareRecords);

        rebuildIndex(rootPage, records);
        database.bumpChangeCounter();
    }

    /**
     * Collects every index entry reachable from {@code pageNo}: leaf records and the boundary
     * separator records stored in interior cells (each is a real entry, stored exactly once).
     */
    private void collectIndexRecords(int pageNo, List<byte[]> out) {
        int type = pageType(pageNo);
        if (type == LEAF_INDEX) {
            List<Integer> pointers = cellPointers(pageNo, headerLen(LEAF_INDEX));
            for (int off : pointers) {
                SegmentReader r = new SegmentReader(pageSlice(pageNo));
                r.goTo(off);
                int payloadSize = (int) r.readVarInt();
                out.add(r.readNBytes(payloadSize));
            }
            return;
        }
        if (type == INTERIOR_INDEX) {
            List<Integer> pointers = cellPointers(pageNo, headerLen(INTERIOR_INDEX));
            for (int off : pointers) {
                SegmentReader r = new SegmentReader(pageSlice(pageNo));
                r.goTo(off);
                int childPage = r.readInt();
                int keyLen = (int) r.readVarInt();
                out.add(r.readNBytes(keyLen));
                collectIndexRecords(childPage, out);
            }
            collectIndexRecords(rightMostChild(pageNo), out);
            return;
        }
        throw new IllegalStateException("Unexpected page type " + type + " on index page " + pageNo);
    }

    /** Rebuilds the entire index b-tree so its keys stay perfectly ordered. Reuses existing page numbers. */
    private void rebuildIndex(int rootPage, List<byte[]> records) {
        List<Integer> pool = new ArrayList<>();
        collectIndexPages(rootPage, pool);
        int buildRoot = buildNode(pool, records);
        if (buildRoot != rootPage) {
            // The pool was empty (no reusable page); the built root must live at rootPage.
            throw new IllegalStateException("Index rebuild lost its root page");
        }
    }

    /**
     * Rebuilds an index subtree over {@code records} (sorted), storing each record exactly once.
     * SQLite's index b-trees store every key exactly once across the whole tree: leaf nodes hold
     * records, and interior cells hold the boundary ("separator") record between consecutive
     * children, which is removed from its child's leaf so it is never duplicated. Mirrors the
     * way SQLite promotes a cell out of a full page during a split.
     */
    private int buildNode(List<Integer> pool, List<byte[]> records) {
        int myPage = takePage(pool);
        if (recordsFitOneLeaf(records)) {
            rewriteIndexLeaf(myPage, records);
            return myPage;
        }
        List<List<byte[]>> leafGroups = new ArrayList<>();
        List<byte[]> seps = new ArrayList<>();
        partitionLeafGroups(records, leafGroups, seps);
        List<IndexEntry> children = new ArrayList<>();
        for (int i = 0; i < leafGroups.size(); i++) {
            int leafPage = buildNode(pool, leafGroups.get(i));
            byte[] sep = i < seps.size() ? seps.get(i) : null;
            children.add(new IndexEntry(leafPage, sep));
        }
        return buildInteriorLevel(pool, children, myPage);
    }

    /** Builds interior levels above {@code children}; the topmost node is written to {@code topPage}. */
    private int buildInteriorLevel(List<Integer> pool, List<IndexEntry> children, int topPage) {
        if (partitionIntoInteriors(children).size() == 1) {
            rewriteIndexInterior(topPage, children);
            return topPage;
        }
        List<List<IndexEntry>> groups = partitionIntoInteriors(children);
        List<IndexEntry> next = new ArrayList<>();
        for (List<IndexEntry> group : groups) {
            int nodePage = takePage(pool);
            rewriteIndexInterior(nodePage, group);
            next.add(new IndexEntry(nodePage, group.get(group.size() - 1).separator()));
        }
        return buildInteriorLevel(pool, next, topPage);
    }

    /**
     * Partitions sorted {@code records} into leaf groups. Between consecutive groups the record
     * immediately following the group's last cell is promoted out of the leaves to become the
     * interior separator, so it is stored exactly once and never duplicated in a leaf.
     */
    private void partitionLeafGroups(List<byte[]> records, List<List<byte[]>> groups, List<byte[]> seps) {
        int i = 0;
        int n = records.size();
        while (i < n) {
            List<byte[]> group = new ArrayList<>();
            long used = headerLen(LEAF_INDEX);
            while (i < n) {
                byte[] r = records.get(i);
                long cellSize = varintLen(r.length) + r.length;
                if (cellSize + 2 + used > pageSize && !group.isEmpty()) {
                    if (i == n - 1) {
                        // The overflowing record is the final one, so it cannot be promoted as a
                        // separator (there would be no following leaf). Instead promote the group's
                        // own last cell and let the final record form its own trailing leaf.
                        byte[] lastCell = group.remove(group.size() - 1);
                        seps.add(lastCell);
                    } else {
                        seps.add(records.get(i));
                        i++;
                    }
                    break;
                }
                group.add(r);
                used += cellSize + 2;
                i++;
            }
            groups.add(group);
        }
    }

    private boolean recordsFitOneLeaf(List<byte[]> records) {
        long used = headerLen(LEAF_INDEX);
        for (byte[] r : records) {
            long cellSize = varintLen(r.length) + r.length;
            if (used > headerLen(LEAF_INDEX) && cellSize + 2 + used > pageSize) return false;
            used += cellSize + 2;
        }
        return true;
    }

    /** Partitions children into interior-node groups; the last child of each group holds the promoted separator. */
    private List<List<IndexEntry>> partitionIntoInteriors(List<IndexEntry> children) {
        List<List<IndexEntry>> groups = new ArrayList<>();
        List<IndexEntry> current = new ArrayList<>();
        long used = headerLen(INTERIOR_INDEX);
        for (int k = 0; k < children.size(); k++) {
            IndexEntry child = children.get(k);
            boolean isLast = k == children.size() - 1;
            long cellSize = isLast ? 0 : 4 + varintLen(child.separator().length) + child.separator().length;
            long add = isLast ? 0 : cellSize + 2;
            if (!current.isEmpty() && used + add > pageSize) {
                groups.add(current);
                current = new ArrayList<>();
                used = headerLen(INTERIOR_INDEX);
            }
            current.add(child);
            used += add;
        }
        if (!current.isEmpty()) groups.add(current);
        if (groups.isEmpty()) groups.add(new ArrayList<>());
        return groups;
    }

    private void collectIndexPages(int pageNo, List<Integer> out) {
        out.add(pageNo);
        int type = pageType(pageNo);
        if (type == LEAF_INDEX) return;
        if (type != INTERIOR_INDEX) throw new IllegalStateException("Unexpected index page type " + type);
        List<Integer> pointers = cellPointers(pageNo, headerLen(INTERIOR_INDEX));
        for (int off : pointers) {
            SegmentReader r = new SegmentReader(pageSlice(pageNo));
            r.goTo(off);
            collectIndexPages(r.readInt(), out);
        }
        collectIndexPages(rightMostChild(pageNo), out);
    }

    private int takePage(List<Integer> pool) {
        if (!pool.isEmpty()) return pool.remove(0);
        return database.allocatePage();
    }

    // ============================ Record encoding ============================

    private byte[] encodeRecord(List<Object> values) {
        List<Byte> header = new ArrayList<>();
        List<Byte> body = new ArrayList<>();

        for (Object val : values) {
            if (val == null) {
                header.addAll(toList(encodeVarInt(0)));
            } else if (val instanceof String s) {
                byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
                header.addAll(toList(encodeVarInt(bytes.length * 2 + 13)));
                body.addAll(toList(bytes));
            } else if (val instanceof Long l) {
                int[] serial = minimalIntegerSerialType(l);
                header.addAll(toList(encodeVarInt(serial[0])));
                body.addAll(toList(encodeInteger(l, serial[0])));
            } else if (val instanceof Integer i) {
                long l = i.longValue();
                int[] serial = minimalIntegerSerialType(l);
                header.addAll(toList(encodeVarInt(serial[0])));
                body.addAll(toList(encodeInteger(l, serial[0])));
            } else if (val instanceof Double d) {
                header.addAll(toList(encodeVarInt(7)));
                body.addAll(toList(longToBytesBE(Double.doubleToRawLongBits(d))));
            } else if (val instanceof Float f) {
                header.addAll(toList(encodeVarInt(7)));
                body.addAll(toList(longToBytesBE(Double.doubleToRawLongBits(f.doubleValue()))));
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + val.getClass().getName());
            }
        }

        int headerSize = header.size() + 1; // assumes the header-size varint fits in a single byte
        byte[] headerSizeVar = encodeVarInt(headerSize);
        if (headerSizeVar.length != 1) {
            headerSize = header.size() + headerSizeVar.length;
            headerSizeVar = encodeVarInt(headerSize);
        }

        byte[] result = new byte[headerSizeVar.length + header.size() + body.size()];
        int p = 0;
        for (byte b : headerSizeVar) result[p++] = b;
        for (byte b : header) result[p++] = b;
        for (byte b : body) result[p++] = b;
        return result;
    }

    /** Returns {serialType, byteCount} for the smallest encoding of the given integer. */
    private int[] minimalIntegerSerialType(long v) {
        if (v >= -128 && v <= 127) return new int[]{1, 1};
        if (v >= -32768 && v <= 32767) return new int[]{2, 2};
        if (v >= -8388608 && v <= 8388607) return new int[]{3, 3};
        if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return new int[]{4, 4};
        if (v >= -140737488355328L && v <= 140737488355327L) return new int[]{5, 6};
        return new int[]{6, 8};
    }

    private byte[] encodeInteger(long v, int serialType) {
        int bytes = serialType <= 3 ? serialType : (serialType == 5 ? 6 : 8);
        byte[] out = new byte[bytes];
        for (int i = bytes - 1; i >= 0; i--) {
            out[i] = (byte) v;
            v >>= 8;
        }
        return out;
    }

    private byte[] longToBytesBE(long v) {
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) v;
            v >>= 8;
        }
        return out;
    }

    private List<Byte> toList(byte[] arr) {
        List<Byte> list = new ArrayList<>();
        for (byte b : arr) list.add(b);
        return list;
    }

    // ============================ Page primitives ============================

    private record TableLeafCell(long rowId, byte[] payload) {}
    private record TableInteriorCell(int childPage, long keyRowId) {}
    private record IndexEntry(int page, byte[] separator) {}

    private int pageType(int pageNo) {
        return dbFile.get(B, pageStart(pageNo) + btreeHeaderOffset(pageNo)) & 0xFF;
    }

    private int numCells(int pageNo) {
        return dbFile.get(SHORT_BE, pageStart(pageNo) + btreeHeaderOffset(pageNo) + 3) & 0xFFFF;
    }

    private int contentStart(int pageNo) {
        int cs = dbFile.get(SHORT_BE, pageStart(pageNo) + btreeHeaderOffset(pageNo) + 5) & 0xFFFF;
        return cs == 0 ? 65536 : cs;
    }

    private int rightMostChild(int pageNo) {
        return dbFile.get(INT_BE, pageStart(pageNo) + btreeHeaderOffset(pageNo) + 8);
    }

    private int btreeHeaderOffset(int pageNo) {
        return pageNo == 1 ? 100 : 0;
    }

    private int headerLen(int pageType) {
        return (pageType == INTERIOR_TABLE || pageType == INTERIOR_INDEX) ? 12 : 8;
    }

    private long pageStart(int pageNo) {
        return (long) (pageNo - 1) * pageSize;
    }

    private MemorySegment pageSlice(int pageNo) {
        return dbFile.asSlice(pageStart(pageNo), pageSize);
    }

    private List<Integer> cellPointers(int pageNo, int headerLen) {
        List<Integer> pointers = new ArrayList<>();
        int base = (int) pageStart(pageNo) + btreeHeaderOffset(pageNo) + headerLen;
        int n = numCells(pageNo);
        for (int i = 0; i < n; i++) {
            pointers.add(dbFile.get(SHORT_BE, base + i * 2L) & 0xFFFF);
        }
        return pointers;
    }

    private List<TableLeafCell> readTableLeafCells(int pageNo) {
        List<TableLeafCell> cells = new ArrayList<>();
        for (int off : cellPointers(pageNo, headerLen(LEAF_TABLE))) {
            SegmentReader r = new SegmentReader(pageSlice(pageNo));
            r.goTo(off);
            long payloadSize = r.readVarInt();
            long rowId = r.readVarInt();
            cells.add(new TableLeafCell(rowId, r.readNBytes((int) payloadSize)));
        }
        return cells;
    }

    private List<TableInteriorCell> readTableInteriorCells(int pageNo) {
        List<TableInteriorCell> cells = new ArrayList<>();
        for (int off : cellPointers(pageNo, headerLen(INTERIOR_TABLE))) {
            SegmentReader r = new SegmentReader(pageSlice(pageNo));
            r.goTo(off);
            int childPage = r.readInt();
            long keyRowId = r.readVarInt();
            cells.add(new TableInteriorCell(childPage, keyRowId));
        }
        return cells;
    }

    private byte[] tableLeafCellBytes(long rowId, byte[] payload) {
        byte[] sizeVar = encodeVarInt(payload.length);
        byte[] rowVar = encodeVarInt(rowId);
        byte[] cell = new byte[sizeVar.length + rowVar.length + payload.length];
        int p = 0;
        for (byte b : sizeVar) cell[p++] = b;
        for (byte b : rowVar) cell[p++] = b;
        for (byte b : payload) cell[p++] = b;
        return cell;
    }

    private byte[] tableInteriorCellBytes(int childPage, long keyRowId) {
        byte[] keyVar = encodeVarInt(keyRowId);
        byte[] cell = new byte[4 + keyVar.length];
        cell[0] = (byte) (childPage >>> 24);
        cell[1] = (byte) (childPage >>> 16);
        cell[2] = (byte) (childPage >>> 8);
        cell[3] = (byte) childPage;
        int p = 4;
        for (byte b : keyVar) cell[p++] = b;
        return cell;
    }

    private boolean fitsCell(int pageNo, int headerLen, int cellSize) {
        int pointerEnd = btreeHeaderOffset(pageNo) + headerLen + numCells(pageNo) * 2;
        return pointerEnd + 2 + cellSize <= contentStart(pageNo);
    }

    private int insertionPosTableLeaf(int pageNo, long rowId) {
        List<TableLeafCell> cells = readTableLeafCells(pageNo);
        int pos = 0;
        while (pos < cells.size() && cells.get(pos).rowId() < rowId) pos++;
        return pos;
    }

    /** Writes a cell into the page's content area and inserts its pointer at the given position. */
    private void insertCellAt(int pageNo, int pos, byte[] cell) {
        int hdr = btreeHeaderOffset(pageNo);
        long pageStart = pageStart(pageNo);
        int n = numCells(pageNo);
        int cs = contentStart(pageNo);
        int newOff = cs - cell.length;

        for (int i = 0; i < cell.length; i++) {
            dbFile.set(B, pageStart + newOff + i, cell[i]);
        }

        // Shift the pointer array to make room at position `pos`, then insert the new pointer.
        for (int i = n; i > pos; i--) {
            dbFile.set(SHORT_BE, pageStart + hdr + pointerBase(pageNo) + i * 2L,
                    dbFile.get(SHORT_BE, pageStart + hdr + pointerBase(pageNo) + (i - 1) * 2L));
        }
        dbFile.set(SHORT_BE, pageStart + hdr + pointerBase(pageNo) + pos * 2L, (short) newOff);

        dbFile.set(SHORT_BE, pageStart + hdr + 3, (short) (n + 1));
        dbFile.set(SHORT_BE, pageStart + hdr + 5, (short) newOff);
    }

    private int pointerBase(int pageNo) {
        return headerLenOfPage(pageNo);
    }

    private int headerLenOfPage(int pageNo) {
        return headerLen(pageType(pageNo));
    }

    private void zeroPageContent(int pageNo) {
        long start = pageStart(pageNo) + btreeHeaderOffset(pageNo);
        for (long i = start; i < pageStart(pageNo) + pageSize; i++) {
            dbFile.set(B, i, (byte) 0);
        }
    }

    private void rewriteTableLeaf(int pageNo, List<TableLeafCell> cells) {
        zeroPageContent(pageNo);
        int hdr = btreeHeaderOffset(pageNo);
        dbFile.set(B, pageStart(pageNo) + hdr, (byte) LEAF_TABLE);
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 3, (short) cells.size());
        int cs = pageSize;
        int base = (int) (pageStart(pageNo) + hdr + headerLen(LEAF_TABLE));
        for (int i = 0; i < cells.size(); i++) {
            byte[] cell = tableLeafCellBytes(cells.get(i).rowId(), cells.get(i).payload());
            int off = cs - cell.length;
            for (int j = 0; j < cell.length; j++) dbFile.set(B, pageStart(pageNo) + off + j, cell[j]);
            dbFile.set(SHORT_BE, base + i * 2L, (short) off);
            cs = off;
        }
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 5, (short) cs);
    }

    private void rewriteTableInterior(int pageNo, List<TableInteriorCell> cells, int rightMost) {
        zeroPageContent(pageNo);
        int hdr = btreeHeaderOffset(pageNo);
        dbFile.set(B, pageStart(pageNo) + hdr, (byte) INTERIOR_TABLE);
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 3, (short) cells.size());
        dbFile.set(INT_BE, pageStart(pageNo) + hdr + 8, rightMost);
        int cs = pageSize;
        int base = (int) (pageStart(pageNo) + hdr + headerLen(INTERIOR_TABLE));
        for (int i = 0; i < cells.size(); i++) {
            byte[] cell = tableInteriorCellBytes(cells.get(i).childPage(), cells.get(i).keyRowId());
            int off = cs - cell.length;
            for (int j = 0; j < cell.length; j++) dbFile.set(B, pageStart(pageNo) + off + j, cell[j]);
            dbFile.set(SHORT_BE, base + i * 2L, (short) off);
            cs = off;
        }
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 5, (short) cs);
    }

    private void rewriteIndexLeaf(int pageNo, List<byte[]> records) {
        zeroPageContent(pageNo);
        int hdr = btreeHeaderOffset(pageNo);
        dbFile.set(B, pageStart(pageNo) + hdr, (byte) LEAF_INDEX);
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 3, (short) records.size());
        int cs = pageSize;
        int base = (int) (pageStart(pageNo) + hdr + headerLen(LEAF_INDEX));
        for (int i = 0; i < records.size(); i++) {
            byte[] rec = records.get(i);
            byte[] sizeVar = encodeVarInt(rec.length);
            byte[] cell = new byte[sizeVar.length + rec.length];
            int p = 0;
            for (byte b : sizeVar) cell[p++] = b;
            for (byte b : rec) cell[p++] = b;
            int off = cs - cell.length;
            for (int j = 0; j < cell.length; j++) dbFile.set(B, pageStart(pageNo) + off + j, cell[j]);
            dbFile.set(SHORT_BE, base + i * 2L, (short) off);
            cs = off;
        }
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 5, (short) cs);
    }

    /**
     * Writes an interior index page. Each cell pairs the left child page number with that child's
     * separator: the boundary record that comes immediately after the child's subtree in sort order
     * (and immediately before the next child's subtree). The separator is a real index entry that is
     * stored ONLY here, never duplicated in a child leaf. The last child is the right-most pointer.
     */
    private void rewriteIndexInterior(int pageNo, List<IndexEntry> children) {
        zeroPageContent(pageNo);
        int hdr = btreeHeaderOffset(pageNo);
        int numCells = children.size() - 1;
        dbFile.set(B, pageStart(pageNo) + hdr, (byte) INTERIOR_INDEX);
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 3, (short) numCells);
        dbFile.set(INT_BE, pageStart(pageNo) + hdr + 8, children.get(children.size() - 1).page());
        int cs = pageSize;
        int base = (int) (pageStart(pageNo) + hdr + headerLen(INTERIOR_INDEX));
        for (int i = 0; i < numCells; i++) {
            IndexEntry child = children.get(i);
            byte[] sep = child.separator();
            byte[] keyVar = encodeVarInt(sep.length);
            byte[] cell = new byte[4 + keyVar.length + sep.length];
            int p = 0;
            cell[p++] = (byte) (child.page() >>> 24);
            cell[p++] = (byte) (child.page() >>> 16);
            cell[p++] = (byte) (child.page() >>> 8);
            cell[p++] = (byte) child.page();
            for (byte b : keyVar) cell[p++] = b;
            for (byte b : sep) cell[p++] = b;
            int off = cs - cell.length;
            for (int j = 0; j < cell.length; j++) dbFile.set(B, pageStart(pageNo) + off + j, cell[j]);
            dbFile.set(SHORT_BE, base + i * 2L, (short) off);
            cs = off;
        }
        dbFile.set(SHORT_BE, pageStart(pageNo) + hdr + 5, (short) cs);
    }

    // ============================ Key comparison ============================

    /**
     * Compares two SQLite records using SQLite's default (binary) sort order:
     * NULL &lt; INTEGER/REAL &lt; TEXT &lt; BLOB, column by column.
     */
    private int compareRecords(byte[] a, byte[] b) {
        return compareRecords(a, b, Integer.MAX_VALUE);
    }

    /**
     * Compares two SQLite records using SQLite's default (binary) sort order:
     * NULL &lt; INTEGER/REAL &lt; TEXT &lt; BLOB, column by column, limited to {@code maxColumns}.
     */
    private int compareRecords(byte[] a, byte[] b, int maxColumns) {
        SegmentReader ra = new SegmentReader(MemorySegment.ofArray(a));
        SegmentReader rb = new SegmentReader(MemorySegment.ofArray(b));
        int headerA = (int) ra.readVarInt();
        int headerB = (int) rb.readVarInt();

        List<Integer> typesA = readSerialTypes(ra, headerA);
        List<Integer> typesB = readSerialTypes(rb, headerB);

        int n = Math.min(maxColumns, Math.min(typesA.size(), typesB.size()));
        for (int i = 0; i < n; i++) {
            int c = compareValue(typesA.get(i), typesB.get(i), ra, rb);
            if (c != 0) return c;
        }
        return 0;
    }

    private List<Integer> readSerialTypes(SegmentReader r, int headerSize) {
        List<Integer> types = new ArrayList<>();
        long headerEnd = r.getOffset() + (headerSize - 1); // minus the header-size varint itself
        while (r.getOffset() < headerEnd) {
            types.add((int) r.readVarInt());
        }
        return types;
    }

    private int compareValue(int ta, int tb, SegmentReader ra, SegmentReader rb) {
        int ca = typeClass(ta);
        int cb = typeClass(tb);
        if (ca != cb) return Integer.compare(ca, cb);
        if (ca == 0) return 0; // both NULL

        if (ca == 1) {
            boolean ia = isInteger(ta);
            boolean ib = isInteger(tb);
            if (ia && ib) {
                long va = readInteger(ra, ta);
                long vb = readInteger(rb, tb);
                return Long.compare(va, vb);
            }
            double da = ia ? (double) readInteger(ra, ta) : readDouble(ra);
            double db = ib ? (double) readInteger(rb, tb) : readDouble(rb);
            return Double.compare(da, db);
        }

        int sa = getSerialTypeSize(ta);
        int sb = getSerialTypeSize(tb);
        byte[] va = ra.readNBytes(sa);
        byte[] vb = rb.readNBytes(sb);
        return compareBytes(va, vb);
    }

    private int typeClass(int type) {
        if (type == 0) return 0;
        if (type <= 9) return 1;
        return type % 2 == 0 ? 3 : 2; // even = BLOB, odd = TEXT
    }

    private boolean isInteger(int type) {
        return type != 7;
    }

    private long readInteger(SegmentReader r, int type) {
        return switch (type) {
            case 1 -> r.readByteSigned();
            case 2 -> r.readShortSigned();
            case 3 -> r.readSignedIntN(3);
            case 4 -> r.readIntSigned();
            case 5 -> r.readSignedIntN(6);
            case 6 -> r.readLong();
            case 8 -> 0L;
            case 9 -> 1L;
            default -> 0L;
        };
    }

    private double readDouble(SegmentReader r) {
        return Double.longBitsToDouble(r.readLong());
    }

    private int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }

    private int getSerialTypeSize(int type) {
        return switch (type) {
            case 0, 8, 9 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 6;
            case 6, 7 -> 8;
            default -> type % 2 == 0 ? (type - 12) / 2 : (type - 13) / 2;
        };
    }

    // ============================ Varints ============================

    private byte[] encodeVarInt(long value) {
        if (value < 0) throw new IllegalArgumentException("varint cannot encode negative values: " + value);
        if (value == 0) return new byte[]{0};
        List<Byte> bytes = new ArrayList<>();
        while (value > 0) {
            bytes.add(0, (byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        bytes.set(bytes.size() - 1, (byte) (bytes.get(bytes.size() - 1) & 0x7F));
        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) res[i] = bytes.get(i);
        return res;
    }

    private int varintLen(long value) {
        int len = 0;
        do {
            value >>>= 7;
            len++;
        } while (value > 0);
        return len;
    }
}
