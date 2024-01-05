package com.yumi.step2;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.yumi.step2.config.FlushDiskType;
import com.yumi.step2.util.LibC;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MappedFile extends ReferenceResource {
    public static final int OS_PAGE_SIZE = 1024 * 4;
    private static final int END_FILE_MIN_BLANK_LENGTH = 4 + 4;
    private static final AtomicLong TOTAL_MAPPED_VIRTUAL_MEMORY = new AtomicLong(0);
    private static final AtomicInteger TOTAL_MAPPED_FILES = new AtomicInteger(0);
    protected final AtomicInteger wrotePosition = new AtomicInteger(0);
    protected final AtomicInteger committedPosition = new AtomicInteger(0);
    private final AtomicInteger flushedPosition = new AtomicInteger(0);
    protected int fileSize;
    protected FileChannel fileChannel;
    protected ByteBuffer writeBuffer = null;
    private String fileName;
    private long fileFromOffset;
    private File file;
    private MappedByteBuffer mappedByteBuffer;
    private volatile long storeTimestamp = 0;
    private boolean firstCreateInQueue = false;

    public MappedFile() {
    }

    public MappedFile(final String fileName, final int fileSize) throws IOException {
        init(fileName, fileSize);
    }

    private void init(final String fileName, final int fileSize) throws IOException {
        // TODO: 2023/12/14 改为池
        this.writeBuffer = ByteBuffer.allocateDirect(fileSize);

        this.fileName = fileName;
        this.fileSize = fileSize;
        this.file = new File(fileName);
        this.fileFromOffset = Long.parseLong(this.file.getName());
        boolean ok = false;

        ensureDirOK(this.file.getParent());

        try {
            this.fileChannel = new RandomAccessFile(this.file, "rw").getChannel();
            this.mappedByteBuffer = this.fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(fileSize);
            TOTAL_MAPPED_FILES.incrementAndGet();
            ok = true;
        } catch (FileNotFoundException e) {
            System.err.println("Failed to create file " + this.fileName);
            e.printStackTrace();
            throw e;
        } catch (IOException e) {
            System.err.println("Failed to map file " + this.fileName);
            e.printStackTrace();
            throw e;
        } finally {
            if (!ok && this.fileChannel != null) {
                this.fileChannel.close();
            }
        }
    }

    public static void ensureDirOK(final String dirName) {
        if (dirName != null) {
            File f = new File(dirName);
            if (!f.exists()) {
                boolean result = f.mkdirs();
                System.out.println(dirName + " mkdir " + (result ? "OK" : "Failed"));
            }
        }
    }

    public long getLastModifiedTimestamp() {
        return this.file.lastModified();
    }

    public int getFileSize() {
        return fileSize;
    }

    public FileChannel getFileChannel() {
        return fileChannel;
    }

    public boolean appendMsg(String msg) {
        int currentPos = this.wrotePosition.get();
        if (currentPos < this.fileSize) {
            ByteBuffer byteBuffer = writeBuffer != null ? writeBuffer.slice() : this.mappedByteBuffer.slice();
            byteBuffer.position(currentPos);
            int maxBlank = this.fileSize - currentPos;
            byte[] msgBytes = msg.getBytes();
            //4为整数类型长度
            if ((4 + msgBytes.length + END_FILE_MIN_BLANK_LENGTH) > maxBlank) {
                ByteBuffer bf = ByteBuffer.allocate(maxBlank);
                bf.putInt(maxBlank);
                bf.putInt(-875286124);//空
                bf.flip();
                bf.limit(maxBlank);
                byteBuffer.put(bf.array(), 0, maxBlank);
                this.wrotePosition.addAndGet(maxBlank);
                this.storeTimestamp = System.currentTimeMillis();
                return false;
            } else {
                byteBuffer.putInt(msgBytes.length);
                byteBuffer.put(msgBytes, 0, msgBytes.length);
                this.wrotePosition.addAndGet(4 + msgBytes.length);
                this.storeTimestamp = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    public boolean appendMsg(final byte[] data) {
        int currentPos = this.wrotePosition.get();

        if ((currentPos + data.length) <= this.fileSize) {
            try {
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data));
            } catch (Throwable e) {
                //
            }
            this.wrotePosition.addAndGet(data.length);
            return true;
        }
        return false;
    }

    public boolean appendMsg(final byte[] data, int offset, int length) {
        int currentPos = this.wrotePosition.get();

        if ((currentPos + length) <= this.fileSize) {
            try {
                this.fileChannel.position(currentPos);
                this.fileChannel.write(ByteBuffer.wrap(data, offset, length));
            } catch (Throwable e) {
                //
            }
            this.wrotePosition.addAndGet(data.length);
            return true;
        }
        return false;
    }

    public int flush(final int flushLeastPages) {
        if (this.isAbleToFlush(flushLeastPages)) {
            if (this.hold()) {
                int value = getReadPosition();
                try {
                    if (writeBuffer != null && fileChannel.position() != 0) {
                        fileChannel.force(false);
                    } else {
                        mappedByteBuffer.force();
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                //todo value的线程安全问题
                this.flushedPosition.set(value);
                this.release();
            }  else {
                System.out.println("in flush, hold failed, flush offset = " + this.flushedPosition.get());
                this.flushedPosition.set(getReadPosition());
            }

        }
        return this.getFlushedPosition();
    }

    public int commit(final int commitLeastPages) {
        if (writeBuffer == null) {
            return this.wrotePosition.get();
        }
        if (this.isAbleToCommit(commitLeastPages)) {
            if (this.hold()) {
                commit0();
                this.release();
            } else {
                System.out.println("in commit, hold failed, commit offset = " + this.committedPosition.get());
            }
        }
        if (writeBuffer != null && this.fileSize == this.committedPosition.get()) {
            writeBuffer = null;
        }
        //todo 线程安全问题
        return this.committedPosition.get();
    }

    protected boolean isAbleToCommit(final int commitLeastPages) {
        int lastCommittedPosition = this.committedPosition.get();
        int writePos = this.wrotePosition.get();

        if (this.isFull()) {
            return true;
        }

        if (commitLeastPages > 0) {
            return ((writePos / OS_PAGE_SIZE) - (lastCommittedPosition / OS_PAGE_SIZE) >= commitLeastPages);
        }
        return writePos > lastCommittedPosition;
    }

    protected void commit0() {
        int writePos = this.wrotePosition.get();
        int lastCommittedPosition = this.committedPosition.get();

        if (writePos > lastCommittedPosition) {
            try {
                ByteBuffer byteBuffer = this.writeBuffer.slice();
                byteBuffer.position(lastCommittedPosition);
                byteBuffer.limit(writePos);
                this.fileChannel.position(lastCommittedPosition);
                this.fileChannel.write(byteBuffer);
                this.committedPosition.set(writePos);
            } catch (Throwable e) {
                //
            }
        }

    }

    public int getFlushedPosition() {
        return flushedPosition.get();
    }

    public void setFlushedPosition(int pos) {
        this.flushedPosition.set(pos);
    }
    private boolean isAbleToFlush(final int flushLeastPages) {
        int flush = this.flushedPosition.get();
        int write = getReadPosition();

        if (this.isFull()) {
            return true;
        }

        if (flushLeastPages > 0) {
            return ((write / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE) >= flushLeastPages);
        }

        return write > flush;
    }

    public int getReadPosition() {
        return this.writeBuffer == null ? this.wrotePosition.get() : this.committedPosition.get();
    }

    public boolean isFull() {
        return this.fileSize == this.wrotePosition.get();
    }

    public SelectMappedBufferResult selectMappedBuffer(int pos, int size) {
        int readPosition = this.getReadPosition();
        if((pos + size) <= readPosition) {
            if (this.hold()) {
                ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
                byteBuffer.position(pos);
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(
                        this.fileFromOffset + pos,
                        byteBufferNew,
                        size,
                        this
                );
            } else {
                System.out.println("matched, but hold failed, request pos: " + pos + ", fileFromOffset: "
                        + this.fileFromOffset);
            }
        }
        return null;
    }

    public SelectMappedBufferResult selectMappedBuffer(int pos) {
        int readPosition = this.getReadPosition();
        if (pos < readPosition && pos >= 0) {
            if (this.hold()) {
                ByteBuffer byteBuffer = mappedByteBuffer.slice();
                byteBuffer.position(pos);
                int size = readPosition - pos;
                ByteBuffer byteBufferNew = byteBuffer.slice();
                byteBufferNew.limit(size);
                return new SelectMappedBufferResult(this.fileFromOffset + pos,
                        byteBufferNew,
                        size,
                        this);
            }
        }
        return null;
    }

    @Override
    public boolean cleanup(long currentRef) {
        if (this.isAvailable()) {
            return false;
        }
        if (this.isCleanupOver()) {
            return true;
        }
        clean(this.mappedByteBuffer);
        TOTAL_MAPPED_VIRTUAL_MEMORY.addAndGet(this.fileSize * (-1));
        TOTAL_MAPPED_FILES.decrementAndGet();
        return true;
    }

    public boolean destroy(final long intervalForcibly) {
        this.shutdown(intervalForcibly);
        if (this.isCleanupOver()) {
            try {
                this.fileChannel.close();
                long beginTime = System.currentTimeMillis();
                boolean result = this.file.delete();
                System.out.println("delete file[REF:" + this.getRefCount() + "] " + this.fileName
                        + (result ? " OK, " : " Failed, ") + "W:" + this.getWrotePosition() + " M:"
                        + this.getFlushedPosition() + ", "
                        + (System.currentTimeMillis() - beginTime));
            } catch (Exception e) {
                //
            }
        }
        return false;
    }

    public int getWrotePosition() {
        return wrotePosition.get();
    }

    public void setWrotePosition(int pos) {
        this.wrotePosition.set(pos);
    }

    public void setCommittedPosition(int pos) {
        this.committedPosition.set(pos);
    }

    public void warmMappedFile(FlushDiskType type, int pages) {
        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        int flush = 0;
        for (int i = 0; i < this.fileSize; i += MappedFile.OS_PAGE_SIZE) {
            byteBuffer.put(i, (byte) 0);
            // force flush when flush disk type is sync
            if (type == FlushDiskType.SYNC_FLUSH) {
                if ((i / OS_PAGE_SIZE) - (flush / OS_PAGE_SIZE) >= pages) {
                    flush = i;
                    mappedByteBuffer.force();
                }
            }
        }
        // force flush when prepare load finished
        if (type == FlushDiskType.SYNC_FLUSH) {
            mappedByteBuffer.force();
        }
        this.mlock();
    }

    public String getFileName() {
        return fileName;
    }

    public MappedByteBuffer getMappedByteBuffer() {
        return mappedByteBuffer;
    }

    public ByteBuffer sliceByteBuffer() {
        return this.mappedByteBuffer.slice();
    }

    public long getStoreTimestamp() {
        return storeTimestamp;
    }

    public boolean isFirstCreateInQueue() {
        return firstCreateInQueue;
    }

    public void setFirstCreateInQueue(boolean firstCreateInQueue) {
        this.firstCreateInQueue = firstCreateInQueue;
    }

    public void mlock() {
        final long beginTime = System.currentTimeMillis();
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        {
            LibC.INSTANCE.mlock(pointer, new NativeLong(this.fileSize));
        }

        {
            LibC.INSTANCE.madvise(pointer, new NativeLong(this.fileSize), LibC.MADV_WILLNEED);
        }
    }

    public void munlock() {
        final long address = ((DirectBuffer) (this.mappedByteBuffer)).address();
        Pointer pointer = new Pointer(address);
        LibC.INSTANCE.munlock(pointer, new NativeLong(this.fileSize));
    }

    File getFile() {
        return this.file;
    }

    @Override
    public String toString() {
        return this.fileName;
    }


    public static void clean(final ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect() || buffer.capacity() == 0)
            return;
        invoke(invoke(viewed(buffer), "cleaner"), "clean");
    }

    private static Object invoke(final Object target, final String methodName, final Class<?>... args) {
        return AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                try {
                    Method method = method(target, methodName, args);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static Method method(Object target, String methodName, Class<?>[] args)
            throws NoSuchMethodException {
        try {
            return target.getClass().getMethod(methodName, args);
        } catch (NoSuchMethodException e) {
            return target.getClass().getDeclaredMethod(methodName, args);
        }
    }

    private static ByteBuffer viewed(ByteBuffer buffer) {
        String methodName = "viewedBuffer";
        Method[] methods = buffer.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals("attachment")) {
                methodName = "attachment";
                break;
            }
        }

        ByteBuffer viewedBuffer = (ByteBuffer) invoke(buffer, methodName);
        if (viewedBuffer == null)
            return buffer;
        else
            return viewed(viewedBuffer);
    }

}
