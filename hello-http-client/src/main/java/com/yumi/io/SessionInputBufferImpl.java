package com.yumi.io;

import com.yumi.protocol.Http;
import com.yumi.util.ByteArrayBuffer;
import com.yumi.util.CharArrayBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

public class SessionInputBufferImpl implements SessionInputBuffer, BufferInfo{

    private final byte[] buffer;
    private final ByteArrayBuffer lineBuffer;
    private final int minChunkLimit;
    private final CharsetDecoder decoder;

    private InputStream inStream;
    private int bufferPos;
    private int bufferLen;
    private CharBuffer cBuf;

    public SessionInputBufferImpl(
            final int bufferSize,
            final int minChunkLimit,
            final CharsetDecoder charDecoder) {
        this.buffer = new byte[bufferSize];
        this.bufferPos = 0;
        this.bufferLen = 0;
        this.minChunkLimit = minChunkLimit >= 0 ? minChunkLimit : 512;
        this.lineBuffer = new ByteArrayBuffer(bufferSize);
        this.decoder = charDecoder;
    }

    public SessionInputBufferImpl(final int bufferSize) {
        this(bufferSize, bufferSize, null);
    }

    public void bind(final InputStream inputStream) {
        this.inStream = inputStream;
    }

    public boolean isBound() {
        return this.inStream != null;
    }



    @Override
    public int capacity() {
        return this.buffer.length;
    }

    @Override
    public int length() {
        return this.bufferLen - this.bufferPos;
    }

    @Override
    public int available() {
        return capacity() - length();
    }

    private int streamRead(final byte[] b, final int off, final int len) throws IOException {
        return this.inStream.read(b, off, len);
    }

    public int fillBuffer() throws IOException {
        // compact the buffer if necessary
        if (this.bufferPos > 0) {
            final int len = this.bufferLen - this.bufferPos;
            if (len > 0) {
                System.arraycopy(this.buffer, this.bufferPos, this.buffer, 0, len);
            }
            this.bufferPos = 0;
            this.bufferLen = len;
        }
        final int readLen;
        final int off = this.bufferLen;
        final int len = this.buffer.length - off;
        readLen = streamRead(this.buffer, off, len);
        if (readLen == -1) {
            return -1;
        }
        this.bufferLen = off + readLen;
        return readLen;
    }

    public boolean hasBufferedData() {
        return this.bufferPos < this.bufferLen;
    }

    public void clear() {
        this.bufferPos = 0;
        this.bufferLen = 0;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return 0;
        }
        if (hasBufferedData()) {
            final int chunk = Math.min(len, this.bufferLen - this.bufferPos);
            System.arraycopy(this.buffer, this.bufferPos, b, off, chunk);
            this.bufferPos += chunk;
            return chunk;
        }
        // If the remaining capacity is big enough, read directly from the
        // underlying input stream bypassing the buffer.
        if (len > this.minChunkLimit) {
            return streamRead(b, off, len);
        }
        // otherwise read to the buffer first
        while (!hasBufferedData()) {
            final int noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        final int chunk = Math.min(len, this.bufferLen - this.bufferPos);
        System.arraycopy(this.buffer, this.bufferPos, b, off, chunk);
        this.bufferPos += chunk;
        return chunk;
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        int noRead;
        while (!hasBufferedData()) {
            noRead = fillBuffer();
            if (noRead == -1) {
                return -1;
            }
        }
        return this.buffer[this.bufferPos++] & 0xff;
    }



