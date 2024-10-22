package com.precognox.ceu.legislative_data_collector.utils;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.precognox.ceu.legislative_data_collector.exceptions.DataCollectionException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextDiffTool {
    private final static String OLD_START_TAG = "<old#>";
    private final static String OLD_END_TAG = "<#old>";
    private final static String NEW_START_TAG = "<new#>";
    private final static String NEW_END_TAG = "<#new>";
    private final static Pattern DIFF_REGEX = Pattern.compile(String.format("(?:(%s)(.*?)%s|(%s)(.*?)%s)", OLD_START_TAG, OLD_END_TAG, NEW_START_TAG, NEW_END_TAG));
    private final static int CAPTURING_GROUP_COUNT = 2;
    private final static int TAG_GROUP_BASE_INDEX = 1;
    private final static int DIFFTEXT_GROUP_BASE_INDEX = 2;

    private final DiffRowGenerator diffRowGenerator;

    public TextDiffTool() {
        this.diffRowGenerator = DiffRowGenerator.create()
                .ignoreWhiteSpaces(true)
                .reportLinesUnchanged(false)
                .mergeOriginalRevised(true)
                .showInlineDiffs(true)
                .oldTag(startingTag -> startingTag ? OLD_START_TAG : OLD_END_TAG)
                .newTag(startingTag -> startingTag ? NEW_START_TAG : NEW_END_TAG)
                .build();
    }


    public long getCharDifference(String oldText, String newText) throws DataCollectionException {
        validate(oldText, "Older");
        validate(newText, "New");

        String oldTextLower = oldText.toLowerCase();
        String newTextLower = newText.toLowerCase();

        List<DiffRow> diffRows = diffRowGenerator.generateDiffRows(splitIntoLines(oldTextLower), splitIntoLines(newTextLower));

        AtomicLong diffCharCount = new AtomicLong();
        diffRows.stream()
                .filter(diffRow -> !diffRow.getTag().equals(DiffRow.Tag.EQUAL))
                .map(DiffRow::getOldLine)
                .forEach(line -> {

                    Matcher matcher = DIFF_REGEX.matcher(line);
                    List<MatchResult> matches = matcher.results().toList();
                    int index = 0;

                    for (; index < matches.size(); index++) {
                        String tag = getGroupFromMatch(matches.get(index), TAG_GROUP_BASE_INDEX);
                        int diffTextLength = TextUtils.getLengthWithoutWhitespace(getGroupFromMatch(matches.get(index), DIFFTEXT_GROUP_BASE_INDEX));

                        //Change can be seen by old and new tags being right next to each other. (First the old and then the new)
                        //In this case the next match would be the new tag of the change, so the index is incremented so the next iteration will skip it. (As it needs to be calculated paired with the old tag)
                        if (tag.equals(OLD_START_TAG) && isChangedText(matches.get(index), line)) {
                            int deletedTextLength = diffTextLength;
                            index++;
                            int insertedTextLength = TextUtils.getLengthWithoutWhitespace(getGroupFromMatch(matches.get(index), DIFFTEXT_GROUP_BASE_INDEX));
                            diffCharCount.addAndGet(Math.max(deletedTextLength, insertedTextLength));
                        } else {
                            diffCharCount.addAndGet(diffTextLength);
                        }
                    }
                });


        return diffCharCount.get();
    }

    private void validate(String text, String info) throws DataCollectionException {
        if (Objects.isNull(text) || text.isBlank()) {
            throw new DataCollectionException(String.format("%s text is invalid (null or blank)", info));
        }
    }

    private List<String> splitIntoLines(String text) {
        return Arrays.stream(text.trim().split("\n"))
                .filter(line -> !line.isBlank())
                .map(String::trim)
                .toList();
    }

    private String getGroupFromMatch(MatchResult result, int startIndex) {
        int tagGroupIndex = startIndex;
        while (result.group(tagGroupIndex) == null) {
            tagGroupIndex += CAPTURING_GROUP_COUNT;
        }
        return result.group(tagGroupIndex);
    }

    private boolean isChangedText(MatchResult result, String line) {
        try {
            String followingText = line.substring(result.end(), result.end() + NEW_START_TAG.length());
            return followingText.equals(NEW_START_TAG);
        } catch (StringIndexOutOfBoundsException ex) {
            return false;
        }
    }
}
