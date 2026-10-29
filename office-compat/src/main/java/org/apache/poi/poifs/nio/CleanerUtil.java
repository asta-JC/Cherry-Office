package org.apache.poi.poifs.nio;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Android compatibility: desktop MethodHandle mmap cleanup cannot be dexed on API 24. */
public final class CleanerUtil {
    public static final boolean UNMAP_SUPPORTED = false;
    public static final String UNMAP_NOT_SUPPORTED_REASON = "Android manages direct buffers; desktop unmapping is unavailable.";
    private CleanerUtil() {}
    public interface BufferCleaner { void freeBuffer(ByteBuffer buffer) throws IOException; }
    public static BufferCleaner getCleaner() { return buffer -> { }; }
}