    @Override
    public int readLine(CharArrayBuffer charBuffer) throws IOException {
        // TODO: 2023/12/8 写死了
        final int maxLineLen =65535;
        int noRead = 0;
        boolean retry = true;
        while (retry) {
            // attempt to find end of line (LF)
            int pos = -1;
            for (int i = this.bufferPos; i < this.bufferLen; i++) {
                if (this.buffer[i] == Http.LF) {
                    pos = i;
                    break;
                }
            }

           final int currentLen = this.lineBuffer.length()
                   + (pos >= 0 ? pos : this.bufferLen) - this.bufferPos;
           if (currentLen >= maxLineLen) {
               throw new IllegalStateException("Maximum line length limit exceeded");
           }

            if (pos != -1) {
                // end of line found.
                if (this.lineBuffer.isEmpty()) {
                    // the entire line is preset in the read buffer
                    return lineFromReadBuffer(charBuffer, pos);
                }
                retry = false;
                final int len = pos + 1 - this.bufferPos;
                this.lineBuffer.append(this.buffer, this.bufferPos, len);
                this.bufferPos = pos + 1;
            } else {
                // end of line not found
                if (hasBufferedData()) {
                    final int len = this.bufferLen - this.bufferPos;
                    this.lineBuffer.append(this.buffer, this.bufferPos, len);
                    this.bufferPos = this.bufferLen;
                }
                noRead = fillBuffer();
                if (noRead == -1) {
                    retry = false;
                }
            }
        }
        if (noRead == -1 && this.lineBuffer.isEmpty()) {
            // indicate the end of stream
            return -1;
        }
        return lineFromLineBuffer(charBuffer);
    }

    private int lineFromLineBuffer(final CharArrayBuffer charBuffer)
            throws IOException {
        // discard LF if found
        int len = this.lineBuffer.length();
        if (len > 0) {
            if (this.lineBuffer.byteAt(len - 1) == Http.LF) {
                len--;
            }
            // discard CR if found
            if (len > 0) {
                if (this.lineBuffer.byteAt(len - 1) == Http.CR) {
                    len--;
                }
            }
        }
        if (this.decoder == null) {
            charBuffer.append(this.lineBuffer, 0, len);
        } else {
            final ByteBuffer bBuf =  ByteBuffer.wrap(this.lineBuffer.buffer(), 0, len);
            len = appendDecoded(charBuffer, bBuf);
        }
        this.lineBuffer.clear();
        return len;
    }

    private int lineFromReadBuffer(final CharArrayBuffer charBuffer, final int position)
            throws IOException {
        int pos = position;
        final int off = this.bufferPos;
        int len;
        this.bufferPos = pos + 1;
        if (pos > off && this.buffer[pos - 1] == Http.CR) {
            // skip CR if found
            pos--;
        }
        len = pos - off;
        if (this.decoder == null) {
            charBuffer.append(this.buffer, off, len);
        } else {
            final ByteBuffer bBuf =  ByteBuffer.wrap(this.buffer, off, len);
            len = appendDecoded(charBuffer, bBuf);
        }
        return len;
    }

    private int appendDecoded(
            final CharArrayBuffer charBuffer, final ByteBuffer bBuf) throws IOException {
        if (!bBuf.hasRemaining()) {
            return 0;
        }
        if (this.cBuf == null) {
            this.cBuf = CharBuffer.allocate(1024);
        }
        this.decoder.reset();
        int len = 0;
        while (bBuf.hasRemaining()) {
            final CoderResult result = this.decoder.decode(bBuf, this.cBuf, true);
            len += handleDecodingResult(result, charBuffer);
        }
        final CoderResult result = this.decoder.flush(this.cBuf);
        len += handleDecodingResult(result, charBuffer);
        this.cBuf.clear();
        return len;
    }

    private int handleDecodingResult(
            final CoderResult result,
            final CharArrayBuffer charBuffer) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.cBuf.flip();
        final int len = this.cBuf.remaining();
        while (this.cBuf.hasRemaining()) {
            charBuffer.append(this.cBuf.get());
        }
        this.cBuf.compact();
        return len;
    }

    @Override
    public String readLine() throws IOException {
        final CharArrayBuffer charBuffer = new CharArrayBuffer(64);
        final int readLen = readLine(charBuffer);
        return readLen != -1 ? charBuffer.toString() : null;
    }
}
