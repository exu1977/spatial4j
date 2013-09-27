/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spatial4j.core.io;


import com.spatial4j.core.context.SpatialContext;
import com.spatial4j.core.shape.Point;
import com.spatial4j.core.shape.Shape;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An extensible parser for <a href="http://en.wikipedia.org/wiki/Well-known_text">
 *   Well Known Text (WKT)</a>.
 * <p />
 * Note, instances are not threadsafe but are reusable.
 */
public class WKTShapeParser {

  //TODO
  // * EMPTY shapes  (new EmptyShape with name?)
  // * avoid toLowerCase ?
  // * shape: multipoint (both syntax's)
  // * shape: geometrycollection
  // * ensure could eventually support: POINT ZM (1 1 5 60)
  //      via parse till ',' or '('

  /** Lower-cased and trim()'ed; set in {@link #parseIfSupported(String)}. */
  protected String rawString;

  /** Offset of the next char in {@link #rawString} to be read. */
  protected int offset;

  protected SpatialContext ctx;

  public WKTShapeParser(SpatialContext ctx) {
    this.ctx = ctx;
  }

  public SpatialContext getCtx() {
    return ctx;
  }

  /**
   * Parses the wktString, returning the defined Shape.
   *
   * @return Non-null Shape defined in the String
   * @throws ParseException Thrown if there is an error in the Shape definition
   */
  public Shape parse(String wktString)  throws ParseException {
    Shape shape = parseIfSupported(wktString);//sets rawString & offset
    if (shape != null)
      return shape;
    String shortenedString = (wktString.length() <= 128 ? wktString : wktString.substring(0, 128-3)+"...");
    throw new ParseException("Unknown Shape definition [" + shortenedString + "]", offset);
  }

  /**
   * Parses the wktString, returning the defined Shape. If it can't because the
   * shape name is unknown or an empty or blank string was passed, then it returns null.
   *
   * @param wktString non-null
   * @return Shape, null if unknown / unsupported type.
   * @throws ParseException Thrown if there is an error in the Shape definition.
   */
  public Shape parseIfSupported(String wktString) throws ParseException {
    this.rawString = wktString.toLowerCase(Locale.ROOT);
    this.offset = 0;
    consumeWhitespace();
    if (offset >= rawString.length())
      return null;
    if (!Character.isLetter(wktString.charAt(offset)))
      return null;
    String shapeType = nextWord();
    Shape result = parseShapeByType(shapeType);
    if (result != null) {
      if (offset != wktString.length())
        throw new ParseException("end of shape expected", offset);
    }
    return result;
  }

  /**
   * Parses the remainder of a shape definition following the shape's name
   * given as {@code shapeType} already consumed via
   * {@link #nextWord()}. If
   * it's able to parse the shape, {@link #offset} should be advanced beyond
   * it (e.g. to the ',' or ')' or EOF in general). The default implementation
   * checks the name against some predefined names and calls corresponding
   * parse methods to handle the rest. This method is an excellent extension
   * point for additional shape types.
   *
   * @param shapeType Non-Null string
   * @return The shape or null if not supported / unknown.
   * @throws ParseException
   */
  protected Shape parseShapeByType(String shapeType) throws ParseException {
    if (shapeType.equals("point")) {
      return parsePoint();
    }
    if (shapeType.equals("envelope")) {
      return parseEnvelope();
    }
    return null;
  }

  /**
   * Parses a Point Shape from the raw String.
   *
   * Point: 'POINT' '(' coordinate ')'
   *
   * @return Point Shape parsed from the raw String
   * @throws ParseException Thrown if the raw String doesn't represent the Point correctly
   */
  private Shape parsePoint() throws ParseException {
    expect('(');
    Point coordinate = point();
    expect(')');
    return coordinate;
  }

  /**
   * Parses an Envelope Shape from the raw String.
   * Source: OGC "Catalogue Services Specification", the "CQL" (Common Query Language) sub-spec.
   *
   * Envelope: 'ENVELOPE' '(' x1 ',' x2 ',' y2 ',' y1 ')'
   *
   * @return Envelope Shape parsed from the raw String
   * @throws ParseException Thrown if the raw String doesn't represent the Envelope correctly
   */
  protected Shape parseEnvelope() throws ParseException {
    expect('(');
    double x1 = parseDouble();
    expect(',');
    double x2 = parseDouble();
    expect(',');
    double y2 = parseDouble();
    expect(',');
    double y1 = parseDouble();
    expect(')');
    return ctx.makeRectangle(x1, x2, y1, y2);
  }

  /**
   * Reads a list of Points (AKA CoordinateSequence) from the current position.
   *
   * CoordinateSequence: '(' coordinate (',' coordinate )* ')'
   *
   * @return Points read from the current position. Non-null, non-empty.
   * @throws ParseException Thrown if reading the CoordinateSequence is unsuccessful
   */
  protected List<Point> pointList() throws ParseException {
    List<Point> sequence = new ArrayList<Point>();
    expect('(');
    do {
      sequence.add(point());
    } while (consumeIfAt(','));
    expect(')');
    return sequence;
  }

