/*******************************************************************************
 * Copyright 2014 uniVocity Software Pty Ltd
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.univocity.parsers.fixed;

import com.univocity.parsers.common.*;

import java.io.*;
import java.nio.charset.*;

/**
 * A fast and flexible fixed-with writer implementation.
 *
 * @see FixedWidthFormat
 * @see FixedWidthFieldLengths
 * @see FixedWidthWriterSettings
 * @see FixedWidthParser
 * @see AbstractWriter
 *
 * @author uniVocity Software Pty Ltd - <a href="mailto:parsers@univocity.com">parsers@univocity.com</a>
 *
 */
public class FixedWidthWriter extends AbstractWriter<FixedWidthWriterSettings> {

	private boolean ignoreLeading;
	private boolean ignoreTrailing;
	private int[] fieldLengths;
	private FieldAlignment[] fieldAlignments;
	private char padding;
	private int length;
	private FieldAlignment alignment;

	private Lookup[] lookaheadFormats;
	private Lookup[] lookbehindFormats;
	private char[] lookupChars;
	private Lookup lookbehindFormat;
	private int[] rootLengths;
	private FieldAlignment[] rootAlignments;

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * <p><strong>Important: </strong> by not providing an instance of {@link java.io.Writer} to this constructor, only the operations that write to Strings are available.</p>
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(FixedWidthWriterSettings settings) {
		this((Writer) null, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param writer the output resource that will receive fixed-width records produced by this class.
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(Writer writer, FixedWidthWriterSettings settings) {
		super(writer, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param file the output file that will receive fixed-width records produced by this class.
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(File file, FixedWidthWriterSettings settings) {
		super(file, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param file the output file that will receive fixed-width records produced by this class.
	 * @param encoding the encoding of the file
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(File file, String encoding, FixedWidthWriterSettings settings) {
		super(file, encoding, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param file the output file that will receive fixed-width records produced by this class.
	 * @param encoding the encoding of the file
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(File file, Charset encoding, FixedWidthWriterSettings settings) {
		super(file, encoding, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param output the output stream that will be written with the fixed-width records produced by this class.
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(OutputStream output, FixedWidthWriterSettings settings) {
		super(output, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 *@param output the output stream that will be written with the fixed-width records produced by this class.
	 * @param encoding the encoding of the stream
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(OutputStream output, String encoding, FixedWidthWriterSettings settings) {
		super(output, encoding, settings);
	}

	/**
	 * The FixedWidthWriter supports all settings provided by {@link FixedWidthWriterSettings}, and requires this configuration to be properly initialized.
	 * @param output the output stream that will be written with the fixed-width records produced by this class.
	 * @param encoding the encoding of the stream
	 * @param settings the fixed-width writer configuration
	 */
	public FixedWidthWriter(OutputStream output, Charset encoding, FixedWidthWriterSettings settings) {
		super(output, encoding, settings);
	}

	/**
	 * Initializes the Fixed-Width writer with CSV-specific configuration
	 * @param settings the Fixed-Width  writer configuration
	 */
	protected final void initialize(FixedWidthWriterSettings settings) {
		FixedWidthFormat format = settings.getFormat();
		this.padding = format.getPadding();

		this.ignoreLeading = settings.getIgnoreLeadingWhitespaces();
		this.ignoreTrailing = settings.getIgnoreTrailingWhitespaces();

		this.fieldLengths = settings.getFieldLengths();
		this.fieldAlignments = settings.getFieldAlignments();

		this.lookaheadFormats = settings.getLookaheadFormats();
		this.lookbehindFormats = settings.getLookbehindFormats();

		if (lookaheadFormats != null || lookbehindFormats != null) {
			lookupChars = new char[Lookup.calculateMaxLookupLength(lookaheadFormats, lookbehindFormats)];
			rootLengths = fieldLengths;
			rootAlignments = fieldAlignments;
		} else {
			lookupChars = null;
			rootLengths = null;
			rootAlignments = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processRow(Object[] row) {
		if (row.length > 0 && lookaheadFormats != null || lookbehindFormats != null) {
			String value = String.valueOf(row[0]);
			int end;
			if (value.length() >= lookupChars.length) {
				end = lookupChars.length;
			} else {
				end = value.length();
				for (int i = lookupChars.length - 1; i > end; i--) {
					lookupChars[i] = '\0';
				}
			}
			value.getChars(0, end, lookupChars, 0);

			boolean matched = false;
			if (lookaheadFormats != null) {
				for (int i = 0; i < lookaheadFormats.length; i++) {
					if (lookaheadFormats[i].matches(lookupChars)) {
						fieldLengths = lookaheadFormats[i].lengths;
						fieldAlignments = lookaheadFormats[i].alignments;
						matched = true;
						break;
					}
				}
				if (lookbehindFormats != null && matched) {
					lookbehindFormat = null;
					for (int i = 0; i < lookbehindFormats.length; i++) {
						if (lookbehindFormats[i].matches(lookupChars)) {
							lookbehindFormat = lookbehindFormats[i];
							break;
						}
					}
				}
			} else {
				for (int i = 0; i < lookbehindFormats.length; i++) {
					if (lookbehindFormats[i].matches(lookupChars)) {
						lookbehindFormat = lookbehindFormats[i];
						matched = true;
						fieldLengths = rootLengths;
						fieldAlignments = rootAlignments;
						break;
					}
				}
			}

			if (!matched) {
				if (lookbehindFormat == null) {
					if (rootLengths == null) {
						throw new TextWritingException("Cannot write with the given configuration. No default field lengths defined and no lookahead/lookbehind value match '" + new String(lookupChars) + '\'', getRecordCount(), row);
					}
					fieldLengths = rootLengths;
					fieldAlignments = rootAlignments;
				} else {
					fieldLengths = lookbehindFormat.lengths;
					fieldAlignments = lookbehindFormat.alignments;
				}
			}
		}

		int lastIndex = fieldLengths.length < row.length ? fieldLengths.length : row.length;

		for (int i = 0; i < lastIndex; i++) {
			length = fieldLengths[i];
			alignment = fieldAlignments[i];
			String nextElement = getStringValue(row[i]);
			processElement(nextElement);
			appendValueToRow();
		}
	}

	private void append(String element) {
		int start = 0;
		if (this.ignoreLeading) {
			start = skipLeadingWhitespace(element);
		}

		int padCount = alignment.calculatePadding(length, element.length() - start);
		length -= padCount;
		appender.fill(padding, padCount);

		if (this.ignoreTrailing) {
			int i = start;
			while (i < element.length() && length > 0) {
				for (; i < element.length() && length-- > 0; i++) {
					char nextChar = element.charAt(i);
					appender.appendIgnoringWhitespace(nextChar);
				}
				if (length == -1 && appender.whitespaceCount() > 0) {
					//if we got here then the value to write got truncated exactly after one or more whitespaces.
					//In this case, if the whitespaces are not at the end of the truncated value they will be put back to the output.
					for (int j = i; j < element.length(); j++) {
						if (element.charAt(j) > ' ') {
							//resets the whitespace count so the original whitespaces are printed to the output.
							appender.resetWhitespaceCount();
							break;
						}
					}
					if (appender.whitespaceCount() > 0) {
						length = 0;
					}
				}
				length += appender.whitespaceCount();
				appendValueToRow();
			}
		} else {
			for (int i = start; i < element.length() && length-- > 0; i++) {
				char nextChar = element.charAt(i);
				appender.append(nextChar);
			}
		}
	}

	private void processElement(String element) {
		if (element != null) {
			append(element);
		}
		appender.fill(padding, length);
	}
}
