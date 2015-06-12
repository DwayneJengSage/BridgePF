package org.sagebionetworks.bridge.dynamodb;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.collections.CollectionUtils;
import org.fest.util.CollectionFilter;
import org.fest.util.Collections;
import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

@Component
public class DynamoStudyConsentDao implements StudyConsentDao {

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder().withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.CONSISTENT)
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoStudyConsent1.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public StudyConsent addConsent(StudyIdentifier studyIdentifier, String storagePath, DateTime createdOn) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(createdOn.getMillis());
        consent.setStoragePath(storagePath);
        mapper.save(consent);
        return consent;
    }

    @Override
    public StudyConsent activate(StudyConsent studyConsent) {
        // Doing the stupidest thing that will work here.
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyConsent.getStudyKey());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey);

        // Set all of the active consents except the current one to inactive.
        List<DynamoStudyConsent1> consentsToSave = Lists.newArrayList();
        
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        for (DynamoStudyConsent1 consent : consents) {
            boolean activation = (consent.getCreatedOn() == studyConsent.getCreatedOn());
            if (consent.getActive() != activation) {
                consent.setActive(activation);
                consentsToSave.add(consent);
            }
        }
        // Ugly.
        boolean hasActive = false;
        for (DynamoStudyConsent1 consent : consentsToSave) {
            if (consent.getActive()) {
                hasActive = true;
            }
        }
        // impossible to be true if collection is empty, so there's something to save...
        if (hasActive) { 
            List<FailedBatch> failures = mapper.batchSave(consentsToSave);
            BridgeUtils.ifFailuresThrowException(failures);
        }
        return getConsent(new StudyIdentifierImpl(studyConsent.getStudyKey()), studyConsent.getCreatedOn());

        /* Well this seems to not be working, let's try something different
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setCreatedOn(studyConsent.getCreatedOn());
        consent = mapper.load(consent);
        consent.setActive(true);

        List<DynamoStudyConsent1> consentsToSave = Lists.newArrayList(consent);

        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyConsent.getStudyKey());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));

        // Set all of the active consents except the current one to inactive.
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        for (DynamoStudyConsent1 otherConsent : consents) {
            if (otherConsent.getCreatedOn() != consent.getCreatedOn()) {
                otherConsent.setActive(false);
                consentsToSave.add(otherConsent);
            }
        }
        List<FailedBatch> failures = mapper.batchSave(consentsToSave);
        BridgeUtils.ifFailuresThrowException(failures);

        return consent;
        */
    }
    
    /**
     * Note that if there is no other active consent, the study will not be able to return a 
     * consent and will be broken.
     * @param studyConsent
     * @return
     */
    /*
    @Override
    public StudyConsent deactivate(StudyConsent studyConsent) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyConsent.getStudyKey());
        consent.setCreatedOn(studyConsent.getCreatedOn());
        consent = mapper.load(consent);
        consent.setActive(false);
        mapper.save(consent);
        return consent;
    }*/

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false)
                .withQueryFilterEntry("active", new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN("1")));
        PaginatedQueryList<DynamoStudyConsent1> page = mapper.query(DynamoStudyConsent1.class, queryExpression);
        if (page.isEmpty()) {
            return null;
        }
        return page.iterator().next();
    }

    @Override
    public StudyConsent getConsent(StudyIdentifier studyIdentifier, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(timestamp);
        return mapper.load(consent);
    }

    @Override
    public List<StudyConsent> getConsents(StudyIdentifier studyIdentifier) {
        DynamoStudyConsent1 hashKey = new DynamoStudyConsent1();
        hashKey.setStudyKey(studyIdentifier.getIdentifier());
        DynamoDBQueryExpression<DynamoStudyConsent1> queryExpression = 
                new DynamoDBQueryExpression<DynamoStudyConsent1>()
                .withHashKeyValues(hashKey)
                .withScanIndexForward(false);
        PaginatedQueryList<DynamoStudyConsent1> consents = mapper.query(DynamoStudyConsent1.class, queryExpression);
        List<StudyConsent> results = new ArrayList<StudyConsent>();
        for (DynamoStudyConsent1 consent : consents) {
            results.add(consent);
        }
        return results;
    }

    @Override
    public void deleteConsent(StudyIdentifier studyIdentifier, long timestamp) {
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setStudyKey(studyIdentifier.getIdentifier());
        consent.setCreatedOn(timestamp);
        consent = mapper.load(consent);
        if (consent != null) {
            mapper.delete(consent);
        }
    }
}
