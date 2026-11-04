package org.cs7is3.parser;

import java.io.*;
import java.util.*;

import org.cs7is3.schemas.TopicSchema;

public class TopicParser {

    private static boolean preprocessContent(String line, TopicSchema topic, String tag, String header) {
        if (line.startsWith(tag) && topic != null) {
            String cleanedLine = line.replace(tag, "");
            cleanedLine = cleanedLine.replace(header, "");
            cleanedLine = cleanedLine.trim();

            switch (tag) {
                case TopicSchema.numberTag -> topic.setNumber(cleanedLine);
                case TopicSchema.titleTag -> topic.setTitle(cleanedLine);
                case TopicSchema.descriptionTag -> topic.setDescription(cleanedLine);
                case TopicSchema.narrativeTag -> topic.setNarrative(cleanedLine);

            }
            return true;
        }
        return false;
    }

    private static void appendContinuations(TopicSchema topic, String tag, String line) {
        if (topic == null || tag == null || line == null) {
            return;
        }
        switch (tag) {
            case TopicSchema.titleTag -> topic.setTitle(topic.getTitle() + " " + line.trim());
            case TopicSchema.descriptionTag -> topic.setDescription(topic.getDescription() + " " + line.trim());
            case TopicSchema.narrativeTag -> topic.setNarrative(topic.getNarrative() + " " + line.trim());

        }
    }

    private static List<TopicSchema> parseRawInput(String rawInput) {

        List<TopicSchema> parsedTopicList = new ArrayList<>();
        TopicSchema currentTopic = null;
        String currentTag = "";

        String[] fileContent = rawInput.split("\n");

        for (String line : fileContent) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase(TopicSchema.topicEndTag)) {
                if (currentTopic != null) {
                    parsedTopicList.add(currentTopic);
                    currentTopic = null;
                }
            }
            if (line.startsWith(TopicSchema.topicStartTag)) {
                currentTopic = new TopicSchema();
                continue;
            }
            if (preprocessContent(line, currentTopic, TopicSchema.numberTag, "Number:")) {
                currentTag = TopicSchema.numberTag;
                continue;
            } else if (preprocessContent(line, currentTopic, TopicSchema.titleTag, "")) {
                currentTag = TopicSchema.titleTag;
                continue;
            } else if (preprocessContent(line, currentTopic, TopicSchema.descriptionTag, "Description:")) {
                currentTag = TopicSchema.descriptionTag;
                continue;
            } else if (preprocessContent(line, currentTopic, TopicSchema.narrativeTag, "Narrative:")) {
                currentTag = TopicSchema.narrativeTag;
                continue;
            } else {
                appendContinuations(currentTopic, currentTag, line);
            }
        }
        if (currentTopic != null) {
            parsedTopicList.add(currentTopic);
        }
        return parsedTopicList;
    }

    private static String readUsingInputPath(InputStream inputStream) throws IOException {
        StringBuilder resultString = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultString.append(line).append("\n");
            }
        }
        return resultString.toString();
    }

    public static List<TopicSchema> parseTopics(String filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("File path is null");
        }
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("No file found at file path: " + filePath);
        }
        FileInputStream inputStream = new FileInputStream(inputFile);
        String rawInput = readUsingInputPath(inputStream);
        return parseRawInput(rawInput);
    }
}
