package org.sagebionetworks.bridge.upload;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.schema.SchemaUtils;

/** Utility class that contains static utility methods for handling uploads. */
public class UploadUtil {
    private static final Logger logger = LoggerFactory.getLogger(UploadUtil.class);

    // Common field names
    public static final String FILENAME_INFO_JSON = "info.json";
    public static final String FILENAME_METADATA_JSON = "metadata.json";
    public static final String FIELD_APP_VERSION = "appVersion";
    public static final String FIELD_CREATED_ON = "createdOn";
    public static final String FIELD_DATA_FILENAME = "dataFilename";
    public static final String FIELD_FORMAT = "format";
    public static final String FIELD_ITEM = "item";
    public static final String FIELD_PHONE_INFO = "phoneInfo";
    public static final String FIELD_SCHEMA_REV = "schemaRevision";
    public static final String FIELD_SURVEY_GUID = "surveyGuid";
    public static final String FIELD_SURVEY_CREATED_ON = "surveyCreatedOn";

    // Regex patterns and strings for validation.
    private static final Pattern FIELD_NAME_MULTIPLE_SPECIAL_CHARS_PATTERN = Pattern.compile("[\\-\\._ ]{2,}");
    private static final Pattern FIELD_NAME_SPECIAL_CHARS_PATTERN = Pattern.compile("[\\-\\._ ]");
    private static final Pattern FIELD_NAME_VALID_CHARS_PATTERN = Pattern.compile("[a-zA-Z0-9\\-\\._ ]+");
    public static final String INVALID_ANSWER_CHOICE_ERROR_MESSAGE = "invalid value %s: must start and end with an " +
            "alphanumeric character, can only contain alphanumeric characters, spaces, dashes, underscores, and " +
            "periods, can't contain two or more non-alphanumeric characters in a row";
    public static final String INVALID_FIELD_NAME_ERROR_MESSAGE = INVALID_ANSWER_CHOICE_ERROR_MESSAGE +
            ", and can't be a reserved keyword";

    // List of reserved SQL keywords and Synapse keywords that can't be used as field names.
    private static final Set<String> RESERVED_FIELD_NAME_LIST = ImmutableSet.<String>builder().add("access", "add",
            "all", "alter", "and", "any", "as", "asc", "audit", "between", "by", "char", "check", "cluster", "column",
            "column_value", "comment", "compress", "connect", "create", "current", "date", "decimal", "default",
            "delete", "desc", "distinct", "drop", "else", "exclusive", "exists", "false", "file", "float", "for", "from",
            "grant", "group", "having", "identified", "immediate", "in", "increment", "index", "initial", "insert",
            "integer", "intersect", "into", "is", "level", "like", "lock", "long", "maxextents", "minus", "mlslabel",
            "mode", "modify", "nested_table_id", "noaudit", "nocompress", "not", "nowait", "null", "number", "of",
            "offline", "on", "online", "option", "or", "order", "pctfree", "prior", "public", "raw", "rename",
            "resource", "revoke", "row", "row_id", "row_version", "rowid", "rownum", "rows", "select", "session", "set",
            "share", "size", "smallint", "start", "successful", "synonym", "sysdate", "table", "then", "time", "to",
            "trigger", "true", "uid", "union", "unique", "update", "user", "validate", "values", "varchar", "varchar2", "view",
            "whenever", "where", "with").build();

    /*
     * Suffix used for unit fields in schemas. For example, if we had a field called "jogtime", we would have a field
     * called "jogtime_unit".
     */
    public static final String UNIT_FIELD_SUFFIX = "_unit";
    public static final String DIASTOLIC_FIELD_SUFFIX = "_diastolic";
    public static final String SYSTOLIC_FIELD_SUFFIX = "_systolic";

    /**
     * Helper method which encapsulates validating an upload before adding it to the attachment map. Right now, all it
     * does is filter out empty attachments.
     *
     * @param attachmentMap
     *         map to add the attachment to
     * @param fieldName
     *         attachment field name (map key)
     * @param data
     *         attachment data (map value)
     */
    public static void addAttachment(Map<String, byte[]> attachmentMap, String fieldName, byte[] data) {
        if (data.length != 0) {
            attachmentMap.put(fieldName, data);
        }
    }

