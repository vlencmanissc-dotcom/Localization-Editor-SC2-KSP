/*
 * Decompiled with CFR 0.152.
 */
package systems.crigges.jmpq3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class MpqFile {
    public static final int COMPRESSED = 512;
    public static final int ENCRYPTED = 65536;
    public static final int SINGLEUNIT = 0x1000000;
    public static final int ADJUSTED_ENCRYPTED = 131072;
    public static final int EXISTS = Integer.MIN_VALUE;
    public static final int DELETED = 0x2000000;
    private MappedByteBuffer buf;
    private BlockTable.Block block;
    private MpqCrypto crypto = null;
    private int sectorSize;
    private int offset;
    private int compSize;
    private int normalSize;
    private int flags;
    private int blockIndex;
    private String name;
    private int sectorCount;
    private int baseKey;
    private int sepIndex;

    public int getBlockIndex() {
        return this.blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public String toString() {
        return "MpqFile [sectorSize=" + this.sectorSize + ", offset=" + this.offset + ", compSize=" + this.compSize + ", normalSize=" + this.normalSize + ", flags=" + this.flags + ", blockIndex=" + this.blockIndex + ", name=" + this.name + "]";
    }

    public void setOffset(int newOffset) {
        this.offset = newOffset;
    }

    public MpqFile(MappedByteBuffer buf, BlockTable.Block b, int sectorSize, String name) throws IOException, JMpqException {
        this.buf = buf;
        this.block = b;
        this.sectorSize = sectorSize;
        this.name = name;
        this.compSize = b.getCompressedSize();
        this.normalSize = b.getNormalSize();
        this.flags = b.getFlags();
        this.sectorCount = (int)(Math.ceil((double)this.normalSize / (double)sectorSize) + 1.0);
        this.baseKey = 0;
        this.sepIndex = name.lastIndexOf(92);
        String pathlessName = name.substring(this.sepIndex + 1);
        if ((b.getFlags() & 0x10000) == 65536) {
            this.crypto = new MpqCrypto();
            this.baseKey = this.crypto.hash(pathlessName, 3);
            if ((b.getFlags() & 0x20000) == 131072) {
                this.baseKey = this.baseKey + b.getFilePos() ^ b.getNormalSize();
            }
        }
    }

    public int getOffset() {
        return this.offset;
    }

    public int getCompSize() {
        return this.compSize;
    }

    public int getNormalSize() {
        return this.normalSize;
    }

    public int getFlags() {
        return this.flags;
    }

    public String getName() {
        return this.name;
    }

    public void extractToFile(File f) throws IOException {
        if (this.sectorCount == 1) {
            f.createNewFile();
        }
        this.extractToOutputStream(new FileOutputStream(f));
    }

    public void extractToOutputStream(OutputStream writer) throws IOException {
        if (this.sectorCount == 1) {
            writer.close();
            return;
        }
        if ((this.block.getFlags() & 0x1000000) == 0x1000000) {
            if ((this.block.getFlags() & 0x200) == 512) {
                this.buf.position(this.block.getFilePos());
                byte[] arr = this.getSectorAsByteArray(this.buf, this.compSize);
                if (this.crypto != null) {
                    arr = this.crypto.decryptBlock(arr, this.baseKey);
                }
                arr = this.decompressSector(arr, this.block.getCompressedSize(), this.block.getNormalSize());
                writer.write(arr);
                writer.flush();
                writer.close();
            } else {
                this.buf.position(this.block.getFilePos());
                byte[] arr = this.getSectorAsByteArray(this.buf, this.compSize);
                if (this.crypto != null) {
                    arr = this.crypto.decryptBlock(arr, this.baseKey);
                }
                writer.write(arr);
                writer.flush();
                writer.close();
            }
            return;
        }
        if ((this.block.getFlags() & 0x200) == 512) {
            ByteBuffer sotBuffer = null;
            this.buf.position(this.block.getFilePos());
            byte[] sot = new byte[this.sectorCount * 4];
            this.buf.get(sot);
            if (this.crypto != null) {
                sot = this.crypto.decryptBlock(sot, this.baseKey - 1);
            }
            sotBuffer = ByteBuffer.wrap(sot).order(ByteOrder.LITTLE_ENDIAN);
            int start = sotBuffer.getInt();
            int end = sotBuffer.getInt();
            int finalSize = 0;
            for (int i = 0; i < this.sectorCount - 1; ++i) {
                this.buf.position(this.block.getFilePos() + start);
                byte[] arr = this.getSectorAsByteArray(this.buf, end - start);
                if (this.crypto != null) {
                    arr = this.crypto.decryptBlock(arr, this.baseKey + i);
                }
                arr = this.block.getNormalSize() - finalSize <= this.sectorSize ? this.decompressSector(arr, end - start, this.block.getNormalSize() - finalSize) : this.decompressSector(arr, end - start, this.sectorSize);
                writer.write(arr);
                finalSize += this.sectorSize;
                start = end;
                try {
                    end = sotBuffer.getInt();
                    continue;
                }
                catch (BufferUnderflowException e) {
                    break;
                }
            }
            writer.flush();
            writer.close();
        } else {
            this.buf.position(this.block.getFilePos());
            byte[] arr = this.getSectorAsByteArray(this.buf, this.compSize);
            if (this.crypto != null) {
                arr = this.crypto.decryptBlock(arr, this.baseKey);
            }
            writer.write(arr);
            writer.flush();
            writer.close();
        }
    }

    public void writeFileAndBlock(BlockTable.Block newBlock, MappedByteBuffer writeBuffer) {
        newBlock.setNormalSize(this.normalSize);
        newBlock.setCompressedSize(this.compSize);
        newBlock.setFlags(this.block.getFlags());
        if (this.compSize <= 0) {
            return;
        }

        // Keep untouched archive entries byte-for-byte identical.
        // Rebuilding sectors here is what was corrupting some SC2Map archives on save.
        this.buf.position(this.block.getFilePos());
        byte[] rawBlock = this.getSectorAsByteArray(this.buf, this.compSize);
        writeBuffer.put(rawBlock);
    }

    public static void writeFileAndBlock(File f, BlockTable.Block b, MappedByteBuffer buf, int sectorSize) {
        try {
            MpqFile.writeFileAndBlock(Files.readAllBytes(f.toPath()), b, buf, sectorSize);
        }
        catch (IOException e) {
            throw new RuntimeException("Internal JMpq Error", e);
        }
    }

    public static void writeFileAndBlock(byte[] fileArr, BlockTable.Block b, MappedByteBuffer buf, int sectorSize) {
        ByteBuffer fileBuf = ByteBuffer.wrap(fileArr);
        fileBuf.position(0);
        b.setNormalSize(fileArr.length);
        b.setFlags(-2147483136);
        int sectorCount = (int)(Math.ceil((double)fileArr.length / (double)sectorSize) + 1.0);
        ByteBuffer sot = ByteBuffer.allocate(sectorCount * 4);
        sot.order(ByteOrder.LITTLE_ENDIAN);
        sot.position(0);
        sot.putInt(sectorCount * 4);
        buf.position(sectorCount * 4);
        int sotPos = sectorCount * 4;
        byte[] temp = new byte[sectorSize];
        for (int i = 1; i <= sectorCount - 1; ++i) {
            if (fileBuf.position() + sectorSize > fileArr.length) {
                temp = new byte[fileArr.length - fileBuf.position()];
            }
            fileBuf.get(temp);
            byte[] compSector = null;
            try {
                compSector = JzLibHelper.deflate(temp);
            }
            catch (ArrayIndexOutOfBoundsException e) {
                compSector = null;
            }
            if (compSector != null && compSector.length < temp.length) {
                buf.put((byte)2);
                buf.put(compSector);
                sotPos += compSector.length + 1;
            } else {
                buf.put(temp);
                sotPos += temp.length;
            }
            sot.putInt(sotPos);
        }
        b.setCompressedSize(sotPos);
        buf.position(0);
        sot.position(0);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(sot);
    }

    private byte[] getSectorAsByteArray(MappedByteBuffer buf, int sectorSize) {
        byte[] arr = new byte[sectorSize];
        buf.get(arr);
        return arr;
    }

    private byte[] decompressSector(byte[] sector, int normalSize, int uncompSize) throws JMpqException {
        if (normalSize == uncompSize) {
            return sector;
        }
        int compressionType = sector[0] & 0xFF;
        if ((compressionType & 2) == 2) {
            return JzLibHelper.inflate(sector, 1, uncompSize);
        }
        if ((compressionType & 0x10) == 0x10) {
            return this.inflateBzip2(sector, 1, uncompSize);
        }
        if (compressionType == 0) {
            int available = Math.max(0, sector.length - 1);
            byte[] out = new byte[uncompSize];
            int copy = Math.min(available, uncompSize);
            if (copy > 0) {
                System.arraycopy(sector, 1, out, 0, copy);
            }
            return out;
        }
        throw new JMpqException("Unsupported compression algorithm: 0x" + Integer.toHexString(compressionType));
    }

    private byte[] inflateBzip2(byte[] sector, int offset, int uncompSize) throws JMpqException {
        byte[] raw = Arrays.copyOfRange(sector, offset, sector.length);
        byte[] output = this.tryInflateBzip(raw);
        if (output == null) {
            byte[] withHeader9 = this.prependBzipHeader(raw, (byte)'9');
            output = this.tryInflateBzip(withHeader9);
        }
        if (output == null) {
            byte[] withHeader1 = this.prependBzipHeader(raw, (byte)'1');
            output = this.tryInflateBzip(withHeader1);
        }
        if (output == null) {
            throw new JMpqException("BZIP2 sector decode failed");
        }

        if (output.length == uncompSize) {
            return output;
        }
        if (output.length > uncompSize) {
            return Arrays.copyOf(output, uncompSize);
        }
        return output;
    }

    private byte[] prependBzipHeader(byte[] payload, byte level) {
        byte[] out = new byte[payload.length + 4];
        out[0] = 'B';
        out[1] = 'Z';
        out[2] = 'h';
        out[3] = level;
        System.arraycopy(payload, 0, out, 4, payload.length);
        return out;
    }

    private byte[] tryInflateBzip(byte[] payload) {
        try (ByteArrayInputStream inRaw = new ByteArrayInputStream(payload);
             BZip2CompressorInputStream in = new BZip2CompressorInputStream(inRaw);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }
}
