package com.rustysnail.world.preview.tfc.backend.export;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class BinaryIndexedPngWriter implements Closeable
{
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final byte[] IHDR = "IHDR".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PLTE = "PLTE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IDAT = "IDAT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IEND = "IEND".getBytes(StandardCharsets.US_ASCII);
    private static final int IDAT_CHUNK_SIZE = 64 * 1024;

    private final int width;
    private final int height;
    private final int filteredRowSize;
    private final DataOutputStream output;
    private final Deflater deflater;
    private final DeflaterOutputStream compressed;
    private final ChunkedIdatOutputStream idat;
    private int rowsWritten;
    private boolean finished;

    public BinaryIndexedPngWriter(Path path, int width, int height, int landRgb, int waterRgb) throws IOException
    {
        if (width <= 0 || height <= 0)
        {
            throw new IllegalArgumentException("PNG dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.filteredRowSize = 1 + ((width + 7) >>> 3);
        this.output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path), IDAT_CHUNK_SIZE));
        this.output.write(SIGNATURE);
        writeHeader();
        writePalette(landRgb, waterRgb);
        this.idat = new ChunkedIdatOutputStream(this.output, IDAT_CHUNK_SIZE);
        this.deflater = new Deflater(Deflater.BEST_SPEED);
        this.compressed = new DeflaterOutputStream(this.idat, this.deflater, IDAT_CHUNK_SIZE);
    }

    public int filteredRowSize()
    {
        return this.filteredRowSize;
    }

    public void writeRows(byte[] filteredRows, int rowCount) throws IOException
    {
        if (this.finished)
        {
            throw new IllegalStateException("PNG is already finished");
        }
        if (rowCount < 0 || this.rowsWritten + rowCount > this.height
            || filteredRows.length != Math.multiplyExact(rowCount, this.filteredRowSize))
        {
            throw new IllegalArgumentException("Invalid filtered PNG row buffer");
        }
        this.compressed.write(filteredRows);
        this.rowsWritten += rowCount;
    }

    public void finish() throws IOException
    {
        if (this.finished)
        {
            return;
        }
        if (this.rowsWritten != this.height)
        {
            throw new IllegalStateException("Expected " + this.height + " rows, received " + this.rowsWritten);
        }

        this.compressed.finish();
        this.compressed.flush();
        this.idat.finishChunks();
        writeChunk(this.output, IEND, new byte[0], 0, 0);
        this.output.flush();
        this.finished = true;
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            if (!this.finished)
            {
                this.compressed.close();
            }
        }
        finally
        {
            this.deflater.end();
            this.output.close();
        }
    }

    private void writeHeader() throws IOException
    {
        byte[] header = new byte[13];
        writeInt(header, 0, this.width);
        writeInt(header, 4, this.height);
        header[8] = 1; // one bit per pixel
        header[9] = 3; // indexed color
        header[10] = 0;
        header[11] = 0;
        header[12] = 0;
        writeChunk(this.output, IHDR, header, 0, header.length);
    }

    private void writePalette(int landRgb, int waterRgb) throws IOException
    {
        byte[] palette = {
            (byte) (landRgb >>> 16), (byte) (landRgb >>> 8), (byte) landRgb,
            (byte) (waterRgb >>> 16), (byte) (waterRgb >>> 8), (byte) waterRgb
        };
        writeChunk(this.output, PLTE, palette, 0, palette.length);
    }

    private static void writeInt(byte[] target, int offset, int value)
    {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static void writeChunk(DataOutputStream output, byte[] type, byte[] data, int offset, int length) throws IOException
    {
        output.writeInt(length);
        output.write(type);
        output.write(data, offset, length);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data, offset, length);
        output.writeInt((int) crc.getValue());
    }

    private static final class ChunkedIdatOutputStream extends OutputStream
    {
        private final DataOutputStream output;
        private final byte[] buffer;
        private int position;

        private ChunkedIdatOutputStream(DataOutputStream output, int chunkSize)
        {
            this.output = output;
            this.buffer = new byte[chunkSize];
        }

        @Override
        public void write(int value) throws IOException
        {
            if (this.position == this.buffer.length)
            {
                flushChunk();
            }
            this.buffer[this.position++] = (byte) value;
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException
        {
            while (length > 0)
            {
                int copy = Math.min(length, this.buffer.length - this.position);
                System.arraycopy(source, offset, this.buffer, this.position, copy);
                this.position += copy;
                offset += copy;
                length -= copy;
                if (this.position == this.buffer.length)
                {
                    flushChunk();
                }
            }
        }

        private void finishChunks() throws IOException
        {
            flushChunk();
        }

        private void flushChunk() throws IOException
        {
            if (this.position > 0)
            {
                writeChunk(this.output, IDAT, this.buffer, 0, this.position);
                this.position = 0;
            }
        }
    }
}
