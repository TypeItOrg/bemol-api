package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.io.RandomAccessInputStream;
import org.apache.pdfbox.io.RandomAccessReadWriteBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Bounds decoded output before PDFBox can grow its buffers, including intermediate filter stages.
 */
final class EnrollmentPdfDecoder implements AutoCloseable {
  private static final int MAX_FILTERS = 8;
  private static final int BUFFER_CHUNK_BYTES = 4096;
  private static final int LZW_ENTRY_OVERHEAD_PER_INPUT_BYTE = 64;
  private static final Set<String> DATA_FILTERS =
      Set.of(
          "FlateDecode",
          "Fl",
          "LZWDecode",
          "LZW",
          "ASCII85Decode",
          "A85",
          "ASCIIHexDecode",
          "AHx",
          "RunLengthDecode",
          "RL",
          "Crypt");

  private final long maxBytes;
  private final List<RandomAccessReadWriteBuffer> buffers = new ArrayList<>();
  private long reservedBytes;

  EnrollmentPdfDecoder(final long maxBytes) {
    this.maxBytes = maxBytes;
  }

  RandomAccessReadWriteBuffer decode(final COSStream stream) throws IOException {
    final var filters = filters(stream);
    try (final var encoded = stream.createRawInputStream()) {
      if (filters.isEmpty()) {
        final var decoded = buffer();
        encoded.transferTo(boundedOutput(decoded, 1));
        decoded.seek(0);
        return decoded;
      }

      var decoded = decodeFilter(stream, filters.getFirst(), 0, encoded);
      for (int index = 1; index < filters.size(); index++) {
        try (final var previous = decoded) {
          final var input = new RandomAccessInputStream(previous);
          decoded = decodeFilter(stream, filters.get(index), index, input);
        }
      }
      return decoded;
    }
  }

  private RandomAccessReadWriteBuffer decodeFilter(
      final COSStream stream, final COSName filter, final int index, final InputStream input)
      throws IOException {
    final boolean lzw = filter.getName().equals("LZWDecode") || filter.getName().equals("LZW");
    if (lzw || filter.getName().equals("FlateDecode") || filter.getName().equals("Fl")) {
      reservePredictorMemory(stream, index);
    }
    final var decoded = buffer();
    final var output = boundedOutput(decoded, lzw ? 2 : 1);
    FilterFactory.INSTANCE
        .getFilter(filter)
        .decode(lzw ? boundedDictionaryInput(input) : input, output, stream, index);
    decoded.seek(0);
    return decoded;
  }

  private List<COSName> filters(final COSStream stream) {
    final var value = stream.getFilters();
    final List<COSName> names = new ArrayList<>();
    if (value instanceof COSName name) {
      names.add(name);
    } else if (value instanceof COSArray array) {
      if (array.size() > MAX_FILTERS) {
        throw invalidContent();
      }
      for (int index = 0; index < array.size(); index++) {
        if (!(array.getObject(index) instanceof COSName name)) {
          throw invalidContent();
        }
        names.add(name);
      }
    } else if (value != null) {
      throw invalidContent();
    }

    // Image filters allocate rasters before writing output. Embedded images are not decoded
    // by this validator; only page content, cross-reference and object streams need data filters.
    for (final var name : names) {
      if (!DATA_FILTERS.contains(name.getName())) {
        throw invalidContent();
      }
    }
    return names;
  }

  private void reservePredictorMemory(final COSStream stream, final int index) {
    final var parameters = predictorParameters(stream, index);
    if (parameters == null || parameters.getInt(COSName.PREDICTOR, 1) <= 1) {
      return;
    }

    final int predictor = parameters.getInt(COSName.PREDICTOR);
    final int colors = parameters.getInt(COSName.COLORS, 1);
    final int bits = parameters.getInt(COSName.BITS_PER_COMPONENT, 8);
    final int columns = parameters.getInt(COSName.COLUMNS, 1);
    if ((predictor != 2 && (predictor < 10 || predictor > 15))
        || colors < 1
        || colors > 32
        || columns < 1
        || (bits != 1 && bits != 2 && bits != 4 && bits != 8 && bits != 16)) {
      throw invalidContent();
    }

    // PDFBox allocates two rows before emitting output and calculates their length with ints.
    // Long arithmetic and this reservation prevent oversized rows and integer overflow first.
    final long rowBytes = ((long) colors * bits * columns + 7) / 8;
    consume(2 * rowBytes);
  }

  private @Nullable COSDictionary predictorParameters(final COSStream stream, final int index) {
    final var filters = stream.getDictionaryObject(COSName.F, COSName.FILTER);
    final var parameters = stream.getDictionaryObject(COSName.DP, COSName.DECODE_PARMS);
    if (filters instanceof COSName && parameters instanceof COSDictionary dictionary) {
      return dictionary;
    }
    if (filters instanceof COSArray
        && parameters instanceof COSArray array
        && index < array.size()
        && array.getObject(index) instanceof COSDictionary dictionary) {
      return dictionary;
    }
    return null;
  }

  private RandomAccessReadWriteBuffer buffer() {
    consume(BUFFER_CHUNK_BYTES);
    final var buffer = new RandomAccessReadWriteBuffer(BUFFER_CHUNK_BYTES);
    buffers.add(buffer);
    return buffer;
  }

  private InputStream boundedDictionaryInput(final InputStream input) {
    // PDFBox retains LZW entries beyond the 12-bit dictionary size. Account for entry overhead
    // per encoded byte; boundedOutput also reserves a second copy for retained codeword data.
    return new FilterInputStream(input) {
      @Override
      public int read() throws IOException {
        final int value = in.read();
        if (value >= 0) {
          consume(LZW_ENTRY_OVERHEAD_PER_INPUT_BYTE);
        }
        return value;
      }

      @Override
      public int read(final byte[] bytes, final int offset, final int length) throws IOException {
        final int read = in.read(bytes, offset, length);
        if (read > 0) {
          consume((long) LZW_ENTRY_OVERHEAD_PER_INPUT_BYTE * read);
        }
        return read;
      }
    };
  }

  private OutputStream boundedOutput(
      final RandomAccessReadWriteBuffer buffer, final int retainedCopies) {
    return new OutputStream() {
      @Override
      public void write(final int value) throws IOException {
        consume(retainedCopies);
        buffer.write(value);
      }

      @Override
      public void write(final byte[] bytes, final int offset, final int length) throws IOException {
        consume((long) retainedCopies * length);
        buffer.write(bytes, offset, length);
      }
    };
  }

  private void consume(final long bytes) {
    if (bytes > maxBytes - reservedBytes) {
      // Runtime application errors must propagate: PDPage deliberately skips IOException streams.
      throw invalidContent();
    }
    reservedBytes += bytes;
  }

  private static InvalidFileException invalidContent() {
    return new InvalidFileException(EnrollmentMessages.FILE_CONTENT_INVALID);
  }

  @Override
  public void close() throws IOException {
    for (final var buffer : buffers) {
      buffer.close();
    }
    buffers.clear();
  }
}
