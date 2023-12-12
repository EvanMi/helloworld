package com.yumi.http.client.util;

import java.io.Serializable;

public class ByteArrayBuffer implements Serializable {
    private static final long serialVersionUID = 4359112959524048036L;

    private byte[] buffer;
    private int len;

    public ByteArrayBuffer(final int capacity) {
        super();
        this.buffer = new byte[capacity];
    }

    private void expand(final int newLen) {
        final byte[] newBuffer = new byte[Math.max(this.buffer.length << 1, newLen)];
        System.arraycopy(this.buffer, 0, newBuffer, 0, this.len);
        this.buffer = newBuffer;
    }

    public void append(final byte[] b, final int off, final int len) {
        if (b == null) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (len == 0) {
            return;
        }
        final int newLen = this.len + len;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        System.arraycopy(b, off, this.buffer, this.len, len);
        this.len = newLen;
    }

    public void append(final int b) {
        final int newLen = this.len + 1;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        this.buffer[this.len] = (byte)b;
        this.len = newLen;
    }

    public void append(final char[] b, final int off, final int len) {
        if (b == null) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (len == 0) {
            return;
        }
        final int oldLen = this.len;
        final int newLen = oldLen + len;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }

        for (int i1 = off, i2 = oldLen; i2 < newLen; i1++, i2++) {
            final int c = b[i1];
            if ((c >= 0x20 && c <= 0x7E) || // Visible ASCII
                    (c >= 0xA0 && c <= 0xFF) || // Visible ISO-8859-1
                    c == 0x09) {                // TAB
                this.buffer[i2] = (byte) c;
            } else {
                this.buffer[i2] = '?';
            }
        }
        this.len = newLen;
    }

    public void append(final CharArrayBuffer b, final int off, final int len) {
        if (b == null) {
            return;
        }
        append(b.buffer(), off, len);
    }

    public void clear() {
        this.len = 0;
    }

    public byte[] toByteArray() {
        final byte[] b = new byte[this.len];
        if (this.len > 0) {
            System.arraycopy(this.buffer, 0, b, 0, this.len);
        }
        return b;
    }

    public int byteAt(final int i) {
        return this.buffer[i];
    }

    public int capacity() {
        return this.buffer.length;
    }
    public int length() {
        return this.len;
    }

    public void ensureCapacity(final int required) {
        if (required <= 0) {
            return;
        }
        final int available = this.buffer.length - this.len;
        if (required > available) {
            expand(this.len + required);
        }
    }
    public byte[] buffer() {
        return this.buffer;
    }

    public void setLength(final int len) {
        if (len < 0 || len > this.buffer.length) {
            throw new IndexOutOfBoundsException("len: "+len+" < 0 or > buffer len: "+this.buffer.length);
        }
        this.len = len;
    }

    public boolean isEmpty() {
        return this.len == 0;
    }

    public boolean isFull() {
        return this.len == this.buffer.length;
    }

    public int indexOf(final byte b, final int from, final int to) {
        int beginIndex = from;
        if (beginIndex < 0) {
            beginIndex = 0;
        }
        int endIndex = to;
        if (endIndex > this.len) {
            endIndex = this.len;
        }
        if (beginIndex > endIndex) {
            return -1;
        }
        for (int i = beginIndex; i < endIndex; i++) {
            if (this.buffer[i] == b) {
                return i;
            }
        }
        return -1;
    }

    public int indexOf(final byte b) {
        return indexOf(b, 0, this.len);
    }

}
