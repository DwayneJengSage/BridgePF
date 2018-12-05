package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

/**
 * Implementation of external identifier.
 */
@DynamoDBTable(tableName = "ExternalIdentifier")
public class DynamoExternalIdentifier implements ExternalIdentifier {

    private String studyId;
    private String substudyId;
    private String identifier;
    private String healthCode;
    
    public DynamoExternalIdentifier() {}
    
    public DynamoExternalIdentifier(StudyIdentifier studyId, String identifier) {
        this.studyId = studyId.getIdentifier();
        this.identifier = identifier;
    }
    
    @DynamoDBHashKey
    @Override
    public String getStudyId() {
        return studyId;
    }
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    
    @DynamoDBAttribute
    @Override
    public String getSubstudyId() {
        return substudyId;
    }
    @Override
    public void setSubstudyId(String substudyId) {
        this.substudyId = substudyId;
    }
    
    @DynamoDBRangeKey
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    @DynamoDBAttribute
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    
}
