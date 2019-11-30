package machine;

import nachos.machine.StubFileSystem;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;

public class PipeKernel implements FileSystem {
    public OpenFile open(String name, boolean truncate) {
        if (!checkName(name))
            return null;

        delay();

        try {
            return new StubFileSystem.StubOpenFile(name, truncate);
        }
        catch (IOException e) {
            return null;
        }
    }

    public int getOpenCount() {
        return openCount;
    }

    public boolean remove(String name) {
        if (!checkName(name))
            return false;

        delay();

        StubFileSystem.FileRemover fr = new StubFileSystem.FileRemover(new File(directory, name));
        privilege.doPrivileged(fr);
        return fr.successful;
    }

    private void delay() {
        long time = Machine.timer().getTime();
        int amount = 1000;
        ThreadedKernel.alarm.waitUntil(amount);
        Lib.assertTrue(Machine.timer().getTime() >= time + amount);
    }

    private class Pipe extends OpenFile {
        Pipe(final String name)
                throws IOException {
            super(PipeKernel.this, name);

            if (openCount == maxOpenFiles)
                throw new IOException();

            open = true;
            openCount++;
        }

        public int read(int pos, byte[] buf, int offset, int length) {
            if (!open)
                return -1;

            try {
                delay();

                file.seek(pos);
                return Math.max(0, file.read(buf, offset, length));
            }
            catch (IOException e) {
                return -1;
            }
        }

        public int write(int pos, byte[] buf, int offset, int length) {
            if (!open)
                return -1;

            try {
                delay();

                file.seek(pos);
                file.write(buf, offset, length);
                return length;
            }
            catch (IOException e) {
                return -1;
            }
        }

        public int length() {
            try {
                return (int) file.length();
            }
            catch (IOException e) {
                return -1;
            }
        }

        public void close() {
            if (open) {
                open = false;
                openCount--;
            }

            try {
                file.close();
            }
            catch (IOException e) {
            }
        }

        private RandomAccessFile file = null;

        private boolean open = false;
    }

    public int openCount = 0;

    private static final int maxOpenFiles = 32;

    private Privilege privilege;

    private File directory;

    private static boolean checkName(String name) {
        char[] chars = name.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] < 0 || chars[i] >= allowedFileNameCharacters.length)
                return false;
            if (!allowedFileNameCharacters[(int) chars[i]])
                return false;
        }
        return true;
    }

    private static boolean[] allowedFileNameCharacters = new boolean[0x80];

    private static void reject(char c) {
        allowedFileNameCharacters[c] = false;
    }

    private static void allow(char c) {
        allowedFileNameCharacters[c] = true;
    }

    private static void reject(char first, char last) {
        for (char c = first; c <= last; c++)
            allowedFileNameCharacters[c] = false;
    }

    private static void allow(char first, char last) {
        for (char c = first; c <= last; c++)
            allowedFileNameCharacters[c] = true;
    }

    static {
        reject((char) 0x00, (char) 0x7F);

        allow('A', 'Z');
        allow('a', 'z');
        allow('0', '9');

        allow('-');
        allow('_');
        allow('.');
        allow(',');
    }
}