  /**
   * Reads a Point (AKA Coordinate) from the current position.
   *
   * Coordinate: number number
   *
   * @return The point read from the current position.
   * @throws java.text.ParseException Thrown if reading the Coordinate is unsuccessful
   */
  protected Point point() throws ParseException {
    double x = parseDouble();
    double y = parseDouble();
    return ctx.makePoint(x, y);
  }

  /**
   * Reads the word starting at the current character position. The word
   * terminates once {@link Character#isLetter(char)} returns false.
   * {@link #offset} is advanced past whitespace.
   *
   * @return Non-null non-empty String.
   * @throws ParseException if the word would otherwise be empty.
   */
  protected String nextWord() throws ParseException {
    int startOffset = offset;
    while (offset < rawString.length() && Character.isLetter(rawString.charAt(offset))) {
      offset++;
    }
    if (startOffset == offset)
      throw new ParseException("Word expected", startOffset);
    String result = rawString.substring(startOffset, offset);
    consumeWhitespace();
    return result;
  }

  /**
   * Reads in a double from the String. Parses digits with an optional decimal, sign, or exponent.
   * {@link #offset} is advanced past whitespace.

   * @return Double value
   * @throws ParseException Thrown if the String is exhausted before the number is delimited
   */
  protected double parseDouble() throws ParseException {
    int startOffset = offset;
    for (; offset < rawString.length(); offset++ ) {
      char c = rawString.charAt(offset);
      if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e')) {
        break;
      }
    }
    if (startOffset == offset)
      throw new ParseException("Expected a number", offset);
    double result;
    try {
      result = Double.parseDouble(rawString.substring(startOffset, offset));
    } catch (Exception e) {
      throw new ParseException(e.toString(), offset);
    }
    consumeWhitespace();
    return result;
  }

  /**
   * Verifies that the current character is of the expected value, bumping offset.
   * If the character is the expected value, then it is consumed.
   * {@link #offset} is advanced past whitespace.
   *
   * @param expected Value that the next non-whitespace character should be
   * @throws ParseException Thrown if the next non-whitespace character is not
   *         the expected value
   */
  protected void expect(char expected) throws ParseException {
    if (offset >= rawString.length())
      throw new ParseException("Expected [" + expected + "] found EOF", offset);
    char c = rawString.charAt(offset);
    if (c == expected) {
      offset++;
      consumeWhitespace();
      return;
    } else {
      throw new ParseException("Expected [" + expected + "] found [" + c + "]", offset);
    }
  }

  /**
   * If the current character is {@code expected}, then offset is advanced after it and any
   * subsequent whitespace.
   *
   * @param expected The expected char.
   * @return true if consumed
   */
  protected boolean consumeIfAt(char expected) {
    if (offset >= rawString.length())
      return false;
    if (rawString.charAt(offset) == expected) {
      offset++;
      consumeWhitespace();
      return true;
    }
    return false;
  }

  /**
   * Moves offset to next non-whitespace character. Doesn't move if the offset is already at
   * non-whitespace.
   */
  protected void consumeWhitespace() {
    for (; offset < rawString.length(); offset++) {
      if (!Character.isWhitespace(rawString.charAt(offset))) {
        return;
      }
    }
  }

  /**
   * Returns the next chunk of text till the next ',' or ')' (non-inclusive)
   * or EOF. If a '(' is encountered, then it looks past its matching ')',
   * taking care to handle nested matching parenthesis too. It's designed to be
   * of use to subclasses that wish to get the entire subshape at the current
   * position as a string so that it might be passed to other software that
   * will parse it.
   * <p/>
   * Example:
   * <pre>
   *   OUTER(INNER(3, 5))
   * </pre>
   * If this is called when offset is at the first character, then it will
   * return this whole string.  If called at the "I" then it will return
   * "INNER(3, 5)".  If called at "3", then it will return "3".  In all cases,
   * offset will be positioned at the next position following the returned
   * substring.
   *
   * @return non-null substring.
   */
  protected String nextSubShapeString() throws ParseException {
    int startOffset = offset;
    int parenStack = 0;//how many parenthesis levels are we in?
    for (; offset < rawString.length(); offset++) {
      char c = rawString.charAt(offset);
      if (c == ',') {
        if (parenStack == 0)
          break;
      } else if (c == ')') {
        if (parenStack == 0)
          break;
        parenStack--;
      } else if (c == '(') {
        parenStack++;
      }
    }
    if (parenStack != 0)
      throw new ParseException("Unbalanced parenthesis", startOffset);
    return  rawString.substring(startOffset, offset);
  }
}
