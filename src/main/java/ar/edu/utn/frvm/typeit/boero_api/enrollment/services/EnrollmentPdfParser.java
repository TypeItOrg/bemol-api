package ar.edu.utn.frvm.typeit.boero_api.enrollment.services;

import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.EnrollmentMessages;
import ar.edu.utn.frvm.typeit.boero_api.enrollment.exceptions.InvalidFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInputStream;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadView;
import org.apache.pdfbox.io.RandomAccessReadWriteBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.jspecify.annotations.Nullable;

/** Installs bounded decoding before PDFBox reads compressed cross-reference or object streams. */
final class EnrollmentPdfParser extends PDFParser implements AutoCloseable {
  private final EnrollmentPdfDecoder decoder;
  private final List<BoundedStream> streams = new ArrayList<>();

  EnrollmentPdfParser(final RandomAccessRead source, final long maxDecodedBytes)
      throws IOException {
    super(source);
    decoder = new EnrollmentPdfDecoder(maxDecodedBytes);
  }

  @Override
  protected COSStream parseCOSStream(final COSDictionary dictionary) throws IOException {
    final var stream = new BoundedStream(super.parseCOSStream(dictionary), decoder);
    streams.add(stream);
    return stream;
  }

  @Override
  protected void prepareDecryption() throws IOException {
    if (document.isEncrypted()) {
      throw new InvalidFileException(EnrollmentMessages.FILE_PDF_ENCRYPTED);
    }
    super.prepareDecryption();
  }

  @Override
  public void close() throws IOException {
    IOException failure = null;
    for (final var stream : streams) {
      try {
        stream.close();
      } catch (IOException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    decoder.close();
    streams.clear();
    if (failure != null) {
      throw failure;
    }
  }

  private static final class BoundedStream extends COSStream {
    private final COSStream encoded;
    private final EnrollmentPdfDecoder decoder;
    private @Nullable RandomAccessReadWriteBuffer content;
    private @Nullable COSStream inputStreamSource;

    private BoundedStream(final COSStream encoded, final EnrollmentPdfDecoder decoder) {
      this.encoded = encoded;
      this.decoder = decoder;
      addAll(encoded);
    }

    @Override
    public InputStream createRawInputStream() throws IOException {
      return encoded.createRawInputStream();
    }

    @Override
    public RandomAccessReadView createView() throws IOException {
      if (content == null) {
        content = decoder.decode(encoded);
      }
      // Views share the bounded buffer rather than copying it for every page reference.
      return new RandomAccessReadView(content, 0, content.length());
    }

    @Override
    public COSInputStream createInputStream() throws IOException {
      return createInputStream(DecodeOptions.DEFAULT);
    }

    @Override
    public COSInputStream createInputStream(final DecodeOptions options) throws IOException {
      if (inputStreamSource == null) {
        // No filters remain here: both PDFBox decoded-reading APIs use the same bounded data.
        inputStreamSource = new COSStream(null, createView());
      }
      return inputStreamSource.createInputStream(options);
    }

    @Override
    public void close() throws IOException {
      try {
        if (inputStreamSource != null) {
          inputStreamSource.close();
        }
      } finally {
        try {
          encoded.close();
        } finally {
          super.close();
        }
      }
    }
  }
}
