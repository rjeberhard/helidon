/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.json;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Replays a polymorphic object without the member that selected its subtype.
 */
final class PolymorphicReplayParser extends JsonParserBase {
    private final JsonParser parser;
    private int remaining;
    private int depth = 1;
    private byte skippedScalar;
    private int markedRemaining;
    private int markedDepth;
    private byte markedSkippedScalar;

    PolymorphicReplayParser(JsonParser parser, int discriminatorIndex) {
        this.parser = parser;
        this.remaining = discriminatorIndex;
    }

    @Override
    public boolean hasNext() {
        return skippedScalar != 0 || parser.hasNext();
    }

    @Override
    public byte currentByte() {
        return skippedScalar == 0 ? parser.currentByte() : skippedScalar;
    }

    @Override
    public byte nextToken() {
        byte token;
        if (skippedScalar == 0) {
            token = parser.nextToken();
        } else {
            skippedScalar = 0;
            token = parser.currentByte();
        }
        if (remaining > 0) {
            if (token == ',' && depth == 1 && --remaining == 0) {
                // The search already validated this member; hide it and one adjoining comma.
                parser.nextToken();
                parser.readStringAsHash();
                parser.nextToken();
                parser.nextToken();
                parser.readStringAsHash();
                token = parser.nextToken();
                remaining = -1;
            }
            if (token == '{' || token == '[') {
                depth++;
            } else if (token == '}' || token == ']') {
                depth--;
            }
        }
        return token;
    }

    @Override
    public JsonString readJsonString() {
        return parser.readJsonString();
    }

    @Override
    public JsonNumber readJsonNumber() {
        return parser.readJsonNumber();
    }

    @Override
    public String readString() {
        return parser.readString();
    }

    @Override
    public int readStringAsHash() {
        return parser.readStringAsHash();
    }

    @Override
    public char readChar() {
        return parser.readChar();
    }

    @Override
    public boolean readBoolean() {
        return parser.readBoolean();
    }

    @Override
    public byte readByte() {
        return parser.readByte();
    }

    @Override
    public short readShort() {
        return parser.readShort();
    }

    @Override
    public int readInt() {
        return parser.readInt();
    }

    @Override
    public long readLong() {
        return parser.readLong();
    }

    @Override
    public float readFloat() {
        return parser.readFloat();
    }

    @Override
    public double readDouble() {
        return parser.readDouble();
    }

    @Override
    public BigInteger readBigInteger() {
        return parser.readBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal() {
        return parser.readBigDecimal();
    }

    @Override
    public byte[] readBinary() {
        return parser.readBinary();
    }

    @Override
    public boolean checkNull() {
        return parser.checkNull();
    }

    @Override
    public void skip() {
        byte start = currentByte();
        parser.skip();
        if (start == '{' || start == '[') {
            depth--;
        } else if (parser.currentByte() == ',' || parser.currentByte() == '}' || parser.currentByte() == ']') {
            // JsonValueParser.skip() advances scalars to their delimiter already.
            skippedScalar = start;
        }
    }

    @Override
    public JsonException createException(String message) {
        return parser.createException(message);
    }

    @Override
    public JsonException createException(String message, Exception cause) {
        return parser.createException(message, cause);
    }

    @Override
    public JsonException createException(String message, byte c) {
        return parser.createException(message, c);
    }

    @Override
    public void mark() {
        parser.mark();
        markedRemaining = remaining;
        markedDepth = depth;
        markedSkippedScalar = skippedScalar;
    }

    @Override
    public void clearMark() {
        parser.clearMark();
    }

    @Override
    public void resetToMark() {
        parser.resetToMark();
        remaining = markedRemaining;
        depth = markedDepth;
        skippedScalar = markedSkippedScalar;
    }
}
