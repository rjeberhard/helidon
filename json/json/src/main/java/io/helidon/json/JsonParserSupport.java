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
import java.util.Objects;

import io.helidon.common.Api;

/**
 * Internal cross-module support for built-in JSON parser implementations.
 */
@Api.Internal
public final class JsonParserSupport {
    private JsonParserSupport() {
    }

    /**
     * Finds a later discriminator after a non-discriminator member name has been read.
     * Stops at the end of the matching name, or at the object end if absent.
     *
     * @param parser parser at the end of the first member name
     * @param discriminatorHash discriminator name hash
     * @return positive zero-based member index, or {@code -1} if absent
     */
    public static int findDiscriminator(JsonParser parser, int discriminatorHash) {
        int index = 0;
        while (true) {
            byte token = parser.nextToken();
            if (token != ':') {
                throw parser.createException("Expected ':' to separate key and value", token);
            }
            byte valueStart = parser.nextToken();
            parser.skip();
            token = parser.currentByte();
            // JsonValueParser.skip() already advances past scalars to their delimiter.
            if (valueStart == '{' || valueStart == '[' || (token != ',' && token != '}')) {
                token = parser.nextToken();
            }
            if (token == '}') {
                return -1;
            }
            if (token != ',') {
                throw parser.createException("Expected ',' or '}' as the next bytes", token);
            }
            token = parser.nextToken();
            if (token != '"') {
                throw parser.createException("Expected a key after ','", token);
            }
            int hash = parser.readStringAsHash();
            index++;
            if (hash == discriminatorHash) {
                return index;
            }
        }
    }

    /**
     * Replays an object without the discriminator member already used for subtype selection.
     * All other members, including duplicate discriminator names, remain visible.
     *
     * @param parser parser reset to the object start
     * @param discriminatorIndex zero-based discriminator index, greater than zero
     * @return parser omitting the selected discriminator
     */
    public static JsonParser replayObject(JsonParser parser, int discriminatorIndex) {
        if (parser.currentByte() != '{' || discriminatorIndex <= 0) {
            throw new IllegalArgumentException("Expected an object start and a later discriminator index");
        }
        return new PolymorphicReplayParser(parser, discriminatorIndex);
    }

    /**
     * Creates a JSON number associated with a built-in parser's expansion budget when needed.
     *
     * @param parser parser producing the number
     * @param value decimal value
     * @return JSON number
     */
    public static JsonNumber createJsonNumber(JsonParserBase parser, BigDecimal value) {
        return JsonNumber.create(Objects.requireNonNull(value), Objects.requireNonNull(parser));
    }

    /**
     * Creates a JSON number associated with a built-in parser's expansion budget when needed.
     *
     * @param parser parser producing the number
     * @param value double value
     * @return JSON number
     */
    public static JsonNumber createJsonNumber(JsonParserBase parser, double value) {
        return JsonNumber.create(value, Objects.requireNonNull(parser));
    }

    /**
     * Converts a decimal value with a built-in parser's expansion budget and exception context.
     *
     * @param parser parser reading the value
     * @param value decimal value
     * @return big integer value
     * @throws JsonException if the conversion exceeds either expansion budget
     */
    public static BigInteger toBigInteger(JsonParserBase parser, BigDecimal value) {
        Objects.requireNonNull(parser);
        Objects.requireNonNull(value);
        BigIntegerExpansionBudget budget = BigIntegerExpansionBudget.requiresExpansion(value)
                ? parser.bigIntegerExpansionBudget()
                : BigIntegerExpansionBudget.noAggregate();
        return budget.toBigInteger(value, parser);
    }
}
