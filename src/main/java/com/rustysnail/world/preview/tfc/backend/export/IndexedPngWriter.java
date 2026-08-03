package com.rustysnail.world.preview.tfc.backend.export;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class IndexedPngWriter implements Closeable
{
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final int IDAT_CHUNK_SIZE = 64 * 1024;

    private final DataOutputStream output;
    private final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
    private final byte[] compressed = new byte[IDAT_CHUNK_SIZE];
    private final int width;
    private final int height;
    private int rowsWritten;
    private boolean finished;

    public IndexedPngWriter(Path path, int width, int height, int[] paletteRgb) throws IOException
    {
        if (width <= 0 || height <= 0 || paletteRgb == null || paletteRgb.length < 1 || paletteRgb.length > 256)
        {
            throw new IllegalArgumentException("Invalid indexed PNG geometry or palette");
        }
        this.width = width;
        this.height = height;
        this.output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)));
        this.output.write(SIGNATURE);
        writeHeader();
        writePalette(paletteRgb);
    }

    public void writeRows(byte[] filteredRows, int rowCount) throws IOException
    {
        if (this.finished || rowCount < 0 || this.rowsWritten + rowCount > this.height
            || filteredRows.length != Math.multiplyExact(rowCount, this.width + 1))
        {
            throw new IllegalArgumentException("Invalid indexed PNG row batch");
        }
        this.deflater.setInput(filteredRows);
        drain(false);
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
            throw new IllegalStateException("PNG is incomplete: " + this.rowsWritten + "/" + this.height + " rows");
        }
        this.deflater.finish();
        drain(true);
        writeChunk("IEND", new byte[0]);
        this.finished = true;
        this.output.flush();
    }

    @Override
    public void close() throws IOException
    {
        this.deflater.end();
        this.output.close();
    }

    private void writeHeader() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(13);
        try (DataOutputStream data = new DataOutputStream(bytes))
        {
            data.writeInt(this.width);
            data.writeInt(this.height);
            data.writeByte(8);
            data.writeByte(3);
            data.writeByte(0);
            data.writeByte(0);
            data.writeByte(0);
        }
        writeChunk("IHDR", bytes.toByteArray());
    }

    private void writePalette(int[] paletteRgb) throws IOException
    {
        byte[] bytes = new byte[paletteRgb.length * 3];
        for (int i = 0; i < paletteRgb.length; i++)
        {
            int rgb = paletteRgb[i];
            bytes[i * 3] = (byte) (rgb >>> 16);
            bytes[i * 3 + 1] = (byte) (rgb >>> 8);
            bytes[i * 3 + 2] = (byte) rgb;
        }
        writeChunk("PLTE", bytes);
    }

    private void drain(boolean finishing) throws IOException
    {
        while (finishing ? !this.deflater.finished() : !this.deflater.needsInput())
        {
            int count = this.deflater.deflate(this.compressed);
            if (count == 0)
            {
                break;
            }
            byte[] chunk = new byte[count];
            System.arraycopy(this.compressed, 0, chunk, 0, count);
            writeChunk("IDAT", chunk);
        }
    }

    private void writeChunk(String type, byte[] data) throws IOException
    {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        this.output.writeInt(data.length);
        this.output.write(typeBytes);
        this.output.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        this.output.writeInt((int) crc.getValue());
    }
}
