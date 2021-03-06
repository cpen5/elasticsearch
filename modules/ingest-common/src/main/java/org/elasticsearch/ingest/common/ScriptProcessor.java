/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.ingest.common;

import com.fasterxml.jackson.core.JsonFactory;

import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.common.xcontent.json.JsonXContentParser;
import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.ScriptService;

import java.util.Arrays;
import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.newConfigurationException;

/**
 * Processor that evaluates a script with an ingest document in its context.
 */
public final class ScriptProcessor extends AbstractProcessor {

    public static final String TYPE = "script";
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private final Script script;
    private final ScriptService scriptService;

    /**
     * Processor that evaluates a script with an ingest document in its context
     *
     * @param tag The processor's tag.
     * @param script The {@link Script} to execute.
     * @param scriptService The {@link ScriptService} used to execute the script.
     */
    ScriptProcessor(String tag, Script script, ScriptService scriptService)  {
        super(tag);
        this.script = script;
        this.scriptService = scriptService;
    }

    /**
     * Executes the script with the Ingest document in context.
     *
     * @param document The Ingest document passed into the script context under the "ctx" object.
     */
    @Override
    public void execute(IngestDocument document) {
        ExecutableScript.Factory factory = scriptService.compile(script, ExecutableScript.INGEST_CONTEXT);
        ExecutableScript executableScript = factory.newInstance(script.getParams());
        executableScript.setNextVar("ctx",  document.getSourceAndMetadata());
        executableScript.run();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    Script getScript() {
        return script;
    }

    public static final class Factory implements Processor.Factory {
        private final ScriptService scriptService;

        public Factory(ScriptService scriptService) {
            this.scriptService = scriptService;
        }

        @Override
        public ScriptProcessor create(Map<String, Processor.Factory> registry, String processorTag,
                                      Map<String, Object> config) throws Exception {
            XContentBuilder builder = XContentBuilder.builder(JsonXContent.jsonXContent).map(config);
            JsonXContentParser parser = new JsonXContentParser(NamedXContentRegistry.EMPTY,
                JSON_FACTORY.createParser(builder.bytes().streamInput()));
            Script script = Script.parse(parser);

            Arrays.asList("id", "source", "inline", "lang", "params", "options").forEach(config::remove);

            // verify script is able to be compiled before successfully creating processor.
            try {
                scriptService.compile(script, ExecutableScript.INGEST_CONTEXT);
            } catch (ScriptException e) {
                throw newConfigurationException(TYPE, processorTag, null, e);
            }

            return new ScriptProcessor(processorTag, script, scriptService);
        }
    }
}