    /** Utility method for canonicalizing an upload JSON value given the schema's field type. */
    public static CanonicalizationResult canonicalize(final JsonNode valueNode, UploadFieldType type) {
        if (valueNode == null || valueNode.isNull()) {
            // Short-cut: Don't do anything if the value is Java null (non-existent) or JSON null.
            return CanonicalizationResult.makeResult(valueNode);
        }

        switch (type) {
            case ATTACHMENT_BLOB:
            case ATTACHMENT_CSV:
            case ATTACHMENT_JSON_BLOB:
            case ATTACHMENT_JSON_TABLE:
            case ATTACHMENT_V2:
            case INLINE_JSON_BLOB: {
                // always valid, always canonical
                return CanonicalizationResult.makeResult(valueNode);
            }
            case BOOLEAN: {
                if (valueNode.isIntegralNumber()) {
                    // For numbers, 0 is false and everything else is true.
                    boolean booleanValue = valueNode.intValue() != 0;
                    return CanonicalizationResult.makeResult(BooleanNode.valueOf(booleanValue));
                } else if (valueNode.isTextual()) {
                    // We accept "true" and "false" (ignoring case), but not anything else.
                    String boolStr = valueNode.textValue();
                    if ("false".equalsIgnoreCase(boolStr)) {
                        return CanonicalizationResult.makeResult(BooleanNode.FALSE);
                    } else if ("true".equalsIgnoreCase(boolStr)) {
                        return CanonicalizationResult.makeResult(BooleanNode.TRUE);
                    } else {
                        return CanonicalizationResult.makeError("Invalid boolean string " + boolStr);
                    }
                } else if (valueNode.isBoolean()) {
                    // This is already canonicalized.
                    return CanonicalizationResult.makeResult(valueNode);
                } else {
                    return CanonicalizationResult.makeError("Invalid boolean JSON value " + valueNode.toString());
                }
            }
            case CALENDAR_DATE: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid calendar date JSON value " +
                            valueNode.toString());
                }

                // parseIosCalendarDate() will truncate full date-times to calendar dates as needed.
                String dateStr = valueNode.textValue();
                LocalDate parsedDate = parseIosCalendarDate(dateStr);

