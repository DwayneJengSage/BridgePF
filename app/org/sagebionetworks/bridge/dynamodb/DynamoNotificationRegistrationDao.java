package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreatePlatformEndpointRequest;
import com.amazonaws.services.sns.model.CreatePlatformEndpointResult;
import com.amazonaws.services.sns.model.DeleteEndpointRequest;
import com.amazonaws.services.sns.model.GetEndpointAttributesRequest;
import com.amazonaws.services.sns.model.GetEndpointAttributesResult;
import com.amazonaws.services.sns.model.InvalidParameterException;
import com.amazonaws.services.sns.model.NotFoundException;
import com.amazonaws.services.sns.model.SetEndpointAttributesRequest;
import com.google.common.collect.Maps;

@Component
public class DynamoNotificationRegistrationDao implements NotificationRegistrationDao {

    private static final String ENABLED = "Enabled";
    private static final String TOKEN = "Token";

    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    @Resource(name = "notificationRegistrationMapper")
    final void setNotificationRegistrationMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }
    
    @Override
    public List<NotificationRegistration> listRegistrations(String healthCode) {
        checkNotNull(healthCode);
        
        DynamoNotificationRegistration hashKey = new DynamoNotificationRegistration();
        hashKey.setHealthCode(healthCode);
        
        DynamoDBQueryExpression<DynamoNotificationRegistration> query =
                new DynamoDBQueryExpression<DynamoNotificationRegistration>().withHashKeyValues(hashKey);

        // This will force all records to be loaded if there's more than a page, in any non-pathological 
        // account there will be half a dozen, tops
        return mapper.query(DynamoNotificationRegistration.class, query)
                .stream().collect(Collectors.toList());
    }

    @Override
    public NotificationRegistration getRegistration(String healthCode, String guid) {
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        DynamoNotificationRegistration hashKey = new DynamoNotificationRegistration();
        hashKey.setHealthCode(healthCode);
        hashKey.setGuid(guid);
        
        NotificationRegistration registration = mapper.load(hashKey);
        if (registration == null) {
            throw new EntityNotFoundException(NotificationRegistration.class);
        }
        return registration;
    }

    @Override
    public NotificationRegistration createRegistration(String platformARN, NotificationRegistration registration) {
        checkNotNull(platformARN);
        checkNotNull(registration);
        checkNotNull(registration.getHealthCode());
        checkNotNull(registration.getDeviceId());
        checkNotNull(registration.getOsName());
        
        // (If the client is submitting same data a second time, SNS quietly ignores it, returns same endpointARN.) 
        CreatePlatformEndpointRequest request = new CreatePlatformEndpointRequest()
                .withToken(registration.getDeviceId())
                .withPlatformApplicationArn(platformARN);
        CreatePlatformEndpointResult result = snsClient.createPlatformEndpoint(request);
        
        // If the data is the same and returns an existing endpointARN, we want to re-use the original record 
        // and GUID we provided to the client, not create a new record. Look for it.
        NotificationRegistration existing = findExistingRecord(registration.getHealthCode(), result.getEndpointArn());
        long timestamp = DateUtils.getCurrentMillisFromEpoch();
        if (existing != null) {
            registration.setGuid(existing.getGuid());
            registration.setCreatedOn(existing.getCreatedOn());
        } else {
            registration.setGuid(BridgeUtils.generateGuid());    
            registration.setCreatedOn(timestamp);
        }
        registration.setHealthCode(registration.getHealthCode());
        registration.setModifiedOn(timestamp);
        registration.setEndpointARN(result.getEndpointArn());
        
        mapper.save(registration);
        return registration;
    }
    
    /**
     * Update an endpoint using the GUID that was supplied on creation. The only thing you can actually update is the
     * device token, but this is important to be able to update as it changes on some platforms.
     */
    @Override
    public NotificationRegistration updateRegistration(NotificationRegistration registration) {
        checkNotNull(registration);
        checkNotNull(registration.getHealthCode());
        checkNotNull(registration.getGuid());
        checkNotNull(registration.getDeviceId());
        
        String guid = registration.getGuid();
        String deviceId = registration.getDeviceId();
        
        // Throws 404 if registration doesn't exist
        NotificationRegistration existingRegistration = getRegistration(registration.getHealthCode(), guid);
        
        // Don't call update unless token has changed
        Map<String, String> attrs = getEndpointAttributes(existingRegistration.getEndpointARN());
        if (!attrs.get(TOKEN).equals(deviceId)) {
            saveEndpointAttributes(registration.getHealthCode(), deviceId);
        
            existingRegistration.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
            existingRegistration.setDeviceId(deviceId);
            mapper.save(existingRegistration);
        }
        return existingRegistration;
    }

    @Override
    public void deleteRegistration(String healthCode, String guid) {
        checkNotNull(healthCode);
        checkNotNull(guid);
        
        // Throws 404 if registration doesn't exist
        NotificationRegistration registration = getRegistration(healthCode, guid);
        
        DeleteEndpointRequest request = new DeleteEndpointRequest().withEndpointArn(registration.getEndpointARN());
        snsClient.deleteEndpoint(request);
        
        mapper.delete(registration);
    }

    private NotificationRegistration findExistingRecord(String healthCode, String endpointARN) {
        List<NotificationRegistration> records = listRegistrations(healthCode);
        for (NotificationRegistration registration : records) {
            if (endpointARN.equals(registration.getEndpointARN())) {
                return registration;
            }
        }
        return null;
    }

    private Map<String, String> getEndpointAttributes(String endpointARN) {
        try {
            GetEndpointAttributesRequest request = new GetEndpointAttributesRequest().withEndpointArn(endpointARN);
            GetEndpointAttributesResult result = snsClient.getEndpointAttributes(request);
            return Maps.newHashMap(result.getAttributes());
        } catch(InvalidParameterException e) {
            throw new BridgeServiceException(e.getMessage());
        } catch(NotFoundException e) {
            throw new EntityNotFoundException(NotificationRegistration.class);
        }
    }

    private void saveEndpointAttributes(String healthCode, String deviceToken) {
        SetEndpointAttributesRequest attrRequest = new SetEndpointAttributesRequest();
        attrRequest.addAttributesEntry(TOKEN, deviceToken);
        attrRequest.addAttributesEntry(ENABLED, Boolean.TRUE.toString());
        snsClient.setEndpointAttributes(attrRequest);
    }
}
