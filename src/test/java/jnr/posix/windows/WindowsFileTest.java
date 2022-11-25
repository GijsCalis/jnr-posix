package jnr.posix.windows;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import jnr.posix.DummyPOSIXHandler;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.WindowsPOSIX;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WindowsFileTest {
    private static POSIX posix;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File tempDir;

    @BeforeClass
    public static void setUpClass() throws Exception {
        posix = POSIXFactory.getPOSIX(new DummyPOSIXHandler(), true);
    }

    @Before
    public void setup() {
        tempDir = tempFolder.getRoot();
    }

    private class Pair {
        public File base;
        public File leaf;
        public Pair(File base, File leaf) {
            this.base = base;
            this.leaf = leaf;
        }

        public void cleanup() {
            cleanup(base);
        }

        public void cleanup(File node) {
            if (node.isDirectory()) {
                File[] files = node.listFiles();
                if (files != null) {
                    for(File file: files) {
                        cleanup(file);
                    }
                }
            }
            node.delete();
        }
    }
    // FIXME: This is a broken method since it does not delete any of the generated dirs.
    private static final String DIR_NAME = "0123456789";
    private Pair makeLongPath() throws IOException {
        File tmp = Files.createTempDirectory("temp" + Long.toHexString(System.nanoTime())).toFile();

        StringBuilder buf = new StringBuilder(DIR_NAME);
        for (int i = 0; i < 30; i++) {
            buf.append(DIR_NAME).append('/');
        }
        File tmp2 = new File(tmp, buf.toString());
        tmp2.mkdirs();

        return new Pair(tmp, tmp2);
    }

    @Test
    public void testLowercaseDriveLetter() throws Throwable {
        // FIXME: Not all systems have a C drive but nearly all do
        FileStat st = posix.stat("c:/");
        assertTrue(st.dev() == 2);
        assertTrue(st.rdev() == 2);
    }

    @Test
    public void testFakeUID() throws Throwable {
        File f = File.createTempFile("stat", null);

        try {
            FileStat st = posix.stat(f.getAbsolutePath());
            // Check for default value defined in AbstractJavaFileStat#uid() and .
            assertEquals(-1, st.uid());
        } finally {
            f.delete();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGidUnsupported() throws IOException {
        File f = File.createTempFile("stat", null);

        try {
            FileStat st = posix.stat(f.getAbsolutePath());
            // Check for default value defined in AbstractJavaFileStat#uid() and .
            st.gid();
        } finally {
            f.delete();
        }
    }

    @Test
    public void testExecutableSuffixesAreExecutable() throws Throwable {
        File f = File.createTempFile("stat", ".exe");

        try {
            FileStat st = posix.stat(f.getAbsolutePath());
            assertEquals("100755", Integer.toOctalString(st.mode()));
            assertTrue(st.isExecutable());
        } finally {
            f.delete();
        }

        f = File.createTempFile("STAT", ".EXE");

        try {
            FileStat st = posix.stat(f.getAbsolutePath());
            assertEquals("100755", Integer.toOctalString(st.mode()));
            assertTrue(st.isExecutable());
        } finally {
            f.delete();
        }

    }

    @Test
    public void testBlocksAndBlockSizeReturn() throws Throwable {
        File f = File.createTempFile("stat", null);

        try {
            FileStat st = posix.stat(f.getAbsolutePath());
            // defined in jnr.posix.AbstractJavaFileStat#blockSize()
            assertEquals(4096, st.blockSize());
        } finally {
            f.delete();
        }
    }

    @Test
    public void testHardlinkNoLinks() throws IOException {
        Path tempPath = tempFolder.getRoot().toPath();

        Path nolinks = tempPath.resolve("nolinks");
        Files.createFile(nolinks);

        FileStat stNolinks = posix.stat(nolinks.toString());
        assertEquals(1, stNolinks.nlink());
    }

    @Test
    public void testHardlinksInSameDir() throws IOException {
        Path tempPath = tempFolder.getRoot().toPath();

        Path linkSource = tempPath.resolve("linkSource");
        Files.createFile(linkSource);
        Path linkDest = tempPath.resolve("linkDest");

        Files.createLink(linkDest, linkSource);

        FileDescriptor fdLink1Source = new FileInputStream(linkSource.toString()).getFD();
        FileDescriptor fdLink1Dest = new FileInputStream(linkDest.toString()).getFD();
        FileStat stLink1Source = posix.fstat(fdLink1Source);
        FileStat stLink1Dest = posix.fstat(fdLink1Dest);

        assertEquals(2, stLink1Source.nlink());
        assertEquals(2, stLink1Dest.nlink());
        assertEquals(stLink1Source.ino(), stLink1Dest.ino());
    }

    @Test
    public void testHardlinkInSubdir() throws IOException {
        Path tempPath = tempFolder.getRoot().toPath();

        Path linkSource = tempPath.resolve("linkSource");
        Files.createFile(linkSource);
        Path subDir = tempPath.resolve("sub");
        Files.createDirectory(subDir);
        Path linkDest = subDir.resolve("linkDest");
        Files.createLink(linkDest, linkSource);

        FileDescriptor fdLink2Source = new FileInputStream(linkSource.toString()).getFD();
        FileDescriptor fdLink2Dest = new FileInputStream(linkDest.toString()).getFD();
        FileStat stLink2Source = posix.fstat(fdLink2Source);
        FileStat stLink2Dest = posix.fstat(fdLink2Dest);

        assertEquals(2, stLink2Source.nlink());
        assertEquals(2, stLink2Dest.nlink());
        assertEquals(stLink2Source.ino(), stLink2Dest.ino());
    }

    @Test
        public void testLongFileRegular() throws Throwable {
        Pair pair = makeLongPath();
        String path = pair.leaf.getAbsolutePath();
        try {
            FileStat st = posix.stat(path);
            assertNotNull("posix.stat failed", st);

            FileStat stat = posix.allocateStat();
            int result = posix.stat(path, stat);
            assertNotNull("posix.stat failed", stat);
            assertEquals(0, result);
        } finally {
            pair.cleanup();
        }
    }

    @Test
    public void testLongFileUNC() throws Throwable {
        Pair pair = makeLongPath();
        String absolutePath = pair.leaf.getAbsolutePath();
        char letter = absolutePath.charAt(0);
        String path = absolutePath.replace(absolutePath.substring(0,2), "\\\\localhost\\" + letter + "$");
        try {
            FileStat st = posix.stat(path);
            assertNotNull("posix.stat failed", st);

            FileStat stat = posix.allocateStat();
            int result = posix.stat(path, stat);
            assertNotNull("posix.stat failed", stat);
            assertEquals(0, result);
        } finally {
            pair.cleanup();
        }
    }

    @Test
    public void statUNCFile() throws Throwable {
        File f = File.createTempFile("stat", null);
        String absolutePath = f.getAbsolutePath();
        char letter = absolutePath.charAt(0);
        String path = absolutePath.replace(absolutePath.substring(0,2), "\\\\localhost\\" + letter + "$");
        try {
            FileStat st = posix.stat(path);
            assertNotNull("posix.stat failed", st);

            FileStat stat = posix.allocateStat();
            int result = posix.stat(path, stat);
            assertNotNull("posix.stat failed", stat);
            assertEquals(0, result);
        } finally {
            f.delete();
        }
    }

    @Test
    public void unlinkTestWindows() throws Throwable {
        File tmp = File.createTempFile("unlinkTest", "tmp");
        RandomAccessFile raf = new RandomAccessFile(tmp, "rw");

        raf.write("hello".getBytes());

        // Windows won't allow you to delete open files, so we must
        // close the handle before trying to delete it.  Unfortunately,
        // this also means we're unable to write to the handle afterwards
        // as we do with the non-Windows test.
        raf.close();

        int res = posix.unlink(tmp.getCanonicalPath());

        assertEquals(0, res);
        assertFalse(tmp.exists());
    }

    @Test
    public void testFindFirstFile() throws Throwable {
        File f = File.createTempFile("stat", null);
        try {

            POSIX posix = POSIXFactory.getNativePOSIX();
            FileStat stat = posix.allocateStat();
            int result = ((WindowsPOSIX) posix).findFirstFile(f.getAbsolutePath(), stat);

            assertEquals(0, result);
            assertTrue(stat.isFile());
        } finally {
            f.delete();
        }
    }

    @Test
    public void testFindFirstFileBogusFile() throws Throwable {
        POSIX posix = POSIXFactory.getNativePOSIX();
        FileStat stat = posix.allocateStat();
        int result = ((WindowsPOSIX) posix).findFirstFile("sdjfhjfsdfhdsdfhsdj", stat);
        assertTrue(result < 0);
    }

    @Test
    public void utimensatWindows() throws Throwable {
        File file = File.createTempFile("utimensat", null);
        try {
            FileStat fileStat = posix.stat(file.getPath());

            long atimeSeconds = fileStat.atime() + 1;
            long mtimeSeconds = fileStat.mtime() - 1;

            // Windows precision is 100 ns
            long atimeNanoSeconds = 123456700;
            long mtimeNanoSeconds = 987654300;

            // dirfd is ignored when passing an absolute path
            // flag can be used to update symlinks
            posix.utimensat(0,
                    file.getAbsolutePath(),
                    new long[]{atimeSeconds, atimeNanoSeconds},
                    new long[]{mtimeSeconds, mtimeNanoSeconds},
                    0);

            fileStat = posix.stat(file.getPath());
            assertEquals("access time should be updated", atimeSeconds, fileStat.atime());
            assertEquals("modification time should be updated", mtimeSeconds, fileStat.mtime());
        } finally {
            file.delete();
        }
    }
    
    @Test
    public void utimensatWindowsCurrentTime() throws Throwable {
        File file = File.createTempFile("file", null);
        try {
            FileStat fileStat = posix.stat(file.getPath());
            long atimeSeconds = fileStat.atime();
            long mtimeSeconds = fileStat.mtime();
            long atimeSecondsInPast = atimeSeconds - 1000;
            long mtimeSecondsInPast = mtimeSeconds - 1000;

            posix.utimensat(0,
                    file.getAbsolutePath(),
                    new long[]{atimeSecondsInPast, 0},
                    new long[]{mtimeSecondsInPast, 0}, 0);
            fileStat = posix.stat(file.getPath());
            assertEquals("access time should be updated", fileStat.atime(), atimeSecondsInPast);
            assertEquals("modification time should be updated", fileStat.mtime(), mtimeSecondsInPast);

            posix.utimensat(0, file.getAbsolutePath(), null, null, 0);

            fileStat = posix.stat(file.getPath());
            assertTrue("access time should be updated to current time",
                    timeWithinRange(fileStat.atime(), atimeSeconds, 10));
            assertTrue("modification time should be updated to current time",
                    timeWithinRange(fileStat.mtime(), mtimeSeconds, 10));
        } finally {
            file.delete();
        }
    }

    @Test
    public void utimenWindowsCurrentTime() throws Throwable {
        File file = File.createTempFile("file", null);
        try {
            FileStat fileStat = posix.stat(file.getPath());
            long atimeSeconds = fileStat.atime();
            long mtimeSeconds = fileStat.mtime();
            long atimeSecondsInPast = atimeSeconds - 1000;
            long mtimeSecondsInPast = mtimeSeconds - 1000;

            posix.utimes(file.getAbsolutePath(),
                    new long[]{atimeSecondsInPast, 0},
                    new long[]{mtimeSecondsInPast, 0});
            fileStat = posix.stat(file.getPath());
            assertEquals("access time should be updated",
                    fileStat.atime(), atimeSecondsInPast);
            assertEquals("modification time should be updated",
                    fileStat.mtime(), mtimeSecondsInPast);

            posix.utimes(file.getAbsolutePath(), null, null);

            fileStat = posix.stat(file.getPath());
            assertTrue("access time should be updated to current time",
                    timeWithinRange(fileStat.atime(), atimeSeconds, 10));
            assertTrue("modification time should be updated to current time",
                    timeWithinRange(fileStat.mtime(), mtimeSeconds, 10));
        } finally {
            file.delete();
        }
    }

    private boolean timeWithinRange(long actual, long expected, long precision) {
        return actual > (expected - precision) && actual < (expected + precision);
    }
}