                if (parsedDate != null) {
                    return CanonicalizationResult.makeResult(new TextNode(DateUtils.getCalendarDateString(
                            parsedDate)));
                } else {
                    return CanonicalizationResult.makeError("Invalid calendar date string " + dateStr);
                }
            }
            case DURATION_V2: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid duration JSON value " + valueNode.toString());
                }

                String durationStr = valueNode.textValue();
                try {
                    // Joda Duration only parses seconds and milliseconds. Use Period to get an ISO 8601 duration.
                    // Period.parse() never returns null.
                    Period parsedPeriod = Period.parse(durationStr);
                    return CanonicalizationResult.makeResult(new TextNode(parsedPeriod.toString()));
                } catch (IllegalArgumentException ex) {
                    return CanonicalizationResult.makeError("Invalid duration string " + durationStr);
                }
            }
            case FLOAT: {
                if (valueNode.isNumber()) {
                    // Already canonicalized.
                    return CanonicalizationResult.makeResult(valueNode);
                } else if (valueNode.isTextual()) {
                    // Convert to decimal.
                    String decimalStr = valueNode.textValue();
                    try {
                        BigDecimal parsedDecimal = new BigDecimal(decimalStr);
                        return CanonicalizationResult.makeResult(new DecimalNode(parsedDecimal));
                    } catch (IllegalArgumentException ex) {
                        return CanonicalizationResult.makeError("Invalid decimal string " + decimalStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid decimal JSON value " + valueNode.toString());
                }
            }
            case INT: {
                if (valueNode.isIntegralNumber()) {
                    // Already canonicalized
                    return CanonicalizationResult.makeResult(valueNode);
                } else if (valueNode.isFloatingPointNumber()) {
                    // Convert floats to ints.
                    return CanonicalizationResult.makeResult(new BigIntegerNode(valueNode.bigIntegerValue()));
                } else if (valueNode.isTextual()) {
                    // Parse as a big decimal, truncate to big int.
                    String numberStr = valueNode.textValue();
                    try {
                        BigDecimal parsedNumber = new BigDecimal(numberStr);
                        return CanonicalizationResult.makeResult(new BigIntegerNode(parsedNumber.toBigInteger()));
                    } catch (IllegalArgumentException ex) {
                        return CanonicalizationResult.makeError("Invalid int string " + numberStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid int JSON value " + valueNode.toString());
                }
            }
            case MULTI_CHOICE: {
                // Expect it in the format ["foo", "bar", "baz"]
                if (!valueNode.isArray()) {
                    return CanonicalizationResult.makeError("Invalid multi-choice JSON value " + valueNode.toString());
                }

                // Fields inside might not be strings. Trivially convert them to strings if they are not.
                ArrayNode convertedValueNode = BridgeObjectMapper.get().createArrayNode();
                int numValues = valueNode.size();
                for (int i = 0; i < numValues; i++) {
                    // Sanitize the multi-choice answers so they match up with the field def's multi-choice answer list
                    String rawAnswer = getAsString(valueNode.get(i));
                    String sanitizedAnswer = SchemaUtils.sanitizeFieldName(rawAnswer);
                    convertedValueNode.add(sanitizedAnswer);
                }

                return CanonicalizationResult.makeResult(convertedValueNode);
            }
            case SINGLE_CHOICE: {
                // Older versions would send a single-element array (example: ["foo"]) as a single-choice answer. For
                // backwards compatibility, accept arrays, but use just the single element.
                JsonNode convertedValueNode;
                if (valueNode.isArray()) {
                    if (valueNode.size() == 1) {
                        convertedValueNode = valueNode.get(0);
                    } else {
                        return CanonicalizationResult.makeError("Single-choice array doesn't have exactly 1 element: "
                                + valueNode.toString());
                    }
                } else {
                    // Not an array. Pass this straight through to the next step.
                    convertedValueNode = valueNode;
                }

                // If the value isn't a string, trivially convert it into a string.
                return CanonicalizationResult.makeResult(convertToStringNode(convertedValueNode));
            }
            case STRING: {
                // If the value isn't a string, trivially convert it into a string.
                return CanonicalizationResult.makeResult(convertToStringNode(valueNode));
            }
            case TIME_V2: {
                if (!valueNode.isTextual()) {
                    return CanonicalizationResult.makeError("Invalid time JSON value " + valueNode.toString());
                }

                // This is a time without date or time-zone, akin to Joda LocalTime. First parse it as a LocalTime.
                String timeStr = valueNode.textValue();
                LocalTime parsedLocalTime = null;
                try {
                    parsedLocalTime = LocalTime.parse(timeStr);
                } catch (IllegalArgumentException ex) {
                    // Swallow exception. We have better logging later in the chain.
                }

                if (parsedLocalTime == null) {
                    // If that doesn't work, fall back to parsing a full timestamp and use just the LocalTime part.
                    DateTime parsedDateTime = parseIosTimestamp(timeStr);
                    if (parsedDateTime != null) {
                        parsedLocalTime = parsedDateTime.toLocalTime();
                    }
                }

                if (parsedLocalTime != null) {
                    return CanonicalizationResult.makeResult(new TextNode(parsedLocalTime.toString()));
                } else {
                    return CanonicalizationResult.makeError("Invalid time string " + timeStr);
                }
            }
            case TIMESTAMP: {
                if (valueNode.isNumber()) {
                    // If this is a number, then it's epoch milliseconds (implicitly in UTC).
                    return CanonicalizationResult.makeResult(new TextNode(DateUtils.convertToISODateTime(
                            valueNode.longValue())));
                } else if (valueNode.isTextual()) {
                    String dateTimeStr = valueNode.textValue();
                    DateTime parsedDateTime = parseIosTimestamp(dateTimeStr);
                    if (parsedDateTime != null) {
                        return CanonicalizationResult.makeResult(new TextNode(parsedDateTime.toString()));
                    } else {
                        return CanonicalizationResult.makeError("Invalid date-time (timestamp) string " + dateTimeStr);
                    }
                } else {
                    return CanonicalizationResult.makeError("Invalid date-time (timestamp) JSON value " +
                            valueNode.toString());
                }
            }
            default: {
                // Should never happen, but just in case.
                return CanonicalizationResult.makeError("Unknown field type " + type.name());
            }
        }
    }

    /**
     * Call this function to convert any JSON node into a string node. If the JSON node is already a string, return it
     * as is. If it's not a string, the returned node is a string with the JSON text as its value. For type-safety and
     * null safety, null values are returned as is.
     *
     * @param inputNode
     *         node to convert
     * @return converted node
     */
    public static JsonNode convertToStringNode(JsonNode inputNode) {
        if (inputNode == null || inputNode.isNull()) {
            // pass back nulls
            return inputNode;
        } else if (inputNode.isTextual()) {
            // This is already a string node. Note that this is identical to the null case, but we use a separate
            // else-if block for code organization. (The compiler will optimize it away anyway.)
            return inputNode;
        } else {
            return new TextNode(inputNode.toString());
        }
    }

    /**
     * <p>
     * Because different files in an upload can have the same keys, the canonical field name is the
     * "filename.fieldname". Additionally, schema fields can refer to the whole file, so "filename" is also a valid
     * field name. This function takes in a list of JSON files and constructs a list of all valid fields, which is a
     * flattened map of all JSON files by name as well as all top-level key-value pairs in the form of
     * "filename.fieldname".
     * </p>
     * <p>
     * Note: This automatically ignores info.json, as that is a metadata file, not a data file.
     * </p>
     * <p>
     * Note: This does not modify the original map.
     * </p>
     *
     * @param jsonDataMap
     *         map of JSON files to flatten
     * @return the flattened map
     */
    public static Map<String, JsonNode> flattenJsonDataMap(Map<String, JsonNode> jsonDataMap) {
        Map<String, JsonNode> dataFieldMap = new HashMap<>();
        for (Map.Entry<String, JsonNode> oneJsonFile : jsonDataMap.entrySet()) {
            String filename = oneJsonFile.getKey();
            if (filename.equals(FILENAME_INFO_JSON)) {
                // Not info.json. Skip.
                continue;
            }

            JsonNode oneJsonFileNode = oneJsonFile.getValue();
            Iterator<String> fieldNameIter = oneJsonFileNode.fieldNames();
            while (fieldNameIter.hasNext()) {
                // Pre-pend file name with field name, so if there are duplicate filenames, they get disambiguated.
                String oneFieldName = fieldNameIter.next();
                dataFieldMap.put(filename + "." + oneFieldName, oneJsonFileNode.get(oneFieldName));
            }

            // Add the whole JSON file into the flattened map. This allows us to simplify some codepaths further down
            // the chain.
            dataFieldMap.put(filename, oneJsonFileNode);
        }

        return dataFieldMap;
    }

    /**
     * Helper method to get the value of a JSON node as string. If the JSON node is a string type, it will return the
     * string value. Otherwise, it'll return the text representation of the JSON.
     *
     * @param node
     *         JSON node to convert to string
     * @return JSON node as string
     */
    public static String getAsString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.textValue();
        } else {
            return node.toString();
        }
    }

    // Helper method to test that two field defs are identical except for the maxAppVersion field (which can only be
    // added to newFieldDef). This is because adding maxAppVersion is how we mark fields as deprecated. Package-scoped
    // to facilitate unit tests.

    /**
     * Helper method to test whether a schema field definition can be modified as specified.
     *
     * @param oldFieldDef
     *         the original field definition, before modification
     * @param newFieldDef
     *         the new field definition, representing the intended modification
     * @return true if the fields can be modified, false otherwise
     */
    public static boolean isCompatibleFieldDef(UploadFieldDefinition oldFieldDef, UploadFieldDefinition newFieldDef) {
        // Short-cut: If they are equal, then they are compatible.
        if (oldFieldDef.equals(newFieldDef)) {
            return true;
        }

        // Attributes that don't affect field def compatibility
        // fileExtension - This is just a hint to BridgeEX for serializing the values. It doesn't affect the columns or
        //   validation.
        // mimeType - Similarly, this is also just a serialization hint.

        // Different types are obviously not compatible.
        // This is the most likely reason fields are incompatible. Check this first.
        if (oldFieldDef.getType() != newFieldDef.getType()) {
            return false;
        }

        // allowOther - You can flip this to true (adds a field), but you can't flip it from true to false.
        Boolean oldAllowOther = oldFieldDef.getAllowOtherChoices();
        Boolean newAllowOther = newFieldDef.getAllowOtherChoices();
        if (oldAllowOther != null && oldAllowOther && (newAllowOther == null || !newAllowOther)) {
            return false;
        }

        // Changing the maxLength will cause the Synapse column to be recreated, so we need to block this. (Strictly
        // speaking, BridgeEX has code to prevent this by ignoring the new max length if it is different, for legacy
        // reasons but this is confusing behavior, so we should just prevent this situation from happening to begin
        // with.)
        if (!Objects.equals(oldFieldDef.getMaxLength(), newFieldDef.getMaxLength())) {
            return false;
        }

        // Note: multiChoiceAnswerList can never be null.
        List<String> oldMultiChoiceAnswerList = oldFieldDef.getMultiChoiceAnswerList();
        List<String> newMultiChoiceAnswerList = newFieldDef.getMultiChoiceAnswerList();
        // Choices might have been re-ordered, so convert to sets so we can determine the choices that have been
        // added, deleted, or retained.
        Set<String> oldMultiChoiceAnswerSet = new HashSet<>(oldMultiChoiceAnswerList);
        Set<String> newMultiChoiceAnswerSet = new HashSet<>(newMultiChoiceAnswerList);

        // Adding choices is okay. Deleting choices is not. (Renaming is deleting one choice and adding another.)
        Set<String> deletedChoiceSet = Sets.difference(oldMultiChoiceAnswerSet, newMultiChoiceAnswerSet);
        if (!deletedChoiceSet.isEmpty()) {
            return false;
        }

        // This should never happen, but if for some reason, the field name changes, the fields are incompatible.
        if (!Objects.equals(oldFieldDef.getName(), newFieldDef.getName())) {
            return false;
        }

        // Going from required to optional is fine. Going from optional to required is okay only if you're adding a
        // minAppVerison.
        if (!oldFieldDef.isRequired() && newFieldDef.isRequired()) {
            return false;
        }

        // isUnboundedText controls whether we use a String or LargeText in Synapse. So changing this is not
        // compatible. (null defaults to false)
        //noinspection ConstantConditions
        boolean oldIsUnboundedText = oldFieldDef.isUnboundedText() != null ? oldFieldDef.isUnboundedText() : false;
        //noinspection ConstantConditions
        boolean newIsUnboundedText = newFieldDef.isUnboundedText() != null ? newFieldDef.isUnboundedText() : false;
        if (oldIsUnboundedText != newIsUnboundedText) {
            return false;
        }

        // If we passed all incompatibility checks, then we're compatible.
        return true;
    }

    /**
     * <p>
     * Validates a survey answer choice. Since these become Synapse table fields, we need to validate them. For
     * consistency, we want to apply the same rules to both single-choice and multi-choice.
     * </p>
     * <p>
     * Rules:
     * 1. must start and end with an alphanumeric character
     * 2. can only contain alphanumeric characters, spaces, dashes, underscores, and periods
     * 3. can't contain two or more non-alphanumeric characters in a row
     * </p>
     *
     * @param name
     *         name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidAnswerChoice(String name) {
        // Blank names are invalid.
        if (StringUtils.isBlank(name)) {
            return false;
        }

        // Can only contain alphanumeric, space, dash, underscore, and period.
        if (!FIELD_NAME_VALID_CHARS_PATTERN.matcher(name).matches()) {
            return false;
        }

        // Must start and end with an alphanumeric char
        String firstChar = name.substring(0, 1);
        if (FIELD_NAME_SPECIAL_CHARS_PATTERN.matcher(firstChar).matches()) {
            return false;
        }

        int nameLength = name.length();
        String lastChar = name.substring(nameLength - 1, nameLength);
        if (FIELD_NAME_SPECIAL_CHARS_PATTERN.matcher(lastChar).matches()) {
            return false;
        }

        // Can't contain multiple special chars in a row.
        if (FIELD_NAME_MULTIPLE_SPECIAL_CHARS_PATTERN.matcher(name).find()) {
            return false;
        }

        // Exhausted all our rules, so it must be valid.
        return true;
    }

    /**
     * Validates a schema field name or survey question name (identifier). This includes all the same rules as
     * {@link #isValidAnswerChoice}, and in addition, the name can't be a reserved keyword.
     *
     * @param name
     *         name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSchemaFieldName(String name) {
        // Valid schema field name follows all the same rules as a valid survey answer choice. (This also checks for
        // nulls and blanks.)
        if (!isValidAnswerChoice(name)) {
            return false;
        }

        // In addition, it can't be a reserved keyword.
        if (RESERVED_FIELD_NAME_LIST.contains(name)) {
            return false;
        }

        // Exhausted all our rules, so it must be valid.
        return true;
    }

    /**
     * <p>
     * For some reason, the iOS are inserting arbitrary times into calendar dates. We need to convert them back to
     * calendar dates. This method simply detects if the string is too long and then truncates it into 10 characters,
     * then tries to parse the result.
     * </p>
     * <p>
     * Note that converting timestamps into calendar dates is inherently ambiguous, since a single timestamp can
     * represent two (or more) calendar dates. Example: 2015-12-23T00:00Z vs 2015-12-22T16:00-08:00
     * </p>
     *
     * @param dateStr
     *         string to parse into a calendar date
     * @return parsed LocalDate, or null if it couldn't be parsed
     */
    public static LocalDate parseIosCalendarDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        if (dateStr.length() > 10) {
            logger.warn("Non-standard calendar date in upload data: " + dateStr);
            dateStr = dateStr.substring(0, 10);
        }

        try {
            return DateUtils.parseCalendarDate(dateStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted calendar date in upload data: " + dateStr);
            return null;
        }
    }

    /**
     * For some reason, the iOS apps are sometimes sending timestamps in form "YYYY-MM-DD hh:mm:ss +ZZZZ", which is
     * non-ISO-compliant and can't be parsed by JodaTime. We'll need to convert these to ISO format, generally
     * "YYYY-MM-DDThh:mm:ss+ZZZZ".
     *
     * @param timestampStr
     *         raw timestamp string
     * @return parsed DateTime, or null if it couldn't be parsed
     */
    public static DateTime parseIosTimestamp(String timestampStr) {
        // Timestamps must have at least 11 chars to represent the date, at minimum.
        if (StringUtils.isBlank(timestampStr) || timestampStr.length() < 11) {
            return null;
        }

        // Detect if this is iOS non-standard format by checking to see if the 10th char is a space.
        if (timestampStr.charAt(10) == ' ') {
            // Log something, so we can keep track of how often this happens.
            logger.warn("Non-standard timestamp in upload data: " + timestampStr);

            // Attempt to convert this by replacing the 10th char with a T and then stripping out all spaces.
            timestampStr = timestampStr.substring(0, 10) + 'T' + timestampStr.substring(11);
            timestampStr = timestampStr.replaceAll("\\s+", "");
        }

        try {
            return DateUtils.parseISODateTime(timestampStr);
        } catch (IllegalArgumentException ex) {
            logger.warn("Malformatted timestamp in upload data: " + timestampStr);
            return null;
        }
    }

    /**
     * <p>
     * Sanitize the field names from the upload to match the rules for sanitizing field names in schemas. (This hasn't
     * yet become a problem in Prod, but we're adding the safeguards to ensure it never becomes a problem.)
     * </p>
     * <p>
     * Note: This does not modify the original map.
     * </p>
     *
     * @param rawFieldMap
     *         map whose keys need to be sanitizied
     * @param <T>
     *         generic type corresponding to the map's value type (generally JsonNode for JSON data or byte[] for
     *         non-JSON data)
     * @return the sanitized map
     */
    public static <T> Map<String, T> sanitizeFieldNames(Map<String, T> rawFieldMap) {
        Map<String, T> sanitizedFieldMap = new HashMap<>();
        for (Map.Entry<String, T> oneRawFieldEntry : rawFieldMap.entrySet()) {
            String rawFieldName = oneRawFieldEntry.getKey();
            String sanitizedFieldName = SchemaUtils.sanitizeFieldName(rawFieldName);
            sanitizedFieldMap.put(sanitizedFieldName, oneRawFieldEntry.getValue());
        }
        return sanitizedFieldMap;
    }
}
