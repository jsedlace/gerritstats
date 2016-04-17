package com.holmsted.gerrit.processors.perperson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.holmsted.file.FileWriter;
import com.holmsted.gerrit.Commit;
import com.holmsted.gerrit.OutputRules;
import com.holmsted.gerrit.processors.CommitDataProcessor;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.tools.generic.ComparisonDateTool;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Type;

import javax.annotation.Nonnull;

class PerPersonHtmlFormatter implements CommitDataProcessor.OutputFormatter<PerPersonData> {
    private static final String RES_OUTPUT_DIR = "res";
    private static final String INDEX_OUTPUT_NAME = "index.html";

    private static final String TEMPLATES_RES_PATH = "templates";
    private static final String VM_PERSON_PROFILE = TEMPLATES_RES_PATH + File.separator + "person_profile.vm";
    private static final String VM_INDEX = TEMPLATES_RES_PATH + File.separator + "index.vm";
    private static final String[] HTML_RESOURCES = {
            "d3.min.js",
            "style.css",
            "jquery.min.js",
            "jquery.tablesorter.min.js",
            "numeral.min.js",
            "bootstrap.css",
            "bootstrap.min.js",
            "gerritstats.js"
    };

    private VelocityEngine velocity = new VelocityEngine();
    private Context baseContext = new VelocityContext();

    private File outputDir;
    private File resOutputDir;

    public PerPersonHtmlFormatter(@Nonnull OutputRules outputRules) {
        velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocity.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocity.init();

        baseContext.put("Gerrit", GerritUtils.class);
        baseContext.put("date", new ComparisonDateTool());
        baseContext.put("outputRules", outputRules);

        outputDir = new File(outputRules.getOutputDir());
        resOutputDir = new File(outputDir.getAbsolutePath() + File.separator + RES_OUTPUT_DIR);
    }

    @Override
    public void format(@Nonnull PerPersonData data) {
        if (!resOutputDir.exists() && !resOutputDir.mkdirs()) {
            throw new IOError(new IOException("Cannot create output directory " + outputDir.getAbsolutePath()));
        }

        IdentityRecordList orderedList = data.toOrderedList(new AlphabeticalOrderComparator());

        baseContext.put("perPersonData", data);
        createIndex(orderedList);
        createPerPersonFiles(orderedList);

        copyFilesToResources(HTML_RESOURCES);

        System.out.println("Output written to " + outputDir.getAbsolutePath() + File.separator + INDEX_OUTPUT_NAME);
    }

    private void copyFilesToResources(String... filenames) {
        for (String filename : filenames) {
            copyFileToResources(filename);
        }
    }

    private void copyFileToResources(String filename) {
        InputStream stream = safeOpenFileResource(filename);
        FileWriter.writeFile(resOutputDir.getAbsolutePath() + File.separator + filename, stream);
    }

    @Nonnull
    private static InputStream safeOpenFileResource(String resourceName) {
        ClassLoader classLoader = PerPersonHtmlFormatter.class.getClassLoader();
        InputStream stream = classLoader.getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOError(new IOException("File not found in jar: " + resourceName));
        }
        return stream;
    }

    private void createIndex(@Nonnull IdentityRecordList identities) {
        Context context = new VelocityContext(baseContext);
        context.put("identities", identities);
        writeTemplate(context, VM_INDEX, INDEX_OUTPUT_NAME);
    }

    private void createPerPersonFiles(@Nonnull IdentityRecordList orderedList) {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(PatchSetCommentTable.class, new PatchSetCommentTableSerializer())
                .create();

        for (IdentityRecord record : orderedList) {
            Context context = new VelocityContext(baseContext);
            context.put("identity", record.identity);
            context.put("record", record);

            String htmlOutputFilename = record.getFilenameStem() + ".html";
            writeTemplate(context, VM_PERSON_PROFILE, htmlOutputFilename);

            writeJsonFile(record, gson);
        }
    }

    /**
     * Writes a .js file with a json object for the given identity record.
     *
     *
     * Ideally, pure .json files would be written,but it's not easily possible
     * to load json files locally from the pages without a web server to serve the
     * requests.
     *
     * See e.g. http://stackoverflow.com/questions/7346563/loading-local-json-file
     */
    private void writeJsonFile(@Nonnull IdentityRecord record, @Nonnull Gson gson) {
        String outputFilename = record.getFilenameStem() + ".js";
        System.out.println("Creating " + outputFilename);

        StringWriter writer = new StringWriter();
        writer.write(String.format("userdata['%s'] = %s;",
                record.getFilenameStem(),
                gson.toJson(record)));

        FileWriter.writeFile(outputDir.getPath()
                + File.separator + "userdata"
                + File.separator + outputFilename, writer.toString());
    }

    private void writeTemplate(@Nonnull Context context, String templateName, String outputFilename) {
        System.out.println("Creating " + outputFilename);

        StringWriter writer = new StringWriter();
        velocity.mergeTemplate(templateName, "UTF-8", context, writer);
        FileWriter.writeFile(outputDir.getPath() + File.separator + outputFilename, writer.toString());
    }

    private static class PatchSetCommentTableSerializer implements JsonSerializer<PatchSetCommentTable> {

        @Override
        public JsonElement serialize(PatchSetCommentTable table,
                                     Type typeOfSrc,
                                     JsonSerializationContext context) {
            JsonArray tableJson = new JsonArray();
            for (Commit key : table.keySet()) {
                JsonObject pair = new JsonObject();
                pair.add("commit", context.serialize(key));
                pair.add("commentsByUser", context.serialize(table.get(key)));
                tableJson.add(pair);
            }
            return tableJson;
        }
    }
}