package minidb.storage;

import java.io.IOException;
import java.io.RandomAccessFile;

public class Pager implements AutoCloseable {
    public static final int PAGE_SIZE = 4096;
    private final RandomAccessFile file;
    private int numPages;

    public Pager(String path) throws IOException {
        this.file = new RandomAccessFile(path, "rw");
        this.numPages = (int) (file.length() / PAGE_SIZE);
    }

    public synchronized byte[] readPage(int pageNum) throws IOException {
        checkBounds(pageNum);
        byte[] buf = new byte[PAGE_SIZE];
        file.seek((long) pageNum * PAGE_SIZE);
        file.readFully(buf);
        return buf;
    }

    public synchronized void writePage(int pageNum, byte[] data) throws IOException {
        checkBounds(pageNum);
        if (data.length != PAGE_SIZE)
            throw new IllegalArgumentException("Page must be exactly " + PAGE_SIZE + " bytes");
        file.seek((long) pageNum * PAGE_SIZE);
        file.write(data);
    }

    public synchronized int allocatePage() throws IOException {
        int pageNum = numPages++;
        file.seek((long) pageNum * PAGE_SIZE);
        file.write(new byte[PAGE_SIZE]);
        return pageNum;
    }

    public synchronized int pageCount() { return numPages; }

    private void checkBounds(int pageNum) {
        if (pageNum < 0 || pageNum >= numPages)
            throw new IllegalArgumentException("Page " + pageNum + " out of range");
    }

    @Override
    public void close() throws IOException { file.close(); }
}