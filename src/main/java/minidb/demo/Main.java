package minidb.demo;

import minidb.storage.Pager;

public class Main {
    public static void main(String[] args) throws Exception {
        try (Pager pager = new Pager("test.db")) {
            int p = pager.allocatePage();
            byte[] data = new byte[Pager.PAGE_SIZE];
            byte[] msg = "hello minidb".getBytes();
            System.arraycopy(msg, 0, data, 0, msg.length);
            pager.writePage(p, data);

            byte[] read = pager.readPage(p);
            System.out.println(new String(read, 0, msg.length));
            System.out.println("Total pages: " + pager.pageCount());
        }
    }
}