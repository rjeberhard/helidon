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

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PolymorphicReplayParserTest {
    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testNestedContainersAndReplay(ParserMethod method) {
        String fields = "\"a\":[{\"kind\":\"nested\"},2],\"b\":{\"kind\":\"other\"}";
        JsonObject expected = JsonParser.create("{" + fields + "}").readJsonObject();
        for (String json : List.of("{\"a\":[{\"kind\":\"nested\"},2],\"kind\":\"selected\",\"b\":{\"kind\":\"other\"}}",
                                   "{" + fields + ",\"kind\":\"selected\"}")) {
            int index = json.indexOf("selected") < json.indexOf("other") ? 1 : 2;
            for (JsonParser source : List.of(method.createParser(json),
                                              JsonParser.create(JsonParser.create(json).readJsonObject()))) {
                JsonParser parser = JsonParserSupport.replayObject(source, index);
                parser.mark();
                assertThat(parser.readJsonObject(), is(expected));
                parser.resetToMark();
                assertThat(parser.readJsonObject(), is(expected));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void testSkipBeforeDiscriminator(ParserMethod method) {
        for (String value : List.of("null", "true", "17", "1.25", "\"text\"", "[]", "{}")) {
            String json = "{\"a\":" + value + ",\"kind\":\"selected\",\"b\":23}";
            for (JsonParser source : List.of(method.createParser(json),
                                              JsonParser.create(JsonParser.create(json).readJsonObject()))) {
                JsonParser parser = JsonParserSupport.replayObject(source, 1);
                parser.nextToken();
                assertThat(parser.readString(), is("a"));
                parser.nextToken();
                parser.nextToken();
                parser.skip();
                parser.mark();
                assertThat(parser.nextToken(), is((byte) ','));
                parser.resetToMark();
                assertThat(parser.nextToken(), is((byte) ','));
                parser.nextToken();
                assertThat(parser.readString(), is("b"));
                parser.nextToken();
                parser.nextToken();
                assertThat(parser.readInt(), is(23));
                assertThat(parser.nextToken(), is((byte) '}'));
            }
        }
    }
}
