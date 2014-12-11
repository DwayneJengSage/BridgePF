package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.commons.lang3.StringUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

/**
 * Used internally (i.e. no interface outside the dynamodb package) to
 * avoid collisions of health code. We do not trust that the underlying RNG
 * is always a solid one.
 */
@DynamoDBTable(tableName = "HealthCode")
public class DynamoHealthCode implements DynamoTable {

    private String code;
    private Long version;
    private String studyId;

    public DynamoHealthCode() {
    }

    public DynamoHealthCode(String code) {
        checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty.");
        this.code = code;
    }

    public DynamoHealthCode(String code, String studyId) {
        this(code);
        checkArgument(StringUtils.isNotBlank(studyId), "study identifier cannot be null or empty.");
        this.studyId = studyId;
    }

    @DynamoDBHashKey
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty.");
        
        this.code = code;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }

    @DynamoDBAttribute
    String getStudyIdentifier() {
        return studyId;
    }
    void setStudyIdentifier(String studyId) {
        this.studyId = studyId;
    }
}
